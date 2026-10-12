package holdout.app.ui

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

/** StatusChip, StatBlock, QuietAction and NoticeCard: what they show, how they are named, what they call back. */
@RunWith(RobolectricTestRunner::class)
class StatusAndStatTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a status chip shows its word in capitals and names its meaning`() {
        compose.setContent { HoldoutTheme { StatusChip("running", tone = ChipTone.Success) } }

        compose.onNodeWithText("RUNNING").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: running").assertExists()
    }

    @Test
    fun `a scripted chip carries the diamond and says scripted member`() {
        compose.setContent { HoldoutTheme { ScriptedChip() } }

        compose.onNodeWithText("◇ SCRIPTED").assertIsDisplayed()
        compose.onNodeWithContentDescription("Scripted member").assertExists()
    }

    @Test
    fun `every tone is still a word`() {
        compose.setContent {
            HoldoutTheme {
                androidx.compose.foundation.layout.Column {
                    StatusChip("devnet")
                    StatusChip("open", tone = ChipTone.Accent)
                    StatusChip("forfeited", tone = ChipTone.Warning)
                }
            }
        }

        listOf("DEVNET", "OPEN", "FORFEITED").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `a stat block shows the number with its caption and drops the denominator to tertiary text in the same line`() {
        compose.setContent { HoldoutTheme { StatBlock(value = "189", of = "312", caption = "still standing", label = "Habit · 21-day") } }

        compose.onNodeWithText("189 / 312").assertIsDisplayed()
        compose.onNodeWithText("STILL STANDING").assertIsDisplayed()
        compose.onNodeWithText("HABIT · 21-DAY").assertIsDisplayed()
        compose.onNodeWithContentDescription("Habit · 21-day, 189 of 312, still standing").assertExists()
    }

    @Test
    fun `a stat block without a ratio shows just the number`() {
        compose.setContent { HoldoutTheme { StatBlock(value = "25", caption = "push-ups", display = true) } }

        compose.onNodeWithText("25").assertIsDisplayed()
        compose.onNodeWithText("PUSH-UPS").assertIsDisplayed()
    }

    @Test
    fun `a quiet action is at least 48 dp tall and fires on click`() {
        var clicks = 0
        compose.setContent { HoldoutTheme { QuietAction("No wallet yet?", onClick = { clicks++ }) } }

        compose.onNode(hasClickAction()).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("No wallet yet?").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled quiet action does not fire`() {
        var clicks = 0
        compose.setContent { HoldoutTheme { QuietAction("Leave", onClick = { clicks++ }, warning = true, enabled = false) } }

        compose.onNodeWithText("Leave").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `a notice card shows its title and sentence and a quiet way out`() {
        var dismissed = 0
        compose.setContent {
            HoldoutTheme {
                NoticeCard(
                    title = "Joining closed",
                    text = "Your transaction landed too late. Nothing was taken.",
                    warning = true,
                    actionText = "OK",
                    onAction = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("Joining closed").assertIsDisplayed()
        compose.onNodeWithText("Your transaction landed too late. Nothing was taken.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `a notice card with no action has nothing to press`() {
        compose.setContent { HoldoutTheme { NoticeCard(title = "Not saved", text = "The phone may be out of space.") } }

        compose.onNodeWithText("Not saved").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
