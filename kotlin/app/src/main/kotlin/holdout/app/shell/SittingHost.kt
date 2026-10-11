package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import holdout.app.nav.NavEvent
import holdout.app.nav.Sitting
import holdout.app.sitting.SittingScreen
import holdout.app.sitting.SittingViewModel
import holdout.app.sittingNote
import holdout.app.storage.Store
import holdout.app.ui.Format
import holdout.core.Derive
import holdout.core.HoldoutDay
import holdout.repcounter.Stakes
import kotlinx.coroutines.flow.first

/**
 * A sitting, full screen with no bar (rulings G1): the free Daily 25, or one
 * day of a Holdout (T065). Ending it — done, stopped, or its day sent on —
 * lands on Today; a Holdout day that can be sent goes to the wallet, whose
 * signing flow then shows (G7).
 */
@Composable
internal fun Shared.SittingHost(s: Sitting, modifier: Modifier = Modifier) {
    when (s) {
        is Sitting.Daily25 -> {
            val vm = remember(wallet, s.day, refreshToken) {
                SittingViewModel(
                    store = store,
                    streaks = streaks,
                    wallet = wallet,
                    scope = Store.CHALLENGE,
                    dayIndex = s.day,
                    target = DAILY_25,
                    chainSecondsNow = { clock.nowSeconds() },
                    stakes = Stakes.DAILY,
                )
            }
            DisposableEffect(vm) { onDispose { vm.release() } }
            // A Holdout day waiting for the network goes over the Daily 25 too: a sitting
            // has no pause limit, and the day's deadline must not pass with the app open
            // and connected. Never mid-set, though — leaving for the wallet then would
            // cost the set's loose reps; it opens once the set ends. Out of a set the
            // wallet's round trip costs nothing: banked blocks are stored, and this
            // sitting, held in the composition, is where the member comes back to.
            SendWaitingDay(vm) { vm.uiState.first { !it.step.inSet } }
            val done = {
                dispatch(NavEvent.SittingEnded)
                bumpRefresh()
            }
            // After the Daily 25: the next Holdout to join, or a comeback in its place — and
            // nothing while a Holdout runs or waits in a lobby, or for a wallet known not to
            // be a Seeker (C-D8, G12). A tap ends this sitting first: the sheet cannot open under it.
            val offer = remember(view, now?.div(60), seekerKnown, knownNotSeeker) { SittingOffers.of(view, now, seekerKnown, knownNotSeeker) }
            SittingScreen(
                viewModel = vm,
                onFinished = done,
                onCancelled = { done() },
                modifier = modifier,
                offer = offer?.offer,
                onOffer = {
                    done()
                    offer?.let { dispatch(it.go) }
                },
            )
        }
        is Sitting.HoldoutDay -> {
            // The Holdout as it was when the sitting started; a later read never swaps it mid-sitting.
            val h = remember(s) { holdout(s.cohort) }
            if (h == null) {
                LaunchedEffect(s) { dispatch(NavEvent.SittingEnded) }
                return
            }
            val scope = cohortScope(h)
            val vm = remember(wallet, h.address, s.day, refreshToken) {
                SittingViewModel(
                    store = store,
                    streaks = streaks,
                    wallet = wallet,
                    scope = scope,
                    dayIndex = s.day.toLong(),
                    target = h.cohort.repsPerDay,
                    chainSecondsNow = { clock.nowSeconds() },
                    // The cohort's own form (H35): Habit from the toes, Spark with knees
                    // allowed — 45° only either way (FR-021a). An unknown form never
                    // reaches here: Start refuses it.
                    stakes = Stakes.ofForm(h.cohort.form) ?: Stakes.STAKED,
                    // The cohort's own days — ten minutes each in the short test cohort.
                    dayOf = { Derive.dayIndex(h.cohort, it).toLong() },
                    // A Holdout day ends at its deadline: no rolling into the next (a missed day is a breach).
                    rollsOver = false,
                    timeLeftAt = { Derive.timeLeftToday(h.cohort, it) },
                )
            }
            DisposableEffect(vm) { onDispose { vm.release() } }
            // The chain can't be reached, or that day's deadline has passed: a submit would
            // fail (before the wallet, or with DeadlinePassed), so the finished screen says
            // the day is not sent yet, or too late to send, and "Back to today" sends
            // nothing — an unsent day waits on the phone and goes when the chain answers
            // (FR-024b, 2026-10-10; too late online or off, 2026-10-11). One value per
            // composition, so label and tap agree.
            val note = sittingNote(h, s.day, now, chain.offline)
            // The chain already counts this day (read live, not the Holdout as the sitting started):
            // the finished page has nothing left to submit.
            val onChain = dayOnChain(holdout(s.cohort), s.day)
            val done = {
                // The day it started on, or the next one if it ran across a boundary.
                val today = now?.let { Derive.dayIndex(h.cohort, it) }
                val completed = listOfNotNull(s.day, today).distinct().firstOrNull { store.isDayComplete(wallet, scope, it.toLong()) }
                // A finished day that came too late to send goes on to its Forfeit, not just back to Today.
                holdoutDayEndEvents(s.cohort, completed != null, completed != null && tooLateToSend(h, completed, now)).forEach(dispatch)
                bumpRefresh()
                if (completed != null) {
                    // A completed cohort day is that day's training for the streak too, so
                    // nobody trains twice (FR-050) — judged leagues only, whose days are real days.
                    // The day the reps were done on — the cohort day's own date — not the day
                    // this screen is left on: across 05:00 UTC those are two different days.
                    if (!h.isTest) streaks.recordCompletedDay(wallet, HoldoutDay.indexOf(h.cohort.start + completed * h.cohort.dayLength))
                    if (note == null && !onChain) holdouts.submitDay(sender, h, completed, h.cohort.repsPerDay)
                }
            }
            SittingScreen(
                viewModel = vm,
                onFinished = done,
                onCancelled = { done() },
                modifier = modifier,
                finishLabel = if (note == null && !onChain) "Submit day ${s.day + 1}" else "Back to today",
                finishNote = note,
                headerLabel = Format.headerLabel(h.cohort, s.day + 1),
                onChain = onChain,
            )
        }
    }
}
