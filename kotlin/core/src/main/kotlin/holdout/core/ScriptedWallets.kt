package holdout.core

import com.solana.publickey.SolanaPublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The published list of scripted wallets (`scripted-wallets.json`, bundled
 * with this module): what labels a scripted member wherever one appears, with
 * no server (FR-043a, FR-064, constitution XII). The contract never learns
 * which wallets these are; only this list and the crank's floors do.
 */
object ScriptedWallets {
    /** Every scripted wallet's key, from the bundled list; empty if it is missing (then nothing is labelled, never guessed). */
    val keys: Set<SolanaPublicKey> by lazy { parse(read()) }

    fun isScripted(wallet: SolanaPublicKey): Boolean = wallet in keys

    fun parse(json: String?): Set<SolanaPublicKey> = runCatching {
        Json
            .parseToJsonElement(json ?: return emptySet())
            .jsonObject
            .getValue("wallets")
            .jsonArray
            .map { SolanaPublicKey.from(it.jsonObject.getValue("pubkey").jsonPrimitive.content) }
            .toSet()
    }.getOrDefault(emptySet())

    private fun read(): String? = ScriptedWallets::class.java.getResourceAsStream("/scripted-wallets.json")?.use { it.readBytes().decodeToString() }
}
