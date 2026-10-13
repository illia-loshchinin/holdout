package holdout.app

import androidx.lifecycle.viewModelScope
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainReads
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.holdout.HoldoutViewModel
import holdout.app.storage.InMemoryStore
import holdout.core.CohortSnapshot
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
        val rosters = mutableListOf<CompletableDeferred<List<MembershipSnapshot>?>>()

        /** Whether each snapshot read was a fresh one (after an action, a Check again, the network's return). */
        val fresh = mutableListOf<Boolean>()

        override suspend fun load(wallet: SolanaPublicKey?): ChainView? = CompletableDeferred<ChainView?>().also {
            reads += it
            fresh += false
        }.await()

        override suspend fun loadFresh(wallet: SolanaPublicKey?): ChainView? = CompletableDeferred<ChainView?>().also {
            reads += it
            fresh += true
        }.await()

        override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = CompletableDeferred<List<MembershipSnapshot>?>().also { rosters += it }.await()

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

    private fun TestScope.watching(view: ChainView = answered) {
        vm.watch(wallet)
        runCurrent()
        chain.reads.last().complete(view)
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

        clock = HoldoutViewModel.BOARD_MAX_AGE_MILLIS
        vm.loadLeaderboard()
        runCurrent()
        assertEquals(2, chain.boards.size)
    }

    // ---- reads by change (FR-071) ---------------------------------------------

    private val cohortA = SolanaPublicKey(ByteArray(32) { 7 })

    /** A view holding one cohort, [cohortA], with [members] members, [forfeits] forfeits recorded and [counted] counted. */
    private fun viewWith(members: Long = 1, forfeits: Long = 0, counted: Long = 0) = ChainView(
        null,
        listOf(Holdout(cohortA, CohortSnapshot(1, 0, 0, 0, 3, 5, 600, 0, listOf(1uL, 2uL, 3uL, 4uL), memberCount = members, forfeitsRecorded = forfeits, counted = counted), null, null)),
        null,
        0uL,
        0L,
    )

    @Test
    fun `given the leaderboards read and nothing moved, when they ask again within ten minutes, then they are not read`() = runTest(dispatcher) {
        watching(viewWith())
        vm.loadLeaderboard()
        runCurrent()
        chain.boards.single().complete(board)
        runCurrent()

        clock = HoldoutViewModel.BOARD_MAX_AGE_MILLIS - 1
        vm.loadLeaderboard()
        runCurrent()

        assertEquals(1, chain.boards.size)
    }

    @Test
    fun `given the leaderboards read, when the pulse sees a settlement or a forfeit move, then they are read again`() = runTest(dispatcher) {
        watching(viewWith())
        vm.loadLeaderboard()
        runCurrent()
        chain.boards.single().complete(board)
        runCurrent()

        nextPass(viewWith(counted = 1))
        clock = HoldoutViewModel.MIN_READ_AGE_MILLIS
        vm.loadLeaderboard()
        runCurrent()

        assertEquals(2, chain.boards.size)
    }

    @Test
    fun `given a roster read and its cohort unmoved, when it is asked for within its age, then it is not read`() = runTest(dispatcher) {
        watching(viewWith())
        vm.loadRoster(cohortA, maxAgeMillis = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS)
        runCurrent()
        chain.rosters.single().complete(emptyList())
        runCurrent()

        clock = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS - 1
        vm.loadRoster(cohortA, maxAgeMillis = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS)
        runCurrent()
        assertEquals(1, chain.rosters.size)

        clock = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS
        vm.loadRoster(cohortA, maxAgeMillis = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS)
        runCurrent()
        assertEquals(2, chain.rosters.size)
    }

    @Test
    fun `given a roster read, when the pulse sees its cohort's counters move, then it is read again at once`() = runTest(dispatcher) {
        watching(viewWith())
        vm.loadRoster(cohortA, maxAgeMillis = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS)
        runCurrent()
        chain.rosters.single().complete(emptyList())
        runCurrent()

        nextPass(viewWith(members = 2))
        clock = HoldoutViewModel.MIN_READ_AGE_MILLIS
        vm.loadRoster(cohortA, maxAgeMillis = HoldoutViewModel.ROSTER_SLOW_MAX_AGE_MILLIS)
        runCurrent()

        assertEquals(2, chain.rosters.size)
    }

    // ---- the pulse: live screens only, backoff, fresh reads -------------------

    @Test
    fun `given a screen without live chain data in front, then the pulse reads nothing until a live one is back`() = runTest(dispatcher) {
        watching()
        vm.setLive(false)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, chain.reads.size)

        vm.setLive(true)
        runCurrent()
        assertEquals(2, chain.reads.size)
    }

    @Test
    fun `given a live screen back before the pulse's 30 seconds are up, then it waits for them`() = runTest(dispatcher) {
        watching()
        vm.setLive(false)
        advanceTimeBy(10_000)
        vm.setLive(true)
        runCurrent()
        assertEquals(1, chain.reads.size)

        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(2, chain.reads.size)
    }

    @Test
    fun `given failed reads, then the pulse waits 30, 60, 120 seconds before the next`() = runTest(dispatcher) {
        watching()
        nextPass(null)
        // One failure: the next read 30 s later.
        nextPass(null)
        // Two: 60 s.
        advanceTimeBy(30_001)
        runCurrent()
        val reads = chain.reads.size
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(reads + 1, chain.reads.size)
        chain.reads.last().complete(null)
        runCurrent()
        // Three: 120 s.
        advanceTimeBy(119_000)
        runCurrent()
        assertEquals(reads + 1, chain.reads.size)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(reads + 2, chain.reads.size)
    }

    @Test
    fun `given the pulse waiting after failures, when a Check again answers, then the wait starts over at 30 seconds`() = runTest(dispatcher) {
        watching()
        nextPass(null)
        nextPass(null)
        assertTrue(state.offline)
        val reads = chain.reads.size

        vm.checkNow()
        runCurrent()
        chain.reads.last().complete(answered)
        runCurrent()
        assertEquals(reads + 1, chain.reads.size)

        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(reads + 2, chain.reads.size)
    }

    @Test
    fun `the pulse reads by change, a Check again reads fresh`() = runTest(dispatcher) {
        watching()
        nextPass(null)
        nextPass(null)

        vm.checkNow()
        runCurrent()

        assertEquals(listOf(false, false, false, true), chain.fresh)
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
