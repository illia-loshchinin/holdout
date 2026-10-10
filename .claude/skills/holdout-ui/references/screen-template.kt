// Template for a new Holdout screen. Copy into kotlin/app/src/main/kotlin/holdout/app/<feature>/<Name>Screen.kt,
// rename, delete what the screen does not need. Not compiled from here.
package holdout.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * <What this screen is for, in one sentence. Cite the spec/PRD/screen-map entry.>
 *
 * ```
 *     21-DAY · FROM 21 SEP · DAY 12 OF 21      ← SectionLabel
 *              189 / 312                       ← StatBlock: CounterStyle, "/ 312" TextTertiary
 *            STILL STANDING                    ← LabelStyle, TextSecondary
 *   ┌───────────────────────────────────┐
 *   │ PRIZE POOL           48,200 SKR   │      ← HoldoutCard: label left, AmountStyle right
 *   │ LEFT TODAY             4h 06m     │      ← Format.countdown, Accent
 *   └───────────────────────────────────┘
 *   [        10 banked · do 15 more     ]      ← PrimaryButton, the one action
 * ```
 */
data class ExampleState(
    val standing: Int,
    val started: Int,
    val poolText: String,
    /** Null while chain time is unknown — say so rather than guess. */
    val secondsLeftToday: Long?,
    val banked: Int,
    val target: Int,
)

@Composable
fun ExampleScreen(
    state: ExampleState,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections, Alignment.CenterVertically),
    ) {
        // Hero: one big number with its caption (StatBlock). Only one CounterStyle per screen.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel("21-day · from 21 Sep · day 12 of 21")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.standing}", style = CounterStyle, color = HoldoutColors.TextPrimary)
                // The denominator of a ratio drops to TextTertiary.
                Text(" / ${state.started}", style = CounterStyle, color = HoldoutColors.TextTertiary)
            }
            Text("still standing".uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary)
        }

        HoldoutCard {
            CardRow("Prize pool") { Text(state.poolText, style = AmountStyle, color = HoldoutColors.TextPrimary) }
            CardRow("Left today") {
                val left = state.secondsLeftToday
                Text(
                    text = left?.let(Format::countdown) ?: "—",
                    style = AmountStyle,
                    color = if (left != null && left < 600) HoldoutColors.Warning else HoldoutColors.Accent,
                )
            }
        }

        PrimaryButton(
            text = if (state.banked > 0) "${state.banked} banked · do ${state.target - state.banked} more" else "Start",
            onClick = onPrimary,
        )

        Body("Test network, test token.", center = true)
        below()
    }
}

@Composable
private fun CardRow(label: String, value: @Composable () -> Unit) {
    // Card row: label left (LabelStyle, TextTertiary), value right (AmountStyle). Rows sit Spacing.tight apart (HoldoutCard does it).
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label, Modifier.weight(1f))
        value()
    }
}

// One preview per meaningful state. Dark canvas background, always inside HoldoutTheme.
@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
private fun ExampleFreshPreview() {
    HoldoutTheme { ExampleScreen(ExampleState(189, 312, "48,200 SKR", 14_760, 0, 25), onPrimary = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
private fun ExampleLastTenMinutesPreview() {
    HoldoutTheme { ExampleScreen(ExampleState(189, 312, "48,200 SKR", 540, 10, 25), onPrimary = {}) }
}
