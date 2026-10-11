package holdout.app.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.home.CohortHome
import holdout.app.home.HoldoutsBlock
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.home.OfferCardState
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.streak.ToesMeter
import holdout.app.testutil.AnimationsOff
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today, both roots: T1 (no Holdout running) and T2 (a Holdout running), each with the bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class TodayShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val spark = OfferCardState("spark", "Spark · 7-day", "Light it up · knees allowed", "12,500 SKR", "at stake · 14 joined", "Joining closes in 2d 3h", "Prize boost: up to 480 SKR", false, 0)
    private val habit = OfferCardState("habit", "Habit · 21-day", "Make it stick · toes only", "9,000 SKR", "at stake · 9 joined", "Joining closes in 2d 3h", null, false, 0)

    private val fresh = HomeState(
        remainingToday = 25,
        bankedToday = 0,
        target = 25,
        completedToday = false,
        streak = 12,
        secondsLeftToday = 14_760,
        holdouts = HoldoutsBlock.Open(listOf(spark, habit)),
    )

    private fun t1(name: String, state: HomeState, badge: Boolean = true) = compose.shoot("today", name, heightDp = 1150) {
        Tabbed(Tab.Today, TabBadges(today = badge)) {
            HomeScreen(state, onStart = {}, onEvent = {}, onJoin = {}, onCheck = {})
        }
    }

    @Test
    fun t1Fresh() = t1("t1-fresh", fresh)

    @Test
    fun t1PartWay() = t1("t1-part-way", fresh.copy(remainingToday = 15, bankedToday = 10, freezes = 1))

    @Test
    fun t1Done() = t1(
        "t1-done",
        fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 13, toes = ToesMeter.Meter(toes = 18, measured = 25, best = 25, week = listOf(10, 16, 14, 22, 25, 18, 24))),
        badge = false,
    )

    @Test
    fun t1PhoneView() = compose.shoot("today", "t1-fresh-phone") {
        Tabbed(Tab.Today, TabBadges(today = true)) { HomeScreen(fresh, onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
    }

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val noon = start + 12 * day - 14_760 - 1

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    private val me = MembershipSnapshot(key(1), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined)

    private val cohort = CohortSnapshot(
        LeagueParams.LEAGUE_21D,
        1,
        start,
        start - 3_600,
        21,
        25,
        day,
        0,
        listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr),
        memberCount = 5,
        pool = 100uL * skr,
        form = LeagueParams.FORM_TOES,
    )

    private val h = Holdout(key(9), cohort, LeagueSnapshot(LeagueParams.judged(LeagueParams.LEAGUE_21D, start), nextIndex = 3).copy(boostReserve = 1_200uL * skr), me)

    private val roster = listOf(
        me,
        MembershipSnapshot(key(2), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined),
        MembershipSnapshot(key(3), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined),
        MembershipSnapshot(key(4), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined),
        MembershipSnapshot(key(5), 1, 1_250uL * skr, start - day, 10, MembershipSnapshot.State.Joined),
    )

    private fun t2(name: String, banked: Int = 0, todayDone: Boolean = false, pendingDay: Int? = null, at: Long = noon, offline: Boolean = false, canPayFees: Boolean = true, heightDp: Int? = null) = compose.shoot("today", name, heightDp = heightDp) {
        Tabbed(Tab.Today, TabBadges(today = !todayDone)) {
            CohortHome(
                h = h,
                members = roster,
                stage = Stage.Active(11, todayDone),
                now = at,
                banked = banked,
                offline = offline,
                pendingDay = pendingDay,
                autoSendTried = false,
                busy = false,
                message = null,
                messageIsWarning = false,
                onStart = {},
                onSubmit = {},
                onDetail = {},
                onGetTokens = {},
                streak = 12,
                canPayFees = canPayFees,
            )
        }
    }

    @Test
    fun t2ToDo() = t2("t2-to-do")

    @Test
    fun t2PartWay() = t2("t2-part-way", banked = 10)

    @Test
    fun t2Last10Minutes() = t2("t2-last-10-minutes", banked = 10, at = start + 12 * day - 1 - 510)

    @Test
    fun t2Submitted() = t2("t2-submitted", todayDone = true)

    @Test
    fun t2DoneNotSent() = t2("t2-done-not-sent", pendingDay = 11)
}
