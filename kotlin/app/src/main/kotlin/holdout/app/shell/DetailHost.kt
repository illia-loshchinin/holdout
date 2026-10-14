package holdout.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.cohort.CohortDetailScreen
import holdout.app.leaderboard.LeaderboardScreen
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.roster.RosterScreen
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.Leaderboard
import holdout.core.ScriptedWallets
import holdout.core.Stage
import kotlinx.coroutines.delay

/**
 * H4 for [id], a Holdout's screen (FR-069b): pushed over whatever opened it — a Holdouts card, a Running now row,
 * Today's crowd strip and rows, the last day on chain — and Back returns there. A watcher's view included (FR-062a).
 */
@Composable
internal fun Shared.DetailHost(id: String, modifier: Modifier = Modifier) {
    val h = holdout(id)
    val n = now
    if (h == null || n == null) return Loading(modifier)
    RosterLoop(h)
    // Only the claim made from this cohort: another cohort's transaction is never shown as this one's.
    val claimSignature = chain.lastClaim?.takeIf { it.first == h.address }?.second
    // The member's own day while it is open (FR-069b), wherever their Holdout's screen opens: the same day, button
    // and sitting as Today.
    val stage = h.stage(n)
    val active = stage as? Stage.Active
    val inputs = if (active != null && h.membership != null) dayInputs(h, active) else null
    CohortDetailScreen(
        h, rosters[h.address], stage, n, claimSignature,
        onRoster = { dispatch(NavEvent.Open(Screen.Roster(id))) },
        onBack = { dispatch(NavEvent.Back) },
        modifier = modifier,
        offline = chain.offline,
        checking = chain.check.checking,
        stillUnreachable = chain.check.stillUnreachable,
        onCheck = { checkChain(h.address) },
        readAgoSeconds = readAgoSeconds,
        onForfeit = { dispatch(NavEvent.Open(Screen.Forfeit(id))) },
        day = if (active != null && inputs != null) dayState(h, active, n, inputs) else null,
        onStartDay = { active?.let { startDay(h, it.dayIndex) } },
        onSubmitDay = { submitDayOf(h, it) },
        onGetTokens = { dispatch(NavEvent.OpenFaucet) },
    )
}

/** H5. */
@Composable
internal fun Shared.RosterHost(id: String, modifier: Modifier = Modifier) {
    val h = holdout(id)
    val n = now
    if (h == null || n == null) return Loading(modifier)
    RosterLoop(h)
    RosterScreen(h, rosters[h.address], wallet, n, { dispatch(NavEvent.Back) }, modifier, onShowing = seekerNames::showing)
}

/** L1: one board per level, the test board only where a test league exists (FR-044a). */
@Composable
internal fun Shared.LeaderboardsHost(board: Leaderboard.Board, read: Leaderboard.Snapshot?, modifier: Modifier = Modifier) {
    InFrontLoop(Unit) {
        holdouts.loadLeaderboard()
        delay(LEADERBOARD_REFRESH_MILLIS)
    }
    val boards = Leaderboard.Board.entries.filter { it != Leaderboard.Board.TEST || read?.cohortsAfter.orEmpty().any { c -> Leaderboard.Board.TEST.covers(c.leagueId) } }
    val n = now
    val shown = if (read == null || n == null) null else Leaderboard.view(board, read, n, ScriptedWallets::isScripted)
    // The test board's badge names the day length of the newest test league (FR-044a).
    val testDay = read?.cohortsAfter.orEmpty().filter { Leaderboard.Board.TEST.covers(it.leagueId) }.maxByOrNull { it.leagueId }?.dayLength
    LeaderboardScreen(
        board, boards, shown, address,
        { dispatch(NavEvent.Swap(Screen.Leaderboards(it))) },
        { dispatch(NavEvent.Back) },
        modifier,
        onShowing = seekerNames::showing,
        testDayLengthSeconds = testDay ?: 600L,
    )
}

/** Whether the app's own loop (Today's crowd strip's) already looks after [h]'s roster, wherever the member is. */
internal fun Shared.rosterReadByApp(h: Holdout): Boolean = active()?.first?.address == h.address

/**
 * A Holdout's roster while it is on screen — unless the app's loop for the Holdout Today is about looks after it
 * already: read now, then whenever its cohort's counters move or it grows old (FR-071, [TabRoots.rosterEveryMillis]).
 */
@Composable
internal fun Shared.RosterLoop(h: Holdout) {
    val covered = rosterReadByApp(h)
    val n = now
    val every = if (n != null) TabRoots.rosterEveryMillis(h, n) else ROSTER_REFRESH_MILLIS
    InFrontLoop(Triple(h.address, covered, every)) {
        if (covered) kotlinx.coroutines.awaitCancellation()
        holdouts.loadRoster(h.address, maxAgeMillis = every)
        delay(ROSTER_REFRESH_MILLIS)
    }
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(HoldoutColors.Canvas).padding(Spacing.screenGutter)) {
        Reading { SectionLabel("Reading the chain…") }
    }
}
