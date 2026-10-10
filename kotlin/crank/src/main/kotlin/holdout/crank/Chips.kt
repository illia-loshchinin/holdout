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
        val askedDeadline = args.option("valid-until")?.let(::parseValidUntil)

        // A set published by hand is refused from chain time + 10 minutes, unless
        // the Operator gives another deadline (FR-055a): a signed set left pending
        // never lands much later than the Operator expects. Derived the same way
        // with or without the key, so a dry run says what a real run would sign.
        fun deadlineAt(chainNow: Long): Long {
            val validUntil = askedDeadline ?: (chainNow + HAND_DEADLINE_SECONDS)
            if (validUntil <= chainNow) {
                configError("--valid-until ${Instant.ofEpochSecond(validUntil)} is not after the chain's time, ${Instant.ofEpochSecond(chainNow)}; the program would refuse the set (6014)")
            }
            return validUntil
        }

        val operatorPath = config.operatorKeypair
        if (operatorPath == null) {
            if (!args.dryRun) config.requireOperator()
            // A dry run without the key can still show the set and its deadline;
            // it cannot build the transaction, which needs the signer. It works
            // offline too: with no answer from the chain the deadline goes unchecked
            // and the report says so.
            val tiers = pricing { price(rate, askedDecimals ?: DEFAULT_SKR_DECIMALS) }
            val chainNow = rpc.chainTime()?.unixSeconds
            val validUntil = chainNow?.let(::deadlineAt) ?: askedDeadline
            return summary(rate, askedDecimals ?: DEFAULT_SKR_DECIMALS, tiers, dryRun = true) {
                put("simulated", false)
                put("sent", false)
                put("validUntil", validUntil?.let { Instant.ofEpochSecond(it).toString() })
                if (chainNow == null) put("deadlineNote", "the chain's time could not be read, so the deadline is unchecked; a real run signs chain time + 10 minutes unless --valid-until")
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
        val validUntil = deadlineAt(rpc.chainTime()?.unixSeconds ?: failed("could not read the chain's time"))
        return sendSet(args, config, rpc, operator, configAddress, onChain, rate, mintDecimals, tiers, pause, validUntil, extra = { })
    }

    /** How long after the chain's time a set published by hand is refused, by default (FR-055a). */
    const val HAND_DEADLINE_SECONDS = 600L

    fun parseValidUntil(text: String): Long = runCatching { Instant.parse(text.trim()).epochSecond }.getOrNull()
        ?: configError("--valid-until must be an ISO-8601 instant such as 2026-10-12T05:00:00Z, got '$text'")

    /** The program refused the set: it reached its deadline before it landed (`ChipSetExpired`, 6014). */
    class ChipSetExpiredException(
        val validUntil: Long,
    ) : CrankException(EXIT_FAILED, "the chip set reached its deadline, ${Instant.ofEpochSecond(validUntil)}, before it landed (6014); nothing was published")

    /** 6014 as the RPC reports it: in a refused preflight (hex) or a landed failure (decimal). */
    private fun isChipSetExpired(message: String?): Boolean {
        val m = message?.lowercase() ?: return false
        return "0x177e" in m || "custom\":6014" in m || "custom: 6014" in m || "custom(6014)" in m || "chipsetexpired" in m
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
        validUntil: Long,
        extra: JsonObjectBuilder.(sent: Boolean) -> Unit,
        afterSigning: suspend () -> Unit = {},
    ): JsonObject {
        val instruction = Instructions.publishChipSet(config.programId, operator.publicKey, configAddress, tiers.map { it.baseUnits }, validUntil)
        // Not retried: every publish is a new version (FR-055a).
        val signature =
            try {
                signAndSend(
                    rpc,
                    "publish_chip_set",
                    listOf(instruction),
                    listOf(operator),
                    args.dryRun,
                    pause,
                    refusedMeans = "nothing was published",
                    beforeRetrying = "Check that signature before publishing again, since a second publish is a new version",
                    afterSigning = afterSigning,
                )
            } catch (e: CrankException) {
                if (isChipSetExpired(e.message)) throw ChipSetExpiredException(validUntil)
                throw e
            }
        val deadline = Instant.ofEpochSecond(validUntil).toString()
        if (signature == null) {
            return summary(rate, mintDecimals, tiers, dryRun = true) {
                put("operator", operator.publicKey.base58())
                put("simulated", true)
                put("sent", false)
                put("nextVersion", onChain.chipVersion + 1)
                put("validUntil", deadline)
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
            put("validUntil", deadline)
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

    /** How long before each weekly boundary the publish window opens (FR-056). */
    const val PUBLISH_WINDOW_SECONDS = 4 * 3_600L

    /**
     * The publish window that [now] is in, or the next one: the 4 hours before
     * the next Monday 05:00 UTC, up to but not including it. The cohorts that
     * open at that boundary copy the set published in it (FR-056).
     */
    fun publishWindow(now: Long): LongRange {
        val closes = latestWeeklyBoundary(now) + 7 * HoldoutDay.SECONDS_PER_DAY
        return closes - PUBLISH_WINDOW_SECONDS until closes
    }

    /**
     * No send starts this close to the window's end: a blockhash lives about
     * this long, and the send is watched for 60 s, so a set sent later could
     * land after 05:00 — after the cohorts opening then had copied the old
     * one — and count as next week's (FR-056).
     */
    const val SEND_MARGIN_SECONDS = 90L

    /** Whether a publish started at [now] can still land inside [window]. */
    private fun canSendIn(window: LongRange, now: Long) = now >= window.first && now <= window.last - SEND_MARGIN_SECONDS

    /**
     * `--auto`: the Operator's weekly re-pricing, run every hour by the
     * Operator's own scheduler, in any time zone — never by `crank run`, so the
     * Operator key is loaded only for this. In order, by chain time: nothing to
     * do outside the publish window, or if a set was already published in it
     * (no rate fetched); no rate, nothing published, and the report says why
     * (FR-057) — the next hourly run in the window tries again; otherwise all
     * four tiers are re-priced at the rate and published — every week, with no
     * threshold, even when the amounts come out the same (FR-056). Publishing
     * in the 4 hours *before* the boundary is what makes the cohort opening at
     * it lock a fresh rate: a set published after 05:00 reaches only the next
     * week's cohort. Every way out exits 0 except a failure to read or send.
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
        val window = publishWindow(now)
        val insideWindow = now in window

        fun JsonObjectBuilder.putWindow() {
            put("windowOpens", Instant.ofEpochSecond(window.first).toString())
            put("windowCloses", Instant.ofEpochSecond(window.last + 1).toString())
            put("validUntil", Instant.ofEpochSecond(window.last + 1).toString())
            put("sendCutoff", Instant.ofEpochSecond(window.last + 1 - SEND_MARGIN_SECONDS).toString())
        }

        val alreadyThisWindow = onChain.chipPublishedAt in window

        // The state at chain time [at]: the time read last, so a report never
        // contradicts the guard that ended the run — nor the publish it just sent.
        fun JsonObjectBuilder.putWindowState(at: Long, sentNow: Boolean = false) {
            val already = alreadyThisWindow || sentNow
            put("insideWindow", at in window)
            put("alreadyPublishedThisWindow", already)
            // What a real run at this chain time would do — a dry run goes on past every guard.
            put("wouldPublishNow", at in window && !already && canSendIn(window, at))
        }

        fun report(published: Boolean, reason: String?, at: Long = now, more: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
            put("command", "chips publish --auto")
            put("dryRun", args.dryRun)
            put("published", published)
            reason?.let { put("reason", it) }
            putWindow()
            putWindowState(at)
            put("lastPublished", Instant.ofEpochSecond(onChain.chipPublishedAt).toString())
            more()
        }

        // A dry run is the Operator's check of the rate source and the set, so
        // it goes on past the window guard any day; a real run stops here (FR-056).
        if (!args.dryRun) {
            if (!insideWindow) return report(false, "outside the publish window")
            if (alreadyThisWindow) return report(false, "already published in this window")
            if (!canSendIn(window, now)) return report(false, TOO_LATE)
        }
        // Every slow step — Jupiter, the mint, the blockhash — is followed by a
        // fresh read of the chain's time, and the report gives the state as of
        // that read: what a real run starting with this one would do.
        suspend fun chainNow() = rpc.chainTime()?.unixSeconds ?: failed("could not read the chain's time")
        val rate = source.usdPerSkr() ?: return report(false, "no rate", at = chainNow()) { source.problem?.let { put("detail", it) } }
        val decimals = mintDecimals(rpc, onChain)
        var stateAt = chainNow()
        // A rate the four tiers cannot be priced at (two tiers collapsing onto one
        // amount, or one too large for a u64) is external data, not a mistake in
        // the command: the current set stays and the week is reported, like no rate.
        val tiers =
            try {
                price(rate, decimals)
            } catch (e: ChipPricingException) {
                return report(false, "unusable rate", at = stateAt) {
                    put("rate", rate.toPlainString())
                    put("detail", e.message ?: "the rate cannot price four increasing tiers")
                }
            }
        // A real run that is already too late stops before reading a blockhash.
        if (!args.dryRun && !canSendIn(window, stateAt)) return report(false, TOO_LATE, at = stateAt) { put("rate", rate.toPlainString()) }
        val autoFields: JsonObjectBuilder.(Boolean) -> Unit = { sent ->
            put("command", "chips publish --auto")
            put("published", sent)
            putWindow()
            putWindowState(stateAt, sentNow = sent)
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
        // The last check sits at the send itself, after the blockhash is read:
        // a transaction expires about 90 s after its blockhash, so one that
        // passes here before the cutoff should not land after 05:00. A dry run
        // reads the time at the same point, so it reports what a real run would.
        val lastCheck: suspend () -> Unit = {
            stateAt = chainNow()
            if (!args.dryRun && !canSendIn(window, stateAt)) throw TooLate(stateAt)
        }
        // The program refuses the set from the window's close (6014), so even a
        // send that outlives the cutoff above never lands after the boundary.
        val validUntil = window.last + 1
        return try {
            sendSet(args, config, rpc, operator, configAddress, onChain, rate, decimals, tiers, pause, validUntil, autoFields, lastCheck)
        } catch (e: TooLate) {
            report(false, TOO_LATE, at = e.at) { put("rate", rate.toPlainString()) }
        } catch (e: ChipSetExpiredException) {
            report(false, TOO_LATE, at = chainNow()) {
                put("rate", rate.toPlainString())
                put("detail", e.message)
            }
        }
    }

    /** The cutoff passed between signing and sending; nothing was sent. */
    private class TooLate(val at: Long) : Exception("too late to send")

    private const val TOO_LATE = "too late to land in the publish window"

    /**
     * The options this command takes, checked before the environment is read
     * so a typo is reported as a typo (`runCrank` calls this first).
     */
    fun checkArgs(args: Args) {
        if ("auto" in args.flags && args.option("rate") != null) configError("--auto reads the rate from Jupiter; it takes no --rate")
        if ("auto" in args.flags && args.option("decimals") != null) configError("--auto reads the decimals from the SKR mint; it takes no --decimals")
        if ("auto" in args.flags && args.option("valid-until") != null) configError("--auto signs the publish window's close; it takes no --valid-until")
        args.allow("rate", "decimals", "auto", "valid-until")
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
