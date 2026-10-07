package holdout.crank

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.TransactionInstruction
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.Instructions
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Pdas
import holdout.core.ProgramErrorCode
import holdout.core.Rpc
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * `crank run` (T071, contracts/crank-cli.md): one automation pass, in the one
 * order that cannot strand anything.
 *
 * 1. **Open** every league's due cohorts — index 0 at once, index *i* once
 *    cohort *i − 1* has started.
 * 2. **Refill** the faucet sponsor when it holds less than half its float
 *    (FR-042b). A refusal — float sufficient, the day's budget spent, the vault
 *    empty — is an expected outcome, reported and retried next pass.
 * 3. **Forfeit** every member of a running cohort who is in breach, in pages.
 *    Each forfeit's developer and operations parts leave at once, to
 *    `Config.developer_destination` and `Config.fee_destination` (H35).
 * 4. **Settle** each league's finished cohorts **oldest first**: page
 *    `settle_count` over the uncounted members, then `settle_finalize`, which
 *    draws the boost and the comeback funding from the league's reserve vault,
 *    credits the cohort's own reserve dues to it, and with no finishers sends
 *    the whole pool out, a third to the developer destination and the rest to
 *    operations (FR-037, FR-037c–e).
 *    Cohort *i* waits while *i − 1* is unsettled — the program refuses it
 *    (6045), so this is not a preference.
 *
 * Every step is idempotent on-chain, so two cranks, or a crank and a stranger,
 * running at once produce one outcome (FR-030a). A failed step is reported and
 * the pass carries on with whatever does not depend on it; the exit code is
 * then [EXIT_FAILED], with the full summary still printed.
 */
object Run {
    fun checkArgs(args: Args) = args.allow()

    /** Most cohorts one league opens in one pass — enough to catch up after a long outage, bounded all the same. */
    const val MAX_OPENS_PER_LEAGUE = 8

    // ---- the plan: pure, so it is tested without a cluster ----------------

    /**
     * The cohort indices [league] is due to open at [now]: `next_index`, and the
     * ones after it whose predecessor has started. [startOf] gives a cohort's
     * start — from its account when it exists, else from the league schedule.
     */
    fun dueOpens(league: LeagueSnapshot, now: Long, startOf: (Long) -> Long = { scheduledStart(league, it) }): List<Long> {
        val due = mutableListOf<Long>()
        var index = league.nextIndex
        while (due.size < MAX_OPENS_PER_LEAGUE && (index == 0L || now >= startOf(index - 1))) {
            due += index
            index++
        }
        return due
    }

    /** The program's `schedule::cohort_start`. */
    fun scheduledStart(league: LeagueSnapshot, index: Long): Long = league.params.firstStart + index * league.params.cadence

    /** Whether the sponsor is below half its float, the only state `top_up_sponsor` accepts. */
    fun sponsorRefillDue(config: ConfigSnapshot, sponsorLamports: Long): Boolean = sponsorLamports.toULong() < config.sponsorFloat / 2uL

    /** The wallets of [members] in breach at [now], in pages of [pageSize]. */
    fun forfeitPages(cohort: CohortSnapshot, members: List<MembershipSnapshot>, now: Long, pageSize: Int): List<List<SolanaPublicKey>> = members
        .filter { Derive.inBreach(it, cohort, now) }
        .map { it.wallet }
        .chunked(pageSize.coerceIn(1, Instructions.MAX_PAGE_PAIRS))

    /** Each league's cohorts awaiting settlement at [now], oldest first. */
    fun settleQueue(cohorts: Collection<CohortSnapshot>, now: Long): Map<Int, List<CohortSnapshot>> = cohorts
        .filter { Derive.status(it, now) == CohortStatus.Settling }
        .groupBy { it.leagueId }
        .mapValues { (_, list) -> list.sortedBy { it.index } }
        .toSortedMap()

    // ---- the pass ----------------------------------------------------------

    const val FAUCET_CAP_REACHED = 6051
    const val FAUCET_EMPTY = 6052
    const val SPONSOR_FLOAT_SUFFICIENT = 6054

    /** What became of one transaction. */
    enum class Outcome { Landed, Refused, Failed }

    private class Pass(val dryRun: Boolean) {
        val steps = mutableListOf<JsonObject>()
        var failures = 0

        fun record(step: String, status: String, detail: JsonObjectBuilder.() -> Unit = {}) {
            steps +=
                buildJsonObject {
                    put("step", step)
                    put("status", status)
                    detail()
                }
        }

        /**
         * Runs one transaction: [Outcome.Landed] when it landed (or simulated
         * cleanly). A refusal whose program error is in [expected] is recorded
         * as `refused` and is not a failure.
         */
        suspend fun send(step: String, detail: JsonObjectBuilder.() -> Unit, expected: Set<Int> = emptySet(), block: suspend () -> String?): Outcome = try {
            val signature = block()
            record(step, if (dryRun) "simulated" else "sent") {
                detail()
                signature?.let { put("signature", it) }
            }
            Outcome.Landed
        } catch (e: CrankException) {
            if (e.exitCode == EXIT_CONFIG) throw e
            val code = ProgramErrorCode.of(e.message)
            if (code != null && code in expected) {
                record(step, "refused") {
                    detail()
                    put("programError", code)
                    put("reason", e.message)
                }
                Outcome.Refused
            } else {
                failures++
                record(step, "failed") {
                    detail()
                    code?.let { put("programError", it) }
                    put("error", e.message)
                }
                Outcome.Failed
            }
        }
    }

    suspend fun run(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        loadKeypair: (path: String, role: String) -> Keypair = Keypair::load,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): JsonObject {
        checkArgs(args)
        val programId = config.programId
        val payer = loadKeypair(config.requirePayer(), "payer")
        val pass = Pass(args.dryRun)

        val now = (rpc.chainTime() ?: failed("could not read chain time from ${config.rpcUrl}")).unixSeconds
        val configAddress = Pdas.config(programId)
        val onChain =
            when (val read = rpc.account(configAddress)) {
                is Rpc.Answer.Ok -> read.value?.let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: failed("the account at $configAddress is not a Config") }
                is Rpc.Answer.Refused -> failed("could not read Config at $configAddress: ${read.message}")
                is Rpc.Answer.Unanswered -> failed("could not read Config at $configAddress: ${read.message}")
            } ?: configError("the program at $programId is not initialized; run crank init first")
        val leagues = (rpc.leagues(programId) ?: failed("could not list leagues from ${config.rpcUrl}")).sortedBy { it.params.leagueId }
        val cohorts: MutableMap<Pair<Int, Long>, CohortSnapshot> =
            (rpc.cohorts(programId) ?: failed("could not list cohorts from ${config.rpcUrl}"))
                .associate { (_, c) -> (c.leagueId to c.index) to c }
                .toMutableMap()
        val nextIndex = leagues.associate { it.params.leagueId to it.nextIndex }.toMutableMap()

        suspend fun send(step: String, instructions: List<TransactionInstruction>): String? = signAndSend(rpc, step, instructions, listOf(payer), args.dryRun, pause, beforeRetrying = "the next pass reads chain state and retries")

        // ---- 1. open ---------------------------------------------------------
        suspend fun openDue(league: LeagueSnapshot) {
            val id = league.params.leagueId
            val current = league.copy(nextIndex = nextIndex.getValue(id))
            val due = dueOpens(current, now) { cohorts[id to it]?.start ?: scheduledStart(league, it) }
            for ((position, index) in due.withIndex()) {
                val previous = if (index == 0L) null else Pdas.cohort(programId, id, index - 1)
                val opened =
                    pass.send("open_cohort", {
                        put("league", id)
                        put("index", index)
                    }) {
                        send("open_cohort $id/$index", listOf(Instructions.openCohort(programId, payer.publicKey, onChain.skrMint, id, index, previous)))
                    }
                if (opened != Outcome.Landed) break
                // A simulated open created nothing, so nothing downstream may treat
                // it as existing: the league's index stays the on-chain one, and the later opens, which
                // name this cohort as their predecessor, are reported as planned.
                if (!args.dryRun) {
                    nextIndex[id] = index + 1
                } else {
                    due.drop(position + 1).forEach { later ->
                        pass.record("open_cohort", "skipped") {
                            put("league", id)
                            put("index", later)
                            put("reason", "dry run: opens after cohort $index, which was only simulated")
                        }
                    }
                    break
                }
            }
        }
        leagues.forEach { openDue(it) }

        // ---- 2. sponsor refill ----------------------------------------------
        val sponsorLamports = rpc.balance(onChain.sponsor)
        if (sponsorLamports == null) {
            pass.failures++
            pass.record("top_up_sponsor", "failed") { put("error", "could not read the sponsor's balance") }
        } else if (sponsorRefillDue(onChain, sponsorLamports)) {
            pass.send("top_up_sponsor", { put("sponsorLamports", sponsorLamports) }, expected = setOf(FAUCET_CAP_REACHED, FAUCET_EMPTY, SPONSOR_FLOAT_SUFFICIENT)) {
                send("top_up_sponsor", listOf(Instructions.topUpSponsor(programId, payer.publicKey, onChain.sponsor)))
            }
        }

        // ---- 3. forfeits ------------------------------------------------------
        for ((key, cohort) in cohorts.toSortedMap(compareBy({ it.first }, { it.second }))) {
            if (Derive.status(cohort, now) != CohortStatus.Running) continue
            val address = Pdas.cohort(programId, key.first, key.second)
            val members = rpc.memberships(programId, address)
            if (members == null) {
                pass.failures++
                pass.record("record_forfeits", "failed") {
                    put("league", key.first)
                    put("index", key.second)
                    put("error", "could not read the roster")
                }
                continue
            }
            forfeitPages(cohort, members, now, config.pageSize).forEachIndexed { page, wallets ->
                pass.send("record_forfeits", {
                    put("league", key.first)
                    put("index", key.second)
                    put("page", page)
                    put("members", wallets.size)
                }) {
                    val pairs = wallets.map { Instructions.MemberPair.of(programId, address, it) }
                    send("record_forfeits ${key.first}/${key.second}#$page", listOf(Instructions.recordForfeits(programId, payer.publicKey, onChain.feeDestination, onChain.developerDestination, address, pairs)))
                }
            }
        }

        // ---- 4. settlement, oldest first per league -------------------------
        for ((leagueId, queue) in settleQueue(cohorts.values, now)) {
            for (cohort in queue) {
                val index = cohort.index
                val detail: JsonObjectBuilder.() -> Unit = {
                    put("league", leagueId)
                    put("index", index)
                }
                val previous = if (index == 0L) null else cohorts[leagueId to index - 1]
                if (index > 0 && previous?.settled != true) {
                    pass.record("settle", "waiting") {
                        detail()
                        put("reason", "cohort ${index - 1} of league $leagueId is not settled yet (FR-061a)")
                    }
                    break
                }
                val address = Pdas.cohort(programId, leagueId, index)
                val previousAddress = if (index == 0L) null else Pdas.cohort(programId, leagueId, index - 1)

                // Count every member not yet counted.
                val members = rpc.membershipAccounts(programId, address)
                if (members == null) {
                    pass.failures++
                    pass.record("settle_count", "failed") {
                        detail()
                        put("error", "could not read the members")
                    }
                    break
                }
                val uncounted = members.filter { !it.second.counted }.map { it.second.member.wallet }
                var countsLanded = true
                uncounted.chunked(config.pageSize.coerceIn(1, Instructions.MAX_PAGE_PAIRS)).forEachIndexed { page, wallets ->
                    val outcome =
                        pass.send("settle_count", {
                            detail()
                            put("page", page)
                            put("members", wallets.size)
                        }) {
                            val pairs = wallets.map { Instructions.MemberPair.of(programId, address, it) }
                            send("settle_count $leagueId/$index#$page", listOf(Instructions.settleCount(programId, payer.publicKey, onChain.feeDestination, onChain.developerDestination, leagueId, address, pairs, previousAddress)))
                        }
                    countsLanded = countsLanded && outcome == Outcome.Landed
                }
                if (!countsLanded) break

                // Finalize once the count is complete on chain.
                val counted =
                    if (uncounted.isEmpty()) {
                        cohort
                    } else if (args.dryRun) {
                        pass.record("settle_finalize", "skipped") {
                            detail()
                            put("reason", "dry run: counting has not landed")
                        }
                        break
                    } else {
                        rpc.accountData(address)?.let { runCatching { CohortSnapshot.decode(it) }.getOrNull() }
                    }
                if (counted == null || counted.counted != counted.memberCount) {
                    pass.failures++
                    pass.record("settle_finalize", "failed") {
                        detail()
                        put("error", if (counted == null) "could not re-read the cohort after counting" else "counted ${counted.counted} of ${counted.memberCount} members")
                    }
                    break
                }
                val outcome =
                    pass.send("settle_finalize", detail) {
                        send("settle_finalize $leagueId/$index", listOf(Instructions.settleFinalize(programId, payer.publicKey, onChain.feeDestination, onChain.developerDestination, leagueId, address, previousAddress)))
                    }
                // A simulated finalize changed nothing: the next cohort still waits on this one.
                if (outcome != Outcome.Landed || args.dryRun) break
                cohorts[leagueId to index] = counted.copy(settled = true)
            }
        }

        val summary =
            buildJsonObject {
                put("command", "run")
                put("dryRun", args.dryRun)
                put("chainTime", now)
                put("failed", pass.failures)
                putJsonArray("steps") { pass.steps.forEach { add(it) } }
            }
        if (pass.failures > 0) throw CrankSummaryException(EXIT_FAILED, summary)
        return summary
    }
}
