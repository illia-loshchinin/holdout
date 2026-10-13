package holdout.app

import holdout.app.holdout.CheckAgainCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The words of the devnet bar's "Retry" (001 FR-069h, 2026-10-14; was "Check again") name the network and never call the member offline (spec 005 FR-007, SC-004). */
class CheckAgainCopyTest {
    private val network = "Devnet"
    private val all = listOf(
        CheckAgainCopy.BUTTON,
        CheckAgainCopy.CHECKING,
        CheckAgainCopy.stillUnreachable(network),
        CheckAgainCopy.unreachable(network),
    )

    @Test
    fun `the lines read as the contract says`() {
        assertEquals("Retry", CheckAgainCopy.BUTTON)
        assertEquals("Checking…", CheckAgainCopy.CHECKING)
        assertEquals("Devnet still isn't answering", CheckAgainCopy.stillUnreachable(network))
        assertEquals("Devnet isn't answering", CheckAgainCopy.unreachable(network))
    }

    @Test
    fun `a local build's network keeps its own name`() {
        val local = "The local validator"
        assertEquals("The local validator isn't answering", CheckAgainCopy.unreachable(local))
        assertEquals("The local validator still isn't answering", CheckAgainCopy.stillUnreachable(local))
    }

    @Test
    fun `every line about the chain names the network`() {
        all.drop(2).forEach { assertTrue(network in it, it) }
    }

    @Test
    fun `no line says offline, internet or error`() {
        all.forEach { line ->
            listOf("offline", "internet", "error").forEach { assertFalse(it in line.lowercase(), line) }
        }
    }
}
