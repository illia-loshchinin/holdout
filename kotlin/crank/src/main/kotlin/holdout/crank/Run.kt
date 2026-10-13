package holdout.crank

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.TransactionInstruction
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.Instructions
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Pdas
import holdout.core.ProfileSnapshot
import holdout.core.ProgramErrorCode
import holdout.core.Rpc
import holdout.core.Schedule
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
 *    cohort *i − 1* has started. Test leagues open only in the newest of
 *    each form (FR-044a).
 * 2. **Refill** the faucet sponsor when it holds less than half its float
 *    (FR-042b). A refusal — float sufficient, the day's budget spent, the vault
 *    empty — is an expected outcome, reported and retried next pass.
 * 3. **Forfeit** every member of a running cohort who is in breach, in pages.
 *    Each forfeit's developer and operations parts leave at once, to
 *    `Config.developer_destination` and `Config.fee_destination` (H35).
 * 4. **Settle** each league's finished cohorts **oldest first**: page
 *    `settle_count` over the uncounted members, which moves each counted
 *    forfeit's comeback pledge into the league's reserve vault and opens (or
 *    makes good) the member's comeback, then `settle_finalize`, which draws the
 *    boost and the comeback refunds owed from that vault and, with no
 *    finishers, sends the whole pool to the league's boost reserve
 *    (FR-037, FR-037c–e).
 *    Cohort *i* waits while *i − 1* is unsettled — the program refuses it
 *    (6045), so this is not a preference.
 * 5. **Release** every comeback pledge nobody can make good any more: read each
 *    profile, and for an open comeback whose last round is settled — or was
 *    never opened and whose first day has passed — send `release_comeback`,
 *    which moves the pledge to the league's boost reserve (FR-037e). A refusal
 *    because it is not due after all (6047) or already released (6046) is an
 *    expected outcome.
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

    /**
     * The test leagues that open no more cohorts: every one but the newest of
     * each form. Devnet runs one test lobby per level — Hackathon · Spark
     * (Easy) and Hackathon · Habit (Hard) — side by side, created with the same
     * days, day length, cadence and first start, so their rounds end in the
     * order they start (owner, 13 October). Streaks are written when a cohort
     * is counted, after it ends, so test leagues of different lengths running
     * together could count a member's outcomes out of order on the one
     * progress record they share: an older test league of a form winds down,
     * its cohorts still forfeited and settled as usual (FR-044a, FR-060).
     * [forms] maps each league id to its form.
     */
    fun retiredTestLeagues(forms: Map<Int, Int>): Set<Int> {
        val tests = forms.filterKeys { it >= LeagueParams.TEST_LEAGUE_MIN }
        val lobbies = tests.entries.groupBy({ it.value }, { it.key }).values.map { it.max() }.toSet()
        return tests.keys - lobbies
    }

    /**
     * The older test league the new lobby waits for, if any: one of [retired]
     * with a cohort not yet settled. Until every cohort of the older leagues
     * has settled, the newest opens nothing, so a member can never hold
     * outcomes in two test leagues at once and the shared streak stays in
     * order through a switchover (FR-044a, FR-060). A retired league in
     * [owing] holds it too: it still owes a comeback round not opened yet, has
     * just opened one this pass (not among [cohorts] until the next read), or
     * what it owes could not be read (FR-037e).
     */
    fun lobbyHeldBy(retired: Set<Int>, cohorts: Collection<CohortSnapshot>, owing: Collection<Int> = emptyList()): Int? = (
        cohorts.filter { it.leagueId in retired && !it.settled }.map { it.leagueId } + owing.filter { it in retired }
        ).minOrNull()

    /**
     * The last round a retired test [league] still owes a comeback in, or null
     * (FR-037e: a comeback is guaranteed). A comeback is made good only in its
     * own league, so a league retired by a newer one keeps opening its cohorts —
     * no further than this round — until every comeback it owes can be finished:
     * the open ones the members' progress records ([profiles]) name, and those
     * still to be recorded when a forfeited cohort is counted ([uncountedMisses],
     * the missed deadline of each forfeit not counted yet). Anyone may join a
     * round opened so, as any open cohort; the new lobby waits until they settle.
     */
    fun owedComebackRound(league: LeagueSnapshot, profiles: Collection<ProfileSnapshot>, uncountedMisses: Collection<Long>, now: Long = Long.MIN_VALUE): Long? {
        val p = league.params
        val recorded = profiles.flatMap { it.leagues }.filter { it.comebackStake > 0uL && it.comebackLeague == p.leagueId }.map { it.comebackLastRound }
        val pending = uncountedMisses.map { Schedule.comebackRounds(p.firstStart, p.cadence, p.joinLock, it).last }
        // A round whose first day is over can take nobody, even late: opening it would be an empty cohort
        // that only holds the new lobby and the pledge's release (`releasesDue` lets it go then).
        return (recorded + pending).filter { Schedule.deadlineOf(Schedule.cohortStart(p.firstStart, p.cadence, it), p.dayLength, 0) >= now }.maxOrNull()
    }

    /**
     * The earliest instant after [now] at which one of [leagues] falls due to
     * open its next cohort: the start of its newest one (`open_cohort` refuses
     * before it). The loop wakes for it instead of waiting out its interval, so
     * a test cohort is joinable seconds after the one before it starts
     * (FR-044a). Null when nothing falls due later — what is due now, the pass
     * has just opened.
     */
    fun nextOpenAt(leagues: Collection<LeagueSnapshot>, cohorts: Collection<CohortSnapshot>, now: Long): Long? {
        val starts = cohorts.associate { (it.leagueId to it.index) to it.start }
        return leagues
            .filter { it.nextIndex > 0 }
            .map { l -> starts[l.params.leagueId to l.nextIndex - 1] ?: scheduledStart(l, l.nextIndex - 1) }
            .filter { it > now }
            .minOrNull()
    }

    /** The program's `schedule::cohort_start`. */
    fun scheduledStart(league: LeagueSnapshot, index: Long): Long = Schedule.cohortStart(league.params.firstStart, league.params.cadence, index)

    /**
     * One open comeback whose pledge `release_comeback` can move to the boost reserve: [round] is its last round,
     * [account] the latest of its rounds the league has opened — the cohort the instruction is passed — or null.
     */
    data class ReleaseDue(val wallet: SolanaPublicKey, val leagueId: Int, val round: Long, val account: Long? = null)

    /**
     * The open comebacks due for release at [now] (FR-037e), the program's own
     * test in `release_comeback`: a comeback is released once **none** of its
     * rounds can be finished any more. Every round the league has opened must be
     * settled — the latest of them, `min(last, nextIndex − 1)`, is checked, and
     * cohorts settle in order — and if the last round is not opened yet, its
     * first day, by the league's schedule, must have passed: later than joining
     * closes, and later than its comeback member may join late.
     * [nextIndex] is each league's next index as it stands after this pass.
     */
    fun releasesDue(profiles: Collection<ProfileSnapshot>, leagues: Collection<LeagueSnapshot>, nextIndex: Map<Int, Long>, cohorts: Map<Pair<Int, Long>, CohortSnapshot>, now: Long): List<ReleaseDue> {
        val byId = leagues.associateBy { it.params.leagueId }
        return profiles.flatMap { profile ->
            profile.leagues.mapNotNull { progress ->
                if (progress.comebackStake == 0uL) return@mapNotNull null
                val league = byId[progress.comebackLeague] ?: return@mapNotNull null
                val first = progress.comebackRound
                val round = progress.comebackLastRound
                val next = nextIndex[league.params.leagueId] ?: league.nextIndex
                val latest = if (next > first) minOf(round, next - 1) else null
                val settled = latest == null || cohorts[league.params.leagueId to latest]?.settled == true
                val closed = next > round || now > Schedule.deadlineOf(scheduledStart(league, round), league.params.dayLength, 0)
                if (settled && closed) ReleaseDue(profile.wallet, league.params.leagueId, round, latest) else null
            }
        }
    }

    /** Whether the sponsor is below half its float, the only state `top_up_sponsor` accepts. */
    fun sponsorRefillDue(config: ConfigSnapshot, sponsorLamports: Long): Boolean = sponsorLamports.toULong() < config.sponsorFloat / 2uL

    /** The wallets of [members] in breach at [now], in pages of [pageSize]. */
    fun forfeitPages(cohort: CohortSnapshot, members: List<MembershipSnapshot>, now: Long, pageSize: Int): List<List<SolanaPublicKey>> = members
        .filter { Derive.inBreach(it, cohort, now) }
        .map { it.wallet }
        .chunked(pageSize.coerceIn(1, Instructions.MAX_PAGE_PAIRS))

    /**
     * The cohorts awaiting settlement at [now], in the order their outcomes
     * happened: by end, then league, then index. Each league's cohorts come
     * oldest first, as the program requires, and the test leagues, which all
     * write one `Profile` slot, are counted in time order across leagues too,
     * so the combined test board's streaks follow the cohorts as they ended
     * (FR-060, FR-061a).
     */
    fun settleQueue(cohorts: Collection<CohortSnapshot>, now: Long): List<CohortSnapshot> = cohorts
        .filter { Derive.status(it, now) == CohortStatus.Settling }
        .sortedWith(compareBy({ Derive.end(it) }, { it.leagueId }, { it.index }))

    // ---- the pass ----------------------------------------------------------

    const val FAUCET_CAP_REACHED = 6051
    const val FAUCET_EMPTY = 6052
    const val SPONSOR_FLOAT_SUFFICIENT = 6054

    /** `release_comeback`: the profile has no open comeback in the league (already released or made good). */
    const val NO_COMEBACK_TO_RELEASE = 6046

    /** `release_comeback`: the comeback's last round can still be finished. */
    const val COMEBACK_STILL_OPEN = 6047

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
        // Retired test leagues that opened a comeback round this pass.
        val openedRetired = mutableSetOf<Int>()

        suspend fun openDue(league: LeagueSnapshot, upTo: Long = Long.MAX_VALUE) {
            val id = league.params.leagueId
            val current = league.copy(nextIndex = nextIndex.getValue(id))
            val due = dueOpens(current, now) { cohorts[id to it]?.start ?: scheduledStart(league, it) }.filter { it <= upTo }
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
                    if (upTo != Long.MAX_VALUE) openedRetired += id
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
        val retired = retiredTestLeagues(leagues.associate { it.params.leagueId to it.params.form })
        // A retired league holds only the lobby that replaced it, the newest of its own form: an
        // Easy league winding down never stops the Hard lobby (Codex review of #70).
        val formOf = leagues.associate { it.params.leagueId to it.params.form }
        fun retiredOfForm(form: Int): Set<Int> = retired.filter { formOf[it] == form }.toSet()
        // A retired test league still opens the rounds its comebacks are owed in (FR-037e).
        val owed = mutableMapOf<Int, Long>()
        // Retired leagues whose debts could not be read hold the new lobby this pass rather than risk two open test leagues.
        val owedUnknown = mutableSetOf<Int>()
        if (retired.isNotEmpty()) {
            val early = rpc.profiles(programId)
            if (early == null) {
                owedUnknown += retired
                pass.failures++
                pass.record("open_cohort", "failed") { put("error", "could not list profiles from ${config.rpcUrl} for the comebacks retired test leagues owe") }
            } else {
                for (league in leagues.filter { it.params.leagueId in retired }) {
                    val id = league.params.leagueId
                    val misses = mutableListOf<Long>()
                    for (cohort in cohorts.values.filter { it.leagueId == id && !it.settled }) {
                        val accounts = rpc.membershipAccounts(programId, Pdas.cohort(programId, id, cohort.index))
                        if (accounts == null) {
                            owedUnknown += id
                            pass.failures++
                            pass.record("open_cohort", "failed") {
                                put("league", id)
                                put("index", cohort.index)
                                put("error", "could not read the members for the comebacks this league owes")
                            }
                            continue
                        }
                        accounts.map { it.second }
                            .filter { !it.counted && (it.member.state == MembershipSnapshot.State.Forfeited || Derive.inBreach(it.member, cohort, now)) }
                            .forEach { misses += Derive.deadlineOf(cohort, it.member.daysCompleted) }
                    }
                    owedComebackRound(league, early, misses, now)?.let { owed[id] = it }
                }
            }
        }

        // The retired leagues that hold the new lobby beyond an unsettled cohort it can see.
        fun owing(): Set<Int> = retired.filter { it in openedRetired || it in owedUnknown || (owed[it] ?: -1L) >= nextIndex.getValue(it) }.toSet()
        for (league in leagues) {
            val id = league.params.leagueId
            if (id in retired) {
                val upTo = owed[id]
                if (upTo != null && upTo >= nextIndex.getValue(id)) {
                    pass.record("open_cohort", "comeback") {
                        put("league", id)
                        put("upTo", upTo)
                        put("reason", "a newer test league of its form is the lobby, but this one owes a comeback in its rounds up to $upTo (FR-037e)")
                    }
                    openDue(league, upTo)
                } else {
                    pass.record("open_cohort", "retired") {
                        put("league", id)
                        put("reason", "a newer test league of its form is the lobby; this one opens no more cohorts (FR-044a)")
                    }
                }
            } else {
                val heldBy = if (id >= LeagueParams.TEST_LEAGUE_MIN) lobbyHeldBy(retiredOfForm(league.params.form), cohorts.values, owing()) else null
                if (heldBy != null) {
                    pass.record("open_cohort", "waiting") {
                        put("league", id)
                        put("reason", "test league $heldBy still has a cohort to open or settle; the new lobby opens once it has wound down (FR-044a)")
                    }
                } else {
                    openDue(league)
                }
            }
        }

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

        // ---- 4. settlement, in the order the cohorts ended --------------------
        // A cohort that cannot settle this pass holds every later cohort writing
        // the same progress slot: its own league's, and for a test league every
        // other test league's too, so no streak is written out of order.
        val heldBy = mutableMapOf<Int, Int>() // progress slot -> the league holding it
        for (cohort in settleQueue(cohorts.values, now)) {
            val leagueId = cohort.leagueId
            val index = cohort.index
            val slot = ProfileSnapshot.slotOf(leagueId)
            val detail: JsonObjectBuilder.() -> Unit = {
                put("league", leagueId)
                put("index", index)
            }
            val holder = heldBy[slot]
            if (holder != null) {
                if (holder != leagueId) {
                    pass.record("settle", "waiting") {
                        detail()
                        put("reason", "an earlier cohort of test league $holder, which shares its progress, is not settled yet (FR-061a)")
                    }
                }
                continue
            }
            val previous = if (index == 0L) null else cohorts[leagueId to index - 1]
            if (index > 0 && previous?.settled != true) {
                pass.record("settle", "waiting") {
                    detail()
                    put("reason", "cohort ${index - 1} of league $leagueId is not settled yet (FR-061a)")
                }
                heldBy[slot] = leagueId
                continue
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
                heldBy[slot] = leagueId
                continue
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
            if (!countsLanded) {
                heldBy[slot] = leagueId
                continue
            }

            // Finalize once the count is complete on chain.
            val counted =
                if (uncounted.isEmpty()) {
                    cohort
                } else if (args.dryRun) {
                    pass.record("settle_finalize", "skipped") {
                        detail()
                        put("reason", "dry run: counting has not landed")
                    }
                    heldBy[slot] = leagueId
                    continue
                } else {
                    rpc.accountData(address)?.let { runCatching { CohortSnapshot.decode(it) }.getOrNull() }
                }
            if (counted == null || counted.counted != counted.memberCount) {
                pass.failures++
                pass.record("settle_finalize", "failed") {
                    detail()
                    put("error", if (counted == null) "could not re-read the cohort after counting" else "counted ${counted.counted} of ${counted.memberCount} members")
                }
                heldBy[slot] = leagueId
                continue
            }
            val outcome =
                pass.send("settle_finalize", detail) {
                    send("settle_finalize $leagueId/$index", listOf(Instructions.settleFinalize(programId, payer.publicKey, onChain.feeDestination, leagueId, address, previousAddress)))
                }
            // A simulated finalize changed nothing: the next cohort still waits on this one.
            if (outcome != Outcome.Landed || args.dryRun) {
                heldBy[slot] = leagueId
                continue
            }
            cohorts[leagueId to index] = counted.copy(settled = true)
        }

        // ---- 5. comebacks nobody can make good any more ------------------------
        // After settlement, so a pledge the counting just closed is not read as open.
        val profiles = rpc.profiles(programId)
        if (profiles == null) {
            pass.failures++
            pass.record("release_comeback", "failed") { put("error", "could not list profiles from ${config.rpcUrl}") }
        } else {
            for (due in releasesDue(profiles, leagues, nextIndex, cohorts, now)) {
                // The latest opened round's cohort is passed; a round not opened yet the program judges by the schedule.
                val round = due.account?.let { Pdas.cohort(programId, due.leagueId, it) }
                pass.send("release_comeback", {
                    put("league", due.leagueId)
                    put("round", due.round)
                    put("wallet", due.wallet.base58())
                }, expected = setOf(NO_COMEBACK_TO_RELEASE, COMEBACK_STILL_OPEN)) {
                    send("release_comeback ${due.leagueId}/${due.round} ${due.wallet.base58()}", listOf(Instructions.releaseComeback(programId, payer.publicKey, due.leagueId, due.wallet, round)))
                }
            }
        }

        // The leagues this crank opens in, as they stand after this pass; a lobby
        // held by an older test league waits on settlement, not on a clock.
        val opening = leagues
            .filter { l ->
                val id = l.params.leagueId
                if (id in retired) {
                    (owed[id] ?: -1L) >= nextIndex.getValue(id)
                } else {
                    id < LeagueParams.TEST_LEAGUE_MIN || lobbyHeldBy(retiredOfForm(l.params.form), cohorts.values, owing()) == null
                }
            }
            .map { it.copy(nextIndex = nextIndex.getValue(it.params.leagueId)) }
        val nextOpen = nextOpenAt(opening, cohorts.values, now)

        val summary =
            buildJsonObject {
                put("command", "run")
                put("dryRun", args.dryRun)
                put("chainTime", now)
                put("failed", pass.failures)
                nextOpen?.let { put("nextOpenAt", it) }
                putJsonArray("steps") { pass.steps.forEach { add(it) } }
            }
        if (pass.failures > 0) throw CrankSummaryException(EXIT_FAILED, summary)
        return summary
    }
}
