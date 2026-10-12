package holdout.app.sitting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import holdout.repcounter.CameraView
import holdout.repcounter.Stakes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Board 16, the set-up guide: Positioning, Rules, Start (owner, 12 Oct 2026). No intro paragraph under
 * the title; the Rules page speaks for the sitting's own form (002 FR-014, ruling C-D2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SetupGuideTest {
    @get:Rule
    val compose = createComposeRule()

    private val introSentence = "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone."
    private val placeFloor = "Phone on the floor, leaning on something, facing where you'll be."
    private val seesYou = "Top of a push-up, hands under your shoulders, turned a little away: it sees you head to feet and your near knee."
    private val outline = "Line up with the outline on screen."
    private val commonRule = "All the way down and up — short reps are skipped."
    private val habitRules = listOf(
        "Full push-ups from your toes, down to just above the floor.",
        "Knee push-ups, or hips resting on the floor, don't count.",
    )
    private val sparkRules = listOf(
        "Aim for full push-ups from your toes.",
        "From the knees still counts, body straight from the knees.",
    )
    private val dailyRule = "From your toes or your knees — both count."
    private val startLines = listOf(
        "Hold the top for a second. A ring fills, then “three, two, one, go”.",
        "Nothing to tap. Each rep beeps; every 10 bank for good.",
        "Stop any time with ✕ in the corner.",
    )

    private var done: List<Boolean> = emptyList()

    private fun show(stakes: Stakes, detected: CameraView? = null, levelLabel: String = SittingCopy.levelLabel(stakes)) {
        done = emptyList()
        compose.setContent {
            HoldoutTheme {
                var dontShowAgain by remember { mutableStateOf(false) }
                SetupGuide(
                    stakes = stakes,
                    detected = detected,
                    levelLabel = levelLabel,
                    dontShowAgain = dontShowAgain,
                    onDontShowAgainChange = { dontShowAgain = it },
                    onDone = { done = done + it },
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun page(n: Int) {
        compose.onNodeWithText(listOf("1. Positioning", "2. Rules", "3. Start")[n - 1]).performClick()
    }

    @Test
    fun `the title carries the level beside it`() {
        show(Stakes.STAKED)
        compose.onNodeWithText("Before you get down").assertIsDisplayed()
        compose.onNodeWithText("habit · 21-day").assertIsDisplayed()
    }

    @Test
    fun `a Spark names itself, the Daily 25 too`() {
        show(Stakes.EASY)
        compose.onNodeWithText("spark · 7-day").assertIsDisplayed()
    }

    @Test
    fun `the Daily 25's guide is named the daily 25`() {
        show(Stakes.DAILY)
        compose.onNodeWithText("the daily 25").assertIsDisplayed()
    }

    @Test
    fun `the tabs are Positioning, Rules and Start`() {
        show(Stakes.STAKED)
        listOf("1. Positioning", "2. Rules", "3. Start").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("1. Phone").assertDoesNotExist()
        compose.onNodeWithText("2. Body").assertDoesNotExist()
    }

    private fun assertNoIntroSentence(stakes: Stakes) {
        show(stakes)
        for (n in 1..3) {
            page(n)
            compose.onNodeWithText(introSentence).assertDoesNotExist()
        }
    }

    @Test
    fun `the intro sentence is gone from every page of a Habit's guide`() = assertNoIntroSentence(Stakes.STAKED)

    @Test
    fun `the intro sentence is gone from every page of a Spark's guide`() = assertNoIntroSentence(Stakes.EASY)

    @Test
    fun `the intro sentence is gone from every page of the Daily 25's guide`() = assertNoIntroSentence(Stakes.DAILY)

    @Test
    fun `the positioning page has the phone, the 45 degree card, the body and the outline in order`() {
        show(Stakes.STAKED)

        compose.onNodeWithText(placeFloor).assertExists()
        compose.onNodeWithText("Ahead of your shoulder, about 1.5 m away.").assertExists()
        compose.onNodeWithText(seesYou).assertExists()
        compose.onNodeWithText(outline).assertExists()
        val order = listOf(placeFloor, "At 45°", seesYou, outline).map(::top)
        assertEquals(order.sorted(), order, "phone, the 45° card, the body, the outline")
    }

    private fun assertNoRuleLineOnPositioning(stakes: Stakes) {
        show(stakes)
        compose.onNodeWithText(Stakes.STAKED_RULE).assertDoesNotExist()
        compose.onNodeWithText(Stakes.EASY_RULE).assertDoesNotExist()
        compose.onNodeWithText("counted as the Spark").assertDoesNotExist()
    }

    @Test
    fun `a Habit's positioning page carries no rule line`() = assertNoRuleLineOnPositioning(Stakes.STAKED)

    @Test
    fun `a Spark's positioning page carries no rule line`() = assertNoRuleLineOnPositioning(Stakes.EASY)

    @Test
    fun `the Daily 25's positioning page carries no rule line`() = assertNoRuleLineOnPositioning(Stakes.DAILY)

    @Test
    fun `the 45 degree card is ticked once the camera sees the phone there`() {
        show(Stakes.STAKED, detected = CameraView.OBLIQUE)
        compose.onNodeWithText("At 45° ✓").assertExists()
    }

    @Test
    fun `a Habit's rules ask for the toes and refuse the knees and the floor, then the common line`() {
        show(Stakes.STAKED)
        page(2)

        val lines = habitRules + commonRule
        lines.forEach { compose.onNodeWithText(it).assertExists() }
        val order = lines.map(::top)
        assertEquals(order.sorted(), order, "the sitting's lines, then the common one last")
        (sparkRules + dailyRule).forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun `a Spark's rules aim at the toes and let the knees count, then the common line`() {
        show(Stakes.EASY)
        page(2)

        val lines = sparkRules + commonRule
        lines.forEach { compose.onNodeWithText(it).assertExists() }
        val order = lines.map(::top)
        assertEquals(order.sorted(), order, "the sitting's lines, then the common one last")
        (habitRules + dailyRule).forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun `the Daily 25's rules say toes or knees both count, then the common line`() {
        show(Stakes.DAILY)
        page(2)

        compose.onNodeWithText(dailyRule).assertExists()
        compose.onNodeWithText(commonRule).assertExists()
        assertTrue(top(dailyRule) < top(commonRule), "the common line is last")
        (habitRules + sparkRules).forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun `the start page has the three lines and the down-and-up line has moved to Rules`() {
        show(Stakes.STAKED)
        page(3)

        startLines.forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText(commonRule).assertDoesNotExist()
    }

    @Test
    fun `Next walks the pages and Got it closes without asking to hide the guide`() {
        show(Stakes.STAKED)

        compose.onNodeWithText("Skip").assertExists()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Back").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Got it").performClick()

        assertEquals(listOf(false), done)
    }

    @Test
    fun `Skip closes the guide`() {
        show(Stakes.STAKED)
        compose.onNodeWithText("Skip").performClick()
        assertEquals(listOf(false), done)
    }

    @Test
    fun `Don't show this again is passed on when the guide closes`() {
        show(Stakes.STAKED)

        compose.onNodeWithText("Don't show this again").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(listOf(true), done)
    }

    @Test
    fun `Back goes to the page before`() {
        show(Stakes.STAKED)
        page(2)
        compose.onNodeWithText(commonRule).assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText(placeFloor).assertExists()
    }
}
