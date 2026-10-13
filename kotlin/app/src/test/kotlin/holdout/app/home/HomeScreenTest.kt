package holdout.app.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import holdout.app.chain.Deployment
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Today with no Holdout running (T1, board Today; FR-053, FR-069a, FR-069h, ES-3, ES-15): the Daily 25 in the
 * one 312 dp hero in every state, the streak chip and the foot link to Me, the rows group, the one compact
 * offer card and its quiet row, the devnet bar, and the callbacks each tap fires.
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

    private fun colourOf(s: String): androidx.compose.ui.graphics.Color {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(s).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        return result.first().layoutInput.style.color
    }

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun bottom(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom

    // ---- the hero: the Daily 25 ---------------------------------------------------------------------

    @Test
    fun `fresh - the Daily 25 in the hero with its time left, the number, the way to do it and Start`() {
        show(fresh)
        text("TODAY").assertIsDisplayed()
        text("THE DAILY 25").assertIsDisplayed()
        text("4h 06m").assertIsDisplayed()
        text("left").assertIsDisplayed()
        text("25").assertIsDisplayed()
        text("PUSH-UPS").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 3 blocks banked").assertExists()
        text("Free · knees allowed · in blocks of 10").assertIsDisplayed()
        text("Start").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the Daily 25 sits in the one hero frame, 312 dp`() {
        show(fresh)
        assertEquals(312f, compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot().height.value)
    }

    @Test
    fun `part-way - what is left, the first bar filled, what is banked for good and Keep going`() {
        show(fresh.copy(remainingToday = 15, bankedToday = 10))
        text("15").assertIsDisplayed()
        text("TO GO").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 blocks banked").assertExists()
        text("10 banked for good").assertIsDisplayed()
        text("Keep going").performClick()
        assertEquals(1, started)
        text("Start").assertDoesNotExist()
    }

    @Test
    fun `done - 25 done in green, all three bars, the streak line, no countdown and the tonal next-Daily pill`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 13))
        text("25").assertIsDisplayed()
        text("DONE").assertIsDisplayed()
        assertEquals(HoldoutColors.Success, colourOf("25"))
        compose.onNodeWithContentDescription("3 of 3 blocks banked").assertExists()
        text("All 25 counted. Streak +1.").assertIsDisplayed()
        text("Next Daily 25 in 4h 06m").assertIsDisplayed()
        text("Start").assertDoesNotExist()
        text("Keep going").assertDoesNotExist()
        text("left").assertDoesNotExist()
        compose.onNodeWithContentDescription("13-day streak, extended today. Opens your streak on Me.").assertExists()
    }

    @Test
    fun `done with the clock unknown - the pill says tomorrow rather than guess a time`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, secondsLeftToday = null))
        text("Next Daily 25 tomorrow").assertIsDisplayed()
    }

    @Test
    fun `the next-Daily pill is not a button`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true))
        text("Next Daily 25 in 4h 06m").performClick()
        assertEquals(0, started)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `no toes meter and no tour row on Today - they are on Me and About`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true))
        text("from your toes", substring = true).assertDoesNotExist()
        text("Best:", substring = true).assertDoesNotExist()
        text("How Holdout works").assertDoesNotExist()
        text("5 cards").assertDoesNotExist()
        text("6 cards").assertDoesNotExist()
    }

    @Test
    fun `the last 10 minutes of the day turn the countdown to its warning and say so in words`() {
        show(fresh.copy(secondsLeftToday = 510))
        compose.onNodeWithContentDescription("Under 10 minutes left, 8m 30s").assertExists()
    }

    @Test
    fun `yesterday's note and the new-phone note take the sub line`() {
        show(fresh.copy(yesterdayNote = "Yesterday ended at 20 of 25. Today starts fresh."))
        text("Yesterday ended at 20 of 25. Today starts fresh.").assertIsDisplayed()
        text("Free · knees allowed · in blocks of 10").assertDoesNotExist()
    }

    @Test
    fun `a new phone says nothing is banked on this device`() {
        show(fresh.copy(newPhone = true))
        text("No banked reps on this device today. 25 to go.").assertIsDisplayed()
    }

    // ---- the streak chip, a button to Me -----------------------------------------------------------

    @Test
    fun `the streak chip is a button to Me, its name ends with where it goes, and its target is 48 dp`() {
        show(fresh)
        val chip = compose.onNodeWithContentDescription("12-day streak. Opens your streak on Me.")
        chip.assertIsEnabled().performClick()
        assertEquals(listOf<NavEvent>(NavEvent.SelectTab(Tab.Me)), events)
        val touch = chip.fetchSemanticsNode().touchBoundsInRoot
        with(compose.density) { assertTrue(touch.height >= 48.dp.toPx(), "touch height ${touch.height}") }
    }

    @Test
    fun `a member who never trained has a plain 0 streak, not a broken one`() {
        show(fresh.copy(streak = 0, longestStreak = 0))
        compose.onNodeWithContentDescription("0-day streak. Opens your streak on Me.").assertExists()
        compose.onNode(hasContentDescription("Streak broken", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `a streak that ran and lapsed shows a broken flame at zero`() {
        show(fresh.copy(streak = 0, longestStreak = 9))
        compose.onNodeWithContentDescription("Streak broken. Opens your streak on Me.").assertExists()
    }

    @Test
    fun `a milestone day shows the chip in its milestone state and the milestone line as the foot`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 7, milestoneToday = true))
        compose.onNodeWithContentDescription("7-day streak, milestone. Opens your streak on Me.").assertExists()
        text("A week, every day. That's how habits start.").assertIsDisplayed()
    }

    @Test
    fun `the milestone line follows the day - a hundredth multiple says still here`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 200, milestoneToday = true))
        compose.onNodeWithContentDescription("200-day streak, milestone. Opens your streak on Me.").assertExists()
        text("200 days. Still here, still going.").assertIsDisplayed()
    }

    @Test
    fun `a plain day keeps the extended-today chip and no milestone line`() {
        show(fresh.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 8))
        compose.onNodeWithContentDescription("8-day streak, extended today. Opens your streak on Me.").assertExists()
        text("A week, every day. That's how habits start.").assertDoesNotExist()
    }

    // ---- the foot: one quiet link to Me ---------------------------------------------------------------

    @Test
    fun `the foot says how far the next streak freeze is, and opens Me`() {
        show(fresh)
        text("2 more days to a streak freeze").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 more days to a streak freeze. Opens Me.").performClick()
        assertEquals(listOf<NavEvent>(NavEvent.SelectTab(Tab.Me)), events)
    }

    @Test
    fun `the foot with a freeze held`() {
        show(fresh.copy(freezes = 1))
        text("1 freeze held · covers one missed day").assertIsDisplayed()
    }

    @Test
    fun `the foot with two freezes held`() {
        show(fresh.copy(freezes = 2))
        text("2 freezes held · cover two missed days").assertIsDisplayed()
    }

    @Test
    fun `the foot with no streak says what earns the first freeze`() {
        show(fresh.copy(streak = 0))
        text("7 days in a row earn a streak freeze").assertIsDisplayed()
    }

    // ---- the offers: one compact card, a quiet row ----------------------------------------------------

    @Test
    fun `the offers are the first Holdout as one compact card - name, at stake, joined and closes in, Join - and no boost line`() {
        show(fresh)
        text("OPEN TO JOIN").assertIsDisplayed()
        text("Spark · 7-day").assertIsDisplayed()
        text("12,500 SKR").assertIsDisplayed()
        text("at stake", substring = true).assertIsDisplayed()
        text("14 joined · closes in 2d 3h").assertIsDisplayed()
        compose.onNodeWithContentDescription("Join Spark · 7-day").assertExists()
        text("Prize boost", substring = true).assertDoesNotExist()
        text("Light it up · knees allowed").assertDoesNotExist()
        text("Habit · 21-day").assertDoesNotExist()
    }

    @Test
    fun `when more can be joined a quiet row says how many and opens Holdouts`() {
        show(fresh)
        text("1 more open ›").assertIsDisplayed()
        assertEquals(HoldoutColors.TextSecondary, colourOf("1 more open ›"))
        text("1 more open ›").performClick()
        assertEquals(listOf<NavEvent>(NavEvent.SelectTab(Tab.Holdouts)), events)
    }

    @Test
    fun `with three open the quiet row counts the two others`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Open(listOf(spark, habit, habit.copy(cohort = "h2")))))
        text("2 more open ›").assertIsDisplayed()
    }

    @Test
    fun `one open Holdout has no quiet row`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Open(listOf(spark))))
        text("more open", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Join on the offer reports that Holdout's address`() {
        show(fresh)
        compose.onNodeWithContentDescription("Join Spark · 7-day").performClick()
        assertEquals(listOf("spark"), joined)
    }

    @Test
    fun `a tap anywhere on the offer card opens it, as Join does`() {
        show(fresh)
        text("Spark · 7-day").performClick()
        assertEquals(listOf("spark"), joined)
    }

    @Test
    fun `a wallet this session found holds no Seeker - the card shows without a Join, and one line says why`() {
        show(fresh.copy(canJoin = false))
        text("Spark · 7-day").assertIsDisplayed()
        text(MyHoldoutsModel.SEEKER_ONLY).assertIsDisplayed()
        compose.onNodeWithContentDescription("Join Spark · 7-day").assertDoesNotExist()
        text("Spark · 7-day").performClick()
        assertEquals(emptyList(), joined)
    }

    @Test
    fun `a Hackathon Holdout keeps its badge and its own name`() {
        val test = spark.copy(cohort = "t", title = "Hackathon · 3-day", tagline = "A quick taste · knees allowed", atStake = "250 SKR", joined = "at stake · 3 joined", timing = "Starts in 12m 30s", boost = null, test = true, testDayLength = 600)
        show(fresh.copy(holdouts = HoldoutsBlock.Open(listOf(test, spark))))
        text("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        text("Hackathon · 3-day").assertIsDisplayed()
        text("3 joined · starts in 12m 30s").assertIsDisplayed()
    }

    @Test
    fun `nothing open to join says the next one opens soon, and nothing else`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Open(emptyList())))
        text("The next Holdout opens soon.").assertIsDisplayed()
        text("The Daily 25 is open now.").assertDoesNotExist()
        text("OPEN TO JOIN").assertDoesNotExist()
    }

    @Test
    fun `while a lobby place is held there are no offers at all, only the lobby row`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Hidden, nudges = listOf(NudgeRowState("Spark · 7-day lobby · starts in 2d 0h", NavEvent.Open(Screen.Lobby("l"))))))
        text("Spark · 7-day lobby · starts in 2d 0h").assertIsDisplayed()
        text("THE DAILY 25").assertIsDisplayed()
        text("OPEN TO JOIN").assertDoesNotExist()
        text("The next Holdout opens soon.").assertDoesNotExist()
        text("more open", substring = true).assertDoesNotExist()
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

    // ---- the rows group ------------------------------------------------------------------------------

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
        val words = "Get back 250 SKR of your lost stake: finish the Habit · 21-day starting in 2d 4h. Joining closes in 1d 3h."
        show(fresh.copy(comeback = ComebackCardState(words, go)))
        text(words).performClick()
        assertEquals(listOf<NavEvent>(go), events)
    }

    @Test
    fun `the rows group sits after the hero with the section gap above it`() {
        show(fresh.copy(nudges = listOf(NudgeRowState("You finished · claim 1,412 SKR", NavEvent.Open(Screen.Claim("c1")), NudgeTone.Success))))
        val rowTop = compose.onNode(hasText("You finished · claim 1,412 SKR")).getUnclippedBoundsInRoot().top
        assertEquals(Spacing.betweenSections.value, (rowTop - bottom("day-hero")).value, 0.5f)
    }

    @Test
    fun `the offers come after the rows`() {
        show(fresh.copy(nudges = listOf(NudgeRowState("You finished · claim 1,412 SKR", NavEvent.Open(Screen.Claim("c1")), NudgeTone.Success))))
        val rowBottom = compose.onNode(hasText("You finished · claim 1,412 SKR")).getUnclippedBoundsInRoot().bottom
        val offersTop = compose.onNode(hasText("OPEN TO JOIN")).getUnclippedBoundsInRoot().top
        assertTrue(offersTop > rowBottom, "offers at $offersTop, rows end at $rowBottom")
    }

    @Test
    fun `Today does not list the running Holdouts - they live on the Holdouts tab`() {
        show(fresh)
        text("RUNNING NOW").assertDoesNotExist()
        text("Running now").assertDoesNotExist()
    }

    // ---- the devnet bar (FR-069h) ---------------------------------------------------------------------

    @Test
    fun `chain unreachable with nothing read - the bar under the header, the Daily 25 still works, and Retry`() {
        show(fresh.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable))
        text("${Deployment.networkName} isn't answering").assertIsDisplayed()
        text("The Daily 25 still works.").assertIsDisplayed()
        text("left").assertDoesNotExist()
        text("Retry").performClick()
        assertEquals(1, checks)
        text("Start").assertIsDisplayed()
    }

    @Test
    fun `the bar sits directly under the header, above the hero`() {
        show(fresh.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable))
        val header = text("TODAY").getUnclippedBoundsInRoot()
        val bar = text("${Deployment.networkName} isn't answering").getUnclippedBoundsInRoot()
        assertTrue(bar.top > header.bottom, "bar ${bar.top} under header ${header.bottom}")
        assertTrue(bar.bottom <= top("day-hero"), "bar ends ${bar.bottom}, hero starts ${top("day-hero")}")
        assertTrue(top("day-hero") - bar.bottom < 80.dp, "nothing sits between the bar and the hero")
    }

    @Test
    fun `no separate can't-be-reached row and no Check again button anywhere`() {
        show(fresh.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable))
        text("can't be reached", substring = true).assertDoesNotExist()
        text("Check again").assertDoesNotExist()
        text("UNREACHABLE", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a failed check says devnet still isn't answering`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Open(listOf(spark), stale = true), stillUnreachable = true))
        text("${Deployment.networkName} still isn't answering").assertIsDisplayed()
        text("Spark · 7-day").assertIsDisplayed()
    }

    @Test
    fun `while checking the bar's action reads Checking and cannot be pressed`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Unreachable, checking = true))
        text("Checking…").assertIsDisplayed().assertIsNotEnabled()
        text("Retry").assertDoesNotExist()
    }

    @Test
    fun `an offline lobby-hidden Today still shows the bar`() {
        show(fresh.copy(holdouts = HoldoutsBlock.Hidden, offline = true))
        text("${Deployment.networkName} isn't answering").assertIsDisplayed()
    }

    @Test
    fun `the bar is not drawn while devnet answers`() {
        show(fresh)
        text("isn't answering", substring = true).assertDoesNotExist()
        text("Retry").assertDoesNotExist()
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
