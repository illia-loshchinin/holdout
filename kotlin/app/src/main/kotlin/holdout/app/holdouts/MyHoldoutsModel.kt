package holdout.app.holdouts

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.cohort.SettlingWords
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.ui.ChipTone
import holdout.app.ui.Format
import holdout.core.CohortStatus
import holdout.core.Comeback.forScreenOf
import holdout.core.Derive
import holdout.core.Journey
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/** H0 My Holdouts, as the screen shows it: three lists, each row's words worked out, and where a tap goes. */
data class MyHoldoutsState(
    val yours: List<YourHoldout> = emptyList(),
    val open: List<OpenHoldout> = emptyList(),
    val finished: List<FinishedHoldout> = emptyList(),
    /** Running Holdouts the wallet is not in, to watch — every wallet, Seeker or not (FR-062a, ruling G14). */
    val running: List<RunningHoldout> = emptyList(),
) {
    /** Nothing of the member's own yet: the empty card shows, the offers still under it. */
    val empty: Boolean get() = yours.isEmpty() && finished.isEmpty()
}

/** One of the member's Holdouts still going: a status chip on the right, two lines, and where it opens. */
data class YourHoldout(
    val cohort: String,
    /** "Habit · 21-day": the level, bare on a card (DS Naming). */
    val name: String,
    /** The chip's word: running, lobby, out, settling, claim. */
    val status: String,
    val tone: ChipTone,
    val line: String,
    /** The line in the state's colour — what to do, or when; null when there is none. */
    val detail: String?,
    val test: Boolean,
    val go: NavEvent,
    val testDayLength: Long = 0,
)

/** A Holdout open to join, with Join — greyed out with the reason when the member cannot (FR-006a, FR-006b). */
data class OpenHoldout(
    val cohort: String,
    val name: String,
    val atStake: String,
    val boost: String?,
    val closesIn: String,
    val blocker: String?,
    val test: Boolean,
    val testDayLength: Long = 0,
)

/** A Holdout the member is done with: one line, to its detail (claimed) or its forfeit. */
data class FinishedHoldout(val cohort: String, val line: String, val go: NavEvent)

/** A running Holdout to watch, read-only: its day and how many still stand; it opens H4 (then H5). */
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
        val yours = mutableListOf<YourHoldout>()
        val finished = mutableListOf<Pair<Long, FinishedHoldout>>()
        for (h in view.holdouts) {
            val m = h.membership ?: continue
            val c = h.cohort
            val id = h.address.base58()
            val name = Format.level(c)
            fun card(status: String, tone: ChipTone, line: String, detail: String?, go: NavEvent) = YourHoldout(id, name, status, tone, line, detail, h.isTest, go, c.dayLength)
            when (val stage = h.stage(now)) {
                Stage.NotJoined -> Unit
                Stage.Lobby, Stage.LobbyLocked -> yours += card(
                    "lobby",
                    ChipTone.Accent,
                    if (stage == Stage.LobbyLocked) "Joining closed · ${Format.skr(m.stake)} staked" else "Joined · ${Format.skr(m.stake)} staked",
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
                    yours += card("running", ChipTone.Success, "Day ${stage.dayIndex + 1} of ${c.durationDays} · $crowd", detail, NavEvent.SelectTab(Tab.Today))
                }
                is Stage.Forfeited -> {
                    val wayBack = view.comebackOffer(now, c.leagueId).forScreenOf(h.address) != null
                    val out = "out after ${stage.daysLasted} day${if (stage.daysLasted == 1) "" else "s"}"
                    // Kept under Yours while the Holdout runs or a way back is open; then it is finished (ruling D-D6).
                    if (wayBack || Derive.status(c, now) in GOING) {
                        yours += card("out", ChipTone.Warning, out.replaceFirstChar { it.uppercase() }, if (wayBack) "A way back is open" else null, NavEvent.Open(Screen.Forfeit(id)))
                    } else {
                        finished += c.start to FinishedHoldout(id, "$name · $out", NavEvent.Open(Screen.Forfeit(id)))
                    }
                }
                Stage.Settling -> yours += card("settling", ChipTone.Success, SettlingWords.homeLine(Derive.settleUsuallyIn(c, now)), null, NavEvent.Open(Screen.Detail(id)))
                is Stage.ReadyToClaim -> yours += card(
                    "claim",
                    ChipTone.Success,
                    "Held out ${m.daysCompleted.coerceAtMost(c.durationDays)} of ${c.durationDays}",
                    "${Format.skr(stage.amount)} waiting for you",
                    NavEvent.Open(Screen.Claim(id)),
                )
                is Stage.Claimed -> finished += c.start to FinishedHoldout(id, "$name · claimed ${Format.skr(stage.amount)}", NavEvent.Open(Screen.Detail(id)))
            }
        }
        val open = view.openToJoin(now).map { h ->
            val c = h.cohort
            val blocker = Journey.joinBlocker(c, view.profile, view.runningEnd(now), now, runningInBreach = view.runningInBreach(now))
            OpenHoldout(
                cohort = h.address.base58(),
                name = Format.level(c),
                atStake = "${Format.skr(Derive.atStake(c))} at stake · ${c.memberCount} joined",
                boost = h.league?.boostReserve?.takeIf { it > 0uL }?.let(Format::boostLine),
                closesIn = "joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}",
                blocker = when (blocker) {
                    Journey.JoinBlocker.LobbyPlaceTaken -> "You already hold a place in one that hasn't started."
                    Journey.JoinBlocker.Overlaps -> "Starts before your current one ends."
                    Journey.JoinBlocker.JoiningClosed -> "Joining has closed."
                    null -> null
                },
                test = h.isTest,
                testDayLength = c.dayLength,
            )
        }
        val running = view.runningNotMine(now).map { h -> runningRow(h, now, rosters[h.address]) }
        return MyHoldoutsState(yours, open, finished.sortedByDescending { it.first }.map { it.second }, running)
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
        return RunningHoldout(h.address.base58(), "${Format.level(c)} · day $day of ${c.durationDays}", line, h.isTest, c.dayLength)
    }

    private val GOING = setOf(CohortStatus.Open, CohortStatus.Locked, CohortStatus.Running)

    /** The empty card's words (screen map H0; ES-12 holds in the worst case). */
    const val EMPTY_TITLE = "No Holdouts yet."
    const val EMPTY_TEXT = "Leave any time before joining closes, for a full refund."

    /** Staking is Seeker-exclusive, said up front (ruling G12). */
    const val SEEKER_LINE = "Holdouts need a Seeker."
}
