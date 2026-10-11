package holdout.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/** Where a step of a confirming transaction stands. */
enum class StepState {
    /** A `success` tick: it happened. */
    Done,

    /** An `accent` spinner: it is happening now. */
    Active,

    /** A `text-tertiary` ring: it has not started. */
    Pending,
}

/**
 * One line of the confirming checklist (**Approved in your wallet · Sent to
 * devnet · Confirming your place · In the lobby**): a [state] icon, then the
 * [text]. Done is a `success` tick, active an `accent` spinner, pending a
 * `text-tertiary` ring, and the text of a pending step drops to `text-tertiary`
 * too. The accessible name carries the state ("Sent to devnet, done"), since
 * the icon's colour is never the only signal.
 */
@Composable
fun ChecklistRow(text: String, state: StepState, modifier: Modifier = Modifier) {
    val spoken = when (state) {
        StepState.Done -> "$text, done"
        StepState.Active -> "$text, in progress"
        StepState.Pending -> "$text, not yet"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        StepIcon(state)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state == StepState.Pending) HoldoutColors.TextTertiary else HoldoutColors.TextPrimary,
        )
    }
}

@Composable
private fun StepIcon(state: StepState) {
    val turn = if (state == StepState.Active) spin(SPIN_MS) else 0f
    Canvas(Modifier.size(ICON)) {
        val u = size.minDimension / UNITS
        when (state) {
            StepState.Done -> {
                drawCircle(HoldoutColors.Success.copy(alpha = DONE_FILL), radius = 8f * u, center = center)
                val tick = Path().apply {
                    moveTo(5.5f * u, 9.2f * u)
                    lineTo(7.9f * u, 11.6f * u)
                    lineTo(12.5f * u, 6.6f * u)
                }
                drawPath(tick, HoldoutColors.Success, style = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            StepState.Active -> {
                val side = 10f * u
                val topLeft = Offset(center.x - side / 2, center.y - side / 2)
                drawArc(HoldoutColors.Hairline, 0f, 360f, useCenter = false, topLeft = topLeft, size = Size(side, side), style = Stroke(2f * u))
                drawArc(HoldoutColors.Accent, turn - 90f, SPIN_SWEEP, useCenter = false, topLeft = topLeft, size = Size(side, side), style = Stroke(2f * u, cap = StrokeCap.Round))
            }
            StepState.Pending -> drawCircle(HoldoutColors.TextTertiary, radius = 7.5f * u, center = center, style = Stroke(1.5f * u))
        }
    }
}

private val ICON = 18.dp
private val ROW_MIN_HEIGHT = 32.dp
private const val UNITS = 18f
private const val DONE_FILL = 0.16f
private const val SPIN_MS = 1_000
private const val SPIN_SWEEP = 100f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChecklistRowPreview() {
    HoldoutTheme {
        HoldoutCard {
            Column {
                ChecklistRow("Approved in your wallet", StepState.Done)
                ChecklistRow("Sent to devnet", StepState.Done)
                ChecklistRow("Confirming your place", StepState.Active)
                ChecklistRow("In the lobby", StepState.Pending)
            }
        }
    }
}
