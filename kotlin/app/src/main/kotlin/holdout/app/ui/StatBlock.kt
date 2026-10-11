package holdout.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The signature of the app (design book, Stat block): an optional context
 * [label] in `text-tertiary`, one big tabular number, and its [caption] in
 * `label` style beneath, `space-1` apart, centred. The number is a `counter`
 * (56 sp), or a `display` (40 sp) when [display] is set. A ratio drops its
 * denominator ([of]) to `text-tertiary`: `189 / 312`.
 *
 * Never two counter blocks on one screen, and no units inside the number: the
 * unit is the caption. [valueColor] is for the one state that earns it
 * (`success` once the Daily 25 is done).
 */
@Composable
fun StatBlock(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    of: String? = null,
    display: Boolean = false,
    valueColor: Color = HoldoutColors.TextPrimary,
) {
    val spoken = listOfNotNull(label, if (of != null) "$value of $of" else value, caption).joinToString(", ")
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        if (label != null) {
            Text(label.uppercase(), style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
        Text(
            text = buildAnnotatedString {
                append(value)
                if (of != null) {
                    withStyle(SpanStyle(color = HoldoutColors.TextTertiary)) { append(" / $of") }
                }
            },
            style = if (display) DisplayStyle else CounterStyle,
            color = valueColor,
        )
        Text(caption.uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StatBlockRatioPreview() {
    HoldoutTheme { StatBlock(value = "189", of = "312", caption = "still standing", label = "Habit · 21-day · day 12 of 21") }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StatBlockDisplayPreview() {
    HoldoutTheme { StatBlock(value = "25", caption = "push-ups", display = true) }
}
