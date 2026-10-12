package holdout.app.streak

/**
 * The days of the Daily 25 streak that get a word (FR-049; screen map S6 and T1).
 *
 * The count is the streak's own: [StreakStore] adds one for a completed day and
 * nothing for a frozen one, so a frozen day never counts toward a milestone.
 * Nothing is stored for it: a milestone is "reached today" exactly when the
 * streak after today's completed day is one of these ([reachedToday]).
 *
 * Personal and cosmetic like the streak (FR-051): on the phone only, it unlocks
 * nothing and is never paid against.
 */
object StreakMilestones {
    /** The days below a hundred that get a line; from 100 on, every hundred does, and a year. */
    private val FIXED = setOf(7, 14, 21, 28, 30, 50, 60, 90)
    private const val HUNDRED = 100
    private const val YEAR = 365

    /** The words under the headline, by day. Every other hundred reads [STILL_GOING]. */
    private val LINES = mapOf(
        7 to "A week, every day. That's how habits start.",
        14 to "Two weeks in. You're making this yours.",
        21 to "21 days. That's a habit now.",
        28 to "Four weeks without a gap.",
        30 to "A month of showing up.",
        50 to "50 days. Most people never get here.",
        60 to "Two months strong.",
        90 to "Three months. This is who you are now.",
        HUNDRED to "100 days. Triple digits.",
        YEAR to "A year, every day.",
    )

    /** Whether a streak of [days] is a milestone: 7, 14, 21, 28, 30, 50, 60, 90, 100, every further 100, and 365. */
    fun isMilestone(days: Int): Boolean = days in FIXED || days == YEAR || (days >= HUNDRED && days % HUNDRED == 0)

    /** The headline of a milestone, `{N}-day streak`. */
    fun headline(days: Int): String = "$days-day streak"

    /** The line under the headline, or null when [days] is not a milestone. */
    fun line(days: Int): String? = when {
        !isMilestone(days) -> null
        else -> LINES[days] ?: "$days days. Still here, still going."
    }

    /** Today's streak is at a milestone because today's day is done: the T1 chip shows it for the rest of the day, the next day it is plain. */
    fun reachedToday(streak: StreakStore.Streak): Boolean = streak.completedToday && isMilestone(streak.current)
}
