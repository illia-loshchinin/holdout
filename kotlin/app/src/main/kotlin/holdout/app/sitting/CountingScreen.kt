package holdout.app.sitting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.ui.CountdownText
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** What the counting page shows (board 6). [done] is banked plus this set; [loose] is this set only, and goes if the sitting stops. */
internal data class CountingUi(
    val header: SittingHeader,
    val done: Int,
    val target: Int,
    val banked: Int,
    val loose: Int,
    /** The last refused rep's reason ("Go lower"): large, readable from the floor, and spoken (002 FR-019). */
    val hint: String?,
    /** A block of 10 just banked and no rep has been counted since. */
    val justBanked: Boolean = false,
)

/**
 * Counting (board 6; screen map S5, 002 FR-019, FR-021, 001 FR-047).
 *
 * ```
 *  (✕)        DAY 12 · 25 REPS
 *
 *            ( ! Go lower )                  ← the refused rep's reason, large
 *
 *              17  of 25                     ← CounterStyle; jumps on a rep, "+1" rises
 *   ━━━━━━━━━  ━━━━━━━░░░  ░░░░░             ← bars, proportional 10 / 10 / 5
 *   10 banked · 7 this set        4h 06m left
 * ```
 * The skeleton is drawn under all of it, by the screen. The last ten minutes of
 * a Holdout day add the sentence that unfinished reps will not count.
 * [extra] is Holdout Lab's readouts slot; the member app passes nothing.
 */
@Composable
internal fun CountingContent(ui: CountingUi, onStop: () -> Unit, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxSize()) {
        TopScrim(Modifier.align(Alignment.TopCenter))
        BottomScrim(Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.betweenItems),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SittingTopBar(ui.header.label, onStop)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.CenterVertically),
            ) {
                ui.hint?.let { Hint(it) }
                if (ui.justBanked && ui.hint == null) BankedPill()
                if (ui.header.inLastMinutes) WarningPill(SittingCopy.LAST_MINUTES)
            }
            Column(
                modifier = Modifier.padding(bottom = Spacing.betweenSections),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
            ) {
                Count(ui.done, ui.target)
                CountingBars(ui.target, ui.banked, ui.loose)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${ui.banked} banked · ${ui.loose} this set", style = LabelStyle, color = HoldoutColors.TextSecondary)
                    ui.header.timeLeft?.let { left ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.base)) {
                            CountdownText(left, style = SmallAmount)
                            Text("left", style = SmallAmount, color = HoldoutColors.TextSecondary)
                        }
                    }
                }
                extra()
            }
        }
    }
}

/**
 * The count, with a reward on every rep: the number jumps, and a "+1" rises off
 * it and fades. A member mid-set glances at the screen for a fraction of a
 * second; this is what makes that glance land (002 FR-021).
 */
@Composable
private fun Count(done: Int, target: Int) {
    var previous by remember { mutableIntStateOf(done) }
    val pulse = remember { Animatable(1f) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done > previous) {
            launch {
                pulse.snapTo(PULSE_FROM)
                pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            launch {
                rise.snapTo(0f)
                rise.animateTo(1f, tween(durationMillis = RISE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
        previous = done
    }
    Box(contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(
                "$done",
                style = CounterStyle,
                color = HoldoutColors.TextPrimary,
                modifier = Modifier.scale(pulse.value),
            )
            Text(
                "of $target",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = HoldoutColors.TextSecondary,
                modifier = Modifier.padding(bottom = Spacing.base),
            )
        }
        if (rise.value < 1f) {
            Text(
                "+1",
                style = MaterialTheme.typography.headlineMedium,
                color = HoldoutColors.Success,
                modifier = Modifier.graphicsLayer {
                    translationY = -rise.value * RISE_DP.dp.toPx() - RISE_START_DP.dp.toPx()
                    translationX = RISE_SIDE_DP.dp.toPx()
                    alpha = 1f - rise.value
                    val grow = GROW_FROM + GROW_BY * rise.value
                    scaleX = grow
                    scaleY = grow
                },
            )
        }
    }
}

/** A refused rep's reason, in a pill on the camera: twice the title size — read from the floor, at arm's length (002 FR-019). */
@Composable
private fun Hint(text: String) {
    val title = MaterialTheme.typography.titleMedium
    Row(
        modifier = Modifier
            .background(HoldoutColors.Surface2.copy(alpha = PILL_FILL), RoundedCornerShape(percent = 50))
            .border(Spacing.border, HoldoutColors.Warning.copy(alpha = HINT_BORDER), RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.betweenSections, vertical = Spacing.betweenItems),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Canvas(Modifier.size(HintIcon)) {
            val x = size.width / 2
            drawLine(HoldoutColors.Warning, Offset(x, size.height * 0.2f), Offset(x, size.height * 0.62f), HINT_STROKE.dp.toPx(), StrokeCap.Round)
            drawLine(HoldoutColors.Warning, Offset(x, size.height * 0.84f), Offset(x, size.height * 0.86f), HINT_STROKE.dp.toPx(), StrokeCap.Round)
        }
        Text(
            text = text,
            style = title.copy(fontSize = title.fontSize * 2, lineHeight = title.lineHeight * 2),
            color = HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The moment a block of 10 is banked: it cannot be lost now (also chimed and spoken). */
@Composable
private fun BankedPill() {
    Row(
        modifier = Modifier
            .background(HoldoutColors.Surface2.copy(alpha = PILL_FILL), RoundedCornerShape(percent = 50))
            .border(Spacing.border, HoldoutColors.Success.copy(alpha = BANKED_BORDER), RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.betweenItems, vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Canvas(Modifier.size(HintIcon)) {
            val stroke = HINT_STROKE.dp.toPx()
            drawLine(HoldoutColors.Success, Offset(size.width * 0.2f, size.height * 0.55f), Offset(size.width * 0.42f, size.height * 0.77f), stroke, StrokeCap.Round)
            drawLine(HoldoutColors.Success, Offset(size.width * 0.42f, size.height * 0.77f), Offset(size.width * 0.8f, size.height * 0.27f), stroke, StrokeCap.Round)
        }
        Text("Block of 10 banked", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success)
    }
}

private val SmallAmount = AmountStyle.copy(fontSize = 14.sp, lineHeight = 18.sp)
private val HintIcon = 20.dp
private const val HINT_STROKE = 2.4f
private const val PILL_FILL = 0.9f
private const val HINT_BORDER = 0.4f
private const val BANKED_BORDER = 0.32f
private const val PULSE_FROM = 1.35f
private const val RISE_MILLIS = 750
private const val RISE_DP = 90
private const val RISE_START_DP = 40
private const val RISE_SIDE_DP = 70
private const val GROW_FROM = 0.8f
private const val GROW_BY = 0.5f

private fun previewUi(done: Int, banked: Int, loose: Int, hint: String? = null, left: Long? = 4 * 3_600L + 6 * 60, daily: Boolean = false) = CountingUi(
    header = SittingHeader(SittingCopy.countingLabel(if (daily) null else 12, 25), left, holdoutDay = !daily),
    done = done,
    target = 25,
    banked = banked,
    loose = loose,
    hint = hint,
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountingPreview() {
    HoldoutTheme { CountingContent(previewUi(17, 10, 7, hint = "Go lower"), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountingDailyPreview() {
    HoldoutTheme { CountingContent(previewUi(3, 0, 3, daily = true), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountingLastMinutesPreview() {
    HoldoutTheme { CountingContent(previewUi(21, 20, 1, left = 9 * 60L + 30), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountingLastBlockPreview() {
    HoldoutTheme { CountingContent(previewUi(22, 20, 2), {}) }
}
