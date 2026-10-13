package holdout.app.home

import holdout.app.nav.NavEvent
import holdout.app.nav.Sitting
import holdout.app.testutil.ChainFixtures.DAY
import holdout.app.testutil.ChainFixtures.START
import holdout.app.testutil.ChainFixtures.holdout
import holdout.app.testutil.ChainFixtures.member
import holdout.core.LeagueParams
import holdout.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The member's day in a running Holdout, one model for Today and Standing's strip (FR-024b, FR-038, FR-047). */
class DayModelTest {
    private val habit = holdout(1, START, member(11), form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D).cohort

    /** Day 12 (index 11) with 4h 06m to its deadline. */
    private val now = START + 12 * DAY - 14_760 - 1

    private fun day(
        banked: Int = 0,
        done: Boolean = false,
        pending: Int? = null,
        tried: Boolean = false,
        offline: Boolean = false,
        busy: Boolean = false,
        fees: Boolean = true,
        at: Long = now,
        index: Int = 11,
        cohort: holdout.core.CohortSnapshot = habit,
        mayStillLand: Boolean = false,
    ) = DayState.of(cohort, Stage.Active(index, done), at, banked, pending, tried, offline, busy, fees, mayStillLand)

    @Test
    fun `nothing banked - the target, how to do it, and Start today's 25 with the deadline`() {
        val d = day()
        assertEquals(DayPhase.ToDo, d.phase)
        assertEquals("25 push-ups", d.title)
        assertEquals("all at once or 10 at a time", d.text)
        assertEquals(DayButton("Start today's 25", DayAction.Start), d.button)
        assertEquals(14_760L, d.timeLeft)
        assertEquals(12, d.day)
        assertEquals(11, d.dayIndex)
    }

    @Test
    fun `part-way - what is banked, what is left and Keep going`() {
        val d = day(banked = 10)
        assertEquals("10 of 25 banked", d.title)
        assertEquals("15 to go", d.text)
        assertEquals(DayButton("Keep going", DayAction.Start), d.button)
        assertEquals(15, d.owed)
    }

    @Test
    fun `the last 10 minutes warn, say unfinished reps won't count, and still offer Keep going`() {
        val d = day(banked = 10, at = START + 12 * DAY - 1 - 510)
        assertTrue(d.warning)
        assertEquals(DayTone.Warning, d.tone)
        assertEquals("15 push-ups still needed", d.title)
        assertEquals("Under 10 minutes left. Unfinished reps won't count.", d.text)
        assertEquals(DayAction.Start, d.button?.action)
        assertTrue(d.button!!.enabled)
    }

    @Test
    fun `done and offline - waiting for network, with no button`() {
        val d = day(pending = 11, offline = true)
        assertEquals(DayPhase.Unsent, d.phase)
        assertEquals("Done — waiting for network", d.title)
        assertNull(d.button)
        assertTrue(d.timeLeft != null)
    }

    @Test
    fun `done online and the send under way - the wallet is out`() {
        val d = day(pending = 11, busy = true)
        assertEquals("Done — sending to the chain", d.title)
        assertEquals(DayButton("Waiting for your wallet…", DayAction.None, busy = true), d.button)
    }

    @Test
    fun `done and the send did not land - tap to submit day 12`() {
        val d = day(pending = 11, tried = true)
        assertEquals("Done — tap to submit", d.title)
        assertEquals(DayButton("Submit day 12", DayAction.Submit), d.button)
    }

    @Test
    fun `done and a send that may still land - Submit is off and the day says why, on Today and Standing alike`() {
        val d = day(pending = 11, tried = true, mayStillLand = true)
        assertEquals(DayPhase.Unsent, d.phase)
        assertEquals("Done — sent, not confirmed yet", d.title)
        assertEquals("Day 12 may still land. Sending opens again once devnet answers.", d.text)
        assertEquals(DayButton("Submit day 12", DayAction.Submit, enabled = false), d.button)
    }

    @Test
    fun `done but short of SOL - Submit is off`() {
        val d = day(pending = 11, fees = false)
        assertEquals(DayPhase.ShortOfSol, d.phase)
        assertEquals("Today's 25 are done", d.title)
        assertEquals(DayButton("Submit day 12", DayAction.Submit, enabled = false), d.button)
    }

    @Test
    fun `a day sent in an earlier day does not make today waiting`() {
        assertEquals(DayPhase.ToDo, day(pending = 10).phase)
    }

    @Test
    fun `on chain - no deadline countdown, no button, and the next day opens in a countdown`() {
        val d = day(done = true)
        assertEquals(DayPhase.OnChain, d.phase)
        assertEquals("Today is done and on chain.", d.title)
        assertEquals("Day 13 opens in 4h 06m.", d.text)
        assertNull(d.timeLeft)
        assertNull(d.button)
        assertFalse(d.lastDay)
    }

    @Test
    fun `on chain inside the last 10 minutes still shows no warning`() {
        val d = day(done = true, at = START + 12 * DAY - 1 - 510)
        assertFalse(d.warning)
        assertNull(d.timeLeft)
        assertEquals("Day 13 opens in 8m 30s.", d.text)
    }

    @Test
    fun `the last day on chain never says the next day opens`() {
        val d = day(done = true, index = 20, at = START + 21 * DAY - 14_760 - 1)
        assertTrue(d.lastDay)
        assertEquals(DayState.LAST_DAY, d.text)
        assertEquals("That was the last day. It settles soon after the end, then your claim is ready.", d.text)
        assertFalse(d.text!!.contains("opens in"))
        assertNull(d.timeLeft)
    }

    @Test
    fun `the last day still to do is a day like any other`() {
        val d = day(index = 20, at = START + 21 * DAY - 14_760 - 1)
        assertTrue(d.lastDay)
        assertEquals(DayPhase.ToDo, d.phase)
        assertEquals("Start today's 25", d.button?.label)
    }

    @Test
    fun `the Holdout's own target names the day`() {
        val d = day(cohort = habit.copy(repsPerDay = 5))
        assertEquals("5 push-ups", d.title)
        assertEquals("Start today's 5", d.button?.label)
        assertEquals(5, d.target)
    }

    @Test
    fun `Start from either screen opens this Holdout's day sitting`() {
        val h = holdout(1, START, member(11), form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D)
        assertEquals(NavEvent.StartSitting(Sitting.HoldoutDay(h.address.base58(), 11)), DayState.start(h, 11))
    }

    @Test
    fun `a form or an exercise this build does not know is refused, not guessed`() {
        val h = holdout(1, START, member(11), form = 99, days = 21, leagueId = LeagueParams.LEAGUE_21D)
        assertNull(DayState.start(h, 11))
        val other = h.copy(cohort = h.cohort.copy(form = LeagueParams.FORM_TOES, exercise = LeagueParams.EXERCISE_PUSHUP + 1))
        assertNull(DayState.start(other, 11))
    }
}
