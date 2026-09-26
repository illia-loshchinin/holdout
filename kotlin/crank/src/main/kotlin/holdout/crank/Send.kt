package holdout.crank

import com.funkatronics.encoders.Base58
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction
import holdout.core.Rpc

/**
 * Signs [instructions] with [signers] — the first pays the fee — and either
 * simulates them ([dryRun]) or sends them once and waits for confirmation.
 * Returns the signature, or null for a clean simulation.
 *
 * Never re-sent: a send with no answer, or one never confirmed, is reported
 * with its signature to check, because it may have landed (the same rule as
 * `chips publish`).
 */
internal suspend fun signAndSend(
    rpc: Rpc,
    what: String,
    instructions: List<TransactionInstruction>,
    signers: List<Keypair>,
    dryRun: Boolean,
    pause: suspend (Long) -> Unit,
    /** What a refusal means for the member of the team reading it. */
    refusedMeans: String = "it did not land",
    /** What to do before running again when the outcome is unknown. */
    beforeRetrying: String = "check that signature, then run again",
): String? {
    val blockhash = rpc.latestBlockhash() ?: failed("could not read a recent blockhash for $what")
    val message =
        Message
            .Builder()
            .apply { instructions.forEach { addInstruction(it) } }
            .setRecentBlockhash(blockhash)
            .build()
    val required = message.accounts.take(message.signatureCount.toInt())
    check(required.firstOrNull() == signers.first().publicKey) { "$what: the fee payer must be ${signers.first().publicKey}, the message put ${required.firstOrNull()} first" }
    val bytes = message.serialize()
    val signatures =
        required.map { key ->
            (signers.firstOrNull { it.publicKey == key } ?: error("$what needs a signature from $key")).sign(bytes)
        }
    val transaction = Transaction(signatures, message).serialize()
    val expected = Base58.encodeToString(signatures.first())

    if (dryRun) {
        val simulation =
            when (val answer = rpc.simulate(transaction)) {
                is Rpc.Answer.Refused -> failed("simulating $what was refused: ${answer.message}")
                is Rpc.Answer.Unanswered -> failed("could not simulate $what: ${answer.message}")
                is Rpc.Answer.Ok -> answer.value
            }
        simulation.error?.let { failed("$what would fail: $it; ${simulation.logs.lastOrNull().orEmpty()}") }
        return null
    }

    val signature =
        when (val answer = rpc.send(transaction)) {
            is Rpc.Answer.Refused -> failed("$what was refused and $refusedMeans: ${answer.message}")
            is Rpc.Answer.Unanswered -> failed("sent $what $expected but got no answer (${answer.message}); it may have landed. $beforeRetrying")
            is Rpc.Answer.Ok -> answer.value
        }
    repeat(Chips.CONFIRM_ATTEMPTS) {
        when (val landing = rpc.landing(signature)) {
            Rpc.Landing.Confirmed -> return signature
            is Rpc.Landing.Failed -> failed("$what $signature landed and failed: ${landing.error}")
            Rpc.Landing.Pending, null -> pause(Chips.CONFIRM_PAUSE_MILLIS)
        }
    }
    failed("sent $what $signature but it was not confirmed within ${Chips.CONFIRM_ATTEMPTS * Chips.CONFIRM_PAUSE_MILLIS / 1000} s; $beforeRetrying")
}
