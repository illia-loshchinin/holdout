package holdout.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import kotlin.math.hypot

/**
 * The Holdout loader (15g): the crowd's 24 members fade out one by one, then
 * back in, around a steady Solana Green Holdout. 2400 ms, looping. Same
 * geometry as the app icon. Shown wherever the app waits on the wallet or on
 * devnet.
 *
 * [size] is the mark's own size: it draws the crowd's span of the 108-unit
 * icon grid, not the whole grid, so 24 dp reads at a label's height. Pass a
 * null [contentDescription] where words beside it already say what is loading.
 * With animations turned off it shows the static mark.
 */
@Composable
fun HoldoutLoader(modifier: Modifier = Modifier, size: Dp = 24.dp, contentDescription: String? = "Loading") {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val t by rememberInfiniteTransition(label = "holdoutLoader").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val described = if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription }
    Canvas(modifier.size(size).then(described)) {
        val u = this.size.minDimension / MARK_UNITS
        val phase = if (animate) t else 0f
        MEMBERS.forEach { m ->
            val center = Offset((m.x - MARK_ORIGIN) * u, (m.y - MARK_ORIGIN) * u)
            drawCircle(HoldoutColors.TextPrimary, radius = m.r * u, center = center, alpha = alphaAt(phase, m.order))
        }
        drawCircle(HoldoutColors.Success, radius = 4.6f * u, center = Offset((54f - MARK_ORIGIN) * u, (54f - MARK_ORIGIN) * u))
    }
}

private const val LOOP_MS = 2400
private const val FADED = 0.12f
private const val FADE = 0.06f

/** The crowd spans 29–79 of the 108-unit grid: draw that, plus a unit either side. */
private const val MARK_UNITS = 52f
private const val MARK_ORIGIN = 28f

/** 100% → 12% at 0.05 + order/24 × 0.38 of the loop, back to 100% at 0.50 + order/24 × 0.38. */
private fun alphaAt(t: Float, order: Int): Float {
    val out = 0.05f + order / 24f * 0.38f
    val back = 0.50f + order / 24f * 0.38f
    return when {
        t < out -> 1f
        t < out + FADE -> 1f - (1f - FADED) * (t - out) / FADE
        t < back -> FADED
        t < back + FADE -> FADED + (1f - FADED) * (t - back) / FADE
        else -> 1f
    }
}

private data class Member(val x: Float, val y: Float, val r: Float, val order: Int)

/** 5×5 grid on the 108 icon canvas, centre left for the Holdout; radius shrinks with distance (halftone). */
private val MEMBERS: List<Member> = buildList {
    var i = 0
    for (row in 0 until 5) {
        for (col in 0 until 5) {
            if (row == 2 && col == 2) continue
            val x = 32f + col * 11f
            val y = 32f + row * 11f
            val d = hypot(x - 54f, y - 54f) / 11f
            add(Member(x, y, 4.1f - d * 0.5f, (i * 7) % 24))
            i++
        }
    }
}
