package holdout.core

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.KtorNetworkDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout

/**
 * The HTTP client under every chain read, and how it recovers when it gets stuck (spec FR-069i, T415).
 *
 * On a Seeker, after a network change or a stretch in the background, every call through the app's client
 * failed with "Connect timeout has expired" while the node answered everything else, and only a restart of
 * the app cleared it. So the client is not kept for the life of the process: it is closed and a new one
 * built when the member presses the devnet bar's *Retry* ([reset]), and on its own after
 * [CONNECT_TIMEOUTS_BEFORE_REBUILD] connect timeouts in a row. Only a connect timeout counts: a node that
 * answered slowly or refused is reached, and a new client would not change that.
 *
 * The client is built on the first read, not before. A call already out on a client that has been replaced
 * finishes on it ([HttpClient.close] lets it), and its failure counts against nothing.
 */
class RecoveringDriver(
    private val connect: () -> Connection,
    private val timeoutsBeforeRebuild: Int = CONNECT_TIMEOUTS_BEFORE_REBUILD,
) : HttpNetworkDriver {
    /** One built client: what calls go through, and how it is closed. */
    class Connection(val driver: HttpNetworkDriver, val close: () -> Unit)

    private val lock = Any()
    private var current: Connection? = null
    private var timeoutsInARow = 0

    private fun connection(): Connection = synchronized(lock) { current ?: connect().also { current = it } }

    /** Throws the client away: the next read builds a new one. Nothing is built if no read has been made. */
    fun reset() {
        val old =
            synchronized(lock) {
                current.also {
                    current = null
                    timeoutsInARow = 0
                }
            }
        old?.close?.invoke()
    }

    override suspend fun makeHttpRequest(request: HttpRequest): String {
        val used = connection()
        val answer =
            try {
                used.driver.makeHttpRequest(request)
            } catch (e: Throwable) {
                if (e.isConnectTimeout()) timedOut(used)
                throw e
            }
        synchronized(lock) { if (current === used) timeoutsInARow = 0 }
        return answer
    }

    private fun timedOut(used: Connection) {
        val stale =
            synchronized(lock) {
                if (current !== used || ++timeoutsInARow < timeoutsBeforeRebuild) return
                current = null
                timeoutsInARow = 0
                used
            }
        stale.close()
    }

    companion object {
        /** How long a call may take to reach the node. */
        const val CONNECT_TIMEOUT_MILLIS = 10_000L

        /** How long a whole call may take, answer included. */
        const val REQUEST_TIMEOUT_MILLIS = 20_000L

        /** Connect timeouts in a row on one client before it is thrown away. */
        const val CONNECT_TIMEOUTS_BEFORE_REBUILD = 3

        /** The one driver every chain reader in the process shares, so one rebuild serves them all. */
        val shared: RecoveringDriver by lazy { RecoveringDriver(::ktor) }

        /** A Ktor CIO client with the limits above, as the RPC library's driver. */
        fun ktor(): Connection {
            val client =
                HttpClient(CIO) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    }
                }
            return Connection(KtorNetworkDriver(client), client::close)
        }

        private fun Throwable.isConnectTimeout(): Boolean = generateSequence(this) { it.cause }.take(8).any { it is ConnectTimeoutException }
    }
}
