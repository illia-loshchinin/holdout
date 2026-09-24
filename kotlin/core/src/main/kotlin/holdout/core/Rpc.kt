package holdout.core

import com.funkatronics.encoders.Base58
import com.solana.networking.KtorNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpccore.JsonRpc20Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Reading the cluster (research R8, T034).
 *
 * Shared by the app and the crank, like everything else in this module, so
 * there is one RPC path and not two that drift (R12).
 *
 * Two kinds of read: the cluster's own clock, which the Daily 25 needs, and
 * program accounts, which every cohort screen needs. A cohort's roster is
 * **one** `getProgramAccounts` call — a `memcmp` on the cohort key and a
 * `dataSlice` holding only the fields the figures use ([AccountQueries]) —
 * refreshed every 30 seconds while a cohort screen is visible
 * ([cohortRefreshes]). No Holdout server and no indexer: the RPC node is chain
 * infrastructure, and it decides and ranks nothing (FR-060, FR-065).
 *
 * Every read returns null when it cannot answer — cluster unreachable, or an
 * RPC error such as a node that does not index program accounts. Null means
 * "unknown", never "empty": a roster that failed to load is not a cohort
 * everyone left.
 */
class Rpc(
    private val endpoint: String = DEVNET,
    driver: Rpc20Driver = Rpc20Driver(endpoint, KtorNetworkDriver()),
) {
    private val rpc = driver

    companion object {
        /**
         * Devnet, everywhere. Nothing in this build touches mainnet or real
         * value, and there is no code path here that could.
         */
        const val DEVNET = "https://api.devnet.solana.com"

        /**
         * Account reads ask for `confirmed`: a roster a few seconds fresher than
         * `finalized`, and nothing here moves money on what it reads — the
         * program re-checks every rule against its own state (constitution I).
         */
        const val READ_COMMITMENT = "confirmed"

        /** The `getProgramAccounts` params for [query] — split out so the exact request is testable. */
        fun programAccountsParams(programId: SolanaPublicKey, query: ProgramAccountsQuery): JsonArray = buildJsonArray {
            add(JsonPrimitive(programId.base58()))
            add(
                buildJsonObject {
                    // Base64: compact, and an encoding a dataSlice may be combined with
                    // (`jsonParsed` may not).
                    put("encoding", "base64")
                    put("commitment", READ_COMMITMENT)
                    put("filters", JsonArray(query.filters.map { it.toJson() }))
                    query.dataSlice?.let { slice ->
                        putJsonObject("dataSlice") {
                            put("offset", slice.offset)
                            put("length", slice.length)
                        }
                    }
                },
            )
        }
    }

    data class ChainTime(val unixSeconds: Long, val slot: Long)

    /**
     * The cluster's time, as two calls: the current slot, then that slot's
     * block time.
     *
     * There is no single RPC for "what time does the chain think it is", and
     * the device's own clock is not an answer — every deadline in Holdout is
     * judged by chain time (FR-017), and a phone's clock can be wrong or moved
     * on purpose. Two round trips a few times an hour is the price; [ChainClock]
     * carries the value forward between them on monotonic time.
     *
     * Returns null when the cluster cannot be reached. The caller shows that it
     * does not know rather than guessing — and the Daily 25 keeps working
     * either way, because counting reps needs no connection at all (FR-024b).
     */
    suspend fun chainTime(): ChainTime? {
        val slot = request("getSlot") ?: return null
        val seconds = request("getBlockTime", JsonArray(listOf(JsonPrimitive(slot))))
            ?: return null
        return ChainTime(seconds, slot)
    }

    /** One program account: its address and its data, or the requested slice of it. */
    class ProgramAccount(val address: SolanaPublicKey, val data: ByteArray)

    /**
     * Every account of the program matching [query] — one call, however many
     * there are. A 1,000-member roster sliced to 51 bytes a member is ~50 KB
     * of account data (R8).
     */
    suspend fun programAccounts(programId: SolanaPublicKey, query: ProgramAccountsQuery): List<ProgramAccount>? {
        val result = call("getProgramAccounts", programAccountsParams(programId, query)) ?: return null
        return runCatching {
            result.jsonArray.map { entry ->
                val o = entry.jsonObject
                ProgramAccount(
                    address = SolanaPublicKey.from(o.getValue("pubkey").jsonPrimitive.content),
                    data = base64Data(o.getValue("account")),
                )
            }
        }.getOrNull()
    }

    /**
     * The roster of one cohort (T034): every `Membership` whose cohort key is
     * [cohort], decoded from the roster slice. A member that fails to decode
     * fails the whole read rather than dropping silently out of the counter.
     */
    suspend fun memberships(programId: SolanaPublicKey, cohort: SolanaPublicKey): List<MembershipSnapshot>? {
        val accounts = programAccounts(programId, AccountQueries.membershipsOf(cohort)) ?: return null
        return runCatching { accounts.map { MembershipSnapshot.decodeRosterSlice(it.data) } }.getOrNull()
    }

    /** Every cohort of the program, whole, keyed by address — what `crank status` lists (T036). */
    suspend fun cohorts(programId: SolanaPublicKey): List<Pair<SolanaPublicKey, CohortSnapshot>>? {
        val accounts = programAccounts(programId, AccountQueries.cohorts()) ?: return null
        return runCatching { accounts.map { it.address to CohortSnapshot.decode(it.data) } }.getOrNull()
    }

    /** One cohort and its roster, the two reads a cohort screen refreshes together. */
    data class CohortRead(
        val address: SolanaPublicKey,
        val cohort: CohortSnapshot,
        val members: List<MembershipSnapshot>,
    )

    suspend fun cohortRead(programId: SolanaPublicKey, cohort: SolanaPublicKey): CohortRead? {
        val data = accountData(cohort) ?: return null
        val snapshot = runCatching { CohortSnapshot.decode(data) }.getOrNull() ?: return null
        val members = memberships(programId, cohort) ?: return null
        return CohortRead(cohort, snapshot, members)
    }

    /**
     * [cohortRead] for [cohort] at once and then every 30 seconds, for as long
     * as the flow is collected (R8). The app collects it only while a cohort
     * screen is visible — with a lifecycle-aware collector, which cancels it
     * when the screen goes and starts it again, with a fresh read, when it
     * comes back. So "refreshed on resume" needs no code of its own.
     */
    fun cohortUpdates(programId: SolanaPublicKey, cohort: SolanaPublicKey): Flow<CohortRead> = cohortRefreshes { cohortRead(programId, cohort) }

    /**
     * An account's data: `Ok(bytes)`, `Ok(null)` when the account does not
     * exist, or a failure when the read itself failed. A caller that reports
     * on the program ("not initialised") needs that distinction — a flaky
     * node must never read as a missing account.
     */
    suspend fun account(address: SolanaPublicKey): Answer<ByteArray?> {
        val params =
            buildJsonArray {
                add(JsonPrimitive(address.base58()))
                add(
                    buildJsonObject {
                        put("encoding", "base64")
                        put("commitment", READ_COMMITMENT)
                    },
                )
            }
        return when (val answer = answer("getAccountInfo", params)) {
            is Answer.Refused -> answer
            is Answer.Unanswered -> answer
            is Answer.Ok -> {
                val value = (answer.value as? JsonObject)?.get("value")
                when {
                    value == null -> Answer.Unanswered("getAccountInfo returned no value: ${answer.value}")
                    value is JsonNull -> Answer.Ok(null)
                    else ->
                        runCatching { Answer.Ok(base64Data(value)) }
                            .getOrElse { Answer.Unanswered("unreadable account data for $address") }
                }
            }
        }
    }

    /**
     * An account's data, or null when it does not exist **or** the cluster
     * could not be reached. Callers that must tell those apart use [account].
     */
    suspend fun accountData(address: SolanaPublicKey): ByteArray? = (account(address) as? Answer.Ok)?.value

    /** Lamports held by [address]; `0` for an account that does not exist. */
    suspend fun balance(address: SolanaPublicKey): Long? {
        val params =
            buildJsonArray {
                add(JsonPrimitive(address.base58()))
                add(buildJsonObject { put("commitment", READ_COMMITMENT) })
            }
        val result = call("getBalance", params) as? JsonObject ?: return null
        return runCatching { result.getValue("value").jsonPrimitive.long }.getOrNull()
    }

    // ---- sending (T062 first; every crank command after it) ---------------

    /** A call that sends or simulates: its result, why the node refused it, or no answer at all. */
    sealed interface Answer<out T> {
        data class Ok<T>(val value: T) : Answer<T>

        /** The node answered no, in its own words — a preflight failure names the program error. */
        data class Refused(val message: String) : Answer<Nothing>

        /**
         * No answer: the connection failed or timed out. For a send this is
         * **not** a refusal — the node may have received the transaction and
         * landed it — so the caller must check before sending again.
         */
        data class Unanswered(val message: String) : Answer<Nothing>
    }

    /** The blockhash a transaction is built on, or null when the cluster cannot be reached. */
    suspend fun latestBlockhash(): String? {
        val params = buildJsonArray { add(buildJsonObject { put("commitment", READ_COMMITMENT) }) }
        val result = call("getLatestBlockhash", params) as? JsonObject ?: return null
        return runCatching {
            result
                .getValue("value")
                .jsonObject
                .getValue("blockhash")
                .jsonPrimitive.content
        }.getOrNull()
    }

    /** What a simulation reports: the error, if any, and the program's logs. */
    data class Simulation(val error: String?, val logs: List<String>)

    /** Runs [transaction] (serialised, signed) against the cluster without landing it. */
    suspend fun simulate(transaction: ByteArray): Answer<Simulation> {
        val params =
            buildJsonArray {
                add(JsonPrimitive(Base64.getEncoder().encodeToString(transaction)))
                add(
                    buildJsonObject {
                        put("encoding", "base64")
                        put("commitment", READ_COMMITMENT)
                        put("sigVerify", true)
                    },
                )
            }
        return when (val answer = answer("simulateTransaction", params)) {
            is Answer.Refused -> answer
            is Answer.Unanswered -> answer
            is Answer.Ok ->
                runCatching {
                    val value = answer.value.jsonObject.getValue("value").jsonObject
                    val err = value["err"]?.takeUnless { it is JsonNull }?.toString()
                    val logs = (value["logs"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                    Answer.Ok(Simulation(err, logs))
                }.getOrElse { Answer.Refused("unreadable simulation result: ${answer.value}") }
        }
    }

    /**
     * Sends [transaction] (serialised, signed) with preflight on, so a
     * transaction the program would refuse comes back refused here, with the
     * reason, instead of landing as a failure. Returns the signature.
     */
    suspend fun send(transaction: ByteArray): Answer<String> {
        val params =
            buildJsonArray {
                add(JsonPrimitive(Base64.getEncoder().encodeToString(transaction)))
                add(
                    buildJsonObject {
                        put("encoding", "base64")
                        put("preflightCommitment", READ_COMMITMENT)
                    },
                )
            }
        return when (val answer = answer("sendTransaction", params)) {
            is Answer.Refused -> answer
            is Answer.Unanswered -> answer
            is Answer.Ok ->
                runCatching { Answer.Ok(answer.value.jsonPrimitive.content) }
                    // The node answered, so it may well have taken the transaction.
                    .getOrElse { Answer.Unanswered("unreadable signature: ${answer.value}") }
        }
    }

    /** Where a sent transaction stands. */
    sealed interface Landing {
        /** Not seen at `confirmed` yet — it may still land. */
        data object Pending : Landing

        data object Confirmed : Landing

        /** It landed and the program refused it. */
        data class Failed(val error: String) : Landing
    }

    /**
     * The status of [signature], or null when the cluster cannot be reached or
     * answers in a shape this does not read — never an exception, because the
     * caller is watching a transaction it has already sent.
     */
    suspend fun landing(signature: String): Landing? {
        val params = buildJsonArray { add(buildJsonArray { add(JsonPrimitive(signature)) }) }
        val result = call("getSignatureStatuses", params) as? JsonObject ?: return null
        return runCatching {
            val status = result.getValue("value").jsonArray[0]
            if (status is JsonNull) return Landing.Pending
            val o = status.jsonObject
            o["err"]?.takeUnless { it is JsonNull }?.let { return Landing.Failed(it.toString()) }
            when (o["confirmationStatus"]?.jsonPrimitive?.content) {
                "confirmed", "finalized" -> Landing.Confirmed
                else -> Landing.Pending
            }
        }.getOrNull()
    }

    private fun base64Data(account: JsonElement): ByteArray = Base64.getDecoder().decode(
        account.jsonObject
            .getValue("data")
            .jsonArray[0]
            .jsonPrimitive.content,
    )

    private suspend fun call(method: String, params: JsonArray): JsonElement? = attempt {
        rpc.makeRequest(
            JsonRpc20Request(method, params, method),
            JsonElement.serializer(),
        ).takeIf { it.error == null }?.result
    }.getOrNull()

    /**
     * Like [call], but keeps the node's reason when it refuses, and tells a
     * refusal apart from no answer at all.
     */
    private suspend fun answer(method: String, params: JsonArray): Answer<JsonElement> = attempt {
        val response = rpc.makeRequest(JsonRpc20Request(method, params, method), JsonElement.serializer())
        response.error?.let { Answer.Refused(it.message) }
            ?: response.result?.let { Answer.Ok(it) }
            ?: Answer.Unanswered("$method returned neither a result nor an error")
    }.getOrElse { Answer.Unanswered("$method got no answer from $endpoint: ${it.message}") }

    private suspend fun request(method: String, params: JsonArray? = null): Long? = attempt {
        rpc.makeRequest(
            JsonRpc20Request(method, params ?: JsonArray(emptyList()), method),
            Long.serializer(),
        ).takeIf { it.error == null }?.result
    }.getOrNull()
}

/**
 * `runCatching` for a call that suspends: any failure becomes a result, except
 * cancellation, which is rethrown. Swallowing it would turn "the screen went
 * away" into "the cluster did not answer", and keep a cancelled loop running.
 */
private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Every 30 seconds while a cohort screen is visible (research R8). */
const val COHORT_REFRESH_MILLIS = 30_000L

/**
 * [load] at once, then every [periodMillis], emitting each read that answered.
 * A failed read (null) emits nothing — the screen keeps the figures it has —
 * and the next attempt waits the full period: a struggling node is not
 * hammered to make up for it.
 *
 * Cold and endless: it runs exactly as long as it is collected, which is what
 * ties it to a screen being visible. [pause] is `delay` except under test.
 */
fun <T : Any> cohortRefreshes(
    periodMillis: Long = COHORT_REFRESH_MILLIS,
    pause: suspend (Long) -> Unit = { delay(it) },
    load: suspend () -> T?,
): Flow<T> = flow {
    while (true) {
        load()?.let { emit(it) }
        pause(periodMillis)
    }
}

/** One `getProgramAccounts` filter. */
sealed interface AccountFilter {
    fun toJson(): JsonObject

    /** Only accounts of exactly this many bytes. */
    data class DataSize(val bytes: Int) : AccountFilter {
        override fun toJson() = buildJsonObject { put("dataSize", bytes) }
    }

    /** Only accounts holding [bytes] at [offset]; sent base58, the encoding every node accepts. */
    class Memcmp(val offset: Int, bytes: ByteArray) : AccountFilter {
        private val value = bytes.copyOf()
        val bytes: ByteArray get() = value.copyOf()

        override fun toJson() = buildJsonObject {
            putJsonObject("memcmp") {
                put("offset", offset)
                put("bytes", Base58.encodeToString(value))
            }
        }
    }
}

/** Which part of each matching account to return. */
data class DataSlice(val offset: Int, val length: Int)

data class ProgramAccountsQuery(val filters: List<AccountFilter>, val dataSlice: DataSlice? = null)

/**
 * The program-account queries Holdout makes, with their offsets taken from
 * [AccountLayout] — so from the program's own structs, pinned by the golden
 * layout bytes.
 *
 * Each query filters on the account's size **and** its discriminator before
 * anything else. The roster slice carries no discriminator of its own to check,
 * so this is the only thing standing between a same-sized account of another
 * type and a misread member.
 */
object AccountQueries {
    /** Every member of [cohort], sliced to the roster fields (R8). */
    fun membershipsOf(cohort: SolanaPublicKey): ProgramAccountsQuery {
        val l = AccountLayout.Membership
        return ProgramAccountsQuery(
            filters =
            listOf(
                AccountFilter.DataSize(l.SIZE),
                AccountFilter.Memcmp(0, l.DISCRIMINATOR),
                AccountFilter.Memcmp(l.COHORT, cohort.bytes),
            ),
            dataSlice = DataSlice(l.ROSTER_SLICE_OFFSET, l.ROSTER_SLICE_LENGTH),
        )
    }

    /** Every cohort, whole. */
    fun cohorts(): ProgramAccountsQuery {
        val l = AccountLayout.Cohort
        return ProgramAccountsQuery(
            filters = listOf(AccountFilter.DataSize(l.SIZE), AccountFilter.Memcmp(0, l.DISCRIMINATOR)),
        )
    }
}
