package holdout.app.wallet

import android.net.Uri
import android.util.Log
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.TransactionInstruction
import com.solana.transaction.toUnsignedTransaction
import holdout.app.BuildConfig
import holdout.app.chain.Deployment
import holdout.app.storage.Store
import holdout.app.storage.WalletSession
import holdout.core.ProgramErrorCode
import holdout.core.Rpc
import holdout.core.Transactions
import kotlinx.coroutines.delay

/** What became of a transaction the member signed. */
sealed interface SendResult {
    data class Landed(val signature: String) : SendResult

    /** No MWA wallet on the phone (ES-0). */
    data object NoWallet : SendResult

    /**
     * Declined, refused by the program, or never confirmed. [message] is said to
     * the member as is. [beforeWallet]: devnet did not answer before the wallet
     * could open, so the member was asked nothing. [declined]: the member said
     * no in the wallet, so nothing was signed or sent. [mayStillLand]: it left
     * the wallet and devnet has not confirmed it either way (see [TxOutcome]).
     */
    data class Failed(
        val message: String,
        val programError: Int? = null,
        val beforeWallet: Boolean = false,
        val declined: Boolean = false,
        val mayStillLand: Boolean = false,
    ) : SendResult
}

/**
 * The wallet, through Mobile Wallet Adapter (T040, T061, contracts/android-app §3).
 *
 * **The app never holds a member's key** (constitution I): every join, leave,
 * completed day and claim is built here, handed to the wallet with
 * `signAndSendTransactions`, and paid for by the member. The auth token the
 * wallet issues is kept, so a restart reconnects without asking again.
 */
open class WalletRepository(private val store: Store, private val rpc: Rpc) {
    // Made on first use, not with the repository: it needs Android's Uri, and the
    // view model that holds this repository is built in JVM tests too (005 T019).
    private val adapter by lazy {
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
            if (Network.of(result.authResult) == Network.Other) {
                // Authorized on another network: nothing is kept, so the next connect asks afresh.
                adapter.authToken = null
                ConnectResult.Failed(WRONG_NETWORK)
            } else {
                val address = SolanaPublicKey(result.authResult.accounts.first().publicKey)
                store.setWalletSession(WalletSession(address.base58(), adapter.authToken))
                ConnectResult.Connected(address)
            }
        }
        is TransactionResult.NoWalletFound -> ConnectResult.NoWallet
        is TransactionResult.Failure -> ConnectResult.Failed(result.message)
    }

    /**
     * Which network the wallet authorized Holdout on, from the chains it reports
     * for its accounts (MWA 2.0). Holdout is devnet only (constitution,
     * Hackathon Constraints): a wallet that reports any other network is
     * refused before anything is signed, and so is one that reports devnet
     * alongside another network. A wallet that reports no chains
     * (MWA 1.x) cannot be checked, and is allowed.
     */
    private enum class Network {
        Devnet,
        Other,
        Unknown,
        ;

        companion object {
            fun of(auth: MobileWalletAdapterClient.AuthorizationResult): Network {
                val chains = auth.accounts.orEmpty().flatMap { it.chains?.toList().orEmpty() }
                Log.i(TAG, "wallet authorized chains: $chains")
                // Devnet only when every chain reported is devnet: with any other
                // among them, the first account — the one connect keeps — may not be.
                return when {
                    chains.isEmpty() -> Unknown
                    chains.all { it == DEVNET_CHAIN } -> Devnet
                    else -> Other
                }
            }
        }
    }

    suspend fun disconnect(sender: ActivityResultSender) {
        runCatching { adapter.disconnect(sender) }
        adapter.authToken = null
        store.setWalletSession(null)
    }

    /**
     * Builds [instructions] into one transaction — the member first, as fee
     * payer — has the wallet sign and send it, and waits for devnet to confirm.
     * [onPhase] hears where it stands, for the signing screens (rulings G7):
     * the wallet holds the screen, then — approved and sent — devnet confirms.
     */
    open suspend fun send(sender: ActivityResultSender, instructions: List<TransactionInstruction>, onPhase: (SendPhase) -> Unit = {}): SendResult {
        val blockhash = rpc.latestBlockhash() ?: return SendResult.Failed("${Deployment.networkName} did not answer. Check the connection and try again.", beforeWallet = true)
        // Never Message.Builder directly: it miscounts an account with mixed flags (see Transactions).
        val transaction = Transactions.message(instructions, blockhash).toUnsignedTransaction()
        onPhase(SendPhase.WaitingForWallet)
        if (signOnly) return signThenSend(sender, transaction, onPhase)
        // Checked inside the session, on the authorization the wallet has just
        // confirmed, before anything is signed.
        val result = adapter.transact(sender) { auth -> if (Network.of(auth) == Network.Other) null else signAndSendTransactions(transaction) }
        store.walletSession()?.let { store.setWalletSession(it.copy(authToken = adapter.authToken)) }
        val signature =
            when (result) {
                is TransactionResult.Success -> result.payload?.signatures?.firstOrNull()?.let { Base58.encodeToString(it) } ?: return wrongNetworkOr("The wallet returned no signature.", result.payload == null)
                is TransactionResult.NoWalletFound -> return SendResult.NoWallet
                is TransactionResult.Failure -> return WalletFailures.of(result.message, result.e)
            }
        onPhase(SendPhase.Confirming)
        return confirm(rpc, signature)
    }

    /**
     * Debug builds pointed at a local validator (quickstart §5a): the wallet
     * sends to its own cluster — devnet — so here it only signs, and the app
     * sends to the validator it reads from. Never in a release build.
     */
    private val signOnly: Boolean = BuildConfig.DEBUG && !Deployment.isDevnet

    private suspend fun signThenSend(sender: ActivityResultSender, transaction: com.solana.transaction.Transaction, onPhase: (SendPhase) -> Unit): SendResult {
        val result = adapter.transact(sender) { auth -> if (Network.of(auth) == Network.Other) null else signTransactions(arrayOf(transaction.serialize())) }
        store.walletSession()?.let { store.setWalletSession(it.copy(authToken = adapter.authToken)) }
        val signed =
            when (result) {
                is TransactionResult.Success -> result.payload?.signedPayloads?.firstOrNull() ?: return wrongNetworkOr("The wallet returned no signed transaction.", result.payload == null)
                is TransactionResult.NoWalletFound -> return SendResult.NoWallet
                is TransactionResult.Failure -> return WalletFailures.of(result.message, result.e)
            }
        onPhase(SendPhase.Confirming)
        return when (val answer = rpc.send(signed)) {
            is Rpc.Answer.Ok -> confirm(rpc, answer.value)
            is Rpc.Answer.Refused -> failure(answer.message)
            // It may have reached the validator all the same: its own signature says.
            is Rpc.Answer.Unanswered ->
                com.solana.transaction.Transaction.from(signed).signatures.firstOrNull()
                    ?.let { confirm(rpc, Base58.encodeToString(it)) }
                    ?: SendResult.Failed("${Deployment.networkName} did not answer: ${answer.message}", mayStillLand = true)
        }
    }

    /** A session refused for the network: the saved authorization is dropped, so reconnecting asks afresh. */
    private fun wrongNetworkOr(otherwise: String, wrongNetwork: Boolean): SendResult.Failed {
        if (!wrongNetwork) return SendResult.Failed(otherwise)
        adapter.authToken = null
        store.walletSession()?.let { store.setWalletSession(it.copy(authToken = null)) }
        return SendResult.Failed(WRONG_NETWORK)
    }

    companion object {
        private const val TAG = "HoldoutWallet"
        private const val DEVNET_CHAIN = "solana:devnet"

        /** Said when the wallet authorized another network — nothing was signed. */
        const val WRONG_NETWORK =
            "Your wallet isn't on devnet, so nothing was signed. Switch it to devnet, then connect again (Me → Disconnect wallet)."

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
            return SendResult.Failed("Sent, but devnet has not confirmed it yet. It may still land — pull to refresh in a minute.", mayStillLand = true)
        }

        fun failure(message: String): SendResult.Failed = SendResult.Failed(message, ProgramErrorCode.of(message))
    }
}
