package holdout.core

import com.funkatronics.encoders.Base58
import com.solana.publickey.ProgramDerivedAddress
import com.solana.publickey.SolanaPublicKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/**
 * Seeker ID names (spec 003): where a wallet's `.skr` names live on Solana
 * mainnet, and which of them it shows. Pure, no I/O — [SeekerNameReader]
 * does the two reads.
 *
 * The program ids, offsets and hash prefix come from the published
 * `@onsol/tldparser` 1.2.1 source, the client the Solana Mobile docs point
 * to, and were confirmed by a spike against mainnet on 2026-10-09: `alice.skr`
 * resolved to its owner and back with exactly these two calls (research R1,
 * R2). Names are for display only: nothing here decides anything (FR-008).
 */
object SeekerNames {
    /** The AllDomains name program (ANS): every name account lives under it. */
    val ANS_PROGRAM = SolanaPublicKey.from("ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK")

    /** The TLD house program: one house per top-level domain. */
    val TLD_HOUSE_PROGRAM = SolanaPublicKey.from("TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S")

    /** The `.skr` TLD's own name account: the parent of every Seeker ID. */
    val SKR_PARENT = SolanaPublicKey.from("F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF")

    /** The `.skr` TLD house, a seed of every `.skr` reverse record. */
    val SKR_TLD_HOUSE = SolanaPublicKey.from("4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA")

    const val SUFFIX = ".skr"

    private const val HASH_PREFIX = "ALT Name Service"

    /** Every name account starts with this header; a reverse record's text follows it. */
    private const val HEADER_BYTES = 200
    private const val PARENT_OFFSET = 8
    private const val OWNER_OFFSET = 40
    private const val MAX_NAME_CHARS = 63

    /**
     * The owner search (contracts §1.1): the wallet's name accounts under the
     * `.skr` parent, with no account data — only their addresses are needed.
     */
    fun ownerSearchParams(wallet: SolanaPublicKey): JsonArray = buildJsonArray {
        add(JsonPrimitive(ANS_PROGRAM.base58()))
        add(
            buildJsonObject {
                put("encoding", "base64")
                put("commitment", Rpc.READ_COMMITMENT)
                putJsonObject("dataSlice") {
                    put("offset", 0)
                    put("length", 0)
                }
                putJsonArray("filters") {
                    add(memcmp(OWNER_OFFSET, wallet))
                    add(memcmp(PARENT_OFFSET, SKR_PARENT))
                }
            },
        )
    }

    /** The reverse record of [nameAccount]: the PDA `[sha256(prefix + base58), .skr house, 32 zero bytes]` under ANS. */
    suspend fun reverseRecordAddress(nameAccount: SolanaPublicKey): SolanaPublicKey = SolanaPublicKey(
        ProgramDerivedAddress.find(
            listOf(hashed(Base58.encodeToString(nameAccount.bytes)), SKR_TLD_HOUSE.bytes, ByteArray(32)),
            ANS_PROGRAM,
        ).getOrThrow().bytes,
    )

    /**
     * The name a reverse record holds, without `.skr`, or null when it is not
     * one to show (research R6): it must be valid UTF-8 up to the first zero
     * byte, 1–63 characters, free of control and format characters — a
     * bidirectional override or a zero-width character could make one name
     * look like another — and must not already end in `.skr`.
     */
    fun nameFromReverseRecord(data: ByteArray): String? {
        if (data.size <= HEADER_BYTES) return null
        val tail = data.copyOfRange(HEADER_BYTES, data.size)
        val end = tail.indexOf(0.toByte()).takeIf { it >= 0 } ?: tail.size
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(tail, 0, end))
                .toString()
        } catch (e: CharacterCodingException) {
            return null
        }
        val chars = text.codePointCount(0, text.length)
        if (chars !in 1..MAX_NAME_CHARS) return null
        if (text.codePoints().anyMatch { Character.getType(it).let { t -> t == Character.CONTROL.toInt() || t == Character.FORMAT.toInt() } }) return null
        if (text.lowercase().endsWith(SUFFIX)) return null
        return text
    }

    /**
     * The name a wallet shows, with `.skr`, or null when it has none (FR-005,
     * research R3): the first by the code points of its lower-cased text, so
     * every phone and every session picks the same one.
     */
    fun choose(candidates: List<String>): String? = candidates
        .minWithOrNull(compareBy<String, String>(CODE_POINTS) { it.lowercase() }.then(CODE_POINTS))
        ?.plus(SUFFIX)

    /**
     * [name] cut to [budget] characters, keeping its start and the whole
     * `.skr` ending (FR-004, research R9). A name that fits is unchanged.
     */
    fun fit(name: String, budget: Int): String {
        if (name.codePointCount(0, name.length) <= budget) return name
        val label = name.removeSuffix(SUFFIX)
        val keep = (budget - SUFFIX.length - 1).coerceAtLeast(1)
        return label.substring(0, label.offsetByCodePoints(0, keep)) + "…" + SUFFIX
    }

    private fun hashed(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest((HASH_PREFIX + text).encodeToByteArray())

    private fun memcmp(offset: Int, key: SolanaPublicKey) = buildJsonObject {
        putJsonObject("memcmp") {
            put("offset", offset)
            put("bytes", key.base58())
        }
    }

    /** Unicode code-point order — not UTF-16 units, which put U+1F600 before U+FF21. */
    private val CODE_POINTS = Comparator<String> { a, b ->
        val x = a.codePoints().toArray()
        val y = b.codePoints().toArray()
        for (i in 0 until minOf(x.size, y.size)) {
            if (x[i] != y[i]) return@Comparator x[i].compareTo(y[i])
        }
        x.size.compareTo(y.size)
    }
}
