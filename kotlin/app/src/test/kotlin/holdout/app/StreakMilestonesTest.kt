package holdout.app

import holdout.app.sitting.StreakAfter
import holdout.app.storage.InMemoryStore
import holdout.app.streak.StreakMilestones
import holdout.app.streak.StreakStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gap 2 — the streak milestones (FR-049, screen map S6 and T1): which days, and the line for each. */
class StreakMilestonesTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"

    private val lines = mapOf(
        7 to "A week, every day. That's how habits start.",
        14 to "Two weeks in. You're making this yours.",
        21 to "21 days. That's a habit now.",
        28 to "Four weeks without a gap.",
        30 to "A month of showing up.",
        50 to "50 days. Most people never get here.",
        60 to "Two months strong.",
        90 to "Three months. This is who you are now.",
        100 to "100 days. Triple digits.",
        365 to "A year, every day.",
    )

    @Test
    fun `every listed milestone is one and has its line`() {
        lines.forEach { (days, line) ->
            assertTrue(StreakMilestones.isMilestone(days), "$days is a milestone")
            assertEquals(line, StreakMilestones.line(days), "the line for $days")
        }
    }

    @Test
    fun `every further hundred is a milestone with the still-here line`() {
        listOf(200, 300, 400, 500, 1_000, 1_200).forEach { days ->
            assertTrue(StreakMilestones.isMilestone(days), "$days is a milestone")
            assertEquals("$days days. Still here, still going.", StreakMilestones.line(days))
        }
    }

    @Test
    fun `a hundred keeps its own line, not the generic one`() {
        assertEquals("100 days. Triple digits.", StreakMilestones.line(100))
    }

    @Test
    fun `days between the milestones are not milestones and have no line`() {
        listOf(0, 1, 2, 6, 8, 13, 15, 20, 22, 27, 29, 31, 49, 51, 59, 61, 89, 91, 99, 101, 199, 201, 364, 366, 399).forEach { days ->
            assertFalse(StreakMilestones.isMilestone(days), "$days is not a milestone")
            assertNull(StreakMilestones.line(days), "no line for $days")
        }
    }

    @Test
    fun `a negative count is never a milestone`() {
        assertFalse(StreakMilestones.isMilestone(-100))
        assertNull(StreakMilestones.line(-7))
    }

    @Test
    fun `the headline is the days then day streak`() {
        assertEquals("7-day streak", StreakMilestones.headline(7))
        assertEquals("365-day streak", StreakMilestones.headline(365))
    }

    // ---- what counts: the streak itself, so a frozen day does not -------------------------------------

    @Test
    fun `the seventh completed day in a row reaches seven`() {
        val streaks = StreakStore(InMemoryStore())
        (100L..105L).forEach { streaks.recordCompletedDay(wallet, it) }

        val after = StreakAfter.record(streaks, wallet, 106L)

        assertEquals(7, after.days)
        assertTrue(after.milestone)
    }

    @Test
    fun `a frozen day does not count toward a milestone`() {
        val streaks = StreakStore(InMemoryStore())
        // Seven completed days earn a freeze; day 107 is missed and covered by it, so day 108 is the 8th, not the 9th.
        (100L..106L).forEach { streaks.recordCompletedDay(wallet, it) }

        val after = StreakAfter.record(streaks, wallet, 108L)

        assertEquals(8, after.days, "the frozen day added nothing")
        assertFalse(after.milestone)
    }

    @Test
    fun `a freeze-covered gap still reaches fourteen on the fourteenth completed day`() {
        val streaks = StreakStore(InMemoryStore())
        // 100..106 are seven days; 107 is missed (frozen); 108..113 are six more: 13 in all.
        (100L..106L).forEach { streaks.recordCompletedDay(wallet, it) }
        (108L..113L).forEach { streaks.recordCompletedDay(wallet, it) }
        assertEquals(13, streaks.read(wallet, 113L).current)

        val after = StreakAfter.record(streaks, wallet, 114L)

        assertEquals(14, after.days)
        assertTrue(after.milestone)
    }

    @Test
    fun `a milestone day recorded again does not grow, so it is not shown again`() {
        val streaks = StreakStore(InMemoryStore())
        (100L..106L).forEach { streaks.recordCompletedDay(wallet, it) }

        val again = StreakAfter.record(streaks, wallet, 106L)

        assertEquals(7, again.days)
        assertFalse(again.grew)
        assertFalse(again.milestone)
    }

    @Test
    fun `Today shows the milestone for the day it was reached, and not the next`() {
        val streaks = StreakStore(InMemoryStore())
        (100L..106L).forEach { streaks.recordCompletedDay(wallet, it) }

        assertTrue(StreakMilestones.reachedToday(streaks.read(wallet, 106L)))
        assertFalse(StreakMilestones.reachedToday(streaks.read(wallet, 107L)), "next day, the plain chip")
    }

    @Test
    fun `Today shows nothing before the day is done`() {
        val streaks = StreakStore(InMemoryStore())
        (100L..105L).forEach { streaks.recordCompletedDay(wallet, it) }

        assertFalse(StreakMilestones.reachedToday(streaks.read(wallet, 106L)), "six so far, today not done")
    }
}
