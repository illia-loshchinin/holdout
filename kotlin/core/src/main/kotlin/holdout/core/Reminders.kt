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
) {
    enum class Kind { Start, Deadline }

    /** Stable across recomputations, so scheduling it again replaces it rather than adding another. */
    val id: Int get() = 31 * (31 * cohort.base58().hashCode() + kind.ordinal) + dayIndex
}

object Reminders {
    /** How long before a deadline: 3 hours before a 24-hour day's, 15 minutes before a one-hour day's (FR-068). */
    fun leadSeconds(dayLength: Long): Long = minOf(3 * 3_600L, dayLength / 4)

    /**
     * Every reminder still to come for one wallet at [now]: for each Holdout it
     * holds and has not forfeited, finished or left — a start reminder while it
     * has not started, and for every day not recorded on chain, one at
     * `deadline − lead`. Nothing in the past; nothing once the member is in
     * breach.
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
        fun reminder(kind: Reminder.Kind, day: Int, at: Long) = Reminder(kind, address, c.leagueId, day, at, Derive.deadlineOf(c, day), c.repsPerDay, c.durationDays, c.dayLength)
        buildList {
            if (now < c.start) add(reminder(Reminder.Kind.Start, 0, c.start))
            val lead = leadSeconds(c.dayLength)
            for (day in m.daysCompleted until c.durationDays) {
                val at = Derive.deadlineOf(c, day) - lead
                if (at > now) add(reminder(Reminder.Kind.Deadline, day, at))
            }
        }
    }.sortedBy { it.at }

    /**
     * How late the phone may deliver a reminder: a third of the lead, and never
     * under 10 minutes — the shortest window Android grants without the
     * exact-alarm permission. So a one-hour day's reminder comes 15 to 5 minutes
     * before its deadline, and a 24-hour day's 3 to 2 hours before: always
     * before the deadline itself.
     */
    fun windowMillis(dayLength: Long): Long = maxOf(10 * 60L, leadSeconds(dayLength) / 3) * 1_000

    /**
     * A chain instant as a moment on the phone's monotonic clock, from one
     * reading of both taken together (research R7): the wall clock never
     * decides when a reminder fires.
     */
    fun elapsedMillisAt(atChainSeconds: Long, chainNowSeconds: Long, elapsedNowMillis: Long): Long = elapsedNowMillis + (atChainSeconds - chainNowSeconds) * 1_000
}
