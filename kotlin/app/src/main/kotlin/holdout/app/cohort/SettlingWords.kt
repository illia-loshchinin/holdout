package holdout.app.cohort

import holdout.core.Derive

/**
 * What the settling card (H4) and the Home/H0 card say about when a finished
 * Holdout settles (FR-035a, contracts §5): a usual time counted from the end,
 * never a clock time (constitution VI) and never a promise (XIII). The figures
 * come from `Derive.sinceEnd` and `Derive.settleUsuallyIn`.
 */
object SettlingWords {
    const val CARD_TITLE = "You finished · settling"

    /** The Home/H0 card: *"You finished · settling, usually within 11m"*, then *"… any moment"*. */
    fun homeLine(usuallyIn: Long): String = if (usuallyIn > 0) "You finished · settling, usually within ${minutesUp(usuallyIn)}m" else "You finished · settling any moment"

    /** The settling card's sentence; past the usual [Derive.SETTLE_USUAL_SECONDS] it says so, and that nothing owed can change. */
    fun cardBody(sinceEnd: Long, usuallyIn: Long): String = if (usuallyIn > 0) {
        val ended = if (sinceEnd < 60) "Ended under a minute ago." else "Ended ${sinceEnd / 60}m ago."
        "Automation settles it on its next pass, usually within ${Derive.SETTLE_USUAL_SECONDS / 60} minutes of the end. $ended"
    } else {
        "Taking longer than usual. It settles on the next automation pass; nothing you're owed can change."
    }

    /**
     * The settling card as the board draws it (H4, FR-035a): the usual-time sentence on its own,
     * and under it the time since the end and what is left of the usual wait ([cardDetail]).
     * Together they say what [cardBody] says in one sentence.
     */
    fun cardLead(usuallyIn: Long): String = if (usuallyIn > 0) {
        "Automation settles it on its next pass, usually within ${Derive.SETTLE_USUAL_SECONDS / 60} minutes of the end."
    } else {
        "Taking longer than usual. It settles on the next automation pass; nothing you're owed can change."
    }

    /** `Ended 4m ago · 11m left of those 15`, then `Ended 19m ago` once the usual wait is over. */
    fun cardDetail(sinceEnd: Long, usuallyIn: Long): String {
        val ended = if (sinceEnd < 60) "Ended under a minute ago" else "Ended ${sinceEnd / 60}m ago"
        return if (usuallyIn > 0) "$ended · ${minutesUp(usuallyIn)}m left of those ${Derive.SETTLE_USUAL_SECONDS / 60}" else ended
    }

    // "Within 1m" for the last seconds, never "within 0m".
    private fun minutesUp(seconds: Long): Long = (seconds + 59) / 60
}
