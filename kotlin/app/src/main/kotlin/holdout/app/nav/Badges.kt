package holdout.app.nav

import holdout.app.faucet.FaucetClient
import holdout.core.Stage

/**
 * The bar's dots (DS BottomNav, screen map §3): an 8 dp accent dot, never a
 * count, and its meaning also in the tab's accessible name. Each is worked out
 * here from plain values, so the shell only gathers them.
 */
object Badges {
    /**
     * Today: a dot while today's reps are not done — the running Holdout's day
     * when there is one (done on chain, or done on this phone and waiting to be
     * sent), else the Daily 25. A Holdout day is the day's training (FR-050).
     */
    fun today(running: Stage.Active?, holdoutDayDoneOnPhone: Boolean, daily25Done: Boolean): Boolean = if (running != null) !(running.todayDone || holdoutDayDoneOnPhone) else !daily25Done

    /**
     * Standing: how many dropped since the member last looked at this Holdout's
     * standing ([lastLooked], kept per Holdout on the phone) — or, never having
     * looked, since it started with [startedWith]. 0 is no dot; so is a roster
     * not read yet.
     */
    fun droppedSinceLooked(standingNow: Int?, lastLooked: Int?, startedWith: Int): Int {
        val now = standingNow ?: return 0
        return ((lastLooked ?: startedWith) - now).coerceAtLeast(0)
    }

    /** Holdouts: a dot when a claim is waiting. */
    fun holdouts(stages: Collection<Stage>): Boolean = stages.any { it is Stage.ReadyToClaim }

    /**
     * Me: a dot when SOL is too low to pay for a transaction — a day's submit
     * included — or SKR too low for the cheapest chip of a Holdout open to join
     * ([cheapestOpenChip], null when none is open).
     */
    fun me(skr: ULong, lamports: Long, cheapestOpenChip: ULong?): Boolean = !FaucetClient.canPayFees(lamports) || (cheapestOpenChip != null && skr < cheapestOpenChip)
}

/** The four dots at once, and each tab's accessible name. */
data class TabBadges(
    val today: Boolean = false,
    /** People dropped since the member last looked; 0 shows no dot. */
    val standingDropped: Int = 0,
    val holdouts: Boolean = false,
    val me: Boolean = false,
) {
    fun dot(tab: Tab): Boolean = when (tab) {
        Tab.Today -> today
        Tab.Standing -> standingDropped > 0
        Tab.Holdouts -> holdouts
        Tab.Me -> me
    }

    /** "Standing, 3 dropped since you last looked": the dot's meaning, said (DS BottomNav). */
    fun accessibleName(tab: Tab): String {
        if (!dot(tab)) return tab.label
        val meaning = when (tab) {
            Tab.Today -> "today's reps aren't done"
            Tab.Standing -> "$standingDropped dropped since you last looked"
            Tab.Holdouts -> "a claim is waiting"
            Tab.Me -> "low on SKR or SOL"
        }
        return "${tab.label}, $meaning"
    }
}
