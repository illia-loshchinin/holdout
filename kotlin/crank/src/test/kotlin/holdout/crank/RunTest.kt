package holdout.crank

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import holdout.core.AccountLayout
import holdout.core.CohortSnapshot
import holdout.core.ConfigSnapshot
import holdout.core.Instructions
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Pdas
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
import kotlin.test.assertTrue

/**
 * T071 — `crank run`: the planning rules on their own, then one whole pass
 * against a cluster in miniature that serves real account bytes and applies
 * what settlement needs to see land.
 */
class RunTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val payer = Keypair.fromSeed(ByteArray(32) { 7 })
    private val sponsor = SolanaPublicKey(ByteArray(32) { 5 })
    private val mint = SolanaPublicKey(ByteArray(32) { 9 })
    private val feeDestination = SolanaPublicKey(ByteArray(32) { 6 })
    private val developerDestination = SolanaPublicKey(ByteArray(32) { 8 })

    private val week = 604_800L
    private val day = 86_400L
    private val first = LeagueParams.FIRST_JUDGED_START
    private val league0 = LeagueSnapshot(LeagueParams.judged(0, first), nextIndex = 3)

    private fun cohort(index: Long, memberCount: Long = 0, counted: Long = 0, settled: Boolean = false) = CohortSnapshot(
        leagueId = 0,
        index = index,
        start = first + index * week,
        joinCutoff = first + index * week - 3_600,
        durationDays = 7,
        repsPerDay = 25,
        dayLength = day,
        exercise = 0,
        chips = listOf(10uL, 25uL, 50uL, 100uL),
        memberCount = memberCount,
        counted = counted,
        settled = settled,
    )

    private fun member(seed: Int, days: Int, state: MembershipSnapshot.State = MembershipSnapshot.State.Joined) = MembershipSnapshot(SolanaPublicKey(ByteArray(32) { seed.toByte() }), 0, 10uL, first, days, state)

    // ---- the plan ----------------------------------------------------------

    @Test
    fun `a league opens cohort 0 at once, then each cohort once the one before it has started`() {
        val fresh = LeagueSnapshot(LeagueParams.judged(0, first), nextIndex = 0)
        assertEquals(listOf(0L), Run.dueOpens(fresh, now = first - 30 * day))
        val one = fresh.copy(nextIndex = 1)
        assertEquals(emptyList(), Run.dueOpens(one, now = first - 1))
        assertEquals(listOf(1L), Run.dueOpens(one, now = first))
        // After an outage the crank catches up, one cohort per started predecessor.
        assertEquals(listOf(1L, 2L, 3L), Run.dueOpens(one, now = first + 2 * week))
        assertEquals(Run.MAX_OPENS_PER_LEAGUE, Run.dueOpens(one, now = first + 100 * week).size)
    }

    @Test
    fun `only members in breach are forfeited, in pages`() {
        val c = cohort(2)
        val now = c.start + day + day / 2 // day 1 of the cohort
        val members = listOf(member(1, 1), member(2, 0), member(3, 0, MembershipSnapshot.State.Forfeited)) + List(12) { member(10 + it, 0) }
        val pages = Run.forfeitPages(c, members, now, pageSize = 10)
        assertEquals(listOf(10, 3), pages.map { it.size })
        assertFalse(pages.flatten().contains(members[0].wallet), "a member whose deadline has not passed")
        assertFalse(pages.flatten().contains(members[2].wallet), "a member already forfeited")
        assertEquals(listOf(10, 3), Run.forfeitPages(c, members, now, pageSize = 50).map { it.size }, "a page is never more than the program takes")
    }

    /** A test-lobby cohort: three 10-minute days from [start]. */
    private fun testCohort(leagueId: Int, index: Long, start: Long, memberCount: Long = 0) = cohort(index, memberCount).copy(leagueId = leagueId, start = start, joinCutoff = start - 300, durationDays = 3, repsPerDay = 5, dayLength = 600)

    @Test
    fun `settlement is queued in the order the cohorts ended, across leagues`() {
        val now = first + 3 * week + 1
        val queue = Run.settleQueue(listOf(cohort(2), cohort(0, settled = true), cohort(1), cohort(3)), now)
        assertEquals(listOf(1L, 2L), queue.map { it.index }, "settled and running cohorts are not queued")

        // Two test leagues share one progress slot: league 101's cohort ended first.
        val a = testCohort(100, 0, start = first + 600)
        val b = testCohort(101, 0, start = first)
        val c = testCohort(100, 1, start = first + 1_200)
        val order = Run.settleQueue(listOf(c, a, b), now = first + 3_600).map { it.leagueId to it.index }
        assertEquals(listOf(101 to 0L, 100 to 0L, 100 to 1L), order, "by end, not by league id (FR-060, FR-061a)")
    }

    // ---- one whole pass ----------------------------------------------------

    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun configBytes(sponsorFloat: Long): ByteArray {
        val l = AccountLayout.Config
        val b = le(l.SIZE)
        b.put(0, l.DISCRIMINATOR)
        b.put(l.SPONSOR, sponsor.bytes).put(l.SKR_MINT, mint.bytes).put(l.FEE_DESTINATION, feeDestination.bytes).put(l.DEVELOPER_DESTINATION, developerDestination.bytes)
        b.putInt(l.CHIP_VERSION, 1).putLong(l.SPONSOR_FLOAT, sponsorFloat).putLong(l.DAILY_LAMPORT_CAP, 720_000_000)
        return b.array()
    }

    private fun leagueBytes(l: LeagueSnapshot): ByteArray {
        val b = le(LeagueSnapshot.SIZE)
        b.put(0, LeagueSnapshot.DISCRIMINATOR)
        b.put(8, l.params.leagueId.toByte()).put(9, l.params.durationDays.toByte()).putShort(10, l.params.repsPerDay.toShort()).put(12, l.params.exercise.toByte())
        b.putLong(13, l.params.dayLength).putLong(21, l.params.firstStart).putLong(29, l.params.cadence).putLong(37, l.params.joinLock).putInt(45, l.nextIndex.toInt())
        b.put(LeagueSnapshot.FORM, l.params.form.toByte()).putLong(LeagueSnapshot.BOOST_RESERVE, l.boostReserve.toLong()).putLong(LeagueSnapshot.COMEBACK_RESERVE, l.comebackReserve.toLong())
        return b.array()
    }

    private fun cohortBytes(c: CohortSnapshot): ByteArray {
        val l = AccountLayout.Cohort
        val b = le(l.SIZE)
        b.put(0, l.DISCRIMINATOR)
        b.put(l.LEAGUE_ID, c.leagueId.toByte()).putInt(l.INDEX, c.index.toInt()).putLong(l.START, c.start).putLong(l.JOIN_CUTOFF, c.joinCutoff)
        b.put(l.DURATION_DAYS, c.durationDays.toByte()).putShort(l.REPS_PER_DAY, c.repsPerDay.toShort()).putLong(l.DAY_LENGTH, c.dayLength).put(l.EXERCISE, c.exercise.toByte())
        c.chips.forEachIndexed { i, v -> b.putLong(l.CHIPS + 8 * i, v.toLong()) }
        b.putInt(l.MEMBER_COUNT, c.memberCount.toInt()).putInt(l.COUNTED, c.counted.toInt()).put(l.SETTLED, if (c.settled) 1 else 0)
        return b.array()
    }

    private fun membershipBytes(cohort: SolanaPublicKey, m: MembershipSnapshot, counted: Boolean): ByteArray {
        val l = AccountLayout.Membership
        val b = le(l.SIZE)
        b.put(0, l.DISCRIMINATOR)
        b.put(l.COHORT, cohort.bytes).put(l.WALLET, m.wallet.bytes).put(l.TIER, m.tier.toByte()).putLong(l.STAKE, m.stake.toLong())
        b.putLong(l.JOINED_AT, m.joinedAt).put(l.DAYS_COMPLETED, m.daysCompleted.toByte()).put(l.STATE, m.state.ordinal.toByte()).put(l.COUNTED, if (counted) 1 else 0)
        return b.array()
    }

    /**
     * A devnet in miniature. It serves the accounts it holds, answers
     * `getProgramAccounts` by the query's size and cohort filters, and applies
     * `settle_count` as far as the crank can see it: the cohort's `counted`.
     */
    private inner class Chain(
        val now: Long,
        val sponsorLamports: Long,
        val refuse: Map<String, String> = emptyMap(),
        // Instructions whose simulation fails with this program error.
        val refuseSimulation: Map<String, Int> = emptyMap(),
    ) : HttpNetworkDriver {
        val accounts = mutableMapOf<SolanaPublicKey, ByteArray>()
        val sent = mutableListOf<String>()
        val sentTx = mutableListOf<Transaction>()

        fun nameOf(t: Transaction): String {
            val ix = t.message.instructions.single()
            return listOf("open_cohort", "top_up_sponsor", "record_forfeits", "settle_count", "settle_finalize").first { Instructions.discriminator(it).contentEquals(ix.data.copyOf(8)) }
        }

        private fun apply(t: Transaction) {
            if (nameOf(t) == "settle_count") {
                val cohortKey = t.message.accounts[t.message.instructions.single().accountIndices[3].toInt()]
                val c = CohortSnapshot.decode(accounts.getValue(cohortKey))
                accounts[cohortKey] = cohortBytes(c.copy(counted = c.memberCount))
            }
        }

        private fun programAccounts(params: kotlinx.serialization.json.JsonArray): String {
            val options = params[1].jsonObject
            val filters = options.getValue("filters").jsonArray.map { it.jsonObject }
            val size = filters.first { "dataSize" in it }.getValue("dataSize").jsonPrimitive.int
            val cohortFilter = filters.mapNotNull { it["memcmp"]?.jsonObject }.firstOrNull { it.getValue("offset").jsonPrimitive.int == AccountLayout.Membership.COHORT }
            val slice = options["dataSlice"]?.jsonObject
            val entries =
                accounts.filter { (_, data) -> data.size == size }
                    .filter { (_, data) -> cohortFilter == null || SolanaPublicKey(data.copyOfRange(8, 40)).base58() == cohortFilter.getValue("bytes").jsonPrimitive.content }
                    .map { (key, data) ->
                        val bytes = slice?.let { data.copyOfRange(it.getValue("offset").jsonPrimitive.int, it.getValue("offset").jsonPrimitive.int + it.getValue("length").jsonPrimitive.int) } ?: data
                        """{"pubkey":"${key.base58()}","account":{"data":["${Base64.getEncoder().encodeToString(bytes)}","base64"],"executable":false,"lamports":1,"owner":"${programId.base58()}","rentEpoch":0}}"""
                    }
            return ok("[${entries.joinToString(",")}]")
        }

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = Json.parseToJsonElement(request.body ?: "{}").jsonObject
            val params = body["params"]?.jsonArray
            fun tx() = Transaction.from(Base64.getDecoder().decode(params!![0].jsonPrimitive.content))
            return when (body.getValue("method").jsonPrimitive.content) {
                "getAccountInfo" -> accountInfo(accounts[SolanaPublicKey.from(params!![0].jsonPrimitive.content)])
                "getProgramAccounts" -> programAccounts(params!!)
                "getSlot" -> ok("100")
                "getBlockTime" -> ok("$now")
                "getBalance" -> ok("""{"context":{"slot":1},"value":$sponsorLamports}""")
                "getLatestBlockhash" -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""")
                "simulateTransaction" -> {
                    val err = refuseSimulation[nameOf(tx())]?.let { """{"InstructionError":[0,{"Custom":$it}]}""" } ?: "null"
                    ok("""{"context":{"slot":1},"value":{"err":$err,"logs":[]}}""")
                }
                "sendTransaction" -> {
                    val t = tx()
                    val name = nameOf(t)
                    refuse[name]?.let { return """{"jsonrpc":"2.0","error":{"code":-32002,"message":"$it"},"id":"x"}""" }
                    sent += name
                    sentTx += t
                    apply(t)
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

    private val env = mapOf("HOLDOUT_RPC_URL" to "http://rpc.test", "HOLDOUT_PROGRAM_ID" to programId.base58(), "HOLDOUT_PAYER_KEYPAIR" to "/keys/payer.json")

    /**
     * League 0 two and a half weeks in: cohort 0 settled, cohort 1 just ended
     * with two uncounted members, cohort 2 on its second day with one member
     * who missed day 0, and cohort 3 due to open because cohort 2 has started.
     */
    private fun liveLeague(sponsorLamports: Long, refuse: Map<String, String> = emptyMap(), refuseSimulation: Map<String, Int> = emptyMap()): Chain = runBlocking {
        val now = first + 2 * week + day + day / 2
        val chain = Chain(now, sponsorLamports, refuse, refuseSimulation)
        chain.accounts[Pdas.config(programId)] = configBytes(sponsorFloat = 50_000_000)
        chain.accounts[Pdas.league(programId, 0)] = leagueBytes(league0)
        val c1 = Pdas.cohort(programId, 0, 1)
        val c2 = Pdas.cohort(programId, 0, 2)
        chain.accounts[Pdas.cohort(programId, 0, 0)] = cohortBytes(cohort(0, settled = true))
        chain.accounts[c1] = cohortBytes(cohort(1, memberCount = 2))
        chain.accounts[c2] = cohortBytes(cohort(2, memberCount = 2))
        listOf(member(1, 7, MembershipSnapshot.State.Finished), member(2, 3, MembershipSnapshot.State.Forfeited)).forEach {
            chain.accounts[Pdas.membership(programId, c1, it.wallet)] = membershipBytes(c1, it, counted = false)
        }
        listOf(member(3, 1), member(4, 0)).forEach {
            chain.accounts[Pdas.membership(programId, c2, it.wallet)] = membershipBytes(c2, it, counted = false)
        }
        chain
    }

    private fun pass(chain: Chain, vararg args: String): JsonObject = runBlocking {
        Run.run(parseArgs(listOf("run") + args), CrankConfig.from(env), Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)), loadKeypair = { _, _ -> payer }, pause = {})
    }

    @Test
    fun `a pass opens, refills, forfeits and settles, in that order`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 10_000_000)
        val json = pass(chain)
        assertEquals(listOf("open_cohort", "top_up_sponsor", "record_forfeits", "settle_count", "settle_finalize"), chain.sent)
        assertEquals(0, json.getValue("failed").jsonPrimitive.int)

        val open = chain.sentTx[0]
        assertTrue(open.message.accounts.contains(Pdas.cohort(programId, 0, 3)), "cohort 3 opens")
        val forfeit = chain.sentTx[2]
        assertTrue(forfeit.message.accounts.contains(Pdas.membership(programId, Pdas.cohort(programId, 0, 2), member(4, 0).wallet)), "the member who missed day 0")
        assertFalse(forfeit.message.accounts.contains(Pdas.membership(programId, Pdas.cohort(programId, 0, 2), member(3, 1).wallet)), "not the member still standing")
        val finalize = chain.sentTx[4]
        assertFalse(finalize.message.accounts.contains(Pdas.cohort(programId, 0, 3)), "settlement names no other cohort: nothing carries over")

        // H35: every step that moves fee money names both destinations, and finalize the league's reserve vault.
        fun accountsOf(t: Transaction) = t.message.instructions.single().accountIndices.map { t.message.accounts[it.toInt()] }
        assertEquals(listOf(feeDestination, developerDestination), accountsOf(forfeit).subList(4, 6), "record_forfeits: operations, then developer")
        assertEquals(listOf(feeDestination, developerDestination), accountsOf(chain.sentTx[3]).subList(5, 7), "settle_count: operations, then developer")
        assertEquals(
            listOf(Pdas.league(programId, 0), Pdas.cohort(programId, 0, 1)),
            accountsOf(finalize).subList(2, 4),
        )
        assertEquals(listOf(feeDestination, developerDestination, Pdas.reserveVault(programId, 0)), accountsOf(finalize).subList(5, 8), "settle_finalize: both destinations, then the reserve vault")
        assertTrue(writableIn(finalize, Pdas.league(programId, 0)), "finalize writes the league's reserves")
        chain.sentTx.forEach { assertEquals(payer.publicKey, it.message.accounts[0], "the crank's payer pays every fee") }
    }

    private fun writableIn(t: Transaction, key: SolanaPublicKey): Boolean = holdout.core.Transactions.isWritable(t.message, t.message.accounts.indexOf(key))

    @Test
    fun `a sponsor above half its float is left alone`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 30_000_000)
        pass(chain)
        assertFalse("top_up_sponsor" in chain.sent)
    }

    @Test
    fun `a refill the program refuses is an expected outcome, not a failure`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 1, refuse = mapOf("top_up_sponsor" to "Transaction simulation failed: custom program error: 0x17a3"))
        val json = pass(chain)
        assertEquals(0, json.getValue("failed").jsonPrimitive.int)
        val refill = json.getValue("steps").jsonArray.map { it.jsonObject }.single { it.getValue("step").jsonPrimitive.content == "top_up_sponsor" }
        assertEquals("refused", refill.getValue("status").jsonPrimitive.content)
        assertEquals(listOf("open_cohort", "record_forfeits", "settle_count", "settle_finalize"), chain.sent, "the rest of the pass still runs")
    }

    @Test
    fun `a failed step is reported, the pass carries on, and the exit is 2`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 30_000_000, refuse = mapOf("record_forfeits" to "Transaction simulation failed: blockhash not found"))
        val e = assertFailsWith<CrankSummaryException> { pass(chain) }
        assertEquals(EXIT_FAILED, e.exitCode)
        assertEquals(1, e.summary.getValue("failed").jsonPrimitive.int)
        assertEquals(listOf("open_cohort", "settle_count", "settle_finalize"), chain.sent, "settlement does not depend on the forfeit page")
    }

    @Test
    fun `a cohort waits while the one before it is unsettled`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 30_000_000)
        // Ended and unsettled, with one member the chain does not hold: its count cannot complete.
        chain.accounts[Pdas.cohort(programId, 0, 0)] = cohortBytes(cohort(0, memberCount = 1))
        val e = assertFailsWith<CrankSummaryException> { pass(chain) }
        assertEquals(1, e.summary.getValue("failed").jsonPrimitive.int)
        assertFalse("settle_count" in chain.sent, "cohort 1 is never counted ahead of cohort 0 (FR-061a)")
        val settleSteps = e.summary.getValue("steps").jsonArray.map { it.jsonObject }.filter { it.getValue("step").jsonPrimitive.content.startsWith("settle") }
        assertTrue(settleSteps.none { it["index"]?.jsonPrimitive?.int == 1 })
    }

    /** Test leagues 100 and 101, each with one ended cohort; league 101's ended first. */
    private fun twoTestLeagues(earlierMembers: Long): Chain = runBlocking {
        val now = first + 3_600
        val chain = Chain(now, sponsorLamports = 30_000_000)
        chain.accounts[Pdas.config(programId)] = configBytes(sponsorFloat = 50_000_000)
        listOf(100 to first + 600, 101 to first).forEach { (id, start) ->
            chain.accounts[Pdas.league(programId, id)] = leagueBytes(LeagueSnapshot(LeagueParams(id, 3, 5, 0, LeagueParams.FORM_KNEES_ALLOWED, start, 600, 100_000, 300), nextIndex = 1))
        }
        chain.accounts[Pdas.cohort(programId, 100, 0)] = cohortBytes(testCohort(100, 0, start = first + 600))
        chain.accounts[Pdas.cohort(programId, 101, 0)] = cohortBytes(testCohort(101, 0, start = first, memberCount = earlierMembers))
        chain
    }

    private fun finalized(chain: Chain): List<SolanaPublicKey> = chain.sentTx
        .filter { chain.nameOf(it) == "settle_finalize" }
        .map { t -> t.message.accounts[t.message.instructions.single().accountIndices[3].toInt()] }

    @Test
    fun `test leagues settle in the order their cohorts ended, not by league id`() = runBlocking {
        val chain = twoTestLeagues(earlierMembers = 0)
        pass(chain)
        assertEquals(listOf(Pdas.cohort(programId, 101, 0), Pdas.cohort(programId, 100, 0)), finalized(chain), "the combined test board's streaks follow the cohorts in time (FR-060)")
    }

    @Test
    fun `a test cohort that cannot settle holds later cohorts of every test league`() = runBlocking {
        // League 101's cohort holds a member the chain does not: its count cannot complete.
        val chain = twoTestLeagues(earlierMembers = 1)
        val e = assertFailsWith<CrankSummaryException> { pass(chain) }
        assertEquals(emptyList(), finalized(chain), "league 100's later cohort is not settled ahead of it")
        val waiting = e.summary.getValue("steps").jsonArray.map { it.jsonObject }.single { it.getValue("step").jsonPrimitive.content == "settle" }
        assertEquals(100, waiting.getValue("league").jsonPrimitive.int)
        assertEquals("waiting", waiting.getValue("status").jsonPrimitive.content)
        assertTrue(waiting.getValue("reason").jsonPrimitive.content.contains("test league 101"))
    }

    @Test
    fun `only the newest test league opens cohorts - an older one winds down`() {
        assertEquals(setOf(100, 101), Run.retiredTestLeagues(listOf(0, 1, 100, 101, 102)))
        assertEquals(emptySet<Int>(), Run.retiredTestLeagues(listOf(0, 1, 100)), "one test league is the lobby")
        assertEquals(emptySet<Int>(), Run.retiredTestLeagues(listOf(0, 1)), "the judged leagues are never retired")
    }

    @Test
    fun `the new test lobby waits until every cohort of an older test league has settled`() {
        val old = testCohort(100, 0, start = first)
        assertEquals(100, Run.lobbyHeldBy(setOf(100), listOf(old, testCohort(101, 0, start = first + 600))), "an older cohort still unsettled holds the lobby")
        assertEquals(null, Run.lobbyHeldBy(setOf(100), listOf(old.copy(settled = true))), "all settled: the lobby opens")
        assertEquals(null, Run.lobbyHeldBy(emptySet(), listOf(old)), "no older test league, nothing to wait for")
    }

    @Test
    fun `a pass holds the newest test league while the older one winds down, and still settles the older one`() = runBlocking {
        val chain = twoTestLeagues(earlierMembers = 0)
        val json = pass(chain)
        assertTrue(chain.sentTx.none { chain.nameOf(it) == "open_cohort" }, "league 101 opens nothing while league 100 has an unsettled cohort; league 100 opens nothing more")
        val steps = json.getValue("steps").jsonArray.map { it.jsonObject }.filter { it.getValue("step").jsonPrimitive.content == "open_cohort" }
        assertEquals("retired", steps.single { it["league"]?.jsonPrimitive?.int == 100 }.getValue("status").jsonPrimitive.content)
        val held = steps.single { it["league"]?.jsonPrimitive?.int == 101 }
        assertEquals("waiting", held.getValue("status").jsonPrimitive.content)
        assertTrue(held.getValue("reason").jsonPrimitive.content.contains("test league 100"))
        assertEquals(listOf(Pdas.cohort(programId, 101, 0), Pdas.cohort(programId, 100, 0)), finalized(chain), "its cohorts still settle (FR-044a)")
    }

    @Test
    fun `once the older test league has settled, the newest one opens its next cohort`() = runBlocking {
        val chain = twoTestLeagues(earlierMembers = 0)
        chain.accounts[Pdas.cohort(programId, 100, 0)] = cohortBytes(testCohort(100, 0, start = first + 600).copy(settled = true))
        pass(chain)
        val opens = chain.sentTx.filter { chain.nameOf(it) == "open_cohort" }.map { t -> t.message.accounts }
        assertTrue(opens.isNotEmpty() && opens.all { Pdas.cohort(programId, 101, 1) in it }, "league 101 opens its next cohort")
        assertTrue(opens.none { Pdas.cohort(programId, 100, 1) in it }, "league 100 stays retired")
    }

    @Test
    fun `a dry run simulates and sends nothing`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 10_000_000)
        val json = pass(chain, "--dry-run")
        assertEquals(emptyList(), chain.sent)
        val statuses = json.getValue("steps").jsonArray.map { it.jsonObject.getValue("status").jsonPrimitive.content }.toSet()
        assertTrue(statuses.all { it == "simulated" || it == "skipped" }, "$statuses")
    }

    @Test
    fun `a dry run behind by several cohorts simulates the first open and reports the rest as planned`() = runBlocking {
        val chain = Chain(first + 2 * week + day, 50_000_000)
        chain.accounts[Pdas.config(programId)] = configBytes(sponsorFloat = 50_000_000)
        chain.accounts[Pdas.league(programId, 0)] = leagueBytes(league0.copy(nextIndex = 1))
        chain.accounts[Pdas.cohort(programId, 0, 0)] = cohortBytes(cohort(0, settled = true))
        val json = pass(chain, "--dry-run")
        val opens = json.getValue("steps").jsonArray.map { it.jsonObject }.filter { it.getValue("step").jsonPrimitive.content == "open_cohort" && it["league"]?.jsonPrimitive?.int == 0 }
        assertEquals(listOf(1 to "simulated", 2 to "skipped", 3 to "skipped"), opens.map { it.getValue("index").jsonPrimitive.int to it.getValue("status").jsonPrimitive.content })
        assertEquals(0, json.getValue("failed").jsonPrimitive.int)
    }

    @Test
    fun `a dry run that reaches a counted cohort simulates its finalize and fails nothing`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 50_000_000)
        // Cohort 1 already counted, so the dry run reaches its finalize.
        val c1 = Pdas.cohort(programId, 0, 1)
        chain.accounts[c1] = cohortBytes(cohort(1, memberCount = 2, counted = 2))
        listOf(member(1, 7, MembershipSnapshot.State.Finished), member(2, 3, MembershipSnapshot.State.Forfeited)).forEach {
            chain.accounts[Pdas.membership(programId, c1, it.wallet)] = membershipBytes(c1, it, counted = true)
        }
        val json = pass(chain, "--dry-run")
        assertEquals(0, json.getValue("failed").jsonPrimitive.int)
        val finalize = json.getValue("steps").jsonArray.map { it.jsonObject }.filter { it.getValue("step").jsonPrimitive.content == "settle_finalize" }
        assertEquals(listOf("simulated"), finalize.map { it.getValue("status").jsonPrimitive.content })
    }

    @Test
    fun `an uninitialized program is a configuration error`() = runBlocking {
        val chain = Chain(first, 0)
        val e = assertFailsWith<CrankException> { pass(chain) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(Run.sponsorRefillDue(ConfigSnapshot.decode(configBytes(50_000_000)), 24_999_999))
    }
}
