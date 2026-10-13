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
 * figure. It keeps the day the sitting banks into, so the crash page reads that
 * day's reps even when the app is reopened after the day has moved on.
 */
class SittingMarker(
    private val store: Store,
    private val wallet: String,
    private val scope: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /**
     * Counting has started, banking into [dayIndex]. The day goes first: the two are separate
     * writes, and a process killed between them must leave no marker without its day (a day
     * with no marker is never read, and the next start overwrites it).
     */
    fun start(dayIndex: Long) {
        store.setSittingDay(wallet, scope, dayIndex)
        store.setSittingStartedAt(wallet, scope, nowMillis())
    }

    /** A sitting running across the day boundary banks into [dayIndex] from here on. */
    fun moveTo(dayIndex: Long) {
        store.setSittingDay(wallet, scope, dayIndex)
    }

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
     * The reps are read from the day the marker was written for
     * ([Store.sittingDay]), so a member who comes back after the day moved on
     * still hears what was kept. A marker with no day (written by an older
     * build) falls back to [dayIndex], today's. One whose day was not known
     * when it started reads the reps banked under the unknown day while they
     * are still there; once the chain's answer has moved them to their day,
     * that day is the marker's too (`OfflineDays`).
     *
     * @param dayIndex the day it is now in this scope
     * @param header the top label (`SittingCopy.countingLabel`) for the sitting's day, given that day
     * @param secondsLeft the day's deadline (a Holdout) or the end of today (the Daily 25), given the sitting's day; null while unknown, and for a day that has gone
     */
    fun consume(
        store: Store,
        wallet: String,
        scope: String,
        dayIndex: Long,
        target: Int,
        header: (sittingDay: Long) -> String,
        secondsLeft: (sittingDay: Long) -> Long?,
    ): StoppedUi? {
        if (store.sittingStartedAt(wallet, scope) == null) return null
        val sittingDay = when (val kept = store.sittingDay(wallet, scope)) {
            null -> dayIndex
            Store.UNKNOWN_DAY -> if (store.bankedReps(wallet, scope, Store.UNKNOWN_DAY) > 0) Store.UNKNOWN_DAY else dayIndex
            else -> kept
        }
        store.setSittingStartedAt(wallet, scope, null)
        val banked = store.bankedReps(wallet, scope, sittingDay)
        if (banked <= 0 || banked >= target || store.isDayComplete(wallet, scope, sittingDay)) return null
        return StoppedUi(header = header(sittingDay), reps = target, banked = banked, notKept = 0, secondsLeft = secondsLeft(sittingDay), crash = true)
    }
}
