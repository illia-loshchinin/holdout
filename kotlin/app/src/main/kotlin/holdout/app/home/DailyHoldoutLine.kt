package holdout.app.home

import com.solana.publickey.SolanaPublicKey
import holdout.app.ui.Format
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.Derive

/**
 * The comeback nudge on Today (H35). The Daily 25 pays nothing; what Today shows
 * is the member's own money in the next Holdout:
 *
 * - a stake lost, with the next Holdout of the same level and length (or the one
 *   after a last-day miss) still to join or already held (FR-037g): before
 *   joining, the round, when it starts and how long joining stays open (to the
 *   end of its first day for a round already started); with a place held, that
 *   finishing it brings back N of the lost stake; with the next round not
 *   opened yet, when it opens. N is exact and guaranteed: 10 % of the lost
 *   stake, never more than the new stake (FR-037e). It is read from the
 *   forfeited membership, so it shows before the forfeit is recorded or settled;
 * Nothing else: the next Holdout to join is an offer card on Today, not a line (board 3).
 *
 * Never the word "league" (FR-041).
 */
object DailyHoldoutLine {
    /**
     * One comeback nudge: its words, and the round it opens the stake sheet on (null: the Holdouts tab).
     * [held]: the member already holds a place in a round of the offer, so there is nothing left to do but wait for it.
     */
    data class Nudge(val text: String, val joinAddress: SolanaPublicKey? = null, val tier: Int = 0, val held: Boolean = false)

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
                return Nudge("You're in the ${Format.holdoutName(place.cohort)}. Finish it and ${Format.skr(offer.amount(place.member?.stake))} of your lost stake comes back.", held = true)
            }
            val offer = waiting ?: return null
            val up = offer.upcoming ?: return null
            return Nudge("The next ${Format.holdoutName(offer.forfeited.cohort)} ${Format.opensForJoining(up.opensAt, now)} and starts in ${Format.countdown(up.start - now)}. Finish it and ${Format.skr(offer.amount())} of your lost stake comes back.")
        }
        val round = toJoin.next ?: return null
        val entry = round.entry ?: return null
        val c = entry.cohort
        // A round already running is "that started today", and its joining closes at the end of its first day.
        val running = round.lateJoin && Derive.status(c, now) == CohortStatus.Running
        val closes = if (round.lateJoin) (round.closesAt - now).coerceAtLeast(0) else Derive.timeUntilJoiningCloses(c, now)
        val text = Format.comebackToday(toJoin.amount(), c, if (running) null else round.start - now) + " Joining closes in ${Format.countdown(closes)}."
        return Nudge(text, entry.address, toJoin.tier)
    }
}
