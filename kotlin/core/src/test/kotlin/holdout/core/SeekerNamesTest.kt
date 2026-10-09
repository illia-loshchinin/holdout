package holdout.core

import com.funkatronics.encoders.Base58
import com.solana.publickey.ProgramDerivedAddress
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Seeker ID names (spec 003, research R1, R3, R6, R9): the constants derived
 * from first principles, the exact owner search, the reverse-record address,
 * which text counts as a name, which name a wallet shows and how it is cut.
 * No network.
 */
class SeekerNamesTest {

    private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())

    private fun pda(program: SolanaPublicKey, vararg seeds: ByteArray): SolanaPublicKey = runBlocking {
        SolanaPublicKey(ProgramDerivedAddress.find(seeds.toList(), program).getOrThrow().bytes)
    }

    private val zeros = ByteArray(32)

    // ---- constants (research R1) -----------------------------------------------

    @Test
    fun `the program ids are the ones tldparser names`() {
        assertEquals("ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK", SeekerNames.ANS_PROGRAM.base58())
        assertEquals("TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S", SeekerNames.TLD_HOUSE_PROGRAM.base58())
    }

    @Test
    fun `the skr parent and tld house derive from first principles`() {
        val origin = pda(SeekerNames.ANS_PROGRAM, sha256("ALT Name Service" + "ANS"), zeros, zeros)
        assertEquals("3mX9b4AZaQehNoQGfckVcmgmA6bkBoFcbLj9RMmMyNcU", origin.base58())

        val parent = pda(SeekerNames.ANS_PROGRAM, sha256("ALT Name Service" + ".skr"), zeros, origin.bytes)
        assertEquals("F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF", parent.base58())
        assertEquals(parent.base58(), SeekerNames.SKR_PARENT.base58())

        val house = pda(SeekerNames.TLD_HOUSE_PROGRAM, "tld_house".encodeToByteArray(), ".skr".encodeToByteArray())
        assertEquals("4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA", house.base58())
        assertEquals(house.base58(), SeekerNames.SKR_TLD_HOUSE.base58())
    }

    // ---- the owner search (contracts §1.1) --------------------------------------

    @Test
    fun `the owner search asks for a wallet's skr name accounts and no data`() {
        val wallet = SolanaPublicKey(ByteArray(32) { 0x11 })
        val expected = Json.parseToJsonElement(
            """
            ["ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK",
             {"encoding":"base64","commitment":"confirmed",
              "dataSlice":{"offset":0,"length":0},
              "filters":[
                {"memcmp":{"offset":40,"bytes":"${wallet.base58()}"}},
                {"memcmp":{"offset":8,"bytes":"F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF"}}]}]
            """,
        )
        assertEquals(expected, SeekerNames.ownerSearchParams(wallet))
    }

    // ---- the reverse record (data-model §1.2) -----------------------------------

    @Test
    fun `a name account's reverse record is the PDA of its base58 under the skr tld house`() {
        val nameAccount = SolanaPublicKey(ByteArray(32) { 0x22 })
        val expected = pda(
            SeekerNames.ANS_PROGRAM,
            sha256("ALT Name Service" + Base58.encodeToString(nameAccount.bytes)),
            SeekerNames.SKR_TLD_HOUSE.bytes,
            zeros,
        )
        assertEquals(expected.base58(), runBlocking { SeekerNames.reverseRecordAddress(nameAccount) }.base58())
    }

    // ---- which text is a name (research R6) -------------------------------------

    private fun record(text: String, trailing: Int = 4): ByteArray = ByteArray(200) { 7 } + text.encodeToByteArray() + ByteArray(trailing)

    @Test
    fun `the name is the text after the 200-byte header, up to the first zero byte`() {
        assertEquals("alice", SeekerNames.nameFromReverseRecord(record("alice")))
        assertEquals("alice", SeekerNames.nameFromReverseRecord(record("alice", trailing = 0)))
        assertEquals("ναι", SeekerNames.nameFromReverseRecord(record("ναι")))
    }

    @Test
    fun `text that could mislead or is malformed is not a name`() {
        // A right-to-left override can draw other text than the bytes say.
        assertNull(SeekerNames.nameFromReverseRecord(record("ali‮ce")))
        // A zero-width space hides a difference between two names.
        assertNull(SeekerNames.nameFromReverseRecord(record("ali​ce")))
        assertNull(SeekerNames.nameFromReverseRecord(record("ali\u0007ce")))
        assertNull(SeekerNames.nameFromReverseRecord(record("")))
        assertNull(SeekerNames.nameFromReverseRecord(record("a".repeat(64))))
        assertEquals("a".repeat(63), SeekerNames.nameFromReverseRecord(record("a".repeat(63))))
        assertNull(SeekerNames.nameFromReverseRecord(record("alice.skr")))
        assertNull(SeekerNames.nameFromReverseRecord(ByteArray(200) { 7 } + byteArrayOf(0xC3.toByte(), 0x28, 0)))
        assertNull(SeekerNames.nameFromReverseRecord(ByteArray(120)))
    }

    // ---- which name a wallet shows (research R3) --------------------------------

    @Test
    fun `the first name in code-point order of its lower-cased text is chosen`() {
        assertEquals("Alice.skr", SeekerNames.choose(listOf("zed", "Alice", "bob")))
        assertEquals("bob.skr", SeekerNames.choose(listOf("bob")))
        assertNull(SeekerNames.choose(emptyList()))
    }

    @Test
    fun `names that differ only in case are chosen the same way every time`() {
        assertEquals("Alice.skr", SeekerNames.choose(listOf("alice", "Alice")))
        assertEquals("Alice.skr", SeekerNames.choose(listOf("Alice", "alice")))
    }

    @Test
    fun `a record with no text after its header is no name, and bytes after the end are ignored`() {
        assertNull(SeekerNames.nameFromReverseRecord(ByteArray(200)))
        assertNull(SeekerNames.nameFromReverseRecord(ByteArray(201)))
        assertEquals("bob", SeekerNames.nameFromReverseRecord(ByteArray(200) + "bob".encodeToByteArray() + byteArrayOf(0, 0x41, 0x42)))
    }

    @Test
    fun `the choice is the same whatever order the names arrive in`() {
        val names = listOf("Émile", "emma", "zoë", "😀smile", "Ａwide")
        assertEquals(SeekerNames.choose(names), SeekerNames.choose(names.reversed()))
        // Code points, not UTF-16 units: U+FF21 sorts before U+1F600.
        assertEquals("Ａwide.skr", SeekerNames.choose(listOf("😀smile", "Ａwide")))
    }

    // ---- fitting a long name (research R9) --------------------------------------

    @Test
    fun `a name within its budget is unchanged`() {
        assertEquals("alice.skr", SeekerNames.fit("alice.skr", 20))
        assertEquals("a".repeat(16) + ".skr", SeekerNames.fit("a".repeat(16) + ".skr", 20))
    }

    @Test
    fun `a cut never splits a character made of two UTF-16 units`() {
        // 30 emoji: each one code point, two UTF-16 units.
        val name = "😀".repeat(30) + ".skr"
        val cut = SeekerNames.fit(name, 20)
        assertEquals(20, cut.codePointCount(0, cut.length))
        assertEquals("😀".repeat(15) + "….skr", cut)
    }

    @Test
    fun `even a tiny budget keeps the skr ending`() {
        assertEquals("a….skr", SeekerNames.fit("averyveryverylongname.skr", 4))
    }

    @Test
    fun `a long name keeps its start and the whole skr ending`() {
        assertEquals("averyveryverylo….skr", SeekerNames.fit("averyveryverylongname.skr", 20))
        assertEquals(20, SeekerNames.fit("averyveryverylongname.skr", 20).length)
        val long = "b".repeat(40) + ".skr"
        assertEquals("b".repeat(23) + "….skr", SeekerNames.fit(long, 28))
    }
}
