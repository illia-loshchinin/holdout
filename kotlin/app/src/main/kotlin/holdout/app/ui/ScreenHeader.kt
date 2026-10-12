package holdout.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The header of a pushed screen, as the canvas draws it: a 40 dp round back
 * button (`hairline` border on `surface-1`), the [label] centred in uppercase
 * `label` style and `text-secondary`, and on the right either a [StatusChip]
 * ([trailing]), a close button ([onClose], for a sheet-like screen), or nothing.
 * Every control is icon-only, so each has a name for TalkBack
 * ([backDescription], [closeDescription]); each reaches `size-touch` (48 dp).
 *
 * The label names the Holdout by level and length, state and countdown, never a
 * calendar date: `SPARK · 7-DAY · DAY 3 OF 7` (`Format.headerLabel`). Tab roots
 * have no header: they are the roots, with the bottom bar.
 */
@Composable
fun ScreenHeader(
    label: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    onClose: (() -> Unit)? = null,
    closeDescription: String = "Close",
    trailing: (@Composable () -> Unit)? = null,
) {
    // The label is centred on the screen, whatever sits on either side of it.
    Box(modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.touchTarget), contentAlignment = Alignment.Center) {
        if (onBack != null) {
            Box(Modifier.align(Alignment.CenterStart)) { RoundButton(backDescription, onBack) { drawChevron(it) } }
        }
        Text(
            text = label.uppercase(),
            style = LabelStyle,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.touchTarget + Spacing.tight),
        )
        when {
            onClose != null -> Box(Modifier.align(Alignment.CenterEnd)) { RoundButton(closeDescription, onClose) { drawCross(it) } }
            trailing != null -> Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

/** A 40 dp visual circle inside a 48 dp touch target. */
@Composable
private fun RoundButton(description: String, onClick: () -> Unit, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Float) -> Unit) {
    Box(
        modifier = Modifier
            .size(Spacing.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(BUTTON_SIZE)
                .background(HoldoutColors.Surface1, CircleShape)
                .border(Spacing.border, HoldoutColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(GLYPH_SIZE)) { glyph(size.minDimension) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChevron(side: Float) {
    val u = side / GLYPH_UNITS
    val path = Path().apply {
        moveTo(10f * u, 3f * u)
        lineTo(5f * u, 8f * u)
        lineTo(10f * u, 13f * u)
    }
    drawPath(path, HoldoutColors.TextPrimary, style = Stroke(width = GLYPH_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCross(side: Float) {
    val u = side / GLYPH_UNITS
    val stroke = Stroke(width = GLYPH_STROKE * u, cap = StrokeCap.Round)
    drawLine(HoldoutColors.TextPrimary, Offset(4f * u, 4f * u), Offset(12f * u, 12f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(HoldoutColors.TextPrimary, Offset(12f * u, 4f * u), Offset(4f * u, 12f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private val BUTTON_SIZE = 40.dp
private val GLYPH_SIZE = 16.dp
private const val GLYPH_UNITS = 16f
private const val GLYPH_STROKE = 1.6f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScreenHeaderPreview() {
    HoldoutTheme {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            ScreenHeader("Spark · 7-day · starts in 2d 4h", onBack = {})
            ScreenHeader("Habit · 21-day · day 12 of 21", onBack = {}, trailing = { StatusChip("running", tone = ChipTone.Success) })
            ScreenHeader("Stake", onClose = {})
        }
    }
}
