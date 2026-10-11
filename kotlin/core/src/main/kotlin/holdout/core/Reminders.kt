package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * On-device reminders (FR-068, research R21): which reminders a wallet should
 * have pending, derived from chain state and chain time alone (plus, for a day
 * done on the phone but not yet on chain, the phone's own record of it), like
 * every other figure. The app recomputes this on every chain refresh and hands
 * the result to the phone's alarm service, so joins, leaves, completed days and
 * forfeits need no bookkeeping of their own — they just change the next answer.
 */
data class Reminder(
    val kind: Kind,
    val cohort: SolanaPublicKey,
    val leagueId: Int,
    /** The cohort day a deadline reminder is for; the first day for a start reminder; `durationDays` for the finished notice. */
    val dayIndex: Int,
    /** When it fires, in chain seconds; for the standing [Kind.NotSent] notice (tick 0), the moment it was computed. */
    val at: Long,
    /**
     * The day's deadline in chain seconds (for a start reminder, the first
     * day's); for the finished notice, the last moment it is still worth
     * posting, `at + FINISHED_HORIZON_SECONDS`. Past it the receiver posts nothing.
     */
    val deadline: Long,
    val repsPerDay: Int,
    val durationDays: Int,
    val dayLength: Long,
    /** Days the chain had recorded when this was scheduled: those are done, whatever the phone holds. */
    val daysOnChain: Int = 0,
    /**
     * Only for [Kind.NotSent]: 0 is the standing notice, posted at once and kept
     * until the day is submitted or its deadline passes; `k ≥ 1` is the silent
     * hourly re-post `k` hours before the deadline.
     */
    val tick: Int = 0,
    /** Only for [Kind.Finished]: this finish should earn a comeback refund (FR-037e), so the notice says so. */
    val comeback: Boolean = false,
) {
    enum class Kind {
        Start,
        Deadline,

        /** Halfway through a long day, with sound (FR-068, 2026-10-10). */
        Midday,

        /** A day done on the phone and not yet on chain (FR-068, 2026-10-11): kept in the shade, silently re-posted every hour. */
        NotSent,

        /**
         * Every day on chain and the cohort not yet settled (FR-068b, 2026-10-11):
         * one notice when settlement usually has come, saying the claim is
         * probably ready. It reads no chain when it fires (FR-068a).
         */
        Finished,
    }

    /** The standing not-sent notice: shown at once rather than set as an alarm. */
    val standing: Boolean get() = kind == Kind.NotSent && tick == 0

    /** Posted without sound or vibration: see [Reminders.isSilent]. */
    val silent: Boolean get() = Reminders.isSilent(kind, dayLength)

    /**
     * Stable across recomputations, so scheduling it again replaces it rather than
     * adding another, and distinct for every (day, kind, tick) of one cohort:
     * a cohort's day index fits a `u8` and a slot is under 64.
     */
    val id: Int get() = (cohort.base58().hashCode() * 256 + dayIndex) * 64 + slot

    private val slot: Int get() = if (kind == Kind.NotSent) NOT_SENT_FIRST_SLOT + tick else kind.ordinal

    private companion object {
        /** The slots below this are the kinds' ordinals: a new kind moves the not-sent slots up by itself. */
        val NOT_SENT_FIRST_SLOT = Kind.entries.size
    }
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
     * Days at least this long also get a reminder halfway through, with sound,
     * and their reminder before the deadline is silent (FR-068, amended
     * 2026-10-10): 3 hours before a 05:00 UTC deadline is the middle of the
     * night in Europe, while the midpoint, 17:00 UTC, is an evening there and a
     * working day in the Americas. Chain time only — no timezone (constitution II).
     */
    const val MIDDAY_MIN_DAY_SECONDS = 12 * 3_600L

    fun hasMidday(dayLength: Long): Boolean = dayLength >= MIDDAY_MIN_DAY_SECONDS

    /**
     * The one rule for sound: the reminder before a long day's deadline and the
     * finished notice of a Holdout of long days (both may fall at night), and
     * every not-sent notice, are posted silently; the start, halfway, a short
     * day's deadline reminder and the test lobby's finished notice sound
     * (FR-068, FR-068b).
     */
    fun isSilent(kind: Reminder.Kind, dayLength: Long): Boolean = when (kind) {
        Reminder.Kind.NotSent -> true
        Reminder.Kind.Deadline, Reminder.Kind.Finished -> hasMidday(dayLength)
        else -> false
    }

    /**
     * One notification per Holdout day: its halfway, deadline and not-sent
     * notices replace one another instead of stacking, so the shade shows the
     * latest time left; the start and the finished notice keep their own.
     */
    fun notificationId(cohortBase58: String, kind: Reminder.Kind, dayIndex: Int): Int = 31 * cohortBase58.hashCode() + when (kind) {
        Reminder.Kind.Start -> -1
        Reminder.Kind.Finished -> -2
        else -> dayIndex
    }

    /**
     * How long after its time a finished notice held by an idle phone is still
     * worth posting (FR-068b): later than that the member has most likely
     * opened the app, and a stale "probably ready" says nothing.
     */
    const val FINISHED_HORIZON_SECONDS = 6 * 3_600L

    /** The hourly not-sent re-posts (FR-068, 2026-10-11) are this far apart, counted back from the deadline. */
    const val NOT_SENT_INTERVAL_SECONDS = 3_600L

    /**
     * At most this many hourly re-posts are scheduled for one day (a day of up
     * to 25 hours gets them all), which bounds the alarms a Holdout holds.
     */
    const val NOT_SENT_MAX_TICKS = 24

    /**
     * Every reminder still to come for one wallet at [now]: for each Holdout it
     * holds, is still in and has days left in — a start reminder while it has
     * not started, and for every day not recorded on chain, one at
     * `deadline − lead` and, for a day of 12 hours or more, one halfway through
     * it. Nothing once the member is in breach or has left. That is at most
     * `1 + 2 × days` reminders a Holdout, each held as two alarms
     * (ReminderScheduler): 43 reminders and 86 alarms for a 21-day Holdout,
     * against the 500 an app may hold; once every day is on chain, only the
     * finished notice below.
     *
     * A reminder stays in the schedule until its delivery window has closed
     * (or its deadline passed), not merely until `at`: the phone may deliver
     * it late within that window, and one dropped from the schedule is
     * cancelled — so a refresh just after `at` must not take it away.
     *
     * A day done on the phone but not yet on chain ([doneOnPhone]) counts for
     * nothing until it reaches the chain, and a submit that failed is exactly
     * when a reminder is worth having. Until it is submitted or its deadline
     * passes, that one day (the first not on chain) has a standing
     * [Reminder.Kind.NotSent] notice (tick 0) and a silent re-post of it every
     * hour before the deadline (ticks 1..[NOT_SENT_MAX_TICKS], two alarms each:
     * at most 48), instead of its halfway reminder and, on a long day, its
     * deadline reminder (FR-068, 2026-10-11, research R21). A short day keeps its
     * deadline reminder, which says to submit it.
     *
     * A member with every day on chain in a cohort not yet settled gets one
     * [Reminder.Kind.Finished] notice at `end + SETTLE_USUAL_SECONDS` (FR-068b),
     * kept only while that time is ahead: a read after it that finds the cohort
     * still unsettled drops it, as does one that finds it settled. [comebackFor]
     * says whether this finish should earn a comeback refund (FR-037e).
     */
    fun schedule(
        memberships: List<Pair<CohortSnapshot, MembershipSnapshot>>,
        cohortAddress: (CohortSnapshot) -> SolanaPublicKey,
        now: Long,
        comebackFor: (CohortSnapshot, MembershipSnapshot) -> Boolean = { _, _ -> false },
        // Last, so a trailing lambda is still the day done on the phone.
        doneOnPhone: (CohortSnapshot, Int) -> Boolean = { _, _ -> false },
    ): List<Reminder> = memberships.flatMap { (c, m) ->
        // Finished: no day left to remind of, only the claim to come.
        if (m.daysCompleted >= c.durationDays) return@flatMap listOfNotNull(finishedReminder(c, m, cohortAddress, now, comebackFor))
        if (m.state != MembershipSnapshot.State.Joined || c.settled || Derive.inBreach(m, c, now) || now >= Derive.end(c)) return@flatMap emptyList()
        val address = cohortAddress(c)
        val window = windowSeconds(c.dayLength)
        val longDay = hasMidday(c.dayLength)
        val lead = leadSeconds(c.dayLength)
        fun reminder(kind: Reminder.Kind, day: Int, at: Long, tick: Int = 0) = Reminder(kind, address, c.leagueId, day, at, Derive.deadlineOf(c, day), c.repsPerDay, c.durationDays, c.dayLength, m.daysCompleted, tick)
        fun deliverable(at: Long, deadline: Long, within: Long = window) = minOf(at + within, deadline) > now
        buildList {
            // Not once the first day is on chain: an alarm still waiting in its
            // window would say day 1 is open and owed after it was submitted.
            if (m.daysCompleted == 0 && deliverable(c.start, Derive.deadlineOf(c, 0))) add(reminder(Reminder.Kind.Start, 0, c.start))
            for (day in m.daysCompleted until c.durationDays) {
                val deadline = Derive.deadlineOf(c, day)
                val opens = c.start + day * c.dayLength
                // The day open now, done here and not on chain: kept in view, not reminded to be done.
                val notSent = day == m.daysCompleted && now >= opens && doneOnPhone(c, day)
                if (notSent) {
                    add(reminder(Reminder.Kind.NotSent, day, now))
                    for (tick in 1..NOT_SENT_MAX_TICKS) {
                        val at = deadline - tick * NOT_SENT_INTERVAL_SECONDS
                        if (at <= opens) break
                        if (deliverable(at, deadline, MIN_WINDOW_SECONDS)) add(reminder(Reminder.Kind.NotSent, day, at, tick))
                    }
                }
                if (longDay && !notSent) {
                    val halfway = deadline - c.dayLength / 2
                    if (deliverable(halfway, deadline)) add(reminder(Reminder.Kind.Midday, day, halfway))
                }
                // A long day's not-sent hours stand in for its deadline reminder.
                if (notSent && longDay) continue
                val at = deadline - lead
                // A day reminded as it opens: the first one is the start reminder's.
                if (at > c.start && deliverable(at, deadline)) add(reminder(Reminder.Kind.Deadline, day, at))
            }
        }
    }.sortedBy { it.at }

    /** The finished notice of [m] in [c] (FR-068b), while its time is ahead and the cohort not settled; else null. */
    private fun finishedReminder(
        c: CohortSnapshot,
        m: MembershipSnapshot,
        cohortAddress: (CohortSnapshot) -> SolanaPublicKey,
        now: Long,
        comebackFor: (CohortSnapshot, MembershipSnapshot) -> Boolean,
    ): Reminder? {
        val at = Derive.end(c) + Derive.SETTLE_USUAL_SECONDS
        val counting = m.state == MembershipSnapshot.State.Joined || m.state == MembershipSnapshot.State.Finished
        if (!counting || c.settled || now >= at) return null
        return Reminder(
            Reminder.Kind.Finished, cohortAddress(c), c.leagueId, c.durationDays, at, at + FINISHED_HORIZON_SECONDS,
            c.repsPerDay, c.durationDays, c.dayLength, m.daysCompleted, comeback = comebackFor(c, m),
        )
    }

    /**
     * Whether the finished notice of [m] in [c] should mention a comeback
     * refund (FR-068b, FR-037e): the membership already carries one, or the
     * league progress says finishing [c] earns one — and no earlier finished,
     * unsettled Holdout of the same league would use the record up first
     * (settlement counts them in order, FR-061a).
     */
    fun comebackExpected(
        c: CohortSnapshot,
        m: MembershipSnapshot,
        profile: ProfileSnapshot?,
        memberships: List<Pair<CohortSnapshot, MembershipSnapshot>>,
    ): Boolean {
        if (m.comebackEntitled > 0uL) return true
        val progress = profile?.progress(c.leagueId) ?: return false
        if (!progress.comebackOpenFor(c.start)) return false
        return memberships.none { (o, om) ->
            o.leagueId == c.leagueId && o.start < c.start && !o.settled && om.daysCompleted >= o.durationDays && progress.comebackOpenFor(o.start)
        }
    }

    /**
     * The finished notices to take out of the shade (FR-068b): those of every
     * Holdout the app now sees settled or claimed, whose claim the app shows itself.
     */
    fun finishedToWithdraw(memberships: List<Pair<CohortSnapshot, MembershipSnapshot>>, cohortAddress: (CohortSnapshot) -> SolanaPublicKey): Set<Int> = memberships
        .filter { (c, m) -> c.settled || m.state == MembershipSnapshot.State.Claimed }
        .map { (c, _) -> notificationId(cohortAddress(c).base58(), Reminder.Kind.Finished, c.durationDays) }
        .toSet()

    /**
     * How late the phone may deliver a reminder: a third of the lead, and never
     * under [MIN_WINDOW_SECONDS]. So a one-hour day's reminder comes 15 to 5
     * minutes before its deadline, and a 24-hour day's 3 to 2 hours before:
     * always before the deadline itself. A 10-minute test day's comes at any
     * point of the day — the window is the whole day — and one that would land
     * after the deadline is not shown (ReminderReceiver).
     */
    fun windowMillis(dayLength: Long): Long = windowSeconds(dayLength) * 1_000

    /** [windowMillis] of one reminder: the hourly not-sent re-posts are only [MIN_WINDOW_SECONDS] wide, or each would overlap the next. */
    fun windowMillis(reminder: Reminder): Long = if (reminder.kind == Reminder.Kind.NotSent) MIN_WINDOW_SECONDS * 1_000 else windowMillis(reminder.dayLength)

    private fun windowSeconds(dayLength: Long): Long = maxOf(MIN_WINDOW_SECONDS, leadSeconds(dayLength) / 3)

    /**
     * A chain instant as a moment on the phone's monotonic clock, from one
     * reading of both taken together (research R7): the wall clock never
     * decides when a reminder fires.
     */
    fun elapsedMillisAt(atChainSeconds: Long, chainNowSeconds: Long, elapsedNowMillis: Long): Long = elapsedNowMillis + (atChainSeconds - chainNowSeconds) * 1_000
}
