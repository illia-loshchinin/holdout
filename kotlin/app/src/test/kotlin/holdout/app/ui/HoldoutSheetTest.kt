package holdout.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The sheet wraps its content up to a ceiling, rather than always standing at
 * 92 % of the screen (wiring item 9): the Seeker gate's short states leave no
 * large empty sheet, and a long form still stops below the top of the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h800dp")
class HoldoutSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val noAnimations = AnimationsOff()

    private fun sheetWith(content: Int) {
        compose.setContent {
            HoldoutTheme {
                Box(Modifier.fillMaxSize().testTag("screen")) {
                    HoldoutSheet(onDismiss = {}, modifier = Modifier.testTag("sheet")) {
                        Box(Modifier.height(content.dp)) { Text("content") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `short content gives a short sheet`() {
        sheetWith(content = 120)
        val screen = compose.onNodeWithTag("screen").getUnclippedBoundsInRoot().height
        val sheet = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().height
        assertTrue(sheet < screen / 2, "sheet $sheet of screen $screen")
        compose.onNodeWithTag("sheet").assertHeightIsAtLeast(120.dp)
    }

    @Test
    fun `tall content stops at the ceiling below the top of the screen`() {
        sheetWith(content = 3_000)
        val screen = compose.onNodeWithTag("screen").getUnclippedBoundsInRoot().height
        val sheet = compose.onNodeWithTag("sheet").getUnclippedBoundsInRoot().height
        assertTrue(sheet <= screen * 0.92f + 0.5.dp, "sheet $sheet of screen $screen")
        assertTrue(sheet >= screen * 0.9f, "sheet $sheet of screen $screen")
    }
}
