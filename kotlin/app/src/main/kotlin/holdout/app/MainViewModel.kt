package holdout.app

import androidx.lifecycle.ViewModel
import holdout.app.chain.Deployment
import holdout.core.ChainClock
import holdout.core.Rpc
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Chain time for the whole app (research R7, T033). The wallet itself lives
 * in `WalletRepository` (T040), and chain state in `HoldoutViewModel`.
 */
class MainViewModel : ViewModel() {
    // Devnet only, everywhere (README: no value touches mainnet; the only mainnet reads are Seeker ID names and the Seeker Genesis Token check, in SeekerNameReader and SeekerGenesisReader, neither able to send). The app
    // reads chain state through the same client the crank uses (R8, R12).
    private val rpcClient = Rpc(Deployment.rpcUrl)

    /**
     * Reads the cluster's clock into [clock]; true when it answered. Failure
     * is silent on purpose: the Daily 25 needs no connection at all
     * (FR-024b), so a phone with no signal counts reps exactly as before, and
     * the caller simply asks again later.
     */
    suspend fun refreshChainTime(clock: ChainClock): Boolean {
        val time = rpcClient.chainTime() ?: return false
        clock.observe(time.unixSeconds, time.slot)
        return true
    }

    /** The wait before chain time is asked again, cut short when the network comes back. */
    val retry = ChainTimeRetry()

    /** The activity was asked to open again, from the launcher or a reminder: Today (FR-069). */
    val opens = AppOpens()
}

/**
 * The wait between failed chain-time reads. A phone that starts with no
 * network and no saved reading knows no day until the chain answers, so the
 * network's return ends the wait at once rather than up to 30 seconds later —
 * a completed cohort day cannot send itself before then (T098).
 */
class ChainTimeRetry {
    // Conflated: a return reported while a read is out still ends the next wait, once.
    private val back = Channel<Unit>(Channel.CONFLATED)

    /** The system reports a network: safe from its own callback thread. */
    fun networkBack() {
        back.trySend(Unit)
    }

    /** Waits [millis], or until [networkBack], whichever comes first. */
    suspend fun await(millis: Long) {
        withTimeoutOrNull(millis) { back.receive() }
    }
}
