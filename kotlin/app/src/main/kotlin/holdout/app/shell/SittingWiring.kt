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
internal fun tooLateToSend(h: Holdout, day: Int, nowSeconds: Long?): Boolean = nowSeconds != null && Derive.deadlineOf(h.cohort, day) - nowSeconds < 0

/** Whether the chain already counts [day] for the member in [live] (the sitting's day-complete page then has nothing left to submit). */
internal fun dayOnChain(live: Holdout?, day: Int): Boolean = (live?.membership?.daysCompleted ?: 0) > day

/**
 * What ending a Holdout day's sitting does to where the member is: Today; and
 * a finished day that came too late to send goes on to the Forfeit of that
 * Holdout, as "Back to today" would otherwise leave the member guessing why.
 */
internal fun holdoutDayEndEvents(cohort: String, completed: Boolean, tooLate: Boolean): List<NavEvent> = if (completed && tooLate) listOf(NavEvent.SittingEnded, NavEvent.Open(Screen.Forfeit(cohort))) else listOf(NavEvent.SittingEnded)

/**
 * The crash variant of the Stopped page (ES-1, ES-2) for every sitting that
 * did not end cleanly: the running Holdout's today, then the Daily 25's.
 * Reading a marker clears it, so each is said once. Each reads the reps from the
 * day its marker was written for, not today's, so a sitting killed before a day
 * boundary still says what was kept. The Daily 25's waits while the day is not
 * known ([Store.UNKNOWN_DAY]). A sitting of one of [others] — the member's
 * Holdouts not running for them now, one forfeited since included — says what
 * it kept too, with no time left: its day has gone.
 */
internal fun interruptedSittings(
    store: Store,
    wallet: String,
    dayIndex: Long,
    chainNow: Long?,
    running: Pair<Holdout, Stage.Active>?,
    others: List<Holdout> = emptyList(),
): List<StoppedUi> = buildList {
    running?.let { (h, stage) ->
        val target = h.cohort.repsPerDay
        InterruptedSitting.consume(
            store,
            wallet,
            cohortScope(h),
            stage.dayIndex.toLong(),
            target,
            header = { SittingCopy.countingLabel(it.toInt() + 1, target) },
            // A day that has gone has no time left.
            secondsLeft = { if (it == stage.dayIndex.toLong()) chainNow?.let { now -> Derive.timeLeftToday(h.cohort, now) } else null },
        )?.let(::add)
    }
    others.filter { it.address != running?.first?.address }.forEach { h ->
        val target = h.cohort.repsPerDay
        InterruptedSitting.consume(
            store,
            wallet,
            cohortScope(h),
            // A marker from before the day was kept with it: the day after those on chain, the one it was for.
            (h.membership?.daysCompleted ?: 0).toLong(),
            target,
            header = { SittingCopy.countingLabel(it.toInt() + 1, target) },
            secondsLeft = { null },
        )?.let(::add)
    }
    if (dayIndex != Store.UNKNOWN_DAY) {
        InterruptedSitting.consume(
            store,
            wallet,
            Store.CHALLENGE,
            dayIndex,
            DAILY_25,
            header = { SittingCopy.countingLabel(null, DAILY_25) },
            secondsLeft = { if (it == dayIndex) chainNow?.let(HoldoutDay::secondsLeftIn) else null },
        )?.let(::add)
    }
}
