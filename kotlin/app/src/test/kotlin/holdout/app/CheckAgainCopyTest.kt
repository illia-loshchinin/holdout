package holdout.app

import holdout.app.holdout.CheckAgainCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The words of "Check again" name the network and never call the member offline (spec 005 FR-007, SC-004). */
class CheckAgainCopyTest {
    private val network = "Devnet"
    private val all = listOf(
        CheckAgainCopy.BUTTON,
        CheckAgainCopy.checking(network),
        CheckAgainCopy.stillUnreachable(network),
        CheckAgainCopy.unreachable(network),
    )

    @Test
    fun `the lines read as the contract says`() {
        assertEquals("Check again", CheckAgainCopy.BUTTON)
        assertEquals("Checking Devnet…", CheckAgainCopy.checking(network))
        assertEquals("Devnet still can't be reached.", CheckAgainCopy.stillUnreachable(network))
        assertEquals("Devnet can't be reached right now.", CheckAgainCopy.unreachable(network))
    }

    @Test
    fun `every line about the chain names the network`() {
        all.drop(1).forEach { assertTrue(network in it, it) }
    }

    @Test
    fun `no line says offline, internet or error`() {
        all.forEach { line ->
            listOf("offline", "internet", "error").forEach { assertFalse(it in line.lowercase(), line) }
        }
    }
}
