package holdout.app.home

import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.holdouts.YourKind
import holdout.app.nav.NavEvent
import holdout.app.nav.Tab
import holdout.app.ui.Format
import holdout.core.Comeback.forScreenOf
import holdout.core.Derive
import holdout.core.Stage

/**
 * One Holdout open to join, as Today and the connect screen draw it (FR-053,
 * board 3): the level and its tagline, what is at stake and how many have
 * joined, how long joining stays open, and the prize boost the reserve can add
 * ("up to", never a promise). A Hackathon Holdout keeps its own name ("Hackathon · 3-day") and its
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
    /**
     * The compact card's meta line (FR-053, 2026-10-13): "14 joined · closes in 2d 3h", or for a Hackathon
     * Holdout "3 joined · starts in 12m 30s". Built from [joined] and [timing], which already say it.
     */
    val compactMeta: String
        get() = "${joined.removePrefix("at stake · ")} · ${timing.removePrefix("Joining ").replaceFirstChar { it.lowercase() }}"

    companion object {
        fun of(h: Holdout, now: Long): OfferCardState {
            val c = h.cohort
            val test = h.isTest
            return OfferCardState(
                cohort = h.address.base58(),
                title = if (test) Format.testName(c) else Format.level(c),
                tagline = if (test) Format.testTagline(c) else Format.levelTagline(c) ?: "${c.repsPerDay} push-ups a day",
                atStake = Format.skr(Derive.atStake(c)),
                joined = "at stake · ${c.memberCount} joined",
                timing = if (test) "Starts in ${Format.countdown(c.start - now)}" else "Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}",
                boost = boostLine(h.league?.boostReserve),
                test = test,
                testDayLength = c.dayLength,
            )
        }
    }
}

/**
 * A nudge row on Today: one line, one tap, to the screen that needs the member (screen map §3 rule 4).
 * [mono] is the part of [text] set in the mono face (an amount, a countdown); [meta] is a quiet second
 * piece before the chevron (FR-069a: the level · length beside a claim).
 */
data class NudgeRowState(
    val text: String,
    val go: NavEvent,
    val tone: NudgeTone = NudgeTone.Neutral,
    val mono: String? = null,
    val meta: String? = null,
)

enum class NudgeTone { Neutral, Success, Warning }

/** The comeback card on Today (FR-037g): green, the way back in one tap. */
data class ComebackCardState(val text: String, val go: NavEvent)

/**
 * While a Holdout runs, the offers it allows are one quiet row (FR-053, FR-069a): how many are open and the
 * smallest at-stake figure among them, in the secondary text colour, opening Holdouts.
 */
data class OffersRow(val open: Int, val from: String) {
    /** "2 open to join · from 9,000 SKR ›": [from] already carries its unit. */
    val line: String get() = "$open open to join · from $from ›"
}

/** What Today's "Holdouts open to join" block shows, in the order the chain gets read. */
sealed interface HoldoutsBlock {
    /** The chain has not answered yet. */
    data object Reading : HoldoutsBlock

    /** Nothing was ever read and the chain can't be reached: the Daily 25 still works (spec 005 FR-002). */
    data object Unreachable : HoldoutsBlock

    /** The program is not deployed or not initialised on this network. */
    data object NotLive : HoldoutsBlock

    /**
     * The member holds a place in a lobby, so they can join nothing more: no block at all, not the cards, the
     * Seeker line or "opens soon". The lobby line and the Daily 25 are all Today shows (owner, 12 Oct).
     */
    data object Hidden : HoldoutsBlock

    /** [cards] joinable now, or none: "The next Holdout opens soon." (ES-15). [stale] an earlier read is shown while the chain can't be reached. */
    data class Open(val cards: List<OfferCardState>, val stale: Boolean = false) : HoldoutsBlock
}

/** Today's chain-derived content, worked out from the chain view (pure; JVM-tested). */
object TodayModel {
    /** ES-15: no Holdout is open to join. */
    const val OPENS_SOON = "The next Holdout opens soon."

    /**
     * T2's offers: one quiet row for what the running Holdout still allows the wallet to join (the program's
     * own refusals left out, FR-006a, FR-006b), or null — nothing to join, no program, no chain read yet,
     * or a lobby place held, which leaves no offers at all.
     */
    fun offersRow(view: ChainView?, now: Long?): OffersRow? {
        if (view == null || now == null || view.config == null || view.holdsLobbyPlace(now)) return null
        val open = view.joinable(now)
        if (open.isEmpty()) return null
        return OffersRow(open.size, Format.skr(open.minOf { Derive.atStake(it.cohort) }))
    }

    /**
     * The Daily 25 hero's foot, one quiet link to Me: the freezes held, or how far the next one is
     * (every seven days in a row earn one), or what earns the first.
     */
    fun streakFoot(streak: Int, freezes: Int): String = when {
        freezes >= 2 -> "2 freezes held · cover two missed days"
        freezes == 1 -> "1 freeze held · covers one missed day"
        streak <= 0 -> "7 days in a row earn a streak freeze"
        else -> (7 - streak % 7).let { "$it more ${if (it == 1) "day" else "days"} to a streak freeze" }
    }

    fun holdouts(view: ChainView?, now: Long?, offline: Boolean): HoldoutsBlock = when {
        view == null || now == null -> if (offline) HoldoutsBlock.Unreachable else HoldoutsBlock.Reading
        view.config == null -> HoldoutsBlock.NotLive
        view.holdsLobbyPlace(now) -> HoldoutsBlock.Hidden
        else -> HoldoutsBlock.Open(view.joinable(now).sortedBy { it.cohort.start }.map { OfferCardState.of(it, now) }, stale = offline)
    }

    /**
     * The comeback card, or null: the round to join opens its stake sheet on the
     * tier of the lost stake; a round not opened yet opens the Holdouts tab
     * (FR-037g). A place already held has no card: its lobby line says it all
     * (owner, 12 Oct walk). Nor has a round the program would refuse (a lobby
     * place held in another Holdout, a running one that ends after it starts):
     * no card for a Join that cannot go through (owner, 12 Oct).
     */
    fun comeback(view: ChainView?, now: Long?, seekerKnown: Boolean): ComebackCardState? {
        if (view == null || now == null || view.config == null) return null
        val nudge = DailyHoldoutLine.nudge(view.comebackOffers(now), now)?.takeUnless { it.held } ?: return null
        val round = nudge.joinAddress?.let { a -> view.holdouts.firstOrNull { it.address == a } }
        if (round != null && view.joinBlocker(round, now) != null) return null
        val go = nudge.joinAddress?.let { NavEvent.OpenStake(it.base58(), nudge.tier, seekerKnown) } ?: NavEvent.GoToRoot(Tab.Holdouts)
        return ComebackCardState(nudge.text, go)
    }

    /**
     * One line per Holdout of the member's that needs a tap — a lobby, a way
     * back after a forfeit, settling, a claim — each opening its screen. The
     * running one is the screen itself, so it has no line. [exceptCohort] leaves
     * out the Holdout on show. A Holdout the member is out of has no line once
     * they hold a place in its comeback round: what waits for them is that
     * round's lobby, not the loss (owner, 12 Oct walk).
     */
    fun nudges(view: ChainView?, now: Long?, exceptCohort: String? = null): List<NudgeRowState> {
        if (view == null || now == null || view.config == null) return emptyList()
        return MyHoldoutsModel.yours(view, now).filter {
            it.cohort != exceptCohort && it.kind != YourKind.Running && !(it.kind == YourKind.Out && comebackHeld(view, now, it.cohort))
        }.map { y ->
            when (y.kind) {
                YourKind.Claim -> claimAmount(view, now, y.cohort).let { amount -> NudgeRowState("Claim $amount", y.go, NudgeTone.Success, mono = amount, meta = y.name) }
                // y.detail is "starts in 2d 4h": the countdown after "starts in" is the mono part.
                YourKind.Lobby -> NudgeRowState("${y.name} ${y.detail}", y.go, mono = y.detail?.removePrefix("starts in "))
                YourKind.Out -> NudgeRowState("${y.name}: ${y.line}${y.detail?.let { " · $it" }.orEmpty()}", y.go, NudgeTone.Warning)
                YourKind.Running, YourKind.Locked, YourKind.Settling -> NudgeRowState("${y.name}: ${y.line}", y.go)
            }
        }
    }

    private fun comebackHeld(view: ChainView, now: Long, cohort: String): Boolean {
        val h = view.holdouts.firstOrNull { it.address.base58() == cohort } ?: return false
        return view.comebackOffer(now, h.cohort.leagueId).forScreenOf(h.address)?.held != null
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
