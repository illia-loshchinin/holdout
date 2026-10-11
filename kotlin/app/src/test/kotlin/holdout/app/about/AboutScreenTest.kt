package holdout.app.about

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * X1 About Holdout (board About; spec 004 FR-012/FR-013, FR-015; rulings
 * "Onboarding / Me"): the test network, the two mainnet reads, the scripted
 * members, the tour again, Disconnect. Nothing for judges.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var tours = 0
    private var backs = 0
    private var disconnects = 0

    private fun show(onTour: (() -> Unit)? = { tours++ }, onDisconnect: (() -> Unit)? = { disconnects++ }) {
        tours = 0
        backs = 0
        disconnects = 0
        compose.setContent { HoldoutTheme { AboutScreen(onTour = onTour, onBack = { backs++ }, onDisconnect = onDisconnect) } }
    }

    @Test
    fun `it says what is real and what is read from mainnet`() {
        show()

        compose.onNodeWithText("About Holdout").assertIsDisplayed()
        compose.onNodeWithText("Test network, test token. Nothing here moves real value.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Read from mainnet, never written to it: Seeker ID names, and whether you hold a Seeker Genesis Token.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `scripted members get their chip and one sentence`() {
        show()

        compose.onNodeWithContentDescription("Scripted member").assertIsDisplayed()
        compose.onNodeWithText("Some members in these Holdouts are scripted. Real wallets, same rules, labelled SCRIPTED.").assertIsDisplayed()
    }

    @Test
    fun `the tour is four cards and opens from a row`() {
        show()

        compose.onNodeWithText("How Holdout works").performClick()
        assertEquals(1, tours)
        compose.onNodeWithText("4 cards").assertIsDisplayed()
        compose.onNodeWithText("The three-card tour again").assertDoesNotExist()
    }

    @Test
    fun `disconnect is the one place to leave and says what it is for`() {
        show()

        compose.onNodeWithText("Disconnect wallet").performClick()
        assertEquals(1, disconnects)
        compose.onNodeWithText("Switched networks? Disconnect and connect again.").assertIsDisplayed()
    }

    @Test
    fun `back goes back and the header is named about`() {
        show()

        compose.onNodeWithText("About", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `without the callbacks the tour row and disconnect are not offered`() {
        show(onTour = null, onDisconnect = null)

        compose.onNodeWithText("How Holdout works").assertDoesNotExist()
        compose.onNodeWithText("Disconnect wallet").assertDoesNotExist()
    }

    @Test
    fun `nothing is addressed to judges`() {
        show()

        listOf("judge", "hackathon", "weakness", "Holdout Lab").forEach {
            compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
