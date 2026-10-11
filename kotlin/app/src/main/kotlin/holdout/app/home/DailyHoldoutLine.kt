package holdout.app.home

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.Comeback
import holdout.core.Derive

/**
 * The Daily 25's way into a Holdout (H35). The Daily 25 pays nothing; what it
 * shows, once done, is the member's own money in the next Holdout:
 *
 * - a stake lost, with a Holdout of the same level and length inside the
 *   14-day comeback window still to join or already held (FR-037g): before
 *   joining, the round, when it starts and how long joining stays open; with a
 *   place held, that finishing it brings back up to N of the lost stake; with the next round not opened yet, when it opens. "Up
 *   to", because the refund is capped at the new stake and scaled to the
 *   reserve of that level and length (FR-037e). It is read from the forfeited
 *   membership, so it shows before the forfeit is recorded or settled;
 * - otherwise the next Holdout to join, when it starts and how many have joined.
 *
 * Never an amount presented as certain (FR-039, constitution XIII), and never
 * the word "league" (FR-041).
 */
object DailyHoldoutLine {
    fun of(openToJoin: List<CohortSnapshot>, offers: List<Comeback.Offer>, now: Long): String? {
        comebackLine(offers, now)?.let { return it }
        val next = openToJoin.minByOrNull { it.start } ?: return null
        return "Next: ${Format.holdoutTitle(next)}, starting in ${Format.countdown(next.start - now)} · ${next.memberCount} joined."
    }

    /** The nudge of the offer whose round comes first: the place held, or the next round open to join. */
    fun comebackLine(offers: List<Comeback.Offer>, now: Long): String? {
        val soonest = offers.minByOrNull { (it.held ?: it.next ?: it.upcoming)?.start ?: Long.MAX_VALUE } ?: return null
        soonest.held?.let { held ->
            val place = held.entry ?: return null
            return "You're in the ${Format.holdoutTitle(place.cohort)}. Finish it and up to ${Format.skr(soonest.amount(place.member?.stake))} of the stake you lost comes back."
        }
        val round = soonest.next ?: return soonest.upcoming?.let { up ->
            "The next ${Format.holdoutTitle(soonest.forfeited.cohort)} ${Format.opensForJoining(up.opensAt, now)} and starts in ${Format.countdown(up.start - now)}. Finish it and up to ${Format.skr(soonest.amount())} of the stake you lost comes back."
        }
        val c = round.entry?.cohort ?: return null
        return "Finish the ${Format.holdoutTitle(c)} starting in ${Format.countdown(round.start - now)} and up to ${Format.skr(soonest.amount())} of the stake you lost comes back. " +
            "Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}."
    }
}
