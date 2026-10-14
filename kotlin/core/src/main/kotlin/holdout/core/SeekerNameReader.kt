package holdout.core

import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpccore.JsonRpc20Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.Base64

/** What one Seeker ID lookup found (contracts §1.1). */
sealed interface Lookup {
    /** The wallet's chosen name, with `.skr` (FR-005). */
    data class Found(val name: String) : Lookup

    /** The wallet has no Seeker ID: an answer, kept like a name. */
    data object None : Lookup

    /**
     * No answer — refused, failed or too slow. Nothing is kept and the
     * shortened address shows (FR-006). [rateLimited] when the node said
     * there were too many requests.
     */
    data class Unknown(val rateLimited: Boolean) : Lookup
}

/**
 * Reads Seeker ID names from Solana **mainnet** (spec 003, research R5):
 * the owner search, then the reverse records, and nothing else.
 *
 * It is not an [Rpc] on purpose. `Rpc` is devnet's client and the only one
 * that can send a transaction; giving the names endpoint a type of its own
 * that **can neither sign nor send** keeps "no code path touches mainnet with
 * a send" checkable in review. Its one public member is [nameOf].
 *
 * The names endpoint receives wallet addresses already public on chain and
 * nothing else (FR-009). Pacing under the public node's limit is the
 * caller's job (research R4).
 */
class SeekerNameReader(
    endpoint: String,
    driver: Rpc20Driver = Rpc20Driver(endpoint, RecoveringDriver.shared),
    timeoutMillis: Long = LOOKUP_TIMEOUT_MILLIS,
) {
    private val rpc = driver
    private val timeout = timeoutMillis

    /** [wallet]'s chosen Seeker ID, that it has none, or that the node did not answer. */
    suspend fun nameOf(wallet: SolanaPublicKey): Lookup = try {
        withTimeout(timeout) { lookUp(wallet) }
    } catch (e: TimeoutCancellationException) {
        Lookup.Unknown(rateLimited = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RateLimited) {
        Lookup.Unknown(rateLimited = true)
    } catch (e: Exception) {
        Lookup.Unknown(rateLimited = e.message?.contains("429") == true)
    }

    private suspend fun lookUp(wallet: SolanaPublicKey): Lookup {
        val nameAccounts = call("getProgramAccounts", SeekerNames.ownerSearchParams(wallet)).jsonArray.map {
            SolanaPublicKey.from(it.jsonObject.getValue("pubkey").jsonPrimitive.content)
        }
        if (nameAccounts.isEmpty()) return Lookup.None
        val records = nameAccounts.map { SeekerNames.reverseRecordAddress(it) }
        val names = records.chunked(REVERSE_READS_PER_CALL).flatMap { chunk ->
            call("getMultipleAccounts", reverseReadParams(chunk))
                .jsonObject.getValue("value").jsonArray
                .mapNotNull { account -> if (account is JsonNull) null else SeekerNames.nameFromReverseRecord(data(account)) }
        }
        return SeekerNames.choose(names)?.let { Lookup.Found(it) } ?: Lookup.None
    }

    private fun reverseReadParams(addresses: List<SolanaPublicKey>): JsonArray = buildJsonArray {
        add(JsonArray(addresses.map { JsonPrimitive(it.base58()) }))
        add(
            buildJsonObject {
                put("encoding", "base64")
                put("commitment", Rpc.READ_COMMITMENT)
            },
        )
    }

    private suspend fun call(method: String, params: JsonArray): JsonElement {
        val response = rpc.makeRequest(JsonRpc20Request(method, params, method), JsonElement.serializer())
        response.error?.let { if (it.code == HTTP_TOO_MANY_REQUESTS) throw RateLimited() else throw IOException("$method: ${it.code} ${it.message}") }
        return response.result ?: throw IOException("$method returned neither a result nor an error")
    }

    private fun data(account: JsonElement): ByteArray = Base64.getDecoder().decode(
        account.jsonObject.getValue("data").jsonArray[0].jsonPrimitive.content,
    )

    private class RateLimited : Exception()
}

/** A lookup that takes longer than this is unknown (contracts §1.1). */
private const val LOOKUP_TIMEOUT_MILLIS = 10_000L

/** `getMultipleAccounts` takes at most 100 addresses a call. */
private const val REVERSE_READS_PER_CALL = 100

/** The public node answers 429 in the JSON-RPC error when it refuses for rate. */
private const val HTTP_TOO_MANY_REQUESTS = 429
