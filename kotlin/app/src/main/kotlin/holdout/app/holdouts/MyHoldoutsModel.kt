package holdout.app.holdouts

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.cohort.SettlingWords
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.ui.ChipTone
import holdout.app.ui.Format
import holdout.core.CohortStatus
import holdout.core.Comeback.forScreenOf
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * H0 My Holdouts, as the screen shows it: the Now tab ([yours] still going, then [open] to join) and the
 * Past tab ([won], then [lost]), each row's words worked out, and where a tap goes (owner, 12 Oct).
 */
data class MyHoldoutsState(
    /** Still going, in the order of [MyHoldoutsModel.NOW_ORDER]: what needs the member first. */
    val yours: List<YourHoldout> = emptyList(),
    val open: List<OpenHoldout> = emptyList(),
    /** Past, claimed, newest first. */
    val won: List<PastHoldout> = emptyList(),
    /** Past, forfeited with no way back left, newest first. */
    val lost: List<PastHoldout> = emptyList(),
    /** The wallet waits in a lobby, so it can join nothing more: the "Open to join" section is not drawn at all (owner, 12 Oct). */
    val holdsLobbyPlace: Boolean = false,
) {
    /** Nothing of the member's own, now or past: the empty card shows with no switch, the offers still under it. */
    val empty: Boolean get() = yours.isEmpty() && won.isEmpty() && lost.isEmpty()

    /** The count on the Now side of the switch. */
    val nowCount: Int get() = yours.size

    /** The count on the Past side of the switch. */
    val pastCount: Int get() = won.size + lost.size
}

/** One of the member's Holdouts still going: a status chip on the right, two lines, and where it opens. */
data class YourHoldout(
    val cohort: String,
    /** "Habit · 21-day": the level, bare on a card (DS Naming). */
    val name: String,
    /** Which state this is: the chip's word, and what Today's nudges key on. */
    val kind: YourKind,
    val tone: ChipTone,
    val line: String,
    /** The line in the state's colour — what to do, or when; null when there is none. */
    val detail: String?,
    val test: Boolean,
    val go: NavEvent,
    val testDayLength: Long = 0,
    /** The detail line's colour when it is not the chip's: a forfeit's way back is good news beside a warning chip. */
    val detailTone: ChipTone = tone,
) {
    /** The chip's word: running, lobby, locked, out, settling, claim. */
    val status: String get() = kind.word
}

/** The state of one of the member's own Holdouts that is still going, with the word its chip carries. */
enum class YourKind(val word: String) {
    Running("running"),
    Lobby("lobby"),
    Locked("locked"),
    Out("out"),
    Settling("settling"),
    Claim("claim"),
}

/** A Holdout the member can join now, with Join: one the program would refuse is not listed (FR-006a, FR-006b). */
data class OpenHoldout(
    val cohort: String,
    val name: String,
    /** "9,000 SKR": the stakes taken so far, which the card sets in the amount face. */
    val amount: String,
    /** "at stake · 9 joined": under the amount. */
    val note: String,
    val boost: String?,
    val closesIn: String,
    val test: Boolean,
    val testDayLength: Long = 0,
)

/**
 * A Holdout the member is done with, one row of the Past tab: the name, a line under it, the amount that
 * ended it (won: claimed; lost: the stake) and where it opens — its detail (won) or its forfeit (lost).
 */
data class PastHoldout(
    val cohort: String,
    val name: String,
    /** "Held out 7 of 7 · 12 of 14 finished", or "Out on day 5 of 21". */
    val line: String,
    /** "1,180 SKR": claimed on a won row, the stake on a lost one. */
    val amount: String,
    val go: NavEvent,
)

/** A running Holdout to watch, read-only: its day and how many still stand; it opens H4 (then H5). Standing's empty state lists them. */
data class RunningHoldout(val cohort: String, val name: String, val line: String, val test: Boolean, val testDayLength: Long = 0) {
    val go: NavEvent get() = NavEvent.Open(Screen.Detail(cohort))
}

object MyHoldoutsModel {
    /**
     * H0 from the chain [view] at chain time [now]. [rosters] give a running
     * Holdout's standing count once read; [bankedToday] the reps this phone has
     * banked toward a running Holdout's day.
     */
    fun build(
        view: ChainView,
        now: Long,
        rosters: Map<SolanaPublicKey, List<MembershipSnapshot>> = emptyMap(),
        bankedToday: (Holdout, Int) -> Int = { _, _ -> 0 },
    ): MyHoldoutsState {
        val sorted = sorted(view, now, rosters, bankedToday)
        val holdsLobbyPlace = view.holdsLobbyPlace(now)
        return MyHoldoutsState(sorted.yours, if (holdsLobbyPlace) emptyList() else open(view, now), sorted.won, sorted.lost, holdsLobbyPlace)
    }

    /**
     * Only the member's own Holdouts still going (H0's Now tab), without the
     * open and past lists a caller such as Today's nudges would discard.
     */
    fun yours(
        view: ChainView,
        now: Long,
        rosters: Map<SolanaPublicKey, List<MembershipSnapshot>> = emptyMap(),
        bankedToday: (Holdout, Int) -> Int = { _, _ -> 0 },
    ): List<YourHoldout> = sorted(view, now, rosters, bankedToday).yours

    /** What [sorted] makes of the member's Holdouts. */
    private class Sorted(val yours: List<YourHoldout>, val won: List<PastHoldout>, val lost: List<PastHoldout>)

    /** The member's Holdouts split into those still going (by [NOW_ORDER]) and those past, won and lost, each newest first. */
    private fun sorted(
        view: ChainView,
        now: Long,
        rosters: Map<SolanaPublicKey, List<MembershipSnapshot>>,
        bankedToday: (Holdout, Int) -> Int,
    ): Sorted {
        val yours = mutableListOf<YourHoldout>()
        val won = mutableListOf<Pair<Long, PastHoldout>>()
        val lost = mutableListOf<Pair<Long, PastHoldout>>()
        for (h in view.holdouts) {
            val m = h.membership ?: continue
            val c = h.cohort
            val id = h.address.base58()
            // A Hackathon Holdout keeps its own name, "Hackathon · 3-day" (G2); the badge on its card says why.
            val name = if (h.isTest) Format.testName(c) else Format.level(c)
            fun card(kind: YourKind, tone: ChipTone, line: String, detail: String?, go: NavEvent) = YourHoldout(id, name, kind, tone, line, detail, h.isTest, go, c.dayLength)
            when (val stage = h.stage(now)) {
                Stage.NotJoined -> Unit
                Stage.Lobby, Stage.LobbyLocked -> yours += card(
                    if (stage == Stage.LobbyLocked) YourKind.Locked else YourKind.Lobby,
                    if (stage == Stage.LobbyLocked) ChipTone.Neutral else ChipTone.Accent,
                    if (stage == Stage.LobbyLocked) "Joining closed · ${c.memberCount} in · ${Format.skr(m.stake)} staked" else "Joined · ${Format.skr(m.stake)} staked",
                    "starts in ${Format.countdown(c.start - now)}",
                    NavEvent.Open(Screen.Lobby(id)),
                )
                is Stage.Active -> {
                    val standing = rosters[h.address]?.let { Derive.survivors(c, it, now).standing }
                    val crowd = standing?.let { "$it still standing" } ?: "${c.memberCount} started"
                    val owed = (c.repsPerDay - bankedToday(h, stage.dayIndex)).coerceAtLeast(0)
                    val detail = when {
                        stage.todayDone -> "Today is on chain"
                        owed == 0 -> "Today's reps are done"
                        else -> "$owed push-ups to go today"
                    }
                    // Its screen above H0, with the day and its Start (FR-069b), so Back returns here and never leaves the app (owner, 12 Oct walk).
                    yours += card(YourKind.Running, ChipTone.Success, "Day ${stage.dayIndex + 1} of ${c.durationDays} · $crowd", detail, NavEvent.Open(Screen.Detail(id)))
                }
                is Stage.Forfeited -> {
                    val offer = view.comebackOffer(now, c.leagueId).forScreenOf(h.address)
                    // The day missed is the one after the days held: "out on day 7" for six days lasted.
                    val missed = (stage.daysLasted + 1).coerceAtMost(c.durationDays)
                    // Kept under Now while the Holdout runs or a way back is open; then it is past, lost (ruling D-D6).
                    if (offer != null || Derive.status(c, now) in GOING) {
                        // A place held in the comeback round already is the win-back under way: nothing left to win back by tapping.
                        val winBack = offer?.let { if (it.held != null) COMEBACK_JOINED else Format.winBack(it.amount()) }
                        yours += card(YourKind.Out, ChipTone.Warning, "Lost ${Format.skr(m.stake)} · out on day $missed", winBack, NavEvent.Open(Screen.Forfeit(id)))
                            .copy(detailTone = ChipTone.Success)
                    } else {
                        lost += c.start to PastHoldout(id, name, "Out on day $missed of ${c.durationDays}", Format.skr(m.stake), NavEvent.Open(Screen.Forfeit(id)))
                    }
                }
                Stage.Settling -> yours += card(YourKind.Settling, ChipTone.Success, SettlingWords.homeLine(Derive.settleUsuallyIn(c, now)), null, NavEvent.Open(Screen.Detail(id)))
                is Stage.ReadyToClaim -> yours += card(
                    YourKind.Claim,
                    ChipTone.Success,
                    "Held out ${m.daysCompleted.coerceAtMost(c.durationDays)} of ${c.durationDays}",
                    "${Format.skr(stage.amount)} waiting for you",
                    NavEvent.Open(Screen.Claim(id)),
                )
                is Stage.Claimed -> {
                    // "12 of 14 finished" once the roster is read; the first half alone until then.
                    val finishers = rosters[h.address]?.let { Derive.survivors(c, it, now) }?.let { " · ${it.standing} of ${it.starters} finished" }.orEmpty()
                    val held = "Held out ${m.daysCompleted.coerceAtMost(c.durationDays)} of ${c.durationDays}$finishers"
                    won += c.start to PastHoldout(id, name, held, Format.skr(stage.amount), NavEvent.Open(Screen.Detail(id)))
                }
            }
        }
        return Sorted(
            yours.sortedBy { NOW_ORDER.indexOf(it.kind) },
            won.sortedByDescending { it.first }.map { it.second },
            lost.sortedByDescending { it.first }.map { it.second },
        )
    }

    /** Only what the program would take (FR-006a, FR-006b): a Join shown always goes through. */
    private fun open(view: ChainView, now: Long): List<OpenHoldout> = view.joinable(now).map { h ->
        val c = h.cohort
        OpenHoldout(
            cohort = h.address.base58(),
            name = if (h.isTest) Format.testName(c) else Format.level(c),
            amount = Format.skr(Derive.atStake(c)),
            note = "at stake · ${c.memberCount} joined",
            boost = h.league?.boostReserve?.takeIf { it > 0uL }?.let(Format::boostLine),
            closesIn = "Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}",
            test = h.isTest,
            testDayLength = c.dayLength,
        )
    }

    /**
     * "Spark · 7-day · day 3 of 7" over "9 still standing of 12 · prize pool 2,400 SKR":
     * a running Holdout watched from outside, its figures from the roster once read
     * (FR-062a), the account's own count until then.
     */
    fun runningRow(h: Holdout, now: Long, roster: List<MembershipSnapshot>?): RunningHoldout {
        val c = h.cohort
        val day = (Derive.dayIndex(c, now) + 1).coerceIn(1, c.durationDays)
        val figures = roster?.let { Derive.figures(c, it, now) }
        val line = figures?.let { "${it.survivors.standing} still standing of ${it.survivors.starters} · prize pool ${Format.skr(it.projectedPool)}" }
            ?: "${c.memberCount} started · prize pool ${Format.skr(c.pool)}"
        return RunningHoldout(h.address.base58(), "${if (h.isTest) Format.testName(c) else Format.level(c)} · day $day of ${c.durationDays}", line, h.isTest, c.dayLength)
    }

    /** The Now tab's order, by what needs the member: a claim, a running day, a settling, the lobbies, then a way back. */
    val NOW_ORDER = listOf(YourKind.Claim, YourKind.Running, YourKind.Settling, YourKind.Lobby, YourKind.Locked, YourKind.Out)

    private val GOING = setOf(CohortStatus.Open, CohortStatus.Locked, CohortStatus.Running)

    /** The empty card's words (screen map H0; ES-12 holds in the worst case). */
    const val EMPTY_TITLE = "No Holdouts yet."
    const val EMPTY_TEXT = "Stake on yourself for 7 or 21 days. Full refund until joining closes."

    /** The detail of an Out row once the member holds a place in its comeback round (owner, 12 Oct). */
    const val COMEBACK_JOINED = "Comeback joined"

    /** Staking is Seeker-exclusive, said up front (ruling G12). */
    const val SEEKER_LINE = "Holdouts need a Seeker."

    /** In place of every Join once this session's Seeker check found no Genesis Token (ruling G12, PR #62 review). */
    const val SEEKER_ONLY = "Joining is for Seeker owners. You can still do the Daily 25 and watch every Holdout."
}
