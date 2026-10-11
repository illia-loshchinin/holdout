package holdout.app.sitting

import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.streak.ToesMeter
import holdout.repcounter.RepStance

/**
 * Banked reps (T051, FR-024, FR-023, AC-15).
 *
 * ```
 * morning   sitting 1:  17 reps  →  banks 10, the 7 extra are lost   banked 10
 * lunch     sitting 2:  10 reps  →  banks 10                         banked 20
 * evening   sitting 3:   5 reps  →  day complete at the 25th rep     done
 * ```
 *
 * Three rules, and all three are about what the member can trust:
 *
 * 1. **A block banks the instant its tenth verified rep is counted**, written
 *    to storage before the screen shows it. What is on screen is what survives
 *    a crash.
 * 2. **The day completes the moment banked + this sitting reach the target**,
 *    so the last block may be smaller than ten — 10 + 10 + 5.
 * 3. **When a sitting ends for any reason, reps short of the next block are
 *    discarded.** Banked blocks stay. There is no pause limit.
 */
class BankingController(
    private val store: Store,
    private val wallet: String,
    private val scope: String,
    dayIndex: Long,
    private val target: Int,
    /**
     * The Daily 25's toes meter, or null where it is not kept. It is told a
     * rep's stance only once that rep is banked: a rep discarded with its
     * sitting was never part of the day, and must not hold a Hard day back.
     */
    private val toes: ToesMeter? = null,
) {
    companion object {
        const val BLOCK = 10

        /**
         * Finishes what a killed process may have left half-written for the
         * Daily 25: the target banked but the day not marked complete (two
         * writes, and the process can die between them), or the day complete
         * but its streak never recorded. Idempotent; returns whether anything
         * was written. Never walks a later streak day back.
         */
        fun recover(
            store: Store,
            streaks: StreakStore,
            wallet: String,
            scope: String,
            dayIndex: Long,
            target: Int,
        ): Boolean {
            var wrote = false
            if (!store.isDayComplete(wallet, scope, dayIndex) && store.bankedReps(wallet, scope, dayIndex) >= target) {
                store.markDayComplete(wallet, scope, dayIndex)
                wrote = true
            }
            val last = store.lastStreakDay(wallet)
            if (scope == Store.CHALLENGE && store.isDayComplete(wallet, scope, dayIndex) && (last == Store.NO_DAY || last < dayIndex)) {
                streaks.recordCompletedDay(wallet, dayIndex)
                wrote = true
            }
            return wrote
        }
    }

    /** The day this sitting banks into; moves forward once, at 05:00 UTC, if the sitting runs across it. */
    var dayIndex: Long = dayIndex
        private set

    /**
     * The day turned over mid-sitting (05:00 UTC, by chain time). Blocks
     * already banked stay on the day they were done; the reps not yet in a
     * block, and everything after, count for [newDay] — one clock, and no
     * effort lost to the boundary (decided 2026-09-26). Returns whether it
     * moved: never backwards, never to the same day.
     */
    fun rollOverTo(newDay: Long): Boolean {
        if (newDay <= dayIndex) return false
        dayIndex = newDay
        return true
    }

    /** Verified reps in the current sitting that have not yet formed a block. */
    var loose: Int = 0
        private set

    /** How each [loose] rep was done, in order; recorded with the block, dropped with it. */
    private val looseStances = mutableListOf<RepStance?>()

    /** Whole banked blocks carried from earlier sittings today. */
    val banked: Int get() = store.bankedReps(wallet, scope, dayIndex)

    /** What the member has to show for today, banked or not. */
    val total: Int get() = banked + loose

    val remaining: Int get() = (target - total).coerceAtLeast(0)

    /** Marked complete, or the target banked — the process may have died between the two writes. */
    val isDayComplete: Boolean get() = store.isDayComplete(wallet, scope, dayIndex) || banked >= target

    sealed interface Outcome {
        /** Nothing to announce; the rep is counted but the block is not full. */
        data object Counted : Outcome

        /** A block of ten just became durable. */
        data class Banked(val banked: Int, val remaining: Int) : Outcome

        /** The target is reached. The day is done and written down. */
        data class DayComplete(val total: Int) : Outcome
    }

    /**
     * One verified rep. Call it only for reps the counter accepted.
     *
     * Completion is checked **before** the block rule, because the day is done
     * the moment the target is reached — waiting for a tenth rep that the
     * member does not owe would be asking for 30 push-ups to finish 25.
     */
    fun onVerifiedRep(stance: RepStance? = null): Outcome {
        if (isDayComplete) return Outcome.DayComplete(total)

        loose += 1
        looseStances += stance

        if (banked + loose >= target) {
            store.setBankedReps(wallet, scope, dayIndex, target)
            store.markDayComplete(wallet, scope, dayIndex)
            loose = 0
            recordStances()
            return Outcome.DayComplete(target)
        }

        if (loose >= BLOCK) {
            val blocks = loose / BLOCK
            loose -= blocks * BLOCK
            val newTotal = banked + blocks * BLOCK
            store.setBankedReps(wallet, scope, dayIndex, newTotal)
            recordStances()
            return Outcome.Banked(newTotal, (target - newTotal).coerceAtLeast(0))
        }

        return Outcome.Counted
    }

    /**
     * The sitting ended — the member stopped, the app went away, the phone
     * died. Loose reps are gone; banked blocks are untouched.
     *
     * Nothing is written here. The banked figure was already durable when its
     * block formed, and a write on the way out is a write that does not happen
     * when the process is killed.
     */
    fun endSitting(): Int {
        val discarded = loose
        loose = 0
        looseStances.clear()
        return discarded
    }

    /**
     * The reps just banked go on the toes meter, on the day they banked into.
     * After the banked figure, never before: a kill in between leaves the meter
     * short, which only ever under-claims a Hard day.
     */
    private fun recordStances() {
        looseStances.forEach { toes?.record(wallet, dayIndex, it) }
        looseStances.clear()
    }
}
