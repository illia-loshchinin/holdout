package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.TransactionInstruction
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The header web3-solana's builder gets wrong when one account carries
 * different flags in different places — and [Transactions.message], which
 * every Holdout transaction is built with, gets right.
 */
class TransactionsTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val member = SolanaPublicKey(ByteArray(32) { 7 })
    private val mint = SolanaPublicKey(ByteArray(32) { 9 })
    private val sponsor = SolanaPublicKey(ByteArray(32) { 5 })
    private val blockhash = "EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N"

    private fun mismatches(ixs: List<TransactionInstruction>, m: Message): List<String> {
        val wanted = Transactions.mergedFlags(ixs)
        return m.accounts.mapIndexedNotNull { i, key -> key.base58().takeIf { Transactions.isWritable(m, i) != (wanted[key]?.second ?: false) } }
    }

    private fun cases(): List<List<TransactionInstruction>> = runBlocking {
        val cohort = Pdas.cohort(programId, 100, 0)
        listOf(
            // A member who claims and joins in one transaction, paying for both (scripted members do).
            listOf(Instructions.claimFaucet(programId, member, member, mint, sponsor), Instructions.joinCohort(programId, member, mint, cohort, 0)),
            // The sponsor pays for a zero-SOL wallet's claim and is reimbursed as a writable account.
            listOf(Instructions.claimFaucet(programId, sponsor, member, mint, sponsor)),
            // The program's own id stands for "none" beside the program itself.
            listOf(Instructions.settleFinalize(programId, member, sponsor, 100, cohort)),
        )
    }

    @Test
    fun `every account is as writable as some instruction needs it`() {
        cases().forEach { ixs -> assertEquals(emptyList(), mismatches(ixs, Transactions.message(ixs, blockhash))) }
    }

    @Test
    fun `web3-solana's own builder miscounts them - the reason Transactions exists`() {
        val ixs = cases().first()
        val raw = Message.Builder().apply { ixs.forEach { addInstruction(it) } }.setRecentBlockhash(blockhash).build()
        // If this starts passing, the library is fixed and Transactions.message can go.
        assertTrue(mismatches(ixs, raw).isNotEmpty(), "web3-solana now builds this header correctly")
    }

    @Test
    fun `the payer stays first, so it pays the fee`() {
        cases().forEach { ixs -> assertEquals(ixs.first().accounts.first().publicKey, Transactions.message(ixs, blockhash).accounts.first()) }
    }
}
