package holdout.app.holdout

import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainReads
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
import holdout.core.Leaderboard
import holdout.core.MembershipSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    private val chain: ChainReads = ChainRepository(rpc),
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
        /** The completed day last sent while the chain could be reached, by any path — kept in the store too (FR-024b, T098). */
        val autoSubmit: AutoSubmit = AutoSubmit(),
        /** A member's "Check again" while the chain can't be reached (spec 005). */
        val check: ChainCheck = ChainCheck(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var address: SolanaPublicKey? = null
    private var refresher: Job? = null
    private var checking: Job? = null

    private val _rosters = MutableStateFlow<Map<SolanaPublicKey, List<MembershipSnapshot>>>(emptyMap())

    /** Rosters read so far, by cohort — what the survivor counter, roster and detail screens derive from. */
    val rosters: StateFlow<Map<SolanaPublicKey, List<MembershipSnapshot>>> = _rosters.asStateFlow()

    /**
     * Reads [cohort]'s roster (one `getProgramAccounts`, sliced — R8); a failed read keeps the last one.
     * It runs in [scope]: a check's roster read is a child of the check, so leaving the front cancels it.
     */
    fun loadRoster(cohort: SolanaPublicKey, scope: CoroutineScope = viewModelScope) {
        // Loads overlap — the 30-second pass, a screen opened twice — and a slow
        // early one must not put an older roster back over a newer one.
        val started = ++rosterLoadsStarted
        scope.launch {
            val roster = chain.roster(cohort) ?: return@launch
            if (started < (newestRoster[cohort] ?: 0)) return@launch
            newestRoster[cohort] = started
            _rosters.update { it + (cohort to roster) }
        }
    }

    private val _leaderboard = MutableStateFlow<Leaderboard.Snapshot?>(null)

    /** The leaderboards' last read — cohorts, every profile, cohorts again (FR-060); null until the first read lands. */
    val leaderboard: StateFlow<Leaderboard.Snapshot?> = _leaderboard.asStateFlow()

    /** Reads a leaderboard snapshot (R8); a failed read keeps the last one, and an older answer never replaces a newer. */
    fun loadLeaderboard() {
        val started = ++profileLoadsStarted
        viewModelScope.launch {
            val read = chain.leaderboard() ?: return@launch
            if (started < newestProfiles) return@launch
            newestProfiles = started
            _leaderboard.value = read
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
        // wallet only, never shown as the new one's. Its automatic send starts
        // from what this phone kept for it, so a restart does not ask again (T098).
        if (wallet != address) {
            // The previous wallet's check goes with its state: it must not finish into this one's.
            checking?.cancel()
            checking = null
            _state.value = UiState(autoSubmit = wallet?.let { AutoSubmit.restore(store, it.base58()) } ?: AutoSubmit())
        }
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
        // A check's reads are foreground-only too (FR-008): none goes on, and none is left "Checking…".
        checking?.cancel()
        checking = null
        _state.update { it.copy(check = ChainCheck()) }
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
        // An answer to a read started after the loss is the network back: the send
        // re-arms now (T098). One already out when it went proves nothing.
        val back = started > readsBeforeLoss
        _state.update {
            if (view == null) {
                it.copy(offline = true)
            } else {
                it.copy(view = view, offline = false, autoSubmit = if (back) it.autoSubmit.answered() else it.autoSubmit, check = it.check.answered())
            }
        }
        if (view != null && back) keepAutoSubmit()
    }

    /**
     * The member's "Check again", by the button or the pull (spec 005): while
     * the last read failed, reads the chain now — the snapshot here and, at
     * the same time, [alsoRead] (chain time, the roster on screen). A second
     * ask while one is out joins it. Only while in front, as every read
     * (FR-008). It sends nothing: a waiting day goes on as 001 FR-024b says
     * once the chain answers (FR-004).
     */
    fun checkNow(alsoRead: suspend (CoroutineScope) -> Unit = {}) {
        if (refresher == null) return
        val step = _state.value.check.request(_state.value.offline)
        if (!step.read) return
        _state.update { it.copy(check = step.next) }
        val wallet = address
        // Any earlier check has finished (a request while one is out joins it); what it left running goes too.
        checking?.cancel()
        checking = viewModelScope.launch {
            val check = this
            // A failing side read (chain time, the roster) must not end the check
            // without its answer, or "Checking…" would stay on with nothing to clear it.
            val also =
                launch {
                    try {
                        // [check] lets a read that is not awaited (the roster) stop with the check.
                        alsoRead(check)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            try {
                refreshNow()
                also.join()
            } catch (e: CancellationException) {
                // This check's own cancellation ([pause], another wallet) ends it here, its state already
                // cleared. A cancellation inside a read, with the check still live, is a read that did not answer.
                currentCoroutineContext().ensureActive()
            } catch (_: Exception) {
                // A read that throws is a read that did not answer: the check still ends.
            }
            // Another wallet's check: its state was reset with the wallet's.
            if (wallet == address) _state.update { it.copy(check = it.check.finished(answered = !it.offline)) }
        }
    }

    /** Keeps the automatic send's state for the connected wallet, so it outlives the process (T098). */
    private fun keepAutoSubmit() {
        address?.let { _state.value.autoSubmit.saveTo(store, it.base58()) }
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
        // A running cohort the member has missed a deadline in and nobody has recorded yet still holds the
        // slot (ObligationsOverlap, 6026): its record goes first in the same transaction (FR-037g, ES-17).
        val instructions = Journey.joinInstructions(Deployment.programId, me, config.skrMint, config.feeDestination, config.developerDestination, h.address, tier, view.joinPlan(now))
        describe(wallet.send(sender, instructions), "You're in. ${Format.skr(h.cohort.chips[tier])} staked.")
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
     * or offline send leaves the day waiting to be sent again rather than nothing (FR-024b).
     */
    fun submitDay(sender: ActivityResultSender, h: Holdout, day: Int, reps: Int) {
        val me = address ?: return
        // Recorded before anything else, so a send that never starts — another
        // action still busy — leaves the day pending, not nothing.
        store.setPendingCohortDay(me.base58(), h.address.base58(), day)
        act {
            // Sent — from the sitting's exit, a tap or by itself: not sent by itself again
            // until the connection drops and returns, even before the chain shows it landed.
            // Kept on the phone too, so a decline holds across a restart of the app.
            _state.update { it.copy(autoSubmit = it.autoSubmit.sent(h.address.base58(), day)) }
            keepAutoSubmit()
            val ix = Instructions.submitDay(Deployment.programId, me, h.address, day, proofOf(h.address, day, reps))
            val result = wallet.send(sender, listOf(ix))
            describe(result, "Day ${day + 1} is on chain.")
            if ((result as? SendResult.Failed)?.beforeWallet == true && me == address) {
                // Devnet did not answer before the wallet could open: nothing was declined,
                // so it goes by itself again at the chain's next answer — not this send's
                // own closing read, which would only ask devnet again at once.
                readsBeforeLoss = refreshesStarted + 1
                _state.update { it.copy(autoSubmit = it.autoSubmit.unreached(h.address.base58(), day)) }
                keepAutoSubmit()
            }
            // Landed, already on chain (6033), or past saving — not today's day
            // (6031) or past its deadline (6032): nothing is left to submit.
            val settled = result is SendResult.Landed || (result as? SendResult.Failed)?.programError in NOTHING_LEFT_TO_SUBMIT
            if (settled) {
                store.setPendingCohortDay(me.base58(), h.address.base58(), null)
                // Nothing pending, nothing held back: the kept send goes with it — this wallet's only.
                if (me == address) keepAutoSubmit()
            }
        }
    }

    fun pendingDay(h: Holdout): Int? = address?.let { store.pendingCohortDay(it.base58(), h.address.base58()) }

    /**
     * The default network went away: a waiting day may open the wallet again
     * once the chain answers — the next successful read, which the network's
     * return asks for at once — never before (T098).
     */
    fun networkLost() {
        // Called from the system's network thread: counted on the main one, in order with the return's read.
        viewModelScope.launch {
            readsBeforeLoss = refreshesStarted
            _state.update { it.copy(autoSubmit = it.autoSubmit.lost()) }
            // Kept, so a restart before the network returns still re-arms it then.
            keepAutoSubmit()
        }
    }

    /** The default-network callback [followNetwork] registered, until [onCleared]. */
    private var network: Pair<ConnectivityManager, ConnectivityManager.NetworkCallback>? = null

    /**
     * Follows the phone's default network from now until this view model is
     * cleared — not only while the app is in front. The automatic send opens
     * the wallet full screen and Holdout stops behind it; a drop and return
     * there, then a decline, must still re-arm the send (T098), so a loss is
     * recorded whenever it comes. A return reads the chain at once, and calls
     * [back], only while in front ([watch] to [pause]): nothing is read in the
     * background, and [watch] reads at once on the way back anyway. Called
     * again, it keeps the callback it has.
     */
    fun followNetwork(connectivity: ConnectivityManager?, back: () -> Unit) {
        if (network != null || connectivity == null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            // A connection coming back is read at once, not at the next 30-second pass,
            // so a completed day waiting for the network goes as soon as it can (FR-024b) —
            // chain time too, which a start with no network and no saved reading still lacks.
            override fun onAvailable(network: Network) {
                // Called from the system's network thread: whether the app is in front is read on the main one.
                viewModelScope.launch {
                    if (refresher == null) return@launch
                    back()
                    refreshNow()
                }
            }

            // A drop and return between two 30-second reads never shows as offline,
            // so the loss itself re-arms the automatic send — for the next read that answers.
            override fun onLost(network: Network) = networkLost()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        network = connectivity to callback
    }

    override fun onCleared() {
        network?.let { (connectivity, callback) -> connectivity.unregisterNetworkCallback(callback) }
        network = null
    }

    /**
     * Reads started when the network was last lost, or a send last found devnet
     * silent before the wallet opened — on the main thread, as [refreshesStarted].
     */
    private var readsBeforeLoss = 0

    /**
     * Whether [pendingDay] of [h] should be sent by itself now: the chain answers
     * again and nothing has sent it since (FR-024b Phase 2, T098). The send itself
     * is [submitDay], which records it.
     */
    fun autoSubmitDue(h: Holdout, pendingDay: Int?, todayDone: Boolean): Boolean {
        val s = _state.value
        val step = s.autoSubmit.step(h.address.base58(), pendingDay, todayDone, s.offline, s.busy)
        if (step.next != s.autoSubmit) {
            _state.update { it.copy(autoSubmit = step.next) }
            keepAutoSubmit()
        }
        return step.send
    }

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
        // The genesis gate is off on devnet and ships off; the app checks the Seeker itself (FR-003b–FR-003d), so
        // this is the contract's own refusal if the admin ever turns it on.
        6024 -> "Staking in a Holdout needs a Seeker. You can still do the Daily 25 and watch every Holdout."
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
