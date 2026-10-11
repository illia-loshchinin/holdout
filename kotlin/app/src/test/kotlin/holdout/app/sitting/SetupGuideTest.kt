package holdout.app.sitting

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
 * Board 16, the set-up guide, with every line 002 FR-015 mandates in its order
 * (ruling C-D1/D3) and the level's rule sentence after them (002 FR-014, C-D2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SetupGuideTest {
    @get:Rule
    val compose = createComposeRule()

    private val headline = "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone."
    private val fromThere = "From there it sees all of you, and the knee nearer to it."
    private val wholeBody = "Your whole body in the picture, head to feet."
    private val knees = "Your knees clearly visible to the phone."
    private val turn = "Turn a little away, so the phone sees your near knee."
    private val position = "Top of a push-up: arms straight, hands under your shoulders."
    private val outline = "Line up with the outline on screen."

    private var done: List<Boolean> = emptyList()

    private fun show(stakes: Stakes, detected: CameraView? = null, levelLabel: String = SittingCopy.levelLabel(stakes)) {
        done = emptyList()
        compose.setContent {
            HoldoutTheme {
                SetupGuide(stakes = stakes, detected = detected, levelLabel = levelLabel, onDone = { done = done + it })
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun page(n: Int) {
        compose.onNodeWithText(listOf("1. Phone", "2. Body", "3. Start")[n - 1]).performClick()
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
    fun `FR-015's headline is under the title on every page`() {
        show(Stakes.STAKED)
        for (n in 1..3) {
            page(n)
            compose.onNodeWithText(headline).assertExists()
            assertTrue(top("Before you get down") < top(headline), "the title comes first on page $n")
        }
    }

    @Test
    fun `the phone page says why 45 degrees serves both, after the drawing`() {
        show(Stakes.STAKED)

        compose.onNodeWithText("Stand the phone on the floor, facing where you'll be. Lean it on something.").assertExists()
        compose.onNodeWithText("Ahead of your shoulder, about 1.5 m away.").assertExists()
        compose.onNodeWithText(fromThere).assertExists()
        assertTrue(top("At 45°") < top(fromThere), "the drawing comes before 'From there it sees all of you'")
    }

    @Test
    fun `the phone page ends with the level's rule sentence, after FR-015's lines`() {
        show(Stakes.STAKED)
        compose.onNodeWithText(Stakes.STAKED_RULE).assertExists()
        assertTrue(top(fromThere) < top(Stakes.STAKED_RULE))
    }

    @Test
    fun `a Spark's guide shows the Spark sentence and never the Habit one`() {
        show(Stakes.EASY)
        compose.onNodeWithText(Stakes.EASY_RULE).assertExists()
        compose.onNodeWithText(Stakes.STAKED_RULE).assertDoesNotExist()
    }

    @Test
    fun `a Habit's guide never shows the Spark sentence`() {
        show(Stakes.STAKED)
        compose.onNodeWithText(Stakes.EASY_RULE).assertDoesNotExist()
    }

    @Test
    fun `the Daily 25's guide shows counted as the Spark, no level sentence`() {
        show(Stakes.DAILY)
        compose.onNodeWithText("counted as the Spark").assertExists()
        compose.onNodeWithText(Stakes.EASY_RULE).assertDoesNotExist()
        compose.onNodeWithText(Stakes.STAKED_RULE).assertDoesNotExist()
    }

    @Test
    fun `the 45 degree card is ticked once the camera sees the phone there`() {
        show(Stakes.STAKED, detected = CameraView.OBLIQUE)
        compose.onNodeWithText("At 45° ✓").assertExists()
    }

    @Test
    fun `the body page opens with the whole-body and knees lines before the position and the outline`() {
        show(Stakes.STAKED)
        page(2)

        compose.onNodeWithText(wholeBody).assertExists()
        compose.onNodeWithText(knees).assertExists()
        compose.onNodeWithText(turn).assertExists()
        val order = listOf(wholeBody, knees, position, outline).map(::top)
        assertEquals(order.sorted(), order, "whole body, knees, position, outline - FR-015's order")
    }

    @Test
    fun `a Habit's body page asks for the toes and refuses the knees and the floor`() {
        show(Stakes.STAKED)
        page(2)
        compose.onNodeWithText("On your toes, down to just above the floor. Knee push-ups don't count.").assertExists()
    }

    @Test
    fun `a Spark's body page says the knees count in a Spark`() {
        show(Stakes.EASY)
        page(2)
        compose.onNodeWithText("Aim for your toes — knees still count in a Spark.").assertExists()
    }

    @Test
    fun `the Daily 25's body page says both count for the Daily 25`() {
        show(Stakes.DAILY)
        page(2)
        compose.onNodeWithText("Aim for your toes — knees still count in the Daily 25.").assertExists()
    }

    @Test
    fun `the start page has the four lines`() {
        show(Stakes.STAKED)
        page(3)

        val lines = listOf(
            "Hold the top for a second. A ring fills, then “three, two, one, go”.",
            "Nothing to tap. Each rep beeps; every 10 bank for good.",
            "All the way down and up — short reps are skipped.",
            "Stop any time with ✕ in the corner.",
        )
        lines.forEach { compose.onNodeWithText(it).assertExists() }
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
        compose.onNodeWithText(wholeBody).assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText(fromThere).assertExists()
    }
}
