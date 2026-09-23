package holdout.crank

import com.solana.publickey.SolanaPublicKey
import holdout.core.CohortSnapshot
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.HoldoutDay
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `crank status` (T036): every cohort, and the faucet's three health figures —
 * the vault's balance, the sponsor's balance against its float, and the day's
 * lamport spend against its cap. What the live period is monitored on (T110,
 * FR-042b). Read-only: it signs nothing and needs no key.
 */
internal object Status {
    suspend fun run(config: CrankConfig, rpc: Rpc): JsonObject {
        val time = rpc.chainTime() ?: failed("could not read chain time from ${config.rpcUrl}")
        val configAddress = Pdas.config(config.programId)
        val faucetVault = Pdas.faucetVault(config.programId)

        // The cluster answered the clock, so a missing Config is an
        // uninitialised program, not a network failure.
        val snapshot =
            rpc.accountData(configAddress)?.let {
                runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: failed("the account at $configAddress is not a Config")
            }
        val cohorts = rpc.cohorts(config.programId) ?: failed("could not list cohorts from ${config.rpcUrl}")
        val vaultLamports = rpc.balance(faucetVault) ?: failed("could not read the faucet vault's balance")
        val sponsorLamports = snapshot?.let { rpc.balance(it.sponsor) ?: failed("could not read the sponsor's balance") }

        return report(
            chainTime = time.unixSeconds,
            slot = time.slot,
            configAddress = configAddress,
            config = snapshot,
            faucetVault = faucetVault,
            faucetVaultLamports = vaultLamports,
            sponsorLamports = sponsorLamports,
            cohorts = cohorts,
        )
    }

    /** The summary, from what was read — pure, so it is tested without a cluster. */
    fun report(
        chainTime: Long,
        slot: Long,
        configAddress: SolanaPublicKey,
        config: ConfigSnapshot?,
        faucetVault: SolanaPublicKey,
        faucetVaultLamports: Long,
        sponsorLamports: Long?,
        cohorts: List<Pair<SolanaPublicKey, CohortSnapshot>>,
    ): JsonObject {
        val today = HoldoutDay.indexOf(chainTime)
        return buildJsonObject {
            put("command", "status")
            put("chainTime", chainTime)
            put("slot", slot)
            put("globalDay", today)

            if (config == null) {
                put("config", JsonNull)
            } else {
                putJsonObject("config") {
                    put("address", configAddress.base58())
                    put("operator", config.operator.base58())
                    put("ladderGateEnabled", config.ladderGateEnabled)
                    put("genesisGateEnabled", config.genesisGateEnabled)
                    putJsonObject("chipSet") {
                        put("version", config.chipVersion)
                        putJsonArray("amounts") { config.chipAmounts.forEach { add(JsonPrimitive(it.toBig())) } }
                        put("maxTopTier", JsonPrimitive(config.maxTopTier.toBig()))
                        put("publishedAt", config.chipPublishedAt)
                    }
                }
            }

            putJsonObject("faucet") {
                put("vault", faucetVault.base58())
                put("vaultLamports", faucetVaultLamports)
                put("sponsor", config?.sponsor?.base58())
                put("sponsorLamports", sponsorLamports)
                put("sponsorFloat", config?.sponsorFloat?.toBig()?.let(::JsonPrimitive) ?: JsonNull)
                // `Faucet::sponsor_refill`: allowed only below half the float (FR-042b).
                put(
                    "sponsorRefillDue",
                    if (config == null || sponsorLamports == null) {
                        JsonNull
                    } else {
                        JsonPrimitive(sponsorLamports.toULong() < config.sponsorFloat / 2uL)
                    },
                )
                put("lamportSpend", config?.let { "${it.lamportsSpentOn(today)} / ${it.dailyLamportCap}" })
            }

            putJsonArray("cohorts") {
                cohorts
                    .sortedWith(compareBy({ it.second.leagueId }, { it.second.index }))
                    .forEach { (address, c) ->
                        addJsonObject {
                            put("address", address.base58())
                            put("league", c.leagueId)
                            put("index", c.index)
                            put("status", Derive.status(c, chainTime).name)
                            put("start", c.start)
                            put("memberCount", c.memberCount)
                            put("forfeitsRecorded", c.forfeitsRecorded)
                            put("counted", c.counted)
                            put("settled", c.settled)
                            put("heldStake", JsonPrimitive(c.heldStake.toBig()))
                            put("pool", JsonPrimitive(c.pool.toBig()))
                            put("carryOverReceived", JsonPrimitive(c.carryOverReceived.toBig()))
                            put("carryOverHeld", JsonPrimitive(c.carryOverHeld.toBig()))
                        }
                    }
            }
        }
    }
}
