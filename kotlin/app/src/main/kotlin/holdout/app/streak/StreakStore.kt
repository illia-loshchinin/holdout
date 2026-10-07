package holdout.app.streak

import holdout.app.storage.Store

/**
 * The daily streak (T052, FR-049…FR-052, ES-9).
 *
 * Four rules, and the last two are the ones that matter:
 *
 * - **+1 on a completed day, from either source.** A cohort day counts as that
 *   day's Daily 25 — nobody trains twice to hold both (FR-050).
 * - **Broken by a day with none** — unless a **freeze** covers it (H35): one
 *   is earned for every seven consecutive active days, at most two are held,
 *   and they are used automatically. A frozen day keeps the streak without
 *   adding to it. Freezes cover the Daily 25 streak only; a Holdout day has no
 *   grace (FR-016). Banked reps short of the target do not keep it alive
 *   (FR-049).
 * - **A forfeit does not touch it** (FR-052). Losing a stake and losing a habit
 *   are different failures, and the app must not conflate them. There is
 *   deliberately no method here for a forfeit to call.
 * - **It turns over at 05:00 UTC by chain time**, so the home screen never
 *   shows yesterday's done state (ES-9). The day index comes from
 *   `HoldoutDay.indexOf`, the same formula the contract uses.
 *
 * The streak is cosmetic and personal: it unlocks nothing, gates nothing,
 * appears on no leaderboard, and nothing is ever paid against it (FR-051).
 * That is precisely why it needs no on-chain record and no anti-cheat — there
 * is nothing to win by lying to yourself.
 */
class StreakStore(private val store: Store) {

    /** [freezes]: held now, after any the days since the last active one have used. */
    data class Streak(val current: Int, val longest: Int, val completedToday: Boolean, val freezes: Int = 0)

    /**
     * What to show for [dayIndex]. Reading never writes: a streak that lapsed
     * is reported as zero, but it is not zeroed on disk until the member
     * actually trains again, so nothing is lost by opening the app late.
     */
    fun read(wallet: String, dayIndex: Long): Streak {
        val last = store.lastStreakDay(wallet)
        val stored = store.streak(wallet)
        val longest = store.longestStreak(wallet)

        val freezes = store.streakFreezes(wallet)
        // Whole days that passed with nothing done, before today.
        val missed = if (last == Store.NO_DAY) 0L else (dayIndex - last - 1).coerceAtLeast(0)
        val alive = last != Store.NO_DAY && missed <= freezes
        val current = if (alive) stored else 0
        // A process killed between the two streak writes leaves the longest
        // behind the current; it is never shown below it.
        return Streak(
            current,
            maxOf(longest, current),
            completedToday = last == dayIndex,
            // A streak that broke used every freeze it held on the way down.
            freezes = if (alive) {
                freezes - missed.toInt()
            } else if (last == Store.NO_DAY) {
                freezes
            } else {
                0
            },
        )
    }

    /**
     * A day was completed — a Daily 25 or a cohort day, it makes no difference.
     * Idempotent within a day: the second completed day of the same date does
     * not extend anything (FR-050, FR-025).
     */
    fun recordCompletedDay(wallet: String, dayIndex: Long): Streak {
        val last = store.lastStreakDay(wallet)
        if (last == dayIndex) return read(wallet, dayIndex)

        val held = store.streakFreezes(wallet)
        val missed = if (last == Store.NO_DAY) Long.MAX_VALUE else dayIndex - last - 1
        // Every missed day costs a freeze; with enough of them the streak goes on.
        val continues = missed in 0..held.toLong()
        val current = if (continues) store.streak(wallet) + 1 else 1
        // Freezes go automatically on missed days, so a break has used them all.
        var freezes = if (continues) {
            held - missed.toInt()
        } else if (last == Store.NO_DAY) {
            held
        } else {
            0
        }
        // One freeze per seven consecutive active days, never more than two held.
        if (current % FREEZE_EVERY_DAYS == 0) freezes = minOf(MAX_FREEZES, freezes + 1)
        // One write: the day and its freezes can never be saved apart, and a
        // second call for this day returns above without writing anything.
        store.setStreak(wallet, current, dayIndex, freezes)

        val longest = maxOf(current, store.longestStreak(wallet))
        if (longest != store.longestStreak(wallet)) store.setLongestStreak(wallet, longest)

        return Streak(current, longest, completedToday = true, freezes = freezes)
    }

    companion object {
        /** Active days in a row that earn one freeze (H35). */
        const val FREEZE_EVERY_DAYS = 7

        /** The most freezes held at once (H35). */
        const val MAX_FREEZES = 2
    }
}
