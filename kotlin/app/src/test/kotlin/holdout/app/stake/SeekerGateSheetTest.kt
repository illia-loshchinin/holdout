package holdout.app.stake

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The Seeker gate's three states, in the same sheet as the stake confirm (board 4; FR-003b–FR-003d, ES-0c, ES-0d). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class SeekerGateSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private var store = 0
    private var retries = 0
    private var notNow = 0

    private fun show(status: SeekerStatus) {
        compose.setContent { HoldoutTheme { SeekerGateSheet(status, onGetSeeker = { store++ }, onRetry = { retries++ }, onNotNow = { notNow++ }) } }
    }

    @Test
    fun `checking - the label and the wait`() {
        show(SeekerStatus.Checking)
        compose.onNodeWithText("SEEKER ONLY").assertIsDisplayed()
        compose.onNodeWithText("Checking your Seeker…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Checking your Seeker").assertExists()
        compose.onNodeWithText("Get a Seeker").assertDoesNotExist()
    }

    @Test
    fun `not a Seeker - Holdouts are for Seeker owners, the Daily 25 stays free, Get a Seeker and Not now`() {
        show(SeekerStatus.NotSeeker)
        compose.onNodeWithText("Holdouts are for Seeker owners").assertIsDisplayed()
        compose.onNodeWithText("The Daily 25 stays free, and you can follow every Holdout.").assertIsDisplayed()
        compose.onNodeWithText("Get a Seeker").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, store)
        assertEquals(1, notNow)
    }

    @Test
    fun `couldn't check - the spec's words, never the Seeker-exclusive notice, and Try again`() {
        show(SeekerStatus.CouldNotCheck)
        compose.onNodeWithText("We couldn't check your Seeker — try again.").assertIsDisplayed()
        compose.onNodeWithText("Holdouts are for Seeker owners").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `there is no Back button inside the sheet`() {
        show(SeekerStatus.NotSeeker)
        compose.onNodeWithText("← Back").assertDoesNotExist()
    }
}
