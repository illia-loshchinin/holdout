package holdout.app.shell

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.core.CohortStatus
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
     * How often the app reads the roster of the Holdout Standing is about: every
     * 30 s while it runs, but a finished one has no days left to be missed (the
     * roster closed at the start), so a slow look is enough to keep its late
     * changes — claims, records — in view.
     */
    fun rosterEveryMillis(h: Holdout, now: Long): Long = when (h.status(now)) {
        CohortStatus.Settling, CohortStatus.Settled -> CHAIN_REFRESH_MILLIS
        else -> ROSTER_REFRESH_MILLIS
    }

    /**
     * The running Holdouts whose rosters the Holdouts root reads itself (its
     * running card and the Running now rows): all of them but the one Standing
     * is about, which the app's own loop reads already (R8).
     */
    fun watchedOnHoldouts(view: ChainView?, now: Long?): List<SolanaPublicKey> {
        if (view == null || now == null) return emptyList()
        val standing = standing(view, now)?.address
        return view.holdouts.filter { it.status(now) == CohortStatus.Running && it.address != standing }.map { it.address }
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
