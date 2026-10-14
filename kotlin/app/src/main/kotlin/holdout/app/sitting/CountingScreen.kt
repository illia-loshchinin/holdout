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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import holdout.repcounter.RepHint
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
            // Hints and statuses sit at the top, directly under the header, soft and announced politely (FR-021, 2026-10-13).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.betweenItems)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ui.hint?.let { Hint(it) }
                if (ui.justBanked && ui.hint == null) BankedPill()
            }
            // No "unfinished reps" sentence during the sitting (owner, 12 Oct): the member is already doing the
            // day; Today and the day strip say it. The time line alone turns the soft warm tone.
            Spacer(Modifier.weight(1f))
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
                            CountdownText(left, style = SmallAmount, lateColor = HoldoutColors.SoftWarm)
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

/**
 * A refused rep's reason (002 FR-019), soft: a calm dark pill with a thin accent ring and a small round icon
 * — an arrow down for "Go lower", a line for "Keep your body straight" — the words at 26 sp, readable from
 * the floor. Never the warning red: coaching guides, it does not scold (FR-021, 2026-10-13).
 */
@Composable
private fun Hint(text: String) {
    val glyph = hintGlyphFor(text)
    val title = MaterialTheme.typography.titleMedium
    Row(
        modifier = Modifier
            .background(HoldoutColors.Surface1.copy(alpha = PILL_FILL), CircleShape)
            .border(Spacing.border, HoldoutColors.Accent.copy(alpha = HINT_RING), CircleShape)
            .padding(start = Spacing.betweenItems + Spacing.base, end = HintEndPadding, top = Spacing.tight + Spacing.base / 2, bottom = Spacing.tight + Spacing.base / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Canvas(
            Modifier
                .size(HintBadge)
                .background(HoldoutColors.Accent.copy(alpha = HINT_BADGE_FILL), CircleShape)
                .testTag("hint-icon-${glyph.name.lowercase()}"),
        ) { drawGlyph(glyph) }
        Text(
            text = text,
            style = title.copy(fontSize = HINT_TEXT_SP.sp, lineHeight = HINT_LINE_SP.sp),
            color = HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The moment a block of 10 is banked: it cannot be lost now (also chimed and spoken). A softer green than a loss is red. */
@Composable
private fun BankedPill() {
    Row(
        modifier = Modifier
            .background(HoldoutColors.Surface1.copy(alpha = PILL_FILL), CircleShape)
            .border(Spacing.border, HoldoutColors.Success.copy(alpha = BANKED_BORDER), CircleShape)
            .padding(horizontal = Spacing.betweenItems + Spacing.base, vertical = Spacing.tight + Spacing.base / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Canvas(Modifier.size(BankedIcon)) {
            val stroke = BANKED_STROKE.dp.toPx()
            drawLine(HoldoutColors.Success, Offset(size.width * 0.2f, size.height * 0.55f), Offset(size.width * 0.42f, size.height * 0.77f), stroke, StrokeCap.Round)
            drawLine(HoldoutColors.Success, Offset(size.width * 0.42f, size.height * 0.77f), Offset(size.width * 0.8f, size.height * 0.27f), stroke, StrokeCap.Round)
        }
        Text("Block of 10 banked", style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.Success)
    }
}

/** The small round icon in a hint's pill. */
internal enum class HintGlyph { ARROW_DOWN, LINE, DOT }

/** Which icon a refused rep's [message] gets: an arrow down for "Go lower", a line for "Keep your body straight", a dot for the rest. */
internal fun hintGlyphFor(message: String): HintGlyph = when (message) {
    RepHint.GO_LOWER.message -> HintGlyph.ARROW_DOWN
    RepHint.KEEP_BODY_STRAIGHT.message -> HintGlyph.LINE
    else -> HintGlyph.DOT
}

private fun DrawScope.drawGlyph(glyph: HintGlyph) {
    // The board draws them on an 18-unit square inside the 32 dp badge.
    val unit = HintGlyphSize.toPx() / GLYPH_UNITS
    val origin = (size.width - HintGlyphSize.toPx()) / 2
    fun at(x: Float, y: Float) = Offset(origin + x * unit, origin + y * unit)
    val stroke = GLYPH_STROKE * unit
    when (glyph) {
        HintGlyph.ARROW_DOWN -> {
            drawLine(HoldoutColors.Accent, at(9f, 3f), at(9f, 14f), stroke, StrokeCap.Round)
            drawLine(HoldoutColors.Accent, at(4.5f, 9.5f), at(9f, 14f), stroke, StrokeCap.Round)
            drawLine(HoldoutColors.Accent, at(13.5f, 9.5f), at(9f, 14f), stroke, StrokeCap.Round)
        }
        HintGlyph.LINE -> drawLine(HoldoutColors.Accent, at(3f, 9f), at(15f, 9f), stroke, StrokeCap.Round)
        HintGlyph.DOT -> drawCircle(HoldoutColors.Accent, stroke * DOT_RADIUS_FACTOR, at(9f, 9f))
    }
}

private val SmallAmount = AmountStyle.copy(fontSize = 14.sp, lineHeight = 18.sp)
private val HintBadge = 32.dp
private val HintGlyphSize = 18.dp
private val HintEndPadding = 22.dp
private val BankedIcon = 16.dp
private const val HINT_TEXT_SP = 26
private const val HINT_LINE_SP = 34
private const val GLYPH_UNITS = 18f
private const val GLYPH_STROKE = 2f
private const val DOT_RADIUS_FACTOR = 1.5f
private const val BANKED_STROKE = 1.8f
private const val PILL_FILL = 0.9f
private const val HINT_RING = 0.28f
private const val HINT_BADGE_FILL = 0.14f
private const val BANKED_BORDER = 0.28f
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
private fun CountingBodyStraightPreview() {
    HoldoutTheme { CountingContent(previewUi(17, 10, 7, hint = "Keep your body straight"), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountingBankedPreview() {
    HoldoutTheme { CountingContent(previewUi(20, 20, 0).copy(justBanked = true), {}) }
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
