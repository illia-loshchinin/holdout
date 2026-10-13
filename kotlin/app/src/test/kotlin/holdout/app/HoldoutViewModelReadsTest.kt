package holdout.app

import androidx.lifecycle.viewModelScope
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainReads
import holdout.app.chain.ChainView
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
 * How often the view model reads the chain, and when it says the chain can't be
 * reached (R8, product owner 2026-10-13): one failed snapshot read keeps the last
 * view, two in a row say so; a read already out answers the 30-second pass; the
 * leaderboards are read one at a time and not again within 20 seconds. The chain
 * is a fake whose every read waits until the test answers it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldoutViewModelReadsTest {
    private val dispatcher = StandardTestDispatcher()
    private val chain = FakeChain()
    private val wallet = SolanaPublicKey(ByteArray(32) { 1 })
    private val answered = ChainView(null, emptyList(), null, 0uL, 0L)
    private val board = Leaderboard.Snapshot(emptyList(), emptyList(), emptyList())
    private var clock = 0L
    private lateinit var vm: HoldoutViewModel

    private class FakeChain : ChainReads {
        val reads = mutableListOf<CompletableDeferred<ChainView?>>()
        val boards = mutableListOf<CompletableDeferred<Leaderboard.Snapshot?>>()

        override suspend fun load(wallet: SolanaPublicKey?): ChainView? = CompletableDeferred<ChainView?>().also { reads += it }.await()

        override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = null

        override suspend fun leaderboard(): Leaderboard.Snapshot? = CompletableDeferred<Leaderboard.Snapshot?>().also { boards += it }.await()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = HoldoutViewModel(InMemoryStore(), rpc = Rpc("http://127.0.0.1:1"), chain = chain, elapsed = { clock })
    }

    @AfterTest
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private val state get() = vm.state.value

    /** Lets the 30-second pass start its read and answers it with [view]. */
    private fun TestScope.nextPass(view: ChainView?) {
        advanceTimeBy(30_001)
        runCurrent()
        chain.reads.last().complete(view)
        runCurrent()
    }

    private fun TestScope.watching() {
        vm.watch(wallet)
        runCurrent()
        chain.reads.last().complete(answered)
        runCurrent()
    }

    @Test
    fun `given a view on screen, when one read fails, then the view stays and the chain is not called unreachable`() = runTest(dispatcher) {
        watching()

        nextPass(null)

        assertFalse(state.offline)
        assertEquals(answered, state.view)
    }

    @Test
    fun `given a view on screen, when two reads in a row fail, then the chain can't be reached and the last view stays`() = runTest(dispatcher) {
        watching()

        nextPass(null)
        nextPass(null)

        assertTrue(state.offline)
        assertEquals(answered, state.view)
    }

    @Test
    fun `given a read failed, when the next answers, then the count starts over`() = runTest(dispatcher) {
        watching()

        nextPass(null)
        nextPass(answered)
        nextPass(null)

        assertFalse(state.offline)
    }

    @Test
    fun `given no view yet, when the first read fails, then the chain can't be reached at once`() = runTest(dispatcher) {
        vm.watch(wallet)
        runCurrent()
        chain.reads.last().complete(null)
        runCurrent()

        assertTrue(state.offline)
    }

    @Test
    fun `given a wallet whose last read failed, when another wallet is connected, then its count starts over`() = runTest(dispatcher) {
        watching()
        nextPass(null)

        vm.watch(SolanaPublicKey(ByteArray(32) { 2 }))
        runCurrent()
        chain.reads.last().complete(answered)
        runCurrent()
        nextPass(null)

        assertFalse(state.offline)
    }

    @Test
    fun `given a leaderboard read out, when the boards ask again, then there is still one read`() = runTest(dispatcher) {
        vm.loadLeaderboard()
        runCurrent()
        vm.loadLeaderboard()
        runCurrent()

        assertEquals(1, chain.boards.size)
    }

    @Test
    fun `given the leaderboards answered less than 20 seconds ago, when they are asked for, then they are not read again`() = runTest(dispatcher) {
        vm.loadLeaderboard()
        runCurrent()
        chain.boards.single().complete(board)
        runCurrent()

        clock += HoldoutViewModel.MIN_READ_AGE_MILLIS - 1
        vm.loadLeaderboard()
        runCurrent()
        assertEquals(1, chain.boards.size)
        assertEquals(board, vm.leaderboard.value)

        clock += 1
        vm.loadLeaderboard()
        runCurrent()
        assertEquals(2, chain.boards.size)
    }

    @Test
    fun `given a leaderboard read that failed, when the boards ask again, then it reads at once`() = runTest(dispatcher) {
        vm.loadLeaderboard()
        runCurrent()
        chain.boards.single().complete(null)
        runCurrent()

        vm.loadLeaderboard()
        runCurrent()

        assertEquals(2, chain.boards.size)
    }
}
