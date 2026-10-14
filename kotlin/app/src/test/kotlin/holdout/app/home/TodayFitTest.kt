package holdout.app.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.SEEKER
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
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
import kotlin.test.assertEquals

/**
 * Today's hero is one frame, 312 dp high in every state of the Daily 25 (T1) and of a Holdout's day (T2),
 * and Today with its rows group and its offers fits one Seeker screen with nothing to scroll (001 FR-069a:
 * "a state changes its content, never its size"; owner, 12 Oct: no-scroll screens). The app's area on a
 * Seeker is 400 × 829 dp; the bottom bar takes its 64 dp, so Today gets what is left. Native graphics, so
 * text is measured with real fonts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SEEKER)
class TodayFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    // ---- T1 fixtures --------------------------------------------------------------------------------

    private val spark = OfferCardState("spark", "Spark · 7-day", "Light it up · knees allowed", "12,500 SKR", "at stake · 14 joined", "Joining closes in 2d 3h", "Prize boost: up to 480 SKR", false, 0)
    private val habit = OfferCardState("habit", "Habit · 21-day", "Make it stick · toes only", "9,000 SKR", "at stake · 9 joined", "Joining closes in 2d 3h", null, false, 0)
    private val claim = NudgeRowState("Claim 1,412 SKR", NavEvent.Open(Screen.Claim("c1")), NudgeTone.Success)

    private val fresh = HomeState(
        remainingToday = 25,
        bankedToday = 0,
        target = 25,
        completedToday = false,
        streak = 12,
        secondsLeftToday = 14_760,
        holdouts = HoldoutsBlock.Open(listOf(spark, habit)),
        nudges = listOf(claim),
    )

    private val done = fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 13)

    private val t1: List<Pair<String, HomeState>> = listOf(
        "T1 fresh" to fresh,
        "T1 claim, comeback and the offers" to fresh.copy(comeback = ComebackCardState("Get back 250 SKR of your lost stake: finish the Habit · 21-day starting in 2d 4h. Joining closes in 1d 3h.", NavEvent.OpenStake("r", 1, true))),
        "T1 part-way" to fresh.copy(remainingToday = 15, bankedToday = 10),
        "T1 done" to done,
        "T1 milestone day" to done.copy(streak = 7, milestoneToday = true),
        "T1 new phone" to fresh.copy(newPhone = true),
        "T1 yesterday missed" to fresh.copy(yesterdayNote = "Yesterday ended at 20 of 25. Today starts fresh."),
        "T1 broken streak" to fresh.copy(streak = 0, longestStreak = 9),
        "T1 the Daily 25 with the devnet bar" to fresh.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable),
        "T1 devnet bar over an earlier read" to fresh.copy(holdouts = HoldoutsBlock.Open(listOf(spark, habit), stale = true), nudges = emptyList(), offline = true),
        "T1 a lobby place held" to fresh.copy(holdouts = HoldoutsBlock.Hidden),
        "T1 nothing open to join" to fresh.copy(holdouts = HoldoutsBlock.Open(emptyList())),
        "T1 a Hackathon round to join" to fresh.copy(holdouts = HoldoutsBlock.Open(listOf(spark.copy(title = "Hackathon · 3-day", atStake = "250 SKR", joined = "at stake · 3 joined", timing = "Starts in 12m 30s", test = true, testDayLength = 600), habit))),
    )

    // ---- T2 fixtures --------------------------------------------------------------------------------

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val now = start + 12 * day - 14_760 - 1

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    private val me = MembershipSnapshot(key(1), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined)

    private val cohort = CohortSnapshot(
        LeagueParams.LEAGUE_21D, 1, start, start - 3_600, 21, 25, day, 0,
        listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr),
        memberCount = 312, pool = 100uL * skr, form = LeagueParams.FORM_TOES,
    )

    private val h = Holdout(key(9), cohort, LeagueSnapshot(LeagueParams.judged(LeagueParams.LEAGUE_21D, start), nextIndex = 3).copy(boostReserve = 1_200uL * skr), me)

    private val roster = listOf(me) + (2..4).map { MembershipSnapshot(key(it), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined) } +
        MembershipSnapshot(key(5), 1, 1_250uL * skr, start - day, 10, MembershipSnapshot.State.Joined)

    private fun t2(
        banked: Int = 0,
        todayDone: Boolean = false,
        pendingDay: Int? = null,
        autoSendTried: Boolean = false,
        offline: Boolean = false,
        busy: Boolean = false,
        canPayFees: Boolean = true,
        mayStillLand: Boolean = false,
        at: Long = now,
        index: Int = 11,
        holdout: Holdout = h,
    ): @Composable () -> Unit = {
        CohortHome(
            h = holdout,
            members = roster,
            stage = Stage.Active(index, todayDone),
            now = at,
            banked = banked,
            offline = offline,
            pendingDay = pendingDay,
            autoSendTried = autoSendTried,
            busy = busy,
            message = null,
            messageIsWarning = false,
            onStart = {},
            onSubmit = {},
            onEvent = {},
            onGetTokens = {},
            streak = 12,
            canPayFees = canPayFees,
            mayStillLand = mayStillLand,
            offers = OffersRow(2, "9,000 SKR"),
            nudges = listOf(claim),
        )
    }

    private val t2: List<Pair<String, @Composable () -> Unit>> = listOf(
        "T2 to do" to t2(),
        "T2 part-way" to t2(banked = 10),
        "T2 last 10 minutes" to t2(banked = 10, at = start + 12 * day - 1 - 510),
        "T2 done, unsent (tap to submit)" to t2(pendingDay = 11, autoSendTried = true),
        "T2 done, sending" to t2(pendingDay = 11, busy = true),
        "T2 done, waiting for network" to t2(pendingDay = 11, offline = true),
        "T2 short of SOL" to t2(pendingDay = 11, canPayFees = false),
        "T2 sent, not confirmed" to t2(pendingDay = 11, autoSendTried = true, mayStillLand = true),
        "T2 on chain" to t2(todayDone = true),
        "T2 last day on chain" to t2(todayDone = true, index = 20, at = start + 21 * day - 14_760 - 1),
        "T2 offline before the sitting" to t2(offline = true),
        "T2 a Hackathon round" to t2(
            holdout = h.copy(cohort = cohort.copy(leagueId = LeagueParams.TEST_LEAGUE_MIN, dayLength = 600, durationDays = 3, repsPerDay = 5)),
            at = start + 1 * 600 + 100,
            index = 1,
        ),
    )

    // ---- the measuring ------------------------------------------------------------------------------

    private var current: @Composable () -> Unit by mutableStateOf({})

    private fun measure(states: List<Pair<String, @Composable () -> Unit>>): Pair<List<String>, Map<String, Float>> {
        compose.setContent {
            HoldoutTheme {
                // Today above the bottom bar's 64 dp, as the shell lays it out.
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { current() }
                    Spacer(Modifier.height(Spacing.navHeight))
                }
            }
        }
        val wrongHeight = mutableListOf<String>()
        val scrolls = linkedMapOf<String, Float>()
        states.forEach { (name, content) ->
            current = content
            compose.waitForIdle()
            val height = compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot().height
            if (height != 312.dp) wrongHeight += "$name is $height"
            val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
            val px = nodes.maxOfOrNull { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() } ?: 0f
            if (px > 0f) scrolls[name] = px
        }
        return wrongHeight to scrolls
    }

    @Test
    fun `T1 - the hero is 312 dp in every state and nothing on Today scrolls`() {
        val (wrongHeight, scrolls) = measure(
            t1.map { (name, state) ->
                name to @Composable { HomeScreen(state, onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
            },
        )
        assertEquals(emptyList(), wrongHeight, "states whose hero is not 312 dp")
        assertEquals(emptyMap(), scrolls, "states that scroll, by how many px at 480 dpi")
    }

    @Test
    fun `T2 - the hero is 312 dp in every state and nothing on Today scrolls`() {
        val (wrongHeight, scrolls) = measure(t2)
        assertEquals(emptyList(), wrongHeight, "states whose hero is not 312 dp")
        assertEquals(emptyMap(), scrolls, "states that scroll, by how many px at 480 dpi")
    }
}
