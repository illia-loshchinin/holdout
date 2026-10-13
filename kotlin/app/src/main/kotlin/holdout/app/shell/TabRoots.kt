package holdout.app.shell

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.core.Stage

/**
 * Which Holdout Today is about, and which have their last day on chain
 * (FR-069a, rulings G1, D-D9). A Holdout's screen is not a tab root since
 * 13 October (FR-069b). Pure, so a JVM test pins each case.
 */
internal object TabRoots {
    /** The member's Holdout running today with them standing in it, if any: Today is T2 exactly then. */
    fun active(view: ChainView?, now: Long?): Pair<Holdout, Stage.Active>? {
        if (view == null || now == null) return null
        return view.mine(now).firstNotNullOfOrNull { h -> (h.stage(now) as? Stage.Active)?.let { h to it } }
    }

    /**
     * The Holdouts (base58) whose last day the member has sent: running, standing in them, today's day done on
     * chain and today the final one — "That was the last day" (FR-069a). The app raises
     * [holdout.app.nav.NavEvent.LastDayOnChain] for each, which opens that Holdout's screen over Today once.
     */
    fun lastDayOnChain(view: ChainView?, now: Long?): List<String> {
        if (view == null || now == null) return emptyList()
        return view.mine(now)
            .filter { h -> (h.stage(now) as? Stage.Active)?.let { it.todayDone && it.dayIndex + 1 >= h.cohort.durationDays } == true }
            .map { it.address.base58() }
    }

    /**
     * The Holdouts the member claimed, whose rosters the Holdouts root reads
     * itself for its Past tab's "12 of 14 finished": a claimed Holdout is over,
     * so a slow look is enough (R8).
     */
    fun claimedOnHoldouts(view: ChainView?, now: Long?): List<SolanaPublicKey> {
        if (view == null || now == null) return emptyList()
        return view.holdouts.filter { it.stage(now) is Stage.Claimed }.map { it.address }
    }
}
