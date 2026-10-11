package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import holdout.app.chain.Deployment
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.home.CohortHome
import holdout.app.home.DailyHoldoutLine
import holdout.app.home.HoldoutOffer
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.home.RunningHoldoutCard
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Sitting
import holdout.app.nav.Tab
import holdout.app.sitting.BankingController
import holdout.app.storage.Store
import holdout.app.streak.ToesMeter
import holdout.app.ui.ChainCheckBox
import holdout.app.ui.ChainUnreachable
import holdout.app.ui.CheckAgain
import holdout.app.ui.QuietAction
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.core.HoldoutDay
import holdout.core.LeagueParams
import holdout.repcounter.Stakes
import kotlinx.coroutines.delay

/**
 * The Today tab root (screen map T1/T2, ruling G1): T2 while a Holdout runs
 * with the member standing in it — its day only, with one nudge line per other
 * Holdout needing a tap (B-D1) — else T1, the Daily 25 with the Holdouts open
 * to join and the ones running now.
 */
@Composable
internal fun Shared.TodayRoot(modifier: Modifier = Modifier) {
    // Finish anything a killed process left half-written, then read the day again.
    LaunchedEffect(wallet, dayIndex) {
        if (dayIndex != Store.UNKNOWN_DAY && BankingController.recover(store, streaks, wallet, Store.CHALLENGE, dayIndex, DAILY_25)) bumpRefresh()
    }
    val running = active()
    val chainNow = now
    if (running != null && chainNow != null) {
        val (h, stage) = running
        InFrontLoop(h.address) {
            holdouts.loadRoster(h.address)
            delay(ROSTER_REFRESH_MILLIS)
        }
        val scope = cohortScope(h)
        // A process killed between banking the last block and marking the day done
        // leaves the target banked and no marker: finish it, as for the Daily 25,
        // or the day could be neither trained again nor submitted.
        LaunchedEffect(wallet, h.address, stage.dayIndex) {
            if (BankingController.recover(store, streaks, wallet, scope, stage.dayIndex.toLong(), h.cohort.repsPerDay)) bumpRefresh()
        }
        val banked = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.bankedReps(wallet, scope, stage.dayIndex.toLong()) }
        val pendingToday = pendingTodayOf(h, stage)
        CohortHome(
            h = h,
            members = rosters[h.address],
            stage = stage,
            now = chainNow,
            banked = banked,
            pendingDay = pendingToday,
            autoSendTried = pendingToday != null && chain.autoSubmit.tried(h.address.base58(), pendingToday),
            offline = chain.offline,
            busy = chain.busy,
            message = chain.message,
            messageIsWarning = chain.messageIsWarning,
            onStart = {
                // An exercise or a form this build does not know is refused, never guessed (FR-014, H30, H35).
                if (h.cohort.exercise == LeagueParams.EXERCISE_PUSHUP && Stakes.ofForm(h.cohort.form) != null) {
                    dispatch(NavEvent.StartSitting(Sitting.HoldoutDay(h.address.base58(), stage.dayIndex)))
                }
            },
            onSubmit = { day -> submitDayOf(h, day) },
            // Its standing is the Standing tab's root.
            onDetail = { dispatch(NavEvent.SelectTab(Tab.Standing)) },
            onAbout = { dispatch(NavEvent.Open(Screen.About)) },
            onRoster = { dispatch(NavEvent.Open(Screen.Roster(h.address.base58()))) },
            onDaily25 = { dispatch(NavEvent.StartSitting(Sitting.Daily25(dayIndex))) },
            onGetTokens = { dispatch(NavEvent.OpenFaucet) },
            modifier = modifier,
            checking = chain.check.checking,
            stillUnreachable = chain.check.stillUnreachable,
            onCheck = { checkChain(h.address) },
            otherHoldouts = { Nudges(exceptCohort = h.address.base58()) },
        )
        return
    }

    val banked = remember(wallet, dayIndex, refreshToken) { store.bankedReps(wallet, Store.CHALLENGE, dayIndex) }
    val complete = remember(wallet, dayIndex, refreshToken) { store.isDayComplete(wallet, Store.CHALLENGE, dayIndex) }
    val streak = remember(wallet, dayIndex, refreshToken) { streaks.read(wallet, dayIndex) }
    val view = view
    // The Daily 25's line and whether it is the comeback nudge, worked out once for Today.
    val holdoutLine = remember(view, chainNow) {
        chainNow?.let { n -> view?.let { v -> DailyHoldoutLine.line(v.openToJoin(n).map { it.cohort }, v.comebackOffers(n), n) } }
    }
    // "Running now" shows figures derived from rosters, as the connect screen's
    // live list does — read here, and kept fresh (FR-062a).
    val watching = chainNow?.let { n -> view?.runningNotMine(n)?.map { it.address } }.orEmpty()
    InFrontLoop(watching) {
        if (watching.isEmpty()) kotlinx.coroutines.awaitCancellation()
        watching.forEach { holdouts.loadRoster(it) }
        delay(ROSTER_REFRESH_MILLIS)
    }

    ChainCheckBox(chain.offline, chain.check.checking, { checkChain(null) }, modifier) {
        HomeScreen(
            state = HomeState(
                remainingToday = (DAILY_25 - banked).coerceAtLeast(0),
                bankedToday = banked,
                target = DAILY_25,
                completedToday = complete,
                streak = streak.current,
                freezes = streak.freezes,
                toes = ToesMeter(store, DAILY_25).read(wallet, dayIndex),
                holdoutLine = holdoutLine?.text,
                holdoutLineIsComeback = holdoutLine?.comeback == true,
                secondsLeftToday = chainNow?.let { HoldoutDay.secondsLeftIn(it) },
            ),
            onStart = {
                if (dayIndex == Store.UNKNOWN_DAY) offlineDays.sittingStarted(wallet, boot, android.os.SystemClock.elapsedRealtime())
                dispatch(NavEvent.StartSitting(Sitting.Daily25(dayIndex)))
            },
            modifier = Modifier,
            below = { TodayBelow() },
        )
    }
}

/** T1 under the hero: the chain's state, one nudge per Holdout of the member's, the offers, Running now, the tour. */
@Composable
private fun Shared.TodayBelow() {
    val chainNow = now
    val view = view
    if (chainNow == null || view == null) {
        if (chain.offline) {
            SectionLabel("${Deployment.networkName} unreachable — the Daily 25 still works")
            CheckAgain(chain.check.checking, chain.check.stillUnreachable, { checkChain(null) })
        } else {
            Reading { SectionLabel("Reading the chain…") }
        }
        QuietAction("How Holdout works →", { dispatch(NavEvent.OpenTour) })
        return
    }
    // An earlier read, shown while the chain can't be reached: said so, with Check again (spec 005 US2).
    if (chain.offline) ChainUnreachable(chain.check.checking, chain.check.stillUnreachable, { checkChain(null) })
    if (view.config == null) {
        SectionLabel("Holdouts are not live on devnet yet")
    } else {
        Nudges(exceptCohort = null)
        val open = view.openToJoin(chainNow)
        if (open.isNotEmpty()) SectionLabel("Holdouts open to join")
        open.forEach { h -> HoldoutOffer(h, chainNow, onClick = { dispatch(NavEvent.OpenStake(h.address.base58(), seekerKnown = seekerKnown)) }) }
        // Watched from outside, by every wallet, with or without a Seeker (FR-062a).
        val running = view.runningNotMine(chainNow)
        if (running.isNotEmpty()) SectionLabel("Running now")
        running.forEach { h -> RunningHoldoutCard(h, chainNow, rosters[h.address], onClick = { dispatch(NavEvent.Open(Screen.Detail(h.address.base58()))) }) }
    }
    QuietAction("How Holdout works →", { dispatch(NavEvent.OpenTour) })
}

/**
 * One line per Holdout of the member's that needs a tap — a lobby, a forfeit,
 * settling, a claim — opening its screen (screen map §3 rule 4, B-D1). The full
 * cards live on H0.
 */
@Composable
internal fun Shared.Nudges(exceptCohort: String?) {
    val chainNow = now ?: return
    val view = view ?: return
    val yours = remember(view, chainNow / 60, rosters) { MyHoldoutsModel.build(view, chainNow, rosters).yours }
    yours.filter { it.cohort != exceptCohort && it.status != "running" }.forEach { y ->
        QuietAction("${y.name}: ${y.detail ?: y.line} →", { dispatch(y.go) }, warning = y.status == "out")
    }
}
