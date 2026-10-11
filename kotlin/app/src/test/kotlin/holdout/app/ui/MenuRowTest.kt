package holdout.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The outlined row that opens something (Me: About, How Holdout works): a title, a quiet caption, a chevron. */
@RunWith(RobolectricTestRunner::class)
class MenuRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `it shows its title and caption and reports a tap`() {
        var taps = 0
        compose.setContent { HoldoutTheme { MenuRow("About Holdout", "devnet · test token", onClick = { taps++ }) } }

        compose.onNodeWithText("About Holdout").assertIsDisplayed()
        compose.onNodeWithText("devnet · test token").assertIsDisplayed()
        compose.onNodeWithText("About Holdout").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `it is a touch target and reads as one control`() {
        compose.setContent { HoldoutTheme { MenuRow("How Holdout works", "4 cards", onClick = {}) } }

        compose.onNodeWithText("How Holdout works").assertHasClickAction()
        compose.onNodeWithText("How Holdout works").assertHeightIsAtLeast(Spacing.touchTarget)
    }

    @Test
    fun `the caption is optional`() {
        compose.setContent { HoldoutTheme { MenuRow("How Holdout works", onClick = {}) } }

        compose.onNodeWithText("How Holdout works").assertIsDisplayed()
    }
}
