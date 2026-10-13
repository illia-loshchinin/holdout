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
 * `crank scripted plan|run|leave|fund` (T087, T088, research R12, FR-043).
 *
 * Scripted members are real wallets under the same contract rules as anyone:
 * they claim the faucet, stake, submit days and forfeit. What is scripted is
 * only *who decides* — this schedule — and that their days are not
 * camera-verified. Nothing on-chain knows which wallets they are; the floors
 * and the roster live here and in the committed public list alone (FR-043a).
 *
 * - **Fill to a floor, real members first.** A cohort is topped up only to
 *   its league's floor — on devnet 10 for a Hackathon round and none for
 *   Spark or Habit (owner, 13 October) — counting everyone already in it: to
 *   half the floor while its lobby is open, to the whole floor from 24 hours
 *   before the join cutoff. A Hackathon round is looked at only in its last
 *   [TEST_FILL_LEAD] seconds before the cutoff, and filled then only if a real
 *   member has joined it: an empty one stays empty. Never over; a scripted
 *   member leaves only when `scripted leave` sends it.
 * - **Which wallets.** The 7-day league plays the same 20 every week; the
 *   21-day league rotates three groups of 12 by cohort index, so no wallet is
 *   ever asked to overlap itself (FR-006b). Test leagues draw on every wallet
 *   whose league's floor is 0 — all 56 on devnet.
 * - **When to wake.** A pass reports `nextScriptedAt`: the next Hackathon fill
 *   window, the next stage of a lobby's fill, or the next day of a running
 *   cohort a scripted member still plays. The loop sleeps until then rather
 *   than polling the RPC (owner, 13 October).
 * - **SOL.** The payer tops a wallet up in its join transaction, so its
 *   faucet claim mints SKR only and never spends testers' lamport budget.
 * - **Tiers and forfeits** come from a hash of the cohort and the wallet, so
 *   every pass — and anyone reading this file — agrees on them: about 40 %
 *   finish, and forfeits fall three times as often in the first half.
 * - **Days** are submitted for every scripted member not scheduled to have
 *   forfeited by today; on its forfeit day a wallet simply stops.
 */
object Scripted {
    /** None on devnet: judges will not run a 7-day Holdout, so its wallets play the Hackathon rounds (owner, 13 October; 20 before). */
    const val DEFAULT_FLOOR_7D = 0

    /** None on devnet: judges will not run a 21-day Holdout, so its wallets play the Hackathon rounds (owner, 13 October). */
    const val DEFAULT_FLOOR_21D = 0

    /** A Hackathon round's floor, once a real member has joined it (owner, 13 October; 4 that morning). */
    const val DEFAULT_FLOOR_TEST = 10

    /**
     * A Hackathon round is looked at once, this many seconds before its join
     * cutoff (its start, join lock 0), and topped up then — not polled while its
     * lobby is open (owner, 13 October).
     */
    const val TEST_FILL_LEAD = 60L

    /** `--fill-lead <s>`: how long before its cutoff a Hackathon round is filled, [TEST_FILL_LEAD] unless said. */
    private fun fillLead(args: Args): Long = args.option("fill-lead")?.let { it.toLongOrNull()?.takeIf { n -> n in 1..3_600 } ?: configError("--fill-lead must be 1..3600 seconds, got $it") } ?: TEST_FILL_LEAD

    /**
     * What a wallet should hold above the faucet's `sol_top_up` before it
     * claims and joins: a first claim's profile and token-account rent, a
     * membership's rent and the fees of its days (R12).
     */
    const val JOIN_RESERVE_LAMPORTS = 10_000_000L

    /** What the payer keeps for opening cohorts and settling: scripted top-ups stop above it. */
    const val PAYER_RESERVE_LAMPORTS = 2_000_000_000L

    /** The last cohort start that gets scripted members: Monday 9 November 2026, 05:00 UTC (R12). */
    val DEFAULT_LAST_START: Long = Instant.parse("2026-11-09T05:00:00Z").epochSecond

    /** The top-up every scripted wallet gets once, before its first join (R12). */
    const val DEFAULT_FUND_LAMPORTS = 20_000_000L

    /** Stage two of the fill starts this long before the join cutoff. */
    const val FULL_FLOOR_BEFORE_CUTOFF = 86_400L

    /** Share of scripted members who finish their cohort (R12). */
    const val FINISH_SHARE = 0.40

    /** How soon a pass that failed to land a scripted day asks to run again. */
    const val RETRY_SECONDS = 30L

    /** Transfers per `scripted fund` transaction. */
    private const val FUND_BATCH = 10

    fun checkArgs(args: Args) {
        when (args.words.getOrNull(1)) {
            "plan" -> args.allow("cohort", "floor-7d", "floor-21d", "floor-test", "fill-empty", "fill-lead")
            "run" -> args.allow("floor-7d", "floor-21d", "floor-test", "fill-empty", "last-start", "fill-lead")
            "leave" -> args.allow("cohort")
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

        /** The floor of a roster entry's own league (`7d` or `21d`): a wallet whose league's floor is 0 is free for the Hackathon rounds. */
        fun ofRoster(league: String): Int = when (league) {
            "7d" -> sevenDay
            "21d" -> twentyOneDay
            else -> 0
        }
    }

    /**
     * How many members [c] should hold at [now]: half the floor (rounded up)
     * while the lobby is open, the whole floor from 24 hours before the cutoff,
     * and no target at all once joining has closed. A Hackathon round has no
     * target until its last [lead] seconds ([TEST_FILL_LEAD] by default), then its whole floor.
     */
    fun target(c: CohortSnapshot, floor: Int, now: Long, lead: Long = TEST_FILL_LEAD): Int = when {
        floor <= 0 || now >= c.joinCutoff -> 0
        needsRealMember(c.leagueId) -> if (now >= testFillAt(c, lead)) floor else 0
        now >= c.joinCutoff - FULL_FLOOR_BEFORE_CUTOFF -> floor
        else -> (floor + 1) / 2
    }

    /** When a Hackathon round is looked at and topped up: [lead] seconds before its join cutoff. */
    fun testFillAt(c: CohortSnapshot, lead: Long = TEST_FILL_LEAD): Long = c.joinCutoff - lead

    /**
     * When [c]'s target next grows after [now], or null: a Hackathon round's
     * fill window, or the full floor 24 hours before a lobby's cutoff.
     */
    fun nextFillAt(c: CohortSnapshot, floor: Int, now: Long, lead: Long = TEST_FILL_LEAD): Long? {
        if (floor <= 0) return null
        val at = if (needsRealMember(c.leagueId)) testFillAt(c, lead) else c.joinCutoff - FULL_FLOOR_BEFORE_CUTOFF
        return at.takeIf { it > now && it < c.joinCutoff }
    }

    /**
     * When a scripted member of running cohort [c] next has a day to submit
     * after [now]: the start of tomorrow's day, if any of [members] still
     * plays it (`Joined`, not past its forfeit day), else null.
     */
    fun nextDayAt(c: CohortSnapshot, cohort: SolanaPublicKey, members: List<MembershipSnapshot>, now: Long): Long? {
        val tomorrow = Derive.dayIndex(c, now) + 1
        if (tomorrow >= c.durationDays) return null
        val plays = members.any { m ->
            m.state == MembershipSnapshot.State.Joined &&
                !Derive.inBreach(m, c, now) &&
                forfeitDayOf(cohort, m.wallet, c.durationDays).let { it == null || tomorrow < it }
        }
        return if (plays) c.start + tomorrow * c.dayLength else null
    }

    /**
     * Scripted wallets to add now: never negative, never past the target.
     * [members] is the roster read just before filling, when there is one: real
     * members who joined after [c] was read count too.
     */
    fun deficit(c: CohortSnapshot, floor: Int, now: Long, members: Int = 0, lead: Long = TEST_FILL_LEAD): Int = (target(c, floor, now, lead) - maxOf(c.memberCount.toInt(), members)).coerceAtLeast(0)

    /** Whether [leagueId]'s cohorts get scripted members only once a real member has joined: the Hackathon rounds (FR-043). */
    fun needsRealMember(leagueId: Int): Boolean = leagueId >= LeagueParams.TEST_LEAGUE_MIN

    /** [needsRealMember], unless `--fill-empty` — a local run (quickstart §5a) — fills empty rounds too. */
    private fun waitsForReal(leagueId: Int, args: Args): Boolean = needsRealMember(leagueId) && "fill-empty" !in args.flags

    /** Lamports the payer sends a wallet holding [balance] before it claims and joins, so the claim takes none from the faucet (FR-043a). */
    fun solTopUp(balance: Long, faucetTopUp: Long): Long = (faucetTopUp + JOIN_RESERVE_LAMPORTS - balance).coerceAtLeast(0)

    /** Whether [members] hold anyone not on the scripted list: what a Hackathon round waits for (FR-043). */
    fun hasRealMember(members: List<MembershipSnapshot>, scripted: Set<SolanaPublicKey>): Boolean = members.any { it.wallet !in scripted }

    /** Where consecutive test rounds start in their pool: coprime with 36 and 56, so every wallet takes turns. */
    private const val TEST_POOL_STRIDE = 5

    /**
     * The wallets that may play [leagueId]'s cohort [index]: the 7-day twenty, the 21-day group whose turn it is,
     * or for a test league every wallet whose own league's floor in [floors] is 0.
     */
    fun poolFor(roster: List<ScriptedWallet>, leagueId: Int, index: Long, floors: Floors = Floors()): List<ScriptedWallet> = when (leagueId) {
        LeagueParams.LEAGUE_7D -> roster.filter { it.group == "7d" }.sortedBy { it.index }
        LeagueParams.LEAGUE_21D -> {
            val groups = roster.filter { it.league == "21d" }.map { it.group }.distinct().sorted()
            if (groups.isEmpty()) emptyList() else roster.filter { it.group == groups[(index % groups.size).toInt()] }.sortedBy { it.index }
        }
        // A Hackathon round starts every 30 minutes in each of two lobbies, and each wallet
        // holds one lobby place and one running Holdout at a time (FR-006a, FR-006b): the
        // wallets Spark and Habit leave idle on devnet, the starting point moved on each
        // round so the same few never play them all, and half the pool apart between
        // leagues so the two lobbies' rounds rarely reach for the same wallets.
        else -> {
            val pool = roster.filter { floors.ofRoster(it.league) == 0 }.sortedWith(compareBy({ it.league }, { it.group }, { it.index }))
            val offset = (leagueId - LeagueParams.TEST_LEAGUE_MIN).toLong() * (pool.size / 2)
            if (pool.isEmpty()) pool else pool.indices.map { pool[((index * TEST_POOL_STRIDE + offset + it) % pool.size).toInt()] }
        }
    }

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
        val floors = floors(args)
        val floor = floors.of(c.leagueId)
        val scripted = members.filter { it.wallet in scriptedKeys }
        return buildJsonObject {
            put("command", "scripted plan")
            put("cohort", address.base58())
            put("league", c.leagueId)
            put("index", c.index)
            put("status", Derive.status(c, now).name)
            put("floor", floor)
            val lead = fillLead(args)
            put("target", target(c, floor, now, lead))
            // When the target next grows: a Hackathon round's last minute, or a lobby's last day.
            nextFillAt(c, floor, now, lead)?.let { put("fillsAt", it) }
            put("members", c.memberCount)
            put("real", members.size - scripted.size)
            put("scripted", scripted.size)
            val waiting = waitsForReal(c.leagueId, args) && !hasRealMember(members, scriptedKeys)
            put("waitingForRealMember", waiting)
            put("deficit", if (waiting) 0 else deficit(c, floor, now, members.size, lead))
            putJsonArray("pool") {
                poolFor(roster, c.leagueId, c.index, floors).forEach { w ->
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
        val lead = fillLead(args)
        val lastStart = args.option("last-start")?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() ?: configError("--last-start must be an instant, got $it") } ?: DEFAULT_LAST_START
        val keysDir = config.scriptedKeysDir ?: configError("HOLDOUT_SCRIPTED_KEYS_DIR is not set")
        val programId = config.programId
        val now = (rpc.chainTime() ?: failed("could not read chain time from ${config.rpcUrl}")).unixSeconds
        val onChain =
            rpc.accountData(Pdas.config(programId))?.let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() }
                ?: configError("the program at $programId is not initialized (or Config could not be read)")
        // Settled cohorts are left out: a scripted pass needs only lobbies and running cohorts.
        val cohorts = (rpc.unsettledCohorts(programId) ?: failed("could not list cohorts")).sortedWith(compareBy({ it.second.leagueId }, { it.second.index }))
        val byAddress = cohorts.toMap()
        val keys = mutableMapOf<SolanaPublicKey, Keypair>()
        fun keyOf(w: ScriptedWallet): Keypair = keys.getOrPut(w.pubkey) {
            val k = loadKeypair(File(keysDir, w.fileName).path, "scripted ${w.fileName}")
            if (k.publicKey != w.pubkey) configError("${w.fileName} holds ${k.publicKey}, but scripted-wallets.json lists ${w.pubkey}")
            k
        }

        // The payer is also the key that opens cohorts: top-ups stop above PAYER_RESERVE_LAMPORTS, and
        // without a payer (or its balance) a wallet that needs SOL simply waits for a later pass.
        val payer = config.payerKeypair?.let { loadKeypair(it, "payer") }
        var payerSpendable = payer?.let { rpc.balance(it.publicKey) }?.minus(PAYER_RESERVE_LAMPORTS) ?: 0L
        val scriptedKeys = roster.map { it.pubkey }.toSet()

        val steps = mutableListOf<JsonObject>()
        var failures = 0
        // When the loop should run this again (chain time): the next fill window or scripted day.
        val wakes = mutableListOf<Long>()
        val byKey = roster.associateBy { it.pubkey }

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
            val pool = poolFor(roster, c.leagueId, c.index, floors)
            val detail: JsonObjectBuilder.() -> Unit = {
                put("league", c.leagueId)
                put("index", c.index)
            }

            // ---- fill an open lobby to its target -----------------------------
            if (status == CohortStatus.Open && c.start <= lastStart && pool.isNotEmpty()) {
                nextFillAt(c, floor, now, lead)?.let(wakes::add)
                if (target(c, floor, now, lead) == 0) continue
                // The cohort and its roster read again, together: a member who joined or
                // left since the cohort list was read moves the deficit either way.
                val fresh = rpc.cohortRead(programId, address) ?: failed("could not read cohort ${c.leagueId}/${c.index} and its roster")
                // A Hackathon round nobody real has joined stays empty (FR-043).
                if (waitsForReal(c.leagueId, args) && !hasRealMember(fresh.members, scriptedKeys)) continue
                // Scripted members already in it submit their first day once it starts.
                if (fresh.members.any { it.wallet in scriptedKeys }) wakes += c.start
                var needed = deficit(fresh.cohort, floor, now, fresh.members.size, lead)
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
                    if (topUp > 0 && (payer == null || topUp > payerSpendable)) {
                        steps += buildJsonObject {
                            put("step", "join")
                            put("status", "skipped")
                            detail()
                            put("wallet", w.pubkey.base58())
                            put("reason", if (payer == null) "needs $topUp lamports and HOLDOUT_PAYER_KEYPAIR is not set" else "needs $topUp lamports and the payer is down to its reserve of $PAYER_RESERVE_LAMPORTS")
                        }
                        continue
                    }
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
                            if (topUp > 0) add(Instructions.systemTransfer(payer!!.publicKey, w.pubkey, topUp.toULong()))
                            if (needsFaucet) add(Instructions.claimFaucet(programId, w.pubkey, w.pubkey, onChain.skrMint, onChain.sponsor))
                            add(Instructions.joinCohort(programId, w.pubkey, onChain.skrMint, address, tier, running))
                        }
                        signAndSend(rpc, "scripted join ${c.leagueId}/${c.index}", ixs, if (topUp > 0) listOf(payer!!, key) else listOf(key), args.dryRun, pause, beforeRetrying = "the next pass re-reads the roster")
                    }
                    if (joined) {
                        needed--
                        payerSpendable -= topUp
                        wakes += c.start
                    }
                }
            }

            // ---- submit today's day for every scripted member due to -----------
            if (status == CohortStatus.Running) {
                val members = rpc.memberships(programId, address) ?: failed("could not read the roster of cohort ${c.leagueId}/${c.index}")
                // Every scripted wallet, not only today's pool: one that joined under other floors still plays its days.
                nextDayAt(c, address, members.filter { it.wallet in scriptedKeys }, now)?.let(wakes::add)
                for (m in members) {
                    val w = byKey[m.wallet] ?: continue
                    if (!shouldSubmit(c, address, m, now)) continue
                    val day = Derive.dayIndex(c, now)
                    val key = keyOf(w)
                    val submitted = attempt("submit_day", {
                        detail()
                        put("wallet", w.pubkey.base58())
                        put("day", day)
                    }) {
                        signAndSend(rpc, "scripted submit ${c.leagueId}/${c.index} day $day", listOf(Instructions.submitDay(programId, w.pubkey, address, day, proofOf(address, w.pubkey, day))), listOf(key), args.dryRun, pause, beforeRetrying = "the next pass re-reads the roster")
                    }
                    // A day that did not land is retried soon, while it is still today.
                    if (!submitted) wakes += now + RETRY_SECONDS
                }
            }
        }

        val summary = buildJsonObject {
            put("command", "scripted run")
            put("dryRun", args.dryRun)
            put("chainTime", now)
            put("failed", failures)
            wakes.filter { it > now }.minOrNull()?.let { put("nextScriptedAt", it) }
            putJsonArray("steps") { steps.forEach { add(it) } }
        }
        if (failures > 0) throw CrankSummaryException(EXIT_FAILED, summary)
        return summary
    }

    // ---- leave -------------------------------------------------------------

    /**
     * `scripted leave --cohort <address>`: every scripted wallet holding a place
     * in that cohort's lobby leaves it, through the program's own `leave_cohort`
     * — a full refund while joining is open (FR-009). Real members are never
     * touched. Used once on devnet to take the scripted members out of the
     * first Spark · 7-day when Spark's floor dropped to 0 (owner, 13 October).
     */
    suspend fun leave(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        roster: List<ScriptedWallet> = roster(),
        loadKeypair: (path: String, role: String) -> Keypair = Keypair::load,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): JsonObject {
        checkArgs(args)
        val text = args.option("cohort") ?: configError("scripted leave needs --cohort <address>")
        val address = runCatching { SolanaPublicKey.from(text) }.getOrNull() ?: configError("--cohort is not a public key: $text")
        val keysDir = config.scriptedKeysDir ?: configError("HOLDOUT_SCRIPTED_KEYS_DIR is not set")
        val programId = config.programId
        val now = (rpc.chainTime() ?: failed("could not read chain time from ${config.rpcUrl}")).unixSeconds
        val onChain =
            rpc.accountData(Pdas.config(programId))?.let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() }
                ?: configError("the program at $programId is not initialized (or Config could not be read)")
        val read = rpc.cohortRead(programId, address) ?: failed("could not read cohort $address and its roster")
        val c = read.cohort
        val status = Derive.status(c, now)
        if (status != CohortStatus.Open || now >= c.joinCutoff) {
            failed("cohort ${c.leagueId}/${c.index} is $status: leave_cohort refunds only while joining is open (FR-009, 6021)")
        }
        val byKey = roster.associateBy { it.pubkey }
        val leaving = read.members.filter { it.wallet in byKey && it.state == MembershipSnapshot.State.Joined }
        val steps = mutableListOf<JsonObject>()
        var failures = 0
        for (m in leaving) {
            val w = byKey.getValue(m.wallet)
            val key = loadKeypair(File(keysDir, w.fileName).path, "scripted ${w.fileName}")
            if (key.publicKey != w.pubkey) configError("${w.fileName} holds ${key.publicKey}, but scripted-wallets.json lists ${w.pubkey}")
            steps += try {
                val signature = signAndSend(
                    rpc,
                    "scripted leave ${c.leagueId}/${c.index} ${w.pubkey.base58()}",
                    listOf(Instructions.leaveCohort(programId, w.pubkey, onChain.skrMint, address)),
                    listOf(key),
                    args.dryRun,
                    pause,
                    beforeRetrying = "run scripted leave again: a wallet that has left is no longer in the roster",
                )
                buildJsonObject {
                    put("step", "leave_cohort")
                    put("status", if (args.dryRun) "simulated" else "sent")
                    put("wallet", w.pubkey.base58())
                    signature?.let { put("signature", it) }
                }
            } catch (e: CrankException) {
                if (e.exitCode == EXIT_CONFIG) throw e
                failures++
                buildJsonObject {
                    put("step", "leave_cohort")
                    put("status", "failed")
                    put("wallet", w.pubkey.base58())
                    put("error", e.message)
                }
            }
        }
        val summary = buildJsonObject {
            put("command", "scripted leave")
            put("dryRun", args.dryRun)
            put("chainTime", now)
            put("cohort", address.base58())
            put("league", c.leagueId)
            put("index", c.index)
            put("members", read.members.size)
            put("real", read.members.count { it.wallet !in byKey })
            put("scripted", leaving.size)
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
