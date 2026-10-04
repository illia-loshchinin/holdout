package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Instruction
import com.solana.transaction.LegacyMessage
import com.solana.transaction.Message
import com.solana.transaction.TransactionInstruction

/**
 * Every transaction message Holdout builds — crank, app, faucet — is compiled
 * here, never by `Message.Builder`.
 *
 * **Why.** web3-solana 0.3.1's `Message.Builder` files each account into one of
 * four sets (writable signers, read-only signers, writable, read-only) and
 * counts the header from the sets' sizes. An account that appears with
 * different flags — the payer that is also `claim_faucet`'s recipient, the
 * sponsor that pays and is reimbursed, the program's own id standing for
 * "none" beside the program itself — is listed once but counted twice, the
 * header's boundaries shift, and an account compiles with the wrong
 * privilege. The program then fails with "writable privilege escalated"
 * (found on a local validator, 2026-09-26).
 *
 * So the legacy message is compiled here, as the runtime reads it: every key
 * once, with its flags merged across all instructions — signer if it signs
 * anywhere, writable if it is written anywhere — the fee payer first, then
 * writable signers, read-only signers, writable accounts and read-only
 * accounts, program ids among the last.
 */
object Transactions {
    fun message(instructions: List<TransactionInstruction>, blockhash: String): Message {
        require(instructions.isNotEmpty()) { "a transaction needs an instruction" }
        val flags = mergedFlags(instructions)
        val payer = instructions.first().accounts.first { it.isSigner }.publicKey
        val programs = instructions.map { it.programId }.filter { it !in flags }.distinct()

        fun group(signer: Boolean, writable: Boolean) = flags.filter { (key, f) -> key != payer && f.first == signer && f.second == writable }.keys
        val keys = listOf(payer) + group(signer = true, writable = true) + group(signer = true, writable = false) + group(signer = false, writable = true) + group(signer = false, writable = false) + programs
        val signers = 1 + group(signer = true, writable = true).size + group(signer = true, writable = false).size
        val readOnlySigners = group(signer = true, writable = false).size
        val readOnlyNonSigners = group(signer = false, writable = false).size + programs.size

        val index = keys.withIndex().associate { (i, k) -> k to i }
        return LegacyMessage(
            signers.toUByte(),
            readOnlySigners.toUByte(),
            readOnlyNonSigners.toUByte(),
            keys,
            SolanaPublicKey.from(blockhash),
            instructions.map { ix ->
                Instruction(
                    index.getValue(ix.programId).toUByte(),
                    ByteArray(ix.accounts.size) { index.getValue(ix.accounts[it].publicKey).toByte() },
                    ix.data,
                )
            },
        )
    }

    /** Each account key's flags across every instruction: signer if any, writable if any. */
    fun mergedFlags(instructions: List<TransactionInstruction>): Map<SolanaPublicKey, Pair<Boolean, Boolean>> {
        val flags = LinkedHashMap<SolanaPublicKey, Pair<Boolean, Boolean>>()
        instructions.flatMap { it.accounts }.forEach { meta ->
            val (s, w) = flags[meta.publicKey] ?: (false to false)
            flags[meta.publicKey] = (s || meta.isSigner) to (w || meta.isWritable)
        }
        return flags
    }

    /** Whether [message] marks account [index] writable, read from its header. */
    fun isWritable(message: Message, index: Int): Boolean {
        val n = message.accounts.size
        val signers = message.signatureCount.toInt()
        return if (index < signers) index < signers - message.readOnlyAccounts.toInt() else index < n - message.readOnlyNonSigners.toInt()
    }
}
