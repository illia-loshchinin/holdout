package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.repeatOnLifecycle
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.faucet.FaucetClient
import holdout.app.holdout.HoldoutViewModel
import holdout.app.names.SeekerNameRepository
import holdout.app.nav.NavEvent
import holdout.app.nav.NavState
import holdout.app.storage.Store
import holdout.app.streak.OfflineDays
import holdout.app.streak.StreakStore
import holdout.core.ChainClock
import holdout.core.HoldoutDay
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * What every host reads and calls, gathered once per composition by
 * `HoldoutApp` for the connected wallet: the stores, the view model and its
 * state, chain time, and the shared actions. The hosts own their screens;
 * the loops, reminders and the router stay in `HoldoutApp`.
 */
internal class Shared(
    val sender: ActivityResultSender,
    val store: Store,
    val streaks: StreakStore,
    val offlineDays: OfflineDays,
    val holdouts: HoldoutViewModel,
    val seekerNames: SeekerNameRepository,
    val clock: ChainClock,
    /** Which boot of the phone this is (`Settings.Global.BOOT_COUNT`), -1 when unknown. */
    val boot: Int,
    /** The connected wallet, base58. */
    val wallet: String,
    val address: SolanaPublicKey,
    val chain: HoldoutViewModel.UiState,
    val rosters: Map<SolanaPublicKey, List<MembershipSnapshot>>,
    /** Chain time, sampled once a second; null until the chain has answered. */
    val now: Long?,
    val nav: NavState,
    /** Bumped when a sitting ends or the phone settles a day on its own: storage reads key on it. */
    val refreshToken: Int,
    val bumpRefresh: () -> Unit,
    val dispatch: (NavEvent) -> Unit,
    /** "Check again" (spec 005): the snapshot, chain time and the roster on screen, read now. */
    val checkChain: (roster: SolanaPublicKey?) -> Unit,
    /** Sends a completed day of a Holdout (and records it as that day's training for the streak). */
    val submitDayOf: (Holdout, Int) -> Unit,
    /** Asks for notifications once, at the first join (FR-068a), then runs the join. */
    val joinAsking: (() -> Unit) -> Unit,
    val disconnect: () -> Unit,
    /** This wallet is confirmed on this phone as holding a Seeker Genesis Token: a join goes straight to the stake confirm. */
    val seekerKnown: Boolean,
    /** Notifications are on: only then may a screen say "we'll remind you" (ruling G9). */
    val notificationsOn: Boolean,
    /** This session's Seeker check found no Genesis Token for the wallet: no screen offers it a stake it cannot make (ruling G12). */
    val knownNotSeeker: Boolean = false,
) {
    val view: ChainView? get() = chain.view

    /** The global day of chain time (05:00 UTC), or [Store.UNKNOWN_DAY]. */
    val dayIndex: Long get() = now?.let { HoldoutDay.indexOf(it) } ?: Store.UNKNOWN_DAY

    fun holdout(id: String): Holdout? = view?.holdouts?.firstOrNull { it.address.base58() == id }

    /** The member's Holdout running today with them standing in it, if any (T2). */
    fun active(): Pair<Holdout, Stage.Active>? = TabRoots.active(view, now)
}

/**
 * Whether the wallet holds the SOL to pay a transaction's fee. Unknown (no
 * chain read yet) counts as yes: nothing is said or blocked on a guess. Today
 * says "short of SOL" below this, and the automatic send stays shut.
 */
internal fun canPayFees(view: ChainView?): Boolean = (view?.lamports ?: Long.MAX_VALUE) >= FaucetClient.MEMBER_CAN_PAY_LAMPORTS

/** Where a Holdout's sittings bank on this phone: per wallet, per cohort, per cohort day (data-model §4). */
internal fun cohortScope(h: Holdout) = "cohort:${h.address.base58()}"

internal const val DAILY_25 = 25
internal const val CHAIN_RETRY_MILLIS = 30_000L
internal const val CHAIN_REFRESH_MILLIS = 600_000L
internal const val ROSTER_REFRESH_MILLIS = 30_000L

/** Repeats [pass] for as long as the app is in front; stops when it is not, and starts again on return. */
@Composable
internal fun InFrontLoop(key: Any?, pass: suspend () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(key, owner) {
        owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) pass()
        }
    }
}

/** Today's day of [h] done on this phone and not yet on chain, if any. Only today's counts: an older one left behind must not hide it. */
@Composable
internal fun Shared.pendingTodayOf(h: Holdout, stage: Stage.Active): Int? {
    val pending = remember(wallet, h.address, refreshToken, chain.busy) { holdouts.pendingDay(h) }
    val locallyDone = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.isDayComplete(wallet, cohortScope(h), stage.dayIndex.toLong()) }
    return pending?.takeIf { it == stage.dayIndex } ?: stage.dayIndex.takeIf { locallyDone && !stage.todayDone }
}

/**
 * A day waiting for the network sends itself once the chain answers again
 * (FR-024b Phase 2, T098) — on whichever screen the member is, the tour and
 * the Daily 25 included, short of a Holdout day's sitting, and again after the
 * system reports the network lost and the chain answers once more.
 * [whenFree] returns once the screen may give way to the wallet; [key] is
 * what it waits on.
 */
@Composable
internal fun Shared.SendWaitingDay(key: Any? = null, whenFree: suspend () -> Unit = {}) {
    val (h, stage) = active() ?: return
    val pendingToday = pendingTodayOf(h, stage)
    // Short of SOL the wallet is not opened by itself: Today says so, and the send goes once the wallet is funded.
    val payable = canPayFees(view)
    LaunchedEffect(h.address, pendingToday, stage.todayDone, chain.offline, chain.busy, chain.autoSubmit, payable, key) {
        if (pendingToday == null || !payable) return@LaunchedEffect
        whenFree()
        if (holdouts.autoSubmitDue(h, pendingToday, stage.todayDone)) submitDayOf(h, pendingToday)
    }
}
