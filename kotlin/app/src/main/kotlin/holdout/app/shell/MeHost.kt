package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import holdout.app.about.AboutScreen
import holdout.app.chain.Deployment
import holdout.app.faucet.FaucetSheet
import holdout.app.me.MeScreen
import holdout.app.me.MeState
import holdout.app.nav.Badges
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Sheet
import holdout.app.storage.Store
import holdout.app.ui.StreakState

/** The Me dot: SKR or SOL too low to stake or submit (DS BottomNav); false before the chain is read. */
internal fun Shared.lowOnFunds(): Boolean {
    val v = view ?: return false
    val n = now ?: return false
    val cheapest = v.openToJoin(n).mapNotNull { it.cohort.chips.minOrNull() }.minOrNull()
    return Badges.me(v.skr, v.lamports, cheapest)
}

/** The Me tab root, W1 (screen map W1, board 26; FR-069). */
@Composable
internal fun Shared.MeRoot(modifier: Modifier = Modifier) {
    val streak = remember(wallet, dayIndex, refreshToken) { streaks.read(wallet, dayIndex) }
    val doneToday = remember(wallet, dayIndex, refreshToken) { store.isDayComplete(wallet, Store.CHALLENGE, dayIndex) }
    val v = view
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
        ),
        onGetTokens = { dispatch(NavEvent.OpenFaucet) },
        onAbout = { dispatch(NavEvent.Open(Screen.About)) },
        onTour = { dispatch(NavEvent.OpenTour) },
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
        // The snapshot, chain time and the program's config are needed to claim: without them the balances are not shown and the tap is off.
        ready = v?.config != null && n != null,
        onClaim = { if (n != null) holdouts.claimFaucet(sender) },
        onBack = {
            holdouts.clearMessage()
            dispatch(NavEvent.CloseSheet)
        },
        // Opened from the stake sheet (ES-0a), it offers the way back to it; from Me, a drag or the scrim closes it.
        backLabel = "Back to the stake sheet".takeIf { (nav.sheet as? Sheet.Faucet)?.returnTo != null },
    )
}
