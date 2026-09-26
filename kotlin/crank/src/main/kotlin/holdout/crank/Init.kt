package holdout.crank

import com.solana.publickey.SolanaPublicKey
import holdout.core.ConfigSnapshot
import holdout.core.FaucetParams
import holdout.core.Instructions
import holdout.core.LeagueParams
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * `crank init` (T091, contracts/crank-cli.md, quickstart §6): everything the
 * program needs before a cohort can open, in the one order that is safe.
 *
 * 1. `initialize`, signed by the **program's upgrade authority** (`--admin` or
 *    `HOLDOUT_ADMIN_KEYPAIR`), which the program checks (6003) — plus a fresh
 *    keypair for the mock SKR mint.
 * 2. The first `publish_chip_set` from `--rate`, signed by the Operator
 *    (`HOLDOUT_OPERATOR_KEYPAIR`) — **before any league exists**, so no cohort
 *    can ever be opened against a zero chip set (6012, FR-055b).
 * 3. `create_league` for the 7-day (0) and 21-day (1) leagues on the devnet
 *    clock, first start Monday 28 September 2026 05:00 UTC unless
 *    `--first-start` says otherwise.
 * 4. With `--fund-faucet <sol>`, a transfer from the admin that tops the
 *    faucet vault up to that balance (research R13).
 *
 * Every step reads the chain first and is skipped when already done, so a run
 * that stopped part-way is finished by running it again, and a run over a
 * set-up program sends nothing. Every input a step needs is checked before
 * the first transaction is sent.
 */
object Init {
    fun checkArgs(args: Args) = args.allow("admin", "operator", "sponsor", "treasury", "rate", "first-start", "fund-faucet")

    private class Step(val name: String, val status: String, val detail: JsonObjectBuilder.() -> Unit = {})

    suspend fun run(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        loadKeypair: (path: String, role: String) -> Keypair = Keypair::load,
        newMint: () -> Keypair = Keypair::generate,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): JsonObject {
        checkArgs(args)
        val programId = config.programId
        val firstStart = firstStart(args.option("first-start"))
        val faucetTarget = args.option("fund-faucet")?.let(::lamports)

        // ---- read what is already there ------------------------------------
        val configAddress = Pdas.config(programId)
        val onChain = readConfig(rpc, configAddress)
        val missingLeagues =
            listOf(LeagueParams.LEAGUE_7D, LeagueParams.LEAGUE_21D).filter { id ->
                val address = Pdas.league(programId, id)
                when (val read = rpc.account(address)) {
                    is Rpc.Answer.Ok -> read.value == null
                    is Rpc.Answer.Refused -> failed("could not read league $id at $address: ${read.message}")
                    is Rpc.Answer.Unanswered -> failed("could not read league $id at $address: ${read.message}")
                }
            }
        val vault = Pdas.faucetVault(programId)
        val vaultBalance = if (onChain != null) rpc.balance(vault) ?: failed("could not read the faucet vault's balance") else 0L

        // ---- check every input before sending anything ---------------------
        val needInitialize = onChain == null
        val needChips = onChain == null || onChain.chipVersion == 0L
        val needAdmin = needInitialize || missingLeagues.isNotEmpty() || (faucetTarget != null && faucetTarget > vaultBalance)

        val admin =
            if (needAdmin) {
                val path = args.option("admin") ?: config.adminKeypair ?: configError("init needs the upgrade authority's keypair: --admin <path> or HOLDOUT_ADMIN_KEYPAIR")
                loadKeypair(path, "admin")
            } else {
                null
            }
        if (onChain != null && admin != null && missingLeagues.isNotEmpty() && admin.publicKey != onChain.admin) {
            configError("the admin key is ${admin.publicKey}, but Config.admin is ${onChain.admin}; only the admin may create a league (6002)")
        }
        val operator = if (onChain == null) pubkey(args, "operator") else onChain.operator
        val sponsor = if (needInitialize) pubkey(args, "sponsor") else null
        val treasury = if (needInitialize) pubkey(args, "treasury") else null
        val rate = if (needChips) args.option("rate") ?: configError("the chip set is not published yet: init needs --rate <usd-per-skr>") else null
        if (rate != null) {
            // Checked now, not at step 2, so a bad rate never leaves a program initialised and unpriced.
            runCatching { Chips.price(Chips.parseRate(rate)) }.onFailure { configError(it.message ?: "invalid --rate") }
            val operatorKey = loadKeypair(config.requireOperator(), "operator")
            if (operatorKey.publicKey != operator) {
                configError("HOLDOUT_OPERATOR_KEYPAIR is ${operatorKey.publicKey}, but the operator is $operator; only the Operator may publish the chip set")
            }
        }

        // ---- the steps -------------------------------------------------------
        val steps = mutableListOf<Step>()
        val dryRun = args.dryRun

        steps +=
            if (!needInitialize) {
                Step("initialize", "skipped") { put("admin", onChain!!.admin.base58()) }
            } else {
                val mint = newMint()
                val ix = Instructions.initialize(programId, admin!!.publicKey, mint.publicKey, operator, sponsor!!, treasury!!, FaucetParams())
                val signature = signAndSend(rpc, "initialize", listOf(ix), listOf(admin, mint), dryRun, pause)
                Step("initialize", if (dryRun) "simulated" else "sent") {
                    signature?.let { put("signature", it) }
                    put("skrMint", mint.publicKey.base58())
                    put("admin", admin.publicKey.base58())
                    put("operator", operator.base58())
                }
            }
        val live = !needInitialize || !dryRun

        steps +=
            when {
                !needChips -> Step("chips", "skipped") { put("version", onChain!!.chipVersion) }
                !live -> Step("chips", "planned") { put("rate", rate) }
                else -> {
                    val flags = if (dryRun) setOf("dry-run") else emptySet()
                    val published = Chips.publish(Args(listOf("chips", "publish"), mapOf("rate" to rate!!), flags), config, rpc, loadKeypair, pause)
                    Step("chips", if (dryRun) "simulated" else "sent") { put("publish", published) }
                }
            }

        for (id in listOf(LeagueParams.LEAGUE_7D, LeagueParams.LEAGUE_21D)) {
            val name = "league $id"
            steps +=
                when {
                    id !in missingLeagues -> Step(name, "skipped")
                    !live -> Step(name, "planned") { put("firstStart", firstStart) }
                    else -> {
                        val ix = Instructions.createLeague(programId, admin!!.publicKey, LeagueParams.judged(id, firstStart))
                        val signature = signAndSend(rpc, "create_league $id", listOf(ix), listOf(admin), dryRun, pause)
                        Step(name, if (dryRun) "simulated" else "sent") {
                            signature?.let { put("signature", it) }
                            put("firstStart", firstStart)
                        }
                    }
                }
        }

        // Read again: `initialize` has just made the vault rent-exempt, and the
        // top-up is to the target, not by it.
        val vaultNow = if (needInitialize && live) rpc.balance(vault) ?: failed("could not read the faucet vault's balance") else vaultBalance
        val topUp = faucetTarget?.let { (it - vaultNow).coerceAtLeast(0) } ?: 0L
        steps +=
            when {
                topUp <= 0 -> Step("faucet", "skipped") { put("vaultLamports", vaultNow) }
                !live -> Step("faucet", "planned") { put("toLamports", faucetTarget) }
                else -> {
                    val ix = Instructions.systemTransfer(admin!!.publicKey, vault, topUp.toULong())
                    val signature = signAndSend(rpc, "faucet top-up", listOf(ix), listOf(admin), dryRun, pause)
                    Step("faucet", if (dryRun) "simulated" else "sent") {
                        signature?.let { put("signature", it) }
                        put("lamports", topUp)
                        put("vaultLamports", vaultNow + topUp)
                    }
                }
            }

        return buildJsonObject {
            put("command", "init")
            put("dryRun", dryRun)
            put("config", configAddress.base58())
            putJsonArray("steps") {
                steps.forEach { s ->
                    add(
                        buildJsonObject {
                            put("step", s.name)
                            put("status", s.status)
                            s.detail(this)
                        },
                    )
                }
            }
        }
    }

    private suspend fun readConfig(rpc: Rpc, address: SolanaPublicKey): ConfigSnapshot? = when (val read = rpc.account(address)) {
        is Rpc.Answer.Refused -> failed("could not read Config at $address: ${read.message}")
        is Rpc.Answer.Unanswered -> failed("could not read Config at $address: ${read.message}")
        is Rpc.Answer.Ok ->
            read.value?.let {
                runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: configError("the account at $address is not a Config")
            }
    }

    private fun pubkey(args: Args, name: String): SolanaPublicKey {
        val text = args.option(name) ?: configError("the program is not initialized yet: init needs --$name <pubkey>")
        return runCatching { SolanaPublicKey.from(text) }.getOrNull() ?: configError("--$name is not a public key: $text")
    }

    private fun firstStart(text: String?): Long {
        val at =
            text?.let {
                try {
                    Instant.parse(it).epochSecond
                } catch (e: DateTimeParseException) {
                    configError("--first-start must be an instant such as 2026-09-28T05:00:00Z, got $it")
                }
            } ?: LeagueParams.FIRST_JUDGED_START
        if (!LeagueParams.isMonday0500Utc(at)) {
            configError("--first-start must be a Monday at 05:00 UTC; the program refuses anything else for a judged league (InvalidLeagueTiming)")
        }
        return at
    }

    private fun lamports(sol: String): Long {
        val amount = runCatching { BigDecimal(sol.trim()) }.getOrNull()?.takeIf { it.signum() >= 0 } ?: configError("--fund-faucet must be a number of SOL, got $sol")
        return amount.movePointRight(9).toBigInteger().toLong()
    }
}
