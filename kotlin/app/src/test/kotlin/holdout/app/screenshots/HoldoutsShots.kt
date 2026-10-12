package holdout.app.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import holdout.app.chain.Holdout
import holdout.app.claim.ClaimScreen
import holdout.app.cohort.CohortDetailScreen
import holdout.app.cohort.ForfeitScreen
import holdout.app.holdouts.FinishedHoldout
import holdout.app.holdouts.MyHoldoutsScreen
import holdout.app.holdouts.MyHoldoutsState
import holdout.app.holdouts.OpenHoldout
import holdout.app.holdouts.RunningHoldout
import holdout.app.holdouts.YourHoldout
import holdout.app.holdouts.YourKind
import holdout.app.leaderboard.LeaderboardScreen
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.roster.RosterScreen
import holdout.app.standing.StandingEmptyScreen
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.ChipTone
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Board
import holdout.core.MembershipSnapshot
import holdout.core.ScriptedWallets
import holdout.core.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Holdouts and Standing tabs and what hangs off them: H0, Standing's empty state, H4, H5, L1, H6 and H7. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class HoldoutsShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // ---- H0 My Holdouts --------------------------------------------------------------------------------

    private val running = YourHoldout("r", "Habit · 21-day", YourKind.Running, ChipTone.Success, "Day 12 of 21 · 189 still standing", "15 push-ups to go today", false, NavEvent.Open(Screen.Detail("r")))
    private val lobby = YourHoldout("l", "Spark · 7-day", YourKind.Lobby, ChipTone.Accent, "Joined · 1,250 SKR staked", "starts in 2d 4h", false, NavEvent.Open(Screen.Lobby("l")))
    private val locked = YourHoldout("k", "Spark · 7-day", YourKind.Locked, ChipTone.Neutral, "Joining closed · 14 in · 1,250 SKR staked", "starts in 41m 10s", false, NavEvent.Open(Screen.Lobby("k")))
    private val out = YourHoldout("o", "Habit · 21-day", YourKind.Out, ChipTone.Warning, "Out after 6 days", "Win back up to 250 SKR", false, NavEvent.Open(Screen.Forfeit("o")), detailTone = ChipTone.Success)
    private val settling = YourHoldout("s", "Spark · 7-day", YourKind.Settling, ChipTone.Success, "You finished · settling, usually within 11m", null, false, NavEvent.Open(Screen.Detail("s")))
    private val claim = YourHoldout("c", "Spark · 7-day", YourKind.Claim, ChipTone.Success, "Held out 7 of 7", "1,412 SKR waiting for you", false, NavEvent.Open(Screen.Claim("c")))
    private val blocked = OpenHoldout("b", "Habit · 21-day", "9,000 SKR", "at stake · 9 joined", "Prize boost: up to 1,200 SKR", "Joining closes in 2d 3h", "You already hold a place in a Holdout that hasn't started.", false)
    private val free = OpenHoldout("f", "Spark · 7-day", "12,500 SKR", "at stake · 14 joined", "Prize boost: up to 480 SKR", "Joining closes in 2d 3h", null, false)
    private val watch = listOf(
        RunningHoldout("a", "Habit · 21-day · day 12 of 21", "189 still standing of 312 · prize pool 48,200 SKR", false),
        RunningHoldout("b", "Spark · 7-day · day 7 of 7", "13 still standing of 20 · prize pool 6,125 SKR", false),
    )

    private fun h0(name: String, state: MyHoldoutsState, badges: TabBadges = TabBadges()) = compose.shoot("holdouts", name, heightDp = 1500) {
        Tabbed(Tab.Holdouts, badges) { MyHoldoutsScreen(state, onEvent = {}, onJoin = {}) }
    }

    @Test
    fun h0Full() = h0(
        "h0-full",
        MyHoldoutsState(
            yours = listOf(running, lobby, claim),
            open = listOf(free, blocked),
            finished = listOf(FinishedHoldout("f", "Spark · 7-day · finished · claimed 1,180 SKR", NavEvent.Open(Screen.Detail("f")))),
            running = watch,
        ),
        TabBadges(holdouts = true),
    )

    @Test
    fun h0OutAndSettling() = h0("h0-out-settling", MyHoldoutsState(yours = listOf(out, settling, locked), open = listOf(free)))

    @Test
    fun h0Empty() = h0("h0-empty", MyHoldoutsState(open = listOf(free), running = watch))

    // ---- Standing ---------------------------------------------------------------------------------------

    @Test
    fun standingEmpty() = compose.shoot("standing", "standing-empty", heightDp = 1000) {
        Tabbed(Tab.Standing) { StandingEmptyScreen(watch, null, onEvent = {}) }
    }

    @Test
    fun standingEmptyLobby() = compose.shoot("standing", "standing-empty-lobby", heightDp = 1000) {
        Tabbed(Tab.Standing) { StandingEmptyScreen(watch, "Yours starts in 2d 4h" to "lobby-1", onEvent = {}) }
    }

    // ---- H4 detail -------------------------------------------------------------------------------------

    private val now = Fx.START + 4 * Fx.DAY + 600
    private val cohort = Fx.cohort(joined = 10, pool = 3_000_000_000uL, held = 12_500_000_000uL)
    private val mine = Fx.holdout(cohort, Fx.member(days = 4), boostReserve = 480_000_000uL)
    private val roster = Fx.roster(standing = 7, out = 3)

    @Test
    fun detailRunning() = compose.shoot("standing", "detail-running", heightDp = 1500) {
        Tabbed(Tab.Standing) { CohortDetailScreen(mine, roster, mine.stage(now), now, null, {}, null, onLeaderboards = {}) }
    }

    @Test
    fun detailRunningPushed() = compose.shoot("standing", "detail-running-pushed", heightDp = 1500) {
        CohortDetailScreen(mine, roster, mine.stage(now), now, null, {}, {}, onLeaderboards = {})
    }

    private val finishedCohort = Fx.cohort(joined = 10, start = Fx.START - 7 * Fx.DAY)
    private val finishedH = Fx.holdout(finishedCohort, Fx.member(days = 7, state = MembershipSnapshot.State.Finished))

    @Test
    fun detailSettling() = compose.shoot("standing", "detail-settling", heightDp = 1300) {
        val at = finishedCohort.start + 7 * Fx.DAY + 240
        CohortDetailScreen(finishedH, Fx.roster(3, 0, finished = true), finishedH.stage(at), at, null, {}, {}, onLeaderboards = {})
    }

    @Test
    fun detailClaimed() = compose.shoot("standing", "detail-claimed", heightDp = 1300) {
        val done = Fx.cohort(joined = 20, start = Fx.START - 9 * Fx.DAY, settled = true, finishers = 11, share = 162_000_000uL)
        val stage = Stage.Claimed(amount = 1_662_000_000uL, stake = 1_250_000_000uL, share = 162_000_000uL, comeback = 250_000_000uL)
        val h = Fx.holdout(done, Fx.member(days = 7, state = MembershipSnapshot.State.Claimed))
        CohortDetailScreen(h, Fx.roster(11, 9, finished = true), stage, Fx.START + 10 * Fx.DAY, "4Bx9pQ7Qk2abcdef7Qk2", {}, {}, onLeaderboards = {})
    }

    // ---- H5 roster, L1 leaderboards ---------------------------------------------------------------------

    private val c21 = Fx.cohort(days = 21, joined = 6)
    private val at21 = c21.start + 11 * Fx.DAY + 600
    private val h21 = Fx.holdout(c21, Fx.member(days = 11))
    private val scripted = ScriptedWallets.keys.firstOrNull()

    @Test
    fun roster() = compose.shoot("standing", "roster", heightDp = 1100) {
        val members = listOf(
            Fx.member(Fx.me, days = 11),
            Fx.member(Fx.key(1), days = 11),
            Fx.member(scripted ?: Fx.key(2), days = 11),
            Fx.member(Fx.key(100), days = 9, state = MembershipSnapshot.State.Forfeited),
            Fx.member(Fx.key(101), days = 3, state = MembershipSnapshot.State.Forfeited),
            Fx.member(Fx.key(102), days = 6, state = MembershipSnapshot.State.Forfeited),
        )
        RosterScreen(h21, members, Fx.me.base58(), at21, {})
    }

    private fun row(n: Int, cleared: Int, forfeited: Int, scripted: Boolean = false) = Leaderboard.Row(Fx.key(n), cleared, forfeited, 1, cleared, 0, scripted)

    private val ranked = Leaderboard.rank(listOf(row(1, 5, 0), row(2, 4, 1), row(3, 3, 1, scripted = true), row(4, 1, 0)))

    @Test
    fun leaderboards() = compose.shoot("standing", "leaderboards", heightDp = 1100) {
        LeaderboardScreen(Board.EASY_7D, listOf(Board.EASY_7D, Board.HARD_21D, Board.TEST), ranked, Fx.key(2), {}, {}, testDayLengthSeconds = 600)
    }

    @Test
    fun leaderboardsEmpty() = compose.shoot("standing", "leaderboards-empty", heightDp = 844) {
        LeaderboardScreen(Board.HARD_21D, listOf(Board.EASY_7D, Board.HARD_21D), Leaderboard.rank(emptyList()), Fx.key(2), {}, {})
    }

    // ---- H6 forfeit, H7 claim ----------------------------------------------------------------------------

    private val cf = Fx.cohort(days = 21, joined = 20, pool = 4_820_000_000uL)
    private val nowF = cf.start + 11 * Fx.DAY + 3_600
    private val hf = Fx.holdout(cf, Fx.member(days = 11, state = MembershipSnapshot.State.Forfeited))
    private val nextCohort = Fx.cohort(start = nowF + 2 * Fx.DAY + 4 * 3_600, days = 21, joined = 9).copy(joinCutoff = nowF + 27 * 3_600)
    private val next = Comeback.Entry(Fx.holdout(nextCohort).address, nextCohort, null)
    private val round = Comeback.Round(start = nextCohort.start, opensAt = nextCohort.start - 7 * Fx.DAY, entry = next, status = CohortStatus.Open)
    private val offer = Comeback.Offer(
        slot = 1,
        lost = 1_250_000_000uL,
        tier = 1,
        missed = cf.start + 12 * Fx.DAY - 1,
        until = cf.start + 26 * Fx.DAY,
        forfeited = Comeback.Entry(hf.address, cf, hf.membership),
        rounds = listOf(round),
        held = null,
        upcoming = null,
    )

    private fun forfeit(name: String, offer: Comeback.Offer?, h: Holdout = hf) = compose.shoot("holdouts", name, heightDp = 1500) {
        ForfeitScreen(
            h = h,
            stage = Stage.Forfeited(daysLasted = 11, recorded = true),
            streak = 12,
            onDaily25 = {},
            onNextHoldout = {},
            onBack = {},
            offer = offer,
            now = nowF,
            onJoin = {},
            members = Fx.roster(standing = 19, out = 1, lasted = 11, going = 11),
        )
    }

    @Test
    fun forfeitWithOffer() = forfeit("forfeit-offer", offer)

    @Test
    fun forfeitNoOffer() = forfeit("forfeit-no-offer", null)

    private val spark = Fx.cohort(days = 7, joined = 20, start = Fx.START - 8 * Fx.DAY, settled = true, finishers = 11, share = 162_000_000uL)

    private fun claimShot(name: String, c: CohortSnapshot = spark, shortOfSol: Boolean = false) = compose.shoot("holdouts", name, heightDp = 1300) {
        val ready = Stage.ReadyToClaim(amount = 1_412_000_000uL, stake = 1_250_000_000uL, share = 162_000_000uL)
        val h = Holdout(Fx.holdout(c).address, c, null, Fx.member(days = c.durationDays, state = MembershipSnapshot.State.Finished))
        ClaimScreen(h, ready, busy = false, message = null, messageIsWarning = true, lastClaimSignature = null, onClaim = {}, onBack = {}, shortOfSol = shortOfSol, onGetTokens = {})
    }

    @Test
    fun claimSpark() = claimShot("claim-spark")

    @Test
    fun claimShortOfSol() = claimShot("claim-short-of-sol", shortOfSol = true)

    @Test
    fun claimHabit() = claimShot("claim-habit", Fx.cohort(days = 21, joined = 12, start = Fx.START - 22 * Fx.DAY, settled = true, finishers = 12, share = 0uL))
}
