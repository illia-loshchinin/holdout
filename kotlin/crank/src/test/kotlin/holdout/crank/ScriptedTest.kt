package holdout.crank

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import holdout.core.AccountLayout
import holdout.core.CohortSnapshot
import holdout.core.HoldoutDay
import holdout.core.Instructions
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import holdout.core.Pdas
import holdout.core.ProfileLayout
import holdout.core.ProfileSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T087, T088 — scripted members: the committed roster, the fill-to-a-floor
 * rule, the seeded tiers and forfeit days, and one pass that fills a lobby and
 * submits a running cohort's day against a cluster in miniature.
 */
class ScriptedTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val first = LeagueParams.FIRST_JUDGED_START
    private val week = 604_800L
    private val day = 86_400L

    private fun cohort(leagueId: Int = 0, index: Long = 0, memberCount: Long = 0, duration: Int = 7) = CohortSnapshot(
        leagueId = leagueId,
        index = index,
        start = first + index * week,
        joinCutoff = first + index * week - 3_600,
        durationDays = duration,
        repsPerDay = 25,
        dayLength = day,
        exercise = 0,
        chips = listOf(10uL, 25uL, 50uL, 100uL),
        memberCount = memberCount,
    )

    private fun key(i: Int) = Keypair.fromSeed(ByteArray(32) { (i + 40).toByte() })

    private fun syntheticRoster(): List<ScriptedWallet> = List(20) { ScriptedWallet(key(it).publicKey, "7d", "7d", it) } +
        listOf("21d-a", "21d-b", "21d-c").flatMapIndexed { g, group -> List(12) { ScriptedWallet(key(20 + g * 12 + it).publicKey, "21d", group, it) } }

    // ---- the roster and the rules -----------------------------------------

    @Test
    fun `the committed roster is 20 seven-day wallets and three groups of 12`() {
        val roster = Scripted.roster()
        assertEquals(56, roster.size)
        assertEquals(56, roster.map { it.pubkey }.toSet().size, "no wallet listed twice")
        assertEquals(mapOf("7d" to 20, "21d-a" to 12, "21d-b" to 12, "21d-c" to 12), roster.groupingBy { it.group }.eachCount())
        assertEquals("7d-03.json", roster.first { it.group == "7d" && it.index == 3 }.fileName)
    }

    @Test
    fun `the 21-day league rotates its three groups by cohort index`() {
        val roster = syntheticRoster()
        assertEquals(20, Scripted.poolFor(roster, 0, 5).size)
        assertEquals(listOf("21d-a", "21d-b", "21d-c", "21d-a"), (0L..3L).map { Scripted.poolFor(roster, 1, it).map { w -> w.group }.distinct().single() })
        assertEquals(8, Scripted.Floors(test = 8).of(100))
        val hackathon = Scripted.poolFor(roster, 102, 5)
        assertEquals(36, hackathon.size, "a Hackathon round draws on the 36 wallets Habit leaves idle")
        assertTrue(hackathon.all { it.league == "21d" })
    }

    @Test
    fun `on devnet Spark keeps 20, Habit gets none and a Hackathon round gets 4 once someone real joins`() {
        assertEquals(listOf(20, 0, 4), listOf(0, 1, 100).map { Scripted.Floors().of(it) })
        assertFalse(Scripted.needsRealMember(LeagueParams.LEAGUE_7D))
        assertFalse(Scripted.needsRealMember(LeagueParams.LEAGUE_21D))
        assertTrue(Scripted.needsRealMember(LeagueParams.TEST_LEAGUE_MIN))
    }

    @Test
    fun `the payer tops a wallet up to the faucet's top-up plus a join's reserve, never down`() {
        val faucet = 20_000_000L
        assertEquals(faucet + Scripted.JOIN_RESERVE_LAMPORTS, Scripted.solTopUp(0, faucet))
        assertEquals(1_600_000L, Scripted.solTopUp(faucet + Scripted.JOIN_RESERVE_LAMPORTS - 1_600_000, faucet))
        assertEquals(0L, Scripted.solTopUp(1_000_000_000, faucet))
    }

    @Test
    fun `a lobby is filled to half the floor, then to the floor a day before the cutoff, and never over`() {
        val c = cohort(memberCount = 3)
        val early = c.joinCutoff - 5 * day
        val late = c.joinCutoff - day + 1
        assertEquals(10, Scripted.target(c, 20, early))
        assertEquals(7, Scripted.deficit(c, 20, early))
        assertEquals(17, Scripted.deficit(c, 20, late))
        assertEquals(0, Scripted.deficit(c.copy(memberCount = 25), 20, late), "real members past the floor leave no room for scripted ones")
        assertEquals(0, Scripted.deficit(c, 20, c.joinCutoff), "joining has closed")
        assertEquals(12, Scripted.deficit(c, 20, late, members = 8), "real members who joined after the cohort was read take places")
        assertEquals(17, Scripted.deficit(c, 20, late, members = 2), "a roster read short never adds places")
        assertEquals(6, Scripted.target(cohort(leagueId = 1), 12, early))
        assertEquals(0, Scripted.target(c, 0, early))
    }

    @Test
    fun `tiers and forfeit days are fixed by the cohort and the wallet, with about 40 percent finishing`() {
        val cohortKey = SolanaPublicKey(ByteArray(32) { 3 })
        val wallets = List(4_000) { SolanaPublicKey(ByteBuffer.allocate(32).putInt(it).array()) }
        assertEquals(Scripted.tierOf(cohortKey, wallets[0]), Scripted.tierOf(cohortKey, wallets[0]))
        val tiers = wallets.groupingBy { Scripted.tierOf(cohortKey, it) }.eachCount()
        assertEquals(setOf(0, 1, 2, 3), tiers.keys)
        tiers.values.forEach { assertTrue(it in 850..1_150, "tiers spread evenly: $tiers") }

        val days = wallets.map { Scripted.forfeitDayOf(cohortKey, it, 7) }
        val finishers = days.count { it == null }.toDouble() / wallets.size
        assertTrue(finishers in 0.36..0.44, "finish share $finishers")
        val forfeits = days.filterNotNull()
        assertTrue(forfeits.all { it in 0..6 })
        val firstHalf = forfeits.count { it < 4 }.toDouble() / forfeits.size
        assertTrue(firstHalf > 0.75, "forfeits fall mostly in the first half: $firstHalf")
    }

    @Test
    fun `a scripted member submits each day until its forfeit day, then stops`() {
        val c = cohort()
        val cohortKey = SolanaPublicKey(ByteArray(32) { 3 })
        val forfeiter = (0 until 500).map { SolanaPublicKey(ByteBuffer.allocate(32).putInt(it).array()) }.first { Scripted.forfeitDayOf(cohortKey, it, 7) == 2 }
        fun m(days: Int) = MembershipSnapshot(forfeiter, 0, 10uL, first, days, MembershipSnapshot.State.Joined)
        val on = { d: Int -> c.start + d * day + 60 }
        assertTrue(Scripted.shouldSubmit(c, cohortKey, m(0), on(0)))
        assertFalse(Scripted.shouldSubmit(c, cohortKey, m(1), on(0)), "today already submitted")
        assertTrue(Scripted.shouldSubmit(c, cohortKey, m(1), on(1)))
        assertFalse(Scripted.shouldSubmit(c, cohortKey, m(2), on(2)), "its forfeit day")
        assertFalse(Scripted.shouldSubmit(c, cohortKey, m(0), c.start - 1), "not running yet")
    }

    @Test
    fun `a wallet with a lobby place elsewhere, or still running past the start, cannot join`() {
        val c = cohort(index = 1)
        val other = SolanaPublicKey(ByteArray(32) { 8 })
        val now = c.joinCutoff - day
        fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(other, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending)
        assertTrue(Scripted.canJoin(null, c, null, now), "a wallet that has never played")
        assertTrue(Scripted.canJoin(profile(running = ProfileSnapshot.Running(other, c.start)), c, c.start, now), "back to back")
        assertFalse(Scripted.canJoin(profile(running = ProfileSnapshot.Running(other, c.start + 1)), c, c.start + 1, now))
        assertFalse(Scripted.canJoin(profile(pending = ProfileSnapshot.Pending(other, c.start + week, c.start + 2 * week)), c, null, now))
    }

    // ---- one pass ----------------------------------------------------------

    private val mint = SolanaPublicKey(ByteArray(32) { 9 })
    private val sponsor = SolanaPublicKey(ByteArray(32) { 5 })

    private inner class Chain(val now: Long) : HttpNetworkDriver {
        val accounts = mutableMapOf<SolanaPublicKey, ByteArray>()
        val sent = mutableListOf<Transaction>()

        // Fee payers whose transactions the node refuses.
        val refused = mutableSetOf<SolanaPublicKey>()

        // Lamports by wallet; anyone not listed holds 1 SOL, which needs no top-up.
        val balances = mutableMapOf<SolanaPublicKey, Long>()

        fun names(t: Transaction) = t.message.instructions.map { ix ->
            if (t.message.accounts[ix.programIdIndex.toInt()] == Instructions.SYSTEM_PROGRAM) return@map "transfer"
            listOf("claim_faucet", "join_cohort", "submit_day").first { Instructions.discriminator(it).contentEquals(ix.data.copyOf(8)) }
        }

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = Json.parseToJsonElement(request.body ?: "{}").jsonObject
            val params = body["params"]?.jsonArray
            return when (body.getValue("method").jsonPrimitive.content) {
                "getAccountInfo" -> accountInfo(accounts[SolanaPublicKey.from(params!![0].jsonPrimitive.content)])
                "getProgramAccounts" -> {
                    val options = params!![1].jsonObject
                    val filters = options.getValue("filters").jsonArray.map { it.jsonObject }
                    val size = filters.first { "dataSize" in it }.getValue("dataSize").jsonPrimitive.int
                    val cohortFilter = filters.mapNotNull { it["memcmp"]?.jsonObject }.firstOrNull { it.getValue("offset").jsonPrimitive.int == AccountLayout.Membership.COHORT }
                    val slice = options["dataSlice"]?.jsonObject
                    val entries = accounts.filter { it.value.size == size }
                        .filter { cohortFilter == null || SolanaPublicKey(it.value.copyOfRange(8, 40)).base58() == cohortFilter.getValue("bytes").jsonPrimitive.content }
                        .map { (k, d) ->
                            val bytes = slice?.let { d.copyOfRange(it.getValue("offset").jsonPrimitive.int, it.getValue("offset").jsonPrimitive.int + it.getValue("length").jsonPrimitive.int) } ?: d
                            """{"pubkey":"${k.base58()}","account":{"data":["${Base64.getEncoder().encodeToString(bytes)}","base64"],"executable":false,"lamports":1,"owner":"${programId.base58()}","rentEpoch":0}}"""
                        }
                    ok("[${entries.joinToString(",")}]")
                }
                "getBalance" -> ok("""{"context":{"slot":1},"value":${balances[SolanaPublicKey.from(params!![0].jsonPrimitive.content)] ?: 1_000_000_000L}}""")
                "getSlot" -> ok("100")
                "getBlockTime" -> ok("$now")
                "getLatestBlockhash" -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""")
                "sendTransaction" -> {
                    val tx = Transaction.from(Base64.getDecoder().decode(params!![0].jsonPrimitive.content))
                    if (tx.message.accounts[0] in refused) return """{"jsonrpc":"2.0","error":{"code":-32002,"message":"Transaction simulation failed: custom program error: 0x1"},"id":"x"}"""
                    sent += tx
                    ok("\"sig${sent.size}\"")
                }
                "getSignatureStatuses" -> ok("""{"context":{"slot":1},"value":[{"err":null,"confirmationStatus":"confirmed"}]}""")
                else -> error("unexpected method ${body["method"]}")
            }
        }
    }

    private fun ok(result: String) = """{"jsonrpc":"2.0","result":$result,"id":"x"}"""

    private fun accountInfo(data: ByteArray?) = ok(
        if (data == null) {
            """{"context":{"slot":1},"value":null}"""
        } else {
            """{"context":{"slot":1},"value":{"data":["${Base64.getEncoder().encodeToString(data)}","base64"],"executable":false,"lamports":1,"owner":"11111111111111111111111111111111","rentEpoch":0}}"""
        },
    )

    private fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)

    private fun configBytes(): ByteArray {
        val l = AccountLayout.Config
        return le(l.SIZE).put(0, l.DISCRIMINATOR).put(l.SPONSOR, sponsor.bytes).put(l.SKR_MINT, mint.bytes).putInt(l.CHIP_VERSION, 1).putLong(l.SOL_TOP_UP, FAUCET_TOP_UP).array()
    }

    private fun cohortBytes(c: CohortSnapshot): ByteArray {
        val l = AccountLayout.Cohort
        val b = le(l.SIZE).put(0, l.DISCRIMINATOR)
        b.put(l.LEAGUE_ID, c.leagueId.toByte()).putInt(l.INDEX, c.index.toInt()).putLong(l.START, c.start).putLong(l.JOIN_CUTOFF, c.joinCutoff)
        b.put(l.DURATION_DAYS, c.durationDays.toByte()).putShort(l.REPS_PER_DAY, c.repsPerDay.toShort()).putLong(l.DAY_LENGTH, c.dayLength)
        c.chips.forEachIndexed { i, v -> b.putLong(l.CHIPS + 8 * i, v.toLong()) }
        return b.putInt(l.MEMBER_COUNT, c.memberCount.toInt()).array()
    }

    private fun membershipBytes(cohort: SolanaPublicKey, wallet: SolanaPublicKey, days: Int): ByteArray {
        val l = AccountLayout.Membership
        return le(l.SIZE).put(0, l.DISCRIMINATOR).put(l.COHORT, cohort.bytes).put(l.WALLET, wallet.bytes).putLong(l.STAKE, 10).put(l.DAYS_COMPLETED, days.toByte()).array()
    }

    private fun profileBytes(wallet: SolanaPublicKey, lastFaucetDay: Long): ByteArray {
        val l = ProfileLayout
        return le(l.SIZE).put(0, l.DISCRIMINATOR).put(l.WALLET, wallet.bytes).putLong(l.LAST_FAUCET_DAY, lastFaucetDay).array()
    }

    private val env = mapOf("HOLDOUT_RPC_URL" to "http://rpc.test", "HOLDOUT_PROGRAM_ID" to programId.base58(), "HOLDOUT_SCRIPTED_KEYS_DIR" to "/keys/scripted")

    @Test
    fun `a pass tops an open lobby up to its target and submits a running cohort's day`() = runBlocking {
        val roster = syntheticRoster()
        val keys = (0 until 56).associate { key(it).publicKey to key(it) }
        val open = cohort(leagueId = 0, index = 1, memberCount = 3)
        val running = cohort(leagueId = 1, index = 0, memberCount = 2, duration = 21)
        val now = open.joinCutoff - 3 * day // lobby open, target half the floor: 10
        val chain = Chain(now)
        val openKey = Pdas.cohort(programId, 0, 1)
        val runningKey = Pdas.cohort(programId, 1, 0)
        chain.accounts[Pdas.config(programId)] = configBytes()
        chain.accounts[openKey] = cohortBytes(open)
        chain.accounts[runningKey] = cohortBytes(running)
        // Two scripted members of the running 21-day cohort, on its day 6 with 6 days done.
        val today = ((now - running.start) / day).toInt()
        val group = Scripted.poolFor(roster, 1, 0)
        val submitters = group.filter { Scripted.forfeitDayOf(runningKey, it.pubkey, 21).let { d -> d == null || d > today } }.take(1)
        val stopped = group.filter { Scripted.forfeitDayOf(runningKey, it.pubkey, 21).let { d -> d != null && d <= today } }.take(1)
        (submitters + stopped).forEach { chain.accounts[Pdas.membership(programId, runningKey, it.pubkey)] = membershipBytes(runningKey, it.pubkey, today) }

        val byFile = roster.associate { w -> w.fileName to keys.getValue(w.pubkey) }
        val json = Scripted.run(
            parseArgs(listOf("scripted", "run")),
            CrankConfig.from(env),
            Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)),
            roster,
            loadKeypair = { path, _ -> byFile.getValue(java.io.File(path).name) },
            pause = {},
        )
        assertEquals(0, json.getValue("failed").jsonPrimitive.int)
        val joins = chain.sent.filter { "join_cohort" in chain.names(it) }
        assertEquals(7, joins.size, "3 real members, target 10")
        joins.forEach { assertEquals(listOf("claim_faucet", "join_cohort"), chain.names(it), "a wallet with no SKR claims first, in the same transaction") }
        val submits = chain.sent.filter { "submit_day" in chain.names(it) }
        assertEquals(submitters.map { it.pubkey }, submits.map { it.message.accounts[0] }, "only the member not scheduled to have forfeited")
        assertNull(stopped.firstOrNull()?.let { s -> submits.firstOrNull { it.message.accounts[0] == s.pubkey } })
        // Every transaction is paid by the scripted wallet itself, never by the faucet's sponsor.
        chain.sent.forEach { assertTrue(it.message.accounts[0] in keys.keys) }
    }

    @Test
    fun `a wallet that already took the faucet today claims again - the faucet has no daily limit per wallet`() = runBlocking {
        val roster = syntheticRoster()
        val keys = (0 until 56).associate { key(it).publicKey to key(it) }
        val open = cohort(leagueId = 0, index = 1, memberCount = 3)
        val now = open.joinCutoff - 3 * day
        val chain = Chain(now)
        chain.accounts[Pdas.config(programId)] = configBytes()
        chain.accounts[Pdas.cohort(programId, 0, 1)] = cohortBytes(open)
        roster.forEach { chain.accounts[Pdas.profile(programId, it.pubkey)] = profileBytes(it.pubkey, lastFaucetDay = HoldoutDay.indexOf(now)) }

        val byFile = roster.associate { w -> w.fileName to keys.getValue(w.pubkey) }
        val json = Scripted.run(parseArgs(listOf("scripted", "run")), CrankConfig.from(env), Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)), roster, loadKeypair = { path, _ -> byFile.getValue(java.io.File(path).name) }, pause = {})

        assertEquals(0, json.getValue("failed").jsonPrimitive.int)
        val joins = chain.sent.filter { "join_cohort" in chain.names(it) }
        assertEquals(7, joins.size, "3 real members, target 10")
        joins.forEach { assertEquals(listOf("claim_faucet", "join_cohort"), chain.names(it)) }
    }

    @Test
    fun `a join that fails does not fill a place - the next wallet in the pool takes it`() = runBlocking {
        val roster = syntheticRoster()
        val keys = (0 until 56).associate { key(it).publicKey to key(it) }
        val open = cohort(leagueId = 0, index = 1, memberCount = 3)
        val chain = Chain(open.joinCutoff - 3 * day) // target 10: 7 places to fill
        chain.accounts[Pdas.config(programId)] = configBytes()
        chain.accounts[Pdas.cohort(programId, 0, 1)] = cohortBytes(open)
        // The first two wallets the pass would try are refused every time.
        chain.refused += Scripted.poolFor(roster, 0, 1).take(2).map { it.pubkey }

        val byFile = roster.associate { w -> w.fileName to keys.getValue(w.pubkey) }
        val json = assertFailsWith<CrankSummaryException> {
            Scripted.run(parseArgs(listOf("scripted", "run")), CrankConfig.from(env), Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)), roster, loadKeypair = { path, _ -> byFile.getValue(java.io.File(path).name) }, pause = {})
        }.summary
        assertEquals(2, json.getValue("failed").jsonPrimitive.int)
        assertEquals(7, chain.sent.count { "join_cohort" in chain.names(it) }, "all 7 places filled by the wallets after the refused ones")
    }

    // ---- Hackathon rounds --------------------------------------------------

    private val payerKey = key(90)
    private val stranger = SolanaPublicKey(ByteArray(32) { 77 })

    /** A Hackathon round 10 minutes before its cutoff, holding [members], after one pass. */
    private fun hackathonPass(
        members: List<SolanaPublicKey>,
        balances: Map<SolanaPublicKey, Long> = emptyMap(),
        flags: List<String> = emptyList(),
        withPayer: Boolean = true,
    ): Pair<Chain, JsonObject> = runBlocking {
        val roster = syntheticRoster()
        val keys = (0 until 56).associate { key(it).publicKey to key(it) }
        val round = cohort(leagueId = 100, index = 0, memberCount = members.size.toLong(), duration = 3)
        val chain = Chain(round.joinCutoff - 600)
        val roundKey = Pdas.cohort(programId, 100, 0)
        chain.accounts[Pdas.config(programId)] = configBytes()
        chain.accounts[roundKey] = cohortBytes(round)
        members.forEach { chain.accounts[Pdas.membership(programId, roundKey, it)] = membershipBytes(roundKey, it, 0) }
        chain.balances[payerKey.publicKey] = 10_000_000_000L
        chain.balances += balances
        val byFile = roster.associate { w -> w.fileName to keys.getValue(w.pubkey) } + ("payer.json" to payerKey)
        val json = Scripted.run(
            parseArgs(listOf("scripted", "run") + flags),
            CrankConfig.from(if (withPayer) env + ("HOLDOUT_PAYER_KEYPAIR" to "/keys/payer.json") else env),
            Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)),
            roster,
            loadKeypair = { path, _ -> byFile.getValue(java.io.File(path).name) },
            pause = {},
        )
        chain to json
    }

    @Test
    fun `a Hackathon round nobody real has joined stays empty`() {
        assertEquals(0, hackathonPass(emptyList()).first.sent.size)
        val scripted = Scripted.poolFor(syntheticRoster(), 100, 0).first().pubkey
        assertEquals(0, hackathonPass(listOf(scripted)).first.sent.size, "a scripted member alone does not count as someone real")
        val local = hackathonPass(emptyList(), flags = listOf("--fill-empty")).first
        assertEquals(4, local.sent.count { "join_cohort" in local.names(it) }, "a local run fills empty rounds, to watch a cycle alone")
    }

    @Test
    fun `a Hackathon round a real member has joined is topped up to 4 from the 21-day wallets`() {
        val (chain, _) = hackathonPass(listOf(stranger))
        val joins = chain.sent.filter { "join_cohort" in chain.names(it) }
        assertEquals(3, joins.size, "1 real member, floor 4")
        val idle = Scripted.poolFor(syntheticRoster(), 100, 0).map { it.pubkey }.toSet()
        joins.forEach { assertTrue(it.message.accounts[0] in idle) }
    }

    @Test
    fun `a wallet short of SOL is topped up by the payer in its join, before the claim, so the faucet sends it none`() {
        val pool = Scripted.poolFor(syntheticRoster(), 100, 0)
        val (chain, _) = hackathonPass(listOf(stranger), pool.associate { it.pubkey to FAUCET_TOP_UP })
        val joins = chain.sent.filter { "join_cohort" in chain.names(it) }
        assertEquals(3, joins.size)
        joins.forEach {
            assertEquals(listOf("transfer", "claim_faucet", "join_cohort"), chain.names(it))
            assertEquals(payerKey.publicKey, it.message.accounts[0], "the payer pays the fee too")
            val amount = ByteBuffer.wrap(it.message.instructions[0].data, 4, 8).order(ByteOrder.LITTLE_ENDIAN).long
            assertEquals(Scripted.JOIN_RESERVE_LAMPORTS, amount)
        }
    }

    @Test
    fun `the payer keeps its reserve, and a pass without a payer skips wallets short of SOL instead of failing`() {
        val pool = Scripted.poolFor(syntheticRoster(), 100, 0)
        val short = pool.associate { it.pubkey to FAUCET_TOP_UP }
        val low = hackathonPass(listOf(stranger), short + (payerKey.publicKey to Scripted.PAYER_RESERVE_LAMPORTS + 1))
        assertEquals(0, low.first.sent.size, "nothing above the payer's reserve to give")
        assertEquals(0, low.second.getValue("failed").jsonPrimitive.int)
        assertEquals(listOf("skipped"), low.second.getValue("steps").jsonArray.map { it.jsonObject.getValue("status").jsonPrimitive.content }.distinct())
        val none = hackathonPass(listOf(stranger), short, withPayer = false)
        assertEquals(0, none.first.sent.size)
        assertEquals(0, none.second.getValue("failed").jsonPrimitive.int)
        val rich = hackathonPass(listOf(stranger), withPayer = false)
        assertEquals(3, rich.first.sent.count { "join_cohort" in rich.first.names(it) }, "wallets that hold enough SOL need no payer")
    }

    @Test
    fun `consecutive Hackathon rounds start at different wallets, and every one of the 36 takes a turn`() {
        val roster = syntheticRoster()
        val firsts = (0L until 36L).map { Scripted.poolFor(roster, 100, it).take(3).map { w -> w.pubkey } }
        assertTrue(firsts.zipWithNext().all { (a, b) -> a.intersect(b.toSet()).isEmpty() }, "the next round's first three are new")
        assertEquals(36, firsts.flatten().toSet().size)
        assertEquals(36, Scripted.poolFor(roster, 100, 7).toSet().size, "a rotation, not a subset")
    }

    @Test
    fun `the join reserve covers a first claim's account rent and fees, so the faucet moves no lamports`() {
        fun rent(bytes: Int) = (bytes + 128L) * 6_960L
        val tokenAccount = 165
        val firstClaim = rent(ProfileLayout.SIZE) + rent(tokenAccount) + 2 * 5_000L
        val join = rent(AccountLayout.Membership.SIZE) + 5_000L
        assertTrue(Scripted.JOIN_RESERVE_LAMPORTS >= firstClaim + join, "reserve ${Scripted.JOIN_RESERVE_LAMPORTS} < ${firstClaim + join}: raise JOIN_RESERVE_LAMPORTS")
    }

    @Test
    fun `plan reports a Hackathon round nobody real has joined as waiting, with nothing to add`() = runBlocking {
        val round = cohort(leagueId = 100, index = 0, memberCount = 1, duration = 3)
        val chain = Chain(round.joinCutoff - 600)
        val roundKey = Pdas.cohort(programId, 100, 0)
        val scripted = Scripted.poolFor(syntheticRoster(), 100, 0).first().pubkey
        chain.accounts[roundKey] = cohortBytes(round)
        chain.accounts[Pdas.membership(programId, roundKey, scripted)] = membershipBytes(roundKey, scripted, 0)
        fun plan(vararg flags: String) = runBlocking {
            Scripted.plan(parseArgs(listOf("scripted", "plan", "--cohort", roundKey.base58()) + flags), CrankConfig.from(env), Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)), syntheticRoster())
        }
        val waiting = plan()
        assertTrue(waiting.getValue("waitingForRealMember").jsonPrimitive.content.toBoolean())
        assertEquals(0, waiting.getValue("deficit").jsonPrimitive.int)
        assertEquals(3, plan("--fill-empty").getValue("deficit").jsonPrimitive.int, "a local run fills it to 4")
    }

    private companion object {
        const val FAUCET_TOP_UP = 20_000_000L
    }
}
