package holdout.app

import holdout.app.holdout.ChainCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A member's "Check again" while the chain can't be reached (spec 005, data-model.md). */
class ChainCheckTest {
    private val idle = ChainCheck()
    private val checking = ChainCheck(checking = true)
    private val still = ChainCheck(stillUnreachable = true)

    @Test
    fun `given the chain answers, when the member asks, then nothing is read`() {
        for (from in listOf(idle, checking, still)) {
            val step = from.request(offline = false)
            assertFalse(step.read)
            assertEquals(from, step.next)
        }
    }

    @Test
    fun `given a check under way, when the member asks again, then it joins and reads nothing more`() {
        val step = checking.request(offline = true)
        assertFalse(step.read)
        assertEquals(checking, step.next)
    }

    @Test
    fun `given no check under way and the chain unreachable, when the member asks, then a read starts`() {
        val fromIdle = idle.request(offline = true)
        assertTrue(fromIdle.read)
        assertTrue(fromIdle.next.checking)

        // The state keeps "still can't be reached" through the next check; the screen hides its line while that check is out.
        val fromStill = still.request(offline = true)
        assertTrue(fromStill.read)
        assertEquals(ChainCheck(checking = true, stillUnreachable = true), fromStill.next)
    }

    @Test
    fun `given a check under way, when it ends unanswered, then the chain is still unreachable`() {
        assertEquals(still, checking.finished(answered = false))
    }

    @Test
    fun `given a check under way, when the chain answers it, then nothing is left to show`() {
        assertEquals(idle, checking.finished(answered = true))
    }

    @Test
    fun `given any check state, when any read answers, then nothing is left to show`() {
        for (from in listOf(idle, checking, still, ChainCheck(checking = true, stillUnreachable = true))) {
            assertEquals(idle, from.answered())
        }
    }
}
