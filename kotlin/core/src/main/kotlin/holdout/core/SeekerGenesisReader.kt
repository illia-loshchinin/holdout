package holdout.core

import com.solana.networking.KtorNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpccore.JsonRpc20Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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
import kotlinx.serialization.json.put
import java.io.IOException

/** What one Seeker Genesis Token check found (spec FR-003b–FR-003d, research R17). */
sealed interface GenesisCheck {
    /**
     * The wallet holds a Seeker Genesis Token: this mint, in this token
     * account — the two accounts `join_cohort` reads once the gate is on at
     * mainnet (research R17).
     */
    data class Holds(val mint: SolanaPublicKey, val tokenAccount: SolanaPublicKey) : GenesisCheck

    /**
     * The node answered in full and the wallet holds none: the Seeker-exclusive
     * notice. Only an answer read in its expected shape can say this; anything
     * else is [Unknown] (FR-003d).
     */
    data object None : GenesisCheck

    /**
     * No answer — refused, failed or too slow. Never the Seeker-exclusive
     * notice: joining is refused with a try-again message (FR-003d).
     * [rateLimited] when the node said there were too many requests.
     */
    data class Unknown(val rateLimited: Boolean) : GenesisCheck
}

/**
 * Checks on Solana **mainnet** whether a wallet holds a Seeker Genesis Token,
 * before the member app offers to sign a join (spec FR-003b, research R17).
 * Seeker Genesis Tokens exist only on mainnet, so on devnet this is what keeps
 * staking Seeker-exclusive; the program's own check takes over at mainnet.
 *
 * Like [SeekerNameReader], it is not an [Rpc] on purpose: it **can neither
 * sign nor send**, so "no code path touches mainnet with a send" stays
 * checkable in review. It reads the wallet's Token-2022 accounts, keeps those
 * holding at least one token (an emptied account stays behind after a
 * transfer), then reads their mints and looks for a `tokenGroupMember`
 * extension naming [SEEKER_GENESIS_GROUP]. It sends the wallet's address,
 * already public on chain, and nothing else.
 */
class SeekerGenesisReader(
    endpoint: String,
    driver: Rpc20Driver = Rpc20Driver(endpoint, KtorNetworkDriver()),
    timeoutMillis: Long = GENESIS_TIMEOUT_MILLIS,
) {
    private val rpc = driver
    private val timeout = timeoutMillis

    /** Whether [wallet] holds a Seeker Genesis Token, holds none, or the node did not answer. */
    suspend fun check(wallet: SolanaPublicKey): GenesisCheck = try {
        withTimeout(timeout) { lookUp(wallet) }
    } catch (e: TimeoutCancellationException) {
        GenesisCheck.Unknown(rateLimited = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RateLimited) {
        GenesisCheck.Unknown(rateLimited = true)
    } catch (e: Exception) {
        GenesisCheck.Unknown(rateLimited = e.message?.contains("429") == true)
    }

    private suspend fun lookUp(wallet: SolanaPublicKey): GenesisCheck {
        // Mint → the token account holding it, for every account holding ≥ 1.
        val held = call("getTokenAccountsByOwner", tokenAccountsParams(wallet))
            .jsonObject.getValue("value").jsonArray
            .mapNotNull { entry ->
                val e = entry.jsonObject
                heldMint(e.getValue("account").jsonObject)?.let { it to e.getValue("pubkey").jsonPrimitive.content }
            }
            .distinctBy { it.first }
        if (held.isEmpty()) return GenesisCheck.None
        for (chunk in held.chunked(MINT_READS_PER_CALL)) {
            val mints = call("getMultipleAccounts", mintReadParams(chunk.map { it.first })).jsonObject.getValue("value").jsonArray
            // A short answer would silently drop a mint, which could be the one that counts.
            if (mints.size != chunk.size) throw IOException("getMultipleAccounts answered ${mints.size} of ${chunk.size} mints")
            chunk.zip(mints).forEach { (pair, account) ->
                val (mint, tokenAccount) = pair
                if (account !is JsonNull && isGenesisMember(account.jsonObject, mint)) {
                    return GenesisCheck.Holds(SolanaPublicKey.from(mint), SolanaPublicKey.from(tokenAccount))
                }
            }
        }
        return GenesisCheck.None
    }

    private suspend fun call(method: String, params: JsonArray): JsonElement {
        val response = rpc.makeRequest(JsonRpc20Request(method, params, method), JsonElement.serializer())
        response.error?.let { if (it.code == HTTP_TOO_MANY_REQUESTS) throw RateLimited() else throw IOException("$method: ${it.code} ${it.message}") }
        return response.result ?: throw IOException("$method returned neither a result nor an error")
    }

    private class RateLimited : Exception()

    companion object {
        /** The Seeker Genesis Token group (program `genesis::SEEKER_GENESIS_GROUP`, research R17). */
        const val SEEKER_GENESIS_GROUP = "GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te"

        /** The Token-2022 program, which owns every Seeker Genesis Token. */
        const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

        internal fun tokenAccountsParams(wallet: SolanaPublicKey): JsonArray = buildJsonArray {
            add(JsonPrimitive(wallet.base58()))
            add(buildJsonObject { put("programId", TOKEN_2022_PROGRAM) })
            add(
                buildJsonObject {
                    put("encoding", "jsonParsed")
                    put("commitment", Rpc.READ_COMMITMENT)
                },
            )
        }

        private fun mintReadParams(mints: List<String>): JsonArray = buildJsonArray {
            add(JsonArray(mints.map { JsonPrimitive(it) }))
            add(
                buildJsonObject {
                    put("encoding", "jsonParsed")
                    put("commitment", Rpc.READ_COMMITMENT)
                },
            )
        }

        /**
         * The mint of a parsed token account holding at least one token, null
         * for an emptied one. An account not in the expected parsed shape
         * throws, so the whole check is [GenesisCheck.Unknown], never None.
         */
        internal fun heldMint(account: JsonObject): String? {
            val info = parsedInfo(account) ?: throw IOException("token account not jsonParsed")
            val amount = (info["tokenAmount"] as? JsonObject)?.get("amount")?.jsonPrimitive?.content?.toBigIntegerOrNull()
                ?: throw IOException("token account without an amount")
            if (amount.signum() != 1) return null
            return info["mint"]?.jsonPrimitive?.content ?: throw IOException("token account without a mint")
        }

        /** Whether a parsed Token-2022 mint is a member of the Seeker Genesis group. */
        internal fun isGenesisMember(account: JsonObject, mint: String): Boolean {
            if (account["owner"]?.jsonPrimitive?.content != TOKEN_2022_PROGRAM) return false
            val info = parsedInfo(account) ?: throw IOException("mint not jsonParsed")
            val extensions = info["extensions"] as? JsonArray ?: return false
            return extensions.any { entry ->
                val ext = entry as? JsonObject ?: return@any false
                val state = ext["state"] as? JsonObject ?: return@any false
                ext["extension"]?.jsonPrimitive?.content == "tokenGroupMember" &&
                    state["group"]?.jsonPrimitive?.content == SEEKER_GENESIS_GROUP &&
                    state["mint"]?.jsonPrimitive?.content == mint
            }
        }

        private fun parsedInfo(account: JsonObject): JsonObject? = ((account["data"] as? JsonObject)?.get("parsed") as? JsonObject)?.get("info") as? JsonObject
    }
}

/** A check that takes longer than this is unknown (FR-003d). */
private const val GENESIS_TIMEOUT_MILLIS = 10_000L

/** `getMultipleAccounts` takes at most 100 addresses a call. */
private const val MINT_READS_PER_CALL = 100

/** The public node answers 429 in the JSON-RPC error when it refuses for rate. */
private const val HTTP_TOO_MANY_REQUESTS = 429
