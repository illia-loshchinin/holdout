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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T091 — `crank init` against a scripted cluster that applies what it is sent:
 * the order of the steps, who signs each, and that a run over a program that
 * is already set up sends nothing.
 */
class InitTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val admin = Keypair.fromSeed(ByteArray(32) { 1 })
    private val operator = Keypair.fromSeed(ByteArray(32) { 3 })
    private val mint = Keypair.fromSeed(ByteArray(32) { 9 })
    private val sponsor = SolanaPublicKey(ByteArray(32) { 5 })
    private val treasury = SolanaPublicKey(ByteArray(32) { 6 })
    private val developer = SolanaPublicKey(ByteArray(32) { 7 })

    private val configAddress = runBlocking { Pdas.config(programId) }
    private val vault = runBlocking { Pdas.faucetVault(programId) }
    private val league0 = runBlocking { Pdas.league(programId, 0) }
    private val league1 = runBlocking { Pdas.league(programId, 1) }

    private val env =
        mapOf(
            "HOLDOUT_RPC_URL" to "http://rpc.test",
            "HOLDOUT_PROGRAM_ID" to programId.base58(),
            "HOLDOUT_OPERATOR_KEYPAIR" to "/keys/operator.json",
            "HOLDOUT_ADMIN_KEYPAIR" to "/keys/admin.json",
        )

    private val fullArgs =
        arrayOf(
            "--operator",
            operator.publicKey.base58(),
            "--sponsor",
            sponsor.base58(),
            "--treasury",
            treasury.base58(),
            "--developer",
            developer.base58(),
            "--rate",
            "0.0213",
        )

    /** A devnet in miniature: it applies `initialize`, `publish_chip_set`, `create_league` and transfers to the vault. */
    private inner class Chain(
        var config: ByteArray? = null,
        val leagues: MutableSet<Int> = mutableSetOf(),
        var vaultLamports: Long = 0,
        /** Chain time: Sunday 4 October 2026, noon UTC — before the default first start's cutoff. */
        var chainNow: Long = LeagueParams.FIRST_JUDGED_START - 17 * 3_600,
    ) : HttpNetworkDriver {
        val sent = mutableListOf<Transaction>()
        val simulated = mutableListOf<Transaction>()

        fun configBytes(
            configAdmin: SolanaPublicKey,
            configOperator: SolanaPublicKey,
            skrMint: SolanaPublicKey,
            version: Int,
        ): ByteArray {
            val l = AccountLayout.Config
            val b = ByteArray(l.SIZE)
            l.DISCRIMINATOR.copyInto(b)
            configAdmin.bytes.copyInto(b, l.ADMIN)
            configOperator.bytes.copyInto(b, l.OPERATOR)
            skrMint.bytes.copyInto(b, l.SKR_MINT)
            b[l.CHIP_VERSION] = version.toByte()
            return b
        }

        private fun apply(tx: Transaction) {
            val keys = tx.message.accounts
            tx.message.instructions.forEach { ix ->
                val program = keys[ix.programIdIndex.toInt()]
                val data = ix.data
                if (program == Instructions.SYSTEM_PROGRAM) {
                    vaultLamports += ByteBuffer.wrap(data, 4, 8).order(ByteOrder.LITTLE_ENDIAN).long
                    return@forEach
                }
                val head = data.copyOf(8)
                when {
                    head.contentEquals(Instructions.discriminator("initialize")) -> {
                        val op = SolanaPublicKey(data.copyOfRange(8, 40))
                        config = configBytes(keys[0], op, keys[ix.accountIndices[4].toInt()], 0)
                        vaultLamports = 890_880
                    }
                    head.contentEquals(Instructions.discriminator("publish_chip_set")) -> {
                        val c = config!!
                        c[AccountLayout.Config.CHIP_VERSION] = (c[AccountLayout.Config.CHIP_VERSION] + 1).toByte()
                    }
                    head.contentEquals(Instructions.discriminator("create_league")) -> leagues += data[8].toInt()
                    else -> error("unexpected instruction")
                }
            }
        }

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = Json.parseToJsonElement(request.body ?: "{}").jsonObject
            val params = body["params"]?.jsonArray
            fun tx() = Transaction.from(Base64.getDecoder().decode(params!![0].jsonPrimitive.content))
            return when (body.getValue("method").jsonPrimitive.content) {
                "getAccountInfo" ->
                    when (params!![0].jsonPrimitive.content) {
                        configAddress.base58() -> accountInfo(config)
                        league0.base58() -> accountInfo(if (0 in leagues) ByteArray(80) else null)
                        league1.base58() -> accountInfo(if (1 in leagues) ByteArray(80) else null)
                        mint.publicKey.base58() -> accountInfo(ByteArray(82).also { it[44] = 6 })
                        else -> accountInfo(null)
                    }
                "getSlot" -> ok("100")
                "getBlockTime" -> ok("$chainNow")
                "getBalance" -> ok("""{"context":{"slot":1},"value":$vaultLamports}""")
                "getLatestBlockhash" -> ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""")
                "simulateTransaction" -> {
                    simulated += tx()
                    ok("""{"context":{"slot":1},"value":{"err":null,"logs":[]}}""")
                }
                "sendTransaction" -> {
                    val t = tx()
                    sent += t
                    apply(t)
                    ok("\"sig${sent.size}\"")
                }
                "getSignatureStatuses" -> ok("""{"context":{"slot":1},"value":[{"err":null,"confirmationStatus":"confirmed"}]}""")
                else -> error("unexpected method")
            }
        }

        fun names(): List<String> = sent.map { t ->
            val ix = t.message.instructions.single()
            if (t.message.accounts[ix.programIdIndex.toInt()] == Instructions.SYSTEM_PROGRAM) {
                "transfer"
            } else {
                listOf("initialize", "publish_chip_set", "create_league").first { Instructions.discriminator(it).contentEquals(ix.data.copyOf(8)) }
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

    private fun init(chain: Chain, vararg args: String, env: Map<String, String> = this.env): JsonObject = runBlocking {
        Init.run(
            parseArgs(listOf("init") + args),
            CrankConfig.from(env),
            Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", chain)),
            loadKeypair = { _, role -> if (role == "admin") admin else operator },
            newMint = { mint },
            pause = {},
        )
    }

    private fun steps(json: JsonObject): Map<String, String> = json.getValue("steps").jsonArray.associate {
        val o = it.jsonObject
        o.getValue("step").jsonPrimitive.content to o.getValue("status").jsonPrimitive.content
    }

    @Test
    fun `a fresh program is initialized, priced, then given both leagues, in that order`() {
        val chain = Chain()
        val json = init(chain, *fullArgs)

        assertEquals(listOf("initialize", "publish_chip_set", "create_league", "create_league"), chain.names())
        assertEquals(
            mapOf("initialize" to "sent", "chips" to "sent", "league 0" to "sent", "league 1" to "sent", "faucet" to "skipped"),
            steps(json),
        )
        assertEquals(setOf(0, 1), chain.leagues)

        val initTx = chain.sent[0]
        assertEquals(admin.publicKey, initTx.message.accounts[0], "the upgrade authority pays")
        assertEquals(2, initTx.signatures.size, "the admin and the new mint both sign")
        assertContentEquals(admin.sign(initTx.message.serialize()), initTx.signatures[0])
        assertContentEquals(mint.sign(initTx.message.serialize()), initTx.signatures[1])
        assertEquals(operator.publicKey, chain.sent[1].message.accounts[0], "the Operator publishes the chip set")
        assertEquals(admin.publicKey, chain.sent[2].message.accounts[0], "the admin creates the leagues")
    }

    @Test
    fun `the leagues start Monday 5 October 0500 UTC unless told otherwise`() {
        val chain = Chain()
        init(chain, *fullArgs)
        val data = chain.sent[2].message.instructions.single().data
        // league_id, duration_days, reps_per_day (u16), exercise, form, then first_start.
        val firstStart = ByteBuffer.wrap(data, 14, 8).order(ByteOrder.LITTLE_ENDIAN).long
        assertEquals(LeagueParams.FIRST_JUDGED_START, firstStart)

        val later = Chain()
        init(later, *fullArgs, "--first-start", "2026-10-12T05:00:00Z")
        val laterStart = ByteBuffer.wrap(later.sent[2].message.instructions.single().data, 14, 8).order(ByteOrder.LITTLE_ENDIAN).long
        assertEquals(LeagueParams.FIRST_JUDGED_START + 604_800, laterStart)
    }

    @Test
    fun `given a fresh program, when init runs, then the 7-day league is Easy and the 21-day Hard, each with its reserve vault`() {
        val chain = Chain()
        val json = init(chain, *fullArgs)
        val leagues = chain.sent.drop(2).map { it.message.instructions.single() to it }
        assertEquals(listOf(0, 1), leagues.map { (ix, _) -> ix.data[8].toInt() })
        assertEquals(listOf(LeagueParams.FORM_KNEES_ALLOWED, LeagueParams.FORM_TOES), leagues.map { (ix, _) -> ix.data[13].toInt() })
        leagues.forEach { (ix, tx) ->
            val accounts = ix.accountIndices.map { tx.message.accounts[it.toInt()] }
            val id = ix.data[8].toInt()
            assertEquals(listOf(mint.publicKey, runBlocking { Pdas.reserveVault(programId, id) }), accounts.subList(3, 5), "the new mint, then league $id's reserve vault")
        }
        val steps = json.getValue("steps").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Easy", "Hard"), steps.filter { it.getValue("step").jsonPrimitive.content.startsWith("league") }.map { it.getValue("formName").jsonPrimitive.content })
    }

    @Test
    fun `given a fresh program, when init runs, then initialize names the developer wallet and its SKR account`() {
        val chain = Chain()
        init(chain, *fullArgs)
        val tx = chain.sent[0]
        val ix = tx.message.instructions.single()
        val accounts = ix.accountIndices.map { tx.message.accounts[it.toInt()] }
        assertEquals(listOf(developer, runBlocking { Pdas.associatedToken(developer, mint.publicKey) }), accounts.subList(8, 10))
        assertContentEquals(developer.bytes, ix.data.copyOfRange(8 + 3 * 32, 8 + 4 * 32), "the fourth key argument")
    }

    @Test
    fun `a run over a program already set up sends nothing`() {
        val chain = Chain()
        init(chain, *fullArgs)
        val before = chain.sent.size

        val json = init(chain)
        assertEquals(before, chain.sent.size)
        assertTrue(steps(json).values.all { it == "skipped" }, "every step already done: ${steps(json)}")
    }

    @Test
    fun `a run that stopped part-way picks up where it stopped`() {
        val chain = Chain()
        chain.config = chain.configBytes(admin.publicKey, operator.publicKey, mint.publicKey, version = 1)
        chain.leagues += 0

        val json = init(chain)
        assertEquals(listOf("create_league"), chain.names())
        assertEquals(1, chain.sent.single().message.instructions.single().data[8].toInt(), "only the 21-day league was missing")
        assertEquals("skipped", steps(json)["initialize"])
    }

    @Test
    fun `the faucet vault is topped up to the target, and no further`() {
        val chain = Chain()
        init(chain, *fullArgs, "--fund-faucet", "2.5")
        assertEquals(2_500_000_000, chain.vaultLamports)
        assertEquals("transfer", chain.names().last())

        init(chain, "--fund-faucet", "2.5")
        assertEquals(5, chain.sent.size, "already at the target: nothing more is sent")
    }

    @Test
    fun `a dry run simulates initialize and sends nothing`() {
        val chain = Chain()
        val json = init(chain, *fullArgs, "--dry-run")
        assertTrue(chain.sent.isEmpty())
        assertEquals(1, chain.simulated.size)
        assertEquals("simulated", steps(json)["initialize"])
        assertEquals("planned", steps(json)["league 0"])
    }

    @Test
    fun `a dry run of a half-done setup plans the leagues behind a chip set only simulated`() {
        // Initialized, but no chip set published and no leagues: a run that stopped after `initialize`.
        val chain = Chain()
        chain.config = chain.configBytes(admin.publicKey, operator.publicKey, mint.publicKey, version = 0)
        val json = init(chain, "--rate", "0.0213", "--dry-run")
        assertTrue(chain.sent.isEmpty())
        assertEquals("skipped", steps(json)["initialize"])
        assertEquals("simulated", steps(json)["chips"])
        assertEquals("planned", steps(json)["league 0"], "create_league would be refused (6012) against the unpublished chip set")
        assertEquals("planned", steps(json)["league 1"])
    }

    @Test
    fun `a fresh program needs the operator, sponsor, treasury, developer and rate before anything is sent`() {
        listOf("--operator", "--sponsor", "--treasury", "--developer", "--rate").forEach { missing ->
            val chain = Chain()
            val i = fullArgs.indexOf(missing)
            val args = fullArgs.toMutableList().apply {
                removeAt(i + 1)
                removeAt(i)
            }
            val e = assertFailsWith<CrankException> { init(chain, *args.toTypedArray()) }
            assertEquals(EXIT_CONFIG, e.exitCode, missing)
            assertTrue(chain.sent.isEmpty(), "nothing sent without $missing")
        }
    }

    @Test
    fun `an operator key that is not the named operator is refused before anything is sent`() {
        val chain = Chain()
        val args = fullArgs.toMutableList()
        args[args.indexOf("--operator") + 1] = sponsor.base58()
        val e = assertFailsWith<CrankException> { init(chain, *args.toTypedArray()) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(chain.sent.isEmpty())
    }

    @Test
    fun `a first start that is not Monday 0500 UTC is refused, as the program would`() {
        val chain = Chain()
        val e = assertFailsWith<CrankException> { init(chain, *fullArgs, "--first-start", "2026-09-28T06:00:00Z") }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(chain.sent.isEmpty())
    }

    @Test
    fun `leagues need Config's admin`() {
        val chain = Chain()
        chain.config = chain.configBytes(sponsor, operator.publicKey, mint.publicKey, version = 1)
        val e = assertFailsWith<CrankException> { init(chain) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(chain.sent.isEmpty())
    }

    @Test
    fun `init is a command the crank runs`() {
        val lines = mutableListOf<String>()
        val code = runCrank(listOf("init", "--nonsense", "x"), env) { lines += it }
        assertEquals(EXIT_CONFIG, code)
        assertTrue("does not take --nonsense" in lines.single(), lines.single())
    }

    @Test
    fun `a first start whose joining has closed is refused before anything is sent`() {
        val chain = Chain(chainNow = LeagueParams.FIRST_JUDGED_START - 3_600)
        val e = assertFailsWith<CrankException> { init(chain, *fullArgs) }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue("later Monday" in e.message!!, e.message)
        assertTrue(chain.sent.isEmpty(), "not even initialize: the whole run needs a start that can still be joined")

        init(chain, *fullArgs, "--first-start", "2026-10-12T05:00:00Z")
        assertEquals(4, chain.sent.size)
    }

    @Test
    fun `setup flags on an initialized program are reported as ignored, not silently dropped`() {
        val chain = Chain()
        init(chain, *fullArgs)
        val json = init(chain, "--sponsor", treasury.base58())
        assertTrue(json.getValue("notes").jsonArray.single().jsonPrimitive.content.contains("--sponsor ignored"))
    }

    @Test
    fun `a faucet target too large for a u64 is refused, not wrapped`() {
        val e = assertFailsWith<CrankException> { init(Chain(), *fullArgs, "--fund-faucet", "1e11") }
        assertEquals(EXIT_CONFIG, e.exitCode)
    }
}
