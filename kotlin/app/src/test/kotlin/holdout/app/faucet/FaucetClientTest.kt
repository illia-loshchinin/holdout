package holdout.app.faucet

import androidx.activity.ComponentActivity
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.transaction.TransactionInstruction
import holdout.app.storage.InMemoryStore
import holdout.app.testutil.ChainFixtures
import holdout.app.wallet.SendPhase
import holdout.app.wallet.SendResult
import holdout.app.wallet.WalletRepository
import holdout.core.Rpc
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The faucet has no daily limit per wallet (owner, 12 Oct): every tap goes to
 * the program, which only refuses when the faucet itself cannot pay (ES-0b).
 */
@RunWith(RobolectricTestRunner::class)
class FaucetClientTest {
    private val me = ChainFixtures.me

    /** A wallet that lands (or refuses with [refusal]) every claim and counts them. */
    private class FakeWallet(var refusal: Int? = null) : WalletRepository(InMemoryStore(), Rpc("http://127.0.0.1:1")) {
        var sends = 0

        override suspend fun send(sender: ActivityResultSender, instructions: List<TransactionInstruction>, onPhase: (SendPhase) -> Unit): SendResult {
            sends++
            return refusal?.let { SendResult.Failed("refused", programError = it) } ?: SendResult.Landed("sig$sends")
        }
    }

    private val wallet = FakeWallet()
    private val client = FaucetClient(Rpc("http://127.0.0.1:1"), wallet, ChainFixtures.config.admin, sponsor = null)
    private val sender get() = ActivityResultSender(Robolectric.buildActivity(ComponentActivity::class.java).create().get())

    @Test
    fun `a wallet that already claimed today claims again - the client sends it, the program decides`() = runTest {
        val s = sender
        val first = client.claim(s, me, ChainFixtures.config, 12_000_000L)
        val second = client.claim(s, me, ChainFixtures.config, 12_000_000L)

        assertEquals(FaucetOutcome.Funded("sig1"), first)
        assertEquals(FaucetOutcome.Funded("sig2"), second)
        assertEquals(2, wallet.sends)
    }

    @Test
    fun `the day's budget used up is still the faucet's to own, never the member's`() = runTest {
        wallet.refusal = FaucetClient.FAUCET_CAP_REACHED

        assertEquals(FaucetOutcome.CannotPayOut, client.claim(sender, me, ChainFixtures.config, 12_000_000L))
    }
}
