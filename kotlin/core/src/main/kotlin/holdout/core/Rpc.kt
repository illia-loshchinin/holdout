package holdout.core

import com.solana.networking.KtorNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.rpccore.JsonRpc20Request
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reading the cluster (research R8, T034 groundwork).
 *
 * Shared by the app and the crank, like everything else in this module, so
 * there is one RPC path and not two that drift (R12).
 *
 * Only what the Daily 25 needs is here: the cluster's own clock. Account reads
 * — `getProgramAccounts` with a `memcmp` on the cohort key and a `dataSlice`,
 * refreshed every 30 seconds while a cohort screen is visible — arrive with the
 * cohort screens.
 */
class Rpc(
    private val endpoint: String = DEVNET,
    driver: Rpc20Driver = Rpc20Driver(endpoint, KtorNetworkDriver()),
) {
    private val rpc = driver

    companion object {
        /**
         * Devnet, everywhere. Nothing in this build touches mainnet or real
         * value, and there is no code path here that could.
         */
        const val DEVNET = "https://api.devnet.solana.com"
    }

    data class ChainTime(val unixSeconds: Long, val slot: Long)

    /**
     * The cluster's time, as two calls: the current slot, then that slot's
     * block time.
     *
     * There is no single RPC for "what time does the chain think it is", and
     * the device's own clock is not an answer — every deadline in Holdout is
     * judged by chain time (FR-017), and a phone's clock can be wrong or moved
     * on purpose. Two round trips a few times an hour is the price; [ChainClock]
     * carries the value forward between them on monotonic time.
     *
     * Returns null when the cluster cannot be reached. The caller shows that it
     * does not know rather than guessing — and the Daily 25 keeps working
     * either way, because counting reps needs no connection at all (FR-024b).
     */
    suspend fun chainTime(): ChainTime? {
        val slot = request("getSlot") ?: return null
        val seconds = request("getBlockTime", JsonArray(listOf(JsonPrimitive(slot))))
            ?: return null
        return ChainTime(seconds, slot)
    }

    private suspend fun request(method: String, params: JsonArray? = null): Long? = runCatching {
        rpc.makeRequest(
            JsonRpc20Request(method, params ?: JsonArray(emptyList()), method),
            Long.serializer(),
        ).takeIf { it.error == null }?.result
    }.getOrNull()
}
