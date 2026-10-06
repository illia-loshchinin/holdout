package holdout.crank

import holdout.core.ConfigSnapshot
import holdout.core.HoldoutDay
import holdout.core.Instructions
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

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
        rateSource: (CrankConfig) -> RateSource = { JupiterRate.from(it) },
    ): JsonObject {
        checkArgs(args)
        if ("auto" in args.flags) return weekly(args, config, rpc, loadKeypair, pause, rateSource(config))
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
        val onChain = readConfig(rpc, configAddress)
        requireOperatorKey(operator, onChain)
        val mintDecimals = mintDecimals(rpc, onChain)
        if (askedDecimals != null && askedDecimals != mintDecimals) {
            configError("--decimals $askedDecimals disagrees with the SKR mint, which has $mintDecimals")
        }
        val tiers = pricing { price(rate, mintDecimals) }
        return sendSet(args, config, rpc, operator, configAddress, onChain, rate, mintDecimals, tiers, pause) { }
    }

    /**
     * Signs and sends `publish_chip_set` with [tiers] — `--dry-run` only
     * simulates — and reports what landed. Not retried: every publish is a new
     * version (FR-055a), so a send whose confirmation never arrives is reported
     * with its signature to check, never sent again. [extra] adds the caller's
     * own fields to the report.
     */
    private suspend fun sendSet(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        operator: Keypair,
        configAddress: com.solana.publickey.SolanaPublicKey,
        onChain: ConfigSnapshot,
        rate: BigDecimal,
        mintDecimals: Int,
        tiers: List<Tier>,
        pause: suspend (Long) -> Unit,
        extra: JsonObjectBuilder.(sent: Boolean) -> Unit,
    ): JsonObject {
        val instruction = Instructions.publishChipSet(config.programId, operator.publicKey, configAddress, tiers.map { it.baseUnits })
        // Not retried: every publish is a new version (FR-055a).
        val signature =
            signAndSend(
                rpc,
                "publish_chip_set",
                listOf(instruction),
                listOf(operator),
                args.dryRun,
                pause,
                refusedMeans = "nothing was published",
                beforeRetrying = "Check that signature before publishing again, since a second publish is a new version",
            )
        if (signature == null) {
            return summary(rate, mintDecimals, tiers, dryRun = true) {
                put("operator", operator.publicKey.base58())
                put("simulated", true)
                put("sent", false)
                put("nextVersion", onChain.chipVersion + 1)
                extra(false)
            }
        }
        // What actually landed, not what the read before signing implied:
        // another publish may have landed in between.
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
            extra(true)
        }
    }

    private suspend fun readConfig(rpc: Rpc, configAddress: com.solana.publickey.SolanaPublicKey): ConfigSnapshot = when (val read = rpc.account(configAddress)) {
        is Rpc.Answer.Refused -> failed("could not read Config at $configAddress: ${read.message}")
        is Rpc.Answer.Unanswered -> failed("could not read Config at $configAddress: ${read.message}")
        is Rpc.Answer.Ok ->
            read.value?.let {
                runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: configError("the account at $configAddress is not a Config")
            } ?: configError("there is no Config at $configAddress; the program is not initialized (crank init)")
    }

    private fun requireOperatorKey(operator: Keypair, onChain: ConfigSnapshot) {
        if (onChain.operator != operator.publicKey) {
            configError("HOLDOUT_OPERATOR_KEYPAIR is ${operator.publicKey}, but Config.operator is ${onChain.operator}; only the Operator may publish (6001)")
        }
    }

    private suspend fun mintDecimals(rpc: Rpc, onChain: ConfigSnapshot): Int = rpc.accountData(onChain.skrMint)?.takeIf { it.size >= MINT_SIZE }?.let { it[MINT_DECIMALS].toInt() and 0xFF }
        ?: failed("could not read the SKR mint ${onChain.skrMint}")

    // ---- the weekly check (T100, FR-056, FR-057, research R15) ---------------------------

    /** Where the weekly check gets the SKR rate. Null is "no rate": nothing is published (FR-057). */
    fun interface RateSource {
        suspend fun usdPerSkr(): BigDecimal?

        /** Why the last ask gave no rate, for the report — so a refused key never reads as a quiet week. */
        val problem: String? get() = null
    }

    /** Jupiter answered, but not with 200. */
    class JupiterRefused(val status: Int) : Exception("Jupiter answered HTTP $status")

    /**
     * The Jupiter Price API (v3), by the **mainnet** SKR mint — the one rate
     * source, a trusted off-chain input named as such in the README
     * (constitution III, XI). Keyless works at Jupiter's free rate limit, far
     * above one call a week; [apiKey] goes in `x-api-key` when set. Any failure
     * — no answer, a non-200, an unusable body — is no rate.
     */
    class JupiterRate(
        val url: String = DEFAULT_URL,
        val mint: String = SKR_MINT,
        val apiKey: String? = null,
        private val get: suspend (url: String, headers: Map<String, String>) -> String? = ::httpGet,
    ) : RateSource {
        override var problem: String? = null
            private set

        override suspend fun usdPerSkr(): BigDecimal? {
            problem = null
            val headers = apiKey?.let { mapOf("x-api-key" to it) }.orEmpty()
            val body =
                try {
                    get("$url?ids=$mint", headers)
                } catch (e: JupiterRefused) {
                    problem = e.message + if (e.status == 401 || e.status == 403) {
                        if (apiKey == null) ": Jupiter now wants a key — set HOLDOUT_JUPITER_API_KEY" else ": HOLDOUT_JUPITER_API_KEY was refused"
                    } else {
                        ""
                    }
                    return null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    problem = "Jupiter could not be reached: ${e.message ?: e::class.simpleName}"
                    return null
                } ?: return null.also { problem = "Jupiter gave no answer" }
            return parse(body, mint).also { if (it == null) problem = "Jupiter's answer had no usable usdPrice for $mint" }
        }

        companion object {
            const val DEFAULT_URL = "https://api.jup.ag/price/v3"

            /** SKR on Solana mainnet — what the market prices; the devnet mint is a test token with no price. */
            const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"

            fun from(config: CrankConfig) = JupiterRate(url = config.jupiterPriceUrl ?: DEFAULT_URL, apiKey = config.jupiterApiKey)

            /** `{"<mint>": {"usdPrice": 0.0213, …}}` → the price, exactly; anything else, or a price that is not above zero, is null. */
            fun parse(body: String, mint: String): BigDecimal? = runCatching {
                val price = Json.parseToJsonElement(body).jsonObject[mint]?.jsonObject?.get("usdPrice")?.jsonPrimitive
                price?.takeIf { !it.isString }?.content?.let(::BigDecimal)
            }.getOrNull()?.takeIf { it.signum() > 0 }

            private val client: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build() }

            private suspend fun httpGet(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
                val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET()
                headers.forEach { (k, v) -> request.header(k, v) }
                val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) throw JupiterRefused(response.statusCode())
                response.body()
            }
        }
    }

    /** The latest Monday 05:00 UTC at or before [now] — the weekly boundary (FR-056). */
    fun latestWeeklyBoundary(now: Long): Long {
        val day = Math.floorDiv(now - HoldoutDay.BOUNDARY_OFFSET_SECONDS, HoldoutDay.SECONDS_PER_DAY)
        // 1970-01-05, day 4, was a Monday.
        val sinceMonday = Math.floorMod(day + 3, 7L)
        return (day - sinceMonday) * HoldoutDay.SECONDS_PER_DAY + HoldoutDay.BOUNDARY_OFFSET_SECONDS
    }

    /**
     * `--auto`: the Operator's weekly re-pricing, run by the Operator's own
     * scheduler after Monday 05:00 UTC — never by `crank run`, so the
     * Operator key is loaded only for this. In order: nothing to do if a set
     * was already published since the latest boundary (no rate fetched); no
     * rate, nothing published, and the report says why (FR-057); otherwise all
     * four tiers are re-priced at the rate and published — every week, with no
     * threshold, even when the amounts come out the same (FR-056). Every way
     * out exits 0 except a failure to read or send.
     */
    private suspend fun weekly(
        args: Args,
        config: CrankConfig,
        rpc: Rpc,
        loadKeypair: (path: String, role: String) -> Keypair,
        pause: suspend (Long) -> Unit,
        source: RateSource,
    ): JsonObject {
        val operatorPath = config.operatorKeypair
        if (operatorPath == null && !args.dryRun) config.requireOperator()
        val operator = operatorPath?.let { loadKeypair(it, "operator") }

        val configAddress = Pdas.config(config.programId)
        val onChain = readConfig(rpc, configAddress)
        operator?.let { requireOperatorKey(it, onChain) }
        val now = rpc.chainTime()?.unixSeconds ?: failed("could not read the chain's time")
        val boundary = latestWeeklyBoundary(now)

        fun report(published: Boolean, reason: String?, more: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
            put("command", "chips publish --auto")
            put("dryRun", args.dryRun)
            put("published", published)
            reason?.let { put("reason", it) }
            put("week", Instant.ofEpochSecond(boundary).toString())
            put("lastPublished", Instant.ofEpochSecond(onChain.chipPublishedAt).toString())
            more()
        }

        if (onChain.chipPublishedAt >= boundary) return report(false, "already published this week")
        val rate = source.usdPerSkr() ?: return report(false, "no rate") { source.problem?.let { put("detail", it) } }
        val decimals = mintDecimals(rpc, onChain)
        // A rate the four tiers cannot be priced at (two tiers collapsing onto one
        // amount, or one too large for a u64) is external data, not a mistake in
        // the command: the current set stays and the week is reported, like no rate.
        val tiers =
            try {
                price(rate, decimals)
            } catch (e: ChipPricingException) {
                return report(false, "unusable rate") {
                    put("rate", rate.toPlainString())
                    put("detail", e.message ?: "the rate cannot price four increasing tiers")
                }
            }
        val autoFields: JsonObjectBuilder.(Boolean) -> Unit = { sent ->
            put("command", "chips publish --auto")
            put("published", sent)
            put("week", Instant.ofEpochSecond(boundary).toString())
            putJsonArray("previous") { onChain.chipAmounts.forEach { add(JsonPrimitive(it.toBig())) } }
        }
        if (operator == null) {
            return summary(rate, decimals, tiers, dryRun = true) {
                put("simulated", false)
                put("sent", false)
                put("note", "set HOLDOUT_OPERATOR_KEYPAIR to build and simulate the transaction")
                autoFields(false)
            }
        }
        return sendSet(args, config, rpc, operator, configAddress, onChain, rate, decimals, tiers, pause, autoFields)
    }

    /**
     * The options this command takes, checked before the environment is read
     * so a typo is reported as a typo (`runCrank` calls this first).
     */
    fun checkArgs(args: Args) {
        if ("auto" in args.flags && args.option("rate") != null) configError("--auto reads the rate from Jupiter; it takes no --rate")
        if ("auto" in args.flags && args.option("decimals") != null) configError("--auto reads the decimals from the SKR mint; it takes no --decimals")
        args.allow("rate", "decimals", "auto")
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
