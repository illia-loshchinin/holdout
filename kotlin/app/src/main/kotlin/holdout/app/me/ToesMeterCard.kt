package holdout.app.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import holdout.app.streak.ToesMeter
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * "18 of 25 from your toes", seven bars for the last week and the best day
 * (H35): how far from a Habit today's Daily 25 was. Measured at 45° only;
 * head-on it says how to see it instead of guessing. On Me since 13 October
 * (FR-052a); Today no longer shows it.
 */
@Composable
internal fun ToesMeterCard(meter: ToesMeter.Meter, target: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
        if (meter.measured == 0) {
            Text(
                "Do it with the phone at 45° to see how many come from your toes",
                style = LabelStyle,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        Text(
            if (meter.measured < target) "${meter.toes} of ${meter.measured} seen at 45° from your toes" else "${meter.toes} of ${meter.measured} from your toes",
            style = AmountStyle,
            color = if (meter.hardDay(target)) HoldoutColors.Success else HoldoutColors.TextPrimary,
        )
        if (meter.hardDay(target)) {
            Text("That's a Habit day.", style = LabelStyle, color = HoldoutColors.Success)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(METER_HEIGHT).padding(top = Spacing.base),
        ) {
            meter.week.forEach { toes ->
                Box(
                    modifier = Modifier
                        .width(METER_BAR)
                        .height(METER_FLOOR + (METER_HEIGHT - METER_FLOOR) * toes.coerceAtMost(target) / target.coerceAtLeast(1))
                        .background(if (toes >= target) HoldoutColors.Success else HoldoutColors.Accent),
                )
            }
        }
        Text("Best: ${meter.best} from your toes", style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

private val METER_HEIGHT = 32.dp
private val METER_FLOOR = 4.dp
private val METER_BAR = 10.dp
