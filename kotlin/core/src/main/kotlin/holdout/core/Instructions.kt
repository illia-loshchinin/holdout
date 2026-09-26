package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Instruction builders (contracts/program-instructions §1–§3), with the
 * encodings written by hand rather than generated (research R2 deviation).
 *
 * Account order is the one `tests/helpers/instructions.ts` fixes and the
 * program's `Accounts` structs follow: signers first, then the state the
 * instruction reads or writes, then programs. Started for T062 with
 * `publish_chip_set`; T030 adds the rest and pins every one of them to
 * `fixtures/instructions.json`.
 */
object Instructions {
    /** Anchor's default: the first 8 bytes of `sha256("global:<snake_case_name>")`. */
    fun discriminator(name: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("global:$name".encodeToByteArray()).copyOf(8)

    /**
     * `publish_chip_set(amounts: [u64; 4])` — four SKR amounts in base units and
     * nothing else: no rate, no USD figure (constitution III).
     *
     * [operator] signs, and in the crank it also pays the fee, so it is marked
     * writable: a fee payer must be. The program only requires a signer, so the
     * extra flag changes nothing it checks.
     */
    fun publishChipSet(
        programId: SolanaPublicKey,
        operator: SolanaPublicKey,
        config: SolanaPublicKey,
        amounts: List<ULong>,
    ): TransactionInstruction {
        require(amounts.size == 4) { "a chip set is four amounts, got ${amounts.size}" }
        val data = ByteBuffer.allocate(8 + 4 * 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("publish_chip_set"))
        amounts.forEach { data.putLong(it.toLong()) }
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(operator, true, true),
                AccountMeta(config, false, true),
            ),
            data.array(),
        )
    }
}
