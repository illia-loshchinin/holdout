package holdout.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.networking.KtorNetworkDriver
import com.solana.programs.MemoProgram
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.SolanaRpcClient
import com.solana.transaction.Message
import com.solana.transaction.toUnsignedTransaction
import holdout.core.ChainClock
import holdout.core.Rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val address: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

class MainViewModel : ViewModel() {
    // Identity as `contracts/android-app.md` §3 fixes it. `holdout.app` is not
    // registered yet and carries no Digital Asset Links file (T009), so wallets
    // cannot verify this identity until it is — the name and URI are already the
    // production ones so nothing has to change when the domain lands.
    private val walletAdapter =
        MobileWalletAdapter(
            connectionIdentity =
            ConnectionIdentity(
                identityUri = Uri.parse("https://holdout.app"),
                iconUri = Uri.parse("favicon.ico"),
                identityName = "Holdout",
            ),
        ).apply {
            blockchain = Solana.Devnet
        }

    // Devnet only, everywhere (README: nothing here touches mainnet). The app
    // reads chain state through the same rpc-core client the crank uses, so
    // there is one RPC path and not two (research R8, R12).
    private val rpc = SolanaRpcClient(Rpc.DEVNET, KtorNetworkDriver())

    /** The shared client, for everything that is not a wallet interaction. */
    private val rpcClient = Rpc()

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    fun connect(sender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            when (val result = walletAdapter.connect(sender)) {
                is TransactionResult.Success ->
                    _uiState.update {
                        it.copy(
                            address =
                            SolanaPublicKey(
                                result.authResult.accounts
                                    .first()
                                    .publicKey,
                            ).base58(),
                            isLoading = false,
                        )
                    }
                is TransactionResult.NoWalletFound ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
                is TransactionResult.Failure ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
            }
        }
    }

    fun signMessage(
        sender: ActivityResultSender,
        message: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val result =
                walletAdapter.transact(sender) { authResult ->
                    signMessagesDetached(
                        arrayOf(message.encodeToByteArray()),
                        arrayOf(authResult.accounts.first().publicKey),
                    ).messages.first().signatures.first()
                }
            when (result) {
                is TransactionResult.Success ->
                    _uiState.update {
                        it.copy(
                            address =
                            SolanaPublicKey(
                                result.authResult.accounts
                                    .first()
                                    .publicKey,
                            ).base58(),
                            isLoading = false,
                            message = "Signature: ${Base58.encodeToString(result.payload)}",
                        )
                    }
                is TransactionResult.NoWalletFound ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
                is TransactionResult.Failure ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
            }
        }
    }

    /**
     * Spike S1's on-device half (T012a): build a transaction with web3-solana,
     * hand it to the wallet with `signAndSendTransactions`, and land it on
     * devnet. A memo carries no value and creates no account, so the spike can
     * be re-run as often as it takes.
     *
     * This is the exact path every member instruction will take once T040 turns
     * it into `WalletRepository` — blockhash from the RPC, message built here,
     * signed and sent by the wallet, signature back. If it works on a Seeker
     * with Seed Vault and on a plain phone with its own MWA wallet, S1 passes;
     * see docs/holdout_setup_runbook.md step 6.
     */
    fun sendDevnetTransaction(sender: ActivityResultSender) {
        val address = _uiState.value.address ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Fetching a blockhash…") }

            val blockhash = runCatching { rpc.getLatestBlockhash().result?.blockhash }.getOrNull()
            if (blockhash == null) {
                _uiState.update { it.copy(isLoading = false, message = "Devnet did not answer. Try again.") }
                return@launch
            }

            val payer = SolanaPublicKey.from(address)
            val transaction =
                Message
                    .Builder()
                    .addInstruction(MemoProgram.publishMemo(payer, "holdout spike S1"))
                    .setRecentBlockhash(blockhash)
                    .build()
                    .toUnsignedTransaction()

            val result = walletAdapter.transact(sender) { signAndSendTransactions(transaction) }
            when (result) {
                is TransactionResult.Success -> {
                    val signature = result.payload.signatures.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message =
                            signature?.let { bytes ->
                                "Landed: " + Base58.encodeToString(bytes)
                            } ?: "The wallet returned no signature.",
                        )
                    }
                }
                is TransactionResult.NoWalletFound ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
                is TransactionResult.Failure ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
            }
        }
    }

    /**
     * Read the cluster's clock into [clock] (research R7, T033).
     *
     * Failure is silent on purpose: the Daily 25 needs no connection at all
     * (FR-024b), so a phone with no signal counts reps exactly as before and
     * the home screen simply does not show a countdown it cannot vouch for.
     */
    /** Asks the cluster for chain time; true when it answered and [clock] now has it. */
    suspend fun refreshChainTime(clock: ChainClock): Boolean {
        val time = rpcClient.chainTime() ?: return false
        clock.observe(time.unixSeconds, time.slot)
        return true
    }

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            when (val result = walletAdapter.disconnect(sender)) {
                is TransactionResult.Success -> _uiState.value = WalletUiState()
                is TransactionResult.NoWalletFound ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
                is TransactionResult.Failure ->
                    _uiState.update {
                        it.copy(isLoading = false, message = result.message)
                    }
            }
        }
    }
}
