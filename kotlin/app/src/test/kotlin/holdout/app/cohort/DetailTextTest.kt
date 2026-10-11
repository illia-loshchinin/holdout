package holdout.app.cohort

import holdout.app.testing.Fx
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The words and figures of H4 and the forfeit, with no screen: pure, and never a date. */
class DetailTextTest {
    private val habit = Fx.cohort(days = 21, joined = 312)
    private val spark = Fx.cohort(days = 7, joined = 20)

    @Test
    fun `label - level and day while running, finished after, starts in before`() {
        assertEquals("HABIT · 21-DAY · DAY 12 OF 21", DetailText.label(habit, habit.start + 11 * Fx.DAY + 60))
        assertEquals("SPARK · 7-DAY · FINISHED", DetailText.label(spark, spark.start + 7 * Fx.DAY + 60))
        assertEquals("SPARK · 7-DAY · STARTS IN 2D 4H", DetailText.label(spark, spark.start - 2 * Fx.DAY - 4 * 3_600))
    }

    @Test
    fun `label - the first and last days are clamped to the Holdout`() {
        assertEquals("SPARK · 7-DAY · DAY 1 OF 7", DetailText.label(spark, spark.start))
        assertEquals("SPARK · 7-DAY · DAY 7 OF 7", DetailText.label(spark, spark.start + 7 * Fx.DAY - 1))
    }

    @Test
    fun `days to go leaves today out - day 12 of 21 has 9 to go (ruling D-D5)`() {
        assertEquals(9, DetailText.daysToGo(habit, habit.start + 11 * Fx.DAY + 60))
        assertEquals(0, DetailText.daysToGo(spark, spark.start + 6 * Fx.DAY + 60))
        assertEquals(0, DetailText.daysToGo(spark, spark.start + 9 * Fx.DAY))
    }

    @Test
    fun `days done reads x of y days`() {
        assertEquals("11 of 21 days", DetailText.daysDone(11, habit))
        assertEquals("7 of 7 days", DetailText.daysDone(9, spark))
    }

    @Test
    fun `the thinning starts at the member count and follows the deadlines that have passed`() {
        val members = listOf(4, 4, 3, 2, 1, 1, 0, 0).map { Fx.member(Fx.key(it * 3 + 1), days = it) }
        val c = Fx.cohort(days = 7, joined = 8)
        // Three deadlines have passed: the first three days.
        val now = c.start + 3 * Fx.DAY + 60
        assertEquals(listOf(8, 6, 4, 3), DetailText.thinning(c, members, now))
    }

    @Test
    fun `before any deadline there is only the start`() {
        val c = Fx.cohort(joined = 8)
        assertEquals(listOf(8), DetailText.thinning(c, Fx.roster(8, 0), c.start + 60))
    }

    @Test
    fun `the chart's sentence carries the start and the last day`() {
        assertEquals(
            "Members still standing after each day: 312 at the start, 189 after day 11",
            DetailText.thinningDescription(listOf(312, 301, 288, 270, 259, 243, 231, 222, 210, 203, 195, 189)),
        )
    }

    @Test
    fun `grid lines sit at round values between the least and the most`() {
        assertEquals(listOf(200, 250, 300), DetailText.gridValues(189, 312))
        assertEquals(listOf(4, 6), DetailText.gridValues(3, 8))
        assertEquals(emptyList(), DetailText.gridValues(7, 7))
        assertTrue(DetailText.gridValues(0, 100_000).size <= 3)
    }

    @Test
    fun `the baseline sits a fifth under the least and never below zero`() {
        assertEquals(151, DetailText.floorValue(189, 312))
        assertEquals(0, DetailText.floorValue(0, 10))
        assertEquals(4, DetailText.floorValue(5, 5))
    }

    @Test
    fun `out title is singular for one day`() {
        assertEquals("You're out after 1 day", DetailText.outTitle(1))
        assertEquals("You're out after 6 days", DetailText.outTitle(6))
    }
}
