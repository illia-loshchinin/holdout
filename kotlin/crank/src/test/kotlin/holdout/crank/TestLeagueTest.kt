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
            b.put(0, l.DISCRIMINATOR).put(l.ADMIN, admin.publicKey.bytes).putInt(l.CHIP_VERSION, 1)
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
    fun `by default the test league is a 7-day Holdout in 7 hours - seven 1-hour days at 5 reps, back to back, as league 100`() {
        val p = Init.testLeagueParams(parseArgs(listOf("init", "--test-league") + quickstart))
        assertEquals(LeagueParams(100, 7, 5, 0, Instant.parse(start).epochSecond, 3_600, 25_200, 300), p)
        val quick = Init.testLeagueParams(parseArgs(listOf("init", "--test-league", "--day-seconds", "600", "--reps", "10", "--days", "3") + quickstart))
        assertEquals(listOf(600L, 10L, 3L, 1_800L), listOf(quick.dayLength, quick.repsPerDay.toLong(), quick.durationDays.toLong(), quick.cadence), "every default can be overridden")
        val custom = Init.testLeagueParams(parseArgs(listOf("init", "--test-league", "--league-id", "101", "--cadence", "7200", "--join-lock", "60") + quickstart))
        assertEquals(listOf(101L, 7_200L, 60L), listOf(custom.leagueId.toLong(), custom.cadence, custom.joinLock))
    }

    @Test
    fun `timing the program would take but no tester could live with is refused before sending`() {
        fun refused(vararg args: String) = assertFailsWith<CrankException> { Init.testLeagueParams(parseArgs(listOf("init", "--test-league") + args)) }.exitCode
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "600", "--reps", "5", "--days", "7"), "no first start")
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "10", "--reps", "5", "--days", "7", "--first-start", start), "a 10-second day")
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "600", "--reps", "0", "--days", "7", "--first-start", start), "zero reps")
        assertEquals(EXIT_CONFIG, refused("--day-seconds", "600", "--reps", "5", "--days", "0", "--first-start", start), "zero days")
        assertEquals(EXIT_CONFIG, refused("--league-id", "1", *quickstart), "a judged league id")
    }

    @Test
    fun `the admin creates it with create_league, signing and paying`() {
        val chain = Chain()
        val json = testLeague(chain, *quickstart)
        assertEquals("sent", json.getValue("status").jsonPrimitive.content)
        assertEquals(100, json.getValue("league").jsonPrimitive.int)
        assertEquals(3_600, json.getValue("daySeconds").jsonPrimitive.long)
        val tx = chain.sent.single()
        assertEquals(admin.publicKey, tx.message.accounts[0])
        val ix = tx.message.instructions.single()
        assertContentEquals(Instructions.discriminator("create_league"), ix.data.copyOf(8))
        assertEquals(100, ix.data[8].toInt())
    }

    @Test
    fun `a league that exists is left alone`() {
        val chain = Chain()
        chain.league = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0, holdout.core.LeagueSnapshot.DISCRIMINATOR).put(8, 100.toByte()).put(9, 7.toByte()).putShort(10, 5).putLong(13, 600).putLong(21, now + 3_600).putLong(29, 4_200).putLong(37, 300).putInt(45, 1)
        }.array()
        val json = testLeague(chain, *quickstart)
        assertEquals("skipped", json.getValue("status").jsonPrimitive.content)
        assertEquals(1, json.getValue("nextIndex").jsonPrimitive.long)
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
