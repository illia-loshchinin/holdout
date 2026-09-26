package holdout.app.streak

import holdout.app.storage.OfflineMark
import holdout.app.storage.Store
import holdout.core.HoldoutDay

/**
 * A Daily 25 needs no connection at all (FR-024b) — including before the app
 * has ever read chain time. Such a sitting cannot know its day, so it banks
 * under [Store.UNKNOWN_DAY] and its start is marked on the boot's monotonic
 * clock. Once the chain answers, the start's chain time is worked back from
 * that clock, and the reps move onto the day they were really done.
 *
 * After a reboot the monotonic clock has restarted and the start cannot be
 * dated; those reps stay where they are rather than land on a guessed day.
 * A saved chain reading (research R7) makes that rare: it is only possible
 * when the phone rebooted and has not been online since.
 */
class OfflineDays(
    private val store: Store,
    private val streaks: StreakStore,
    private val target: Int,
) {
    /** A Daily 25 sitting is starting with chain time unknown. The latest start wins. */
    fun sittingStarted(wallet: String, boot: Int, elapsedMillis: Long) {
        store.setOfflineSittingMark(wallet, OfflineMark(boot, elapsedMillis))
    }

    /**
     * Chain time is now [chainNow], at [elapsedMillis] on boot [boot]: moves
     * anything banked under [Store.UNKNOWN_DAY] onto its real day. Returns
     * that day, or null when there was nothing to move or it cannot be dated.
     */
    fun settle(wallet: String, boot: Int, elapsedMillis: Long, chainNow: Long): Long? {
        val mark = store.offlineSittingMark(wallet) ?: return null
        val scope = Store.CHALLENGE
        val banked = store.bankedReps(wallet, scope, Store.UNKNOWN_DAY)
        val done = store.isDayComplete(wallet, scope, Store.UNKNOWN_DAY)
        if (banked == 0 && !done) {
            store.setOfflineSittingMark(wallet, null)
            return null
        }
        // An unknown boot (-1) matches nothing: two unknown boots may differ.
        if (boot < 0 || mark.boot != boot || mark.elapsedMillis > elapsedMillis) return null

        val startedAt = chainNow - (elapsedMillis - mark.elapsedMillis) / 1000
        val day = HoldoutDay.indexOf(startedAt)
        val total = (store.bankedReps(wallet, scope, day) + banked).coerceAtMost(target)
        store.setBankedReps(wallet, scope, day, total)
        if (done || total >= target) {
            store.markDayComplete(wallet, scope, day)
            // Never walk the streak back: a later day already on it stays.
            val last = store.lastStreakDay(wallet)
            if (last == Store.NO_DAY || last < day) streaks.recordCompletedDay(wallet, day)
        }
        store.clearDay(wallet, scope, Store.UNKNOWN_DAY)
        store.setOfflineSittingMark(wallet, null)
        return day
    }
}
