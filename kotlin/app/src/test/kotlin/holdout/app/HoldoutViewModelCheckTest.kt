package holdout.app

import androidx.lifecycle.viewModelScope
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainReads
import holdout.app.chain.ChainView
import holdout.app.holdout.AutoSubmit
import holdout.app.holdout.ChainCheck
import holdout.app.holdout.HoldoutViewModel
import holdout.app.storage.InMemoryStore
import holdout.core.Leaderboard
import holdout.core.MembershipSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a member's "Check again" is wired into the view model (spec 005 T019, the
 * stand-in review of PR #57): the reads it starts, the ones it joins or sets
 * aside, and that it never sends anything. The chain is a fake whose every
 * snapshot read waits until the test answers it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldoutViewModelCheckTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryStore()
    private val chain = FakeChain()
    private val walletA = SolanaPublicKey(ByteArray(32) { 1 })
    private val walletB = SolanaPublicKey(ByteArray(32) { 2 })

    /** The "not live here" view: the chain answered. */
    private val answered = ChainView(null, emptyList(), null, 0uL, 0L)

    private lateinit var vm: HoldoutViewModel

    private class FakeChain : ChainReads {
        val reads = mutableListOf<CompletableDeferred<ChainView?>>()

        override suspend fun load(wallet: SolanaPublicKey?): ChainView? = CompletableDeferred<ChainView?>().also { reads += it }.await()

        override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = null

        override suspend fun leaderboard(): Leaderboard.Snapshot? = null
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Every chain read goes to the fake. The Rpc (and the faucet built on it) is never
        // called by a check; it points at a closed local port so a stray call fails, not hangs.
        vm = HoldoutViewModel(store, rpc = Rpc("http://127.0.0.1:1"), chain = chain)
    }

    @AfterTest
    fun tearDown() {
        // Everything the view model started, the checks included, ends with the test.
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private val state get() = vm.state.value

    /** Watches [wallet] and lets its first read fail: the chain can't be reached. */
    private fun TestScope.unreachableFor(wallet: SolanaPublicKey) {
        vm.watch(wallet)
        runCurrent()
        chain.reads.last().complete(null)
        runCurrent()
        assertTrue(state.offline)
    }

    @Test
    fun `given the app is not in front, when the member asks, then nothing is read`() = runTest(dispatcher) {
        unreachableFor(walletA)
        vm.pause()
        val before = chain.reads.size

        vm.checkNow()
        runCurrent()

        assertEquals(before, chain.reads.size)
        assertEquals(ChainCheck(), state.check)
    }

    @Test
    fun `given a check under way, when the member asks again, then it joins the first`() = runTest(dispatcher) {
        unreachableFor(walletA)
        val before = chain.reads.size

        vm.checkNow()
        runCurrent()
        vm.checkNow()
        runCurrent()

        assertEquals(before + 1, chain.reads.size)
        assertTrue(state.check.checking)

        chain.reads.last().complete(null)
        runCurrent()
        assertEquals(ChainCheck(stillUnreachable = true), state.check)
    }

    @Test
    fun `given a check under way, when the chain answers it, then nothing is left to show`() = runTest(dispatcher) {
        unreachableFor(walletA)
        vm.checkNow()
        runCurrent()

        chain.reads.last().complete(answered)
        runCurrent()

        assertFalse(state.offline)
        assertEquals(ChainCheck(), state.check)
    }

    @Test
    fun `given a check under way, when the wallet changes, then its answer touches nothing of the new wallet's`() = runTest(dispatcher) {
        unreachableFor(walletA)
        vm.checkNow()
        runCurrent()
        val checkRead = chain.reads.last()

        // The new wallet's own first read fails too, so its state is "can't be reached":
        // were the old check's answer applied to it, it would read "still can't be reached".
        vm.watch(walletB)
        runCurrent()
        chain.reads.last().complete(null)
        runCurrent()
        assertTrue(state.offline)
        checkRead.complete(null)
        runCurrent()

        assertEquals(ChainCheck(), state.check)
    }

    @Test
    fun `given a check under way, when the scheduled read answers first, then the check is cleared at once`() = runTest(dispatcher) {
        unreachableFor(walletA)
        vm.checkNow()
        runCurrent()
        assertTrue(state.check.checking)

        // The 30-second pass starts a newer read while the check's is still out.
        advanceTimeBy(30_001)
        runCurrent()
        chain.reads.last().complete(answered)
        runCurrent()

        assertFalse(state.offline)
        assertEquals(ChainCheck(), state.check)
    }

    @Test
    fun `given a newer read answered, when the check's own read fails late, then it is set aside`() = runTest(dispatcher) {
        unreachableFor(walletA)
        vm.checkNow()
        runCurrent()
        val checkRead = chain.reads.last()
        advanceTimeBy(30_001)
        runCurrent()
        chain.reads.last().complete(answered)
        runCurrent()

        checkRead.complete(null)
        runCurrent()

        // Not "can't be reached" again, and not "still can't be reached": the newer answer stands.
        assertFalse(state.offline)
        assertEquals(ChainCheck(), state.check)
    }

    @Test
    fun `given a side read that throws, when the chain answers the check, then the answer still counts`() = runTest(dispatcher) {
        unreachableFor(walletA)
        var sideRead = false

        vm.checkNow {
            sideRead = true
            error("chain time failed")
        }
        runCurrent()
        assertTrue(sideRead)
        // Had the throw cancelled the check, this answer would never be applied.
        chain.reads.last().complete(answered)
        runCurrent()

        assertFalse(state.offline)
        assertEquals(ChainCheck(), state.check)
    }

    @Test
    fun `given a side read that throws, when the chain stays silent, then the check still ends`() = runTest(dispatcher) {
        unreachableFor(walletA)

        vm.checkNow { error("chain time failed") }
        runCurrent()
        chain.reads.last().complete(null)
        runCurrent()

        assertEquals(ChainCheck(stillUnreachable = true), state.check)
    }

    @Test
    fun `given a side read still out, when the snapshot answers, then the check waits for it`() = runTest(dispatcher) {
        unreachableFor(walletA)
        val chainTime = CompletableDeferred<Unit>()

        vm.checkNow { chainTime.await() }
        runCurrent()
        chain.reads.last().complete(null)
        runCurrent()
        assertTrue(state.check.checking)

        chainTime.complete(Unit)
        runCurrent()
        assertEquals(ChainCheck(stillUnreachable = true), state.check)
    }

    @Test
    fun `given a day waiting on the phone, when a check finds the chain, then the automatic send's record and the day are untouched`() = runTest(dispatcher) {
        val cohort = "cohort-1"
        store.setPendingCohortDay(walletA.base58(), cohort, 2)
        unreachableFor(walletA)

        vm.checkNow()
        runCurrent()
        chain.reads.last().complete(answered)
        runCurrent()

        // Only that: the view model records no send, and the day still waits on the phone.
        // Whether a waiting day then goes is the UI's automatic send (SendWaitingDay, 001
        // FR-024b), which these tests do not drive, so SC-003 is covered here in part only.
        assertEquals(AutoSubmit(), state.autoSubmit)
        assertEquals(2, store.pendingCohortDay(walletA.base58(), cohort))
    }
}
