package holdout.app.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import holdout.app.chain.Deployment
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.streak.ToesMeter
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Today with no Holdout running (T1, board 3 · 3b · 3c; FR-053, ES-3, ES-15):
 * every state of the Daily 25 card, the nudges, the comeback card and the
 * block of Holdouts open to join, and the callbacks each button fires.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

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

    private var started = 0
    private var joined = mutableListOf<String>()
    private var events = mutableListOf<NavEvent>()
    private var checks = 0

    private fun show(state: HomeState) {
        compose.setContent {
            HoldoutTheme {
                HomeScreen(state, onStart = { started++ }, onEvent = { events += it }, onJoin = { joined += it }, onCheck = { checks++ })
            }
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onNode(hasText(s, substring = substring))

    @Test
    fun `fresh - the Daily 25 card with the number, the way to do it and Start`() {
        show(fresh)
        text("THE DAILY 25").assertIsDisplayed()
        text("FREE").assertIsDisplayed()
        text("25").assertIsDisplayed()
        text("push-ups").assertIsDisplayed()
        text("all at once, or 10 at a time · knees allowed").assertIsDisplayed()
        text("TODAY").assertIsDisplayed()
        compose.onNodeWithContentDescription("12-day streak").assertExists()
        text("4h 06m").assertIsDisplayed()
        text("left today").assertIsDisplayed()
        text("Start").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `part-way - what is left, the bar, what is banked for good and Keep going`() {
        show(fresh.copy(remainingToday = 15, bankedToday = 10))
        text("15").assertIsDisplayed()
        text("to go").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 blocks banked").assertExists()
        text("10 banked for good").assertIsDisplayed()
        text("Keep going").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `done - the number in green, the streak line, no button and no time left`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 13, toes = ToesMeter.Meter(toes = 18, measured = 25, best = 25, week = listOf(10, 16, 14, 22, 25, 18, 24))))
        text("25").assertIsDisplayed()
        text("done").assertIsDisplayed()
        text("Streak +1. See you tomorrow.").assertIsDisplayed()
        text("18 of 25 from your toes").assertIsDisplayed()
        text("Best: 25 from your toes").assertIsDisplayed()
        compose.onNodeWithContentDescription("13-day streak, extended today").assertExists()
        text("Start").assertDoesNotExist()
        text("Keep going").assertDoesNotExist()
        text("left today").assertDoesNotExist()
    }

    @Test
    fun `yesterday's note and the new-phone note sit in the card`() {
        show(fresh.copy(yesterdayNote = "Yesterday ended at 20 of 25. Today starts fresh.", newPhone = true))
        text("Yesterday ended at 20 of 25. Today starts fresh.").assertIsDisplayed()
        text("No banked reps on this device today. 25 to go.").assertIsDisplayed()
    }

    // ---- a streak milestone (Gap 2, T1) -------------------------------------------------------------

    private val milestoneDone = fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 7, milestoneToday = true)

    @Test
    fun `a milestone day shows the chip in its milestone state with the line under it`() {
        show(milestoneDone)
        compose.onNodeWithContentDescription("7-day streak, milestone").assertExists()
        compose.onNodeWithContentDescription("7-day streak, extended today").assertDoesNotExist()
        text("A week, every day. That's how habits start.").assertIsDisplayed()
    }

    @Test
    fun `the milestone line follows the day - a hundredth multiple says still here`() {
        show(milestoneDone.copy(streak = 200))
        compose.onNodeWithContentDescription("200-day streak, milestone").assertExists()
        text("200 days. Still here, still going.").assertIsDisplayed()
    }

    @Test
    fun `a plain day keeps the extended-today chip and no milestone line`() {
        show(milestoneDone.copy(streak = 8, milestoneToday = false))
        compose.onNodeWithContentDescription("8-day streak, extended today").assertExists()
        compose.onNodeWithContentDescription("8-day streak, milestone").assertDoesNotExist()
        text("A week, every day. That's how habits start.").assertDoesNotExist()
    }

    @Test
    fun `the next day at the same count is the plain chip again`() {
        // 7 days, but today's day is not done: no milestone state, no line.
        show(fresh.copy(streak = 7, milestoneToday = false))
        compose.onNodeWithContentDescription("7-day streak").assertExists()
        text("A week, every day. That's how habits start.").assertDoesNotExist()
    }

    @Test
    fun `no streak shows a broken flame at zero, and a held freeze is said`() {
        show(fresh.copy(streak = 0))
        compose.onNodeWithContentDescription("Streak broken").assertExists()
    }

    @Test
    fun `freeze lines - none held, one held, two held`() {
        show(fresh.copy(freezes = 1))
        text("1 freeze held — a missed day won't break the streak").assertIsDisplayed()
    }

    @Test
    fun `two freezes held say two missed days won't break it`() {
        show(fresh.copy(freezes = 2))
        text("2 freezes held — two missed days won't break the streak").assertIsDisplayed()
    }

    @Test
    fun `the offers show level, tagline, what is at stake, how long joining stays open and the boost, each with Join`() {
        show(fresh)
        text("HOLDOUTS OPEN TO JOIN").assertIsDisplayed()
        text("Holdouts need a Seeker.").assertIsDisplayed()
        text("Spark · 7-day").assertIsDisplayed()
        text("Light it up · knees allowed").assertIsDisplayed()
        text("12,500 SKR").assertIsDisplayed()
        text("at stake · 14 joined").assertIsDisplayed()
        text("Prize boost: up to 480 SKR").assertIsDisplayed()
        text("Habit · 21-day").performScrollTo().assertIsDisplayed()
        text("Make it stick · toes only").assertIsDisplayed()
        compose.onAllNodes(hasText("Joining closes in 2d 3h")).assertCountEquals(2)
    }

    @Test
    fun `Join on an offer reports that Holdout's address`() {
        show(fresh)
        compose.onNodeWithContentDescription("Join Spark · 7-day").performClick()
        compose.onNodeWithContentDescription("Join Habit · 21-day").performScrollTo().performClick()
        assertEquals(listOf("spark", "habit"), joined)
    }

    @Test
    fun `a Hackathon Holdout keeps its badge and its own name`() {
        val test = spark.copy(cohort = "t", title = OfferCardState.TEST_TITLE, tagline = "3 days · 5 push-ups a day · joinable right up to its start", atStake = "250 SKR", joined = "at stake · 3 joined", timing = "Starts in 12m 30s", boost = null, test = true, testDayLength = 600)
        show(fresh.copy(holdouts = HoldoutsBlock.Open(listOf(test, spark))))
        text("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        text("Hackathon Holdout").assertIsDisplayed()
        text("Starts in 12m 30s").assertIsDisplayed()
    }

    @Test
    fun `nothing open to join says the next one opens soon`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Open(emptyList())))
        text("The next Holdout opens soon.").assertIsDisplayed()
        text("The Daily 25 is open now.").assertIsDisplayed()
        text("HOLDOUTS OPEN TO JOIN").assertDoesNotExist()
    }

    @Test
    fun `while a lobby place is held the Holdouts block is not drawn at all`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Hidden, nudges = listOf(NudgeRowState("Spark · 7-day lobby · starts in 2d 0h", NavEvent.Open(Screen.Lobby("l"))))))
        text("Spark · 7-day lobby · starts in 2d 0h").assertIsDisplayed()
        text("THE DAILY 25").assertIsDisplayed()
        text("HOLDOUTS OPEN TO JOIN").assertDoesNotExist()
        text("Holdouts need a Seeker.").assertDoesNotExist()
        text("The next Holdout opens soon.").assertDoesNotExist()
        text("Join").assertDoesNotExist()
    }

    @Test
    fun `no program on the network says Holdouts are not live yet`() {
        show(fresh.copy(holdouts = HoldoutsBlock.NotLive))
        text("HOLDOUTS ARE NOT LIVE ON ${Deployment.networkName.uppercase()} YET").assertIsDisplayed()
    }

    @Test
    fun `before the chain answers the screen says it is reading`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Reading))
        text("READING THE CHAIN…").assertIsDisplayed()
    }

    @Test
    fun `chain unreachable with nothing read - the Daily 25 still works, and Check again`() {
        show(fresh.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable))
        text("UNREACHABLE — THE DAILY 25 STILL WORKS", substring = true).assertIsDisplayed()
        text("${Deployment.networkName} can't be reached right now.").assertIsDisplayed()
        text("left today").assertDoesNotExist()
        text("Check again").performClick()
        assertEquals(1, checks)
        text("Start").assertIsDisplayed()
    }

    @Test
    fun `an earlier read shown while unreachable says so above the offers`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Open(listOf(spark), stale = true), stillUnreachable = true))
        text("${Deployment.networkName} still can't be reached.").assertIsDisplayed()
        text("Spark · 7-day").assertIsDisplayed()
    }

    @Test
    fun `a nudge row opens the screen that needs the member`() {
        val go = NavEvent.Open(Screen.Claim("c1"))
        show(fresh.copy(nudges = listOf(NudgeRowState("You finished · claim 1,412 SKR", go, NudgeTone.Success))))
        text("You finished · claim 1,412 SKR").performClick()
        assertEquals(listOf<NavEvent>(go), events)
    }

    @Test
    fun `the comeback card says what can come back and takes the member to the round`() {
        val go = NavEvent.OpenStake("r", 1, true)
        val words = "Get back up to 250 SKR of your lost stake: finish the Habit · 21-day starting in 2d 4h. Joining closes in 1d 3h."
        show(fresh.copy(comeback = ComebackCardState(words, go)))
        text(words).performClick()
        assertEquals(listOf<NavEvent>(go), events)
    }

    @Test
    fun `How Holdout works opens the tour`() {
        show(fresh)
        text("How Holdout works").performScrollTo().performClick()
        text("4 cards").assertExists()
        assertEquals(listOf<NavEvent>(NavEvent.OpenTour), events)
    }

    @Test
    fun `Today does not list the running Holdouts - they live on the Holdouts tab`() {
        show(fresh)
        text("RUNNING NOW").assertDoesNotExist()
        text("Running now").assertDoesNotExist()
    }

    @Test
    fun `no word on the screen is banned in member copy, and no date or clock time shows`() {
        show(fresh.copy(freezes = 1, nudges = listOf(NudgeRowState("You finished · claim 1,412 SKR", NavEvent.SelectTab(Tab.Holdouts), NudgeTone.Success))))
        compose.onNodeWithText("TODAY'S CHALLENGE").assertDoesNotExist()
        text("earn", substring = true).assertDoesNotExist()
        text("cohort", substring = true).assertDoesNotExist()
        text("Easy", substring = true).assertDoesNotExist()
        text("Hard", substring = true).assertDoesNotExist()
        text("≈", substring = true).assertDoesNotExist()
        compose.onNodeWithText("12 Oct", substring = true).assertDoesNotExist()
    }
}
