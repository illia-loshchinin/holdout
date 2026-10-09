package holdout.app.names

import holdout.app.storage.KeptName
import holdout.app.storage.Store
import holdout.core.Lookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Members' Seeker IDs for display (spec 003, contracts §3.3): what the phone
 * keeps, and one worker that looks up the rest — lazily, paced, connected
 * wallet first ([LookupQueue]).
 *
 * Nothing here ever blocks a screen: [names] starts from the kept entries at
 * once, and a wallet with no name in it shows its shortened address (FR-006).
 * A lookup that gets no answer writes nothing, so a failure never turns a
 * name into "none" (data-model §2).
 */
class SeekerNameRepository(
    /** Looks one wallet up — `SeekerNameReader.nameOf` in the app, a fake in tests. */
    private val nameOf: suspend (wallet: String) -> Lookup,
    private val store: Store,
    /** Wall-clock time. It decides only when to ask again, never anything shown as a deadline (research R7). */
    private val now: () -> Long = System::currentTimeMillis,
    private val queue: LookupQueue = LookupQueue(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val kept = MutableStateFlow(store.allSeekerNames())

    /** Wallet base58 → chosen Seeker ID, for every kept entry that has one. */
    val names: StateFlow<Map<String, String>> = kept.asStateFlow()

    init {
        scope.launch { work() }
    }

    /**
     * [wallets] are the rows now on screen, top first (FR-012): the ones not
     * checked in the last day are looked up, and pending ones that left the
     * screen are dropped. Screens pass an empty list when they go away.
     */
    fun showing(wallets: Collection<String>) {
        val stale = wallets.filterNot(::fresh)
        synchronized(lock) { queue.showing(stale) }
        wake.trySend(Unit)
    }

    /** The connected wallet: checked again now, however fresh its entry (FR-007). */
    fun connected(wallet: String) {
        synchronized(lock) { queue.connected(wallet) }
        wake.trySend(Unit)
    }

    private fun fresh(wallet: String): Boolean = store.seekerName(wallet)?.let { now() - it.checkedAtMillis < FRESH_MILLIS } == true

    private suspend fun work() {
        while (currentCoroutineContext().isActive) {
            val wallet = synchronized(lock) { queue.next(now()) }
            if (wallet == null) {
                val wait = synchronized(lock) { queue.waitMillis(now()) }
                if (wait == null) wake.receive() else withTimeoutOrNull(wait) { wake.receive() }
                continue
            }
            val found = try {
                nameOf(wallet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Lookup.Unknown(rateLimited = false)
            }
            val at = now()
            when (found) {
                is Lookup.Found -> keep(wallet, KeptName(found.name, at))
                Lookup.None -> keep(wallet, KeptName(null, at))
                is Lookup.Unknown -> synchronized(lock) { queue.failed(wallet, at) }
            }
        }
    }

    private fun keep(wallet: String, entry: KeptName) {
        store.setSeekerName(wallet, entry)
        synchronized(lock) { queue.answered(wallet) }
        kept.update { if (entry.name == null) it - wallet else it + (wallet to entry.name) }
    }
}

/** A kept entry younger than this is shown without asking again (FR-007). */
private const val FRESH_MILLIS = 24 * 60 * 60 * 1_000L
