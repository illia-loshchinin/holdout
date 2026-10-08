package holdout.crank

import com.solana.publickey.SolanaPublicKey
import holdout.core.CohortSnapshot
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.HoldoutDay
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
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
 * `crank status` (T036): every cohort, every league with its form and its two
 * reserves (H35), and the faucet's three health figures — the vault's balance,
 * the sponsor's balance against its float, and the day's lamport spend against
 * its cap. What the live period is monitored on (T110, FR-042b). Read-only: it
 * signs nothing and needs no key.
 */
internal object Status {
    suspend fun run(config: CrankConfig, rpc: Rpc): JsonObject {
        val time = rpc.chainTime() ?: failed("could not read chain time from ${config.rpcUrl}")
        val configAddress = Pdas.config(config.programId)
        val faucetVault = Pdas.faucetVault(config.programId)

        // Only an answer that the account does not exist means an
        // uninitialised program; a failed read is a failed read, even right
        // after the clock answered.
        val snapshot =
            when (val read = rpc.account(configAddress)) {
                is Rpc.Answer.Refused -> failed("could not read Config at $configAddress: ${read.message}")
                is Rpc.Answer.Unanswered -> failed("could not read Config at $configAddress: ${read.message}")
                is Rpc.Answer.Ok ->
                    read.value?.let {
                        runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: failed("the account at $configAddress is not a Config")
                    }
            }
        val cohorts = rpc.cohorts(config.programId) ?: failed("could not list cohorts from ${config.rpcUrl}")
        // The two judged leagues, so a run after `crank init` shows they exist and when they start…
        val judged =
            listOf(LeagueParams.LEAGUE_7D, LeagueParams.LEAGUE_21D).map { id ->
                val address = Pdas.league(config.programId, id)
                id to
                    when (val read = rpc.account(address)) {
                        is Rpc.Answer.Refused -> failed("could not read league $id at $address: ${read.message}")
                        is Rpc.Answer.Unanswered -> failed("could not read league $id at $address: ${read.message}")
                        is Rpc.Answer.Ok -> read.value?.let { runCatching { LeagueSnapshot.decode(it) }.getOrNull() ?: failed("the account at $address is not a LeagueConfig") }
                    }
            }
        // …and any test league beside them.
        val tests =
            (rpc.leagues(config.programId) ?: failed("could not list leagues from ${config.rpcUrl}"))
                .filter { it.params.leagueId >= LeagueParams.TEST_LEAGUE_MIN }
                .sortedBy { it.params.leagueId }
                .map { it.params.leagueId to it }
        val leagues = judged + tests
        // What each reserve vault really holds, beside what the league says it holds (INV-1). Null when unreadable.
        val reserveVaults =
            leagues.filter { it.second != null }.associate { (id, _) ->
                val vault = Pdas.reserveVault(config.programId, id)
                id to (vault to rpc.tokenAmount(vault))
            }
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
            leagues = leagues,
            reserveVaults = reserveVaults,
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
        /** Each judged league id, then each test league, with its stored account or null when it does not exist yet. */
        leagues: List<Pair<Int, LeagueSnapshot?>> = emptyList(),
        /** Each existing league's reserve vault, and the SKR it holds (null when the read failed). */
        reserveVaults: Map<Int, Pair<SolanaPublicKey, ULong?>> = emptyMap(),
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
                    // The two fee destinations (H35, FR-037f): operations and developer.
                    put("feeDestination", config.feeDestination.base58())
                    put("developerDestination", config.developerDestination.base58())
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

            putJsonArray("leagues") {
                leagues.forEach { (id, league) ->
                    addJsonObject {
                        put("league", id)
                        put("exists", league != null)
                        if (league != null) {
                            put("durationDays", league.params.durationDays)
                            put("repsPerDay", league.params.repsPerDay)
                            put("form", league.form)
                            put("formName", LeagueParams.formName(league.form))
                            put("firstStart", league.params.firstStart)
                            put("nextIndex", league.nextIndex)
                            put("boostReserve", JsonPrimitive(league.boostReserve.toBig()))
                            put("comebackReserve", JsonPrimitive(league.comebackReserve.toBig()))
                            reserveVaults[id]?.let { (vault, amount) ->
                                put("reserveVault", vault.base58())
                                put("reserveVaultAmount", amount?.toBig()?.let(::JsonPrimitive) ?: JsonNull)
                            }
                        }
                    }
                }
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
                            put("form", c.form)
                            put("pool", JsonPrimitive(c.pool.toBig()))
                            // Reserve shares held in the vault until the cohort finalizes (FR-037c).
                            put("boostDue", JsonPrimitive(c.boostDue.toBig()))
                            put("comebackDue", JsonPrimitive(c.comebackDue.toBig()))
                            // Written by settlement (FR-037d, FR-037e).
                            put("boostReceived", JsonPrimitive(c.boostReceived.toBig()))
                            put("comebackOwed", JsonPrimitive(c.comebackOwed.toBig()))
                            put("comebackFunded", JsonPrimitive(c.comebackFunded.toBig()))
                            put("comebackClaimed", JsonPrimitive(c.comebackClaimed.toBig()))
                        }
                    }
            }
        }
    }
}
