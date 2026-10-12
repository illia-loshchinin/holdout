package holdout.app.sitting

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.repcounter.Stakes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The words of the sitting path (boards 15-20, 6; rulings C-D2, G2, G3; 002 FR-014,
 * FR-015, FR-047). Pure strings, no Compose.
 */
class SittingCopyTest {
    @Test
    fun `a Holdout day is named by its day and reps, the Daily 25 by its name`() {
        assertEquals("day 12 · 25 reps", SittingCopy.countingLabel(dayNumber = 12, target = 25))
        assertEquals("the daily 25", SittingCopy.countingLabel(dayNumber = null, target = 25))
        assertEquals("day 3 · 5 reps", SittingCopy.countingLabel(dayNumber = 3, target = 5))
    }

    @Test
    fun `getting into position says so in the label`() {
        assertEquals("day 12 · get into position", SittingCopy.positionLabel(dayNumber = 12))
        assertEquals("the daily 25 · get into position", SittingCopy.positionLabel(dayNumber = null))
    }

    @Test
    fun `the time line is a countdown to the deadline for a Holdout and what is left today for the Daily 25`() {
        val fourHours = 4 * 3_600L + 6 * 60
        assertEquals("4h 06m to today's deadline", SittingCopy.timeLine(fourHours, holdoutDay = true))
        assertEquals("4h 06m left today", SittingCopy.timeLine(fourHours, holdoutDay = false))
        assertEquals("9m 30s to today's deadline", SittingCopy.timeLine(9 * 60L + 30, holdoutDay = true))
    }

    @Test
    fun `the counting screen's time reads left`() {
        assertEquals("4h 06m left", SittingCopy.timeLeft(4 * 3_600L + 6 * 60))
    }

    @Test
    fun `no time line shows a calendar date or a clock time`() {
        val lines = listOf(
            SittingCopy.timeLine(86_400L * 3, holdoutDay = true),
            SittingCopy.timeLine(40_000L, holdoutDay = false),
            SittingCopy.timeLeft(40_000L),
        )
        lines.forEach { assertFalse(Regex("""\d{1,2}:\d{2}|\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b""").containsMatchIn(it), it) }
    }

    @Test
    fun `the last ten minutes carry FR-047's sentence`() {
        assertTrue(SittingCopy.LAST_MINUTES.contains("Unfinished reps won't count"))
        assertTrue(SittingCopy.LAST_MINUTES.startsWith("Under 10 minutes left"))
    }

    @Test
    fun `the guide names the level beside its title`() {
        assertEquals("habit · 21-day", SittingCopy.levelLabel(Stakes.STAKED))
        assertEquals("spark · 7-day", SittingCopy.levelLabel(Stakes.EASY))
        assertEquals("the daily 25", SittingCopy.levelLabel(Stakes.DAILY))
    }

    @Test
    fun `a Holdout day's guide label reads the length from its cohort, not from the stakes`() {
        val chips = listOf(1uL, 2uL, 3uL, 4uL)
        val habit3 = CohortSnapshot(100, 0, 0, 0, 3, 5, 600, 0, chips, form = LeagueParams.FORM_TOES)
        val spark3 = CohortSnapshot(101, 0, 0, 0, 3, 5, 600, 0, chips, form = LeagueParams.FORM_KNEES_ALLOWED)
        assertEquals("habit · 3-day", SittingCopy.levelLabel(habit3))
        assertEquals("spark · 3-day", SittingCopy.levelLabel(spark3))
        // The same casing as the stakes-keyed label of the regular lengths.
        val habit21 = habit3.copy(durationDays = 21)
        assertEquals(SittingCopy.levelLabel(Stakes.STAKED), SittingCopy.levelLabel(habit21))
    }

    @Test
    fun `a Holdout sitting shows its own level's rule sentence and never the other's`() {
        assertEquals(Stakes.STAKED_RULE, SittingCopy.ruleCaption(Stakes.STAKED))
        assertEquals(Stakes.EASY_RULE, SittingCopy.ruleCaption(Stakes.EASY))
        assertNotEquals(SittingCopy.ruleCaption(Stakes.STAKED), SittingCopy.ruleCaption(Stakes.EASY))
        // The sentences are the design book's, word for word (002 FR-014).
        assertEquals("At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count.", SittingCopy.ruleCaption(Stakes.EASY))
        assertEquals(
            "At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count.",
            SittingCopy.ruleCaption(Stakes.STAKED),
        )
    }

    @Test
    fun `the Daily 25 is counted as the Spark and shows no level sentence`() {
        assertEquals("counted as the Spark", SittingCopy.ruleCaption(Stakes.DAILY))
        assertFalse(SittingCopy.ruleCaption(Stakes.DAILY).contains("At 45°"))
    }

    @Test
    fun `the rule sentences come from the one place Format reads them`() {
        assertEquals(Stakes.EASY_RULE, Format.levelRule(1))
        assertEquals(Stakes.STAKED_RULE, Format.levelRule(0))
    }

    @Test
    fun `no sitting string uses the code's words for the levels`() {
        val all = listOf(
            SittingCopy.levelLabel(Stakes.STAKED),
            SittingCopy.levelLabel(Stakes.EASY),
            SittingCopy.ruleCaption(Stakes.STAKED),
            SittingCopy.ruleCaption(Stakes.EASY),
            SittingCopy.ruleCaption(Stakes.DAILY),
            SittingCopy.countingLabel(3, 25),
        )
        all.forEach { assertFalse(Regex("""\b(Easy|Hard|Staked|cohort|league|challenge)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it), it) }
    }
}
