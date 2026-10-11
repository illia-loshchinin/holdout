package holdout.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.about.AboutScreen
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Proves Compose UI tests run on the JVM with no device: a stateless screen
 * inside [HoldoutTheme], driven by `createComposeRule()` under Robolectric.
 * Copy this shape for screen tests; it touches no MMKV and no camera.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeRobolectricSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aboutScreenShowsItsTitleAndFiresTheTourCallback() {
        var tours = 0
        compose.setContent {
            HoldoutTheme {
                AboutScreen(onTour = { tours++ }, onBack = {})
            }
        }

        compose.onNodeWithText("About Holdout").assertExists()
        compose.onNodeWithText("The three-card tour again").performClick()

        assertEquals(1, tours)
    }
}
