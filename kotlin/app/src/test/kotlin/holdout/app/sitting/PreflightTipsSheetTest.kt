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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutTheme
import holdout.repcounter.Coach
import holdout.repcounter.PreflightCheck
import holdout.repcounter.PreflightReport
import holdout.repcounter.PreflightStatus
import holdout.repcounter.Stakes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pre-flight tip sheet (Gap 1, screen map S3): the button under the fix sentence, the sheet it opens
 * (every check in the pills' order, the red ones marked), and how it closes without stopping the sitting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class PreflightTipsSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    private var tips = 0
    private var setups = 0
    private var closes = 0
    private var stops = 0

    private val labels = listOf("Lighting", "Whole body in frame", "Knees visible", "Camera view", "Background still")

    private fun report(vararg failing: PreflightCheck) = PreflightReport(PreflightCheck.entries.map { PreflightStatus(it, passing = it !in failing) })

    private fun ui(offered: Boolean, vararg failing: PreflightCheck) = preflightUi(
        state = SittingViewModel.UiState(preflight = report(*failing), coach = Coach.TURN_45, tipsOffered = offered),
        header = SittingHeader("x", null, true),
        stakes = Stakes.STAKED,
    )

    private fun showScreen(ui: PreflightUi) {
        compose.setContent {
            HoldoutTheme { PreflightContent(ui, onStop = { stops++ }, onSetup = { setups++ }, onTips = { tips++ }) }
        }
    }

    /** The sitting's shape: its own Back handler first, the screen under it, the sheet (while open) composed after both. */
    private fun showSheet(vararg failing: PreflightCheck) {
        val rows = ui(true, *failing).tips
        compose.setContent {
            HoldoutTheme {
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    BackHandler { stops++ }
                    if (open) {
                        PreflightTipsSheet(
                            tips = rows,
                            onSetup = {
                                open = false
                                setups++
                            },
                            onClose = {
                                open = false
                                closes++
                            },
                        )
                    }
                }
            }
        }
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    // ---- the button ---------------------------------------------------------------------------

    @Test
    fun `the button is hidden before the offer`() {
        showScreen(ui(offered = false, PreflightCheck.CAMERA_ANGLE))
        compose.onNodeWithText(TIPS_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `the button is shown once offered, and a tap opens the tips`() {
        showScreen(ui(offered = true, PreflightCheck.CAMERA_ANGLE))
        compose.onNodeWithText(TIPS_BUTTON).assertIsDisplayed().performClick()
        assertEquals(1, tips)
    }

    @Test
    fun `the button says Still not green? Tips, word for word`() {
        assertEquals("Still not green? Tips", TIPS_BUTTON)
    }

    @Test
    fun `the fix sentence is still there beside the button`() {
        showScreen(ui(offered = true, PreflightCheck.CAMERA_ANGLE))
        compose.onNodeWithText(PreflightCheck.CAMERA_ANGLE.fix).assertIsDisplayed()
        compose.onNodeWithText("How to set up").assertIsDisplayed()
    }

    // ---- the rows -----------------------------------------------------------------------------

    @Test
    fun `the rows are the five checks in pill order, each with its fix sentence`() {
        val rows = ui(true, PreflightCheck.CAMERA_ANGLE).tips
        assertEquals(labels, rows.map { it.label })
        assertEquals(PreflightCheck.entries.map { it.fix }, rows.map { it.fix })
        assertEquals(listOf(false, false, false, true, false), rows.map { it.red })
    }

    @Test
    fun `a red check's row says the sentence S3 would show, a more specific one included`() {
        val specific = "Step back a little: your feet are out of the picture"
        val state = SittingViewModel.UiState(
            preflight = PreflightReport(
                PreflightCheck.entries.map {
                    if (it == PreflightCheck.WHOLE_BODY) PreflightStatus(it, passing = false, specificFix = specific) else PreflightStatus(it, passing = true)
                },
            ),
        )
        val rows = preflightUi(state, SittingHeader("x", null, true), Stakes.STAKED).tips
        assertEquals(specific, rows[1].fix)
        assertEquals(PreflightCheck.LIGHTING.fix, rows[0].fix)
    }

    @Test
    fun `the knees the position cannot place mark the Knees visible row, with the sentence the screen shows`() {
        val fix = "The phone cannot make out your knees"
        val state = SittingViewModel.UiState(preflight = report(), coach = Coach.KNEES_UNCLEAR, holdFix = fix)
        val rows = preflightUi(state, SittingHeader("x", null, true), Stakes.STAKED).tips
        assertEquals(listOf(false, false, true, false, false), rows.map { it.red })
        assertEquals(fix, rows[2].fix)
    }

    @Test
    fun `no report yet means no rows and no offer is drawn`() {
        val ui = preflightUi(SittingViewModel.UiState(tipsOffered = true), SittingHeader("x", null, true), Stakes.STAKED)
        assertTrue(ui.tips.isEmpty())
    }

    // ---- the sheet ----------------------------------------------------------------------------

    @Test
    fun `the sheet has its title and lists every check in the pills' order`() {
        showSheet(PreflightCheck.CAMERA_ANGLE)
        compose.onNodeWithText("Getting the checks green").assertIsDisplayed()
        val tops = labels.map { compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        PreflightCheck.entries.forEach { compose.onNodeWithText(it.fix).assertIsDisplayed() }
    }

    @Test
    fun `a red check's row is marked Not green yet for a screen reader, a green one is not`() {
        showSheet(PreflightCheck.CAMERA_ANGLE, PreflightCheck.LIGHTING)
        compose.onAllMarked().let { marked ->
            assertEquals(2, marked.size)
            assertTrue(marked.all { it.contains("Not green yet") })
            assertTrue(marked.any { it.startsWith("Lighting") })
            assertTrue(marked.any { it.startsWith("Camera view") })
        }
    }

    @Test
    fun `Open the set-up guide hands over to the guide`() {
        showSheet(PreflightCheck.CAMERA_ANGLE)
        compose.onNodeWithText("Open the set-up guide").performClick()
        assertEquals(1, setups)
        assertEquals(0, closes)
    }

    @Test
    fun `Back to the checks closes the sheet`() {
        showSheet(PreflightCheck.CAMERA_ANGLE)
        compose.onNodeWithText("Back to the checks").performClick()
        assertEquals(1, closes)
        assertEquals(0, stops)
    }

    @Test
    fun `a tap outside the sheet closes it`() {
        showSheet(PreflightCheck.CAMERA_ANGLE)
        compose.onNodeWithContentDescription("Close the tips").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, closes)
    }

    @Test
    fun `Back with the sheet open closes it and does not stop the sitting`() {
        showSheet(PreflightCheck.CAMERA_ANGLE)
        back()
        compose.waitForIdle()
        assertEquals(1, closes)
        assertEquals(0, stops)
    }

    @Test
    fun `Back with the sheet closed reaches the sitting's own handler`() {
        showSheet(PreflightCheck.CAMERA_ANGLE)
        back()
        compose.waitForIdle()
        back()
        compose.waitForIdle()
        assertEquals(1, closes)
        assertEquals(1, stops)
    }

    /** The rows' accessible names that carry the mark: every merged description naming a check and "Not green yet". */
    private fun SemanticsNodeInteractionsProvider.onAllMarked(): List<String> = onAllNodes(hasContentDescription("Not green yet", substring = true)).fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        .filter { it.contains("Not green yet") }
}
