package holdout.app.names

/**
 * Which wallet's Seeker ID to look up next, and when (spec 003 data-model §4,
 * FR-012). Pure: no Android, no coroutines, an explicit clock in
 * milliseconds — [SeekerNameRepository] drives it.
 *
 * - **Lazy**: only the connected wallet and the rows on screen are ever
 *   looked up, the connected wallet first. A row that leaves the screen
 *   before its search starts is dropped (research R11).
 * - **Paced** under the public mainnet node's limit of about 10 owner
 *   searches per 10 s: one search per [paceMillis], and nothing for
 *   [backOffMillis] after a failed one (research R4).
 * - A wallet that failed [maxFailures] times is left as its address until
 *   the next session.
 *
 * Not thread-safe; the repository holds it under one lock.
 */
class LookupQueue(
    private val paceMillis: Long = 1_200,
    private val backOffMillis: Long = 15_000,
    private val maxFailures: Int = 2,
) {
    private var connected: String? = null
    private var onScreen: Set<String> = emptySet()

    /** Waiting to be searched, in the order they were asked for. */
    private val pending = LinkedHashSet<String>()
    private val searching = mutableSetOf<String>()
    private val failures = mutableMapOf<String, Int>()

    private var lastStart: Long? = null
    private var backOffUntil = Long.MIN_VALUE

    /**
     * [wallet] is the connected wallet, searched before anything on screen;
     * null when none is. The wallet it replaces is dropped unsearched unless
     * it is a row on screen (FR-012).
     */
    fun connected(wallet: String?) {
        val previous = connected
        connected = wallet
        if (previous != null && previous != wallet && previous !in onScreen) pending -= previous
        wallet?.let(::want)
    }

    /**
     * [wallets] are the rows now on screen, top first, replacing the last
     * set. Pending wallets that left the screen are dropped unsearched.
     */
    fun showing(wallets: Collection<String>) {
        onScreen = LinkedHashSet(wallets)
        pending.retainAll { it == connected || it in onScreen }
        wallets.forEach(::want)
    }

    /** The wallet to search now, or null when none is due or the pace forbids it. It counts as searching. */
    fun next(nowMillis: Long): String? {
        if (waitMillis(nowMillis) != 0L) return null
        val wallet = connected?.takeIf { it in pending } ?: pending.first()
        pending -= wallet
        searching += wallet
        lastStart = nowMillis
        return wallet
    }

    /**
     * How long until [next] can hand out a wallet: null when none is pending,
     * 0 when it can now.
     */
    fun waitMillis(nowMillis: Long): Long? {
        if (pending.isEmpty()) return null
        val paced = lastStart?.let { it + paceMillis } ?: Long.MIN_VALUE
        val due = maxOf(paced, backOffUntil)
        // Compared before subtracting: MIN_VALUE - now would wrap around.
        return if (due <= nowMillis) 0 else due - nowMillis
    }

    /** [wallet]'s search answered — a name or none. */
    fun answered(wallet: String) {
        searching -= wallet
    }

    /**
     * [wallet]'s search got no answer at [nowMillis]: nothing is searched for
     * [backOffMillis], and the wallet goes back in line if it is still wanted.
     */
    fun failed(wallet: String, nowMillis: Long) {
        searching -= wallet
        backOffUntil = nowMillis + backOffMillis
        failures[wallet] = (failures[wallet] ?: 0) + 1
        if (wallet == connected || wallet in onScreen) want(wallet)
    }

    private fun want(wallet: String) {
        if (wallet in searching || (failures[wallet] ?: 0) >= maxFailures) return
        pending += wallet
    }
}
