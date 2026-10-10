package holdout.crank

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import holdout.core.AccountLayout
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T100, T116 — `crank chips publish --auto`, the Operator's weekly re-pricing
 * (FR-056, FR-057, AC-12, research R15): the rate from Jupiter only, every
 * week with no threshold, at most one publish per publish window — the 4
 * hours before Monday 05:00 UTC, so the cohort opening then locks a fresh rate.
 */
class ChipsAutoTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val operator = Keypair.fromSeed(ByteArray(32) { 3 })
    private val mint = SolanaPublicKey(ByteArray(32) { 9 })
    private val configAddress = runBlocking { Pdas.config(programId) }

    /** Wednesday 7 October 2026, 12:00 UTC; the latest boundary is Monday 5 October, 05:00 UTC. */
    private val wednesday = Instant.parse("2026-10-07T12:00:00Z").epochSecond
    private val monday = Instant.parse("2026-10-05T05:00:00Z").epochSecond

    /** The next boundary, Monday 12 October 05:00 UTC, and its publish window from 01:00. */
    private val nextMonday = Instant.parse("2026-10-12T05:00:00Z").epochSecond
    private val windowOpens = Instant.parse("2026-10-12T01:00:00Z").epochSecond

    /** The first hourly run inside the window (a cron at :17). */
    private val inWindow = Instant.parse("2026-10-12T01:17:00Z").epochSecond

    /** Last week's set, published in last week's window. */
    private val lastWeek = Instant.parse("2026-10-05T01:17:00Z").epochSecond

    private val env =
        mapOf(
            "HOLDOUT_RPC_URL" to "http://rpc.test",
            "HOLDOUT_PROGRAM_ID" to programId.base58(),
            "HOLDOUT_OPERATOR_KEYPAIR" to "/keys/operator.json",
        )

    /** The launch set at 0.0213 USD/SKR: the $100 chip is 4,690 SKR. */
    private val launchSet = listOf(460_000_000uL, 1_170_000_000uL, 2_340_000_000uL, 4_690_000_000uL)

    private fun configBytes(
        amounts: List<ULong> = launchSet,
        publishedAt: Long = lastWeek,
        configOperator: SolanaPublicKey = operator.publicKey,
        version: Int = 1,
    ): ByteArray {
        val l = AccountLayout.Config
        val b = ByteArray(l.SIZE)
        l.DISCRIMINATOR.copyInto(b)
        configOperator.bytes.copyInto(b, l.OPERATOR)
        mint.bytes.copyInto(b, l.SKR_MINT)
        val w = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        amounts.forEachIndexed { i, a -> w.putLong(l.CHIP_AMOUNTS + 8 * i, a.toLong()) }
        w.putInt(l.CHIP_VERSION, version)
        w.putLong(l.CHIP_PUBLISHED_AT, publishedAt)
        return b
    }

    private class Cluster(val replies: Map<String, (JsonObject) -> String>) : HttpNetworkDriver {
        val calls = mutableListOf<JsonObject>()

        /** The chain's time; a test moves it between runs. */
        var now = 0L

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = Json.parseToJsonElement(request.body ?: "{}").jsonObject
            calls += body
            val method = body.getValue("method").jsonPrimitive.content
            return (replies[method] ?: error("unexpected $method"))(body)
        }

        fun sent(method: String): List<Transaction> = calls
            .filter { it.getValue("method").jsonPrimitive.content == method }
            .map { Transaction.from(Base64.getDecoder().decode(it.getValue("params").jsonArray[0].jsonPrimitive.content)) }
    }

    private fun ok(result: String) = """{"jsonrpc":"2.0","result":$result,"id":"x"}"""

    private fun accountInfo(data: ByteArray) = ok(
        """{"context":{"slot":1},"value":{"data":["${Base64.getEncoder().encodeToString(data)}","base64"],"executable":false,"lamports":1,"owner":"11111111111111111111111111111111","rentEpoch":0}}""",
    )

    private fun cluster(config: ByteArray = configBytes(), now: Long = inWindow): Cluster {
        var configNow = config
        lateinit var cluster: Cluster
        cluster = Cluster(
            mapOf(
                "getAccountInfo" to { body ->
                    when (body.getValue("params").jsonArray[0].jsonPrimitive.content) {
                        configAddress.base58() -> accountInfo(configNow)
                        mint.base58() -> accountInfo(ByteArray(82).also { it[44] = 6 })
                        else -> ok("""{"context":{"slot":1},"value":null}""")
                    }
                },
                "getSlot" to { _ -> ok("777") },
                "getBlockTime" to { _ -> ok("${cluster.now}") },
                "getLatestBlockhash" to { _ -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""") },
                "simulateTransaction" to { _ -> ok("""{"context":{"slot":1},"value":{"err":null,"logs":[]}}""") },
                "sendTransaction" to { _ -> ok("\"5sig\"").also { configNow = configBytes(version = 2, publishedAt = cluster.now) } },
                "getSignatureStatuses" to { _ -> ok("""{"context":{"slot":1},"value":[{"err":null,"confirmationStatus":"confirmed"}]}""") },
            ),
        )
        cluster.now = now
        return cluster
    }

    /** A rate source that answers [rate] and counts how often it was asked. */
    private class FixedRate(val rate: BigDecimal?) : Chips.RateSource {
        var asked = 0

        override suspend fun usdPerSkr(): BigDecimal? {
            asked++
            return rate
        }
    }

    private fun auto(cluster: Cluster, rate: Chips.RateSource, vararg args: String, env: Map<String, String> = this.env): JsonObject = runBlocking {
        Chips.publish(
            parseArgs(listOf("chips", "publish", "--auto") + args),
            CrankConfig.from(env),
            Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", cluster)),
            loadKeypair = { _, _ -> operator },
            pause = { },
            rateSource = { rate },
        )
    }

    private fun amountsIn(tx: Transaction): List<ULong> {
        val b = ByteBuffer.wrap(tx.message.instructions.single().data, 8, 32).order(ByteOrder.LITTLE_ENDIAN)
        return List(4) { b.getLong().toULong() }
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    // ---- the weekly re-price ---------------------------------------------------------

    @Test
    fun `given the week is open and a rate, when run, then all four tiers are re-priced at it and published, however small the move`() {
        val c = cluster()
        val json = auto(c, FixedRate(BigDecimal("0.024"))) // the $100 chip would be worth 112.56: well under the old 20 %
        assertEquals("true", json.text("published"))
        assertEquals(2, json.getValue("version").jsonPrimitive.long)
        assertEquals(Chips.price(BigDecimal("0.024"), 6).map { it.baseUnits }, amountsIn(c.sent("sendTransaction").single()))
    }

    @Test
    fun `given a rate that rounds to the current amounts, when run, then a new version is published anyway`() {
        val c = cluster()
        val json = auto(c, FixedRate(BigDecimal("0.0213"))) // prices exactly the launch set
        assertEquals("true", json.text("published"))
        assertEquals(launchSet, amountsIn(c.sent("sendTransaction").single()))
    }

    // ---- FR-057: no rate --------------------------------------------------------------

    @Test
    fun `given no rate, when checked, then it exits cleanly without publishing and the current set stays`() {
        val c = cluster()
        val json = auto(c, FixedRate(null))
        assertEquals("false", json.text("published"))
        assertEquals("no rate", json.text("reason"))
        assertTrue(c.sent("simulateTransaction").isEmpty() && c.sent("sendTransaction").isEmpty())
    }

    @Test
    fun `given a rate the tiers cannot be priced at, when run, then it exits cleanly without publishing and says why`() {
        val c = cluster()
        val json = auto(c, FixedRate(BigDecimal("2"))) // $10 and $25 both floor to the 10-SKR minimum
        assertEquals("false", json.text("published"))
        assertEquals("unusable rate", json.text("reason"))
        assertTrue(json.text("detail").contains("InvalidChipSet"), json.text("detail"))
        assertTrue(c.sent("simulateTransaction").isEmpty() && c.sent("sendTransaction").isEmpty())
    }

    // ---- once per publish window: the 4 hours before Monday 05:00 UTC -----------------

    @Test
    fun `given the window's first and last second, when run, then each publishes`() {
        for (now in listOf(windowOpens, nextMonday - 1)) {
            val json = auto(cluster(now = now), FixedRate(BigDecimal("0.03")))
            assertEquals("true", json.text("published"), Instant.ofEpochSecond(now).toString())
        }
    }

    @Test
    fun `given an instant outside the window, when run, then no rate is fetched, nothing is sent, and the report names the window`() {
        val outside = listOf(windowOpens - 1, nextMonday, wednesday, Instant.parse("2026-10-11T12:00:00Z").epochSecond)
        for (now in outside) {
            val c = cluster(now = now)
            val rate = FixedRate(BigDecimal("0.03"))
            val json = auto(c, rate)
            val at = Instant.ofEpochSecond(now).toString()
            assertEquals("false", json.text("published"), at)
            assertEquals("outside the publish window", json.text("reason"), at)
            assertEquals(0, rate.asked, at)
            assertTrue(c.sent("simulateTransaction").isEmpty() && c.sent("sendTransaction").isEmpty(), at)
        }
        val json = auto(cluster(now = wednesday), FixedRate(BigDecimal("0.03")))
        assertEquals("2026-10-12T01:00:00Z", json.text("windowOpens"))
        assertEquals("2026-10-12T05:00:00Z", json.text("windowCloses"))
    }

    @Test
    fun `given a set already published in this window, when run again in it, then no rate is fetched and nothing is sent`() {
        val c = cluster()
        assertEquals("true", auto(c, FixedRate(BigDecimal("0.03"))).text("published"))
        c.now = inWindow + 3_600
        val rate = FixedRate(BigDecimal("0.05"))
        val json = auto(c, rate)
        assertEquals("false", json.text("published"))
        assertEquals("already published in this window", json.text("reason"))
        assertEquals(0, rate.asked)
        assertEquals(1, c.sent("sendTransaction").size, "only the first run's publish")
    }

    @Test
    fun `given a run in the window found no rate, when the next hourly run in it has one, then that run publishes`() {
        val c = cluster()
        assertEquals("no rate", auto(c, FixedRate(null)).text("reason"))
        c.now = inWindow + 3_600
        val json = auto(c, FixedRate(BigDecimal("0.03")))
        assertEquals("true", json.text("published"))
        assertEquals(1, c.sent("sendTransaction").size)
    }

    @Test
    fun `given a set published last week or just before this window, when run in the window, then it still publishes`() {
        for (publishedAt in listOf(nextMonday - 7 * 86_400 - 1, windowOpens - 1)) {
            val json = auto(cluster(configBytes(publishedAt = publishedAt)), FixedRate(BigDecimal("0.03")))
            assertEquals("true", json.text("published"), Instant.ofEpochSecond(publishedAt).toString())
        }
    }

    @Test
    fun `given an instant outside the window, when dry-run, then the rate is still fetched, priced and simulated, and nothing is sent`() {
        val c = cluster(now = wednesday)
        val rate = FixedRate(BigDecimal("0.03"))
        val json = auto(c, rate, "--dry-run")
        assertEquals(1, rate.asked, "the pre-scheduling check reaches Jupiter any day")
        assertEquals("0.03", json.text("rate"))
        assertEquals("false", json.text("insideWindow"), "and says a real run would send nothing now")
        assertEquals("false", json.text("published"))
        assertEquals(1, c.sent("simulateTransaction").size)
        assertTrue(c.sent("sendTransaction").isEmpty())
    }

    @Test
    fun `given this window's set already published, when dry-run in it, then it prices and simulates and says a real run would send nothing`() {
        val c = cluster(configBytes(publishedAt = inWindow))
        val json = auto(c, FixedRate(BigDecimal("0.03")), "--dry-run")
        assertEquals("true", json.text("insideWindow"))
        assertEquals("true", json.text("alreadyPublishedThisWindow"))
        assertEquals(1, c.sent("simulateTransaction").size)
        assertTrue(c.sent("sendTransaction").isEmpty())
    }

    @Test
    fun `given any instant, when the publish window is found, then it is the 4 hours before the next Monday 05 00 UTC`() {
        val window = windowOpens until nextMonday
        assertEquals(window, Chips.publishWindow(wednesday))
        assertEquals(window, Chips.publishWindow(monday))
        assertEquals(window, Chips.publishWindow(windowOpens))
        assertEquals(window, Chips.publishWindow(nextMonday - 1))
        assertEquals(window.first + 7 * 86_400 until window.last + 1 + 7 * 86_400, Chips.publishWindow(nextMonday))
    }

    @Test
    fun `given any instant, when the weekly boundary is found, then it is the latest Monday 05 00 UTC at or before it`() {
        assertEquals(monday, Chips.latestWeeklyBoundary(monday))
        assertEquals(monday - 7 * 86_400, Chips.latestWeeklyBoundary(monday - 1))
        assertEquals(monday, Chips.latestWeeklyBoundary(wednesday))
        assertEquals(monday, Chips.latestWeeklyBoundary(Instant.parse("2026-10-12T04:59:59Z").epochSecond))
        assertEquals(monday + 7 * 86_400, Chips.latestWeeklyBoundary(Instant.parse("2026-10-12T05:00:00Z").epochSecond))
    }

    // ---- dry run and keys -----------------------------------------------------------

    @Test
    fun `given --dry-run, when run, then the re-priced set is simulated and not sent`() {
        val c = cluster()
        val json = auto(c, FixedRate(BigDecimal("0.03")), "--dry-run")
        assertEquals("false", json.text("published"))
        assertEquals("true", json.text("simulated"))
        assertEquals(1, c.sent("simulateTransaction").size)
        assertTrue(c.sent("sendTransaction").isEmpty())
    }

    @Test
    fun `given --dry-run without the Operator key, when run, then it reports the rate and the set from a Config read and signs nothing`() {
        val c = cluster()
        val json = auto(c, FixedRate(BigDecimal("0.03")), "--dry-run", env = env - "HOLDOUT_OPERATOR_KEYPAIR")
        assertEquals("0.03", json.text("rate"))
        assertEquals(Chips.price(BigDecimal("0.03"), 6).map { it.baseUnits.toString() }, json.getValue("amounts").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("false", json.text("published"))
        assertTrue(c.sent("simulateTransaction").isEmpty())
    }

    @Test
    fun `given a key that is not Config's operator, when checked, then it is refused before any rate is fetched`() {
        val rate = FixedRate(BigDecimal("0.03"))
        assertFailsWith<CrankException> { auto(cluster(configBytes(configOperator = mint)), rate) }
        assertEquals(0, rate.asked)
    }

    // ---- arguments ------------------------------------------------------------------

    @Test
    fun `given the weekly flags, when parsed, then --auto excludes --rate and --decimals and there is no --if-drift any more`() {
        fun check(vararg a: String) = Chips.checkArgs(parseArgs(listOf("chips", "publish") + a))
        check("--auto")
        check("--auto", "--dry-run")
        assertFailsWith<CrankException> { check("--auto", "--rate", "0.02") }
        assertFailsWith<CrankException> { check("--auto", "--decimals", "6") }.also { assertTrue(it.message!!.contains("--decimals"), it.message) }
        assertFailsWith<CrankException> { check("--auto", "--if-drift", "0.20") }
    }

    // ---- the Jupiter answer -----------------------------------------------------------

    private val skr = Chips.JupiterRate.SKR_MINT

    @Test
    fun `given a Jupiter answer for the mint, when read, then its usdPrice is the rate, exactly`() {
        val body = """{"$skr":{"usdPrice":0.0213456,"blockId":1,"decimals":6,"priceChange24h":-1.2}}"""
        assertEquals(BigDecimal("0.0213456"), Chips.JupiterRate.parse(body, skr))
    }

    @Test
    fun `given an unusable Jupiter answer, when read, then it is no rate`() {
        listOf(
            "{}",
            """{"$skr":null}""",
            """{"$skr":{"usdPrice":0}}""",
            """{"$skr":{"usdPrice":-0.02}}""",
            """{"$skr":{"usdPrice":"abc"}}""",
            """{"other":{"usdPrice":0.02}}""",
            "not json",
        ).forEach { assertNull(Chips.JupiterRate.parse(it, skr), it) }
    }

    @Test
    fun `given the source, when asked, then it calls the price URL by mint, sends a key only when set, and a failed call is no rate`() {
        val seen = mutableListOf<Pair<String, Map<String, String>>>()
        val answer = """{"$skr":{"usdPrice":0.02}}"""
        val keyless = Chips.JupiterRate(get = { url, headers ->
            seen += url to headers
            answer
        })
        assertEquals(BigDecimal("0.02"), runBlocking { keyless.usdPerSkr() })
        assertEquals("https://api.jup.ag/price/v3?ids=$skr", seen.single().first)
        assertTrue(seen.single().second.isEmpty())

        seen.clear()
        runBlocking {
            Chips.JupiterRate(apiKey = "k", get = { url, headers ->
                seen += url to headers
                answer
            }).usdPerSkr()
        }
        assertEquals(mapOf("x-api-key" to "k"), seen.single().second)

        assertNull(runBlocking { Chips.JupiterRate(get = { _, _ -> null }).usdPerSkr() })
        assertNull(runBlocking { Chips.JupiterRate(get = { _, _ -> throw java.io.IOException("down") }).usdPerSkr() })
    }

    @Test
    fun `given Jupiter refuses, when the check reports no rate, then it says why instead of passing for a quiet week`() {
        val keyless = Chips.JupiterRate(get = { _, _ -> throw Chips.JupiterRefused(401) })
        val json = auto(cluster(), keyless)
        assertEquals("no rate", json.text("reason"))
        assertTrue(json.text("detail").contains("HOLDOUT_JUPITER_API_KEY"), json.text("detail"))

        val badKey = Chips.JupiterRate(apiKey = "stale", get = { _, _ -> throw Chips.JupiterRefused(401) })
        runBlocking { badKey.usdPerSkr() }
        assertEquals("Jupiter answered HTTP 401: HOLDOUT_JUPITER_API_KEY was refused", badKey.problem)

        val down = Chips.JupiterRate(get = { _, _ -> throw java.io.IOException("timed out") })
        runBlocking { down.usdPerSkr() }
        assertEquals("Jupiter could not be reached: timed out", down.problem)

        val empty = Chips.JupiterRate(get = { _, _ -> "{}" })
        runBlocking { empty.usdPerSkr() }
        assertTrue(empty.problem!!.contains("no usable usdPrice"))
    }
}
