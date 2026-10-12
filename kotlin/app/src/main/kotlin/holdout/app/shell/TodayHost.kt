package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import holdout.app.home.CohortHome
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.home.NudgeRow
import holdout.app.home.TodayModel
import holdout.app.nav.NavEvent
import holdout.app.nav.Sitting
import holdout.app.nav.Tab
import holdout.app.sitting.BankingController
import holdout.app.sitting.StoppedScreen
import holdout.app.sitting.StoppedUi
import holdout.app.storage.Store
import holdout.app.streak.ToesMeter
import holdout.app.ui.ChainCheckBox
import holdout.core.HoldoutDay
import holdout.core.LeagueParams
import holdout.repcounter.Stakes

/**
 * The Today tab root (screen map T1/T2, ruling G1): T2 while a Holdout runs
 * with the member standing in it — its day only, with one nudge line per other
 * Holdout needing a tap (B-D1) — else T1, the Daily 25 with the nudges, the
 * comeback card and the Holdouts open to join. Running now is not here: it
 * lives on Holdouts and Standing's empty state (ruling G14).
 */
@Composable
internal fun Shared.TodayRoot(modifier: Modifier = Modifier) {
    // Finish anything a killed process left half-written, then read the day again.
    LaunchedEffect(wallet, dayIndex) {
        if (dayIndex != Store.UNKNOWN_DAY && BankingController.recover(store, streaks, wallet, Store.CHALLENGE, dayIndex, DAILY_25)) bumpRefresh()
    }
    val running = active()
    val chainNow = now
    // A sitting that did not end cleanly (the app killed mid-set) says what was kept, once, on the next open (ES-1, ES-2).
    var interrupted by remember { mutableStateOf<List<StoppedUi>>(emptyList()) }
    LaunchedEffect(wallet, dayIndex, running?.first?.address, running?.second?.dayIndex) {
        interrupted = interrupted + interruptedSittings(store, wallet, dayIndex, chainNow, running)
    }
    interrupted.firstOrNull()?.let { page ->
        StoppedScreen(page, onBack = { interrupted = interrupted.drop(1) }, modifier = modifier)
        return
    }
    if (running != null && chainNow != null) {
        val (h, stage) = running
        // Its roster is read every 30 s by the app's Standing loop: the running Holdout is the one Standing is about.
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
            onGetTokens = { dispatch(NavEvent.OpenFaucet) },
            modifier = modifier,
            streak = streaks.read(wallet, dayIndex).current,
            canPayFees = canPayFees(view),
            checking = chain.check.checking,
            stillUnreachable = chain.check.stillUnreachable,
            onCheck = { checkChain(h.address) },
            otherHoldouts = { TodayModel.nudges(view, chainNow, exceptCohort = h.address.base58()).forEach { NudgeRow(it) { dispatch(it.go) } } },
        )
        return
    }

    val banked = remember(wallet, dayIndex, refreshToken) { store.bankedReps(wallet, Store.CHALLENGE, dayIndex) }
    val complete = remember(wallet, dayIndex, refreshToken) { store.isDayComplete(wallet, Store.CHALLENGE, dayIndex) }
    val streak = remember(wallet, dayIndex, refreshToken) { streaks.read(wallet, dayIndex) }
    // Yesterday's count is still on the phone: a day short of 25 says so, once (ES-3, B-D6).
    val yesterday = remember(wallet, dayIndex, refreshToken) {
        if (dayIndex == Store.UNKNOWN_DAY) {
            null
        } else {
            TodayModel.yesterdayNote(
                banked = store.bankedReps(wallet, Store.CHALLENGE, dayIndex - 1),
                completed = store.isDayComplete(wallet, Store.CHALLENGE, dayIndex - 1),
                streakAlive = streak.current > 0,
                target = DAILY_25,
                todayBanked = banked,
                todayDone = complete,
            )
        }
    }
    val view = view
    val offline = chain.offline
    val holdoutsBlock = TodayModel.holdouts(view, chainNow, offline)
    // The nudges and the comeback card come from the chain read; with none they are empty.
    val nudges = remember(view, chainNow?.div(60), rosters) { TodayModel.nudges(view, chainNow) }
    val comeback = remember(view, chainNow?.div(60), seekerKnown) { TodayModel.comeback(view, chainNow, seekerKnown) }

    ChainCheckBox(offline, chain.check.checking, { checkChain(null) }, modifier) {
        HomeScreen(
            state = HomeState(
                remainingToday = (DAILY_25 - banked).coerceAtLeast(0),
                bankedToday = banked,
                target = DAILY_25,
                completedToday = complete,
                streak = streak.current,
                freezes = streak.freezes,
                toes = ToesMeter(store, DAILY_25).read(wallet, dayIndex),
                yesterdayNote = yesterday,
                secondsLeftToday = if (offline) null else chainNow?.let { HoldoutDay.secondsLeftIn(it) },
                nudges = nudges,
                comeback = comeback,
                holdouts = holdoutsBlock,
                checking = chain.check.checking,
                stillUnreachable = chain.check.stillUnreachable,
            ),
            onStart = {
                if (dayIndex == Store.UNKNOWN_DAY) offlineDays.sittingStarted(wallet, boot, android.os.SystemClock.elapsedRealtime())
                dispatch(NavEvent.StartSitting(Sitting.Daily25(dayIndex)))
            },
            onEvent = dispatch,
            onJoin = { cohort -> dispatch(NavEvent.OpenStake(cohort, seekerKnown = seekerKnown)) },
            onCheck = { checkChain(null) },
            modifier = Modifier,
        )
    }
}
