package holdout.app.cohort

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Derive
import holdout.core.MembershipSnapshot

/**
 * What the Holdout detail (H4), its forfeit and their chart say, kept pure so
 * it can be tested: no calendar date or clock time anywhere (constitution VI,
 * ruling G3), no amount that is not a figure of the chain (FR-038a).
 */
internal object DetailText {
    /** The button to the page of everyone in the Holdout (FR-062, 2026-10-13: not the old word for it). */
    const val MEMBERS = "Members"
    const val WATCHING = "You're watching this one"
    const val WATCHER_NOTE = "You can watch it thin. Joining is for Seeker owners."
    const val SHARE_NOTE = "on top of your stake, if today's standing holds"

    /** `HABIT · 21-DAY · DAY 12 OF 21` while it runs, `SPARK · 7-DAY · FINISHED` after, `… · STARTS IN 2D 4H` before. */
    fun label(c: CohortSnapshot, now: Long): String = when (Derive.status(c, now)) {
        CohortStatus.Open, CohortStatus.Locked -> Format.lobbyHeaderLabel(c, c.start - now)
        CohortStatus.Running -> Format.headerLabel(c, (Derive.dayIndex(c, now) + 1).coerceIn(1, c.durationDays))
        CohortStatus.Settling, CohortStatus.Settled -> Format.finishedHeaderLabel(c)
    }

    /** Members standing at the start, then after each deadline that has passed. */
    fun thinning(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): List<Int> = listOf(c.memberCount.toInt()) + survivorHistory(c, members, now).map { it.second }

    /** `Members still standing after each day: 312 at the start, 189 after day 11`. */
    fun thinningDescription(points: List<Int>): String {
        val last = points.lastIndex
        return "Members still standing after each day: ${points.first()} at the start, ${points.last()} after day $last"
    }

    /** `You're out after 6 days.` */
    fun outTitle(daysLasted: Int): String = "You're out after $daysLasted ${if (daysLasted == 1) "day" else "days"}"

    /** The breakdown rows of a claimed Holdout need a stake to show. */
    fun hasBreakdown(stake: ULong): Boolean = stake > 0uL

    /**
     * Where the chart's dotted lines sit: the multiples of a round step strictly
     * between [min] and [max], no more than three (the board draws 200 · 250 · 300
     * for 189 … 312).
     */
    fun gridValues(min: Int, max: Int): List<Int> {
        val span = max - min
        if (span <= 0) return emptyList()
        // The first round step that leaves three lines or fewer (a span of 17 at step 5 can hold four).
        return STEPS.firstNotNullOfOrNull { step ->
            generateSequence((min / step + 1) * step) { it + step }.takeWhile { it < max }.toList().takeIf { it.size <= 3 }
        } ?: emptyList()
    }

    /** The lowest value the chart's baseline stands for: a fifth under the smallest point, so the line does not sit on it. */
    fun floorValue(min: Int, max: Int): Int = if (max > min) (min * 4 / 5).coerceAtMost(min - 1).coerceAtLeast(0) else (min - 1).coerceAtLeast(0)

    private val STEPS = listOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1_000, 2_000, 2_500, 5_000, 10_000, 100_000)
}

/** Members still standing after each deadline that has passed (see [CohortDetailScreen]). */
internal fun survivorHistory(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): List<Pair<Int, Int>> {
    val passed = (0 until c.durationDays).filter { now > Derive.deadlineOf(c, it) }
    return passed.map { day -> day to members.count { it.daysCompleted > day } }
}
