package holdout.app.sitting

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutTheme
import holdout.repcounter.Stakes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The set-up guide as a sheet over the sitting: closing it by the scrim, a drag or system Back keeps
 * a ticked "Don't show this again", and Back closes the guide before it reaches the sitting's own
 * Back (which stops the sitting, as the X does).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class SetupSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    private var closed: List<Boolean> = emptyList()
    private var stopped = 0

    /** The sitting's shape: its own Back handler first, the guide (while open) composed after it. */
    private fun show() {
        closed = emptyList()
        stopped = 0
        compose.setContent {
            HoldoutTheme {
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    BackHandler { stopped++ }
                    if (open) {
                        SetupSheet(
                            stakes = Stakes.STAKED,
                            detected = null,
                            onDone = {
                                open = false
                                closed = closed + it
                            },
                        )
                    }
                }
            }
        }
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `a tick on Don't show this again survives a scrim tap`() {
        show()
        compose.onNodeWithText("Don't show this again").performClick()
        compose.onNodeWithContentDescription("Close the set-up guide").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(true), closed)
    }

    @Test
    fun `a scrim tap without the tick closes without hiding the guide`() {
        show()
        compose.onNodeWithContentDescription("Close the set-up guide").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(false), closed)
    }

    @Test
    fun `Back with the guide open closes it, with the tick, and does not stop the sitting`() {
        show()
        compose.onNodeWithText("Don't show this again").performClick()
        back()
        compose.waitForIdle()
        assertEquals(listOf(true), closed)
        assertEquals(0, stopped)
    }

    @Test
    fun `Back with the guide closed reaches the sitting's own handler`() {
        show()
        back()
        compose.waitForIdle()
        back()
        compose.waitForIdle()
        assertEquals(listOf(false), closed)
        assertEquals(1, stopped)
    }
}
