package holdout.core

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T390 — a stuck network client recovers without a restart (FR-069i). On the Seeker every read failed with
 * "Connect timeout has expired" while the node answered everything else, until the app was restarted. The
 * driver throws its client away and builds a new one when the member presses Retry ([RecoveringDriver.reset])
 * and after 3 connect timeouts in a row; the next read goes through the new client. No network: each client
 * is a fake whose next answers the test scripts.
 */
class RecoveringDriverTest {

    /** One built client: it answers from [script] in order, and knows whether it was closed. */
    private class FakeClient(val number: Int, private val script: ArrayDeque<() -> String>) : HttpNetworkDriver {
        val asked = mutableListOf<String>()
        var closed = false

        /** When set, every call waits for it before it answers. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            asked += request.body.orEmpty()
            gate?.await()
            return script.removeFirstOrNull()?.invoke() ?: "ok from $number"
        }
    }

    /** Builds clients that share one script, so a test says what the next call meets, whichever client gets it. */
    private class Factory {
        val script = ArrayDeque<() -> String>()
        val built = mutableListOf<FakeClient>()

        fun connect(): RecoveringDriver.Connection {
            val client = FakeClient(built.size + 1, script)
            built += client
            return RecoveringDriver.Connection(client) { client.closed = true }
        }
    }

    private fun request(body: String) = object : HttpRequest {
        override val url = "http://rpc.test"
        override val method = "POST"
        override val properties: Map<String, String> = emptyMap()
        override val body = body
    }

    private val connectTimeout: () -> String = { throw ConnectTimeoutException("Connect timeout has expired [url=http://rpc.test, connect_timeout=unknown ms]") }

    private val factory = Factory()
    private val driver = RecoveringDriver(factory::connect)

    private fun read(body: String = "read"): String = runBlocking { driver.makeHttpRequest(request(body)) }

    private fun timedOut() {
        assertFailsWith<ConnectTimeoutException> { read("timed out") }
    }

    @Test
    fun `no client is built before the first read, and reads share one client`() {
        assertEquals(0, factory.built.size)
        read()
        read()
        assertEquals(1, factory.built.size)
        assertEquals(2, factory.built[0].asked.size)
    }

    @Test
    fun `given a reset, the old client is closed and the next read goes through a new one`() {
        read()
        driver.reset()

        assertTrue(factory.built[0].closed)
        assertEquals("ok from 2", read("after"))
        assertEquals(listOf("after"), factory.built[1].asked)
        assertEquals(false, factory.built[1].closed)
    }

    @Test
    fun `a reset before any read builds nothing`() {
        driver.reset()
        assertEquals(0, factory.built.size)
    }

    @Test
    fun `given 3 connect timeouts in a row, the client is rebuilt and the next read goes through the new one`() {
        repeat(3) { factory.script += connectTimeout }
        repeat(3) { timedOut() }

        assertTrue(factory.built[0].closed)
        assertEquals("ok from 2", read("after"))
        assertEquals(listOf("after"), factory.built[1].asked)
    }

    @Test
    fun `given 2 connect timeouts in a row, the client is kept`() {
        repeat(2) { factory.script += connectTimeout }
        repeat(2) { timedOut() }

        assertEquals(false, factory.built[0].closed)
        assertEquals("ok from 1", read())
        assertEquals(1, factory.built.size)
    }

    @Test
    fun `given connect timeouts broken by an answer, the count starts again`() {
        factory.script += listOf(connectTimeout, connectTimeout, { "answered" }, connectTimeout, connectTimeout)
        timedOut()
        timedOut()
        assertEquals("answered", read())
        timedOut()
        timedOut()

        assertEquals(1, factory.built.size)
        assertEquals(false, factory.built[0].closed)
    }

    @Test
    fun `a request timeout, or any other failure, is not a connect timeout`() {
        factory.script += listOf(
            { throw HttpRequestTimeoutException("http://rpc.test", 20_000) },
            { throw java.io.IOException("reset by peer") },
            connectTimeout,
            connectTimeout,
        )
        assertFailsWith<HttpRequestTimeoutException> { read() }
        assertFailsWith<java.io.IOException> { read() }
        timedOut()
        timedOut()

        assertEquals(1, factory.built.size)
    }

    @Test
    fun `a connect timeout wrapped in another exception still counts`() {
        repeat(3) { factory.script += { throw IllegalStateException("call failed", ConnectTimeoutException("Connect timeout has expired")) } }
        repeat(3) { assertFailsWith<IllegalStateException> { read() } }

        assertTrue(factory.built[0].closed)
    }

    @Test
    fun `after a rebuild, the count starts again for the new client`() {
        repeat(5) { factory.script += connectTimeout }
        repeat(5) { timedOut() }

        assertEquals(2, factory.built.size)
        assertEquals(false, factory.built[1].closed)
    }

    @Test
    fun `late connect timeouts from a client already replaced do not count against the new one`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val slow = Factory()
        val waiting = RecoveringDriver({ slow.connect().also { (it.driver as FakeClient).gate = gate } })
        // Three reads out on the first client, all of them stuck until the gate opens.
        val calls = List(3) { async(start = CoroutineStart.UNDISPATCHED) { runCatching { waiting.makeHttpRequest(request("stuck")) } } }
        slow.script += List(3) { { throw ConnectTimeoutException("Connect timeout has expired") } }
        waiting.reset()
        gate.complete(Unit)
        calls.awaitAll()

        assertTrue(slow.built[0].closed)
        assertEquals(1, slow.built.size)
        assertEquals("ok from 2", waiting.makeHttpRequest(request("after")))
        assertEquals(false, slow.built[1].closed)
    }

    @Test
    fun `an Rpc's reset reaches the driver it was given`() {
        var resets = 0
        val rpc = Rpc("http://rpc.test", reconnect = { resets++ })
        rpc.reset()
        assertEquals(1, resets)
    }

    @Test
    fun `the real client builds with its timeouts and closes`() {
        val connection = RecoveringDriver.ktor()
        assertTrue(connection.driver is com.solana.networking.KtorNetworkDriver)
        connection.close()
    }

    @Test
    fun `the shared driver sets the agreed limits`() {
        assertEquals(10_000L, RecoveringDriver.CONNECT_TIMEOUT_MILLIS)
        assertEquals(20_000L, RecoveringDriver.REQUEST_TIMEOUT_MILLIS)
        assertEquals(3, RecoveringDriver.CONNECT_TIMEOUTS_BEFORE_REBUILD)
    }
}
