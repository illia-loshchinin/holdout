package holdout.app.home

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.ProfileSnapshot
import holdout.core.Settlement

/**
 * The Daily 25's way into a Holdout (H35). The Daily 25 pays nothing; what it
 * shows, once done, is the member's own money in the next Holdout:
 *
 * - a stake lost in a league whose comeback window covers the next Holdout of
 *   that league: what comes back by finishing it — "up to", because the refund
 *   is capped at the new stake and scaled to the league's reserve (FR-037e);
 * - otherwise the next Holdout to join, when it starts and how many have joined.
 *
 * Never an amount presented as certain (FR-039, constitution XIII).
 */
object DailyHoldoutLine {
    fun of(openToJoin: List<CohortSnapshot>, profile: ProfileSnapshot?, now: Long): String? {
        val upcoming = openToJoin.sortedBy { it.start }
        val comeback = profile?.let { p ->
            upcoming.firstNotNullOfOrNull { c ->
                p.progress(c.leagueId).takeIf { it.comebackOpenFor(c.start) }?.let { c to it.comebackStake }
            }
        }
        if (comeback != null) {
            val (c, lost) = comeback
            val back = Settlement.comebackOwed(lost, ULong.MAX_VALUE)
            return "Finish the ${Format.holdoutTitle(c)} starting in ${Format.countdown(c.start - now)} and up to ${Format.skr(back)} of the stake you lost comes back."
        }
        val next = upcoming.firstOrNull() ?: return null
        return "Next: ${Format.holdoutTitle(next)}, starting in ${Format.countdown(next.start - now)} · ${next.memberCount} joined."
    }
}
