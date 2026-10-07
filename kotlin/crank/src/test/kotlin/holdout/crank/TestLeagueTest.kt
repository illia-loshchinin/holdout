package holdout.crank

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import holdout.core.AccountLayout
import holdout.core.Instructions
import holdout.core.LeagueParams
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T091a — `crank init --test-league`: the one disclosed short test cohort's
 * league (FR-044a, quickstart §5), admin-only, idempotent, and refused in so
 * many words by a program built without `test-leagues`.
 */
class TestLeagueTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val admin = Keypair.fromSeed(ByteArray(32) { 1 })
    private val stranger = Keypair.fromSeed(ByteArray(32) { 2 })
    private val mint = SolanaPublicKey(ByteArray(32) { 9 })
    private val now = Instant.parse("2026-09-27T12:00:00Z").epochSecond
    private val start = "2026-09-27T15:00:00Z"
    private val env = mapOf("HOLDOUT_RPC_URL" to "http://rpc.test", "HOLDOUT_PROGRAM_ID" to programId.base58(), "HOLDOUT_ADMIN_KEYPAIR" to "/keys/admin.json")

    /** The quickstart's command: the first start alone, everything else the default. */
    private val quickstart = arrayOf("--first-start", start)

    private inner class Chain(val initialized: Boolean = true, val refusal: String? = null) : HttpNetworkDriver {
        val sent = mutableListOf<Transaction>()
        var league: ByteArray? = null

        private fun configBytes(): ByteArray {
            val l = AccountLayout.Config
            val b = ByteBuffer.allocate(l.SIZE).order(ByteOrder.LITTLE_ENDIAN)
            b.put(0, l.DISCRIMINATOR).put(l.ADMIN, admin.publicKey.bytes).put(l.SKR_MINT, mint.bytes).putInt(l.CHIP_VERSION, 1)
            return b.array()
        }

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = Json.parseToJsonElement(request.body ?: "{}").jsonObject
            val params = body["params"]?.jsonArray
            return when (body.getValue("method").jsonPrimitive.content) {
                "getAccountInfo" ->
                    accountInfo(
                        when (params!![0].jsonPrimitive.content) {
                            runBlocking { Pdas.config(programId) }.base58() -> if (initialized) configBytes() else null
                            else -> league
                        },
                    )
                "getSlot" -> ok("100")
                "getBlockTime" -> ok("$now")
                "getLatestBlockhash" -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""")
                "simulateTransaction" -> ok("""{"context":{"slot":1},"value":{"err":null,"logs":[]}}""")
                "sendTransaction" -> {
                    refusal?.let { return """{"jsonrpc":"2.0","error":{"code":-32002,"message":"$it"},"id":"x"}""" }
                    sent += Transaction.from(Base64.getDecoder().decode(params!![0].jsonPrimitive.content))
                    ok("\"sig${sent.size}\"")
                }
                "getSignatureStatuses" -> ok("""{"context":{"slot":1},"value":[{"err":null,"confirmationStatus":"confirmed"}]}""")
                else -> error("unexpected method")
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

    private fun testLeague(chain: Chain, vararg args: String, key: Keypair = admin): JsonObject = runBlocking {
        Init.runTestLeague(parseArgs(listOf("init", "--test-league") + args), CrankConfig.from(env), Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)), loadKeypair = { _, _ -> key }, pause = {})
    }

    @Test
    fun `by default the test league is the test lobby - three 10-minute days at 5 reps, a new cohort every 30 minutes, back to back, as league 100`() {
        val p = Init.testLeagueParams(parseArgs(listOf("init", "--test-league") + quickstart))
        assertEquals(LeagueParams(100, 3, 5, 0, LeagueParams.FORM_KNEES_ALLOWED, Instant.parse(start).epochSecond, 600, 1_800, 300), p, "three 10-minute days, a cohort every 30 minutes, Easy by default (FR-044a, H35, H36)")
        val quick = Init.testLeagueParams(parseArgs(listOf("init", "--test-league", "--day-seconds", "3600", "--reps", "10", "--days", "3") + quickstart))
        assertEquals(listOf(3_600L, 10L, 3L, 10_800L), listOf(quick.dayLength, quick.repsPerDay.toLong(), quick.durationDays.toLong(), quick.cadence), "every default can be overridden")
        val custom = Init.testLeagueParams(parseArgs(listOf("init", "--test-league", "--league-id", "101", "--cadence", "600", "--join-lock", "60") + quickstart))
        assertEquals(listOf(101L, 600L, 60L), listOf(custom.leagueId.toLong(), custom.cadence, custom.joinLock), "a cohort every 10 minutes, overlapping")
    }

    @Test
    fun `timing the program would take but no tester could live with is refused before sending`() {
        fun refused(vararg args: String) = assertFailsWith<CrankException> { Init.testLeagueParams(parseArgs(listOf("init", "--test-league") + args)) }.exitCode
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "600", "--reps", "5", "--days", "7"), "no first start")
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "10", "--reps", "5", "--days", "7", "--first-start", start), "a 10-second day")
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "600", "--reps", "0", "--days", "7", "--first-start", start), "zero reps")
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "600", "--reps", "5", "--days", "0", "--first-start", start), "zero days")
        assertEquals(EXIT_CONFIG, refused("--league-id", "1", *quickstart), "a judged league id")
        assertEquals(EXIT_CONFIG, refused("--form", "2", *quickstart), "a form the program refuses (6013)")
    }

    @Test
    fun `given --form hard, when the test league is planned, then it judges from the toes`() {
        fun form(vararg args: String) = Init.testLeagueParams(parseArgs(listOf("init", "--test-league", *args) + quickstart)).form
        assertEquals(LeagueParams.FORM_TOES, form("--form", "hard"))
        assertEquals(LeagueParams.FORM_TOES, form("--form", "0"))
        assertEquals(LeagueParams.FORM_KNEES_ALLOWED, form("--form", "Easy"))
        assertEquals(LeagueParams.FORM_KNEES_ALLOWED, form())
    }

    @Test
    fun `the admin creates it with create_league, signing and paying`() {
        val chain = Chain()
        val json = testLeague(chain, *quickstart)
        assertEquals("sent", json.getValue("status").jsonPrimitive.content)
        assertEquals(100, json.getValue("league").jsonPrimitive.int)
        assertEquals(600, json.getValue("daySeconds").jsonPrimitive.long)
        val tx = chain.sent.single()
        assertEquals(admin.publicKey, tx.message.accounts[0])
        val ix = tx.message.instructions.single()
        assertContentEquals(Instructions.discriminator("create_league"), ix.data.copyOf(8))
        assertEquals(100, ix.data[8].toInt())
        assertEquals(LeagueParams.FORM_KNEES_ALLOWED, ix.data[13].toInt(), "the form follows the exercise")
        assertEquals("Easy", json.getValue("formName").jsonPrimitive.content)
        // The reserve vault is created with the league, holding Config's SKR mint.
        val accounts = ix.accountIndices.map { tx.message.accounts[it.toInt()] }
        assertEquals(listOf(mint, runBlocking { Pdas.reserveVault(programId, 100) }), accounts.subList(3, 5))
    }

    /** League 100 as stored on chain: [days] days of 600 s at 5 reps, a cohort every [cadence] s, Easy. */
    private fun existingLeague(days: Int, cadence: Long) = ByteBuffer.allocate(holdout.core.LeagueSnapshot.SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
        put(0, holdout.core.LeagueSnapshot.DISCRIMINATOR).put(8, 100.toByte()).put(9, days.toByte()).putShort(10, 5).putLong(13, 600).putLong(21, now + 3_600).putLong(29, cadence).putLong(37, 300).putInt(45, 1)
            .put(holdout.core.LeagueSnapshot.FORM, LeagueParams.FORM_KNEES_ALLOWED.toByte())
    }.array()

    @Test
    fun `a league that exists with the same shape is left alone`() {
        val chain = Chain()
        chain.league = existingLeague(days = 3, cadence = 1_800)
        val json = testLeague(chain, *quickstart)
        assertEquals("skipped", json.getValue("status").jsonPrimitive.content)
        assertEquals(1, json.getValue("nextIndex").jsonPrimitive.long)
        assertTrue(chain.sent.isEmpty())
    }

    @Test
    fun `a league that exists with another shape is refused, naming --league-id, not passed off as done`() {
        val chain = Chain()
        chain.league = existingLeague(days = 7, cadence = 4_200) // the earlier seven-day test league
        val e = assertFailsWith<CrankException> { testLeague(chain, *quickstart) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("--league-id"), e.message)
        assertTrue(chain.sent.isEmpty())
    }

    @Test
    fun `only the admin, only after init, and only while joining can still open`() {
        assertEquals(EXIT_CONFIG, assertFailsWith<CrankException> { testLeague(Chain(), *quickstart, key = stranger) }.exitCode)
        assertEquals(EXIT_CONFIG, assertFailsWith<CrankException> { testLeague(Chain(initialized = false), *quickstart) }.exitCode)
        val past = arrayOf("--day-seconds", "600", "--reps", "5", "--days", "7", "--first-start", "2026-09-27T12:02:00Z")
        assertEquals(EXIT_CONFIG, assertFailsWith<CrankException> { testLeague(Chain(), *past) }.exitCode, "joining would already be closed")
    }

    @Test
    fun `a program built without test-leagues is named as the reason`() {
        val chain = Chain(refusal = "Transaction simulation failed: Error processing Instruction 0: custom program error: 0x177a")
        val e = assertFailsWith<CrankException> { testLeague(chain, *quickstart) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("test-leagues"), e.message)
    }
}
