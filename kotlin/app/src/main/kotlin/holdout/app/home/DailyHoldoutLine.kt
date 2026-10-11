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
    /** The line and whether it is the comeback nudge (which Home shows before the Daily 25 is done). */
    data class Line(val text: String, val comeback: Boolean)

    fun of(openToJoin: List<CohortSnapshot>, offers: List<Comeback.Offer>, now: Long): String? = line(openToJoin, offers, now)?.text

    fun line(openToJoin: List<CohortSnapshot>, offers: List<Comeback.Offer>, now: Long): Line? {
        comebackLine(offers, now)?.let { return Line(it, comeback = true) }
        val next = openToJoin.minByOrNull { it.start } ?: return null
        return Line("Next: ${Format.holdoutTitle(next)}, starting in ${Format.countdown(next.start - now)} · ${next.memberCount} joined.", comeback = false)
    }

    /**
     * The nudge of one offer, by priority: a round open to join first (the soonest start), then a place
     * already held, then a round not opened yet. The member can act on the first; the others only wait.
     */
    fun comebackLine(offers: List<Comeback.Offer>, now: Long): String? {
        val toJoin = offers.filter { it.held == null && it.next != null }.minByOrNull { it.next!!.start }
        val held = offers.filter { it.held != null }.minByOrNull { it.held!!.start }
        val waiting = offers.filter { it.held == null && it.next == null && it.upcoming != null }.minByOrNull { it.upcoming!!.start }
        if (toJoin == null) {
            held?.let { offer ->
                val place = offer.held?.entry ?: return null
                return "You're in the ${Format.holdoutTitle(place.cohort)}. Finish it and up to ${Format.skr(offer.amount(place.member?.stake))} of your lost stake comes back."
            }
            val offer = waiting ?: return null
            val up = offer.upcoming ?: return null
            return "The next ${Format.holdoutTitle(offer.forfeited.cohort)} ${Format.opensForJoining(up.opensAt, now)} and starts in ${Format.countdown(up.start - now)}. Finish it and up to ${Format.skr(offer.amount())} of your lost stake comes back."
        }
        val round = toJoin.next ?: return null
        val c = round.entry?.cohort ?: return null
        return Format.comebackToday(toJoin.amount(), c, round.start - now) + " Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}."
    }
}
