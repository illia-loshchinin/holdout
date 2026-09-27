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
import kotlin.test.assertNull
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

    @Test
    fun `settlement is queued per league, oldest first`() {
        val now = first + 3 * week + 1
        val queue = Run.settleQueue(listOf(cohort(2), cohort(0, settled = true), cohort(1), cohort(3)), now)
        assertEquals(listOf(1L, 2L), queue.getValue(0).map { it.index }, "settled and running cohorts are not queued")
    }

    @Test
    fun `the carry-over target is the league's newest cohort, never the cohort itself`() {
        assertEquals(4L, Run.carryOverTargetIndex(nextIndex = 5, cohortIndex = 2))
        assertNull(Run.carryOverTargetIndex(nextIndex = 3, cohortIndex = 2))
        assertNull(Run.carryOverTargetIndex(nextIndex = 0, cohortIndex = 0))
    }

    // ---- one whole pass ----------------------------------------------------

    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun configBytes(sponsorFloat: Long): ByteArray {
        val l = AccountLayout.Config
        val b = le(l.SIZE)
        b.put(0, l.DISCRIMINATOR)
        b.put(l.SPONSOR, sponsor.bytes).put(l.SKR_MINT, mint.bytes).put(l.FEE_DESTINATION, feeDestination.bytes)
        b.putInt(l.CHIP_VERSION, 1).putLong(l.SPONSOR_FLOAT, sponsorFloat).putLong(l.DAILY_LAMPORT_CAP, 720_000_000)
        return b.array()
    }

    private fun leagueBytes(l: LeagueSnapshot): ByteArray {
        val b = le(LeagueSnapshot.SIZE)
        b.put(0, LeagueSnapshot.DISCRIMINATOR)
        b.put(8, l.params.leagueId.toByte()).put(9, l.params.durationDays.toByte()).putShort(10, l.params.repsPerDay.toShort()).put(12, l.params.exercise.toByte())
        b.putLong(13, l.params.dayLength).putLong(21, l.params.firstStart).putLong(29, l.params.cadence).putLong(37, l.params.joinLock).putInt(45, l.nextIndex.toInt())
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
                "simulateTransaction" -> ok("""{"context":{"slot":1},"value":{"err":null,"logs":[]}}""")
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
    private fun liveLeague(sponsorLamports: Long, refuse: Map<String, String> = emptyMap()): Chain = runBlocking {
        val now = first + 2 * week + day + day / 2
        val chain = Chain(now, sponsorLamports, refuse)
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
        assertTrue(finalize.message.accounts.contains(Pdas.cohort(programId, 0, 3)), "the carry-over target is the lobby this pass opened")
        chain.sentTx.forEach { assertEquals(payer.publicKey, it.message.accounts[0], "the crank's payer pays every fee") }
    }

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

    @Test
    fun `a dry run simulates and sends nothing`() = runBlocking {
        val chain = liveLeague(sponsorLamports = 10_000_000)
        val json = pass(chain, "--dry-run")
        assertEquals(emptyList(), chain.sent)
        val statuses = json.getValue("steps").jsonArray.map { it.jsonObject.getValue("status").jsonPrimitive.content }.toSet()
        assertTrue(statuses.all { it == "simulated" || it == "skipped" }, "$statuses")
    }

    @Test
    fun `an uninitialized program is a configuration error`() = runBlocking {
        val chain = Chain(first, 0)
        val e = assertFailsWith<CrankException> { pass(chain) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(Run.sponsorRefillDue(ConfigSnapshot.decode(configBytes(50_000_000)), 24_999_999))
    }
}
