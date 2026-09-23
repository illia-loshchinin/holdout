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

    /** A `Config` with [configOperator] as operator, the mock mint, and chip set version 1. */
    private fun configBytes(configOperator: SolanaPublicKey = operator.publicKey): ByteArray {
        val l = AccountLayout.Config
        val b = ByteArray(l.SIZE)
        l.DISCRIMINATOR.copyInto(b)
        configOperator.bytes.copyInto(b, l.OPERATOR)
        mint.bytes.copyInto(b, l.SKR_MINT)
        b[l.CHIP_VERSION] = 1
        return b
    }

    private fun mintBytes(decimals: Int) = ByteArray(82).also { it[44] = decimals.toByte() }

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

    private fun cluster(
        config: ByteArray? = configBytes(),
        decimals: Int = 6,
        send: String = ok("\"5sig\""),
        statuses: MutableList<String> = mutableListOf("""{"err":null,"confirmationStatus":"confirmed"}"""),
        simulation: String = ok("""{"context":{"slot":1},"value":{"err":null,"logs":["Program log: Instruction: PublishChipSet"]}}"""),
    ) = Cluster(
        mapOf(
            "getAccountInfo" to { body ->
                when (body.getValue("params").jsonArray[0].jsonPrimitive.content) {
                    configAddress.base58() -> accountInfo(config)
                    mint.base58() -> accountInfo(mintBytes(decimals))
                    else -> accountInfo(null)
                }
            },
            "getLatestBlockhash" to { _ -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""") },
            "simulateTransaction" to { _ -> simulation },
            "sendTransaction" to { _ -> send },
            "getSignatureStatuses" to { _ ->
                val next = if (statuses.size > 1) statuses.removeAt(0) else statuses.single()
                ok("""{"context":{"slot":1},"value":[$next]}""")
            },
        ),
    )

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

    private val priced = listOf(460_000_000uL, 1_170_000_000uL, 2_340_000_000uL, 4_690_000_000uL)

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
        assertEquals(EXIT_FAILED, e.exitCode)
        assertTrue(e.message!!.contains("crank init"))
    }

    @Test
    fun `a refused send says why, and that nothing was published`() {
        val c = cluster(send = refused("Transaction simulation failed: custom program error: 0x1771"))
        val e = assertFailsWith<CrankException> { publish(c, "--rate", "0.0213") }
        assertEquals(EXIT_FAILED, e.exitCode)
        assertTrue(e.message!!.contains("0x1771") && e.message!!.contains("nothing was published"))
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
    fun `a dry run without the key still prices the set, and touches no cluster`() {
        val c = cluster()
        val json = publish(c, "--rate", "0.0213", "--dry-run", env = env - "HOLDOUT_OPERATOR_KEYPAIR")
        assertEquals("false", json.getValue("simulated").jsonPrimitive.content)
        assertEquals(priced.map { it.toLong() }, json.getValue("amounts").jsonArray.map { it.jsonPrimitive.long })
        assertTrue(c.calls.isEmpty())
    }
}
