package holdout.app.tour

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.SEEKER
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.welcome.WelcomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The welcome and every tour card fit one Seeker screen with nothing to scroll
 * (owner, 12 Oct; FR-067, FR-067a): the app's area on a Seeker is 400 × 829 dp
 * (1200 × 2670 px at 480 dpi, less the 110 px status bar and the 72 px
 * navigation bar), the size of the canvas boards. Native graphics, so text is
 * measured with real fonts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SEEKER)
class TourFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    private fun overflowPx(what: String): Float {
        compose.waitForIdle()
        val scrolls = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
        assertTrue(scrolls.isNotEmpty(), "$what has no scroll container to check")
        return scrolls.maxOf { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
    }

    @Test
    fun `no card scrolls on a Seeker`() {
        compose.setContent { HoldoutTheme { TourScreen(onDone = {}) } }
        val overflow = (1..6).associateWith { card ->
            val px = overflowPx("card $card")
            if (card < 6) compose.onNodeWithText("Next").performClick()
            px
        }
        assertEquals(emptyMap(), overflow.filterValues { it > 0f }, "cards that scroll, by how many px at 480 dpi")
    }

    @Test
    fun `the welcome does not scroll on a Seeker, with a Seeker ID or without, and a long one`() {
        var id by mutableStateOf<String?>("nova.skr")
        compose.setContent { HoldoutTheme { WelcomeScreen(wallet = WALLET, seekerId = id, onStart = {}, onSkip = {}) } }
        val overflow = linkedMapOf<String, Float>()
        overflow["named"] = overflowPx("the welcome")
        compose.runOnUiThread { id = "a-rather-long-seeker-name.skr" }
        overflow["long name"] = overflowPx("the welcome with a long name")
        compose.runOnUiThread { id = null }
        overflow["no name"] = overflowPx("the welcome with no name")
        assertEquals(emptyMap(), overflow.filterValues { it > 0f }, "welcomes that scroll, by how many px at 480 dpi")
    }

    private companion object {
        const val WALLET = "7xQpAbCdEfGhJkLmNpQrStUvWxYz123456789ABCDEFG"
    }
}
