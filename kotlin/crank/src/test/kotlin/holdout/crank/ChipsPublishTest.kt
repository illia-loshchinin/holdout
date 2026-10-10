package holdout.crank

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import holdout.core.AccountLayout
import holdout.core.Instructions
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T062 — `crank chips publish --rate` against a scripted cluster: what it reads
 * before it signs, the transaction it builds, and each way a send can end.
 */
class ChipsPublishTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val operator = Keypair.fromSeed(ByteArray(32) { 3 })
    private val mint = SolanaPublicKey(ByteArray(32) { 9 })
    private val configAddress = runBlocking { Pdas.config(programId) }

    private val env =
        mapOf(
            "HOLDOUT_RPC_URL" to "http://rpc.test",
            "HOLDOUT_PROGRAM_ID" to programId.base58(),
            "HOLDOUT_OPERATOR_KEYPAIR" to "/keys/operator.json",
        )

    /** A `Config` with [configOperator] as operator, the mock mint, and chip set [version]. */
    private fun configBytes(
        configOperator: SolanaPublicKey = operator.publicKey,
        version: Int = 1,
    ): ByteArray {
        val l = AccountLayout.Config
        val b = ByteArray(l.SIZE)
        l.DISCRIMINATOR.copyInto(b)
        configOperator.bytes.copyInto(b, l.OPERATOR)
        mint.bytes.copyInto(b, l.SKR_MINT)
        b[l.CHIP_VERSION] = version.toByte()
        return b
    }

    private fun mintBytes(decimals: Int) = ByteArray(82).also { it[44] = decimals.toByte() }

    /** The chain's time in every scripted cluster: Saturday 10 October 2026, 12:00 UTC. */
    private val chainNow = java.time.Instant.parse("2026-10-10T12:00:00Z").epochSecond

    /** A cluster that answers each method from [replies], and records what was sent. */
    private class Cluster(val replies: Map<String, (JsonObject) -> String>) : HttpNetworkDriver {
        val calls = mutableListOf<JsonObject>()

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = Json.parseToJsonElement(request.body ?: "{}").jsonObject
            calls += body
            val method = body.getValue("method").jsonPrimitive.content
            return (replies[method] ?: error("unexpected $method"))(body)
        }

        fun sentTransactions(method: String): List<Transaction> = calls
            .filter { it.getValue("method").jsonPrimitive.content == method }
            .map { Transaction.from(Base64.getDecoder().decode(it.getValue("params").jsonArray[0].jsonPrimitive.content)) }
    }

    private fun ok(result: String) = """{"jsonrpc":"2.0","result":$result,"id":"x"}"""

    private fun refused(message: String) = """{"jsonrpc":"2.0","error":{"code":-32002,"message":"$message"},"id":"x"}"""

    private fun accountInfo(data: ByteArray?) = ok(
        if (data == null) {
            """{"context":{"slot":1},"value":null}"""
        } else {
            """{"context":{"slot":1},"value":{"data":["${Base64.getEncoder().encodeToString(data)}","base64"],"executable":false,"lamports":1,"owner":"11111111111111111111111111111111","rentEpoch":0}}"""
        },
    )

    /**
     * [config] is what `Config` reads as until a transaction is sent, and
     * [configAfterSend] afterwards — the version that landed.
     */
    private fun cluster(
        config: ByteArray? = configBytes(),
        configAfterSend: ByteArray? = configBytes(version = 2),
        decimals: Int = 6,
        configFails: Boolean = false,
        send: () -> String = { ok("\"5sig\"") },
        statuses: MutableList<String> = mutableListOf("""{"err":null,"confirmationStatus":"confirmed"}"""),
        simulation: String = ok("""{"context":{"slot":1},"value":{"err":null,"logs":["Program log: Instruction: PublishChipSet"]}}"""),
    ): Cluster {
        var configNow = config
        return Cluster(
            mapOf(
                "getAccountInfo" to { body ->
                    when (body.getValue("params").jsonArray[0].jsonPrimitive.content) {
                        configAddress.base58() -> if (configFails) refused("Node is behind by 42 slots") else accountInfo(configNow)
                        mint.base58() -> accountInfo(mintBytes(decimals))
                        else -> accountInfo(null)
                    }
                },
                "getLatestBlockhash" to { _ -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""") },
                "getSlot" to { _ -> ok("100") },
                "getBlockTime" to { _ -> ok("$chainNow") },
                "simulateTransaction" to { _ -> simulation },
                "sendTransaction" to { _ -> send().also { configNow = configAfterSend } },
                "getSignatureStatuses" to { _ ->
                    val next = if (statuses.size > 1) statuses.removeAt(0) else statuses.single()
                    ok("""{"context":{"slot":1},"value":[$next]}""")
                },
            ),
        )
    }

    private var pauses = 0

    private fun publish(cluster: Cluster, vararg args: String, env: Map<String, String> = this.env): JsonObject = runBlocking {
        Chips.publish(
            parseArgs(listOf("chips", "publish") + args),
            CrankConfig.from(env),
            Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", cluster)),
            loadKeypair = { _, _ -> operator },
            pause = { pauses++ },
        )
    }

    private fun amountsIn(tx: Transaction): List<ULong> {
        val data = tx.message.instructions.single().data
        val b = ByteBuffer.wrap(data, 8, 32).order(ByteOrder.LITTLE_ENDIAN)
        return List(4) { b.getLong().toULong() }
    }

    private fun deadlineIn(tx: Transaction): Long = ByteBuffer.wrap(tx.message.instructions.single().data, 40, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()

    private val priced = listOf(460_000_000uL, 1_170_000_000uL, 2_340_000_000uL, 4_690_000_000uL)

    // ---- the deadline (FR-055a) ----------------------------------------------------

    @Test
    fun `a set published by hand is refused from chain time plus 10 minutes, and the report says when`() {
        val c = cluster()
        val json = publish(c, "--rate", "0.0213")
        assertEquals(chainNow + 600, deadlineIn(c.sentTransactions("sendTransaction").single()))
        assertEquals("2026-10-10T12:10:00Z", json.getValue("validUntil").jsonPrimitive.content)
    }

    @Test
    fun `--valid-until gives another deadline, and one not after the chain's time is refused before signing`() {
        val c = cluster()
        publish(c, "--rate", "0.0213", "--valid-until", "2026-10-12T05:00:00Z", "--dry-run")
        assertEquals(java.time.Instant.parse("2026-10-12T05:00:00Z").epochSecond, deadlineIn(c.sentTransactions("simulateTransaction").single()))

        for (late in listOf("2026-10-10T12:00:00Z", "2026-10-09T00:00:00Z")) {
            val d = cluster()
            val e = assertFailsWith<CrankException> { publish(d, "--rate", "0.0213", "--valid-until", late) }
            assertEquals(EXIT_CONFIG, e.exitCode, late)
            assertTrue(e.message!!.contains("6014"), e.message)
            assertTrue(d.sentTransactions("sendTransaction").isEmpty() && d.sentTransactions("simulateTransaction").isEmpty())
        }
        val bad = assertFailsWith<CrankException> { publish(cluster(), "--rate", "0.0213", "--valid-until", "Monday") }
        assertEquals(EXIT_CONFIG, bad.exitCode)
    }

    @Test
    fun `a set the program refuses as past its deadline is reported as such, refused or landed`() {
        val refusedSend = cluster(send = { refused("Transaction simulation failed: Error processing Instruction 0: custom program error: 0x177e") })
        val e1 = assertFailsWith<Chips.ChipSetExpiredException> { publish(refusedSend, "--rate", "0.0213") }
        assertEquals(EXIT_FAILED, e1.exitCode)
        assertTrue(e1.message!!.contains("deadline") && e1.message!!.contains("nothing was published"), e1.message)

        val landedFailed = cluster(statuses = mutableListOf("""{"err":{"InstructionError":[0,{"Custom":6014}]},"confirmationStatus":"confirmed"}"""))
        assertFailsWith<Chips.ChipSetExpiredException> { publish(landedFailed, "--rate", "0.0213") }
    }

    @Test
    fun `a dry run builds, signs and simulates the set, and sends nothing`() {
        val c = cluster()
        val json = publish(c, "--rate", "0.0213", "--dry-run")

        assertEquals("true", json.getValue("simulated").jsonPrimitive.content)
        assertEquals("false", json.getValue("sent").jsonPrimitive.content)
        assertEquals(2, json.getValue("nextVersion").jsonPrimitive.long)
        assertTrue(c.sentTransactions("sendTransaction").isEmpty(), "a dry run sends nothing")

        val tx = c.sentTransactions("simulateTransaction").single()
        assertEquals(priced, amountsIn(tx))
        assertEquals(operator.publicKey, tx.message.accounts.first(), "the operator pays")
        assertContentEquals(operator.sign(tx.message.serialize()), tx.signatures.single(), "signed by the operator")
        assertContentEquals(
            Instructions.discriminator("publish_chip_set"),
            tx.message.instructions
                .single()
                .data
                .copyOf(8),
        )
    }

    @Test
    fun `a send is confirmed and reports its signature and the new version`() {
        val c = cluster(statuses = mutableListOf("null", """{"err":null,"confirmationStatus":"processed"}""", """{"err":null,"confirmationStatus":"confirmed"}"""))
        val json = publish(c, "--rate", "0.0213")

        assertEquals("true", json.getValue("sent").jsonPrimitive.content)
        assertEquals("5sig", json.getValue("signature").jsonPrimitive.content)
        assertEquals(2, json.getValue("version").jsonPrimitive.long)
        assertEquals(priced, amountsIn(c.sentTransactions("sendTransaction").single()))
        assertEquals(2, pauses, "watched until confirmed, not longer")
    }

    @Test
    fun `the amounts follow the mint's decimals, and --decimals only cross-checks them`() {
        val c = cluster(decimals = 9)
        publish(c, "--rate", "0.0213", "--dry-run")
        assertEquals(priced.map { it * 1000uL }, amountsIn(c.sentTransactions("simulateTransaction").single()))

        val e = assertFailsWith<CrankException> { publish(cluster(decimals = 9), "--rate", "0.0213", "--decimals", "6") }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("mint"))
    }

    @Test
    fun `a key that is not Config's operator is refused before anything is signed`() {
        val c = cluster(config = configBytes(configOperator = SolanaPublicKey(ByteArray(32) { 4 })))
        val e = assertFailsWith<CrankException> { publish(c, "--rate", "0.0213") }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("Config.operator"))
        assertTrue(c.sentTransactions("sendTransaction").isEmpty())
    }

    @Test
    fun `an uninitialized program is reported, not published to`() {
        val e = assertFailsWith<CrankException> { publish(cluster(config = null), "--rate", "0.0213") }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("crank init"))
    }

    @Test
    fun `a failed Config read is a failure, not an uninitialized program`() {
        val e = assertFailsWith<CrankException> { publish(cluster(configFails = true), "--rate", "0.0213") }
        assertEquals(EXIT_FAILED, e.exitCode)
        assertTrue(e.message!!.contains("Node is behind"), e.message)
        assertTrue(!e.message!!.contains("crank init"), "the program may well be initialized")
    }

    @Test
    fun `a refused send says why, and that nothing was published`() {
        val c = cluster(send = { refused("Transaction simulation failed: custom program error: 0x1771") })
        val e = assertFailsWith<CrankException> { publish(c, "--rate", "0.0213") }
        assertEquals(EXIT_FAILED, e.exitCode)
        assertTrue(e.message!!.contains("0x1771") && e.message!!.contains("nothing was published"))
    }

    @Test
    fun `a send with no answer is not called a refusal, and names the signature to check`() {
        val c = cluster(send = { throw java.net.SocketTimeoutException("read timed out") })
        val e = assertFailsWith<CrankException> { publish(c, "--rate", "0.0213") }
        assertEquals(EXIT_FAILED, e.exitCode)
        val sent = c.calls.single { it.getValue("method").jsonPrimitive.content == "sendTransaction" }
        val tx = Transaction.from(Base64.getDecoder().decode(sent.getValue("params").jsonArray[0].jsonPrimitive.content))
        val signature = com.funkatronics.encoders.Base58.encodeToString(tx.signatures.single())
        assertTrue(e.message!!.contains(signature), "names the transaction it may have landed: ${e.message}")
        assertTrue(e.message!!.contains("may have landed"))
        assertTrue(!e.message!!.contains("nothing was published"), "it cannot know that")
        assertEquals(0, pauses, "and it is not watched or sent again")
    }

    @Test
    fun `the version reported is the one read back after confirmation`() {
        // Another publish landed in between: 1 before signing, 3 after.
        val json = publish(cluster(configAfterSend = configBytes(version = 3)), "--rate", "0.0213")
        assertEquals(3, json.getValue("version").jsonPrimitive.long)
    }

    @Test
    fun `a confirmed publish whose Config cannot be read back says so rather than guessing`() {
        val json = publish(cluster(configAfterSend = null), "--rate", "0.0213")
        assertEquals("true", json.getValue("sent").jsonPrimitive.content)
        assertEquals(JsonNull, json.getValue("version"))
    }

    @Test
    fun `a send that is never confirmed names its signature, and is not sent again`() {
        val c = cluster(statuses = mutableListOf("null"))
        val e = assertFailsWith<CrankException> { publish(c, "--rate", "0.0213") }
        assertEquals(EXIT_FAILED, e.exitCode)
        assertTrue(e.message!!.contains("5sig"))
        assertEquals(1, c.sentTransactions("sendTransaction").size, "a second publish would be a second version")
        assertEquals(Chips.CONFIRM_ATTEMPTS, pauses)
    }

    @Test
    fun `a transaction that lands and fails is a failure`() {
        val c = cluster(statuses = mutableListOf("""{"err":{"InstructionError":[0,{"Custom":6011}]},"confirmationStatus":"confirmed"}"""))
        assertEquals(EXIT_FAILED, assertFailsWith<CrankException> { publish(c, "--rate", "0.0213") }.exitCode)
    }

    @Test
    fun `a dry run whose simulation fails exits 2 with the program's words`() {
        val c = cluster(simulation = ok("""{"context":{"slot":1},"value":{"err":{"InstructionError":[0,{"Custom":6001}]},"logs":["Program log: AnchorError NotOperator"]}}"""))
        val e = assertFailsWith<CrankException> { publish(c, "--rate", "0.0213", "--dry-run") }
        assertEquals(EXIT_FAILED, e.exitCode)
        assertTrue(e.message!!.contains("6001"))
    }

    @Test
    fun `a dry run without the key still prices the set and its deadline, and reads only the chain's time`() {
        val c = cluster()
        val json = publish(c, "--rate", "0.0213", "--dry-run", env = env - "HOLDOUT_OPERATOR_KEYPAIR")
        assertEquals("false", json.getValue("simulated").jsonPrimitive.content)
        assertEquals(priced.map { it.toLong() }, json.getValue("amounts").jsonArray.map { it.jsonPrimitive.long })
        assertEquals("2026-10-10T12:10:00Z", json.getValue("validUntil").jsonPrimitive.content)
        assertEquals(setOf("getSlot", "getBlockTime"), c.calls.map { it.getValue("method").jsonPrimitive.content }.toSet())
    }

    @Test
    fun `a dry run without the key refuses an expired --valid-until, as a run with it does`() {
        val e =
            assertFailsWith<CrankException> {
                publish(cluster(), "--rate", "0.0213", "--valid-until", "2026-10-10T11:00:00Z", "--dry-run", env = env - "HOLDOUT_OPERATOR_KEYPAIR")
            }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("6014"), e.message)
    }
}
