package holdout.app

import holdout.core.ChainClock
import holdout.core.Rpc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Chain-time reads overlap — the loop's and a member's "Check again" — and the clock must never move back (PR #57 review). */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ChainClock { 0L }

    @Test
    fun `given two reads out, when the earlier one answers last, then the clock keeps the later one's time`() = runTest(dispatcher) {
        val vm = MainViewModel()
        val early = CompletableDeferred<Rpc.ChainTime?>()
        val late = CompletableDeferred<Rpc.ChainTime?>()
        launch { vm.refreshChainTime(clock) { early.await() } }
        runCurrent()
        launch { vm.refreshChainTime(clock) { late.await() } }
        runCurrent()

        late.complete(Rpc.ChainTime(unixSeconds = 2_000, slot = 20))
        runCurrent()
        early.complete(Rpc.ChainTime(unixSeconds = 1_000, slot = 10))
        runCurrent()

        assertEquals(2_000L, clock.nowSeconds())
        assertEquals(20L, clock.slot)
    }

    @Test
    fun `given a read that answers, when it is the only one, then the clock takes it and the read counts as answered`() = runTest(dispatcher) {
        val vm = MainViewModel()
        var answered = false
        launch { answered = vm.refreshChainTime(clock) { Rpc.ChainTime(unixSeconds = 1_000, slot = 10) } }
        runCurrent()

        assertTrue(answered)
        assertEquals(1_000L, clock.nowSeconds())
    }

    @Test
    fun `given a read that fails, when it ends, then the clock is untouched`() = runTest(dispatcher) {
        val vm = MainViewModel()
        var answered = true
        launch { answered = vm.refreshChainTime(clock) { null } }
        runCurrent()

        assertFalse(answered)
        assertFalse(clock.hasReading)
    }
}
