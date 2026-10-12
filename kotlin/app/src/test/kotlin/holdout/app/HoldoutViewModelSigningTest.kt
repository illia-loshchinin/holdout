package holdout.app

import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.TransactionInstruction
import holdout.app.chain.ChainReads
import holdout.app.chain.ChainView
import holdout.app.holdout.HoldoutViewModel
import holdout.app.storage.InMemoryStore
import holdout.app.testutil.ChainFixtures
import holdout.app.wallet.SendPhase
import holdout.app.wallet.SendResult
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.app.wallet.WalletRepository
import holdout.core.Leaderboard
import holdout.core.MembershipSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a signature runs through the view model (rulings G7): the result screen's
 * "Try again" must always start a new signature, even while the closing read of
 * the one that just failed is still out (PR #62 review). The wallet and the chain
 * are fakes whose answers the test gives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HoldoutViewModelSigningTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val chain = FakeChain()
    private val wallet = FakeWallet(store)
    private val me = ChainFixtures.me
    private val holdout = ChainFixtures.holdout(3, ChainFixtures.START)
    private val view = ChainFixtures.view(holdout)
    private lateinit var vm: HoldoutViewModel
    private lateinit var sender: ActivityResultSender

    private class FakeChain : ChainReads {
        val reads = mutableListOf<CompletableDeferred<ChainView?>>()
        val rosterReads = mutableListOf<CompletableDeferred<List<MembershipSnapshot>?>>()

        override suspend fun load(wallet: SolanaPublicKey?): ChainView? = CompletableDeferred<ChainView?>().also { reads += it }.await()

        override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = CompletableDeferred<List<MembershipSnapshot>?>().also { rosterReads += it }.await()

        override suspend fun leaderboard(): Leaderboard.Snapshot? = null
    }

    /** A wallet whose every signature waits for the test to answer it. */
    private class FakeWallet(store: InMemoryStore) : WalletRepository(store, Rpc("http://127.0.0.1:1")) {
        val sends = mutableListOf<CompletableDeferred<SendResult>>()

        override suspend fun send(sender: ActivityResultSender, instructions: List<TransactionInstruction>, onPhase: (SendPhase) -> Unit): SendResult {
            onPhase(SendPhase.WaitingForWallet)
            return CompletableDeferred<SendResult>().also { sends += it }.await()
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = HoldoutViewModel(store, rpc = Rpc("http://127.0.0.1:1"), chain = chain, wallet = wallet)
        sender = ActivityResultSender(Robolectric.buildActivity(ComponentActivity::class.java).create().get())
    }

    @AfterTest
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private val state get() = vm.state.value

    /** Watches the member's wallet and answers its first read: the chain is live and shows a Holdout. */
    private fun TestScope.watching() {
        vm.watch(me)
        runCurrent()
        chain.reads.last().complete(view)
        runCurrent()
    }

    @Test
    fun `given a signature just declined and its closing read still out, when the member taps Try again, then a new signature starts`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, holdout)
        runCurrent()
        assertEquals(1, wallet.sends.size)
        wallet.sends.single().complete(SendResult.Failed("no", declined = true))
        runCurrent()
        // The result is on screen; the read that closes the signature has not answered.
        assertEquals(TxPhase.Done(TxOutcome.Declined), state.signing?.phase)

        vm.claim(sender, holdout)
        runCurrent()

        assertEquals(2, wallet.sends.size, "Try again must open the wallet again")
        assertEquals(2, state.signing?.id)
        assertEquals(TxPhase.WaitingForWallet, state.signing?.phase)
    }

    @Test
    fun `given a retry started while the first signature's closing read is out, when that read answers, then the retry is still busy`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, holdout)
        runCurrent()
        wallet.sends.single().complete(SendResult.Failed("no", declined = true))
        runCurrent()
        vm.claim(sender, holdout)
        runCurrent()

        // The first signature's closing read answers while the second waits for the wallet.
        chain.reads.last().complete(view)
        runCurrent()

        assertTrue(state.busy, "the new signature still holds the action")
        // A third ask meanwhile does not open the wallet a third time.
        vm.claim(sender, holdout)
        runCurrent()
        assertEquals(2, wallet.sends.size)
    }

    @Test
    fun `given a signature that landed, when the closing read is out, then the action stays held until it answers`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, holdout)
        runCurrent()
        wallet.sends.single().complete(SendResult.Landed("sig"))
        runCurrent()

        assertTrue(state.busy)
        chain.reads.last().complete(view)
        runCurrent()
        assertFalse(state.busy)
    }
}
