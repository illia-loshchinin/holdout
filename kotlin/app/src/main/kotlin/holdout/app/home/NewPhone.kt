package holdout.app.home

import holdout.app.chain.ChainView
import holdout.app.storage.Store
import holdout.core.HoldoutDay

/**
 * The new-phone banner's signal (screen map T1/T2, ES-3a, Gap 3): a member who
 * used Holdout on another phone opens this one and sees nothing banked today.
 * The banner says so, so the empty count is not mistaken for a lost day.
 *
 * It shows when all three hold:
 * - this phone first saw the wallet **today** (`seen:{wallet}`, kept when the
 *   wallet first connects here);
 * - the chain has the wallet in a Holdout that **started before today**, which
 *   it could only have joined and used elsewhere;
 * - **nothing is banked here today** — a block banked here ends it, and so
 *   does the next day.
 *
 * Nothing leaves the phone, and nothing is paid against it.
 */
object NewPhone {
    /** The banner's sentence; [target] is the Daily 25's (T1) or the Holdout's daily target (T2). */
    fun line(target: Int): String = "No banked reps on this device today. $target to go."

    /**
     * Keeps the day this phone first saw [wallet], once. A wallet the phone knew before the key
     * existed (its tour seen, or any streak or banked-rep record) counts as seen before, so an
     * update never shows the banner. With the day unknown (an offline first run) a new wallet is
     * kept as [Store.SEEN_PENDING] and the first call with a known day makes that its day: what
     * this phone holds of the wallet by then (a tour seen, reps banked offline) is its own use
     * since, not a sign it knew the wallet before, so it is not looked at again.
     */
    fun markSeen(store: Store, wallet: String, dayIndex: Long) {
        val seen = store.firstSeenDay(wallet)
        if (seen != null && seen != Store.SEEN_PENDING) return
        if (seen == Store.SEEN_PENDING) {
            if (dayIndex != Store.UNKNOWN_DAY) store.setFirstSeenDay(wallet, dayIndex)
        } else if (knownHere(store, wallet, dayIndex)) {
            store.setFirstSeenDay(wallet, Store.SEEN_EARLIER)
        } else {
            store.setFirstSeenDay(wallet, if (dayIndex != Store.UNKNOWN_DAY) dayIndex else Store.SEEN_PENDING)
        }
    }

    /** Whether the banner shows on [dayIndex] with [bankedToday] reps banked on this phone toward today's day. */
    fun shows(store: Store, wallet: String, dayIndex: Long, view: ChainView?, bankedToday: Int): Boolean {
        if (dayIndex == Store.UNKNOWN_DAY || bankedToday > 0 || store.firstSeenDay(wallet) != dayIndex) return false
        return view?.holdouts.orEmpty().any { it.membership != null && HoldoutDay.indexOf(it.cohort.start) < dayIndex }
    }

    /** The phone holds something of this wallet from before the first-seen key: a seen tour, a streak, or reps banked. */
    private fun knownHere(store: Store, wallet: String, dayIndex: Long): Boolean {
        if (store.hasSeenTour(wallet) || store.streak(wallet) > 0 || store.longestStreak(wallet) > 0 || store.lastStreakDay(wallet) != Store.NO_DAY) return true
        val days = if (dayIndex == Store.UNKNOWN_DAY) listOf(Store.UNKNOWN_DAY) else listOf(Store.UNKNOWN_DAY, dayIndex, dayIndex - 1)
        return days.any { store.bankedReps(wallet, Store.CHALLENGE, it) > 0 || store.isDayComplete(wallet, Store.CHALLENGE, it) }
    }
}
