package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.toUnsignedTransaction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T062 — the PDAs and the one instruction `chips publish` sends, pinned to the
 * other two sides rather than to themselves: addresses to what
 * `tests/helpers/pdas.ts` derives with `@solana/kit`, and the instruction's
 * discriminator and account flags to the IDL the Anchor build writes.
 */
class InstructionsTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val operator = SolanaPublicKey(ByteArray(32) { 0x11 })

    private val idlPublish =
        Json
            .parseToJsonElement(RepoFiles.read("idl/holdout.json"))
            .jsonObject
            .getValue("instructions")
            .jsonArray
            .map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == "publish_chip_set" }

    @Test
    fun `the singleton PDAs are the ones the TypeScript suite derives`() = runBlocking {
        // Printed by `tests/helpers/pdas.ts` (configPda, faucetVaultPda, mintAuthorityPda).
        assertEquals("7BRLZEH3QU8imSTGfskDooRRHHCxNc5qj3NtMd85KJGh", Pdas.config(programId).base58())
        assertEquals("6RKCsJCLtE99dfxB2wbkJ3EYVxxBgR6pv9oP6vqR19xV", Pdas.faucetVault(programId).base58())
        assertEquals("DoArEnFCULZBNRzEdT8S1x9RtFzrhFMHf6azeBUiw7ST", Pdas.mintAuthority(programId).base58())
    }

    @Test
    fun `the discriminator is the one the built IDL lists`() {
        val fromIdl = idlPublish.getValue("discriminator").jsonArray.map { it.jsonPrimitive.int.toByte() }
        assertEquals(fromIdl, Instructions.discriminator("publish_chip_set").toList())
    }

    @Test
    fun `publish_chip_set carries four little-endian amounts and nothing else`() = runBlocking {
        val config = Pdas.config(programId)
        val amounts = listOf(460_000_000uL, 1_170_000_000uL, 2_340_000_000uL, ULong.MAX_VALUE)
        val ix = Instructions.publishChipSet(programId, operator, config, amounts)

        assertEquals(programId, ix.programId)
        assertEquals(8 + 32, ix.data.size, "discriminator and four u64s — no rate, no price (constitution III)")
        assertContentEquals(Instructions.discriminator("publish_chip_set"), ix.data.copyOf(8))
        val body = ByteBuffer.wrap(ix.data, 8, 32).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(amounts, List(4) { body.getLong().toULong() })
    }

    @Test
    fun `publish_chip_set accounts follow the IDL, with the operator also paying`() = runBlocking {
        val config = Pdas.config(programId)
        val ix = Instructions.publishChipSet(programId, operator, config, listOf(1uL, 2uL, 3uL, 4uL))
        val idlAccounts = idlPublish.getValue("accounts").jsonArray.map { it.jsonObject }

        assertEquals(idlAccounts.map { it.getValue("name").jsonPrimitive.content }, listOf("operator", "config"))
        assertEquals(listOf(operator, config), ix.accounts.map { it.publicKey })
        assertTrue(ix.accounts[0].isSigner, "the operator signs")
        assertTrue(ix.accounts[0].isWritable, "and pays the fee, so it is writable")
        assertTrue(!ix.accounts[1].isSigner && ix.accounts[1].isWritable, "config is written, never signs")
    }

    @Test
    fun `a message built from it has the operator as its only signer and fee payer`() = runBlocking {
        val ix = Instructions.publishChipSet(programId, operator, Pdas.config(programId), listOf(1uL, 2uL, 3uL, 4uL))
        val message =
            Message
                .Builder()
                .addInstruction(ix)
                .setRecentBlockhash(SolanaPublicKey(ByteArray(32) { 7 }))
                .build()
        val decoded = Transaction.from(message.toUnsignedTransaction().serialize()).message

        assertEquals(1, decoded.signatureCount.toInt(), "one signature: the operator's")
        assertEquals(operator, decoded.accounts.first(), "account 0 pays the fee")
    }

    @Test
    fun `a chip set is exactly four amounts`() {
        assertFailsWith<IllegalArgumentException> {
            Instructions.publishChipSet(programId, operator, operator, listOf(1uL, 2uL, 3uL))
        }
    }
}
