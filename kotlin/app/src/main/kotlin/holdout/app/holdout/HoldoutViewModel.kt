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
import holdout.app.wallet.SendPhase
import holdout.app.wallet.SendResult
import holdout.app.wallet.SignAction
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.app.wallet.WalletRepository
import holdout.core.Instructions
import holdout.core.Journey
import holdout.core.Leaderboard
import holdout.core.MembershipSnapshot
import holdout.core.Rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val rpc: Rpc = Deployment.rpc(),
    private val chain: ChainReads = ChainRepository(rpc),
    val wallet: WalletRepository = WalletRepository(store, rpc),
    private val faucet: FaucetClient = FaucetClient(rpc, wallet, Deployment.programId),
    /** The monotonic clock, in milliseconds: how long ago a transaction that may still land was sent. */
    private val elapsed: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : ViewModel() {
    data class UiState(
        /** Null until the first read answers; the screens say "reading devnet" rather than showing nothing. */
        val view: ChainView? = null,
        /** The chain can't be reached: two snapshot reads in a row failed, or the first with no view yet (001 FR-071); [view] is the previous one, if any. */
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
        /** A join, a day or a claim the wallet has been asked to sign, for the signing screens (rulings G7); null when none. */
        val signing: Signing? = null,
        /** Sent, not confirmed, and may land yet: the same action on the same Holdout stays shut until a read settles each (PR #62 review). */
        val pending: List<PendingTx> = emptyList(),
        /** The outcomes of signatures the member left before they came back, oldest first, a Notice card each on the screen they went to (at most [UnseenTx.MAX_CARDS]). */
        val unseen: List<UnseenTx> = emptyList(),
    ) {
        /** The transaction that keeps [action] on [cohort] shut, if one may still land. */
        fun pendingFor(action: SignAction, cohort: SolanaPublicKey, day: Int = 0): PendingTx? = pending.firstOrNull { it.isFor(action, cohort, day) }
    }

    /**
     * One signature's progress: [id] tells a second attempt from the first,
     * [phase] is what its screen shows — from the tap to its outcome, a devnet
     * that does not answer before the wallet opens included (a failure that
     * moved nothing).
     */
    data class Signing(val id: Int, val action: SignAction, val cohort: SolanaPublicKey, val phase: TxPhase, val tier: Int = 0, val day: Int = 0)

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
     * A cohort's read still out answers for any asked meanwhile — the 30-second pass, a screen opened
     * twice, the Standing dot's own loop — so the same `getProgramAccounts` is never in flight twice.
     * A roster is read again only when its cohort's counters have moved since its last answer (a member
     * joined or left, a forfeit recorded, a member counted, the cohort settled) or once that answer is
     * [maxAgeMillis] old — 30 seconds for a Holdout running its day, whose submits show in no counter,
     * 10 minutes otherwise (FR-071) — and never within [MIN_READ_AGE_MILLIS] of it. A [force]d read
     * ("Check again") starts its own anyway: one already out may be hanging, and the check promises a
     * fresh roster; the newer answer wins either way.
     */
    fun loadRoster(cohort: SolanaPublicKey, scope: CoroutineScope = viewModelScope, force: Boolean = false, maxAgeMillis: Long = ROSTER_MAX_AGE_MILLIS) {
        val counters = countersOf(cohort)
        if (!force) {
            if (rosterReads[cohort]?.isActive == true) return
            val at = rosterAnsweredAt[cohort]
            if (at != null) {
                val age = elapsed() - at
                if (age < MIN_READ_AGE_MILLIS) return
                if (age < maxAgeMillis && counters == rosterCounters[cohort]) return
            }
        }
        // A read given up (see [ROSTER_READ_MILLIS]) can still be answered late; a slow
        // early one must not put an older roster back over a newer one.
        val started = ++rosterLoadsStarted
        val read =
            scope.launch {
                // Its own failure ends only this read: as a child of a check it must not cancel the check.
                val roster =
                    try {
                        withTimeoutOrNull(ROSTER_READ_MILLIS) { chain.roster(cohort) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    } ?: return@launch
                if (started < (newestRoster[cohort] ?: 0)) return@launch
                newestRoster[cohort] = started
                rosterAnsweredAt[cohort] = elapsed()
                rosterCounters[cohort] = counters
                _rosters.update { it + (cohort to roster) }
            }
        rosterReads[cohort] = read
        read.invokeOnCompletion { if (rosterReads[cohort] === read) rosterReads.remove(cohort) }
    }

    /** The roster read now out, by cohort — on the main thread. */
    private val rosterReads = mutableMapOf<SolanaPublicKey, Job>()

    /** When each cohort's roster last answered, on [elapsed]'s clock — on the main thread. */
    private val rosterAnsweredAt = mutableMapOf<SolanaPublicKey, Long>()

    /** Each cohort's counters as the snapshot showed them when its roster read that answered last started. */
    private val rosterCounters = mutableMapOf<SolanaPublicKey, List<Any>?>()

    /** [cohort]'s counters in the snapshot on screen, or null while it holds no such cohort. */
    private fun countersOf(cohort: SolanaPublicKey): List<Any>? = _state.value.view?.holdouts?.firstOrNull { it.address == cohort }?.counters

    /** Every cohort's forfeits recorded and settlement, which are all that move a leaderboard (FR-060, FR-061a). */
    private fun boardCounters(): Map<SolanaPublicKey, List<Any>>? = _state.value.view?.holdouts?.associate { it.address to listOf(it.cohort.forfeitsRecorded, it.cohort.counted, it.cohort.settled) }

    private val _leaderboard = MutableStateFlow<Leaderboard.Snapshot?>(null)

    /** The leaderboards' last read — cohorts, every profile, cohorts again (FR-060); null until the first read lands. */
    val leaderboard: StateFlow<Leaderboard.Snapshot?> = _leaderboard.asStateFlow()

    /**
     * Reads a leaderboard snapshot (R8); a failed read keeps the last one, and an older answer never replaces a newer.
     * One read at a time, read again only when a cohort's forfeits recorded or settlement have moved since the last
     * answer or once it is [BOARD_MAX_AGE_MILLIS] old, and never within [MIN_READ_AGE_MILLIS] of it (FR-071): the
     * boards opened again show what was just read.
     */
    fun loadLeaderboard() {
        if (leaderboardRead?.isActive == true) return
        val counters = boardCounters()
        leaderboardAnsweredAt?.let { at ->
            val age = elapsed() - at
            if (age < MIN_READ_AGE_MILLIS) return
            if (age < BOARD_MAX_AGE_MILLIS && counters == leaderboardCounters) return
        }
        val started = ++profileLoadsStarted
        leaderboardRead = viewModelScope.launch {
            val read = chain.leaderboard() ?: return@launch
            if (started < newestProfiles) return@launch
            newestProfiles = started
            leaderboardAnsweredAt = elapsed()
            leaderboardCounters = counters
            _leaderboard.value = read
        }
    }

    private var profileLoadsStarted = 0
    private var newestProfiles = 0

    /** The leaderboard read now out, and when the last one answered — on the main thread. */
    private var leaderboardRead: Job? = null
    private var leaderboardAnsweredAt: Long? = null
    private var leaderboardCounters: Map<SolanaPublicKey, List<Any>>? = null

    /** Roster loads started, and per cohort the latest one whose answer was taken — both on the main thread. */
    private var rosterLoadsStarted = 0
    private val newestRoster = mutableMapOf<SolanaPublicKey, Int>()

    /**
     * Whether a screen showing live chain data is in front (FR-071): the pulse reads only then. Set by the
     * app from its navigation; true until it says otherwise.
     */
    private val live = MutableStateFlow(true)

    /** Wakes the pulse's wait, so it starts its 30 seconds over: an answer after failures, a Check again. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The app says whether the screen in front shows live chain data; coming back to one reads at once when the last pulse is 30 s old or more. */
    fun setLive(isLive: Boolean) {
        live.value = isLive
    }

    /** How long the pulse waits after its read: 30 s, and after failures 30, 60, 120, 240 s, at most 5 minutes (FR-071). */
    private fun pulseWait(): Long = if (failedReads <= 1) REFRESH_MILLIS else minOf(REFRESH_MILLIS shl (failedReads - 1).coerceAtMost(4), MAX_BACKOFF_MILLIS)

    /** Starts reading for [wallet] (or with none, for the connect screen's live list), every 30 s while a live screen is in front. */
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
            // And its sends that may still land, kept on the phone, so a process killed inside their window keeps them shut.
            _state.value = UiState(
                autoSubmit = wallet?.let { AutoSubmit.restore(store, it.base58()) } ?: AutoSubmit(),
                pending = wallet?.let { w -> store.unconfirmedSends(w.base58())?.let { PendingTx.restore(it, elapsed()) } }.orEmpty(),
            )
            failedReads = 0
        }
        address = wallet
        refresher?.cancel()
        refresher = viewModelScope.launch {
            while (true) {
                live.first { it }
                refreshNow(join = true)
                // A wake (an answer after failures, a Check again) starts the wait over at its current length.
                while (withTimeoutOrNull(pulseWait()) { wake.receive() } != null) {
                    // Woken: wait again, from now.
                }
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

    /** Snapshot reads in a row whose answer was taken and that did not answer — on the main thread. */
    private var failedReads = 0

    /** The snapshot read now out, the wallet it reads for, and the signal its end completes — on the main thread. */
    private var readOut: Pair<SolanaPublicKey?, CompletableDeferred<Unit>>? = null

    /**
     * Reads the snapshot and applies its answer. With [join], a read already out for the same wallet
     * answers instead (the 30-second pass): one snapshot read at a time (R8). One out for longer than
     * [JOIN_READ_MILLIS] is not waited for any more, so a read that hangs cannot hold the passes off for
     * good. Without [join] — a "Check again", the read closing an action, the network's return — the
     * read starts now: one started earlier, maybe while the chain was down, cannot answer for them.
     */
    private suspend fun refreshNow(join: Boolean = false, fresh: Boolean = false) {
        val wallet = address
        if (join) {
            val out = readOut?.takeIf { it.first == wallet }
            if (out != null && withTimeoutOrNull(JOIN_READ_MILLIS) { out.second.await() } != null) return
        }
        val done = CompletableDeferred<Unit>()
        readOut = wallet to done
        try {
            read(wallet, fresh)
        } finally {
            done.complete(Unit)
            if (readOut?.second === done) readOut = null
        }
    }

    private suspend fun read(wallet: SolanaPublicKey?, fresh: Boolean) {
        val started = ++refreshesStarted
        // Only a transaction that may still land needs the read's start time; one sent while it is out is not settled by its age.
        val readAt = if (_state.value.pending.isEmpty()) null else elapsed()
        val view = if (fresh) chain.loadFresh(wallet) else chain.load(wallet)
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
        // An answer after failures starts the pulse's wait over at 30 s.
        if (view != null && failedReads > 0) wake.trySend(Unit)
        failedReads = if (view == null) failedReads + 1 else 0
        _state.update {
            if (view == null) {
                // One read that did not answer is not yet "can't be reached": the last view stays until
                // the next read fails too (R8). With no view yet there is nothing to keep showing.
                it.copy(offline = it.offline || it.view == null || failedReads >= OFFLINE_AFTER_FAILED_READS)
            } else {
                it.copy(view = view, offline = false, autoSubmit = if (back) it.autoSubmit.answered() else it.autoSubmit, check = it.check.answered()).settle(view, readAt)
            }
        }
        if (view != null && back) keepAutoSubmit()
        if (readAt != null) keepPending()
    }

    /**
     * The member's "Check again", by the button or the pull (spec 005): while
     * the chain can't be reached ([UiState.offline]), reads the chain now — the snapshot here and, at
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
                refreshNow(fresh = true)
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

    /** The signing screens closed: the last signature's progress is no longer shown. */
    fun clearSigning() = _state.update { it.copy(signing = null) }

    /** The member dismissed [card], the card of an outcome they had left ("OK"). */
    fun dismissUnseen(card: UnseenTx) = _state.update { it.copy(unseen = it.unseen - card) }

    /** Keeps the connected wallet's sends that may still land on the phone (see [UiState.pending]). */
    private fun keepPending() {
        address?.let { store.setUnconfirmedSends(it.base58(), PendingTx.encode(_state.value.pending)) }
    }

    /**
     * What a read started at [readAt] that answered with [view] settles: each
     * transaction that may still land and now has — or now can't — opens its
     * action again. One the member left the screen of says how it ended: a
     * landed one needs no card, one that never landed says so.
     */
    private fun UiState.settle(view: ChainView, readAt: Long?): UiState {
        val (settled, still) = pending.partition { it.settledBy(view, readAt) }
        if (settled.isEmpty()) return this
        val after = unseen.mapNotNull { card ->
            when (settled.firstOrNull { card.isFor(it) }?.landedIn(view)) {
                null -> card
                true -> null
                false -> card.copy(outcome = TxOutcome.Failed(mayStillLand = false, message = ""))
            }
        }
        // A result still on screen that said it may land now says how it ended: landed, or not and "Try again" (PR #67 review).
        val shown = signing?.let { sig ->
            val done = (sig.phase as? TxPhase.Done)?.outcome as? TxOutcome.Failed
            val send = settled.firstOrNull { it.isFor(sig.action, sig.cohort, sig.day) }
            if (done?.mayStillLand != true || send == null) {
                sig
            } else {
                sig.copy(phase = TxPhase.Done(if (send.landedIn(view)) TxOutcome.Landed(null) else TxOutcome.Failed(mayStillLand = false, message = done.message)))
            }
        }
        // And the warning it left on the screen it was sent from no longer stands.
        return copy(pending = still, unseen = after, signing = shown, message = message.takeUnless { messageIsWarning }, messageIsWarning = false)
    }

    private var signings = 0

    /**
     * Runs [send] for [action] on [cohort], reporting its phases in
     * [UiState.signing] from the moment the wallet is asked, and its outcome
     * when it ends (rulings G7, C-D14).
     */
    private suspend fun signing(action: SignAction, cohort: SolanaPublicKey, tier: Int = 0, day: Int = 0, send: suspend (onPhase: (SendPhase) -> Unit) -> SendResult): SendResult {
        val id = ++signings
        // The wallet it is signed for: once another is connected, nothing of this one is shown or kept (PR #67 review).
        val owner = address
        val started = Signing(id, action, cohort, TxPhase.WaitingForWallet, tier, day)
        // A card about the same action's last outcome is old news once it is tried again.
        _state.update { s -> s.copy(signing = started, unseen = s.unseen.filterNot { it.isFor(action, cohort, day) }) }
        // When it went to the network: its window to land starts there, not when the confirmation polling gives up.
        // It is kept on the phone from that moment, so a process killed while confirming still keeps the action shut.
        var sentAt: Long? = null
        val result = send { phase ->
            if (owner != address) return@send
            if (phase == SendPhase.Confirming && sentAt == null) {
                val sent = PendingTx(action, cohort, day, elapsed())
                sentAt = sent.sentAt
                _state.update { s -> s.copy(pending = s.pending.filterNot { it.isFor(action, cohort, day) } + sent) }
                keepPending()
            }
            _state.update { s -> s.copy(signing = s.signing?.takeIf { it.id == id }?.copy(phase = TxPhase.of(phase)) ?: s.signing) }
        }
        if (owner != address) return result
        val lowOnSol = (_state.value.view?.lamports ?: Long.MAX_VALUE) < holdout.app.faucet.FaucetClient.MEMBER_CAN_PAY_LAMPORTS
        val outcome = TxOutcome.of(action, result, lowOnSol)
        val pending = (outcome as? TxOutcome.Failed)?.takeIf { it.mayStillLand }?.let { PendingTx(action, cohort, day, sentAt ?: elapsed()) }
        // A result that offers "Try again" frees the action with it: the closing read in [act] would
        // otherwise drop that tap, and the screen would wait for a wallet nobody asked.
        _state.update { s ->
            val shown = s.signing?.takeIf { it.id == id }
            s.copy(
                // Its screen closed meanwhile (the member left it while confirming): the outcome is kept
                // for the screen they went to instead, not put back where nothing shows it (PR #62 review).
                signing = shown?.copy(phase = TxPhase.Done(outcome)) ?: s.signing,
                unseen = if (shown == null && outcome !is TxOutcome.Landed) (s.unseen + UnseenTx(action, cohort, day, outcome)).takeLast(UnseenTx.MAX_CARDS) else s.unseen,
                // Only one that may still land stays: any other outcome settles the record kept while it confirmed.
                pending = s.pending.filterNot { it.isFor(action, cohort, day) } + listOfNotNull(pending),
                busy = s.busy && !outcome.canRetry,
            )
        }
        if (pending != null || sentAt != null) keepPending()
        return result
    }

    private fun say(message: String?, warning: Boolean = false) = _state.update { it.copy(message = message, messageIsWarning = warning) }

    /** Actions started: one frees [UiState.busy] at its end only if no later one has taken it since. */
    private var actions = 0

    private fun act(block: suspend () -> Unit) {
        if (_state.value.busy) return
        val mine = ++actions
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } finally {
                refreshNow(fresh = true)
                // A failed signature frees the action before this read ends (see [signing]); a retry may
                // hold it by now, and this action's end must not release the retry's hold.
                if (mine == actions) _state.update { it.copy(busy = false) }
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
            say(if (result.declined) DECLINED else ProgramErrors.explain(result.programError) ?: result.message, warning = true)
            false
        }
    }

    // ---- the faucet (T043) -------------------------------------------------

    fun claimFaucet(sender: ActivityResultSender) = act {
        val me = address ?: return@act
        val view = _state.value.view ?: return@act
        val config = view.config ?: return@act say("Holdout is not live on devnet yet.", warning = true)
        val outcome = faucet.claim(sender, me, config, view.lamports)
        _state.update { it.copy(lastFaucet = outcome) }
    }

    // ---- joining and leaving (T059, T060, T061) ----------------------------

    fun join(sender: ActivityResultSender, h: Holdout, tier: Int, now: Long) = act {
        val me = address ?: return@act
        if (_state.value.pendingFor(SignAction.Join, h.address) != null) return@act
        val view = _state.value.view ?: return@act
        val config = view.config ?: return@act
        // A running cohort the member has missed a deadline in and nobody has recorded yet still holds the
        // slot (ObligationsOverlap, 6026): its record goes first in the same transaction (FR-037g, ES-17).
        val instructions = Journey.joinInstructions(Deployment.programId, me, config.skrMint, config.feeDestination, config.developerDestination, h.address, tier, view.joinPlan(now, h))
        val result = signing(SignAction.Join, h.address, tier = tier) { onPhase -> wallet.send(sender, instructions, onPhase) }
        describe(result, "You're in. ${Format.skr(h.cohort.chips[tier])} staked.")
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
        // A send of this day that may still land holds it: the day stays pending until a read settles that one.
        if (_state.value.pendingFor(SignAction.Submit, h.address, day) != null) return
        act {
            // Sent — from the sitting's exit, a tap or by itself: not sent by itself again
            // until the connection drops and returns, even before the chain shows it landed.
            // Kept on the phone too, so a decline holds across a restart of the app.
            _state.update { it.copy(autoSubmit = it.autoSubmit.sent(h.address.base58(), day)) }
            keepAutoSubmit()
            val ix = Instructions.submitDay(Deployment.programId, me, h.address, day, proofOf(h.address, day, reps))
            val result = signing(SignAction.Submit, h.address, day = day) { onPhase -> wallet.send(sender, listOf(ix), onPhase) }
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
                    refreshNow(fresh = true)
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
        // A send of that day that may still land: nothing goes by itself until a read settles it.
        if (pendingDay != null && s.pendingFor(SignAction.Submit, h.address, pendingDay) != null) return false
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
        if (_state.value.pendingFor(SignAction.Claim, h.address) != null) return@act
        val config = _state.value.view?.config ?: return@act
        val result = signing(SignAction.Claim, h.address) { onPhase -> wallet.send(sender, listOf(Instructions.claimPayout(Deployment.programId, me, config.skrMint, h.address)), onPhase) }
        if (describe(result, "Claimed.")) _state.update { it.copy(lastClaim = h.address to (result as SendResult.Landed).signature) }
    }

    companion object {
        private const val REFRESH_MILLIS = 30_000L

        /** Snapshot reads in a row that must fail before the app says the chain can't be reached (R8). */
        const val OFFLINE_AFTER_FAILED_READS = 2

        /** How long a pass waits on a snapshot read already out before it starts its own. */
        private const val JOIN_READ_MILLIS = 60_000L

        /** A roster or the leaderboards that answered less than this long ago are not read again (R8). */
        const val MIN_READ_AGE_MILLIS = 20_000L

        /** A roster's age at which it is read again with nothing moved: a Holdout running its day, whose submits show in no counter (FR-071). */
        const val ROSTER_MAX_AGE_MILLIS = 30_000L

        /** A roster's age at which it is read again with nothing moved, for any other Holdout (FR-071). */
        const val ROSTER_SLOW_MAX_AGE_MILLIS = 600_000L

        /** The leaderboards' age at which they are read again with nothing moved (FR-071). */
        const val BOARD_MAX_AGE_MILLIS = 600_000L

        /** The pulse's longest wait after failed reads (FR-071). */
        const val MAX_BACKOFF_MILLIS = 300_000L

        /** A roster read that has not answered by now is given up, so one that hangs cannot hold its cohort's reads off for good. */
        private const val ROSTER_READ_MILLIS = 60_000L

        /** A refusal in the wallet: nothing was signed, so nothing moved. */
        const val DECLINED = "You declined in your wallet. Nothing was sent."
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
        6051, 6052 -> "The faucet can't pay out right now. It is not something you did."
        // The token program's InsufficientFunds: not enough SKR for the chip.
        1 -> "Not enough SKR for that chip. Get test tokens first."
        else -> null
    }
}
