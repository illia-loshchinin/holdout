package holdout.app.sitting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Board 18, the countdown: a ring, the digit, "get ready · stay at the top". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class CountdownScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var stops = 0

    private fun show(remaining: Int, dayNumber: Int? = 12, timeLeft: Long? = 4 * 3_600L + 6 * 60) {
        stops = 0
        compose.setContent {
            HoldoutTheme {
                CountdownContent(
                    ui = CountdownUi(
                        header = SittingHeader(SittingCopy.countingLabel(dayNumber, 25), timeLeft, holdoutDay = dayNumber != null),
                        remaining = remaining,
                        progress = remaining / 3f,
                    ),
                    onStop = { stops++ },
                )
            }
        }
    }

    @Test
    fun `three is shown first`() {
        show(3)
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun `then two`() {
        show(2)
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("3").assertDoesNotExist()
    }

    @Test
    fun `then one`() {
        show(1)
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun `the caption tells the member to stay at the top`() {
        show(3)
        compose.onNodeWithText("GET READY · STAY AT THE TOP").assertIsDisplayed()
    }

    @Test
    fun `the ring is named for the seconds left`() {
        show(2)
        compose.onNodeWithContentDescription("2 seconds to go").assertExists()
    }

    @Test
    fun `a Holdout names its day and its deadline`() {
        show(3)
        compose.onNodeWithText("DAY 12 · 25 REPS").assertIsDisplayed()
        compose.onNodeWithText("4h 06m to today's deadline").assertIsDisplayed()
    }

    @Test
    fun `the Daily 25 names itself and what is left today`() {
        show(3, dayNumber = null)
        compose.onNodeWithText("THE DAILY 25").assertIsDisplayed()
        compose.onNodeWithText("4h 06m left today").assertIsDisplayed()
    }

    @Test
    fun `the close circle stops the sitting`() {
        show(3)
        compose.onNodeWithContentDescription("Stop this sitting").performClick()
        assertEquals(1, stops)
    }
}
