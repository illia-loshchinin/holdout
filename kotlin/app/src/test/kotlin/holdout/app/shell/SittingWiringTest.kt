package holdout.app.shell

import holdout.app.nav.Nav
import holdout.app.nav.NavEvent
import holdout.app.nav.NavState
import holdout.app.nav.Screen
import holdout.app.nav.Sitting
import holdout.app.nav.Tab
import holdout.app.nav.then
import holdout.app.sitting.SittingViewModel
import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.testutil.ChainFixtures.DAY
import holdout.app.testutil.ChainFixtures.START
import holdout.app.testutil.ChainFixtures.holdout
import holdout.app.testutil.ChainFixtures.member
import holdout.core.Derive
import holdout.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a sitting ends and begins in the shell (wiring items 3, 5, 6; FR-024b, ES-1, ES-2). */
class SittingWiringTest {
    private val wallet = "Wallet1111111111111111111111111111111111111"
    private val h = holdout(1, START, member = member(2))
    private val day3Deadline = Derive.deadlineOf(h.cohort, 2)

    // ---- item 5: a day finished too late goes to the Forfeit ------------------------------------

    @Test
    fun `a day is too late once its own deadline has passed, and not a second before`() {
        assertFalse(tooLateToSend(h, 2, day3Deadline - 1))
        assertFalse(tooLateToSend(h, 2, day3Deadline), "the deadline second itself still takes a submit (now <= deadline on chain)")
        assertTrue(tooLateToSend(h, 2, day3Deadline + 1))
        assertFalse(tooLateToSend(h, 2, null))
    }

    @Test
    fun `an earlier day's deadline counts, not today's`() {
        assertTrue(tooLateToSend(h, 0, day3Deadline - 30))
    }

    @Test
    fun `a finished day that is too late ends the sitting on the Forfeit of that Holdout`() {
        val id = h.address.base58()
        val sitting = NavState().then(NavEvent.Connected(tourSeen = true), NavEvent.StartSitting(Sitting.HoldoutDay(id, 2)))
        val after = holdoutDayEndEvents(id, completed = true, tooLate = true).fold(sitting) { s, e -> Nav.reduce(s, e) }
        assertNull(after.sitting)
        assertEquals(Tab.Today, after.tab)
        assertEquals(Screen.Forfeit(id), after.top)
    }

    @Test
    fun `a day sent in time, or a sitting stopped part-way, lands on Today's root`() {
        val id = h.address.base58()
        val sitting = NavState().then(NavEvent.Connected(tourSeen = true), NavEvent.StartSitting(Sitting.HoldoutDay(id, 2)))
        for ((completed, late) in listOf(true to false, false to false, false to true)) {
            val after = holdoutDayEndEvents(id, completed, late).fold(sitting) { s, e -> Nav.reduce(s, e) }
            assertNull(after.sitting)
            assertNull(after.top, "completed=$completed late=$late")
        }
    }

    // ---- item 3: the day is already on chain ----------------------------------------------------

    @Test
    fun `a day is on chain once the member's days completed pass it`() {
        assertTrue(dayOnChain(holdout(1, START, member = member(3)), 2))
        assertFalse(dayOnChain(holdout(1, START, member = member(2)), 2))
        assertFalse(dayOnChain(holdout(1, START), 0))
        assertFalse(dayOnChain(null, 0))
    }

    // ---- item 3b: the finished page asks the chain before it says it can't be reached ------------

    @Test
    fun `a finished day over a failed read asks the chain again before the page says it is unreachable`() {
        assertTrue(rechecksAtFinish(SittingViewModel.Step.COMPLETE, offline = true))
    }

    @Test
    fun `nothing is asked while the sitting runs, or when the last read answered`() {
        for (step in listOf(SittingViewModel.Step.PREFLIGHT, SittingViewModel.Step.COUNTDOWN, SittingViewModel.Step.COUNTING)) {
            assertFalse(rechecksAtFinish(step, offline = true), "$step")
        }
        assertFalse(rechecksAtFinish(SittingViewModel.Step.COMPLETE, offline = false))
    }

    // ---- item 6: the crash variant on the next open ---------------------------------------------

    private val running: Pair<holdout.app.chain.Holdout, Stage.Active> = h to Stage.Active(dayIndex = 2, todayDone = false)
    private val chainNow = h.cohort.start + 2 * DAY + 3_600

    @Test
    fun `a Holdout sitting that was killed after a banked block says what was kept`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, cohortScope(h), 1L)
        store.setBankedReps(wallet, cohortScope(h), 2, 10)
        val found = interruptedSittings(store, wallet, Store.UNKNOWN_DAY, chainNow, running)
        assertEquals(1, found.size)
        val ui = found.single()
        assertTrue(ui.crash)
        assertEquals(10, ui.banked)
        assertEquals(25, ui.reps)
        assertEquals("day 3 · 25 reps", ui.header)
        assertEquals(Derive.timeLeftToday(h.cohort, chainNow), ui.secondsLeft)
        // Said once: the marker is gone.
        assertTrue(interruptedSittings(store, wallet, Store.UNKNOWN_DAY, chainNow, running).isEmpty())
    }

    @Test
    fun `a Daily 25 killed after a banked block says it too, under today's day`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)
        store.setBankedReps(wallet, Store.CHALLENGE, 500L, 20)
        val ui = interruptedSittings(store, wallet, 500L, chainNow, null).single()
        assertEquals(20, ui.banked)
        assertEquals("the daily 25", ui.header)
        assertEquals(25, ui.reps)
    }

    @Test
    fun `both sittings killed give two pages, the Holdout's first`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, cohortScope(h), 1L)
        store.setBankedReps(wallet, cohortScope(h), 2, 10)
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)
        store.setBankedReps(wallet, Store.CHALLENGE, 500L, 20)
        assertEquals(listOf("day 3 · 25 reps", "the daily 25"), interruptedSittings(store, wallet, 500L, chainNow, running).map { it.header })
    }

    @Test
    fun `nothing banked, a clean exit or an unknown day says nothing`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, cohortScope(h), 1L)
        assertTrue(interruptedSittings(store, wallet, 500L, chainNow, running).isEmpty())
        assertNull(store.sittingStartedAt(wallet, cohortScope(h)), "the marker is cleared even with nothing to say")
        assertTrue(interruptedSittings(InMemoryStore(), wallet, 500L, chainNow, running).isEmpty())
        val marked = InMemoryStore()
        marked.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)
        marked.setBankedReps(wallet, Store.CHALLENGE, 500L, 20)
        assertTrue(interruptedSittings(marked, wallet, Store.UNKNOWN_DAY, chainNow, null).isEmpty(), "the day is not known yet, so the marker waits")
        assertEquals(1L, marked.sittingStartedAt(wallet, Store.CHALLENGE))
    }
}
