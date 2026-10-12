package holdout.app.cohort

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The Holdout's days as a strip of [durationDays] short bars (forfeit, board 9):
 * the [daysLasted] the member held out in `success`, the missed day in
 * `warning`, the days that never came faded; `Day 1` · `You: out on day 12` ·
 * `Day 21` under it. The word is in the labels, never the colour alone. The
 * strip is decoration for the summary card around it, which names the facts.
 */
@Composable
fun DayStrip(durationDays: Int, daysLasted: Int, modifier: Modifier = Modifier) {
    val lasted = daysLasted.coerceIn(0, durationDays)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Canvas(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            val gap = GAP.toPx()
            val width = (size.width - gap * (durationDays - 1)) / durationDays
            for (i in 0 until durationDays) {
                val color = when {
                    i < lasted -> HoldoutColors.Success
                    i == lasted -> HoldoutColors.Warning
                    else -> HoldoutColors.Hairline
                }
                drawRoundRect(color, Offset(i * (width + gap), 0f), Size(width, size.height), CornerRadius(size.height / 2))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Day 1", style = LabelStyle, color = HoldoutColors.TextTertiary)
            Text(DayStripText.outOn(lasted), style = LabelStyle, color = HoldoutColors.Warning)
            Text("Day $durationDays", style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
    }
}

internal object DayStripText {
    /** `You: out on day 12`: the day that did not reach the chain in time. */
    fun outOn(daysLasted: Int): String = "You: out on day ${daysLasted + 1}"
}

private val BAR_HEIGHT = 6.dp
private val GAP = 3.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DayStripPreview() {
    HoldoutTheme {
        Column(Modifier.padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections)) {
            DayStrip(21, 11)
            DayStrip(7, 0)
        }
    }
}
