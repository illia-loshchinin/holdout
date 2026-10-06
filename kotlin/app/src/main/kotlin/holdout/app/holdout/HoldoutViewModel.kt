package holdout.app.holdout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainRepository
import holdout.app.chain.ChainView
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.faucet.FaucetClient
import holdout.app.faucet.FaucetOutcome
import holdout.app.storage.Store
import holdout.app.ui.Format
import holdout.app.wallet.SendResult
import holdout.app.wallet.WalletRepository
import holdout.core.Instructions
import holdout.core.Journey
import holdout.core.MembershipSnapshot
import holdout.core.ProfileSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * The staked side of the app: one chain snapshot, refreshed every 30 seconds
 * while the app is in front (research R8), and every action a member takes on
 * it — faucet, join, leave, submit a completed day, claim (US1, US2, US3, US6,
 * US8). Screens only read [state] and call these; what screen a member sees is
 * decided by `Journey.stage`, from the snapshot and chain time.
 */
class HoldoutViewModel(
    private val store: Store,
    private val rpc: Rpc = Rpc(Deployment.rpcUrl),
    private val chain: ChainRepository = ChainRepository(rpc),
    val wallet: WalletRepository = WalletRepository(store, rpc),
    private val faucet: FaucetClient = FaucetClient(rpc, wallet, Deployment.programId),
) : ViewModel() {
    data class UiState(
        /** Null until the first read answers; the screens say "reading devnet" rather than showing nothing. */
        val view: ChainView? = null,
        /** The last read failed; [view] is the previous one, if any. */
        val offline: Boolean = false,
        /** An action is waiting on the wallet or on devnet. */
        val busy: Boolean = false,
        /** What the last action said, for the screen it was taken on. */
        val message: String? = null,
        val messageIsWarning: Boolean = false,
        val lastFaucet: FaucetOutcome? = null,
        /** The last claim that landed — its cohort and signature — for that cohort's link only. */
        val lastClaim: Pair<SolanaPublicKey, String>? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var address: SolanaPublicKey? = null
    private var refresher: Job? = null

    private val _rosters = MutableStateFlow<Map<SolanaPublicKey, List<MembershipSnapshot>>>(emptyMap())

    /** Rosters read so far, by cohort — what the survivor counter, roster and detail screens derive from. */
    val rosters: StateFlow<Map<SolanaPublicKey, List<MembershipSnapshot>>> = _rosters.asStateFlow()

    /** Reads [cohort]'s roster (one `getProgramAccounts`, sliced — R8); a failed read keeps the last one. */
    fun loadRoster(cohort: SolanaPublicKey) {
        // Loads overlap — the 30-second pass, a screen opened twice — and a slow
        // early one must not put an older roster back over a newer one.
        val started = ++rosterLoadsStarted
        viewModelScope.launch {
            val roster = chain.roster(cohort) ?: return@launch
            if (started < (newestRoster[cohort] ?: 0)) return@launch
            newestRoster[cohort] = started
            _rosters.update { it + (cohort to roster) }
        }
    }

    private val _profiles = MutableStateFlow<List<ProfileSnapshot>?>(null)

    /** Every wallet's progress, last read — what the league leaderboards rank (FR-060); null until the first read lands. */
    val profiles: StateFlow<List<ProfileSnapshot>?> = _profiles.asStateFlow()

    /** Reads every `Profile` (one `getProgramAccounts`, R8); a failed read keeps the last one, and an older answer never replaces a newer. */
    fun loadProfiles() {
        val started = ++profileLoadsStarted
        viewModelScope.launch {
            val read = chain.profiles() ?: return@launch
            if (started < newestProfiles) return@launch
            newestProfiles = started
            _profiles.value = read
        }
    }

    private var profileLoadsStarted = 0
    private var newestProfiles = 0

    /** Roster loads started, and per cohort the latest one whose answer was taken — both on the main thread. */
    private var rosterLoadsStarted = 0
    private val newestRoster = mutableMapOf<SolanaPublicKey, Int>()

    /** Starts reading for [wallet] (or with none, for the connect screen's live list), every 30 s. */
    fun watch(wallet: SolanaPublicKey?) {
        if (refresher != null && wallet == address) return
        // Another wallet: the last view was the previous wallet's memberships,
        // profile and balances. It is kept across failed reads of the same
        // wallet only, never shown as the new one's.
        if (wallet != address) _state.value = UiState()
        address = wallet
        refresher?.cancel()
        refresher = viewModelScope.launch {
            while (true) {
                refreshNow()
                delay(REFRESH_MILLIS)
            }
        }
    }

    /** The app left the foreground: no reads until [watch] is called again. The last view stays. */
    fun pause() {
        refresher?.cancel()
        refresher = null
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    /** Reads started, and the latest one whose answer was taken — both on the main thread. */
    private var refreshesStarted = 0
    private var newestApplied = 0

    private suspend fun refreshNow() {
        val wallet = address
        val started = ++refreshesStarted
        val view = chain.load(wallet)
        // The wallet changed while this read was out: its answer is not the new wallet's.
        if (wallet != address) return
        // Reads overlap — the 30-second pass, a pull to refresh, the one after an
        // action — and a slow early one must not put back what a later one replaced:
        // a join or a claim that landed would look undone until the next pass.
        if (started < newestApplied) return
        newestApplied = started
        _state.update { if (view == null) it.copy(offline = true) else it.copy(view = view, offline = false) }
    }

    fun clearMessage() = _state.update { it.copy(message = null, lastFaucet = null) }

    private fun say(message: String?, warning: Boolean = false) = _state.update { it.copy(message = message, messageIsWarning = warning) }

    private fun act(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } finally {
                refreshNow()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun describe(result: SendResult, landed: String): Boolean = when (result) {
        is SendResult.Landed -> {
            say(landed)
            true
        }
        SendResult.NoWallet -> {
            say("No Solana wallet on this phone. Install one to continue.", warning = true)
            false
        }
        is SendResult.Failed -> {
            say(ProgramErrors.explain(result.programError) ?: result.message, warning = true)
            false
        }
    }

    // ---- the faucet (T043) -------------------------------------------------

    fun claimFaucet(sender: ActivityResultSender, now: Long) = act {
        val me = address ?: return@act
        val view = _state.value.view ?: return@act
        val config = view.config ?: return@act say("Holdout is not live on devnet yet.", warning = true)
        val outcome = faucet.claim(sender, me, config, view.profile, view.lamports, now)
        _state.update { it.copy(lastFaucet = outcome) }
    }

    // ---- joining and leaving (T059, T060, T061) ----------------------------

    fun join(sender: ActivityResultSender, h: Holdout, tier: Int, now: Long) = act {
        val me = address ?: return@act
        val view = _state.value.view ?: return@act
        val config = view.config ?: return@act
        val running = Journey.runningCohortFor(view.profile, now)
        val ix = Instructions.joinCohort(Deployment.programId, me, config.skrMint, h.address, tier, running)
        describe(wallet.send(sender, listOf(ix)), "You're in. ${Format.skr(h.cohort.chips[tier])} staked.")
    }

    fun leave(sender: ActivityResultSender, h: Holdout) = act {
        val me = address ?: return@act
        val config = _state.value.view?.config ?: return@act
        val ix = Instructions.leaveCohort(Deployment.programId, me, config.skrMint, h.address)
        describe(wallet.send(sender, listOf(ix)), "You left. Your full stake is back.")
    }

    // ---- a completed day (T065, T066) ---------------------------------------

    /**
     * Sends the completed [day] of [h], recorded first as pending so a failed
     * or offline send leaves "Done — tap to submit" rather than nothing (FR-024b).
     */
    fun submitDay(sender: ActivityResultSender, h: Holdout, day: Int, reps: Int) {
        val me = address ?: return
        // Recorded before anything else, so a send that never starts — another
        // action still busy — leaves "Done — tap to submit", not nothing.
        store.setPendingCohortDay(me.base58(), h.address.base58(), day)
        act {
            val ix = Instructions.submitDay(Deployment.programId, me, h.address, day, proofOf(h.address, day, reps))
            val result = wallet.send(sender, listOf(ix))
            describe(result, "Day ${day + 1} is on chain.")
            // Landed, already on chain (6033), or past saving — not today's day
            // (6031) or past its deadline (6032): nothing is left to submit.
            val settled = result is SendResult.Landed || (result as? SendResult.Failed)?.programError in NOTHING_LEFT_TO_SUBMIT
            if (settled) store.setPendingCohortDay(me.base58(), h.address.base58(), null)
        }
    }

    fun pendingDay(h: Holdout): Int? = address?.let { store.pendingCohortDay(it.base58(), h.address.base58()) }

    // ---- the claim (T081) ----------------------------------------------------

    fun claim(sender: ActivityResultSender, h: Holdout) = act {
        val me = address ?: return@act
        val config = _state.value.view?.config ?: return@act
        val result = wallet.send(sender, listOf(Instructions.claimPayout(Deployment.programId, me, config.skrMint, h.address)))
        if (describe(result, "Claimed.")) _state.update { it.copy(lastClaim = h.address to (result as SendResult.Landed).signature) }
    }

    companion object {
        private const val REFRESH_MILLIS = 30_000L
        const val DAY_ALREADY_COMPLETED = 6033

        /** Refusals after which a pending day has nothing left to send: wrong day, deadline passed, already on chain. */
        private val NOTHING_LEFT_TO_SUBMIT = setOf(6031, 6032, DAY_ALREADY_COMPLETED)

        /**
         * The 32 bytes a completed day carries: SHA-256 of the day's summary —
         * cohort, day, reps and the pose model it was counted with (research
         * R16). Emitted, not stored; nothing verifies it on devnet, which the
         * README names as a weakness.
         */
        fun proofOf(cohort: SolanaPublicKey, day: Int, reps: Int): ByteArray = MessageDigest.getInstance("SHA-256").digest(
            "holdout-day-v1|${cohort.base58()}|$day|$reps|pose_landmarker_full".encodeToByteArray(),
        )
    }
}

/** The program's refusals, in words a member can act on (contracts/program-instructions §5). */
object ProgramErrors {
    fun explain(code: Int?): String? = when (code) {
        6021 -> "Joining for this Holdout has closed."
        6023 -> "This Holdout needs a 7-day clear first."
        6025 -> "You already hold a place in a Holdout that hasn't started. Leave it first, or wait for it to start."
        6026 -> "This Holdout would start before your current one ends."
        6030 -> "This Holdout isn't running right now."
        6031 -> "That's not today's day. Pull to refresh."
        6032 -> "The deadline for that day has passed."
        6033 -> "Today is already on chain."
        6043 -> "Nothing to claim here."
        6044 -> "Already claimed."
        6050 -> "Today's test tokens are already claimed."
        6051, 6052 -> "The faucet can't pay out right now. It is not something you did."
        // The token program's InsufficientFunds: not enough SKR for the chip.
        1 -> "Not enough SKR for that chip. Get test tokens first."
        else -> null
    }
}
