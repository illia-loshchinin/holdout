package holdout.crank

import com.solana.publickey.SolanaPublicKey
import holdout.core.Rpc
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

/**
 * The crank: opens due cohorts, records forfeits, settles, drives scripted
 * members and publishes chip sets (contracts/crank-cli.md, T036).
 *
 * Every step is permissionless and idempotent — a stranger running it produces
 * the same state as the Scheduler (FR-030a, constitution VIII). Nothing here is
 * privileged except `chips publish`, which only the Operator's key can sign.
 *
 * Every command prints one JSON summary to stdout and exits [EXIT_OK],
 * [EXIT_CONFIG] or [EXIT_FAILED].
 */
fun main(args: Array<String>) {
    exitProcess(runCrank(args.toList(), System.getenv()) { println(it) })
}

/** Success. */
const val EXIT_OK = 0

/** A configuration error: a missing variable, a bad argument, a command this build cannot run. */
const val EXIT_CONFIG = 1

/** The cluster could not be read, or a transaction failed after retries. */
const val EXIT_FAILED = 2

/** Stops a command with an exit code and a line for the JSON summary. */
open class CrankException(val exitCode: Int, message: String) : Exception(message)

/**
 * A command that ran to the end but not cleanly — `crank run` with a failed
 * step: its whole summary is still printed, under [exitCode].
 */
class CrankSummaryException(exitCode: Int, val summary: JsonObject) : CrankException(exitCode, "${summary["failed"]} step(s) failed")

internal fun configError(message: String): Nothing = throw CrankException(EXIT_CONFIG, message)

internal fun failed(message: String): Nothing = throw CrankException(EXIT_FAILED, message)

/**
 * Runs one command. [env] and [out] are parameters so tests can drive it
 * without a process; [rpcFor] builds the client for the configured endpoint.
 */
fun runCrank(
    args: List<String>,
    env: Map<String, String>,
    rpcFor: (String) -> Rpc = { Rpc(it) },
    out: (String) -> Unit,
): Int {
    var command = args.takeWhile { !it.startsWith("--") }.joinToString(" ")
    return try {
        val parsed = parseArgs(args)
        command = parsed.words.joinToString(" ")
        if (parsed.words.isEmpty() || "help" in parsed.flags) {
            out(usage().toString())
            return if (parsed.words.isEmpty() && "help" !in parsed.flags) EXIT_CONFIG else EXIT_OK
        }
        // The command is resolved before the environment is read, so a typo is
        // reported as a typo and not as a missing variable.
        val command: (CrankConfig) -> JsonObject =
            when (parsed.words) {
                listOf("status") -> {
                    parsed.allow()
                    fun(config: CrankConfig) = runBlocking { Status.run(config, rpcFor(config.rpcUrl)) }
                }
                listOf("init") ->
                    if ("test-league" in parsed.flags) {
                        Init.checkTestLeagueArgs(parsed)
                        fun(config: CrankConfig) = runBlocking { Init.runTestLeague(parsed, config, rpcFor(config.rpcUrl)) }
                    } else {
                        Init.checkArgs(parsed)
                        fun(config: CrankConfig) = runBlocking { Init.run(parsed, config, rpcFor(config.rpcUrl)) }
                    }
                listOf("chips", "publish") -> {
                    Chips.checkArgs(parsed)
                    fun(config: CrankConfig) = runBlocking { Chips.publish(parsed, config, rpcFor(config.rpcUrl)) }
                }
                listOf("run") -> {
                    Run.checkArgs(parsed)
                    fun(config: CrankConfig) = runBlocking { Run.run(parsed, config, rpcFor(config.rpcUrl)) }
                }
                listOf("scripted", "plan") -> {
                    Scripted.checkArgs(parsed)
                    fun(config: CrankConfig) = runBlocking { Scripted.plan(parsed, config, rpcFor(config.rpcUrl)) }
                }
                listOf("scripted", "run") -> {
                    Scripted.checkArgs(parsed)
                    fun(config: CrankConfig) = runBlocking { Scripted.run(parsed, config, rpcFor(config.rpcUrl)) }
                }
                listOf("scripted", "fund") -> {
                    Scripted.checkArgs(parsed)
                    fun(config: CrankConfig) = runBlocking { Scripted.fund(parsed, config, rpcFor(config.rpcUrl)) }
                }
                else -> configError("unknown command '${parsed.words.joinToString(" ")}'; try --help")
            }
        out(command(CrankConfig.from(env)).toString())
        EXIT_OK
    } catch (e: CrankSummaryException) {
        out(e.summary.toString())
        e.exitCode
    } catch (e: CrankException) {
        out(
            buildJsonObject {
                put("command", command)
                put("error", e.message)
            }.toString(),
        )
        e.exitCode
    } catch (e: Exception) {
        // Anything unforeseen still ends in a JSON summary and exit 2, never a
        // bare stack trace and the JVM's exit 1, which means a configuration
        // error to whoever schedules the crank (contracts/crank-cli.md).
        out(
            buildJsonObject {
                put("command", command)
                put("error", "unexpected ${e::class.simpleName}: ${e.message}")
            }.toString(),
        )
        EXIT_FAILED
    }
}

private fun usage(): JsonObject = buildJsonObject {
    put(
        "usage",
        "crank <command> [--dry-run]; commands: status, chips publish --rate <usd-per-skr> [--decimals <n>], " +
            "init [--admin <keypair>] --operator <pubkey> --sponsor <pubkey> --treasury <pubkey> --developer <pubkey> --rate <usd-per-skr> [--first-start <iso>] [--fund-faucet <sol>], " +
            "init --test-league --first-start <iso> [--day-seconds <n, default 600>] [--days <n, default 3>] [--reps <n, default 5>] [--form <easy|hard, default easy>] [--league-id <100..255>] [--cadence <s>] [--join-lock <s>], " +
            "run, scripted plan --cohort <address> | scripted run [--floor-7d <n>] [--floor-21d <n>] [--last-start <iso>] | scripted fund [--lamports <n>] (contracts/crank-cli.md)",
    )
}

/** A command line: its words (`chips publish`), `--name value` options and bare flags. */
data class Args(
    val words: List<String>,
    val options: Map<String, String>,
    val flags: Set<String>,
) {
    /** Build and simulate, send nothing — accepted by every command. */
    val dryRun: Boolean get() = "dry-run" in flags

    fun option(name: String): String? = options[name]

    /** Refuses any option or flag the command does not take, rather than ignoring a typo. */
    fun allow(vararg names: String) {
        val allowed = names.toSet() + "dry-run"
        (options.keys + flags).firstOrNull { it !in allowed }?.let { configError("'${words.joinToString(" ")}' does not take --$it") }
    }
}

/** Flags that never take a value. Every other `--name` does. */
private val FLAGS = setOf("dry-run", "auto", "help", "test-league")

fun parseArgs(args: List<String>): Args {
    val words = mutableListOf<String>()
    val options = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            !arg.startsWith("--") -> words += arg
            "=" in arg -> {
                val (name, value) = arg.removePrefix("--").split("=", limit = 2)
                options[name] = value
            }
            arg.removePrefix("--") in FLAGS -> flags += arg.removePrefix("--")
            else -> {
                val name = arg.removePrefix("--")
                val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") } ?: configError("--$name needs a value")
                options[name] = value
                i++
            }
        }
        i++
    }
    return Args(words, options, flags)
}

/**
 * The environment the contract names (contracts/crank-cli.md, Configuration).
 *
 * `HOLDOUT_RPC_URL` and `HOLDOUT_PROGRAM_ID` are checked for every command.
 * Keys are checked by the commands that sign: the contract lists
 * `HOLDOUT_PAYER_KEYPAIR` as required, but `status` only reads, and a monitor
 * that needs a funded key to look at the faucet is one more secret on one more
 * machine for nothing.
 */
data class CrankConfig(
    val rpcUrl: String,
    val programId: SolanaPublicKey,
    val payerKeypair: String?,
    val operatorKeypair: String?,
    val adminKeypair: String?,
    val scriptedKeysDir: String?,
    /** `(Membership, Profile)` pairs per paged transaction (R9). */
    val pageSize: Int,
) {
    fun requirePayer(): String = payerKeypair ?: configError("HOLDOUT_PAYER_KEYPAIR is not set")

    fun requireOperator(): String = operatorKeypair ?: configError("HOLDOUT_OPERATOR_KEYPAIR is not set; only the Operator may publish a chip set")

    companion object {
        const val DEFAULT_PAGE_SIZE = 10

        fun from(env: Map<String, String>): CrankConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() } ?: configError("$name is not set")

            fun optional(name: String) = env[name]?.takeIf { it.isNotBlank() }

            val programId =
                required("HOLDOUT_PROGRAM_ID").let {
                    runCatching { SolanaPublicKey.from(it) }.getOrNull() ?: configError("HOLDOUT_PROGRAM_ID is not a public key: $it")
                }
            val pageSize =
                optional("HOLDOUT_PAGE_SIZE")?.let {
                    it.toIntOrNull()?.takeIf { n -> n > 0 } ?: configError("HOLDOUT_PAGE_SIZE must be a positive number, got $it")
                } ?: DEFAULT_PAGE_SIZE
            return CrankConfig(
                rpcUrl = required("HOLDOUT_RPC_URL"),
                programId = programId,
                payerKeypair = optional("HOLDOUT_PAYER_KEYPAIR"),
                operatorKeypair = optional("HOLDOUT_OPERATOR_KEYPAIR"),
                adminKeypair = optional("HOLDOUT_ADMIN_KEYPAIR"),
                scriptedKeysDir = optional("HOLDOUT_SCRIPTED_KEYS_DIR"),
                pageSize = pageSize,
            )
        }
    }
}
