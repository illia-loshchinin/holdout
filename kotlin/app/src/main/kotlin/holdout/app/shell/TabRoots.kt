package holdout.app.shell

import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.core.Stage

/**
 * Which Holdout the Today and Standing roots are about (FR-069a, FR-069b,
 * rulings G1, D-D9). Pure, so a JVM test pins each case.
 */
internal object TabRoots {
    /** The member's Holdout running today with them standing in it, if any: Today is T2 exactly then. */
    fun active(view: ChainView?, now: Long?): Pair<Holdout, Stage.Active>? {
        if (view == null || now == null) return null
        return view.mine(now).firstNotNullOfOrNull { h -> (h.stage(now) as? Stage.Active)?.let { h to it } }
    }

    /**
     * The Holdout the Standing tab is about: the one running with the member
     * standing in it, else the last one they ran — started, whatever came of
     * it. A lobby place alone is not one: there is no crowd yet.
     */
    fun standing(view: ChainView?, now: Long?): Holdout? {
        if (view == null || now == null) return null
        active(view, now)?.let { return it.first }
        return view.holdouts
            .filter { it.membership != null && it.cohort.start <= now && it.stage(now) != Stage.NotJoined }
            .maxByOrNull { it.cohort.start }
    }
}
