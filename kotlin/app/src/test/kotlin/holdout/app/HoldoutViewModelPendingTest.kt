package holdout.app

import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.TransactionInstruction
import holdout.app.chain.ChainReads
import holdout.app.chain.ChainView
import holdout.app.holdout.HoldoutViewModel
import holdout.app.holdout.PendingTx
import holdout.app.holdout.UnseenTx
import holdout.app.holdout.waitLine
import holdout.app.storage.InMemoryStore
import holdout.app.testutil.ChainFixtures
import holdout.app.wallet.SendPhase
import holdout.app.wallet.SendResult
import holdout.app.wallet.SignAction
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A signature whose outcome the member may not see, or that may still land
 * (PR #62 review): one that left the wallet and devnet did not confirm keeps
 * its action shut until a chain read settles it, and one the member left the
 * confirming screen of is kept for a card on the screen they went to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HoldoutViewModelPendingTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val chain = FakeChain()
    private val wallet = FakeWallet(store)
    private val me = ChainFixtures.me
    private val finished = ChainFixtures.holdout(3, ChainFixtures.START, member = ChainFixtures.member(7, MembershipSnapshot.State.Finished))
    private val claimed = finished.copy(membership = ChainFixtures.member(7, MembershipSnapshot.State.Claimed))
    private val view = ChainFixtures.view(finished)
    private var clock = 1_000_000L
    private lateinit var vm: HoldoutViewModel
    private lateinit var sender: ActivityResultSender

    private class FakeChain : ChainReads {
        val reads = mutableListOf<CompletableDeferred<ChainView?>>()

        override suspend fun load(wallet: SolanaPublicKey?): ChainView? = CompletableDeferred<ChainView?>().also { reads += it }.await()

        override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = null

        override suspend fun leaderboard(): Leaderboard.Snapshot? = null
    }

    /** A wallet whose every signature waits for the test to answer it, reporting the send as confirming first. */
    private class FakeWallet(store: InMemoryStore) : WalletRepository(store, Rpc("http://127.0.0.1:1")) {
        val sends = mutableListOf<CompletableDeferred<SendResult>>()

        override suspend fun send(sender: ActivityResultSender, instructions: List<TransactionInstruction>, onPhase: (SendPhase) -> Unit): SendResult {
            onPhase(SendPhase.Confirming)
            return CompletableDeferred<SendResult>().also { sends += it }.await()
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = HoldoutViewModel(store, rpc = Rpc("http://127.0.0.1:1"), chain = chain, wallet = wallet, elapsed = { clock })
        sender = ActivityResultSender(Robolectric.buildActivity(ComponentActivity::class.java).create().get())
    }

    @AfterTest
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private val state get() = vm.state.value

    private fun TestScope.answer(v: ChainView) {
        chain.reads.last().complete(v)
        runCurrent()
    }

    private fun TestScope.watching() {
        vm.watch(me)
        runCurrent()
        answer(view)
    }

    /** A claim sent and not confirmed: it left the wallet, so it may land yet. Its closing read answers with [after]. */
    private fun TestScope.claimNotConfirmed(after: ChainView = view) {
        vm.claim(sender, finished)
        runCurrent()
        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()
        answer(after)
    }

    @Test
    fun `given a claim that may still land, when the member asks again, then the wallet is not opened a second time`() = runTest(dispatcher) {
        watching()
        claimNotConfirmed()

        assertNotNull(state.pendingFor(SignAction.Claim, finished.address))
        vm.claim(sender, finished)
        runCurrent()
        assertEquals(1, wallet.sends.size)
    }

    @Test
    fun `given a claim that may still land, when a read started after it could have landed shows nothing, then Claim opens again`() = runTest(dispatcher) {
        watching()
        claimNotConfirmed()
        clock += PendingTx.LANDS_WITHIN_MILLIS
        // The next 30-second pass.
        dispatcher.scheduler.advanceTimeBy(30_001)
        runCurrent()
        answer(view)

        assertNull(state.pendingFor(SignAction.Claim, finished.address))
        vm.claim(sender, finished)
        runCurrent()
        assertEquals(2, wallet.sends.size)
    }

    @Test
    fun `given a claim that may still land, when the next read shows it claimed, then it is settled at once`() = runTest(dispatcher) {
        watching()
        claimNotConfirmed(after = ChainFixtures.view(claimed))

        assertNull(state.pendingFor(SignAction.Claim, finished.address))
    }

    @Test
    fun `given a read soon after shows nothing, then a claim that may still land stays shut`() = runTest(dispatcher) {
        watching()
        claimNotConfirmed()
        clock += PendingTx.LANDS_WITHIN_MILLIS - 1
        dispatcher.scheduler.advanceTimeBy(30_001)
        runCurrent()
        answer(view)

        assertNotNull(state.pendingFor(SignAction.Claim, finished.address))
    }

    @Test
    fun `given a claim the member left while it confirmed, when it fails, then its outcome is kept for a card and not put back on the signing screens`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        assertEquals(TxPhase.Confirming, state.signing?.phase)
        // "Leave this screen".
        vm.clearSigning()

        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()

        assertNull(state.signing, "nothing shows the signing screens any more")
        val unseen = state.unseen.single()
        assertEquals(SignAction.Claim, unseen.action)
        assertEquals("Not confirmed yet", unseen.title)
        assertFalse(unseen.warning)

        vm.dismissUnseen(unseen)
        assertTrue(state.unseen.isEmpty())
    }

    @Test
    fun `given a left claim that may still land, when a later read settles it unlanded, then the card says it didn't land`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        vm.clearSigning()
        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()
        answer(view)
        clock += PendingTx.LANDS_WITHIN_MILLIS
        dispatcher.scheduler.advanceTimeBy(30_001)
        runCurrent()
        answer(view)

        val unseen = state.unseen.single()
        assertEquals("It didn't land", unseen.title)
        assertTrue(unseen.warning)
        assertEquals("Your claim didn't land. Your claim waits here.", unseen.text)
    }

    @Test
    fun `given a left claim that may still land, when a later read shows it claimed, then the card goes`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        vm.clearSigning()
        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()
        answer(ChainFixtures.view(claimed))

        assertTrue(state.unseen.isEmpty())
    }

    @Test
    fun `given a left claim, when it lands, then no card is kept`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        vm.clearSigning()
        wallet.sends.last().complete(SendResult.Landed("sig"))
        runCurrent()

        assertTrue(state.unseen.isEmpty())
        assertNull(state.signing)
    }

    @Test
    fun `given a claim the member waited for, when it fails, then the result shows on the signing screens and no card is kept`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()

        assertEquals(TxPhase.Done(TxOutcome.Failed(mayStillLand = true, message = "timed out")), state.signing?.phase)
        assertTrue(state.unseen.isEmpty())
    }

    @Test
    fun `given a day sent that may still land, then it is not sent by itself again`() = runTest(dispatcher) {
        val running = ChainFixtures.holdout(4, ChainFixtures.START, member = ChainFixtures.member(2))
        vm.watch(me)
        runCurrent()
        answer(ChainFixtures.view(running))
        vm.submitDay(sender, running, 2, 25)
        runCurrent()
        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()
        answer(ChainFixtures.view(running))

        assertFalse(vm.autoSubmitDue(running, pendingDay = 2, todayDone = false))
        vm.submitDay(sender, running, 2, 25)
        runCurrent()
        assertEquals(1, wallet.sends.size)
        assertEquals(2, vm.pendingDay(running), "the day stays waiting on the phone")
    }

    // ---- the pure parts --------------------------------------------------------------------------

    @Test
    fun `a pending submit is for its own day only`() {
        val p = PendingTx(SignAction.Submit, finished.address, day = 2, sentAt = 0)
        assertTrue(p.isFor(SignAction.Submit, finished.address, 2))
        assertFalse(p.isFor(SignAction.Submit, finished.address, 3))
        assertFalse(p.isFor(SignAction.Claim, finished.address))
        assertEquals("Day 3 may still land. Sending opens again once devnet answers.", p.waitLine())
    }

    @Test
    fun `a pending submit lands once the chain counts its day`() {
        val p = PendingTx(SignAction.Submit, finished.address, day = 2, sentAt = 0)
        val running = ChainFixtures.holdout(3, ChainFixtures.START, member = ChainFixtures.member(2))
        assertFalse(p.landedIn(ChainFixtures.view(running)))
        assertTrue(p.landedIn(ChainFixtures.view(running.copy(membership = ChainFixtures.member(3)))))
    }

    @Test
    fun `a pending join lands once the member holds a place`() {
        val open = ChainFixtures.holdout(3, ChainFixtures.START)
        val p = PendingTx(SignAction.Join, open.address, day = 0, sentAt = 0)
        assertFalse(p.landedIn(ChainFixtures.view(open)))
        assertTrue(p.landedIn(ChainFixtures.view(open.copy(membership = ChainFixtures.member(0)))))
    }

    @Test
    fun `a card names the action and says what to do next`() {
        val joinClosed = UnseenTx(SignAction.Join, finished.address, 0, TxOutcome.JoiningClosed)
        assertEquals("Joining closed first", joinClosed.title)
        assertEquals("Your transaction landed too late. Nothing was taken.", joinClosed.text)
        val day = UnseenTx(SignAction.Submit, finished.address, 4, TxOutcome.Failed(mayStillLand = true, message = "x"))
        assertEquals("Day 5 was sent, but the network hasn't confirmed it. It may still land — check again in a minute.", day.text)
        val refused = UnseenTx(SignAction.Join, finished.address, 0, TxOutcome.Refused("This Holdout would start before your current one ends."))
        assertEquals("This Holdout would start before your current one ends.", refused.text)
        assertTrue(refused.warning)
    }
    // ---- PR #67 review ---------------------------------------------------------------------------

    @Test
    fun `the window to land starts when the send reached the network, not when confirming gave up`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        val sent = clock
        // The confirmation polling takes its time before it gives up.
        clock += 30_000
        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()

        assertEquals(sent, state.pendingFor(SignAction.Claim, finished.address)?.sentAt)
    }

    @Test
    fun `a send that may still land is kept on the phone, so a new process keeps its action shut`() = runTest(dispatcher) {
        watching()
        claimNotConfirmed()
        val again = HoldoutViewModel(store, rpc = Rpc("http://127.0.0.1:1"), chain = chain, wallet = wallet, elapsed = { clock })
        try {
            again.watch(me)
            runCurrent()
            assertNotNull(again.state.value.pendingFor(SignAction.Claim, finished.address))
            again.claim(sender, finished)
            runCurrent()
            assertEquals(1, wallet.sends.size)
        } finally {
            again.viewModelScope.cancel()
        }
    }

    @Test
    fun `a kept send past its window, or from before the phone restarted, is not restored`() {
        val p = PendingTx(SignAction.Claim, finished.address, 0, sentAt = 10_000)
        val kept = assertNotNull(PendingTx.encode(listOf(p)))
        assertEquals(listOf(p), PendingTx.restore(kept, now = 10_000 + PendingTx.LANDS_WITHIN_MILLIS - 1))
        assertTrue(PendingTx.restore(kept, now = 10_000 + PendingTx.LANDS_WITHIN_MILLIS).isEmpty())
        assertTrue(PendingTx.restore(kept, now = 5_000).isEmpty(), "the clock restarted with the phone")
        assertNull(PendingTx.encode(emptyList()))
    }

    @Test
    fun `a settled send is no longer kept on the phone`() = runTest(dispatcher) {
        watching()
        claimNotConfirmed()
        assertNotNull(store.unconfirmedSends(me.base58()))
        clock += PendingTx.LANDS_WITHIN_MILLIS
        dispatcher.scheduler.advanceTimeBy(30_001)
        runCurrent()
        answer(view)

        assertNull(store.unconfirmedSends(me.base58()))
    }

    @Test
    fun `a result for a wallet since disconnected is neither shown nor kept for the new one`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        vm.clearSigning()
        val other = SolanaPublicKey(ByteArray(32) { 0x22 })
        vm.watch(other)
        runCurrent()

        wallet.sends.last().complete(SendResult.Failed("timed out", mayStillLand = true))
        runCurrent()

        assertTrue(state.unseen.isEmpty())
        assertTrue(state.pending.isEmpty())
        assertNull(store.unconfirmedSends(other.base58()))
    }

    @Test
    fun `two results left one after the other each keep their card until dismissed`() = runTest(dispatcher) {
        val second = ChainFixtures.holdout(5, ChainFixtures.START, member = ChainFixtures.member(7, MembershipSnapshot.State.Finished))
        vm.watch(me)
        runCurrent()
        answer(ChainFixtures.view(finished, second))
        vm.claim(sender, finished)
        runCurrent()
        vm.clearSigning()
        wallet.sends.last().complete(SendResult.Failed("refused", programError = 6044))
        runCurrent()
        answer(ChainFixtures.view(finished, second))
        vm.claim(sender, second)
        runCurrent()
        vm.clearSigning()
        wallet.sends.last().complete(SendResult.Failed("refused", programError = 6044))
        runCurrent()

        assertEquals(listOf(finished.address, second.address), state.unseen.map { it.cohort })
        vm.dismissUnseen(state.unseen.first())
        assertEquals(listOf(second.address), state.unseen.map { it.cohort })
    }

    @Test
    fun `a send is kept on the phone from the moment it confirms, so a process killed while confirming keeps its action shut`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        // Confirming: the wallet sent it; the confirmation polling has not ended.
        assertNotNull(store.unconfirmedSends(me.base58()))
        val again = HoldoutViewModel(store, rpc = Rpc("http://127.0.0.1:1"), chain = chain, wallet = wallet, elapsed = { clock })
        try {
            again.watch(me)
            runCurrent()
            assertNotNull(again.state.value.pendingFor(SignAction.Claim, finished.address))
        } finally {
            again.viewModelScope.cancel()
        }
    }

    @Test
    fun `a send that ends any other way than may-still-land is no longer kept`() = runTest(dispatcher) {
        watching()
        vm.claim(sender, finished)
        runCurrent()
        wallet.sends.last().complete(SendResult.Failed("no", declined = true))
        runCurrent()

        assertNull(state.pendingFor(SignAction.Claim, finished.address))
        assertNull(store.unconfirmedSends(me.base58()))
    }

    @Test
    fun `a card for one day's send stays when another day of the same Holdout is sent`() = runTest(dispatcher) {
        val running = ChainFixtures.holdout(4, ChainFixtures.START, member = ChainFixtures.member(2))
        vm.watch(me)
        runCurrent()
        answer(ChainFixtures.view(running))
        vm.submitDay(sender, running, 2, 25)
        runCurrent()
        vm.clearSigning()
        wallet.sends.last().complete(SendResult.Failed("refused", programError = 6031))
        runCurrent()
        answer(ChainFixtures.view(running))
        assertEquals(listOf(2), state.unseen.map { it.day })

        vm.submitDay(sender, running, 3, 25)
        runCurrent()

        assertEquals(listOf(2), state.unseen.map { it.day }, "day 3's send leaves day 2's card")
    }
}
