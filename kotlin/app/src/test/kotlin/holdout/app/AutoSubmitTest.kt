package holdout.app

import holdout.app.holdout.AutoSubmit
import holdout.app.sitting.SittingViewModel
import holdout.app.storage.InMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A completed day waiting for the network sends itself once the chain answers (FR-024b Phase 2, ES-3b, T098). */
class AutoSubmitTest {
    private val cohort = "Cohort1111111111111111111111111111111111111"

    /** Runs [states] through the gate in order, as the home screen does, and returns the days it sent. */
    private fun run(vararg states: State): List<Int> {
        var gate = AutoSubmit()
        val sent = mutableListOf<Int>()
        for (s in states) {
            val step = gate.step(s.cohort, s.pendingDay, s.todayDone, s.offline, s.busy)
            gate = step.next
            if (step.send) sent += s.pendingDay!!
        }
        return sent
    }

    private data class State(
        val pendingDay: Int?,
        val offline: Boolean = false,
        val busy: Boolean = false,
        val todayDone: Boolean = false,
        val cohort: String = "Cohort1111111111111111111111111111111111111",
    )

    @Test
    fun `given a day completed offline, when the chain answers again, then it is sent once by itself`() {
        assertEquals(listOf(3), run(State(3, offline = true), State(3, offline = true), State(3), State(3)))
    }

    @Test
    fun `given a declined send, when the network drops and returns between two chain reads, then the wallet opens once more`() {
        val tried = AutoSubmit().step(cohort, 3, todayDone = false, offline = false, busy = false)
        assertTrue(tried.send)
        // Declined: the chain still shows day 3 pending, and no read ever saw offline.
        assertFalse(tried.next.step(cohort, 3, todayDone = false, offline = false, busy = false).send)
        // The system reports the loss and the chain answers again: with the day still pending it sends again.
        val back = tried.next.lost().answered().step(cohort, 3, todayDone = false, offline = false, busy = false)
        assertTrue(back.send)
        assertFalse(back.next.step(cohort, 3, todayDone = false, offline = false, busy = false).send, "and only once")
    }

    @Test
    fun `given a declined send, when the network is lost and has not come back, then nothing is sent yet`() {
        val declined = AutoSubmit().step(cohort, 3, todayDone = false, offline = false, busy = false).next
        // The last read still says online: the loss alone must not send into no network.
        val lost = declined.lost()
        val down = lost.step(cohort, 3, todayDone = false, offline = false, busy = false)
        assertFalse(down.send)
        assertTrue(down.next.tried(cohort, 3), "the tap stays the way while the network is down")
        // The chain answers again: now it goes, once.
        val back = down.next.answered().step(cohort, 3, todayDone = false, offline = false, busy = false)
        assertTrue(back.send)
        assertFalse(back.next.answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send, "and only once")
    }

    @Test
    fun `given a declined send, when the chain answers with no loss in between, then the wallet is not opened again`() {
        val declined = AutoSubmit().step(cohort, 3, todayDone = false, offline = false, busy = false).next
        assertFalse(declined.answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send)
    }

    @Test
    fun `given a lost network, when a send starts before the chain answers, then the answer still re-arms it`() {
        // The send went out with no network under it and failed: the return must try again.
        val sent = AutoSubmit().lost().sent(cohort, 3)
        assertTrue(sent.tried(cohort, 3))
        assertTrue(sent.answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send)
    }

    @Test
    fun `given a send that never reached the wallet, when the chain answers again, then it is sent again by itself, once`() {
        // Devnet did not answer before the wallet could open: the member declined nothing.
        val unreached = AutoSubmit().step(cohort, 3, todayDone = false, offline = false, busy = false).next.unreached(cohort, 3)
        // Not at once — that would only ask devnet again straight away — and the tap stays the way meanwhile.
        assertFalse(unreached.step(cohort, 3, todayDone = false, offline = false, busy = false).send)
        assertTrue(unreached.tried(cohort, 3))
        val back = unreached.answered().step(cohort, 3, todayDone = false, offline = false, busy = false)
        assertTrue(back.send)
        assertFalse(back.next.answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send, "and only once")
    }

    @Test
    fun `given a send of another day that never reached the wallet, when the chain answers, then the day tried is still held back`() {
        val declined = AutoSubmit().sent(cohort, 3).unreached(cohort, 2)
        assertFalse(declined.answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send)
    }

    @Test
    fun `given no connection, when a day is waiting, then nothing is sent`() {
        assertEquals(emptyList(), run(State(3, offline = true), State(3, offline = true)))
    }

    @Test
    fun `given the wallet declined the automatic send, when nothing else changes, then the wallet is not opened again`() {
        // Sent, the wallet busy, then back with the day still pending: the tap is the way left.
        assertEquals(listOf(3), run(State(3), State(3, busy = true), State(3), State(3)))
        val gate = AutoSubmit().step(cohort, 3, todayDone = false, offline = false, busy = false).next
        assertTrue(gate.tried(cohort, 3))
    }

    @Test
    fun `given an automatic send that did not land, when the connection drops and returns, then it is sent again`() {
        assertEquals(listOf(3, 3), run(State(3), State(3, offline = true), State(3)))
    }

    @Test
    fun `given another action holds the wallet, when it finishes, then the waiting day is sent`() {
        assertEquals(listOf(3), run(State(3, busy = true), State(3, busy = true), State(3)))
    }

    @Test
    fun `given the day is already on chain, when the chain answers, then nothing is sent`() {
        assertEquals(emptyList(), run(State(3, todayDone = true), State(null)))
    }

    @Test
    fun `given a day sent by itself, when the next day is waiting, then that day is sent too`() {
        assertEquals(listOf(3, 4), run(State(3), State(null), State(4)))
    }

    @Test
    fun `given a day sent from the sitting's own exit, when it has landed but the chain does not show it yet, then it is not sent again`() {
        val gate = AutoSubmit().sent(cohort, 3)
        assertFalse(gate.step(cohort, 3, todayDone = false, offline = false, busy = false).send)
    }

    @Test
    fun `given a fresh gate, when nothing was sent yet, then the tap is not offered`() {
        assertFalse(AutoSubmit().tried(cohort, 3))
    }

    // ---- across a restart of the app ------------------------------------------

    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val store = InMemoryStore()

    /** What the app holds after it is killed and started again: only what the store kept. */
    private fun restarted() = AutoSubmit.restore(store, wallet)

    @Test
    fun `given a declined send, when the app restarts with the day still pending, then the wallet is not opened again`() {
        store.setPendingCohortDay(wallet, cohort, 3)
        AutoSubmit().step(cohort, 3, todayDone = false, offline = false, busy = false).next.saveTo(store, wallet)
        // The new process's first read answers: no loss in between, so the tap is the way left.
        val back = restarted().answered()
        assertTrue(back.tried(cohort, 3))
        assertFalse(back.step(cohort, 3, todayDone = false, offline = false, busy = false).send)
    }

    @Test
    fun `given a declined send and a lost network, when the app restarts and the chain answers, then it is sent once and the mark is gone`() {
        store.setPendingCohortDay(wallet, cohort, 3)
        AutoSubmit().sent(cohort, 3).lost().saveTo(store, wallet)
        val back = restarted().answered()
        // Re-armed, and nothing kept: a restart now would not hold it back either.
        back.saveTo(store, wallet)
        assertNull(store.autoSubmitMark(wallet))
        val step = back.step(cohort, 3, todayDone = false, offline = false, busy = false)
        assertTrue(step.send)
        step.next.saveTo(store, wallet)
        assertFalse(restarted().answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send, "and only once")
    }

    @Test
    fun `given a send that never reached the wallet, when the app restarts, then the chain's first answer sends it`() {
        store.setPendingCohortDay(wallet, cohort, 3)
        AutoSubmit().sent(cohort, 3).unreached(cohort, 3).saveTo(store, wallet)
        assertTrue(restarted().answered().step(cohort, 3, todayDone = false, offline = false, busy = false).send)
    }

    @Test
    fun `given a sent day, when it is no longer pending, then a restart keeps nothing`() {
        store.setPendingCohortDay(wallet, cohort, 3)
        val sent = AutoSubmit().sent(cohort, 3)
        sent.saveTo(store, wallet)
        // Landed: the pending day is cleared, and the mark with it.
        store.setPendingCohortDay(wallet, cohort, null)
        sent.saveTo(store, wallet)
        assertNull(store.autoSubmitMark(wallet))
        assertFalse(restarted().tried(cohort, 3))
    }

    @Test
    fun `given a kept mark, when its day is no longer the one pending, then a restart ignores it`() {
        store.setPendingCohortDay(wallet, cohort, 3)
        AutoSubmit().sent(cohort, 3).saveTo(store, wallet)
        // The next day is waiting now: it is sent by itself once more.
        store.setPendingCohortDay(wallet, cohort, 4)
        assertTrue(restarted().step(cohort, 4, todayDone = false, offline = false, busy = false).send)
        assertFalse(restarted().tried(cohort, 3))
    }

    @Test
    fun `given a declined send, when a chain read fails, then the kept mark is cleared too`() {
        store.setPendingCohortDay(wallet, cohort, 3)
        val declined = AutoSubmit().sent(cohort, 3)
        declined.saveTo(store, wallet)
        declined.step(cohort, 3, todayDone = false, offline = true, busy = false).next.saveTo(store, wallet)
        assertNull(store.autoSubmitMark(wallet))
    }

    @Test
    fun `given two wallets on one phone, when one declined, then the other's gate is untouched`() {
        val other = "Other11111111111111111111111111111111111111"
        store.setPendingCohortDay(wallet, cohort, 3)
        store.setPendingCohortDay(other, cohort, 3)
        AutoSubmit().sent(cohort, 3).saveTo(store, wallet)
        assertFalse(AutoSubmit.restore(store, other).tried(cohort, 3))
    }

    // ---- during a Daily 25 sitting ----------------------------------------------

    @Test
    fun `given a Daily 25 sitting, when it is mid-set, then the wallet waits, and otherwise it may open over it`() {
        // Leaving for the wallet mid-set would cost the set's loose reps (onLeftForeground).
        assertTrue(SittingViewModel.Step.COUNTDOWN.inSet)
        assertTrue(SittingViewModel.Step.COUNTING.inSet)
        assertFalse(SittingViewModel.Step.PREFLIGHT.inSet)
        assertFalse(SittingViewModel.Step.COMPLETE.inSet)
    }
}
