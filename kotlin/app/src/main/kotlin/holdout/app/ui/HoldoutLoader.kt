package holdout.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors

/**
 * The Holdout loader (icon v2, 9 Oct): the crowd's members drop out one by one
 * and seven random survivors turn `accent`; then it runs in reverse, the
 * survivors staying accent until the grid is full. 2400 ms, with a new pick of
 * survivors every loop. Same 5×5 grid as the app icon. Shown wherever the app
 * waits on the wallet or on devnet.
 *
 * [size] is the mark's own size: it draws the crowd's span of the 108-unit
 * icon grid, not the whole grid, so 24 dp reads at a label's height. Pass a
 * null [contentDescription] where words beside it already say what is loading.
 * With animations turned off it shows the static mark: the crowd with the H in
 * accent.
 */
@Composable
fun HoldoutLoader(modifier: Modifier = Modifier, size: Dp = 24.dp, contentDescription: String? = "Loading") {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val t = remember { Animatable(0f) }
    var survivors by remember { mutableStateOf(MEMBERS.shuffled().take(SURVIVORS)) }
    LaunchedEffect(animate) {
        while (animate) {
            survivors = MEMBERS.shuffled().take(SURVIVORS)
            t.snapTo(0f)
            t.animateTo(1f, tween(durationMillis = LOOP_MS, easing = LinearEasing))
        }
    }
    val fadeOrder = remember(survivors) { MEMBERS.filter { it !in survivors }.mapIndexed { i, p -> p to ((i * 7) % 18) / 18f }.toMap() }
    val described = if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription }
    Canvas(modifier.size(size).then(described)) {
        val u = this.size.minDimension / MARK_UNITS
        val v = t.value
        MEMBERS.forEach { p ->
            val center = Offset((32f + p.second * 11f - MARK_ORIGIN) * u, (32f + p.first * 11f - MARK_ORIGIN) * u)
            if (!animate) {
                drawCircle(if (p in H) HoldoutColors.Accent else HoldoutColors.TextPrimary, radius = R * u, center = center)
            } else if (p in survivors) {
                // White → accent at 32–36% of the loop, back to white as the grid fills (96–99%).
                val colour = lerp(lerp(HoldoutColors.TextPrimary, HoldoutColors.Accent, ramp(v, 0.32f, 0.36f)), HoldoutColors.TextPrimary, ramp(v, 0.96f, 0.99f))
                drawCircle(colour, radius = R * u, center = center)
            } else {
                // Out to 12% in a scattered order, back in the reverse order.
                val k = 0.04f + fadeOrder.getValue(p) * 0.24f
                val alpha = 1f - (1f - FADED) * ramp(v, k, k + 0.05f) + (1f - FADED) * ramp(v, 0.95f - k, 1f - k)
                drawCircle(HoldoutColors.TextPrimary, radius = R * u, center = center, alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
}

private const val LOOP_MS = 2400
private const val SURVIVORS = 7
private const val FADED = 0.12f
private const val R = 3.4f

/** The crowd spans 28.6–79.4 of the 108-unit grid: draw 28–80. */
private const val MARK_UNITS = 52f
private const val MARK_ORIGIN = 28f

/** The 5×5 grid as (row, col), 11 apart from (32, 32) on the icon's 108 grid. */
private val MEMBERS: List<Pair<Int, Int>> = (0 until 25).map { it / 5 to it % 5 }

/** The H the icon draws in accent: the static mark, with every other member white. */
private val H = listOf(1 to 1, 2 to 1, 3 to 1, 2 to 2, 1 to 3, 2 to 3, 3 to 3)

private fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
