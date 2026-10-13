package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import holdout.app.about.AboutScreen
import holdout.app.chain.Deployment
import holdout.app.faucet.FaucetSheet
import holdout.app.me.MeRank
import holdout.app.me.MeScreen
import holdout.app.me.MeState
import holdout.app.nav.Badges
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Sheet
import holdout.app.storage.Store
import holdout.app.streak.ToesMeter
import holdout.app.ui.StreakState
import holdout.core.Leaderboard
import holdout.core.ScriptedWallets

/** The Me dot: SKR or SOL too low to stake or submit (DS BottomNav); false before the chain is read. */
internal fun Shared.lowOnFunds(): Boolean {
    val v = view ?: return false
    val n = now ?: return false
    val cheapest = v.openToJoin(n).mapNotNull { it.cohort.chips.minOrNull() }.minOrNull()
    return Badges.me(v.skr, v.lamports, cheapest)
}

/**
 * The Me tab root, W1 (screen map W1, board 26; FR-069d). [leaderboardRead] is the leaderboards' last read: the
 * Leaderboards row's rank comes from it, so Me asks for one read when it opens — the rank is not on the member's
 * own profile, which holds only their figures, and nobody else's.
 */
@Composable
internal fun Shared.MeRoot(leaderboardRead: Leaderboard.Snapshot?, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { holdouts.loadLeaderboard() }
    val streak = remember(wallet, dayIndex, refreshToken) { streaks.read(wallet, dayIndex) }
    val doneToday = remember(wallet, dayIndex, refreshToken) { store.isDayComplete(wallet, Store.CHALLENGE, dayIndex) }
    val toes = remember(wallet, dayIndex, refreshToken) { ToesMeter(store, DAILY_25).read(wallet, dayIndex) }
    val v = view
    val n = now
    // The member's place on each board that has been read; a board the chain is still counting has none yet.
    val views = remember(leaderboardRead, n) {
        if (leaderboardRead == null || n == null) {
            emptyMap()
        } else {
            Leaderboard.Board.entries.associateWith { Leaderboard.view(it, leaderboardRead, n, ScriptedWallets::isScripted) }
        }
    }
    MeScreen(
        state = MeState(
            wallet = wallet,
            skr = v?.skr,
            lamports = v?.lamports,
            faucetSkr = v?.config?.maxTopTier,
            streak = streak.current,
            streakState = when {
                // A Holdout day counts as much as the Daily 25: either one done today extends it.
                doneToday || streak.completedToday -> StreakState.ExtendedToday
                // Broken only once there was a streak to break; a member who never trained has a plain 0.
                streak.current == 0 && streak.longest > 0 -> StreakState.Broken
                else -> StreakState.Active
            },
            freezes = streak.freezes,
            lowOnFunds = lowOnFunds(),
            network = Deployment.networkName.lowercase(),
            longest = streak.longest,
            toes = toes,
            toesTarget = DAILY_25,
            leaderboardMeta = MeRank.meta(views, address),
        ),
        onGetTokens = { dispatch(NavEvent.OpenFaucet) },
        // The board the member's best rank is on, else the first (Spark · 7-day).
        onLeaderboards = { dispatch(NavEvent.Open(Screen.Leaderboards(MeRank.board(views, address) ?: Leaderboard.Board.EASY_7D))) },
        onAbout = { dispatch(NavEvent.Open(Screen.About)) },
        onDisconnect = disconnect,
        modifier = modifier,
    )
}

/** X1 About Holdout, pushed from Me. */
@Composable
internal fun Shared.AboutHost(modifier: Modifier = Modifier) {
    AboutScreen(onTour = { dispatch(NavEvent.OpenTour) }, onBack = { dispatch(NavEvent.Back) }, modifier = modifier, onDisconnect = disconnect)
}

/** The faucet in its sheet (G8): from Me, or from any short-of-funds moment, back where it was opened (ES-0a). */
@Composable
internal fun Shared.FaucetSheetHost() {
    val v = view
    val n = now
    // A drag or a tap on the scrim closes the sheet without its buttons: the last tap's outcome goes with it.
    DisposableEffect(Unit) { onDispose { holdouts.clearMessage() } }
    FaucetSheet(
        skr = v?.skr ?: 0uL,
        lamports = v?.lamports ?: 0,
        maxTopTier = v?.config?.maxTopTier ?: 0uL,
        busy = chain.busy,
        outcome = chain.lastFaucet,
        // The snapshot, chain time and a published chip set are needed to claim (claim_faucet refuses an
        // unpublished one, 6012): without them the balances are not shown and the tap is off.
        ready = (v?.config?.chipVersion ?: 0L) > 0L && n != null,
        onClaim = { if (n != null) holdouts.claimFaucet(sender) },
        onBack = {
            holdouts.clearMessage()
            dispatch(NavEvent.CloseSheet)
        },
        // Opened from the stake sheet (ES-0a), it offers the way back to it; from Me, a drag or the scrim closes it.
        backLabel = "Back to the stake sheet".takeIf { (nav.sheet as? Sheet.Faucet)?.returnTo != null },
    )
}
