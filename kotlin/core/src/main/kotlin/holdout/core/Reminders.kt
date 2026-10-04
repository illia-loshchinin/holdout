package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * On-device reminders (FR-068, research R21): which reminders a wallet should
 * have pending, derived from chain state and chain time alone, like every
 * other figure. The app recomputes this on every chain refresh and hands the
 * result to the phone's alarm service, so joins, leaves, completed days and
 * forfeits need no bookkeeping of their own — they just change the next answer.
 */
data class Reminder(
    val kind: Kind,
    val cohort: SolanaPublicKey,
    val leagueId: Int,
    /** The cohort day a deadline reminder is for; the first day for a start reminder. */
    val dayIndex: Int,
    /** When it fires, in chain seconds. */
    val at: Long,
    /** The day's deadline in chain seconds (for a start reminder, the first day's). */
    val deadline: Long,
    val repsPerDay: Int,
    val durationDays: Int,
    val dayLength: Long,
    /** Days the chain had recorded when this was scheduled: those are done, whatever the phone holds. */
    val daysOnChain: Int = 0,
) {
    enum class Kind { Start, Deadline }

    /** Stable across recomputations, so scheduling it again replaces it rather than adding another. */
    val id: Int get() = 31 * (31 * cohort.base58().hashCode() + kind.ordinal) + dayIndex
}

object Reminders {
    /**
     * How long before a deadline (FR-068): a quarter of the day, at most 3 hours
     * — 3 hours before a 24-hour day's deadline, 15 minutes before a one-hour
     * day's. Never less than the 10 minutes Android may take to deliver it
     * ([MIN_WINDOW_SECONDS]), nor more than the day itself: a 10-minute test
     * day is reminded as it opens.
     */
    fun leadSeconds(dayLength: Long): Long = minOf(3 * 3_600L, maxOf(dayLength / 4, MIN_WINDOW_SECONDS), dayLength - 1)

    /** The shortest window Android grants an inexact alarm without the exact-alarm permission. */
    const val MIN_WINDOW_SECONDS = 10 * 60L

    /**
     * Every reminder still to come for one wallet at [now]: for each Holdout it
     * holds and has not forfeited, finished or left — a start reminder while it
     * has not started, and for every day not recorded on chain, one at
     * `deadline − lead`. Nothing once the member is in breach.
     *
     * A reminder stays in the schedule until its delivery window has closed
     * (or its deadline passed), not merely until `at`: the phone may deliver
     * it late within that window, and one dropped from the schedule is
     * cancelled — so a refresh just after `at` must not take it away.
     *
     * A day done on the phone but not yet on chain is still reminded: until it
     * reaches the chain it counts for nothing, and a submit that failed is
     * exactly when a reminder is worth having. The notification says to submit
     * it rather than to do it.
     */
    fun schedule(
        memberships: List<Pair<CohortSnapshot, MembershipSnapshot>>,
        cohortAddress: (CohortSnapshot) -> SolanaPublicKey,
        now: Long,
    ): List<Reminder> = memberships.flatMap { (c, m) ->
        if (m.state != MembershipSnapshot.State.Joined || c.settled || Derive.inBreach(m, c, now) || now >= Derive.end(c)) return@flatMap emptyList()
        val address = cohortAddress(c)
        val window = windowSeconds(c.dayLength)
        fun reminder(kind: Reminder.Kind, day: Int, at: Long) = Reminder(kind, address, c.leagueId, day, at, Derive.deadlineOf(c, day), c.repsPerDay, c.durationDays, c.dayLength, m.daysCompleted)
        fun deliverable(at: Long, deadline: Long) = minOf(at + window, deadline) > now
        buildList {
            // Not once the first day is on chain: an alarm still waiting in its
            // window would say day 1 is open and owed after it was submitted.
            if (m.daysCompleted == 0 && deliverable(c.start, Derive.deadlineOf(c, 0))) add(reminder(Reminder.Kind.Start, 0, c.start))
            val lead = leadSeconds(c.dayLength)
            for (day in m.daysCompleted until c.durationDays) {
                val at = Derive.deadlineOf(c, day) - lead
                // A day reminded as it opens: the first one is the start reminder's.
                if (at > c.start && deliverable(at, Derive.deadlineOf(c, day))) add(reminder(Reminder.Kind.Deadline, day, at))
            }
        }
    }.sortedBy { it.at }

    /**
     * How late the phone may deliver a reminder: a third of the lead, and never
     * under [MIN_WINDOW_SECONDS]. So a one-hour day's reminder comes 15 to 5
     * minutes before its deadline, and a 24-hour day's 3 to 2 hours before:
     * always before the deadline itself. A 10-minute test day's comes at any
     * point of the day — the window is the whole day — and one that would land
     * after the deadline is not shown (ReminderReceiver).
     */
    fun windowMillis(dayLength: Long): Long = windowSeconds(dayLength) * 1_000

    private fun windowSeconds(dayLength: Long): Long = maxOf(MIN_WINDOW_SECONDS, leadSeconds(dayLength) / 3)

    /**
     * A chain instant as a moment on the phone's monotonic clock, from one
     * reading of both taken together (research R7): the wall clock never
     * decides when a reminder fires.
     */
    fun elapsedMillisAt(atChainSeconds: Long, chainNowSeconds: Long, elapsedNowMillis: Long): Long = elapsedNowMillis + (atChainSeconds - chainNowSeconds) * 1_000
}
