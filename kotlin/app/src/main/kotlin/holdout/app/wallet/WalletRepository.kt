package holdout.app.wallet

import android.net.Uri
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.TransactionInstruction
import com.solana.transaction.toUnsignedTransaction
import holdout.app.BuildConfig
import holdout.app.chain.Deployment
import holdout.app.storage.Store
import holdout.app.storage.WalletSession
import holdout.core.Rpc
import holdout.core.Transactions
import kotlinx.coroutines.delay

/** What became of a transaction the member signed. */
sealed interface SendResult {
    data class Landed(val signature: String) : SendResult

    /** No MWA wallet on the phone (ES-0). */
    data object NoWallet : SendResult

    /** Declined, refused by the program, or never confirmed. [message] is said to the member as is. */
    data class Failed(val message: String, val programError: Int? = null) : SendResult
}

/**
 * The wallet, through Mobile Wallet Adapter (T040, T061, contracts/android-app §3).
 *
 * **The app never holds a member's key** (constitution I): every join, leave,
 * completed day and claim is built here, handed to the wallet with
 * `signAndSendTransactions`, and paid for by the member. The auth token the
 * wallet issues is kept, so a restart reconnects without asking again.
 */
class WalletRepository(private val store: Store, private val rpc: Rpc) {
    private val adapter =
        MobileWalletAdapter(
            connectionIdentity =
            ConnectionIdentity(
                // Not registered yet and no Digital Asset Links file (T009): wallets
                // show the raw package until it is. The production URI all the same.
                identityUri = Uri.parse("https://holdout.app"),
                iconUri = Uri.parse("favicon.ico"),
                identityName = "Holdout",
            ),
        ).apply {
            blockchain = Solana.Devnet
            authToken = store.walletSession()?.authToken
        }

    /** The wallet a previous run connected, if any — restored without a prompt. */
    val restored: SolanaPublicKey? get() = store.walletSession()?.address?.let { runCatching { SolanaPublicKey.from(it) }.getOrNull() }

    sealed interface ConnectResult {
        data class Connected(val address: SolanaPublicKey) : ConnectResult

        data object NoWallet : ConnectResult

        data class Failed(val message: String) : ConnectResult
    }

    suspend fun connect(sender: ActivityResultSender): ConnectResult = when (val result = adapter.connect(sender)) {
        is TransactionResult.Success -> {
            val address = SolanaPublicKey(result.authResult.accounts.first().publicKey)
            store.setWalletSession(WalletSession(address.base58(), adapter.authToken))
            ConnectResult.Connected(address)
        }
        is TransactionResult.NoWalletFound -> ConnectResult.NoWallet
        is TransactionResult.Failure -> ConnectResult.Failed(result.message)
    }

    suspend fun disconnect(sender: ActivityResultSender) {
        runCatching { adapter.disconnect(sender) }
        adapter.authToken = null
        store.setWalletSession(null)
    }

    /**
     * Builds [instructions] into one transaction — the member first, as fee
     * payer — has the wallet sign and send it, and waits for devnet to confirm.
     */
    suspend fun send(sender: ActivityResultSender, instructions: List<TransactionInstruction>): SendResult {
        val blockhash = rpc.latestBlockhash() ?: return SendResult.Failed("${Deployment.networkName} did not answer. Check the connection and try again.")
        // Never Message.Builder directly: it miscounts an account with mixed flags (see Transactions).
        val transaction = Transactions.message(instructions, blockhash).toUnsignedTransaction()
        if (signOnly) return signThenSend(sender, transaction)
        val result = adapter.transact(sender) { signAndSendTransactions(transaction) }
        store.walletSession()?.let { store.setWalletSession(it.copy(authToken = adapter.authToken)) }
        val signature =
            when (result) {
                is TransactionResult.Success -> result.payload.signatures.firstOrNull()?.let { Base58.encodeToString(it) } ?: return SendResult.Failed("The wallet returned no signature.")
                is TransactionResult.NoWalletFound -> return SendResult.NoWallet
                is TransactionResult.Failure -> return failure(result.message)
            }
        return confirm(rpc, signature)
    }

    /**
     * Debug builds pointed at a local validator (quickstart §5a): the wallet
     * sends to its own cluster — devnet — so here it only signs, and the app
     * sends to the validator it reads from. Never in a release build.
     */
    private val signOnly: Boolean = BuildConfig.DEBUG && Deployment.rpcUrl != Rpc.DEVNET

    private suspend fun signThenSend(sender: ActivityResultSender, transaction: com.solana.transaction.Transaction): SendResult {
        val result = adapter.transact(sender) { signTransactions(arrayOf(transaction.serialize())) }
        store.walletSession()?.let { store.setWalletSession(it.copy(authToken = adapter.authToken)) }
        val signed =
            when (result) {
                is TransactionResult.Success -> result.payload.signedPayloads.firstOrNull() ?: return SendResult.Failed("The wallet returned no signed transaction.")
                is TransactionResult.NoWalletFound -> return SendResult.NoWallet
                is TransactionResult.Failure -> return failure(result.message)
            }
        return when (val answer = rpc.send(signed)) {
            is Rpc.Answer.Ok -> confirm(rpc, answer.value)
            is Rpc.Answer.Refused -> failure(answer.message)
            is Rpc.Answer.Unanswered -> SendResult.Failed("The local validator did not answer: ${answer.message}")
        }
    }

    companion object {
        private const val CONFIRM_ATTEMPTS = 30
        private const val CONFIRM_PAUSE_MILLIS = 1_000L

        /** Waits for [signature] to confirm on devnet — about 30 s, then says it may still land. */
        suspend fun confirm(rpc: Rpc, signature: String): SendResult {
            repeat(CONFIRM_ATTEMPTS) {
                when (val landing = rpc.landing(signature)) {
                    Rpc.Landing.Confirmed -> return SendResult.Landed(signature)
                    is Rpc.Landing.Failed -> return failure(landing.error)
                    Rpc.Landing.Pending, null -> delay(CONFIRM_PAUSE_MILLIS)
                }
            }
            return SendResult.Failed("Sent, but devnet has not confirmed it yet. It may still land — pull to refresh in a minute.")
        }

        fun failure(message: String): SendResult.Failed = SendResult.Failed(message, programErrorIn(message))

        /** The program error a failure names, however the node or the wallet words it. */
        fun programErrorIn(message: String?): Int? {
            if (message == null) return null
            Regex("custom program error: 0x([0-9a-fA-F]+)").find(message)?.let { return it.groupValues[1].toInt(16) }
            Regex("\"Custom\"\\s*:\\s*(\\d+)").find(message)?.let { return it.groupValues[1].toInt() }
            Regex("Custom\\((\\d+)\\)").find(message)?.let { return it.groupValues[1].toInt() }
            return null
        }
    }
}
