package holdout.core

/**
 * Cohort schedule arithmetic — the Kotlin mirror of
 * `programs/holdout/src/math/schedule.rs` and data-model §1.3.
 *
 * One clock (constitution VI): every instant is chain time in seconds, and a
 * day turns over at the cohort's own boundary. The program enforces these
 * figures; the app shows them, so they must agree to the second.
 */
object Schedule {
    /** 05:00 UTC, in seconds past midnight (OD-1). */
    const val BOUNDARY_OFFSET = 18_000L

    /** A cohort day on devnet. */
    const val DAY_LENGTH = 86_400L

    /** Joining and leaving close one hour before the start (FR-008). */
    const val JOIN_LOCK = 3_600L

    /** Cohorts start weekly (FR-007a). */
    const val CADENCE = 604_800L

    /** Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start. */
    const val FIRST_START = 1_790_571_600L

    /** Start of cohort [index] in a league: `first_start + index × cadence` (R11). */
    fun cohortStart(firstStart: Long, cadence: Long, index: Int): Long = firstStart + index * cadence

    /**
     * The cohort open for joining at [now]: the first whose join cutoff has not
     * passed. Every league opens its next cohort the moment its current one
     * starts (FR-007a), and joining closes an hour before the start.
     */
    fun openIndex(firstStart: Long, cadence: Long, joinLock: Long, now: Long): Int {
        if (now < firstStart - joinLock) return 0
        // The first index whose cutoff is still ahead: cutoff(i) > now.
        return (Math.floorDiv(now - (firstStart - joinLock), cadence) + 1).toInt()
    }
}

/** data-model §1.3: `status(now)` without settlement, which the app reads from the chain. */
enum class CohortStatus { OPEN, LOCKED, RUNNING, ENDED }

/** The times that define a cohort — copied from its league at creation (FR-007). */
data class CohortTimes(
    val start: Long,
    val durationDays: Int,
    val dayLength: Long = Schedule.DAY_LENGTH,
    val joinLock: Long = Schedule.JOIN_LOCK,
) {
    val joinCutoff: Long get() = start - joinLock
    val end: Long get() = start + durationDays * dayLength

    fun status(now: Long): CohortStatus = when {
        now < joinCutoff -> CohortStatus.OPEN
        now < start -> CohortStatus.LOCKED
        now < end -> CohortStatus.RUNNING
        else -> CohortStatus.ENDED
    }

    /** The last second of day [k] (zero-based): `start + (k + 1) × day_length − 1`. */
    fun deadlineOf(k: Int): Long = start + (k + 1) * dayLength - 1

    /** Whole days elapsed at [now], clamped to `0 … durationDays` — the running day's index. */
    fun daysElapsed(now: Long): Int {
        if (now < start) return 0
        return minOf((now - start) / dayLength, durationDays.toLong()).toInt()
    }

    /** The INV-3 ceiling: the day in progress may itself be completed at any second of it. */
    fun maxDaysCompleted(now: Long): Int = if (now < start) 0 else minOf(daysElapsed(now) + 1, durationDays)

    /** `now > deadline_of(days_completed)` once running, unless every day is done (data-model §1.4). */
    fun inBreach(daysCompleted: Int, now: Long): Boolean = now >= start && daysCompleted < durationDays && now > deadlineOf(daysCompleted)
}
