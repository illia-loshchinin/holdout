package holdout.app.shell

import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.sitting.InterruptedSitting
import holdout.app.sitting.SittingCopy
import holdout.app.sitting.StoppedUi
import holdout.app.storage.Store
import holdout.core.Derive
import holdout.core.HoldoutDay
import holdout.core.Stage

/**
 * The parts of the sitting's wiring that decide something, apart from the
 * composables that carry them out, so a JVM test pins each (wiring items 3, 5, 6).
 */

/** Whether [day] of [h] can no longer be sent: its own deadline has passed at chain time [nowSeconds] (unknown time is never late). */
internal fun tooLateToSend(h: Holdout, day: Int, nowSeconds: Long?): Boolean = nowSeconds != null && Derive.deadlineOf(h.cohort, day) - nowSeconds <= 0

/** Whether the chain already counts [day] for the member in [live] (the sitting's day-complete page then has nothing left to submit). */
internal fun dayOnChain(live: Holdout?, day: Int): Boolean = (live?.membership?.daysCompleted ?: 0) > day

/**
 * What ending a Holdout day's sitting does to where the member is: Today; and
 * a finished day that came too late to send goes on to the Forfeit of that
 * Holdout, as "Back to today" would otherwise leave the member guessing why.
 */
internal fun holdoutDayEndEvents(cohort: String, completed: Boolean, tooLate: Boolean): List<NavEvent> =
    if (completed && tooLate) listOf(NavEvent.SittingEnded, NavEvent.Open(Screen.Forfeit(cohort))) else listOf(NavEvent.SittingEnded)

/**
 * The crash variant of the Stopped page (ES-1, ES-2) for every sitting that
 * did not end cleanly: the running Holdout's today, then the Daily 25's.
 * Reading a marker clears it, so each is said once. The Daily 25's waits while
 * the day is not known ([Store.UNKNOWN_DAY]).
 */
internal fun interruptedSittings(store: Store, wallet: String, dayIndex: Long, chainNow: Long?, running: Pair<Holdout, Stage.Active>?): List<StoppedUi> = buildList {
    running?.let { (h, stage) ->
        val target = h.cohort.repsPerDay
        InterruptedSitting.consume(
            store, wallet, cohortScope(h), stage.dayIndex.toLong(), target,
            header = SittingCopy.countingLabel(stage.dayIndex + 1, target),
            secondsLeft = chainNow?.let { Derive.timeLeftToday(h.cohort, it) },
        )?.let(::add)
    }
    if (dayIndex != Store.UNKNOWN_DAY) {
        InterruptedSitting.consume(
            store, wallet, Store.CHALLENGE, dayIndex, DAILY_25,
            header = SittingCopy.countingLabel(null, DAILY_25),
            secondsLeft = chainNow?.let { HoldoutDay.secondsLeftIn(it) },
        )?.let(::add)
    }
}
