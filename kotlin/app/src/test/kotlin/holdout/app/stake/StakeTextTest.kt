package holdout.app.stake

import holdout.core.CohortSnapshot
import holdout.core.Journey
import holdout.core.LeagueParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The stake confirm's sentences (FR-041, ES-12, rulings G2, G5): worded once, for each level. */
class StakeTextTest {
    private val skr = 1_000_000uL
    private val day = 86_400L

    private fun cohort(form: Int, days: Int, leagueId: Int = 0, reps: Int = 25, dayLength: Long = day, joinLock: Long = 3_600L) = CohortSnapshot(leagueId, 1, 1_791_176_400L, 1_791_176_400L - joinLock, days, reps, dayLength, 0, listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr), form = form)

    private val spark = cohort(LeagueParams.FORM_KNEES_ALLOWED, 7)
    private val habit = cohort(LeagueParams.FORM_TOES, 21)

    @Test
    fun `the title joins the level by name`() {
        assertEquals("Join the Spark · 7-day Holdout", StakeText.title(spark, test = false))
        assertEquals("Join the Habit · 21-day Holdout", StakeText.title(habit, test = false))
        assertEquals("Join the Hackathon · Spark Holdout", StakeText.title(spark, test = true))
        assertEquals("Join the Hackathon · Habit Holdout", StakeText.title(habit, test = true))
    }

    @Test
    fun `the level line leads with the tagline's first words`() {
        assertEquals("Light it up: 25 push-ups a day for 7 days.", StakeText.levelLine(spark, false))
        assertEquals("Make it stick: 25 push-ups a day for 21 days.", StakeText.levelLine(habit, false))
        assertEquals("A quick taste: 5 push-ups a day for 3 days. Joinable right up to its start.", StakeText.levelLine(cohort(LeagueParams.FORM_KNEES_ALLOWED, 3, reps = 5, joinLock = 0), true))
    }

    @Test
    fun `a test Holdout with a join lock does not claim to be joinable up to its start`() {
        assertEquals("A quick taste: 5 push-ups a day for 3 days.", StakeText.levelLine(cohort(LeagueParams.FORM_KNEES_ALLOWED, 3, reps = 5, joinLock = 60), true))
    }

    @Test
    fun `the sheet's one line on losing is pinned word for word, with no split in it`() {
        assertEquals("Miss a day and you lose your stake.", StakeText.MISS_A_DAY)
        assertEquals("How it works", StakeText.HOW_IT_WORKS)
        assertFalse("%" in StakeText.MISS_A_DAY)
        assertFalse("nobody" in StakeText.MISS_A_DAY)
    }

    @Test
    fun `the prize boost line shows only when the reserve holds more than nothing`() {
        assertEquals("Prize boost: up to 480 SKR", StakeText.boost(480uL * skr))
        assertEquals("Prize boost: up to 1,200 SKR", StakeText.boost(1_200uL * skr))
        assertNull(StakeText.boost(0uL))
        assertNull(StakeText.boost(null))
    }

    @Test
    fun `the closing warning shows in the last ten minutes only`() {
        assertEquals("A transaction signed now may land too late.", StakeText.closingWarning(510))
        assertEquals("A transaction signed now may land too late.", StakeText.closingWarning(600))
        assertNull(StakeText.closingWarning(601))
        assertNull(StakeText.closingWarning(0))
        assertNull(StakeText.closingWarning(2 * day))
    }

    @Test
    fun `each blocker has its own sentence`() {
        assertEquals("Joining has closed for this Holdout.", StakeText.blocker(Journey.JoinBlocker.JoiningClosed))
        assertEquals("You already hold a place in a Holdout that hasn't started.", StakeText.blocker(Journey.JoinBlocker.LobbyPlaceTaken))
        assertEquals("This Holdout starts before your current one ends.", StakeText.blocker(Journey.JoinBlocker.Overlaps))
    }

    @Test
    fun `the comeback line is exact, in the member's words`() {
        assertEquals("Finish it and 25 SKR of the stake you lost comes back.", StakeText.comeback(25uL * skr))
    }

    @Test
    fun `the lines every join carries are the design book's, word for word`() {
        assertEquals("If nobody drops out, you get exactly your stake back.", StakeText.NO_DROPOUT)
        assertEquals("Test network, test token. Dollar values are approximate.", StakeText.FOOTER)
        assertEquals("Full refund until joining closes.", StakeText.FULL_REFUND)
    }

    @Test
    fun `none of the stake sheet's words is banned in member copy`() {
        val words = listOf(StakeText.NO_DROPOUT, StakeText.FULL_REFUND, StakeText.NOTIFICATION_REASON, StakeText.FOOTER, StakeText.MISS_A_DAY, StakeText.HOW_IT_WORKS, StakeText.boost(480uL * skr).orEmpty(), StakeText.SHORT_OF_SOL)
        words.forEach { w -> listOf("earn", "challenge", "cohort", "league", "Easy", "Hard").forEach { assertFalse(it in w, "$it in $w") } }
    }
}
