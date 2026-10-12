package holdout.crank

import com.solana.publickey.SolanaPublicKey
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.Instructions
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import holdout.core.Pdas
import holdout.core.ProfileSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.time.Instant

/** One entry of `scripted-wallets.json`: a public key and the group whose turn it plays (research R12). */
data class ScriptedWallet(val pubkey: SolanaPublicKey, val league: String, val group: String, val index: Int) {
    /** The keypair's file name in `HOLDOUT_SCRIPTED_KEYS_DIR`, as `scripts/gen-scripted-wallets.sh` writes it. */
    val fileName: String get() = "%s-%02d.json".format(group, index)
}

/**
 * `crank scripted plan|run|fund` (T087, T088, research R12, FR-043).
 *
 * Scripted members are real wallets under the same contract rules as anyone:
 * they claim the faucet, stake, submit days and forfeit. What is scripted is
 * only *who decides* — this schedule — and that their days are not
 * camera-verified. Nothing on-chain knows which wallets they are; the floors
 * and the roster live here and in the committed public list alone (FR-043a).
 *
 * - **Fill to a floor, real members first.** A cohort is topped up only to
 *   its league's floor — on devnet 20 for Spark, 4 for a Hackathon round and
 *   none for Habit (owner, 13 October) — counting everyone already in it: to
 *   half the floor while its lobby is open, to the whole floor from 24 hours
 *   before the join cutoff. Never over, never leaving. A Hackathon round is
 *   filled only once a real member has joined it: an empty one stays empty.
 * - **Which wallets.** The 7-day league plays the same 20 every week; the
 *   21-day league rotates three groups of 12 by cohort index, so no wallet is
 *   ever asked to overlap itself (FR-006b). Test leagues draw on all 36
 *   21-day wallets, idle while Habit's floor is 0.
 * - **SOL.** The payer tops a wallet up in its join transaction, so its
 *   faucet claim mints SKR only and never spends testers' lamport budget.
 * - **Tiers and forfeits** come from a hash of the cohort and the wallet, so
 *   every pass — and anyone reading this file — agrees on them: about 40 %
 *   finish, and forfeits fall three times as often in the first half.
 * - **Days** are submitted for every scripted member not scheduled to have
 *   forfeited by today; on its forfeit day a wallet simply stops.
 */
object Scripted {
    const val DEFAULT_FLOOR_7D = 20

    /** None on devnet: judges will not run a 21-day Holdout, so its wallets play the Hackathon rounds (owner, 13 October). */
    const val DEFAULT_FLOOR_21D = 0

    /** A Hackathon round's floor, once a real member has joined it (owner, 13 October). */
    const val DEFAULT_FLOOR_TEST = 4

    /**
     * What a wallet should hold above the faucet's `sol_top_up` before it
     * claims and joins: a first claim's profile and token-account rent, a
     * membership's rent and the fees of its days (R12).
     */
    const val JOIN_RESERVE_LAMPORTS = 10_000_000L

    /** The last cohort start that gets scripted members: Monday 9 November 2026, 05:00 UTC (R12). */
    val DEFAULT_LAST_START: Long = Instant.parse("2026-11-09T05:00:00Z").epochSecond

    /** The top-up every scripted wallet gets once, before its first join (R12). */
    const val DEFAULT_FUND_LAMPORTS = 20_000_000L

    /** Stage two of the fill starts this long before the join cutoff. */
    const val FULL_FLOOR_BEFORE_CUTOFF = 86_400L

    /** Share of scripted members who finish their cohort (R12). */
    const val FINISH_SHARE = 0.40

    /** Transfers per `scripted fund` transaction. */
    private const val FUND_BATCH = 10

    fun checkArgs(args: Args) {
        when (args.words.getOrNull(1)) {
            "plan" -> args.allow("cohort", "floor-7d", "floor-21d", "floor-test", "fill-empty")
            "run" -> args.allow("floor-7d", "floor-21d", "floor-test", "fill-empty", "last-start")
            "fund" -> args.allow("lamports")
            else -> configError("unknown command '${args.words.joinToString(" ")}'; try --help")
        }
    }

    // ---- the roster --------------------------------------------------------

    /** The committed public list, bundled with `kotlin/core` — what the app labels scripted members from. */
    fun roster(json: String = defaultRosterJson()): List<ScriptedWallet> = Json
        .parseToJsonElement(json)
        .jsonObject
        .getValue("wallets")
        .jsonArray
        .map { it.jsonObject }
        .map {
            ScriptedWallet(
                SolanaPublicKey.from(it.getValue("pubkey").jsonPrimitive.content),
                it.getValue("league").jsonPrimitive.content,
                it.getValue("group").jsonPrimitive.content,
                it.getValue("index").jsonPrimitive.int,
            )
        }

    private fun defaultRosterJson(): String = Scripted::class.java.getResourceAsStream("/scripted-wallets.json")?.use { it.readBytes().decodeToString() }
        ?: configError("scripted-wallets.json is not on the classpath; run scripts/gen-scripted-wallets.sh and rebuild")

    // ---- the plan: pure ----------------------------------------------------

    /**
     * The devnet floors (FR-043). [test] applies only to a round a real member
     * has joined ([needsRealMember]); a local run (quickstart §5a) passes its
     * own floors to watch a whole cycle with nobody on a phone.
     */
    data class Floors(val sevenDay: Int = DEFAULT_FLOOR_7D, val twentyOneDay: Int = DEFAULT_FLOOR_21D, val test: Int = DEFAULT_FLOOR_TEST) {
        fun of(leagueId: Int): Int = when (leagueId) {
            LeagueParams.LEAGUE_7D -> sevenDay
            LeagueParams.LEAGUE_21D -> twentyOneDay
            else -> test
        }
    }

    /**
     * How many members [c] should hold at [now]: half the floor (rounded up)
     * while the lobby is open, the whole floor from 24 hours before the cutoff,
     * and no target at all once joining has closed.
     */
    fun target(c: CohortSnapshot, floor: Int, now: Long): Int = when {
        floor <= 0 || now >= c.joinCutoff -> 0
        now >= c.joinCutoff - FULL_FLOOR_BEFORE_CUTOFF -> floor
        else -> (floor + 1) / 2
    }

    /**
     * Scripted wallets to add now: never negative, never past the target.
     * [members] is the roster read just before filling, when there is one: real
     * members who joined after [c] was read count too.
     */
    fun deficit(c: CohortSnapshot, floor: Int, now: Long, members: Int = 0): Int = (target(c, floor, now) - maxOf(c.memberCount.toInt(), members)).coerceAtLeast(0)

    /** Whether [leagueId]'s cohorts get scripted members only once a real member has joined: the Hackathon rounds (FR-043). */
    fun needsRealMember(leagueId: Int): Boolean = leagueId >= LeagueParams.TEST_LEAGUE_MIN

    /** [needsRealMember], unless `--fill-empty` — a local run (quickstart §5a) — fills empty rounds too. */
    private fun waitsForReal(leagueId: Int, args: Args): Boolean = needsRealMember(leagueId) && "fill-empty" !in args.flags

    /** Lamports the payer sends a wallet holding [balance] before it claims and joins, so the claim takes none from the faucet (FR-043a). */
    fun solTopUp(balance: Long, faucetTopUp: Long): Long = (faucetTopUp + JOIN_RESERVE_LAMPORTS - balance).coerceAtLeast(0)

    /** The wallets that may play [leagueId]'s cohort [index]: the 7-day twenty, the 21-day group whose turn it is, or for a test league all 36 21-day wallets. */
    fun poolFor(roster: List<ScriptedWallet>, leagueId: Int, index: Long): List<ScriptedWallet> = when (leagueId) {
        LeagueParams.LEAGUE_7D -> roster.filter { it.group == "7d" }
        LeagueParams.LEAGUE_21D -> {
            val groups = roster.filter { it.league == "21d" }.map { it.group }.distinct().sorted()
            if (groups.isEmpty()) emptyList() else roster.filter { it.group == groups[(index % groups.size).toInt()] }
        }
        // A Hackathon round starts every 30 minutes and each wallet holds one running
        // Holdout at a time (FR-006b): the 36 wallets Habit leaves idle on devnet.
        else -> roster.filter { it.league == "21d" }
    }.sortedBy { it.index }

    private fun seed(cohort: SolanaPublicKey, wallet: SolanaPublicKey, salt: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update("holdout-scripted:$salt".encodeToByteArray())
            update(cohort.bytes)
            update(wallet.bytes)
        }.digest()
        return ByteBuffer.wrap(digest, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long ushr 1
    }

    private fun unit(cohort: SolanaPublicKey, wallet: SolanaPublicKey, salt: String): Double = seed(cohort, wallet, salt).toDouble() / Long.MAX_VALUE

    /** The tier [wallet] stakes at in [cohort], 0..3, evenly spread. */
    fun tierOf(cohort: SolanaPublicKey, wallet: SolanaPublicKey): Int = (seed(cohort, wallet, "tier") % 4).toInt()

    /**
     * The zero-based cohort day on which [wallet] stops submitting, or null for
     * a finisher. First-half days weigh three times the second half's (R12).
     */
    fun forfeitDayOf(cohort: SolanaPublicKey, wallet: SolanaPublicKey, durationDays: Int): Int? {
        if (unit(cohort, wallet, "finish") < FINISH_SHARE) return null
        val weights = List(durationDays) { day -> if (day < (durationDays + 1) / 2) 3 else 1 }
        var pick = unit(cohort, wallet, "day") * weights.sum()
        weights.forEachIndexed { day, w ->
            pick -= w
            if (pick < 0) return day
        }
        return durationDays - 1
    }

    /**
     * Whether a scripted member should submit today: it is `Joined`, today's
     * day is not yet submitted, and its forfeit day has not come.
     */
    fun shouldSubmit(c: CohortSnapshot, cohort: SolanaPublicKey, m: MembershipSnapshot, now: Long): Boolean {
        if (Derive.status(c, now) != CohortStatus.Running || m.state != MembershipSnapshot.State.Joined) return false
        val today = Derive.dayIndex(c, now)
        if (m.daysCompleted != today || Derive.inBreach(m, c, now)) return false
        val forfeitDay = forfeitDayOf(cohort, m.wallet, c.durationDays)
        return forfeitDay == null || today < forfeitDay
    }

    /**
     * Whether [profile] leaves room to join [c]: no lobby place held elsewhere,
     * and any running cohort ends by the time [c] starts (FR-006a, FR-006b).
     * [runningEnd] is the running cohort's end, read from its account.
     */
    fun canJoin(profile: ProfileSnapshot?, c: CohortSnapshot, runningEnd: Long?, now: Long): Boolean {
        val p = profile?.normalised(now) ?: return true
        if (p.pending != null) return false
        return runningEnd == null || runningEnd <= now || c.start >= runningEnd
    }

    /** The 32 bytes a scripted day carries as its proof: a label, not a camera — which is the point (FR-043). */
    fun proofOf(cohort: SolanaPublicKey, wallet: SolanaPublicKey, day: Int): ByteArray = MessageDigest.getInstance("SHA-256").apply {
        update("holdout-scripted-day:not-camera-verified".encodeToByteArray())
        update(cohort.bytes)
        update(wallet.bytes)
        update(day.toByte())
    }.digest()

    // ---- plan --------------------------------------------------------------

    private fun floors(args: Args): Floors {
        fun floor(name: String, default: Int) = args.option(name)?.let { it.toIntOrNull()?.takeIf { n -> n in 0..1_000 } ?: configError("--$name must be 0..1000, got $it") } ?: default
        return Floors(floor("floor-7d", DEFAULT_FLOOR_7D), floor("floor-21d", DEFAULT_FLOOR_21D), floor("floor-test", DEFAULT_FLOOR_TEST))
    }

    suspend fun plan(args: Args, config: CrankConfig, rpc: Rpc, roster: List<ScriptedWallet> = roster()): JsonObject {
        checkArgs(args)
        val text = args.option("cohort") ?: configError("scripted plan needs --cohort <address>")
        val address = runCatching { SolanaPublicKey.from(text) }.getOrNull() ?: configError("--cohort is not a public key: $text")
        val now = (rpc.chainTime() ?: failed("could not read chain time")).unixSeconds
        val c = rpc.accountData(address)?.let { runCatching { CohortSnapshot.decode(it) }.getOrNull() } ?: failed("no cohort at $address")
        val members = rpc.memberships(config.programId, address) ?: failed("could not read the roster of $address")
        val scriptedKeys = roster.map { it.pubkey }.toSet()
        val floor = floors(args).of(c.leagueId)
        val scripted = members.filter { it.wallet in scriptedKeys }
        return buildJsonObject {
            put("command", "scripted plan")
            put("cohort", address.base58())
            put("league", c.leagueId)
            put("index", c.index)
            put("status", Derive.status(c, now).name)
            put("floor", floor)
            put("target", target(c, floor, now))
            put("members", c.memberCount)
            put("real", members.size - scripted.size)
            put("scripted", scripted.size)
            put("needsRealMember", waitsForReal(c.leagueId, args))
            put("deficit", if (waitsForReal(c.leagueId, args) && scripted.size == members.size) 0 else deficit(c, floor, now, members.size))
            putJsonArray("pool") {
                poolFor(roster, c.leagueId, c.index).forEach { w ->
                    addJsonObject {
                        put("wallet", w.pubkey.base58())
                        put("group", w.group)
                        put("joined", members.any { it.wallet == w.pubkey })
                        put("tier", tierOf(address, w.pubkey))
                        put("forfeitDay", forfeitDayOf(address, w.pubkey, c.durationDays)?.let(::JsonPrimitive) ?: JsonPrimitive("finishes"))
                    }
                }
            }
        }
    }

    // ---- run ---------------------------------------------------------------

    suspend fun run(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        roster: List<ScriptedWallet> = roster(),
        loadKeypair: (path: String, role: String) -> Keypair = Keypair::load,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): JsonObject {
        checkArgs(args)
        val floors = floors(args)
        val lastStart = args.option("last-start")?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() ?: configError("--last-start must be an instant, got $it") } ?: DEFAULT_LAST_START
        val keysDir = config.scriptedKeysDir ?: configError("HOLDOUT_SCRIPTED_KEYS_DIR is not set")
        val programId = config.programId
        val now = (rpc.chainTime() ?: failed("could not read chain time from ${config.rpcUrl}")).unixSeconds
        val onChain =
            rpc.accountData(Pdas.config(programId))?.let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() }
                ?: configError("the program at $programId is not initialized (or Config could not be read)")
        val cohorts = (rpc.cohorts(programId) ?: failed("could not list cohorts")).sortedWith(compareBy({ it.second.leagueId }, { it.second.index }))
        val byAddress = cohorts.toMap()
        val keys = mutableMapOf<SolanaPublicKey, Keypair>()
        fun keyOf(w: ScriptedWallet): Keypair = keys.getOrPut(w.pubkey) {
            val k = loadKeypair(File(keysDir, w.fileName).path, "scripted ${w.fileName}")
            if (k.publicKey != w.pubkey) configError("${w.fileName} holds ${k.publicKey}, but scripted-wallets.json lists ${w.pubkey}")
            k
        }

        // Loaded on the first top-up only: a pass that sends no SOL needs no payer.
        val payer by lazy { loadKeypair(config.requirePayer(), "payer") }
        val scriptedKeys = roster.map { it.pubkey }.toSet()

        val steps = mutableListOf<JsonObject>()
        var failures = 0

        // True when the transaction landed (or simulated cleanly).
        suspend fun attempt(step: String, detail: JsonObjectBuilder.() -> Unit, block: suspend () -> String?): Boolean {
            try {
                val signature = block()
                steps += buildJsonObject {
                    put("step", step)
                    put("status", if (args.dryRun) "simulated" else "sent")
                    detail()
                    signature?.let { put("signature", it) }
                }
                return true
            } catch (e: CrankException) {
                if (e.exitCode == EXIT_CONFIG) throw e
                failures++
                steps += buildJsonObject {
                    put("step", step)
                    put("status", "failed")
                    detail()
                    put("error", e.message)
                }
                return false
            }
        }

        for ((address, c) in cohorts) {
            val status = Derive.status(c, now)
            val floor = floors.of(c.leagueId)
            val pool = poolFor(roster, c.leagueId, c.index)
            if (pool.isEmpty()) continue
            val detail: JsonObjectBuilder.() -> Unit = {
                put("league", c.leagueId)
                put("index", c.index)
            }

            // ---- fill an open lobby to its target -----------------------------
            if (status == CohortStatus.Open && c.start <= lastStart) {
                if (target(c, floor, now) == 0) continue
                // A Hackathon round nobody real has joined stays empty (FR-043).
                if (waitsForReal(c.leagueId, args) && c.memberCount == 0L) continue
                // The cohort and its roster read again, together: a member who joined or
                // left since the cohort list was read moves the deficit either way.
                val fresh = rpc.cohortRead(programId, address) ?: failed("could not read cohort ${c.leagueId}/${c.index} and its roster")
                if (waitsForReal(c.leagueId, args) && fresh.members.all { it.wallet in scriptedKeys }) continue
                var needed = deficit(fresh.cohort, floor, now, fresh.members.size)
                if (needed == 0) continue
                val inCohort = fresh.members.map { it.wallet }.toSet()
                for (w in pool) {
                    if (needed == 0) break
                    if (w.pubkey in inCohort) continue
                    val profile =
                        when (val read = rpc.profile(programId, w.pubkey)) {
                            is Rpc.Answer.Ok -> read.value
                            else -> failed("could not read the profile of ${w.pubkey}")
                        }
                    val running = profile?.normalised(now)?.running?.cohort
                    val runningEnd = running?.let { r -> byAddress[r]?.let(Derive::end) }
                    if (!canJoin(profile, c, runningEnd, now)) continue
                    val tier = tierOf(address, w.pubkey)
                    // A read that failed is not an empty account: skip the wallet this pass rather than spend its faucet claim.
                    val balance = rpc.tokenAmount(Pdas.associatedToken(w.pubkey, onChain.skrMint)) ?: continue
                    val needsFaucet = balance < c.chips[tier]
                    val lamports = rpc.balance(w.pubkey) ?: continue
                    val topUp = solTopUp(lamports, onChain.solTopUp.toLong())
                    val key = keyOf(w)
                    // Only a join that landed fills a place; after a failed one the next wallet is tried.
                    val joined = attempt("join", {
                        detail()
                        put("wallet", w.pubkey.base58())
                        put("tier", tier)
                        put("faucet", needsFaucet)
                        if (topUp > 0) put("solTopUp", topUp)
                    }) {
                        val ixs = buildList {
                            // First, so the claim finds the faucet's top-up already held and moves no lamports; the payer then pays the fee too.
                            if (topUp > 0) add(Instructions.systemTransfer(payer.publicKey, w.pubkey, topUp.toULong()))
                            if (needsFaucet) add(Instructions.claimFaucet(programId, w.pubkey, w.pubkey, onChain.skrMint, onChain.sponsor))
                            add(Instructions.joinCohort(programId, w.pubkey, onChain.skrMint, address, tier, running))
                        }
                        signAndSend(rpc, "scripted join ${c.leagueId}/${c.index}", ixs, if (topUp > 0) listOf(payer, key) else listOf(key), args.dryRun, pause, beforeRetrying = "the next pass re-reads the roster")
                    }
                    if (joined) needed--
                }
            }

            // ---- submit today's day for every scripted member due to -----------
            if (status == CohortStatus.Running) {
                val members = rpc.memberships(programId, address) ?: failed("could not read the roster of cohort ${c.leagueId}/${c.index}")
                val scripted = pool.associateBy { it.pubkey }
                for (m in members) {
                    val w = scripted[m.wallet] ?: continue
                    if (!shouldSubmit(c, address, m, now)) continue
                    val day = Derive.dayIndex(c, now)
                    val key = keyOf(w)
                    attempt("submit_day", {
                        detail()
                        put("wallet", w.pubkey.base58())
                        put("day", day)
                    }) {
                        signAndSend(rpc, "scripted submit ${c.leagueId}/${c.index} day $day", listOf(Instructions.submitDay(programId, w.pubkey, address, day, proofOf(address, w.pubkey, day))), listOf(key), args.dryRun, pause, beforeRetrying = "the next pass re-reads the roster")
                    }
                }
            }
        }

        val summary = buildJsonObject {
            put("command", "scripted run")
            put("dryRun", args.dryRun)
            put("chainTime", now)
            put("failed", failures)
            putJsonArray("steps") { steps.forEach { add(it) } }
        }
        if (failures > 0) throw CrankSummaryException(EXIT_FAILED, summary)
        return summary
    }

    // ---- fund --------------------------------------------------------------

    /**
     * Tops every scripted wallet up **to** `--lamports` (default 0.02 SOL) from
     * the Operator, in batches — once, before their first joins (R12). A
     * wallet at or above the target is left alone, so a rerun sends nothing.
     */
    suspend fun fund(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        roster: List<ScriptedWallet> = roster(),
        loadKeypair: (path: String, role: String) -> Keypair = Keypair::load,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): JsonObject {
        checkArgs(args)
        val target = args.option("lamports")?.let { it.toLongOrNull()?.takeIf { n -> n > 0 } ?: configError("--lamports must be a positive number, got $it") } ?: DEFAULT_FUND_LAMPORTS
        val operator = loadKeypair(config.requireOperator(), "operator")
        val topUps =
            roster.mapNotNull { w ->
                val balance = rpc.balance(w.pubkey) ?: failed("could not read the balance of ${w.pubkey}")
                (target - balance).takeIf { it > 0 }?.let { w to it }
            }
        val signatures = mutableListOf<String>()
        topUps.chunked(FUND_BATCH).forEachIndexed { batch, chunk ->
            val ixs = chunk.map { (w, lamports) -> Instructions.systemTransfer(operator.publicKey, w.pubkey, lamports.toULong()) }
            signAndSend(rpc, "scripted fund batch $batch", ixs, listOf(operator), args.dryRun, pause)?.let(signatures::add)
        }
        return buildJsonObject {
            put("command", "scripted fund")
            put("dryRun", args.dryRun)
            put("targetLamports", target)
            put("wallets", roster.size)
            put("toppedUp", topUps.size)
            put("lamports", topUps.sumOf { it.second })
            putJsonArray("signatures") { signatures.forEach { add(JsonPrimitive(it)) } }
        }
    }
}
