package holdout.crank

import com.funkatronics.encoders.Base58
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import holdout.core.ConfigSnapshot
import holdout.core.Instructions
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** A rate or a chip set the Operator should not publish, with the reason. */
class ChipPricingException(message: String) : IllegalArgumentException(message)

/**
 * `crank chips publish --rate <usd-per-skr>` (T062, research R15).
 *
 * **The one place a price exists** (constitution III). The Operator reads the
 * SKR rate by hand from a public market on launch day — a trusted off-chain
 * input, named as one in the README (constitution XI) — and this turns it into
 * four SKR amounts. `publish_chip_set` receives those amounts and nothing else:
 * no rate, no USD figure, and nothing on-chain can tell where they came from.
 *
 * Each tier is `floor(usd ÷ rate)` whole SKR, rounded **down** to a multiple of
 * 10, and never below 10 — readable amounts, never more SKR than the tier's
 * dollars. The arithmetic is decimal, not floating point: a rate of 0.1 prices
 * $10 at exactly 100 SKR, not 99.999….
 */
object Chips {
    /** The four stake tiers (FR-054). The USD labels are display only, always shown with ≈ (FR-059). */
    val USD_TIERS = listOf(10, 25, 50, 100)

    /** Amounts are multiples of this many whole SKR (R15). */
    const val STEP_WHOLE_SKR = 10

    /** No tier below this many whole SKR (R15). */
    const val MIN_WHOLE_SKR = 10

    /**
     * Decimals of the mock SKR mint: 6, the `SKR_DECIMALS` `initialize` creates
     * it with (decision H31). `--decimals` overrides it until the tool reads the
     * mint itself.
     */
    const val DEFAULT_SKR_DECIMALS = 6

    data class Tier(
        val usd: Int,
        val wholeSkr: BigInteger,
        /** `wholeSkr × 10^decimals` — what `publish_chip_set` carries. */
        val baseUnits: ULong,
    )

    fun parseRate(text: String): BigDecimal = runCatching { BigDecimal(text.trim()) }.getOrNull() ?: throw ChipPricingException("--rate must be a number of USD per SKR, got '$text'")

    /**
     * The four tiers at [usdPerSkr]. Refuses a rate that is not positive, an
     * amount that does not fit a `u64`, and a set that is not strictly
     * increasing — at a high enough rate two tiers round onto the same amount,
     * and the program rejects that (`InvalidChipSet`, 6011).
     */
    fun price(usdPerSkr: BigDecimal, decimals: Int = DEFAULT_SKR_DECIMALS): List<Tier> {
        if (usdPerSkr.signum() <= 0) throw ChipPricingException("--rate must be above zero, got ${usdPerSkr.toPlainString()}")
        if (decimals !in 0..MAX_DECIMALS) throw ChipPricingException("--decimals must be 0..$MAX_DECIMALS, got $decimals")

        val step = BigDecimal(STEP_WHOLE_SKR)
        val scale = BigInteger.TEN.pow(decimals)
        val tiers =
            USD_TIERS.map { usd ->
                val steps = BigDecimal(usd).divide(usdPerSkr.multiply(step), 0, RoundingMode.FLOOR).toBigIntegerExact()
                val whole = (steps * BigInteger.valueOf(STEP_WHOLE_SKR.toLong())).max(BigInteger.valueOf(MIN_WHOLE_SKR.toLong()))
                val units = whole * scale
                if (units.bitLength() > 64) {
                    throw ChipPricingException("$$usd at ${usdPerSkr.toPlainString()} USD/SKR is $whole SKR, too large for a u64 amount")
                }
                Tier(usd, whole, units.toString().toULong())
            }
        tiers.zipWithNext().firstOrNull { (a, b) -> b.baseUnits <= a.baseUnits }?.let { (a, b) ->
            throw ChipPricingException(
                "at ${usdPerSkr.toPlainString()} USD/SKR the $${a.usd} and $${b.usd} tiers both price at ${b.wholeSkr} SKR; " +
                    "a chip set must be strictly increasing (InvalidChipSet, 6011)",
            )
        }
        return tiers
    }

    private const val MAX_DECIMALS = 19

    /**
     * The command (contracts/crank-cli.md). Prices the set, then:
     *
     * - with the Operator's key, builds and signs `publish_chip_set` —
     *   `--dry-run` simulates it, otherwise it is sent and confirmed;
     * - with `--dry-run` and no key, prints the priced set and stops there.
     *
     * Before signing it reads `Config` from the cluster: the key must be
     * `Config.operator` (the program would refuse anyone else with 6001, but
     * saying so here costs no fee), and the amounts are scaled by the mint's
     * own decimals. `--decimals` is only a cross-check against the mint.
     *
     * Not retried: every publish is a new version (FR-055a), so a send whose
     * confirmation never arrives is reported with its signature to check,
     * never sent again.
     */
    suspend fun publish(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        loadKeypair: (path: String, role: String) -> Keypair = Keypair::load,
        pause: suspend (Long) -> Unit = { delay(it) },
    ): JsonObject {
        checkArgs(args)
        val rateText = args.option("rate") ?: configError("chips publish needs --rate <usd-per-skr>")
        val rate = pricing { parseRate(rateText) }
        val askedDecimals = args.option("decimals")?.let { it.toIntOrNull() ?: configError("--decimals must be a whole number, got $it") }

        val operatorPath = config.operatorKeypair
        if (operatorPath == null) {
            if (!args.dryRun) config.requireOperator()
            // A dry run without the key can still show the set; it cannot build
            // the transaction, which needs the signer.
            val tiers = pricing { price(rate, askedDecimals ?: DEFAULT_SKR_DECIMALS) }
            return summary(rate, askedDecimals ?: DEFAULT_SKR_DECIMALS, tiers, dryRun = true) {
                put("simulated", false)
                put("sent", false)
                put("note", "set HOLDOUT_OPERATOR_KEYPAIR to build and simulate the transaction")
            }
        }
        val operator = loadKeypair(operatorPath, "operator")

        val configAddress = Pdas.config(config.programId)
        val onChain =
            when (val read = rpc.account(configAddress)) {
                is Rpc.Answer.Refused -> failed("could not read Config at $configAddress: ${read.message}")
                is Rpc.Answer.Unanswered -> failed("could not read Config at $configAddress: ${read.message}")
                is Rpc.Answer.Ok ->
                    read.value?.let {
                        runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: configError("the account at $configAddress is not a Config")
                    } ?: configError("there is no Config at $configAddress; the program is not initialized (crank init)")
            }
        if (onChain.operator != operator.publicKey) {
            configError("HOLDOUT_OPERATOR_KEYPAIR is ${operator.publicKey}, but Config.operator is ${onChain.operator}; only the Operator may publish (6001)")
        }
        val mintDecimals =
            rpc.accountData(onChain.skrMint)?.takeIf { it.size >= MINT_SIZE }?.let { it[MINT_DECIMALS].toInt() and 0xFF }
                ?: failed("could not read the SKR mint ${onChain.skrMint}")
        if (askedDecimals != null && askedDecimals != mintDecimals) {
            configError("--decimals $askedDecimals disagrees with the SKR mint, which has $mintDecimals")
        }
        val tiers = pricing { price(rate, mintDecimals) }

        val blockhash = rpc.latestBlockhash() ?: failed("could not read a recent blockhash from ${config.rpcUrl}")
        val message =
            Message
                .Builder()
                .addInstruction(Instructions.publishChipSet(config.programId, operator.publicKey, configAddress, tiers.map { it.baseUnits }))
                .setRecentBlockhash(blockhash)
                .build()
        val signed = operator.sign(message.serialize())
        val transaction = Transaction(listOf(signed), message).serialize()
        // A transaction's ID is its first signature, known before it is sent —
        // so even a send that gets no answer can say what to look up.
        val expectedSignature = Base58.encodeToString(signed)

        if (args.dryRun) {
            val simulation =
                when (val answer = rpc.simulate(transaction)) {
                    is Rpc.Answer.Refused -> failed("simulating publish_chip_set was refused: ${answer.message}")
                    is Rpc.Answer.Unanswered -> failed("could not simulate publish_chip_set: ${answer.message}")
                    is Rpc.Answer.Ok -> answer.value
                }
            simulation.error?.let { failed("publish_chip_set would fail: $it; ${simulation.logs.lastOrNull().orEmpty()}") }
            return summary(rate, mintDecimals, tiers, dryRun = true) {
                put("operator", operator.publicKey.base58())
                put("simulated", true)
                put("sent", false)
                put("nextVersion", onChain.chipVersion + 1)
            }
        }

        val signature =
            when (val answer = rpc.send(transaction)) {
                is Rpc.Answer.Refused -> failed("publish_chip_set was refused and nothing was published: ${answer.message}")
                is Rpc.Answer.Unanswered ->
                    failed(
                        "sent publish_chip_set $expectedSignature but got no answer (${answer.message}); it may have landed. " +
                            "Check that signature before publishing again, since a second publish is a new version",
                    )
                is Rpc.Answer.Ok -> answer.value
            }
        repeat(CONFIRM_ATTEMPTS) {
            when (val landing = rpc.landing(signature)) {
                Rpc.Landing.Confirmed -> {
                    // What actually landed, not what the read before signing
                    // implied: another publish may have landed in between.
                    val after = rpc.accountData(configAddress)?.let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() }
                    return summary(rate, mintDecimals, tiers, dryRun = false) {
                        put("operator", operator.publicKey.base58())
                        put("sent", true)
                        put("signature", signature)
                        if (after != null) {
                            put("version", after.chipVersion)
                        } else {
                            put("version", JsonNull)
                            put("note", "confirmed, but Config could not be read back; run crank status for the version")
                        }
                    }
                }
                is Rpc.Landing.Failed -> failed("publish_chip_set $signature landed and failed: ${landing.error}")
                Rpc.Landing.Pending, null -> pause(CONFIRM_PAUSE_MILLIS)
            }
        }
        failed(
            "sent publish_chip_set $signature but it was not confirmed within ${CONFIRM_ATTEMPTS * CONFIRM_PAUSE_MILLIS / 1000} s; " +
                "check that signature before publishing again, since a second publish is a new version",
        )
    }

    /**
     * The options this command takes, checked before the environment is read
     * so a typo is reported as a typo (`runCrank` calls this first).
     */
    fun checkArgs(args: Args) {
        if ("auto" in args.flags || args.option("if-drift") != null) {
            configError("--auto and --if-drift are the Phase 2 weekly check (FR-056, FR-057); this build publishes from --rate only")
        }
        args.allow("rate", "decimals")
    }

    /** How long a send is watched for: 30 checks, 2 s apart. */
    const val CONFIRM_ATTEMPTS = 30
    const val CONFIRM_PAUSE_MILLIS = 2_000L

    /** An SPL mint is 82 bytes; `decimals` is the byte after the 36-byte authority and the 8-byte supply. */
    private const val MINT_SIZE = 82
    private const val MINT_DECIMALS = 44

    private inline fun <T> pricing(block: () -> T): T = try {
        block()
    } catch (e: ChipPricingException) {
        configError(e.message ?: "invalid rate")
    }

    private fun summary(
        rate: BigDecimal,
        decimals: Int,
        tiers: List<Tier>,
        dryRun: Boolean,
        extra: JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        put("command", "chips publish")
        put("dryRun", dryRun)
        put("rate", rate.toPlainString())
        put("decimals", decimals)
        putJsonArray("tiers") {
            tiers.forEach { t ->
                addJsonObject {
                    put("usd", t.usd)
                    put("skr", JsonPrimitive(t.wholeSkr))
                    put("amount", JsonPrimitive(t.baseUnits.toBig()))
                }
            }
        }
        putJsonArray("amounts") { tiers.forEach { add(JsonPrimitive(it.baseUnits.toBig())) } }
        extra()
    }
}

internal fun ULong.toBig(): BigInteger = BigInteger(toString())
