package holdout.app.wallet

import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a signature became, as the signing screens show it (rulings G7, C-D14):
 * the wallet's answer read from Mobile Wallet Adapter's own error codes
 * (`ProtocolContract.ERROR_*`), then one outcome per flow.
 */
class SigningTest {
    // ---- the wallet's answer ---------------------------------------------------------------------

    @Test
    fun `the member said no in the wallet - declined, nothing sent`() {
        val f = WalletFailures.of("User did not authorize signing", remote(NOT_SIGNED))
        assertTrue(f.declined)
        assertFalse(f.mayStillLand)
    }

    @Test
    fun `the member refused to authorize Holdout - declined too`() {
        assertTrue(WalletFailures.of("Auth token invalid", remote(AUTHORIZATION_FAILED)).declined)
    }

    @Test
    fun `signed but not submitted by the wallet - not declined, and it may still land`() {
        val f = WalletFailures.of("Not all transactions were submitted", remote(NOT_SUBMITTED))
        assertFalse(f.declined)
        assertTrue(f.mayStillLand)
    }

    @Test
    fun `refused before signing - invalid payloads, too many, wrong cluster - nothing was sent`() {
        for (code in listOf(INVALID_PAYLOADS, TOO_MANY_PAYLOADS, CLUSTER_NOT_SUPPORTED)) {
            val f = WalletFailures.of("Remote exception", remote(code))
            assertFalse(f.declined, "code $code")
            assertFalse(f.mayStillLand, "code $code")
        }
    }

    @Test
    fun `the session broke after the wallet opened - it may still land`() {
        assertTrue(WalletFailures.of("IO error while sending operation", IOException("closed")).mayStillLand)
        assertTrue(WalletFailures.of("Timed out while waiting for result", TimeoutException()).mayStillLand)
    }

    @Test
    fun `the wallet never opened - nothing was sent`() {
        assertFalse(WalletFailures.of("Timed out waiting to send association intent", InterruptedException()).mayStillLand)
    }

    @Test
    fun `a program refusal names its code`() {
        assertEquals(6021, WalletFailures.of("Transaction simulation failed: custom program error: 0x1785", null).programError)
    }

    // ---- one outcome per flow ----------------------------------------------------------------------

    @Test
    fun `landed - success, with its signature`() {
        assertEquals(TxOutcome.Landed("sig"), TxOutcome.of(SignAction.Join, SendResult.Landed("sig"), lowOnSol = false))
    }

    @Test
    fun `declined - the same in every flow`() {
        for (a in SignAction.entries) {
            assertEquals(TxOutcome.Declined, TxOutcome.of(a, SendResult.Failed("x", declined = true), lowOnSol = false))
        }
    }

    @Test
    fun `join refused because joining closed first - ES-12`() {
        assertEquals(TxOutcome.JoiningClosed, TxOutcome.of(SignAction.Join, SendResult.Failed("closed", programError = 6021), lowOnSol = false))
    }

    @Test
    fun `submit refused past the deadline - ES-4, the forfeit`() {
        assertEquals(TxOutcome.DeadlinePassed, TxOutcome.of(SignAction.Submit, SendResult.Failed("late", programError = 6032), lowOnSol = false))
    }

    @Test
    fun `submit of a day already on chain - success`() {
        assertEquals(TxOutcome.Landed(null), TxOutcome.of(SignAction.Submit, SendResult.Failed("dup", programError = 6033), lowOnSol = false))
    }

    @Test
    fun `not enough SOL - said by the network, or a failure with too little SOL in the wallet`() {
        assertEquals(TxOutcome.NotEnoughSol, TxOutcome.of(SignAction.Claim, SendResult.Failed("Attempt to debit an account but found no record of a prior credit."), lowOnSol = false))
        assertEquals(TxOutcome.NotEnoughSol, TxOutcome.of(SignAction.Submit, SendResult.Failed("Transaction simulation failed: InsufficientFundsForFee"), lowOnSol = false))
        assertEquals(TxOutcome.NotEnoughSol, TxOutcome.of(SignAction.Join, SendResult.Failed("Remote exception", mayStillLand = true), lowOnSol = true))
    }

    @Test
    fun `a devnet that did not answer before the wallet opened is a failure, never short of SOL`() {
        val o = TxOutcome.of(SignAction.Submit, SendResult.Failed("Devnet did not answer.", beforeWallet = true), lowOnSol = true)
        assertEquals(TxOutcome.Failed(mayStillLand = false, message = "Devnet did not answer."), o)
    }

    @Test
    fun `sent but not confirmed - failed, and it may still land`() {
        val o = TxOutcome.of(SignAction.Claim, SendResult.Failed("Sent, but not confirmed", mayStillLand = true), lowOnSol = false)
        assertEquals(TxOutcome.Failed(mayStillLand = true, message = "Sent, but not confirmed"), o)
    }

    @Test
    fun `any other program refusal - said in words a member can act on`() {
        val o = TxOutcome.of(SignAction.Claim, SendResult.Failed("raw", programError = 6044), lowOnSol = false)
        assertEquals(TxOutcome.Refused("Already claimed."), o)
        // A join's 6021 is its own state, but the same code on a claim is just a refusal.
        assertTrue(TxOutcome.of(SignAction.Claim, SendResult.Failed("raw", programError = 6021), lowOnSol = false) is TxOutcome.Refused)
    }

    @Test
    fun `no wallet on the phone`() {
        assertEquals(TxOutcome.NoWallet, TxOutcome.of(SignAction.Join, SendResult.NoWallet, lowOnSol = false))
    }

    @Test
    fun `Try again is offered after a decline or a failure that sent nothing, never when it may still land`() {
        assertTrue(TxOutcome.Declined.canRetry)
        assertTrue(TxOutcome.Failed(mayStillLand = false, message = "x").canRetry)
        assertFalse(TxOutcome.Failed(mayStillLand = true, message = "x").canRetry)
        assertFalse(TxOutcome.Landed("sig").canRetry)
        assertFalse(TxOutcome.NotEnoughSol.canRetry)
        assertFalse(TxOutcome.Refused("x").canRetry)
    }

    private fun remote(code: Int) = JsonRpc20Client.JsonRpc20RemoteException(code, "remote", null)

    private companion object {
        // com.solana.mobilewalletadapter.common.ProtocolContract
        const val AUTHORIZATION_FAILED = -1
        const val INVALID_PAYLOADS = -2
        const val NOT_SIGNED = -3
        const val NOT_SUBMITTED = -4
        const val TOO_MANY_PAYLOADS = -6
        const val CLUSTER_NOT_SUPPORTED = -7
    }
}
