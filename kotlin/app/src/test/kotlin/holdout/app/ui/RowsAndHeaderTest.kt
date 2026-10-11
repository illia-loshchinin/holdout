package holdout.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** RosterRow, KeyValueRow and ScreenHeader. */
@RunWith(RobolectricTestRunner::class)
class RowsAndHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val wallet = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

    @Test
    fun `a standing member with a Seeker ID shows the name, the days and a tick`() {
        compose.setContent {
            HoldoutTheme {
                CompositionLocalProvider(LocalSeekerNames provides mapOf(wallet to "mira.skr")) {
                    RosterRow(wallet, isYou = false, standing = true, days = 12, scripted = false)
                }
            }
        }

        compose.onNodeWithText("12 days ✓", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("mira.skr, still standing, 12 days").assertExists()
    }

    @Test
    fun `a member with no Seeker ID shows the shortened address and is named the same way`() {
        compose.setContent { HoldoutTheme { RosterRow(wallet, isYou = false, standing = true, days = 3, scripted = false) } }

        compose.onNodeWithContentDescription("7xKX…gAsU, still standing, 3 days").assertExists()
    }

    @Test
    fun `the connected member's row ends in you`() {
        compose.setContent { HoldoutTheme { RosterRow(wallet, isYou = true, standing = true, days = 1, scripted = false) } }

        compose.onNodeWithContentDescription("7xKX…gAsU · you, still standing, 1 day").assertExists()
    }

    @Test
    fun `a member who is out shows how long they lasted and is named out`() {
        compose.setContent { HoldoutTheme { RosterRow(wallet, isYou = false, standing = false, days = 9, scripted = false) } }

        compose.onNodeWithText("lasted 9 days", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("7xKX…gAsU, out, lasted 9 days").assertExists()
        compose.onNodeWithText("12 days ✓", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a scripted member carries the scripted chip`() {
        compose.setContent { HoldoutTheme { RosterRow(wallet, isYou = false, standing = true, days = 5, scripted = true) } }

        compose.onNodeWithText("◇ SCRIPTED", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("7xKX…gAsU, scripted, still standing, 5 days").assertExists()
    }

    @Test
    fun `a roster row never shows money`() {
        compose.setContent { HoldoutTheme { RosterRow(wallet, isYou = false, standing = true, days = 5, scripted = false) } }

        compose.onNodeWithText("SKR", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a roster row is at least a button tall`() {
        compose.setContent { HoldoutTheme { RosterRow(wallet, isYou = false, standing = true, days = 5, scripted = false) } }

        compose.onNodeWithContentDescription("7xKX…gAsU, still standing, 5 days").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `a key value row reads label then value`() {
        compose.setContent { HoldoutTheme { KeyValueRow("Stake back", "1,250 SKR", success = true) } }

        compose.onNodeWithText("Stake back").assertIsDisplayed()
        compose.onNodeWithText("1,250 SKR").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stake back, 1,250 SKR").assertExists()
    }

    @Test
    fun `a header shows its label in capitals and a named back button`() {
        var backs = 0
        compose.setContent { HoldoutTheme { ScreenHeader("Spark · 7-day · starts in 2d 4h", onBack = { backs++ }) } }

        compose.onNodeWithText("SPARK · 7-DAY · STARTS IN 2D 4H").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `the back button is at least 48 dp`() {
        compose.setContent { HoldoutTheme { ScreenHeader("Roster", onBack = {}) } }

        compose.onNodeWithContentDescription("Back").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `a header can name its back button for the screen it goes to`() {
        compose.setContent { HoldoutTheme { ScreenHeader("Lobby", onBack = {}, backDescription = "Back to Holdouts") } }

        compose.onNodeWithContentDescription("Back to Holdouts").assertExists()
    }

    @Test
    fun `a header can end in a status chip`() {
        compose.setContent { HoldoutTheme { ScreenHeader("Habit · 21-day · day 12 of 21", onBack = {}, trailing = { StatusChip("running", tone = ChipTone.Success) }) } }

        compose.onNodeWithText("RUNNING").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: running").assertExists()
    }

    @Test
    fun `the close variant has a named close button and no back button`() {
        var closes = 0
        compose.setContent { HoldoutTheme { ScreenHeader("Stake", onClose = { closes++ }) } }

        compose.onNodeWithContentDescription("Close").performClick()

        assertEquals(1, closes)
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `a header with nothing to go back to has no buttons`() {
        compose.setContent { HoldoutTheme { ScreenHeader("Me") } }

        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
