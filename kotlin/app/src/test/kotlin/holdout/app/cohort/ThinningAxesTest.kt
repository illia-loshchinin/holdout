package holdout.app.cohort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the crowd thinned: the axes cover the Holdout's whole length up front, and a small crowd gets whole-number gridlines. */
class ThinningAxesTest {
    @Test
    fun `x runs from the start to the last day of the Holdout, not to the last point`() {
        // A 3-day Holdout at day 2: three points of four, so the line ends two thirds across.
        assertEquals(0f, ThinningAxes.xFraction(0, 3))
        assertEquals(2f / 3f, ThinningAxes.xFraction(2, 3))
        assertEquals(1f, ThinningAxes.xFraction(3, 3))
        assertEquals(11f / 21f, ThinningAxes.xFraction(11, 21))
    }

    @Test
    fun `a day outside the Holdout stays on the axis`() {
        assertEquals(1f, ThinningAxes.xFraction(9, 7))
        assertEquals(0f, ThinningAxes.xFraction(-1, 7))
        assertEquals(0f, ThinningAxes.xFraction(0, 0))
    }

    @Test
    fun `labels - start, a middle mark or two, and the last day at the right edge`() {
        assertEquals(listOf(0 to "start", 7 to "day 7", 14 to "day 14", 21 to "day 21"), ThinningAxes.xTicks(21).map { it.day to it.label })
        assertEquals(listOf(0 to "start", 3 to "day 3", 7 to "day 7"), ThinningAxes.xTicks(7).map { it.day to it.label })
        assertEquals(listOf(0 to "start", 3 to "day 3"), ThinningAxes.xTicks(3).map { it.day to it.label })
    }

    @Test
    fun `labels never crowd - each is a fifth of the axis from the next`() {
        for (total in 1..60) {
            val fractions = ThinningAxes.xTicks(total).map { ThinningAxes.xFraction(it.day, total) }
            assertEquals(fractions.sorted(), fractions)
            assertEquals(fractions.distinct(), fractions)
            fractions.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 0.2f, "day $total: $a then $b") }
        }
    }

    @Test
    fun `a small crowd - 4 started, 1 standing - has whole-number gridlines between 0 and 4`() {
        val y = ThinningAxes.yAxis(listOf(4, 3, 1))
        assertEquals(0, y.lo)
        assertEquals(4, y.hi)
        assertEquals(listOf(2, 3), y.grid)
    }

    @Test
    fun `gridlines are whole, distinct, ascending and inside the axis for any crowd`() {
        for (start in 1..40) {
            for (standing in 0..start) {
                val y = ThinningAxes.yAxis(listOf(start, standing))
                assertEquals(y.grid.distinct().sorted(), y.grid, "$start to $standing")
                assertTrue(y.grid.all { it > y.lo && it < y.hi }, "$start to $standing: ${y.grid} in ${y.lo}..${y.hi}")
                assertTrue(y.lo >= 0 && y.lo < y.hi, "$start to $standing")
                assertTrue(y.grid.size <= 3)
            }
        }
    }

    @Test
    fun `a crowd that has not thinned still has an axis`() {
        val y = ThinningAxes.yAxis(listOf(4, 4))
        assertEquals(3, y.lo)
        assertEquals(4, y.hi)
        assertEquals(emptyList(), y.grid)
    }

    @Test
    fun `the board's crowd keeps its round gridlines`() {
        val y = ThinningAxes.yAxis(listOf(312, 301, 288, 270, 259, 243, 231, 222, 210, 203, 195, 189))
        assertEquals(listOf(200, 250, 300), y.grid)
        assertEquals(151, y.lo)
        assertEquals(312, y.hi)
    }
}
