package holdout.app.sitting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** What the countdown page shows: the digit, and how much of the three seconds is left (0..1) for the ring. */
internal data class CountdownUi(val header: SittingHeader, val remaining: Int, val progress: Float)

/**
 * "Three, two, one, go" (board 18; screen map S4): a ring that drains over the
 * three seconds (ruling C-D6) around the digit, and the caption *get ready · stay
 * at the top*. The sound is the cue, not this screen.
 */
@Composable
internal fun CountdownContent(ui: CountdownUi, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        TopScrim(Modifier.align(Alignment.TopCenter))
        BottomScrim(Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.betweenItems),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SittingHeaderBlock(ui.header, onStop)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier
                        .size(RingSize)
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (ui.remaining == 1) "1 second to go" else "${ui.remaining} seconds to go"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(RingSize)) {
                        val stroke = Spacing.ringStroke.toPx()
                        val inset = stroke / 2
                        val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(HoldoutColors.Hairline, 0f, FULL_TURN, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
                        drawArc(HoldoutColors.Accent, START_DEGREES, FULL_TURN * ui.progress.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Text("${ui.remaining}", style = DigitStyle, color = HoldoutColors.TextPrimary)
                }
                Text("get ready · stay at the top".uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary)
            }
        }
    }
}

/**
 * The countdown's own clock: 3, 2, 1, one second each, the ring draining
 * smoothly across all three, then [onDone]. [onTick] is told each digit as it
 * appears (the spoken cue). The contract with the view model is unchanged.
 */
@Composable
internal fun Countdown(header: SittingHeader, onStop: () -> Unit, onTick: (Int) -> Unit, onDone: () -> Unit) {
    var remaining by remember { mutableIntStateOf(FROM) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // The ring drains on its own, linearly, while the digits step once a second.
        launch { progress.animateTo(0f, tween(durationMillis = FROM * SECOND, easing = LinearEasing)) }
        while (remaining > 0) {
            onTick(remaining)
            kotlinx.coroutines.delay(SECOND.toLong())
            remaining -= 1
        }
        onDone()
    }
    CountdownContent(CountdownUi(header, remaining.coerceAtLeast(1), progress.value), onStop)
}

private val DigitStyle = TextStyle(
    fontFamily = CounterStyle.fontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 140.sp,
    lineHeight = 140.sp,
    letterSpacing = CounterStyle.letterSpacing,
    fontFeatureSettings = CounterStyle.fontFeatureSettings,
)

private val RingSize = 220.dp
private const val FULL_TURN = 360f
private const val START_DEGREES = -90f
private const val FROM = 3
private const val SECOND = 1_000

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountdownPreview() {
    HoldoutTheme { CountdownContent(CountdownUi(SittingHeader(SittingCopy.countingLabel(12, 25), 4 * 3_600L + 6 * 60), remaining = 3, progress = 1f), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CountdownDailyPreview() {
    HoldoutTheme { CountdownContent(CountdownUi(SittingHeader(SittingCopy.countingLabel(null, 25), 4 * 3_600L + 6 * 60, holdoutDay = false), remaining = 2, progress = 0.6f), {}) }
}
