package holdout.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** PagerDots, ChecklistRow and ProgressRing. */
@RunWith(RobolectricTestRunner::class)
class ProgressTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the pager dots say which card of how many`() {
        compose.setContent { HoldoutTheme { PagerDots(count = 4, current = 1) } }

        compose.onNodeWithContentDescription("Card 2 of 4").assertExists()
    }

    @Test
    fun `the pager dots follow the current card`() {
        compose.setContent { HoldoutTheme { PagerDots(count = 4, current = 3) } }

        compose.onNodeWithContentDescription("Card 4 of 4").assertExists()
    }

    @Test
    fun `a done step shows its text and says done`() {
        compose.setContent { HoldoutTheme { ChecklistRow("Approved in your wallet", StepState.Done) } }

        compose.onNodeWithText("Approved in your wallet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Approved in your wallet, done").assertExists()
    }

    @Test
    fun `the active step says it is in progress`() {
        compose.setContent { HoldoutTheme { ChecklistRow("Confirming your place", StepState.Active) } }

        compose.onNodeWithContentDescription("Confirming your place, in progress").assertExists()
    }

    @Test
    fun `a pending step says not yet`() {
        compose.setContent { HoldoutTheme { ChecklistRow("In the lobby", StepState.Pending) } }

        compose.onNodeWithText("In the lobby").assertIsDisplayed()
        compose.onNodeWithContentDescription("In the lobby, not yet").assertExists()
    }

    @Test
    fun `the confirming ring is named for what it is waiting on`() {
        compose.setContent { HoldoutTheme { ProgressRing("Taking your place") } }

        compose.onNodeWithContentDescription("Taking your place").assertExists()
    }

    @Test
    fun `the ring closes with a tick once it is done`() {
        compose.setContent { HoldoutTheme { ProgressRing("Place secured", done = true) } }

        compose.onNodeWithContentDescription("Place secured").assertExists()
    }
}
