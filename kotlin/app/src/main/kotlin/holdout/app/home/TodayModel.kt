package holdout.app.home

import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.nav.NavEvent
import holdout.app.nav.Tab
import holdout.app.ui.Format
import holdout.core.Derive
import holdout.core.Stage

/**
 * One Holdout open to join, as Today and the connect screen draw it (FR-053,
 * board 3): the level and its tagline, what is at stake and how many have
 * joined, how long joining stays open, and the prize boost the reserve can add
 * ("up to", never a promise). A short test Holdout keeps its own name and its
 * badge (FR-044a).
 */
data class OfferCardState(
    val cohort: String,
    val title: String,
    val tagline: String,
    /** `12,500 SKR`. */
    val atStake: String,
    /** `at stake · 14 joined`. */
    val joined: String,
    /** `Joining closes in 2d 3h`, or for a test Holdout `Starts in 12m 30s`. */
    val timing: String,
    val boost: String?,
    val test: Boolean,
    val testDayLength: Long,
) {
    companion object {
        fun of(h: Holdout, now: Long): OfferCardState {
            val c = h.cohort
            val test = h.isTest
            return OfferCardState(
                cohort = h.address.base58(),
                title = if (test) TEST_TITLE else Format.level(c),
                tagline = if (test) {
                    "${c.durationDays} days · ${c.repsPerDay} push-ups a day · joinable right up to its start"
                } else {
                    Format.levelTagline(c) ?: "${c.repsPerDay} push-ups a day"
                },
                atStake = Format.skr(Derive.atStake(c)),
                joined = "at stake · ${c.memberCount} joined",
                timing = if (test) "Starts in ${Format.countdown(c.start - now)}" else "Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}",
                boost = boostLine(h.league?.boostReserve),
                test = test,
                testDayLength = c.dayLength,
            )
        }

        /** A short test Holdout's own name (ruling G2: test cohorts keep their own label). */
        const val TEST_TITLE = "Test Holdout"
    }
}

/** A nudge row on Today: one line, one tap, to the screen that needs the member (screen map §3 rule 4). */
data class NudgeRowState(val text: String, val go: NavEvent, val tone: NudgeTone = NudgeTone.Neutral)

enum class NudgeTone { Neutral, Success, Warning }

/** The comeback card on Today (FR-037g): green, the way back in one tap. */
data class ComebackCardState(val text: String, val go: NavEvent)

/** What Today's "Holdouts open to join" block shows, in the order the chain gets read. */
sealed interface HoldoutsBlock {
    /** The chain has not answered yet. */
    data object Reading : HoldoutsBlock

    /** Nothing was ever read and the chain can't be reached: the Daily 25 still works (spec 005 FR-002). */
    data object Unreachable : HoldoutsBlock

    /** The program is not deployed or not initialised on this network. */
    data object NotLive : HoldoutsBlock

    /** [cards] open to join, or none: "The next Holdout opens soon." (ES-15). [stale] an earlier read is shown while the chain can't be reached. */
    data class Open(val cards: List<OfferCardState>, val stale: Boolean = false) : HoldoutsBlock
}

/** Today's chain-derived content, worked out from the chain view (pure; JVM-tested). */
object TodayModel {
    /** Staking is Seeker-exclusive, said up front beside the offers (ruling G12). */
    const val SEEKER_LINE = MyHoldoutsModel.SEEKER_LINE

    /** ES-15: no Holdout is open to join. */
    const val OPENS_SOON = "The next Holdout opens soon."
    const val OPENS_SOON_DETAIL = "The Daily 25 is open now."

    fun holdouts(view: ChainView?, now: Long?, offline: Boolean): HoldoutsBlock = when {
        view == null || now == null -> if (offline) HoldoutsBlock.Unreachable else HoldoutsBlock.Reading
        view.config == null -> HoldoutsBlock.NotLive
        else -> HoldoutsBlock.Open(view.openToJoin(now).sortedBy { it.cohort.start }.map { OfferCardState.of(it, now) }, stale = offline)
    }

    /**
     * The comeback card, or null: the round to join opens its stake sheet on the
     * tier of the lost stake; a place held or a round not opened yet opens the
     * Holdouts tab (FR-037g).
     */
    fun comeback(view: ChainView?, now: Long?, seekerKnown: Boolean): ComebackCardState? {
        if (view == null || now == null || view.config == null) return null
        val nudge = DailyHoldoutLine.nudge(view.comebackOffers(now), now) ?: return null
        val go = nudge.joinAddress?.let { NavEvent.OpenStake(it.base58(), nudge.tier, seekerKnown) } ?: NavEvent.SelectTab(Tab.Holdouts)
        return ComebackCardState(nudge.text, go)
    }

    /**
     * One line per Holdout of the member's that needs a tap — a lobby, a way
     * back after a forfeit, settling, a claim — each opening its screen. The
     * running one is the screen itself, so it has no line. [exceptCohort] leaves
     * out the Holdout on show.
     */
    fun nudges(view: ChainView?, now: Long?, exceptCohort: String? = null): List<NudgeRowState> {
        if (view == null || now == null || view.config == null) return emptyList()
        val yours = MyHoldoutsModel.build(view, now).yours
        return yours.filter { it.cohort != exceptCohort && it.status != "running" }.map { y ->
            when (y.status) {
                "claim" -> NudgeRowState("You finished · claim ${claimAmount(view, now, y.cohort)}", y.go, NudgeTone.Success)
                "lobby" -> NudgeRowState("${y.name} lobby · ${y.detail}", y.go)
                "out" -> NudgeRowState("${y.name}: ${y.line}${y.detail?.let { " · $it" }.orEmpty()}", y.go, NudgeTone.Warning)
                else -> NudgeRowState("${y.name}: ${y.line}", y.go)
            }
        }
    }

    private fun claimAmount(view: ChainView, now: Long, cohort: String): String {
        val stage = view.holdouts.firstOrNull { it.address.base58() == cohort }?.stage(now) as? Stage.ReadyToClaim
        return stage?.let { Format.skr(it.amount) } ?: "your stake and share"
    }

    /**
     * "Yesterday ended at 20 of 25. Today starts fresh." (ES-3, B-D6): only when
     * yesterday banked something and was not completed; a freeze that covered it
     * says so instead. Null otherwise, and once today has begun.
     */
    fun yesterdayNote(banked: Int, completed: Boolean, streakAlive: Boolean, target: Int, todayBanked: Int, todayDone: Boolean): String? {
        if (banked <= 0 || completed || todayDone || todayBanked > 0) return null
        val ended = "Yesterday ended at $banked of $target."
        return if (streakAlive) "$ended A freeze kept your streak." else "$ended Today starts fresh."
    }
}
