package holdout.app.sitting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.ui.QuietAction
import holdout.app.ui.Reading
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.repcounter.Coach
import holdout.repcounter.PreflightCheck
import holdout.repcounter.PreflightReport
import holdout.repcounter.Stakes
import kotlin.math.roundToInt

/** One of the five pre-flight checks, as the pill draws it: its label never changes. */
internal data class PreflightPill(val label: String, val passing: Boolean)

/**
 * One row of the tip sheet: a check's pill label and the sentence S3 would say if it were the first
 * to fix, [red] while the pill is red (Gap 1). No copy of its own: the sentence is the check's.
 */
internal data class PreflightTip(val label: String, val fix: String, val red: Boolean)

/**
 * What the pre-flight and hold page shows (board 17). [pills] is null before
 * the first frame; [coach] is the one correction, at most three words, shown
 * big and spoken (002 FR-018); [sub] is the sentence that says what to do
 * (each failure states its fix, 001 FR-019; the position's own, 002 FR-018a);
 * [holdProgress] is non-null once there is nothing left to correct and the
 * ready hold is filling.
 */
internal data class PreflightUi(
    val header: SittingHeader,
    val pills: List<PreflightPill>?,
    val coach: String?,
    val coachIsHold: Boolean = false,
    val sub: String? = null,
    val subIsWarning: Boolean = false,
    val holdProgress: Float? = null,
    /** The level's rule sentence, or the Daily 25's "counted as the Spark" (002 FR-014). */
    val rule: String,
    /** The sitting has been short of green long enough (or often enough) for the "Still not green? Tips" button (Gap 1). */
    val tipsOffered: Boolean = false,
    /** The tip sheet's rows, one per check in the pills' order; empty before the first frame. */
    val tips: List<PreflightTip> = emptyList(),
)

/**
 * The five pills as the screen draws them, from the check report. The ready position's own blocker,
 * the knees the model cannot place, is the Knees visible pill's too (board 17, Blocked).
 */
internal fun preflightPills(report: PreflightReport, coach: Coach?): List<PreflightPill> = report.statuses.map {
    PreflightPill(it.check.label, it.passing && !(it.check == PreflightCheck.KNEES_VISIBLE && coach == Coach.KNEES_UNCLEAR))
}

/** The page's state from the sitting's: pills in the check order, the first thing to fix as the sentence. */
internal fun preflightUi(state: SittingViewModel.UiState, header: SittingHeader, stakes: Stakes): PreflightUi {
    val report = state.preflight
    val coach = state.coach
    val aligned = state.guidance?.aligned == true
    val statuses = report?.statuses.orEmpty()
    val gating = statuses.firstOrNull { !it.passing && !it.check.advisory }
    val advisory = statuses.firstOrNull { !it.passing && it.check.advisory }
    val (sub, warning) = when {
        // Before the phone stands on the floor nothing about the body can be checked: say only where it goes.
        coach == Coach.AIM_AT_FLOOR -> SittingCopy.PLACE_PHONE to false
        state.holdFix != null -> state.holdFix to true
        gating != null -> gating.fix to false
        advisory != null -> advisory.fix to true
        else -> null to false
    }
    val holding = aligned && coach == Coach.HOLD
    return PreflightUi(
        header = header,
        pills = report?.let { preflightPills(it, coach) },
        coach = coach?.words,
        coachIsHold = coach == Coach.HOLD,
        sub = sub,
        subIsWarning = warning,
        holdProgress = state.readyProgress.takeIf { holding },
        rule = SittingCopy.ruleCaption(stakes),
        tipsOffered = state.tipsOffered,
        tips = report?.let { tipsOf(it, coach, state.holdFix) }.orEmpty(),
    )
}

/** The tip sheet's rows: each check's label and the sentence S3 shows for it, red as the pills are. */
private fun tipsOf(report: PreflightReport, coach: Coach?, holdFix: String?): List<PreflightTip> = report.statuses.zip(preflightPills(report, coach)).map { (status, pill) ->
    // The knees the position cannot place: S3 says the position's own sentence for it, not the picture check's.
    val fix = if (!status.passing || pill.passing) status.fix else holdFix
    PreflightTip(pill.label, fix ?: status.check.fix, red = !pill.passing)
}

/**
 * Getting into position and the hold (board 17; screen map S3, 001 FR-019, 002 FR-018, FR-018a).
 *
 * ```
 *  (✕)   DAY 12 · GET INTO POSITION
 *       4h 06m to today's deadline            ← mono 13; warning in the last 10 minutes
 *  (✓ Lighting) (✓ Whole body in frame) (✓ Knees visible) (! Camera view) (✓ Background still)
 *
 *                 Phone to 45°               ← the one correction, 54 sp, spoken
 *         Move the phone so it can see all of you
 *     At 45° only. Aim for full push-ups …    ← the level's rule sentence (Daily: counted as the Spark)
 *        Still not green? Tips               ← once offered (30 s short of green, or the third flip to red)
 *                How to set up
 * ```
 * Once nothing is left to correct the word gives way to the ring with "Hold…".
 * Text over the camera sits on the two scrims.
 */
@Composable
internal fun PreflightContent(ui: PreflightUi, onStop: () -> Unit, onSetup: () -> Unit, modifier: Modifier = Modifier, onTips: () -> Unit = {}) {
    Box(modifier.fillMaxSize()) {
        TopScrim(Modifier.align(Alignment.TopCenter))
        BottomScrim(Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.betweenItems),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            SittingHeaderBlock(ui.header, onStop)
            if (ui.pills == null) {
                Reading { Text("Looking…", style = LabelStyle, color = HoldoutColors.TextTertiary) }
            } else {
                PreflightChips(ui.pills)
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier.padding(bottom = Spacing.tight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
            ) {
                if (ui.holdProgress != null) {
                    HoldRing(ui.holdProgress)
                    Sub("Hold the top. The countdown starts when the ring fills.", warning = false)
                } else {
                    ui.coach?.let { word ->
                        Text(
                            text = word,
                            style = CoachStyle,
                            color = if (ui.coachIsHold) HoldoutColors.Success else HoldoutColors.TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    ui.sub?.let { Sub(it, ui.subIsWarning) }
                }
                if (ui.tipsOffered) QuietAction(TIPS_BUTTON, onTips)
                Text(
                    text = ui.rule,
                    style = LabelStyle,
                    color = HoldoutColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                QuietAction("How to set up", onSetup)
            }
        }
    }
}

@Composable
private fun Sub(text: String, warning: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (warning) HoldoutColors.Warning else HoldoutColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
}

/** The ready hold filling: an accent arc on a hairline track, "Hold…" inside. */
@Composable
private fun HoldRing(progress: Float) {
    val percent = (progress * PERCENT).roundToInt()
    Box(
        modifier = Modifier
            .size(RingSize)
            .semantics(mergeDescendants = true) { contentDescription = "Hold, $percent percent" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(RingSize)) {
            val stroke = Spacing.ringStroke.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(HoldoutColors.Hairline, startAngle = 0f, sweepAngle = FULL_TURN, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
            drawArc(HoldoutColors.Accent, startAngle = START_DEGREES, sweepAngle = FULL_TURN * progress.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("Hold…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light, color = HoldoutColors.TextPrimary)
    }
}

/** The text button under the fix sentence once the tips are offered; it opens [PreflightTipsSheet]. Never opens by itself. */
internal const val TIPS_BUTTON = "Still not green? Tips"

/** The coach's words: read from two metres away, so as big as a line allows (002 FR-018). */
// A soft shadow keeps the words readable where they cross the outline and its figure (owner, 12 Oct walk).
internal val CoachStyle = TextStyle(
    fontSize = 54.sp,
    lineHeight = 58.sp,
    fontWeight = FontWeight.Light,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset.Zero, blurRadius = 16f),
)

private val RingSize = 120.dp
private const val FULL_TURN = 360f
private const val START_DEGREES = -90f
private const val PERCENT = 100

private val previewPills = listOf("Lighting", "Whole body in frame", "Knees visible", "Camera view", "Background still")

private fun previewUi(failing: Set<String>, coach: String?, sub: String?, hold: Float? = null, warning: Boolean = false, stakes: Stakes = Stakes.STAKED) = PreflightUi(
    header = SittingHeader(SittingCopy.positionLabel(12), 4 * 3_600L + 6 * 60),
    pills = previewPills.map { PreflightPill(it, it !in failing) },
    coach = coach,
    coachIsHold = hold != null,
    sub = sub,
    subIsWarning = warning,
    holdProgress = hold,
    rule = SittingCopy.ruleCaption(stakes),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun PreflightChecksPreview() {
    HoldoutTheme { PreflightContent(previewUi(setOf("Camera view"), "Phone to 45°", "Move the phone so it can see all of you"), {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun PreflightHoldingPreview() {
    HoldoutTheme { PreflightContent(previewUi(emptySet(), "Hold", null, hold = 0.62f), {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun PreflightBlockedPreview() {
    HoldoutTheme {
        PreflightContent(
            previewUi(emptySet(), "Can't see knees", "The phone cannot make out your knees. Loose trousers hide them: wear something fitted, or roll them up", warning = true, stakes = Stakes.EASY),
            {},
            {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun PreflightLastMinutesPreview() {
    HoldoutTheme {
        PreflightContent(previewUi(setOf("Lighting"), "More light", "Add light").let { it.copy(header = it.header.copy(timeLeft = 9 * 60L + 30)) }, {}, {})
    }
}
