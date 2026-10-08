package holdout.app

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

/** Chain time is asked again as soon as the network comes back, not at the end of the 30-second wait (R7, T098). */
class ChainTimeRetryTest {
    private val hour = 3_600_000L

    @Test
    fun `given a wait for the next try, when the network comes back, then it ends at once`() = runBlocking {
        val retry = ChainTimeRetry()
        val waiting = async { retry.await(hour) }
        // The system's network callback runs on a thread of its own.
        thread {
            Thread.sleep(50)
            retry.networkBack()
        }
        withTimeout(5_000) { waiting.await() }
    }

    @Test
    fun `given the network came back during a read, when the wait starts, then it ends at once`() = runBlocking {
        val retry = ChainTimeRetry()
        retry.networkBack()
        withTimeout(5_000) { retry.await(hour) }
    }

    @Test
    fun `given no network back, when the wait starts, then it runs its full length`() = runBlocking {
        val retry = ChainTimeRetry()
        val started = System.nanoTime()
        retry.await(200)
        assertTrue(System.nanoTime() - started >= 200_000_000L)
    }

    @Test
    fun `given two returns, when two waits follow, then only the first is cut short`() = runBlocking {
        val retry = ChainTimeRetry()
        retry.networkBack()
        retry.networkBack()
        withTimeout(5_000) { retry.await(hour) }
        val started = System.nanoTime()
        retry.await(200)
        assertTrue(System.nanoTime() - started >= 200_000_000L)
    }
}
