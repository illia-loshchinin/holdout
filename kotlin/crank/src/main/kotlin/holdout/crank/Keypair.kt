package holdout.crank

import com.solana.publickey.SolanaPublicKey
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.EdECPublicKey
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.NamedParameterSpec

/**
 * A key the crank signs with: the Operator's for `chips publish`, the payer's
 * for everything permissionless (contracts/crank-cli.md).
 *
 * Read from the Solana CLI's keypair file — a JSON array of 64 bytes, the
 * 32-byte Ed25519 seed then the 32-byte public key — and signed with the JDK's
 * own Ed25519 (Java 15+), so the crank carries no crypto dependency of its own.
 * The app never holds a key; it signs through the wallet (constitution I).
 */
class Keypair private constructor(
    private val seed: ByteArray,
    val publicKey: SolanaPublicKey,
) {
    fun sign(message: ByteArray): ByteArray {
        val key = KeyFactory.getInstance(ED25519).generatePrivate(EdECPrivateKeySpec(NamedParameterSpec.ED25519, seed))
        return Signature.getInstance(ED25519).run {
            initSign(key)
            update(message)
            sign()
        }
    }

    companion object {
        private const val ED25519 = "Ed25519"

        /**
         * From a 32-byte seed. The public key is derived, not trusted: the
         * generator is handed the seed as its only randomness, which is how the
         * JDK turns a seed into a key pair.
         */
        fun fromSeed(seed: ByteArray): Keypair {
            require(seed.size == 32) { "an Ed25519 seed is 32 bytes, got ${seed.size}" }
            val fixed =
                object : SecureRandom() {
                    override fun nextBytes(bytes: ByteArray) {
                        seed.copyInto(bytes, endIndex = bytes.size)
                    }
                }
            val generator = KeyPairGenerator.getInstance(ED25519).apply { initialize(NamedParameterSpec.ED25519, fixed) }
            val public = generator.generateKeyPair().public as EdECPublicKey
            return Keypair(seed.copyOf(), SolanaPublicKey(encode(public)))
        }

        /**
         * From the Solana CLI's 64-byte form. A file whose second half is not
         * the public key its seed derives is refused: signing with it would
         * produce signatures for a key nobody expects.
         */
        fun fromBytes(bytes: ByteArray): Keypair {
            require(bytes.size == 64) { "a Solana keypair is 64 bytes, got ${bytes.size}" }
            val keypair = fromSeed(bytes.copyOf(32))
            require(keypair.publicKey.bytes.contentEquals(bytes.copyOfRange(32, 64))) {
                "the keypair's public half does not match its secret half"
            }
            return keypair
        }

        /** Reads [path], a Solana CLI keypair file; any problem is a configuration error. */
        fun load(path: String, role: String): Keypair {
            val text =
                runCatching { File(path).readText() }.getOrNull()
                    ?: configError("cannot read the $role keypair at $path")
            val bytes =
                runCatching {
                    text
                        .trim()
                        .removeSurrounding("[", "]")
                        .split(',')
                        .map { it.trim().toInt().also { b -> require(b in 0..255) }.toByte() }
                        .toByteArray()
                }.getOrNull() ?: configError("the $role keypair at $path is not a JSON array of bytes")
            return runCatching { fromBytes(bytes) }.getOrElse { configError("the $role keypair at $path: ${it.message}") }
        }

        /** RFC 8032 §5.1.2: `y` little-endian, with the top bit carrying the sign of `x`. */
        private fun encode(key: EdECPublicKey): ByteArray {
            val y = key.point.y.toByteArray().reversedArray() // big-endian → little-endian
            val out = ByteArray(32)
            y.copyInto(out, endIndex = minOf(32, y.size))
            if (key.point.isXOdd) out[31] = (out[31].toInt() or 0x80).toByte()
            return out
        }
    }
}
