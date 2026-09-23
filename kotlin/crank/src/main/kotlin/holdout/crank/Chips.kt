package holdout.crank

import kotlinx.serialization.json.JsonObject
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
     * The command. Prices the set and prints it; with `--dry-run` that is the
     * whole job.
     *
     * **Sending waits on T030.** Building `publish_chip_set` needs the
     * instruction builders and PDA derivation in `kotlin/core` (T030), which do
     * not exist yet. Until they do, a run without `--dry-run` checks the
     * Operator key is configured, prints the amounts it would publish, and
     * stops with [EXIT_CONFIG] having sent nothing.
     */
    fun publish(args: Args, config: CrankConfig): JsonObject {
        if ("auto" in args.flags || args.option("if-drift") != null) {
            configError("--auto and --if-drift are the Phase 2 weekly check (FR-056, FR-057); this build publishes from --rate only")
        }
        args.allow("rate", "decimals")
        val rateText = args.option("rate") ?: configError("chips publish needs --rate <usd-per-skr>")
        val decimals =
            args.option("decimals")?.let { it.toIntOrNull() ?: configError("--decimals must be a whole number, got $it") }
                ?: DEFAULT_SKR_DECIMALS
        val rate: BigDecimal
        val tiers: List<Tier>
        try {
            rate = parseRate(rateText)
            tiers = price(rate, decimals)
        } catch (e: ChipPricingException) {
            configError(e.message ?: "invalid rate")
        }

        if (!args.dryRun) {
            config.requireOperator()
            configError(
                "priced ${tiers.joinToString(" / ") { "${it.wholeSkr}" }} SKR, but sending publish_chip_set waits on T030's " +
                    "instruction builders; nothing was sent; rerun with --dry-run to see the set",
            )
        }

        return buildJsonObject {
            put("command", "chips publish")
            put("dryRun", true)
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
            put("sent", false)
            put("note", "simulating publish_chip_set waits on T030's instruction builders")
        }
    }
}

internal fun ULong.toBig(): BigInteger = BigInteger(toString())
