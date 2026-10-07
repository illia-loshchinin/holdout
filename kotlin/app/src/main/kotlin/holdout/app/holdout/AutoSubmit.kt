package holdout.app.holdout

import holdout.app.storage.AutoSubmitMark
import holdout.app.storage.Store

/**
 * When a completed cohort day waiting on this phone sends itself (FR-024b
 * Phase 2, T098): as soon as the chain answers again, once. Sending opens the
 * wallet to approve it, so a member who declines there is not asked again
 * and again — the day falls back to "Done — tap to submit". Losing the
 * connection again arms it once more: the next time the chain answers, the
 * wallet opens by itself.
 *
 * [sentFor] is the cohort and day already sent while the chain could be
 * reached — by itself, a tap or the sitting's own exit — or null. A send is
 * recorded when it starts ([sent]), so a day that has just landed is not sent
 * again before the chain shows it. [lostSince] is set when the system reported
 * the network gone after that: the send re-arms only when the chain answers
 * again ([answered]), never into no network.
 *
 * Both are kept in the store ([saveTo], [restore]) while the day sent is still
 * pending, so the app killed and started again does not open the wallet again
 * for a day the member declined — the restart is no lost connection.
 */
data class AutoSubmit(val sentFor: Pair<String, Int>? = null, val lostSince: Boolean = false) {

    /** What to do now: the state to keep, and whether to send [pendingDay] of [cohort]. */
    data class Step(val next: AutoSubmit, val send: Boolean)

    fun step(cohort: String, pendingDay: Int?, todayDone: Boolean, offline: Boolean, busy: Boolean): Step = when {
        offline -> Step(AutoSubmit(), send = false)
        pendingDay == null || todayDone || busy -> Step(this, send = false)
        sentFor == cohort to pendingDay -> Step(this, send = false)
        else -> Step(copy(sentFor = cohort to pendingDay), send = true)
    }

    /**
     * The phone lost its network. Arms the send again once the chain answers
     * ([answered]) — a drop and return between two chain reads is never seen
     * as offline there, so the system's own network callback reports it. Not
     * at once: the last read may still say online, and a send now would fail
     * for want of a network and count as tried.
     */
    fun lost(): AutoSubmit = copy(lostSince = true)

    /** The chain answered: if the network was lost since, the day sent before may go again. */
    fun answered(): AutoSubmit = if (lostSince) AutoSubmit() else this

    /** [day] of [cohort] was sent — by itself, a tap or the sitting's own exit. A loss not yet answered still re-arms it. */
    fun sent(cohort: String, day: Int): AutoSubmit = copy(sentFor = cohort to day)

    /** Whether [day] of [cohort] was already sent since the chain last answered: the tap is the way left. */
    fun tried(cohort: String, day: Int): Boolean = sentFor == cohort to day

    /**
     * Keeps this for [wallet] in [store] — only while the day it names is still
     * pending there: one landed or past saving holds nothing back, and a re-arm
     * ([answered]) leaves nothing to keep.
     */
    fun saveTo(store: Store, wallet: String) {
        val kept = sentFor?.takeIf { (cohort, day) -> store.pendingCohortDay(wallet, cohort) == day }
        store.setAutoSubmitMark(wallet, kept?.let { (cohort, day) -> AutoSubmitMark(cohort, day, lostSince) })
    }

    companion object {
        /** What [saveTo] kept for [wallet], if its day is still the one pending; otherwise a fresh gate. */
        fun restore(store: Store, wallet: String): AutoSubmit {
            val mark = store.autoSubmitMark(wallet)?.takeIf { store.pendingCohortDay(wallet, it.cohort) == it.day } ?: return AutoSubmit()
            return AutoSubmit(mark.cohort to mark.day, mark.lost)
        }
    }
}
