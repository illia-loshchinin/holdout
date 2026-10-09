package holdout.app.names

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Seeker ID lookup queue (spec 003 data-model §4, FR-012, SC-002): paced
 * under the public node's limit, the connected wallet first, then the rows on
 * screen and nothing else. An explicit clock in milliseconds; no coroutines.
 */
class LookupQueueTest {

    private val queue = LookupQueue()

    /** Everything handed out by [until], answering each at once. */
    private fun drain(until: Long, step: Long = 100): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        var t = 0L
        while (t <= until) {
            queue.next(t)?.let {
                out += t to it
                queue.answered(it)
            }
            t += step
        }
        return out
    }

    @Test
    fun `at most one owner search per 1200 ms`() {
        queue.showing(listOf("a", "b", "c"))
        assertEquals("a", queue.next(0))
        assertNull(queue.next(1_199))
        assertEquals("b", queue.next(1_200))
        assertNull(queue.next(2_000))
        assertEquals("c", queue.next(2_400))
    }

    @Test
    fun `the connected wallet comes before any row on screen`() {
        queue.showing(listOf("a", "b"))
        queue.connected("me")
        assertEquals(listOf("me", "a", "b"), drain(5_000).map { it.second })
    }

    @Test
    fun `rows that leave the screen before their search starts are dropped`() {
        queue.showing(listOf("a", "b", "c"))
        assertEquals("a", queue.next(0))
        queue.answered("a")
        queue.showing(listOf("c", "d"))
        assertEquals(listOf("c", "d"), drain(5_000).map { it.second }.filter { it != "a" })
    }

    @Test
    fun `a dropped row is queued again when it comes back into view`() {
        queue.showing(listOf("a"))
        queue.showing(emptyList())
        assertNull(queue.next(0))
        queue.showing(listOf("a"))
        assertEquals("a", queue.next(0))
    }

    @Test
    fun `a wallet neither connected nor on screen is never handed out`() {
        queue.showing(listOf("a"))
        queue.showing(emptyList())
        queue.connected("me")
        assertEquals(listOf("me"), drain(10_000).map { it.second })
    }

    @Test
    fun `a wallet already pending is not queued twice`() {
        queue.showing(listOf("a", "a"))
        queue.showing(listOf("a"))
        queue.connected("a")
        assertEquals(listOf("a"), drain(10_000).map { it.second })
    }

    @Test
    fun `a wallet being searched is not queued again`() {
        queue.showing(listOf("a"))
        assertEquals("a", queue.next(0))
        queue.showing(listOf("a"))
        assertNull(queue.next(5_000))
    }

    @Test
    fun `after a failed search, nothing is searched for 15 s and the wallet goes back if still on screen`() {
        queue.showing(listOf("a", "b"))
        assertEquals("a", queue.next(0))
        queue.failed("a", 300)
        assertNull(queue.next(1_200))
        assertNull(queue.next(15_299))
        val after = drain(40_000).filter { it.first >= 15_300 }.map { it.second }
        assertEquals(listOf("b", "a"), after)
    }

    @Test
    fun `a failed wallet that left the screen is not searched again`() {
        queue.showing(listOf("a"))
        assertEquals("a", queue.next(0))
        queue.showing(emptyList())
        queue.failed("a", 0)
        assertTrue(drain(60_000).isEmpty())
    }

    @Test
    fun `a wallet that failed twice is not queued again this session`() {
        queue.showing(listOf("a"))
        assertEquals("a", queue.next(0))
        queue.failed("a", 0)
        assertEquals("a", queue.next(15_000))
        queue.failed("a", 15_000)
        assertNull(queue.next(60_000))
        queue.showing(emptyList())
        queue.showing(listOf("a"))
        queue.connected("a")
        assertNull(queue.next(120_000))
    }

    @Test
    fun `SC-002 budget - the connected wallet and ten rows on screen are all searched within 12 s`() {
        queue.connected("me")
        queue.showing((1..10).map { "row$it" })
        val handed = drain(12_000)
        assertEquals(11, handed.size)
        assertEquals("me", handed.first().second)
        assertTrue(handed.last().first <= 12_000)
    }

    @Test
    fun `how long to wait - nothing pending is no wait at all, else until the pace allows`() {
        assertNull(queue.waitMillis(0))
        queue.showing(listOf("a", "b"))
        assertEquals(0, queue.waitMillis(0))
        assertEquals("a", queue.next(0))
        assertEquals(1_200, queue.waitMillis(0))
        assertEquals(200, queue.waitMillis(1_000))
    }
}
