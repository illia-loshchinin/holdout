package holdout.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.SEEKER
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * One calm devnet status bar (001 FR-069h, 2026-10-13/14): a soft warm dot, "{network} isn't answering"
 * ("still isn't answering" after a failed retry), one line for the screen, and a text Retry that reads
 * "Checking…" and cannot be pressed while it checks. No red text; a polite live region.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = SEEKER)
class DevnetBarTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private var retries = 0

    private fun show(still: Boolean = false, checking: Boolean = false, network: String = "Devnet", sub: String = "The Daily 25 still works.") {
        compose.setContent {
            HoldoutTheme { DevnetBar(network = network, still = still, checking = checking, sub = sub, onRetry = { retries++ }) }
        }
        compose.waitForIdle()
    }

    private fun colourOf(text: String): Color {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        return result.first().layoutInput.style.color
    }

    @Test
    fun `the line, its sub line and Retry`() {
        show()
        compose.onNodeWithText("Devnet isn't answering").assertIsDisplayed()
        compose.onNodeWithText("The Daily 25 still works.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `after a failed retry it still isn't answering`() {
        show(still = true)
        compose.onNodeWithText("Devnet still isn't answering").assertIsDisplayed()
        compose.onNodeWithText("Devnet isn't answering").assertDoesNotExist()
    }

    @Test
    fun `a local build keeps the local validator's name`() {
        show(network = "The local validator")
        compose.onNodeWithText("The local validator isn't answering").assertIsDisplayed()
    }

    @Test
    fun `Retry asks once`() {
        show()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `while checking Retry reads Checking and cannot be pressed`() {
        show(checking = true)
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Checking…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Checking…").performClick()
        assertEquals(0, retries)
    }

    @Test
    fun `the bar is one polite live region`() {
        show()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertCountEquals(1)
    }

    @Test
    fun `no text is the warning colour - the line is primary, the sub tertiary, Retry accent`() {
        show()
        listOf("Devnet isn't answering", "The Daily 25 still works.", "Retry").forEach { assertNotEquals(HoldoutColors.Warning, colourOf(it), it) }
        assertEquals(HoldoutColors.TextPrimary, colourOf("Devnet isn't answering"))
        assertEquals(HoldoutColors.TextTertiary, colourOf("The Daily 25 still works."))
        assertEquals(HoldoutColors.Accent, colourOf("Retry"))
    }

    @Test
    fun `Retry has a 48 dp touch target`() {
        show()
        val node = compose.onNodeWithText("Retry", useUnmergedTree = false)
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue(bounds.height.value >= 48f, "height ${bounds.height}")
    }
}
