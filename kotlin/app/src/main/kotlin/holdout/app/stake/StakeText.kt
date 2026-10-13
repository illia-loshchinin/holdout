package holdout.app.stake

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.Derive
import holdout.core.Journey

/**
 * The words of the stake confirm (board 4 · 4c; FR-041, FR-059, FR-068a,
 * ES-12), in one place so they are tested without a screen. The sentences that
 * several screens share — the level and its rule — come from
 * [Format], never typed again.
 */
object StakeText {
    /** Before every join, word for word (design book, Always, word for word). */
    const val NO_DROPOUT = "If nobody drops out, you get exactly your stake back."

    const val FULL_REFUND = "Full refund until joining closes."

    /** The one line on losing; where the money goes is the tour's card 4, one tap away (FR-041, 2026-10-13). */
    const val MISS_A_DAY = "Miss a day and you lose your stake."

    /** The action beside it: opens the tour at card 2 and comes back to the sheet. */
    const val HOW_IT_WORKS = "How it works"

    /** `Prize boost: up to 480 SKR` on its own line, only while the level's reserve holds more than 0 (FR-053); null for a Hackathon round (no judged league). */
    fun boost(reserve: ULong?): String? = reserve?.takeIf { it > 0uL }?.let { Format.boostLine(it) }

    /** The reason beside the stake button for the notification ask (FR-068a); shown only while notifications are off (ruling G9). */
    const val NOTIFICATION_REASON = "Allow notifications and this phone reminds you before each deadline. Reminders never leave the phone."

    const val FOOTER = "Test network, test token. Dollar values are approximate."

    /** The last minutes before the cutoff (ES-12): a transaction signed now may land too late. */
    const val CLOSING_WINDOW_SECONDS = 600L

    /** `Join the Spark · 7-day Holdout`; `Join the Hackathon · 3-day Holdout`. */
    fun title(c: CohortSnapshot, test: Boolean): String = if (test) "Join the ${Format.testName(c)} Holdout" else "Join the ${Format.holdoutTitle(c)}"

    /** `Light it up: 25 push-ups a day for 7 days.` / `Make it stick: …`; a test Holdout says what it is: `A quick taste: 5 push-ups a day for 3 days.` */
    fun levelLine(c: CohortSnapshot, test: Boolean): String {
        val plain = "${c.repsPerDay} push-ups a day for ${c.durationDays} days."
        if (test) return "A quick taste: $plain" + if (Format.joinableToStart(c)) " Joinable right up to its start." else ""
        val lead = Format.levelTagline(c)?.substringBefore(" · ")
        return if (lead == null) plain else "$lead: $plain"
    }

    /** `A transaction signed now may land too late.` in the last minutes before the cutoff, or null before them (ES-12); the countdown above it turns to the warning colour. */
    fun closingWarning(secondsToClose: Long): String? = if (secondsToClose in 1..CLOSING_WINDOW_SECONDS) {
        "A transaction signed now may land too late."
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

    /** `Finish it and 25 SKR of the stake you lost comes back.` for the chip chosen: exactly, `min(10 % of the lost stake, the chip)` (FR-037e, FR-037g). */
    fun comeback(amount: ULong): String = "Finish it and ${Format.skr(amount)} of the stake you lost comes back."

    fun joiningCloses(c: CohortSnapshot, now: Long): String = Format.countdown(Derive.timeUntilJoiningCloses(c, now))
}
