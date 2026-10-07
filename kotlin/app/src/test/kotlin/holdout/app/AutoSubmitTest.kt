package holdout.app

import holdout.app.holdout.AutoSubmit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
