package holdout.crank

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T036 — argument parsing, configuration and exit codes (contracts/crank-cli.md):
 * a JSON summary on stdout, `0` on success, `1` on a configuration error, `2`
 * when the cluster or a transaction failed.
 */
class MainTest {

    private val env =
        mapOf(
            "HOLDOUT_RPC_URL" to "http://rpc.test",
            "HOLDOUT_PROGRAM_ID" to "Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4",
        )

    private fun run(vararg args: String, env: Map<String, String> = this.env): Pair<Int, JsonObject> {
        val out = mutableListOf<String>()
        val code = runCrank(args.toList(), env) { out += it }
        return code to Json.parseToJsonElement(out.single()).jsonObject
    }

    // ---- parsing -------------------------------------------------------------

    @Test
    fun `words, options and flags are told apart`() {
        val a = parseArgs(listOf("chips", "publish", "--rate", "0.0213", "--dry-run", "--decimals=9"))
        assertEquals(listOf("chips", "publish"), a.words)
        assertEquals(mapOf("rate" to "0.0213", "decimals" to "9"), a.options)
        assertEquals(setOf("dry-run"), a.flags)
        assertTrue(a.dryRun)
    }

    @Test
    fun `an option with no value is a configuration error`() {
        val e = assertFailsWith<CrankException> { parseArgs(listOf("chips", "publish", "--rate")) }
        assertEquals(EXIT_CONFIG, e.exitCode)
    }

    // ---- configuration -------------------------------------------------------

    @Test
    fun `the RPC endpoint and program id are required`() {
        val (code, json) = run("status", env = env - "HOLDOUT_RPC_URL")
        assertEquals(EXIT_CONFIG, code)
        assertTrue(json.getValue("error").jsonPrimitive.content.contains("HOLDOUT_RPC_URL"))

        val (code2, _) = run("status", env = env + ("HOLDOUT_PROGRAM_ID" to "not a key"))
        assertEquals(EXIT_CONFIG, code2)
    }

    @Test
    fun `the page size defaults to 10 and must be a positive number`() {
        assertEquals(10, CrankConfig.from(env).pageSize)
        assertEquals(4, CrankConfig.from(env + ("HOLDOUT_PAGE_SIZE" to "4")).pageSize)
        assertFailsWith<CrankException> { CrankConfig.from(env + ("HOLDOUT_PAGE_SIZE" to "0")) }
        assertFailsWith<CrankException> { CrankConfig.from(env + ("HOLDOUT_PAGE_SIZE" to "ten")) }
    }

    @Test
    fun `a sending command needs the payer, a read does not`() {
        val config = CrankConfig.from(env)
        assertFailsWith<CrankException> { config.requirePayer() }
        assertEquals("/keys/payer.json", CrankConfig.from(env + ("HOLDOUT_PAYER_KEYPAIR" to "/keys/payer.json")).requirePayer())
    }

    // ---- commands ------------------------------------------------------------

    @Test
    fun `an unknown command is a configuration error`() {
        val (code, json) = run("launch")
        assertEquals(EXIT_CONFIG, code)
        assertTrue(json.getValue("error").jsonPrimitive.content.contains("launch"))
    }

    @Test
    fun `commands owned by later tasks say which, and send nothing`() {
        for (words in listOf(listOf("run"), listOf("init"), listOf("scripted", "run"))) {
            val (code, json) = run(*words.toTypedArray())
            assertEquals(EXIT_CONFIG, code, "$words")
            assertTrue(json.getValue("error").jsonPrimitive.content.contains("T0"), "$words: ${json["error"]}")
        }
    }

    @Test
    fun `an option a command does not take is refused`() {
        val (code, json) = run("status", "--rate", "0.02")
        assertEquals(EXIT_CONFIG, code)
        assertTrue(json.getValue("error").jsonPrimitive.content.contains("--rate"))
    }

    @Test
    fun `chips publish --dry-run prints the four amounts and sends nothing`() {
        val (code, json) = run("chips", "publish", "--rate", "0.0213", "--dry-run")
        assertEquals(EXIT_OK, code)
        assertEquals("chips publish", json.getValue("command").jsonPrimitive.content)
        assertEquals(
            listOf(460_000_000L, 1_170_000_000L, 2_340_000_000L, 4_690_000_000L),
            json.getValue("amounts").jsonArray.map { it.jsonPrimitive.long },
        )
        assertEquals("false", json.getValue("sent").jsonPrimitive.content)
    }

    @Test
    fun `chips publish needs a rate`() {
        val (code, json) = run("chips", "publish", "--dry-run")
        assertEquals(EXIT_CONFIG, code)
        assertTrue(json.getValue("error").jsonPrimitive.content.contains("--rate"))
    }

    @Test
    fun `chips publish refuses a set the program would reject`() {
        val (code, json) = run("chips", "publish", "--rate", "2", "--dry-run")
        assertEquals(EXIT_CONFIG, code)
        assertTrue(json.getValue("error").jsonPrimitive.content.contains("strictly increasing"))
    }

    @Test
    fun `chips publish without --dry-run needs the operator key`() {
        val (code, json) = run("chips", "publish", "--rate", "0.0213")
        assertEquals(EXIT_CONFIG, code)
        assertTrue(json.getValue("error").jsonPrimitive.content.contains("HOLDOUT_OPERATOR_KEYPAIR"))
    }

    @Test
    fun `the Phase 2 drift check is not in this build`() {
        val (code, _) = run("chips", "publish", "--auto", "--if-drift", "0.20")
        assertEquals(EXIT_CONFIG, code)
    }
}
