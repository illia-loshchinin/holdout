package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import holdout.app.chain.Deployment
import holdout.app.claim.ClaimScreen
import holdout.app.cohort.ForfeitScreen
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.holdouts.MyHoldoutsScreen
import holdout.app.lobby.LobbyScreen
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Sheet
import holdout.app.nav.Sitting
import holdout.app.stake.SeekerGate
import holdout.app.stake.SeekerGateSheet
import holdout.app.stake.SeekerStatus
import holdout.app.stake.StakeSheet
import holdout.app.ui.ChainUnreachable
import holdout.app.ui.Format
import holdout.app.ui.SectionLabel
import holdout.core.CohortStatus
import holdout.core.Comeback.forScreenOf
import holdout.core.Journey
import holdout.core.Stage
import kotlinx.coroutines.delay

/**
 * The Holdouts tab root, H0 My Holdouts (screen map H0, board 10; FR-069):
 * the member's own Holdouts by stage, those open to join, the ones running now
 * to watch (FR-062a), and the finished ones.
 */
@Composable
internal fun Shared.HoldoutsRoot(modifier: Modifier = Modifier) {
    val n = now
    val v = view
    val state = remember(v, n?.let { it / 60 }, rosters, refreshToken) {
        if (v == null || n == null) {
            null
        } else {
            MyHoldoutsModel.build(v, n, rosters) { h, day -> store.bankedReps(wallet, cohortScope(h), day.toLong()) }
        }
    }
    // Rosters for the Running now rows; the running card's is the app's Standing loop's to read.
    val watched = TabRoots.watchedOnHoldouts(v, n)
    InFrontLoop(watched) {
        if (watched.isEmpty()) kotlinx.coroutines.awaitCancellation()
        watched.forEach { holdouts.loadRoster(it) }
        delay(ROSTER_REFRESH_MILLIS)
    }
    MyHoldoutsScreen(
        state = state,
        onEvent = dispatch,
        onJoin = { cohort -> dispatch(NavEvent.OpenStake(cohort, seekerKnown = seekerKnown)) },
        modifier = modifier,
        header = {
            if (chain.offline) ChainUnreachable(chain.check.checking, chain.check.stillUnreachable, { checkChain(null) })
            if (v != null && v.config == null) SectionLabel("Holdouts are not live on ${Deployment.networkName.lowercase()} yet")
        },
    )
}

/** H3: back to where it was opened from; Leave signs inline (G7) and lands on H0 once the chain shows it. */
@Composable
internal fun Shared.LobbyHost(id: String, modifier: Modifier = Modifier) {
    val h = holdout(id)
    val n = now
    if (h == null || n == null) return Loading(modifier)
    RosterLoop(h)
    // The stake held while the member was in: what "You left" says came back, if the chain shows the leave before the screen goes.
    var held by remember(id) { mutableStateOf<ULong?>(null) }
    h.membership?.let { held = it.stake }
    LobbyScreen(
        h, n, chain.busy, chain.message, chain.messageIsWarning,
        onLeave = { holdouts.leave(sender, h) },
        onRoster = { dispatch(NavEvent.Open(Screen.Roster(id))) },
        onBack = { dispatch(NavEvent.Back) },
        modifier = modifier,
        leftStake = held.takeIf { h.membership == null }?.let(Format::skr),
        notificationsOn = notificationsOn,
    )
}

/** H6: the way back — the comeback offer's stake sheet, the same level's next Holdout, or today's Daily 25. */
@Composable
internal fun Shared.ForfeitHost(id: String, modifier: Modifier = Modifier) {
    val h = holdout(id)
    val n = now
    val v = view
    if (h == null || n == null || v == null) return Loading(modifier)
    RosterLoop(h)
    val stage = h.stage(n)
    // What finishing a later Holdout of this level and length brings back, from the forfeit itself (FR-037g).
    // Only the screen of the latest forfeit carries it; an older forfeit's screen keeps the plain ways back.
    val offer = v.comebackOffer(n, h.cohort.leagueId).forScreenOf(h.address)
    val round = offer?.next
    ForfeitScreen(
        h = h,
        stage = stage as? Stage.Forfeited ?: Stage.Forfeited(h.membership?.daysCompleted ?: 0, recorded = false),
        streak = streaks.read(wallet, dayIndex).current,
        // Today's Daily 25 starts its sitting (ruling: not just Today).
        onDaily25 = { dispatch(NavEvent.StartSitting(Sitting.Daily25(dayIndex))) },
        // The way back is the same league's next Holdout, its stake sheet straight
        // (US-4; the test lobby for a test cohort); none open to join right now, and
        // the button is not shown.
        onNextHoldout = Journey.wayBack(h.cohort, v.openToJoin(n)) { it.cohort }
            ?.let { next -> { dispatch(NavEvent.OpenStake(next.address.base58(), seekerKnown = seekerKnown)) } },
        onBack = { dispatch(NavEvent.Back) },
        modifier = modifier,
        offline = chain.offline,
        checking = chain.check.checking,
        stillUnreachable = chain.check.stillUnreachable,
        onCheck = { checkChain(h.address) },
        offer = offer,
        // The missed day is the one after the days on chain; finished on this phone means it was too late to send.
        doneOnPhone = store.isDayComplete(wallet, cohortScope(h), (h.membership?.daysCompleted ?: 0).toLong()),
        members = rosters[h.address],
        // A place held in a round of the offer: its lobby, or Today once it runs.
        onSeePlace = offer?.held?.entry?.let { place ->
            {
                dispatch(
                    if (holdout.core.Derive.status(place.cohort, n) == holdout.core.CohortStatus.Running) {
                        NavEvent.SelectTab(holdout.app.nav.Tab.Today)
                    } else {
                        NavEvent.Open(holdout.app.nav.Screen.Lobby(place.address.base58()))
                    },
                )
            }
        },
        now = n,
        joinBlocker = round?.entry?.let { e -> Journey.joinBlocker(e.cohort, v.profile, v.runningEnd(n), n, runningInBreach = v.runningInBreach(n)) },
        // The stake sheet on the lost stake's chip (FR-037g).
        onJoin = { round?.entry?.let { e -> dispatch(NavEvent.OpenStake(e.address.base58(), offer?.tier ?: 0, seekerKnown)) } },
    )
}

/** H7: Claim opens the signing flow (G7); back to where it was opened from. */
@Composable
internal fun Shared.ClaimHost(id: String, modifier: Modifier = Modifier) {
    val h = holdout(id)
    val n = now
    if (h == null || n == null) return Loading(modifier)
    RosterLoop(h)
    val claimSignature = chain.lastClaim?.takeIf { it.first == h.address }?.second
    ClaimScreen(
        h, h.stage(n), chain.busy, chain.message, chain.messageIsWarning, claimSignature,
        onClaim = { holdouts.claim(sender, h) },
        onBack = { dispatch(NavEvent.Back) },
        modifier = modifier,
        // Too little SOL for the fee: the faucet sheet first, then back here (ES-0a).
        shortOfSol = !canPayFees(view),
        onGetTokens = { dispatch(NavEvent.OpenFaucet) },
    )
}

/**
 * H2, the stake confirm, in the sheet over the screen that opened it (G8),
 * behind the Seeker gate (FR-003b–FR-003d): a wallet confirmed on this phone
 * goes straight on; otherwise mainnet is asked, and a no or no answer shows its
 * own variant in the same sheet. Stake asks for notifications once, then the
 * wallet — whose signing flow covers the sheet (G7).
 */
@Composable
internal fun Shared.StakeSheetHost(sheet: Sheet.Stake, seekerGate: SeekerGate) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(wallet, sheet.cohort, sheet.gate) {
        if (sheet.gate == SeekerStatus.Checking) dispatch(NavEvent.SeekerChecked(seekerGate.status(wallet)))
    }
    if (sheet.gate != SeekerStatus.Confirmed) {
        SeekerGateSheet(
            status = sheet.gate,
            onGetSeeker = { runCatching { uriHandler.openUri(SeekerGate.SEEKER_STORE_URL) } },
            onRetry = { dispatch(NavEvent.SeekerRetry) },
            onNotNow = { dispatch(NavEvent.CloseSheet) },
        )
        return
    }
    val h = holdout(sheet.cohort)
    val n = now
    val v = view
    if (h == null || n == null || v == null) return Loading()
    RosterLoop(h)
    StakeSheet(
        h = h,
        skrBalance = v.skr,
        lamports = v.lamports,
        now = n,
        // A running Holdout the member has missed a deadline in does not block: the join sends its record too (ES-17).
        joinBlocker = Journey.joinBlocker(h.cohort, v.profile, v.runningEnd(n), n, runningInBreach = v.runningInBreach(n)),
        busy = chain.busy,
        message = chain.message,
        messageIsWarning = chain.messageIsWarning,
        onStake = { tier -> joinAsking { holdouts.join(sender, h, tier, n) } },
        onGetTokens = { dispatch(NavEvent.OpenFaucet) },
        initialTier = sheet.tier,
        onTierChange = { dispatch(NavEvent.StakeTier(it)) },
        comeback = v.comebackOffer(n, h.cohort.leagueId),
        // "See your lobby": the sheet closes, then the lobby the member already holds a place in opens.
        onSeeLobby = v.mine(n).firstOrNull { it.status(n) == CohortStatus.Open || it.status(n) == CohortStatus.Locked }?.let { lobby ->
            {
                dispatch(NavEvent.CloseSheet)
                dispatch(NavEvent.Open(Screen.Lobby(lobby.address.base58())))
            }
        },
        showNotificationReason = !notificationsOn,
    )
}
