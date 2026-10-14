package holdout.app.sitting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Derive

/**
 * What the top of every sitting screen says (boards 15, 17, 18, 6, 20): the
 * [label] in the bar, and the time still to run. [timeLeft] is seconds to the
 * Holdout day's deadline, or to the end of today for the Daily 25 ([holdoutDay]
 * false); null while the chain is unread, and always null in Holdout Lab.
 */
internal data class SittingHeader(
    val label: String?,
    val timeLeft: Long? = null,
    val holdoutDay: Boolean = true,
) {
    /** A Holdout day's last ten minutes: its time turns the soft warm tone (FR-047, 2026-10-13); the sentence is Today's, not the sitting's. */
    val inLastMinutes: Boolean get() = holdoutDay && Derive.inWarningWindow(timeLeft)
}

/** The ✕ circle (40 dp inside a 48 dp target), the caps label centred, and a spacer the circle's size. */
@Composable
internal fun SittingTopBar(label: String?, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        StopCircle(onStop)
        Text(
            text = label.orEmpty().uppercase(),
            style = LabelStyle,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(Spacing.touchTarget))
    }
}

@Composable
private fun StopCircle(onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Spacing.touchTarget)
            .clickable(onClick = onStop)
            .semantics {
                contentDescription = STOP_DESCRIPTION
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(CircleSize)
                .background(HoldoutColors.Surface1.copy(alpha = OVER_CAMERA), CircleShape)
                .border(Spacing.border, HoldoutColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(CrossSize)) {
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                val pad = 1.dp.toPx()
                drawLine(HoldoutColors.TextPrimary, Offset(pad, pad), Offset(size.width - pad, size.height - pad), stroke.width, StrokeCap.Round)
                drawLine(HoldoutColors.TextPrimary, Offset(size.width - pad, pad), Offset(pad, size.height - pad), stroke.width, StrokeCap.Round)
            }
        }
    }
}

/** The bar, then the time line under it: `4h 06m to today's deadline` / `4h 06m left today`; the last-ten-minutes sentence in a Holdout. */
@Composable
internal fun SittingHeaderBlock(header: SittingHeader, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        SittingTopBar(header.label, onStop)
        TimeLine(header)
    }
}

@Composable
internal fun TimeLine(header: SittingHeader, modifier: Modifier = Modifier) {
    val left = header.timeLeft ?: return
    val late = header.inLastMinutes
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
        Text(
            text = SittingCopy.timeLine(left, header.holdoutDay),
            style = TimeLineStyle,
            color = if (late || Derive.inWarningWindow(left)) HoldoutColors.SoftWarm else HoldoutColors.TextSecondary,
        )
        // The warning sentence lives on Today and the day strip, not in the sitting (owner, 12 Oct).
    }
}

/** Black fading to nothing from the top edge, under the bar: text over the camera sits on a scrim. */
@Composable
internal fun TopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TopScrimHeight)
            .background(Brush.verticalGradient(0f to HoldoutColors.Canvas.copy(alpha = TOP_SCRIM), TOP_SCRIM_STOP to HoldoutColors.Canvas.copy(alpha = TOP_SCRIM_MID), 1f to Color.Transparent)),
    )
}

/** Black fading in from the bottom edge, under the count and the coach word. */
@Composable
internal fun BottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BottomScrimHeight)
            .background(Brush.verticalGradient(0f to Color.Transparent, BOTTOM_SCRIM_STOP to HoldoutColors.Canvas.copy(alpha = BOTTOM_SCRIM), 1f to HoldoutColors.Canvas.copy(alpha = BOTTOM_SCRIM))),
    )
}

/** One of the five pre-flight checks: a pill with a tick (green) or "!" (coral); the label never changes — the fix is the sentence under the coach word. */
@Composable
internal fun PreflightChip(pill: PreflightPill, modifier: Modifier = Modifier) {
    val tone = if (pill.passing) HoldoutColors.Success else HoldoutColors.Warning
    Row(
        modifier = modifier
            .background(HoldoutColors.Surface2.copy(alpha = CHIP_FILL), CircleShape)
            .border(Spacing.border, tone.copy(alpha = if (pill.passing) CHIP_BORDER_OK else CHIP_BORDER_FIX), CircleShape)
            .padding(horizontal = Spacing.betweenItems, vertical = ChipPaddingVertical)
            .semantics(mergeDescendants = true) { contentDescription = "${pill.label}, ${if (pill.passing) "ok" else "needs fixing"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.base + Spacing.base / 2),
    ) {
        Canvas(Modifier.size(ChipIconSize)) {
            val stroke = 1.5.dp.toPx()
            if (pill.passing) {
                val p = Offset(2.5.dp.toPx(), 6.5.dp.toPx())
                val q = Offset(5.dp.toPx(), 9.dp.toPx())
                val r = Offset(9.5.dp.toPx(), 3.dp.toPx())
                drawLine(tone, p, q, stroke, StrokeCap.Round)
                drawLine(tone, q, r, stroke, StrokeCap.Round)
            } else {
                drawCircle(tone, radius = 4.5.dp.toPx(), style = Stroke(width = stroke))
                drawLine(tone, Offset(size.width / 2, 3.8.dp.toPx()), Offset(size.width / 2, 6.4.dp.toPx()), stroke, StrokeCap.Round)
            }
        }
        Text(pill.label, style = LabelStyle, color = tone)
    }
}

/** The five pills, centred, wrapping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreflightChips(pills: List<PreflightPill>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        pills.forEach { PreflightChip(it) }
    }
}

/** Seconds left, ticking: the live clock under a sitting's bar. Null while unread. */
@Composable
internal fun rememberSecondsLeft(read: () -> Long?): Long? {
    var left by remember { mutableStateOf(read()) }
    LaunchedEffect(Unit) {
        while (true) {
            left = read()
            kotlinx.coroutines.delay(TICK_MILLIS)
        }
    }
    return left
}

internal const val STOP_DESCRIPTION = "Stop this sitting"

private val TimeLineStyle = AmountStyle.copy(fontSize = 13.sp, lineHeight = 16.sp)
private val CircleSize = 40.dp
private val CrossSize = 14.dp
private val ChipIconSize = 12.dp
private val ChipPaddingVertical = 7.dp
private val TopScrimHeight = 140.dp
private val BottomScrimHeight = 320.dp
private const val OVER_CAMERA = 0.7f
private const val TOP_SCRIM = 0.85f

/** The scrim holds most of its strength down to the time line under the bar, then fades: text there sits on a bright room too. */
private const val TOP_SCRIM_STOP = 0.5f
private const val TOP_SCRIM_MID = 0.65f
private const val BOTTOM_SCRIM = 0.92f
private const val BOTTOM_SCRIM_STOP = 0.55f
private const val CHIP_FILL = 0.9f
private const val CHIP_BORDER_OK = 0.32f
private const val CHIP_BORDER_FIX = 0.4f
private const val TICK_MILLIS = 1_000L
