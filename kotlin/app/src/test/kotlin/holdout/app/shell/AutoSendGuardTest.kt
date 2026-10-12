package holdout.app.shell

import holdout.app.testutil.ChainFixtures.view
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A day waiting for the network sends itself only when the wallet can pay the
 * fee: Today shows "short of SOL" (CohortHome) and the wallet must not open
 * on its own with nothing to pay with (wiring item 8).
 */
class AutoSendGuardTest {
    private val enough = 5_000_000L

    @Test
    fun `a wallet with the fee in SOL may be sent to`() {
        assertTrue(canPayFees(view(lamports = enough)))
        assertTrue(canPayFees(view(lamports = enough + 1)))
    }

    @Test
    fun `a wallet short of SOL is never opened by the automatic send`() {
        assertFalse(canPayFees(view(lamports = enough - 1)))
        assertFalse(canPayFees(view(lamports = 0L)))
    }

    @Test
    fun `before the chain has answered nothing says it is short`() {
        assertTrue(canPayFees(null))
    }
}
