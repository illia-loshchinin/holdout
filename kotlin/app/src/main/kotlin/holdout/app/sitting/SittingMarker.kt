package holdout.app.sitting

import holdout.app.storage.Store
import holdout.app.streak.StreakMilestones
import holdout.app.streak.StreakStore

/**
 * The sitting-in-progress marker (`sitting:{wallet}:{scope}`, data-model §4, ES-1, ES-2).
 *
 * Written when counting starts; removed on every clean exit — stop, completion,
 * the app leaving the foreground (which ends the sitting, FR-020) and the screen
 * going away. If it is found set at the next open, the app crashed or was
 * killed mid-sitting ([InterruptedSitting]). It is written to storage and read
 * back, nothing else: the counter never reads it and it changes no banked
 * figure.
 */
class SittingMarker(
    private val store: Store,
    private val wallet: String,
    private val scope: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** Counting has started. */
    fun start() = store.setSittingStartedAt(wallet, scope, nowMillis())

    /** The sitting ended cleanly. */
    fun clear() = store.setSittingStartedAt(wallet, scope, null)
}

/**
 * The daily streak once a Daily 25 is finished: how many days, and whether
 * this day grew it. "+1" is shown only when it did — a Holdout day done earlier
 * the same date has counted for the streak already (FR-050, FR-025).
 */
data class StreakAfter(val days: Int, val grew: Boolean) {
    /** This day took the streak to a milestone (S6 shows the milestone state, once): a day that did not grow it reaches nothing. */
    val milestone: Boolean get() = grew && StreakMilestones.isMilestone(days)

    companion object {
        /** Records [dayIndex] as completed and reports the result. Idempotent per day. */
        fun record(streaks: StreakStore, wallet: String, dayIndex: Long): StreakAfter {
            val before = streaks.read(wallet, dayIndex).current
            val after = streaks.recordCompletedDay(wallet, dayIndex).current
            return StreakAfter(days = after, grew = after > before)
        }
    }
}

/**
 * The crash variant of the Stopped screen (ES-1, ES-2). Reading the marker and
 * clearing it are one step, so the message comes once: if the member leaves
 * before tapping, it has been said as much as it needs to be.
 */
object InterruptedSitting {
    /**
     * What to tell the member about a sitting [scope] that did not end cleanly,
     * or null: no marker, nothing banked, or the day was completed anyway.
     * The marker is cleared in every case. Reads [Store.bankedReps] and never
     * writes one.
     *
     * @param header the top label (`SittingCopy.countingLabel`)
     * @param secondsLeft the day's deadline (a Holdout) or the end of today (the Daily 25); null while unknown
     */
    fun consume(
        store: Store,
        wallet: String,
        scope: String,
        dayIndex: Long,
        target: Int,
        header: String,
        secondsLeft: Long?,
    ): StoppedUi? {
        if (store.sittingStartedAt(wallet, scope) == null) return null
        store.setSittingStartedAt(wallet, scope, null)
        val banked = store.bankedReps(wallet, scope, dayIndex)
        if (banked <= 0 || banked >= target || store.isDayComplete(wallet, scope, dayIndex)) return null
        return StoppedUi(header = header, reps = target, banked = banked, notKept = 0, secondsLeft = secondsLeft, crash = true)
    }
}
