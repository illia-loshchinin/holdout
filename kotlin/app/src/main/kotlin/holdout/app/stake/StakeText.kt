package holdout.app.stake

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.Derive
import holdout.core.Journey

/**
 * The words of the stake confirm (board 4 · 4c; FR-041, FR-059, FR-068a,
 * ES-12), in one place so they are tested without a screen. The sentences that
 * several screens share — the level, its rule, the fee split — come from
 * [Format], never typed again.
 */
object StakeText {
    /** Before every join, word for word (design book, Always, word for word). */
    const val NO_DROPOUT = "If nobody drops out, you get exactly your stake back."

    const val FULL_REFUND = "Full refund until joining closes."

    /** The reason beside the stake button for the notification ask (FR-068a); shown only while notifications are off (ruling G9). */
    const val NOTIFICATION_REASON = "Allow notifications and this phone reminds you before each deadline. Reminders never leave the phone."

    const val FOOTER = "Test network, test token. Dollar values are approximate."

    /** The word a short test Holdout goes by in a sentence (spec 001 FR-044a; the badge keeps TEST COHORT). */
    const val TEST_NAME = "Test Holdout"

    /** The last minutes before the cutoff (ES-12): a transaction signed now may land too late. */
    const val CLOSING_WINDOW_SECONDS = 600L

    /** `Join the Spark · 7-day Holdout`; `Join the Test Holdout`. */
    fun title(c: CohortSnapshot, test: Boolean): String = if (test) "Join the $TEST_NAME" else "Join the ${Format.holdoutTitle(c)}"

    /** `Light it up: 25 push-ups a day for 7 days.` / `Make it stick: …`; a test Holdout says what it is. */
    fun levelLine(c: CohortSnapshot, test: Boolean): String {
        if (test) return "${c.repsPerDay} push-ups a day for ${c.durationDays} days. Joinable right up to its start."
        val lead = Format.levelTagline(c)?.substringBefore(" · ")
        val plain = "${c.repsPerDay} push-ups a day for ${c.durationDays} days."
        return if (lead == null) plain else "$lead: $plain"
    }

    /** The fee sentence with the Holdout's own name (ruling G5); a test Holdout names itself, not a level. */
    fun fee(c: CohortSnapshot, test: Boolean): String = Format.lostStakeSplit(c, name = if (test) "short test Holdout" else Format.level(c))

    /** `Joining closes in 8m 30s. A transaction signed now may land too late.`, or null before the last minutes (ES-12). */
    fun closingWarning(secondsToClose: Long): String? = if (secondsToClose in 1..CLOSING_WINDOW_SECONDS) {
        "Joining closes in ${Format.countdown(secondsToClose)}. A transaction signed now may land too late."
    } else {
        null
    }

    /** Why the stake button is off, when it is (FR-006a, FR-006b). */
    fun blocker(blocker: Journey.JoinBlocker): String = when (blocker) {
        Journey.JoinBlocker.JoiningClosed -> "Joining has closed for this Holdout."
        Journey.JoinBlocker.LobbyPlaceTaken -> "You already hold a place in a Holdout that hasn't started."
        Journey.JoinBlocker.Overlaps -> "This Holdout starts before your current one ends."
    }

    fun shortOfSkr(held: ULong, needed: ULong): String = "You hold ${Format.skr(held)}. This chip needs ${Format.skr(needed)}."

    const val SHORT_OF_SOL = "Your wallet has too little devnet SOL to pay for joining. The faucet sends some with the test tokens."

    /** `Finish it and up to 250 SKR of the stake you lost comes back.` for the chip chosen (FR-037g). */
    fun comeback(amount: ULong): String = "Finish it and up to ${Format.skr(amount)} of the stake you lost comes back."

    fun joiningCloses(c: CohortSnapshot, now: Long): String = Format.countdown(Derive.timeUntilJoiningCloses(c, now))
}
