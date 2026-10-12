package holdout.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.cohort.CohortDetailScreen
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.leaderboard.LeaderboardScreen
import holdout.app.nav.Badges
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.roster.RosterScreen
import holdout.app.standing.StandingEmptyScreen
import holdout.app.standing.StandingEmptyText
import holdout.app.ui.Format
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.Leaderboard
import holdout.core.ScriptedWallets
import holdout.core.Stage
import kotlinx.coroutines.delay

/**
 * The Holdout the Standing tab is about (rulings G1): the one running with the
 * member standing in it, else the last one they ran — started, whatever came
 * of it. A lobby place alone is not one: there is no crowd yet (D-D9).
 */
internal fun Shared.standingHoldout(): Holdout? = TabRoots.standing(view, now)

/** The Standing dot: how many dropped since the member last looked at that Holdout (DS BottomNav). */
internal fun Shared.standingDropped(): Int {
    val h = standingHoldout() ?: return 0
    val n = now ?: return 0
    val standing = rosters[h.address]?.let { Derive.survivors(h.cohort, it, n).standing }
    return Badges.droppedSinceLooked(standing, store.standingLooked(wallet, h.address.base58()), h.cohort.memberCount.toInt())
}

/** The Standing tab root: H4 of [standingHoldout], or the empty state. Looking at it clears the dot. */
@Composable
internal fun Shared.StandingRoot(modifier: Modifier = Modifier) {
    val h = standingHoldout()
    val n = now
    if (h != null && n != null) {
        val standing = rosters[h.address]?.let { Derive.survivors(h.cohort, it, n).standing }
        LaunchedEffect(h.address, standing) {
            if (standing != null) store.setStandingLooked(wallet, h.address.base58(), standing)
        }
        DetailHost(h.address.base58(), root = true, modifier = modifier)
        return
    }
    val v = view
    if (v == null || n == null) {
        Loading(modifier)
        return
    }
    val running = v.runningNotMine(n)
    InFrontLoop(running.map { it.address }) {
        if (running.isEmpty()) kotlinx.coroutines.awaitCancellation()
        running.forEach { holdouts.loadRoster(it.address) }
        delay(ROSTER_REFRESH_MILLIS)
    }
    val lobby = v.mine(n).firstOrNull {
        val s = it.stage(n)
        s == Stage.Lobby || s == Stage.LobbyLocked
    }
    StandingEmptyScreen(
        running = running.map { MyHoldoutsModel.runningRow(it, n, rosters[it.address]) },
        lobby = lobby?.let { "${StandingEmptyText.LOBBY_LEAD}${Format.countdown(it.cohort.start - n)}" to it.address.base58() },
        onEvent = dispatch,
        modifier = modifier,
    )
}

/** H4 for [id]: the Standing root ([root], no back arrow) or pushed from a list, a watcher's view included (FR-062a). */
@Composable
internal fun Shared.DetailHost(id: String, root: Boolean = false, modifier: Modifier = Modifier) {
    val h = holdout(id)
    val n = now
    if (h == null || n == null) return Loading(modifier)
    RosterLoop(h)
    // Only the claim made from this cohort: another cohort's transaction is never shown as this one's.
    val claimSignature = chain.lastClaim?.takeIf { it.first == h.address }?.second
    CohortDetailScreen(
        h, rosters[h.address], h.stage(n), n, claimSignature,
        onRoster = { dispatch(NavEvent.Open(Screen.Roster(id))) },
        onBack = if (root) null else ({ dispatch(NavEvent.Back) }),
        modifier = modifier,
        offline = chain.offline,
        checking = chain.check.checking,
        stillUnreachable = chain.check.stillUnreachable,
        onCheck = { checkChain(h.address) },
        onLeaderboards = { dispatch(NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.of(h.cohort.leagueId)))) },
        onForfeit = { dispatch(NavEvent.Open(Screen.Forfeit(id))) },
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
        delay(ROSTER_REFRESH_MILLIS)
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

/** Whether the app's own loop (the Standing dot's) already reads [h]'s roster every 30 s, wherever the member is. */
internal fun Shared.rosterReadByApp(h: Holdout): Boolean {
    val n = now ?: return false
    val about = standingHoldout() ?: return false
    return about.address == h.address && TabRoots.rosterEveryMillis(about, n) == ROSTER_REFRESH_MILLIS
}

/** A Holdout's roster, read now and every 30 s while it is on screen — unless the app's Standing loop is reading it already. */
@Composable
internal fun Shared.RosterLoop(h: Holdout) {
    val covered = rosterReadByApp(h)
    InFrontLoop(h.address to covered) {
        if (covered) kotlinx.coroutines.awaitCancellation()
        holdouts.loadRoster(h.address)
        delay(ROSTER_REFRESH_MILLIS)
    }
}

@Composable
internal fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(HoldoutColors.Canvas).padding(Spacing.screenGutter)) {
        Reading { SectionLabel("Reading the chain…") }
    }
}
