package holdout.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * The large ring on a transaction's confirming screen: a `hairline` track with
 * a short `accent` arc turning while the network confirms ([done] false), and a
 * closed `success` ring with a tick once it has ([done]). It draws no words:
 * [description] is its accessible name ("Taking your place", "Place secured"),
 * and the words sit beside it on screen. With animations turned off the arc
 * holds still.
 */
@Composable
fun ProgressRing(description: String, modifier: Modifier = Modifier, done: Boolean = false, size: Dp = RING_SIZE) {
    val turn = spin(RING_TURN_MS)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val stroke = Spacing.ringStroke.toPx()
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        val origin = Offset(stroke / 2, stroke / 2)
        if (done) {
            drawArc(HoldoutColors.Success, 0f, 360f, useCenter = false, topLeft = origin, size = arc, style = Stroke(stroke))
            drawTick(HoldoutColors.Success, this.size.minDimension, stroke)
        } else {
            drawArc(HoldoutColors.Hairline, 0f, 360f, useCenter = false, topLeft = origin, size = arc, style = Stroke(stroke))
            drawArc(HoldoutColors.Accent, turn - 90f, ARC_SWEEP, useCenter = false, topLeft = origin, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** The angle, in degrees, of something turning once every [millis], or 0 with animations off. */
@Composable
internal fun spin(millis: Int): Float {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    if (!animate) return 0f
    val turn by rememberInfiniteTransition(label = "turn").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = millis, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    return turn
}

private fun DrawScope.drawTick(color: Color, side: Float, stroke: Float) {
    val u = side / TICK_UNITS
    val path = Path().apply {
        moveTo(32f * u, 49f * u)
        lineTo(43f * u, 60f * u)
        lineTo(64f * u, 36f * u)
    }
    drawPath(path, color, style = Stroke(stroke * TICK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private val RING_SIZE = 96.dp
private const val RING_TURN_MS = 1_100

/** The arc is 28 % of the ring (the canvas draws 77 of 276). */
private const val ARC_SWEEP = 100f
private const val TICK_UNITS = 96f
private const val TICK_STROKE = 0.75f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ProgressRingPreview() {
    HoldoutTheme {
        Column {
            ProgressRing("Taking your place")
            ProgressRing("Place secured", done = true)
        }
    }
}
