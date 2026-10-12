package holdout.app.sitting

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Board 15, camera permission: asking, and denied. S1, N4. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class CameraPermissionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var allowed = 0
    private var settings = 0
    private var stopped = 0

    private fun show(denied: Boolean, label: String = SittingCopy.countingLabel(12, 25)) {
        compose.setContent {
            HoldoutTheme {
                CameraPermissionScreen(
                    label = label,
                    denied = denied,
                    onAllow = { allowed++ },
                    onOpenSettings = { settings++ },
                    onStop = { stopped++ },
                )
            }
        }
    }

    @Test
    fun `asking says why the camera, and offers Allow camera`() {
        show(denied = false)

        compose.onNodeWithText("The camera counts\nyour reps").assertExists()
        compose.onNodeWithText("Nothing is recorded or uploaded.").assertExists()
        compose.onNodeWithText("Allow camera").performClick()

        assertEquals(1, allowed)
        assertEquals(0, settings)
    }

    @Test
    fun `denied says it is off and sends the member to Settings`() {
        show(denied = true)

        compose.onNodeWithText("Camera access is off").assertExists()
        compose.onNodeWithText("Turn it on in Settings › Apps › Holdout › Permissions.").assertExists()
        compose.onNodeWithText("Allow camera").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()

        assertEquals(1, settings)
        assertEquals(0, allowed)
    }

    @Test
    fun `the label names the day, or the Daily 25`() {
        show(denied = false, label = SittingCopy.countingLabel(12, 25))
        compose.onNodeWithText("DAY 12 · 25 REPS").assertExists()
    }

    @Test
    fun `the Daily 25 is named by its name`() {
        show(denied = false, label = SittingCopy.countingLabel(null, 25))
        compose.onNodeWithText("THE DAILY 25").assertExists()
    }

    @Test
    fun `the close circle stops the sitting`() {
        show(denied = false)

        compose.onNodeWithContentDescription("Stop this sitting").performClick()

        assertEquals(1, stopped)
    }
}
