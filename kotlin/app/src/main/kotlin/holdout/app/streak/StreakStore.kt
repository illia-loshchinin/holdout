package holdout.app.streak

import holdout.app.storage.Store

/**
 * The daily streak (T052, FR-049…FR-052, ES-9).
 *
 * Four rules, and the last two are the ones that matter:
 *
 * - **+1 on a completed day, from either source.** A cohort day counts as that
 *   day's Daily 25 — nobody trains twice to hold both (FR-050).
 * - **Broken by a day with none.** Banked reps short of the target do not keep
 *   it alive (FR-049).
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

    data class Streak(val current: Int, val longest: Int, val completedToday: Boolean)

    /**
     * What to show for [dayIndex]. Reading never writes: a streak that lapsed
     * is reported as zero, but it is not zeroed on disk until the member
     * actually trains again, so nothing is lost by opening the app late.
     */
    fun read(wallet: String, dayIndex: Long): Streak {
        val last = store.lastStreakDay(wallet)
        val stored = store.streak(wallet)
        val longest = store.longestStreak(wallet)

        val current = when {
            last == Store.NO_DAY -> 0
            // Trained today, or yesterday and today is still open.
            last == dayIndex || last == dayIndex - 1 -> stored
            else -> 0
        }
        // A process killed between the two streak writes leaves the longest
        // behind the current; it is never shown below it.
        return Streak(current, maxOf(longest, current), completedToday = last == dayIndex)
    }

    /**
     * A day was completed — a Daily 25 or a cohort day, it makes no difference.
     * Idempotent within a day: the second completed day of the same date does
     * not extend anything (FR-050, FR-025).
     */
    fun recordCompletedDay(wallet: String, dayIndex: Long): Streak {
        val last = store.lastStreakDay(wallet)
        if (last == dayIndex) return read(wallet, dayIndex)

        val current = if (last == dayIndex - 1) store.streak(wallet) + 1 else 1
        store.setStreak(wallet, current, dayIndex)

        val longest = maxOf(current, store.longestStreak(wallet))
        if (longest != store.longestStreak(wallet)) store.setLongestStreak(wallet, longest)

        return Streak(current, longest, completedToday = true)
    }
}
