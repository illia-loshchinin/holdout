package holdout.crank

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T062 — the Operator's key. Pinned to RFC 8032 §7.1, tests 1 and 2 (cross-checked
 * against Node's `crypto`), so the JDK's Ed25519 and the seed-to-public-key
 * derivation are both checked against the standard, not against themselves.
 */
class KeypairTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    private val seed1 = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    private val public1 = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")

    @Test
    fun `RFC 8032 test 1 - the seed derives its public key and signs the empty message`() {
        val k = Keypair.fromSeed(seed1)
        assertContentEquals(public1, k.publicKey.bytes)
        assertContentEquals(
            hex(
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
            ),
            k.sign(ByteArray(0)),
        )
    }

    @Test
    fun `RFC 8032 test 2 - a one-byte message`() {
        val k = Keypair.fromSeed(hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"))
        assertContentEquals(hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"), k.publicKey.bytes)
        assertContentEquals(
            hex(
                "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
            ),
            k.sign(byteArrayOf(0x72)),
        )
    }

    @Test
    fun `a Solana CLI keypair file loads, and one with the wrong public half is refused`() {
        val file = File.createTempFile("operator", ".json").apply { deleteOnExit() }
        file.writeText((seed1 + public1).joinToString(",", "[", "]") { (it.toInt() and 0xFF).toString() })
        assertContentEquals(public1, Keypair.load(file.path, "operator").publicKey.bytes)

        val wrong = seed1 + ByteArray(32) { 1 }
        file.writeText(wrong.joinToString(",", "[", "]") { (it.toInt() and 0xFF).toString() })
        val e = assertFailsWith<CrankException> { Keypair.load(file.path, "operator") }
        assertEquals(EXIT_CONFIG, e.exitCode)
        assertTrue(e.message!!.contains("public half"))
    }

    @Test
    fun `an unreadable or malformed keypair file is a configuration error`() {
        assertEquals(EXIT_CONFIG, assertFailsWith<CrankException> { Keypair.load("/no/such/key.json", "operator") }.exitCode)
        val file = File.createTempFile("operator", ".json").apply { deleteOnExit() }
        file.writeText("[1,2,3]")
        assertEquals(EXIT_CONFIG, assertFailsWith<CrankException> { Keypair.load(file.path, "operator") }.exitCode)
        file.writeText("not json")
        assertEquals(EXIT_CONFIG, assertFailsWith<CrankException> { Keypair.load(file.path, "operator") }.exitCode)
    }
}
