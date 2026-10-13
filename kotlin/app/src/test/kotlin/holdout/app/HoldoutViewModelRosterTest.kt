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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The roster read (R8, the heaviest read there is): one at a time per cohort,
 * however many screens and loops ask for it (PR #62 review). The chain is a
 * fake whose every roster read waits until the test answers it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldoutViewModelRosterTest {
    private val dispatcher = StandardTestDispatcher()
    private val chain = FakeChain()
    private val cohortA = SolanaPublicKey(ByteArray(32) { 1 })
    private val cohortB = SolanaPublicKey(ByteArray(32) { 2 })
    private lateinit var vm: HoldoutViewModel

    /** The view model's monotonic clock: how old a roster's last answer is. */
    private var clock = 0L

    private class FakeChain : ChainReads {
        val rosterReads = mutableListOf<CompletableDeferred<List<MembershipSnapshot>?>>()

        override suspend fun load(wallet: SolanaPublicKey?): ChainView? = null

        override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = CompletableDeferred<List<MembershipSnapshot>?>().also { rosterReads += it }.await()

        override suspend fun leaderboard(): Leaderboard.Snapshot? = null
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

    @Test
    fun `given a cohort's roster read still out, when it is asked for again, then there is still one chain read`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        vm.loadRoster(cohortA)
        runCurrent()
        vm.loadRoster(cohortA)
        runCurrent()

        assertEquals(1, chain.rosterReads.size)

        chain.rosterReads.single().complete(emptyList())
        runCurrent()
        assertEquals(setOf(cohortA), vm.rosters.value.keys)
    }

    @Test
    fun `given a cohort's read has answered, when it is asked for again once its age is reached, then it reads again`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        chain.rosterReads.single().complete(emptyList())
        runCurrent()

        clock += HoldoutViewModel.ROSTER_MAX_AGE_MILLIS
        vm.loadRoster(cohortA)
        runCurrent()

        assertEquals(2, chain.rosterReads.size)
    }

    @Test
    fun `given a roster that answered less than 20 seconds ago, when a screen asks for it, then it is not read again`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        chain.rosterReads.single().complete(emptyList())
        runCurrent()

        clock += HoldoutViewModel.MIN_READ_AGE_MILLIS - 1
        vm.loadRoster(cohortA)
        runCurrent()

        assertEquals(1, chain.rosterReads.size)
    }

    @Test
    fun `given a roster that just answered, when Check again asks for it, then it is read anyway`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        chain.rosterReads.single().complete(emptyList())
        runCurrent()

        vm.loadRoster(cohortA, force = true)
        runCurrent()

        assertEquals(2, chain.rosterReads.size)
    }

    @Test
    fun `given one cohort's roster just answered, when another is asked for, then it is read`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        chain.rosterReads.single().complete(emptyList())
        runCurrent()

        vm.loadRoster(cohortB)
        runCurrent()

        assertEquals(2, chain.rosterReads.size)
    }

    @Test
    fun `given one cohort's read still out, when another is asked for, then it reads at once`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        vm.loadRoster(cohortB)
        runCurrent()

        assertEquals(2, chain.rosterReads.size)
    }

    @Test
    fun `given a read that failed or threw, when it is asked for again, then it reads again`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        chain.rosterReads.last().completeExceptionally(IllegalStateException("roster failed"))
        runCurrent()
        vm.loadRoster(cohortA)
        runCurrent()
        chain.rosterReads.last().complete(null)
        runCurrent()
        vm.loadRoster(cohortA)
        runCurrent()

        assertEquals(3, chain.rosterReads.size)
        assertTrue(vm.rosters.value.isEmpty())
    }

    @Test
    fun `given a read that was set aside with the check that owned it, when it is asked for again, then it reads again`() = runTest(dispatcher) {
        val owner = Job()
        vm.loadRoster(cohortA, kotlinx.coroutines.CoroutineScope(dispatcher + owner))
        runCurrent()
        owner.cancel()
        runCurrent()

        vm.loadRoster(cohortA)
        runCurrent()

        assertEquals(2, chain.rosterReads.size)
    }

    @Test
    fun `given a read that never answers, when a minute has passed, then it is given up and the next ask reads`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()

        vm.loadRoster(cohortA)
        runCurrent()

        assertEquals(2, chain.rosterReads.size)
    }

    @Test
    fun `given two reads that overlap after one was given up, when the older answers last, then the newer roster stays`() = runTest(dispatcher) {
        vm.loadRoster(cohortA)
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()
        vm.loadRoster(cohortA)
        runCurrent()

        val newer = listOf<MembershipSnapshot>()
        chain.rosterReads[1].complete(newer)
        runCurrent()
        // The given-up read is cancelled: its late answer is nothing.
        chain.rosterReads[0].complete(null)
        runCurrent()

        assertEquals(newer, vm.rosters.value[cohortA])
    }
}
