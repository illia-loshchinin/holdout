package holdout.app.wallet

import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import holdout.app.holdout.ProgramErrors
import holdout.core.ProgramErrorCode

/**
 * Where a transaction the member signs stands, as [WalletRepository.send]
 * reports it while it runs: the wallet holds the screen, then devnet confirms.
 * Approved and sent arrive together — `signAndSendTransactions` returns once
 * the wallet has done both.
 */
enum class SendPhase {
    /** The wallet app is open, asking the member. Nothing moves until they approve. */
    WaitingForWallet,

    /** Approved in the wallet and sent to devnet; waiting for it to confirm. */
    Confirming,
}

/** The flows Holdout shows its own signing screens for (rulings G7): leave and the faucet stay inline. */
enum class SignAction { Join, Submit, Claim }

/** What a signing screen shows: waiting for the wallet, the confirming checklist, or a result. */
sealed interface TxPhase {
    data object WaitingForWallet : TxPhase

    data object Confirming : TxPhase

    data class Done(val outcome: TxOutcome) : TxPhase

    companion object {
        fun of(phase: SendPhase): TxPhase = when (phase) {
            SendPhase.WaitingForWallet -> WaitingForWallet
            SendPhase.Confirming -> Confirming
        }
    }
}

/** A signing flow's result (rulings G7, C-D14). */
sealed interface TxOutcome {
    /** On chain. [signature] is null when the program said it was already there (a day already submitted). */
    data class Landed(val signature: String?) : TxOutcome

    /** The member said no in the wallet: nothing was signed or sent. */
    data object Declined : TxOutcome

    /**
     * Devnet did not confirm it. [mayStillLand]: it left the wallet, so it may
     * land yet — the screen never says the funds are untouched then.
     */
    data class Failed(val mayStillLand: Boolean, val message: String) : TxOutcome

    /** Join only: joining closed before the transaction landed; nothing was taken (ES-12). */
    data object JoiningClosed : TxOutcome

    /** Submit only: the day's deadline passed before it landed (ES-4): the forfeit. */
    data object DeadlinePassed : TxOutcome

    /** Too little devnet SOL to pay for it: the faucet sheet, and back (ES-0a). */
    data object NotEnoughSol : TxOutcome

    /** No Mobile Wallet Adapter wallet on the phone (ES-0). */
    data object NoWallet : TxOutcome

    /** Any other refusal by the program, in words a member can act on. */
    data class Refused(val message: String) : TxOutcome

    /** Whether the result screen offers "Try again": the member declined, or it failed with nothing sent. */
    val canRetry: Boolean get() = this == Declined || (this is Failed && !mayStillLand)

    companion object {
        /**
         * The outcome of [action] from what the wallet and devnet said.
         * [lowOnSol]: the wallet held too little SOL to pay for a transaction when
         * it was sent — a failure then is read as that, short of a devnet that
         * never answered (`beforeWallet`), which proves nothing about SOL.
         */
        fun of(action: SignAction, result: SendResult, lowOnSol: Boolean): TxOutcome = when (result) {
            is SendResult.Landed -> Landed(result.signature)
            SendResult.NoWallet -> NoWallet
            is SendResult.Failed -> when {
                result.declined -> Declined
                action == SignAction.Join && result.programError == JOINING_CLOSED -> JoiningClosed
                action == SignAction.Submit && result.programError == DEADLINE_PASSED -> DeadlinePassed
                action == SignAction.Submit && result.programError == DAY_ALREADY_ON_CHAIN -> Landed(null)
                result.beforeWallet -> Failed(mayStillLand = false, message = result.message)
                SHORT_OF_SOL.containsMatchIn(result.message) -> NotEnoughSol
                result.programError != null -> Refused(ProgramErrors.explain(result.programError) ?: result.message)
                lowOnSol -> NotEnoughSol
                else -> Failed(result.mayStillLand, result.message)
            }
        }

        const val JOINING_CLOSED = 6021
        const val DEADLINE_PASSED = 6032
        const val DAY_ALREADY_ON_CHAIN = 6033

        /** How the runtime, a node or a wallet says the fee payer cannot pay the fee or the rent. */
        private val SHORT_OF_SOL = Regex(
            "InsufficientFundsForFee|insufficient funds for fee|InsufficientFundsForRent|insufficient funds for rent|no record of a prior credit|insufficient lamports",
            RegexOption.IGNORE_CASE,
        )
    }
}

/**
 * The wallet's refusals, read from Mobile Wallet Adapter's error codes
 * (`com.solana.mobilewalletadapter.common.ProtocolContract.ERROR_*`) on the
 * exception `TransactionResult.Failure` carries — never from its wording.
 */
object WalletFailures {
    private const val ERROR_AUTHORIZATION_FAILED = -1
    private const val ERROR_NOT_SIGNED = -3
    private const val ERROR_NOT_SUBMITTED = -4

    /** What the client library says when it could not even reach a wallet: nothing was sent. */
    private const val NEVER_OPENED = "Timed out waiting to send association intent"

    fun of(message: String, error: Throwable?): SendResult.Failed {
        val code = (error as? JsonRpc20Client.JsonRpc20RemoteException)?.code
        val declined = code == ERROR_NOT_SIGNED || code == ERROR_AUTHORIZATION_FAILED
        // A remote refusal other than "not submitted" came before anything was sent. A session
        // that broke after the wallet opened (IO, timeout, cancelled) may have sent it.
        val mayStillLand = when {
            declined -> false
            code != null -> code == ERROR_NOT_SUBMITTED
            message.startsWith(NEVER_OPENED) -> false
            else -> true
        }
        return SendResult.Failed(message, ProgramErrorCode.of(message), declined = declined, mayStillLand = mayStillLand)
    }
}
