package holdout.app.holdout

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
 * again before the chain shows it.
 */
data class AutoSubmit(val sentFor: Pair<String, Int>? = null) {

    /** What to do now: the state to keep, and whether to send [pendingDay] of [cohort]. */
    data class Step(val next: AutoSubmit, val send: Boolean)

    fun step(cohort: String, pendingDay: Int?, todayDone: Boolean, offline: Boolean, busy: Boolean): Step = when {
        offline -> Step(AutoSubmit(), send = false)
        pendingDay == null || todayDone || busy -> Step(this, send = false)
        sentFor == cohort to pendingDay -> Step(this, send = false)
        else -> Step(AutoSubmit(cohort to pendingDay), send = true)
    }

    /** [day] of [cohort] was sent — by itself, a tap or the sitting's own exit. */
    fun sent(cohort: String, day: Int): AutoSubmit = AutoSubmit(cohort to day)

    /** Whether [day] of [cohort] was already sent since the chain last answered: the tap is the way left. */
    fun tried(cohort: String, day: Int): Boolean = sentFor == cohort to day
}
