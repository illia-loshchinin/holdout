package holdout.app.cohort

/**
 * The thinning chart's axes, apart from the drawing so a JVM test pins them. The x axis is the
 * Holdout's whole length, laid out up front: the start at the left edge and the last day at the
 * right, so a line that has run two days of three ends two thirds across and the rest is room
 * to come. The y axis runs from a floor under the least to the most, with gridlines at whole
 * numbers only.
 */
object ThinningAxes {
    /** A label under the baseline: [day] 0 is the start. */
    data class XTick(val day: Int, val label: String)

    /** The y axis: [lo] at the baseline, [hi] at the top, a dotted line at each of [grid]. */
    data class YAxis(val lo: Int, val hi: Int, val grid: List<Int>)

    /** Where [day] sits across the axis, 0 at the start and 1 at the last day of [totalDays]. */
    fun xFraction(day: Int, totalDays: Int): Float = if (totalDays <= 0) 0f else day.coerceIn(0, totalDays).toFloat() / totalDays

    /**
     * `start`, a middle mark (a longer Holdout gets two, at thirds) and `day N` at the right edge.
     * Marks are at least a fifth of the axis apart, so none overlaps; a Holdout under five days has none.
     */
    fun xTicks(totalDays: Int): List<XTick> {
        val middle = when {
            totalDays >= TWO_MARKS_FROM -> listOf(totalDays / 3, totalDays * 2 / 3)
            totalDays >= ONE_MARK_FROM -> listOf(totalDays / 2)
            else -> emptyList()
        }
        return buildList {
            add(XTick(0, "start"))
            middle.forEach { add(XTick(it, "day $it")) }
            if (totalDays > 0) add(XTick(totalDays, "day $totalDays"))
        }
    }

    /** The y axis for [points] (members standing at the start, then after each day): a floor under the least, the start count on top, whole-number gridlines between. */
    fun yAxis(points: List<Int>): YAxis {
        val hi = points.max()
        val min = points.min()
        return YAxis(DetailText.floorValue(min, hi), hi, DetailText.gridValues(min, hi))
    }

    private const val ONE_MARK_FROM = 5
    private const val TWO_MARKS_FROM = 14
}
