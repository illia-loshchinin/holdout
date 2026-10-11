package holdout.app.home

import com.solana.publickey.SolanaPublicKey
import holdout.app.ui.Format
import holdout.core.Comeback
import holdout.core.Derive

/**
 * The comeback nudge on Today (H35). The Daily 25 pays nothing; what Today shows
 * is the member's own money in the next Holdout:
 *
 * - a stake lost, with a Holdout of the same level and length inside the
 *   14-day comeback window still to join or already held (FR-037g): before
 *   joining, the round, when it starts and how long joining stays open; with a
 *   place held, that finishing it brings back up to N of the lost stake; with the next round not opened yet, when it opens. "Up
 *   to", because the refund is capped at the new stake and scaled to the
 *   reserve of that level and length (FR-037e). It is read from the forfeited
 *   membership, so it shows before the forfeit is recorded or settled;
 * Nothing else: the next Holdout to join is an offer card on Today, not a line (board 3).
 *
 * Never an amount presented as certain (FR-039, constitution XIII), and never
 * the word "league" (FR-041).
 */
object DailyHoldoutLine {
    /** One comeback nudge on Today: its words, and the round it opens the stake sheet on (null: the Holdouts tab). */
    data class Nudge(val text: String, val joinAddress: SolanaPublicKey? = null, val tier: Int = 0)

    /**
     * The nudge of one offer, by priority: a round open to join first (the soonest start), then a place
     * already held, then a round not opened yet. The member can act on the first; the others only wait.
     */
    fun comebackLine(offers: List<Comeback.Offer>, now: Long): String? = nudge(offers, now)?.text

    /** [comebackLine] with where a tap goes (board 3, comeback card): the round to join, else the Holdouts tab. */
    fun nudge(offers: List<Comeback.Offer>, now: Long): Nudge? {
        val toJoin = offers.filter { it.held == null && it.next != null }.minByOrNull { it.next!!.start }
        val held = offers.filter { it.held != null }.minByOrNull { it.held!!.start }
        val waiting = offers.filter { it.held == null && it.next == null && it.upcoming != null }.minByOrNull { it.upcoming!!.start }
        if (toJoin == null) {
            held?.let { offer ->
                val place = offer.held?.entry ?: return null
                return Nudge("You're in the ${Format.level(place.cohort)}. Finish it and up to ${Format.skr(offer.amount(place.member?.stake))} of your lost stake comes back.")
            }
            val offer = waiting ?: return null
            val up = offer.upcoming ?: return null
            return Nudge("The next ${Format.level(offer.forfeited.cohort)} ${Format.opensForJoining(up.opensAt, now)} and starts in ${Format.countdown(up.start - now)}. Finish it and up to ${Format.skr(offer.amount())} of your lost stake comes back.")
        }
        val round = toJoin.next ?: return null
        val entry = round.entry ?: return null
        val c = entry.cohort
        val text = Format.comebackToday(toJoin.amount(), c, round.start - now) + " Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}."
        return Nudge(text, entry.address, toJoin.tier)
    }
}
