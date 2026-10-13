package holdout.app.welcome

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.sitting.PHONE
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The welcome (board Welcome; FR-067a): between a first connect and the tour, "Welcome," over
 * the Seeker ID — or "Welcome to Holdout." and "You're in." — the short address and
 * "connected", the brief on offer, *Start the brief* and *Skip*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class WelcomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var starts = 0
    private var skips = 0

    private fun show(seekerId: String? = "nova.skr") {
        starts = 0
        skips = 0
        compose.setContent { HoldoutTheme { WelcomeScreen(wallet = WALLET, seekerId = seekerId, onStart = { starts++ }, onSkip = { skips++ }) } }
    }

    @Test
    fun `with a Seeker ID it says Welcome over the name`() {
        show()

        compose.onNodeWithText("Welcome,").assertIsDisplayed()
        compose.onNodeWithText("nova.skr").assertIsDisplayed()
        compose.onNodeWithText("Welcome to Holdout.").assertDoesNotExist()
        compose.onNodeWithText("You're in.").assertDoesNotExist()
    }

    @Test
    fun `with no name it says Welcome to Holdout and You're in`() {
        show(seekerId = null)

        compose.onNodeWithText("Welcome to Holdout.").assertIsDisplayed()
        compose.onNodeWithText("You're in.").assertIsDisplayed()
        compose.onNodeWithText("Welcome,").assertDoesNotExist()
        compose.onNodeWithText("nova.skr").assertDoesNotExist()
    }

    @Test
    fun `the short address and connected sit under the greeting, with a name`() {
        show()

        compose.onNodeWithText("7xQp…DEFG", substring = true).assertIsDisplayed()
        compose.onNodeWithText("connected", substring = true).assertIsDisplayed()
        compose.onNodeWithText(WALLET, substring = true).assertDoesNotExist()
    }

    @Test
    fun `the short address and connected sit under the greeting, with no name too`() {
        show(seekerId = null)

        compose.onNodeWithText("7xQp…DEFG", substring = true).assertIsDisplayed()
        compose.onNodeWithText("connected", substring = true).assertIsDisplayed()
    }

    @Test
    fun `it offers the brief - what it covers and how long it takes`() {
        show()

        compose.onNodeWithText("First, a quick brief.").assertIsDisplayed()
        compose.onNodeWithText("The Daily 25, the two kinds of Holdout and their rules, where a lost stake goes, and how reps are counted.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Six cards, about a minute.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Test network · test token", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `Start the brief and Skip each do their own thing once`() {
        show()

        compose.onNodeWithText("Start the brief").performClick()
        assertEquals(1, starts)
        assertEquals(0, skips)
        compose.onNodeWithText("Skip").performClick()
        assertEquals(1, starts)
        assertEquals(1, skips)
    }

    @Test
    fun `nothing is for judges or says earn, cohort, league or a date`() {
        show()

        listOf("judge", "Holdout Lab", "earn", "cohort", "league", "≈").forEach {
            compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    private companion object {
        const val WALLET = "7xQpAbCdEfGhJkLmNpQrStUvWxYz123456789ABCDEFG"
    }
}
