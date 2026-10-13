package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import holdout.app.chain.Holdout
import holdout.app.home.DayState
import holdout.app.sitting.BankingController
import holdout.app.wallet.SignAction
import holdout.core.Stage

/** What the phone knows of today's day in a Holdout: reps banked, a finished day not yet on chain, and whether it was already sent by itself. */
internal class DayInputs(val banked: Int, val pendingDay: Int?, val autoSendTried: Boolean, val mayStillLand: Boolean = false)

/**
 * Reads today's day of [h] from the phone for Today (T2) and Standing's day strip alike, so both
 * draw the same day (FR-069a, FR-069b). A process killed between banking the last block and
 * marking the day done leaves the target banked and no marker: it is finished here, as for
 * the Daily 25, or the day could be neither trained again nor submitted.
 */
@Composable
internal fun Shared.dayInputs(h: Holdout, stage: Stage.Active): DayInputs {
    val scope = cohortScope(h)
    LaunchedEffect(wallet, h.address, stage.dayIndex) {
        if (BankingController.recover(store, streaks, wallet, scope, stage.dayIndex.toLong(), h.cohort.repsPerDay)) bumpRefresh()
    }
    val banked = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.bankedReps(wallet, scope, stage.dayIndex.toLong()) }
    val pendingToday = pendingTodayOf(h, stage)
    // A send of today's day that may still land keeps Submit shut on both screens until a chain read settles it (FR-070a).
    val mayStillLand = chain.pendingFor(SignAction.Submit, h.address, stage.dayIndex) != null
    return DayInputs(banked, pendingToday, pendingToday != null && chain.autoSubmit.tried(h.address.base58(), pendingToday), mayStillLand)
}

/** The day [h] is on, drawn by Standing's strip (Today's [holdout.app.home.CohortHome] builds the same from the same inputs). */
internal fun Shared.dayState(h: Holdout, stage: Stage.Active, now: Long, inputs: DayInputs): DayState = DayState.of(h.cohort, stage, now, inputs.banked, inputs.pendingDay, inputs.autoSendTried, chain.offline, chain.busy, canPayFees(view), inputs.mayStillLand)

/** Start (or Keep going) on today's day of [h]: the same sitting from Today and from Standing. */
internal fun Shared.startDay(h: Holdout, dayIndex: Int) {
    DayState.start(h, dayIndex)?.let(dispatch)
}
