package holdout.app.sitting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import holdout.repcounter.Coach
import holdout.repcounter.Guidance
import holdout.repcounter.GuideBox
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Board 17, pre-flight and hold: five pills (002 FR-024a), the coach, the ring, the rule sentence (C-D2, C-D16). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class PreflightScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var stops = 0
    private var setups = 0

    private val labels = listOf("Lighting", "Whole body in frame", "Knees visible", "Camera view", "Background still")

    private fun pills(failing: Set<String> = emptySet()) = labels.map { PreflightPill(it, it !in failing) }

    private fun holdout(
        pills: List<PreflightPill>? = pills(setOf("Camera view")),
        coach: String? = "Phone to 45°",
        sub: String? = "Move the phone so it can see all of you",
        timeLeft: Long? = 4 * 3_600L + 6 * 60,
        holdProgress: Float? = null,
        coachIsHold: Boolean = false,
        subIsWarning: Boolean = false,
        stakes: Stakes = Stakes.STAKED,
        dayNumber: Int? = 12,
    ) = PreflightUi(
        header = SittingHeader(SittingCopy.positionLabel(dayNumber), timeLeft, holdoutDay = dayNumber != null),
        pills = pills,
        coach = coach,
        coachIsHold = coachIsHold,
        sub = sub,
        subIsWarning = subIsWarning,
        holdProgress = holdProgress,
        rule = SittingCopy.ruleCaption(stakes),
    )

    private fun show(ui: PreflightUi) {
        stops = 0
        setups = 0
        compose.setContent { HoldoutTheme { PreflightContent(ui, onStop = { stops++ }, onSetup = { setups++ }) } }
    }

    @Test
    fun `five pills show - Knees visible among them - and each keeps its label`() {
        show(holdout())
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `a failing pill keeps its label and the fix is said under the coach word`() {
        show(holdout())

        compose.onNodeWithText("Camera view").assertIsDisplayed()
        compose.onNodeWithText("Phone to 45°").assertIsDisplayed()
        compose.onNodeWithText("Move the phone so it can see all of you").assertIsDisplayed()
    }

    @Test
    fun `no pill carries the camera view's name - the oblique suffix was a display bug`() {
        show(holdout(pills = pills()))
        compose.onNodeWithText("oblique", substring = true, ignoreCase = true).assertDoesNotExist()
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `the pills' accessible names say passing or needs fixing, not only their colour`() {
        show(holdout())
        compose.onNodeWithContentDescription("Lighting, ok").assertExists()
        compose.onNodeWithContentDescription("Camera view, needs fixing").assertExists()
    }

    @Test
    fun `the label and the time line name a Holdout day`() {
        show(holdout())
        compose.onNodeWithText("DAY 12 · GET INTO POSITION").assertIsDisplayed()
        compose.onNodeWithText("4h 06m to today's deadline").assertIsDisplayed()
    }

    @Test
    fun `the Daily 25 shows what is left today`() {
        show(holdout(dayNumber = null, stakes = Stakes.DAILY))
        compose.onNodeWithText("THE DAILY 25 · GET INTO POSITION").assertIsDisplayed()
        compose.onNodeWithText("4h 06m left today").assertIsDisplayed()
    }

    @Test
    fun `no time line while the chain is unread - Lab, or offline before the first read`() {
        show(holdout(timeLeft = null))
        compose.onNodeWithText("to today's deadline", substring = true).assertDoesNotExist()
        compose.onNodeWithText("left today", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the last ten minutes show the time alone - the sentence is Today's, not the sitting's`() {
        show(holdout(timeLeft = 9 * 60L + 30))
        compose.onNodeWithText("9m 30s to today's deadline").assertIsDisplayed()
        compose.onNodeWithText(SittingCopy.LAST_MINUTES).assertDoesNotExist()
    }

    @Test
    fun `earlier in the day the warning is not shown`() {
        show(holdout())
        compose.onNodeWithText(SittingCopy.LAST_MINUTES).assertDoesNotExist()
    }

    @Test
    fun `the Daily 25 never says unfinished reps will not count - its reps roll into the next day`() {
        show(holdout(dayNumber = null, stakes = Stakes.DAILY, timeLeft = 9 * 60L + 30))
        compose.onNodeWithText(SittingCopy.LAST_MINUTES).assertDoesNotExist()
    }

    @Test
    fun `a Habit sitting shows the Habit rule sentence`() {
        show(holdout(stakes = Stakes.STAKED))
        compose.onNodeWithText(Stakes.STAKED_RULE).assertExists()
        compose.onNodeWithText(Stakes.EASY_RULE).assertDoesNotExist()
    }

    @Test
    fun `a Spark sitting shows the Spark rule sentence`() {
        show(holdout(stakes = Stakes.EASY))
        compose.onNodeWithText(Stakes.EASY_RULE).assertExists()
        compose.onNodeWithText(Stakes.STAKED_RULE).assertDoesNotExist()
    }

    @Test
    fun `the Daily 25 says it is counted as the Spark`() {
        show(holdout(stakes = Stakes.DAILY, dayNumber = null))
        compose.onNodeWithText("counted as the Spark").assertExists()
    }

    @Test
    fun `holding shows the ring with Hold and its sentence, and every pill stays green`() {
        show(holdout(pills = pills(), coach = "Hold", coachIsHold = true, sub = null, holdProgress = 0.62f))

        compose.onNodeWithContentDescription("Hold, 62 percent").assertExists()
        compose.onNodeWithText("Hold…").assertIsDisplayed()
        compose.onNodeWithText("Hold the top. The countdown starts when the ring fills.").assertIsDisplayed()
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `blocked on the knees says so, with the sentence the position lacks`() {
        val fix = "The phone cannot make out your knees. Loose trousers hide them: wear something fitted, or roll them up"
        show(holdout(pills = pills(), coach = "Can't see knees", sub = fix, subIsWarning = true))

        compose.onNodeWithText("Can't see knees").assertIsDisplayed()
        compose.onNodeWithText(fix).assertIsDisplayed()
    }

    @Test
    fun `before the first frame the pills read Looking`() {
        // The loader turns for ever: the clock must not wait for it to settle.
        compose.mainClock.autoAdvance = false
        show(holdout(pills = null, coach = null, sub = null))
        compose.onNodeWithText("Looking…").assertIsDisplayed()
    }

    @Test
    fun `How to set up is always there and opens the guide`() {
        show(holdout(pills = pills(), coach = "Hold", coachIsHold = true, sub = null, holdProgress = 0.3f))
        compose.onNodeWithText("How to set up").performClick()
        assertEquals(1, setups)
    }

    @Test
    fun `the close circle stops the sitting`() {
        show(holdout())
        compose.onNodeWithContentDescription("Stop this sitting").performClick()
        assertEquals(1, stops)
    }

    // ---- the mapping from the view model's state ----------------------------------------------------

    private fun report(vararg failing: PreflightCheck, fixes: Map<PreflightCheck, String> = emptyMap()) = PreflightReport(
        PreflightCheck.entries.map { PreflightStatus(it, passing = it !in failing) },
    )

    @Test
    fun `before the phone stands, the page says only where it goes - no body fix under it`() {
        val ui = preflightUi(
            state = SittingViewModel.UiState(
                preflight = report(PreflightCheck.WHOLE_BODY, fixes = mapOf(PreflightCheck.WHOLE_BODY to "Step back so your whole body is visible")),
                coach = Coach.AIM_AT_FLOOR,
            ),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals("Place your phone", ui.coach)
        assertEquals(SittingCopy.PLACE_PHONE, ui.sub)
    }

    @Test
    fun `the five checks become the five pills in order, labels untouched`() {
        val ui = preflightUi(
            state = SittingViewModel.UiState(preflight = report(PreflightCheck.CAMERA_ANGLE), coach = Coach.TURN_45),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals(labels, ui.pills!!.map { it.label })
        assertEquals(listOf(true, true, true, false, true), ui.pills!!.map { it.passing })
    }

    @Test
    fun `the sentence under the coach is the first failing gating check's fix`() {
        val ui = preflightUi(
            state = SittingViewModel.UiState(preflight = report(PreflightCheck.CAMERA_ANGLE, PreflightCheck.BACKGROUND_STILL), coach = Coach.TURN_45),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals(PreflightCheck.CAMERA_ANGLE.fix, ui.sub)
        assertFalse(ui.subIsWarning)
    }

    @Test
    fun `with every gating pill green the background's advisory fix is said, in the warning colour`() {
        val ui = preflightUi(
            state = SittingViewModel.UiState(preflight = report(PreflightCheck.BACKGROUND_STILL), coach = Coach.HOLD),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals(PreflightCheck.BACKGROUND_STILL.fix, ui.sub)
        assertTrue(ui.subIsWarning)
    }

    @Test
    fun `the position's own fix leads when the pills are green and the hold is not filling`() {
        val fix = "Straighten your arms: the hold starts from the top of a push-up"
        val ui = preflightUi(
            state = SittingViewModel.UiState(preflight = report(), coach = Coach.ARMS_STRAIGHT, holdFix = fix),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals("Arms straight", ui.coach)
        assertEquals(fix, ui.sub)
        assertTrue(ui.subIsWarning)
    }

    @Test
    fun `an aligned member sees the ring filling, with Hold`() {
        val guidance = Guidance(
            horizonY = null,
            target = GuideBox(0.5f, 0.5f, 0.2f, 0.3f),
            bodyBox = null,
            fit = 1f,
            hint = null,
            aligned = true,
        )
        val ui = preflightUi(
            state = SittingViewModel.UiState(preflight = report(), coach = Coach.HOLD, guidance = guidance, readyProgress = 0.4f),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals(0.4f, ui.holdProgress)
        assertTrue(ui.coachIsHold)
        assertNull(ui.sub)
    }

    @Test
    fun `blocked on the knees marks the Knees visible pill, though the picture check passed`() {
        val ui = preflightUi(
            state = SittingViewModel.UiState(preflight = report(), coach = Coach.KNEES_UNCLEAR, holdFix = "The phone cannot make out your knees"),
            header = SittingHeader("x", null, true),
            stakes = Stakes.STAKED,
        )
        assertEquals(listOf(true, true, false, true, true), ui.pills!!.map { it.passing })
    }

    @Test
    fun `no report yet maps to no pills`() {
        val ui = preflightUi(SittingViewModel.UiState(), SittingHeader("x", null, true), Stakes.STAKED)
        assertNull(ui.pills)
    }
}
