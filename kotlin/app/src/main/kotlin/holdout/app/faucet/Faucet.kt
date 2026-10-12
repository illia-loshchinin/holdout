package holdout.app.faucet

import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import holdout.app.BuildConfig
import holdout.app.chain.Deployment
import holdout.app.wallet.SendResult
import holdout.app.wallet.WalletRepository
import holdout.core.ConfigSnapshot
import holdout.core.HoldoutDay
import holdout.core.Instructions
import holdout.core.ProfileSnapshot
import holdout.core.Rpc
import holdout.core.Transactions
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * The faucet's fee sponsor (research R13): the one key the app signs with.
 *
 * Its secret ships in the APK **by design**, and the README says so: it holds
 * a 0.05 SOL float and nothing else, `claim_faucet` reimburses it in the same
 * instruction, a daily lamport budget bounds what anyone can make it cost, and
 * anyone can refill it with `top_up_sponsor`. It cannot touch a stake or a pool.
 */
class SponsorKey private constructor(private val seed: ByteArray, val publicKey: SolanaPublicKey) {
    fun sign(message: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, Ed25519PrivateKeyParameters(seed, 0))
        update(message, 0, message.size)
        generateSignature()
    }

    companion object {
        /** From the 64-byte Solana keypair as hex, as the build writes it; null when absent or not a matching pair. */
        fun fromHex(hex: String): SponsorKey? {
            if (hex.length != 128) return null
            val bytes = ByteArray(64) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
            val seed = bytes.copyOf(32)
            val derived = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
            if (!derived.contentEquals(bytes.copyOfRange(32, 64))) return null
            return SponsorKey(seed, SolanaPublicKey(derived))
        }

        val bundled: SponsorKey? by lazy { fromHex(BuildConfig.SPONSOR_KEYPAIR_HEX) }
    }
}

/** What the faucet sheet shows after a tap (ES-0, ES-0a, ES-0b). */
sealed interface FaucetOutcome {
    data class Funded(val signature: String) : FaucetOutcome

    /** One claim a day: [returnsAt] is the next 05:00 UTC, in chain seconds (ES-0a). */
    data class UsedToday(val returnsAt: Long) : FaucetOutcome

    /** The vault or the day's budget is spent, or no sponsor can pay — never the member's fault (ES-0b). */
    data object CannotPayOut : FaucetOutcome

    data object NoWallet : FaucetOutcome

    data class Failed(val message: String) : FaucetOutcome
}

/**
 * `claim_faucet` from the app (T043, contracts/android-app §3): sponsor-paid
 * for a wallet with no SOL — no member signature at all — and through the
 * wallet, member-paid, for one that has some, or when the sponsor's float is
 * empty (FR-042b).
 */
class FaucetClient(private val rpc: Rpc, private val wallet: WalletRepository, private val programId: SolanaPublicKey, private val sponsor: SponsorKey? = SponsorKey.bundled) {
    suspend fun claim(sender: ActivityResultSender, recipient: SolanaPublicKey, config: ConfigSnapshot, profile: ProfileSnapshot?, lamports: Long, now: Long): FaucetOutcome {
        val today = HoldoutDay.indexOf(now)
        if (profile?.claimedFaucetOn(today) == true) return FaucetOutcome.UsedToday(HoldoutDay.endOf(today))

        val viaSponsor = sponsor?.takeIf { it.publicKey == config.sponsor }
        if (!canPayFees(lamports)) return viaSponsor?.let { sponsorPaid(it, recipient, config, today) } ?: FaucetOutcome.CannotPayOut
        val ix = Instructions.claimFaucet(programId, recipient, recipient, config.skrMint, config.sponsor)
        return when (val sent = wallet.send(sender, listOf(ix))) {
            is SendResult.Landed -> FaucetOutcome.Funded(sent.signature)
            SendResult.NoWallet -> FaucetOutcome.NoWallet
            is SendResult.Failed -> classify(sent, today) ?: FaucetOutcome.Failed(sent.message)
        }
    }

    /**
     * Built with web3-solana, signed by the bundled sponsor, sent with rpc-core — no MWA in it (R13).
     *
     * Only a refusal is the faucet's to own (ES-0b): a node that does not answer,
     * or a claim sent but not yet confirmed, is said as it is, so the member
     * tries again rather than being told the faucet cannot pay.
     */
    private suspend fun sponsorPaid(key: SponsorKey, recipient: SolanaPublicKey, config: ConfigSnapshot, today: Long): FaucetOutcome {
        val blockhash = rpc.latestBlockhash() ?: return FaucetOutcome.Failed("${Deployment.networkName} did not answer. Check the connection and try again.")
        // The sponsor both pays and is reimbursed: exactly the mixed-flags case Transactions exists for.
        val message = Transactions.message(listOf(Instructions.claimFaucet(programId, key.publicKey, recipient, config.skrMint, config.sponsor)), blockhash)
        val signature = key.sign(message.serialize())
        val bytes = Transaction(listOf(signature), message).serialize()
        val sent =
            when (val answer = rpc.send(bytes)) {
                is Rpc.Answer.Ok -> WalletRepository.confirm(rpc, answer.value)
                // Refused before it ran — the float or the day's budget is spent, or the program said no.
                is Rpc.Answer.Refused -> return classify(WalletRepository.failure(answer.message), today) ?: FaucetOutcome.CannotPayOut
                is Rpc.Answer.Unanswered -> WalletRepository.confirm(rpc, Base58.encodeToString(signature))
            }
        return when (sent) {
            is SendResult.Landed -> FaucetOutcome.Funded(sent.signature)
            is SendResult.Failed -> classify(sent, today) ?: FaucetOutcome.Failed(sent.message)
            SendResult.NoWallet -> FaucetOutcome.CannotPayOut
        }
    }

    private fun classify(failed: SendResult.Failed, today: Long): FaucetOutcome? = when (failed.programError) {
        FAUCET_ALREADY_CLAIMED_TODAY -> FaucetOutcome.UsedToday(HoldoutDay.endOf(today))
        FAUCET_CAP_REACHED, FAUCET_EMPTY -> FaucetOutcome.CannotPayOut
        else -> null
    }

    companion object {
        /** Enough SOL for a member to pay a transaction's fee and first-time rent themselves — a claim or a join. */
        const val MEMBER_CAN_PAY_LAMPORTS = 5_000_000L

        /** Whether [lamports] is enough SOL to pay a transaction's fee (see [MEMBER_CAN_PAY_LAMPORTS]). */
        fun canPayFees(lamports: Long): Boolean = lamports >= MEMBER_CAN_PAY_LAMPORTS

        const val FAUCET_ALREADY_CLAIMED_TODAY = 6050
        const val FAUCET_CAP_REACHED = 6051
        const val FAUCET_EMPTY = 6052
    }
}
