package holdout.app

import androidx.lifecycle.ViewModel
import holdout.app.chain.Deployment
import holdout.core.ChainClock
import holdout.core.Rpc

/**
 * Chain time for the whole app (research R7, T033). The wallet itself lives
 * in `WalletRepository` (T040), and chain state in `HoldoutViewModel`.
 */
class MainViewModel : ViewModel() {
    // Devnet only, everywhere (README: nothing here touches mainnet). The app
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
}
