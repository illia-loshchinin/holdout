package holdout.app.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every tour card fits one Seeker screen with nothing to scroll (owner, 12 Oct):
 * the app's area on a Seeker is 400 × 829 dp (1200 × 2670 px at 480 dpi, less
 * the 110 px status bar and the 72 px navigation bar), the size of the canvas
 * boards. Native graphics, so text is measured with real fonts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SEEKER)
class TourFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    @Test
    fun `no card scrolls on a Seeker`() {
        compose.setContent { HoldoutTheme { TourScreen(onDone = {}) } }
        val overflow = (1..5).associateWith { card ->
            compose.waitForIdle()
            val scrolls = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
            assertTrue(scrolls.isNotEmpty(), "card $card has no scroll container to check")
            val px = scrolls.maxOf { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
            if (card < 5) compose.onNodeWithText("Next").performClick()
            px
        }
        assertEquals(emptyMap(), overflow.filterValues { it > 0f }, "cards that scroll, by how many px at 480 dpi")
    }
}

/** The app's area on a Seeker: 400 × 829 dp at 480 dpi. */
internal const val SEEKER = "w400dp-h829dp-xxhdpi"
