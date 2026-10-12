package holdout.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import holdout.app.sitting.CameraPermissionScreen
import holdout.app.sitting.CountdownContent
import holdout.app.sitting.CountdownUi
import holdout.app.sitting.CountingContent
import holdout.app.sitting.CountingUi
import holdout.app.sitting.DayCompleteContent
import holdout.app.sitting.DayCompleteOffer
import holdout.app.sitting.DayCompleteUi
import holdout.app.sitting.HoldoutDayCard
import holdout.app.sitting.PreflightContent
import holdout.app.sitting.PreflightPill
import holdout.app.sitting.PreflightTip
import holdout.app.sitting.PreflightTipsSheet
import holdout.app.sitting.PreflightUi
import holdout.app.sitting.SetupSheet
import holdout.app.sitting.SittingCopy
import holdout.app.sitting.SittingHeader
import holdout.app.sitting.StoppedScreen
import holdout.app.sitting.StoppedUi
import holdout.app.sitting.StreakAfter
import holdout.app.testutil.AnimationsOff
import holdout.repcounter.CameraView
import holdout.repcounter.PreflightCheck
import holdout.repcounter.Stakes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sitting's pages, full screen with no bar; the camera pages over a stand-in for a bright room. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SittingShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** What the camera sees: a light floor and a pale wall, the worst case for white text. */
    @Composable
    private fun Room(content: @Composable BoxScope.() -> Unit) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE9E4D8), Color(0xFFB9A88A)))),
        ) { content() }
    }

    private val label = SittingCopy.positionLabel(12)
    private val left = 4 * 3_600L + 6 * 60
    private val labels = listOf("Lighting", "Whole body in frame", "Knees visible", "Camera view", "Background still")

    private fun pills(failing: Set<String> = emptySet()) = labels.map { PreflightPill(it, it !in failing) }

    private fun preflight(name: String, stakes: Stakes = Stakes.STAKED, day: Int? = 12, pills: List<PreflightPill>? = pills(setOf("Camera view")), coach: String? = "Phone to 45°", sub: String? = "Move the phone so it can see all of you", hold: Float? = null, timeLeft: Long? = left, tipsOffered: Boolean = false) = compose.shoot("sitting", name) {
        Room {
            PreflightContent(
                PreflightUi(
                    header = SittingHeader(SittingCopy.positionLabel(day), timeLeft, holdoutDay = day != null),
                    pills = pills,
                    coach = coach,
                    coachIsHold = hold != null,
                    sub = sub,
                    holdProgress = hold,
                    rule = SittingCopy.ruleCaption(stakes),
                    tipsOffered = tipsOffered,
                ),
                onStop = {},
                onSetup = {},
            )
        }
    }

    @Test
    fun cameraPermission() = compose.shoot("sitting", "camera-permission") { CameraPermissionScreen(label, denied = false, onAllow = {}, onOpenSettings = {}, onStop = {}) }

    @Test
    fun cameraPermissionDenied() = compose.shoot("sitting", "camera-permission-denied") { CameraPermissionScreen(label, denied = true, onAllow = {}, onOpenSettings = {}, onStop = {}) }

    @Test
    fun setupGuideHabit() = compose.shoot("sitting", "setup-guide-habit") { Room { SetupSheet(Stakes.STAKED, CameraView.OBLIQUE, {}) } }

    @Test
    fun setupGuideDaily() = compose.shoot("sitting", "setup-guide-daily") { Room { SetupSheet(Stakes.DAILY, null, {}) } }

    @Test
    fun preflightFixing() = preflight("preflight-fixing")

    @Test
    fun preflightTipsOffered() = preflight("preflight-tips-offered", tipsOffered = true)

    @Test
    fun preflightTipsSheet() = compose.shoot("sitting", "preflight-tips-sheet") {
        Room {
            PreflightTipsSheet(
                PreflightCheck.entries.map { PreflightTip(it.label, it.fix, red = it == PreflightCheck.CAMERA_ANGLE || it == PreflightCheck.LIGHTING) },
                onSetup = {},
                onClose = {},
            )
        }
    }

    @Test
    fun preflightLooking() = preflight("preflight-looking", pills = null, coach = null, sub = null)

    @Test
    fun preflightHolding() = preflight("preflight-holding", pills = pills(), coach = "Hold", sub = null, hold = 0.62f)

    @Test
    fun preflightDaily() = preflight("preflight-daily", stakes = Stakes.DAILY, day = null, pills = pills(setOf("Knees visible")), coach = "Turn a little", sub = "Turn a little away, so the phone sees your near knee.")

    @Test
    fun preflightLastMinutes() = preflight("preflight-last-minutes", timeLeft = 9 * 60L + 30)

    @Test
    fun countdown() = compose.shoot("sitting", "countdown") {
        Room { CountdownContent(CountdownUi(SittingHeader(SittingCopy.countingLabel(12, 25), left), 3, 0.66f), onStop = {}) }
    }

    private fun counting(name: String, done: Int = 17, banked: Int = 10, loose: Int = 7, hint: String? = null, justBanked: Boolean = false, day: Int? = 12, timeLeft: Long? = left, target: Int = 25) = compose.shoot("sitting", name) {
        Room {
            CountingContent(
                CountingUi(SittingHeader(SittingCopy.countingLabel(day, target), timeLeft, holdoutDay = day != null), done, target, banked, loose, hint, justBanked),
                onStop = {},
            )
        }
    }

    @Test
    fun counting() = counting("counting")

    @Test
    fun countingHint() = counting("counting-hint", hint = "Go lower")

    @Test
    fun countingJustBanked() = counting("counting-just-banked", done = 10, banked = 10, loose = 0, justBanked = true)

    @Test
    fun countingLastMinutes() = counting("counting-last-minutes", timeLeft = 9 * 60L + 30)

    @Test
    fun countingDaily() = counting("counting-daily", day = null, timeLeft = null)

    private val holdoutComplete = DayCompleteUi(
        header = "HABIT · 21-DAY · DAY 12 OF 21",
        reps = 25,
        holdout = HoldoutDayCard(dayNumber = 12, secondsLeft = left),
        streak = null,
        offer = null,
        button = DayCompleteUi.buttonFor(holdoutDay = 12, note = null, onChain = false),
    )

    private val dailyComplete = DayCompleteUi(
        header = "THE DAILY 25",
        reps = 25,
        holdout = null,
        streak = StreakAfter(days = 13, grew = true),
        offer = DayCompleteOffer("Stake on yourself next", "Spark · 7-day · joining closes in 1d 3h"),
        button = DayCompleteUi.buttonFor(holdoutDay = null, note = null, onChain = false),
    )

    @Test
    fun dayCompleteHoldout() = compose.shoot("sitting", "day-complete-holdout") { DayCompleteContent(holdoutComplete, {}, {}) }

    @Test
    fun dayCompleteHoldoutOffline() = compose.shoot("sitting", "day-complete-holdout-offline") {
        val note = "Done — not sent yet" to "It counts once it reaches the chain before the deadline. Holdout sends it when it can."
        DayCompleteContent(holdoutComplete.copy(holdout = HoldoutDayCard(12, left, note = note), button = DayCompleteUi.buttonFor(12, note, false)), {}, {})
    }

    @Test
    fun dayCompleteDaily() = compose.shoot("sitting", "day-complete-daily") { DayCompleteContent(dailyComplete, {}, {}) }

    @Test
    fun dayCompleteDailyNoOffer() = compose.shoot("sitting", "day-complete-daily-no-offer") { DayCompleteContent(dailyComplete.copy(offer = null), {}, {}) }

    @Test
    fun stopped() = compose.shoot("sitting", "stopped") {
        StoppedScreen(StoppedUi(SittingCopy.countingLabel(12, 25), 25, banked = 10, notKept = 7, secondsLeft = 5 * 3_600L + 12 * 60), onBack = {})
    }

    @Test
    fun stoppedCrash() = compose.shoot("sitting", "stopped-crash") {
        StoppedScreen(StoppedUi(SittingCopy.countingLabel(12, 25), 25, banked = 10, notKept = 0, secondsLeft = 5 * 3_600L + 12 * 60, crash = true), onBack = {})
    }
}
