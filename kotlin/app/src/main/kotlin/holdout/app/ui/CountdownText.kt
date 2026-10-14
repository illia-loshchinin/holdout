package holdout.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.Derive

/**
 * Time remaining, never a clock time or a date (design book, Countdown): the
 * `amount` face (tabular) in `accent`, turning `warning` inside the last 10
 * minutes of the day ([Derive.inWarningWindow], FR-047) — or [lateColor], which a sitting sets to the soft
 * warm tone. The text is
 * [Format.countdown] (`3d 4h`, `4h 06m`, `12m 30s`); the accessible name adds
 * "left" and says the warning in words, since colour is never the only signal.
 *
 * It never blocks anything: a sitting can always start. The start card adds
 * "unfinished reps won't count" itself.
 */
@Composable
fun CountdownText(seconds: Long, modifier: Modifier = Modifier, style: TextStyle = AmountStyle, lateColor: Color = HoldoutColors.Warning) {
    val late = Derive.inWarningWindow(seconds)
    val shown = Format.countdown(seconds)
    Text(
        text = shown,
        style = style,
        color = if (late) lateColor else HoldoutColors.Accent,
        modifier = modifier.semantics { contentDescription = if (late) "Under 10 minutes left, $shown" else "$shown left" },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CountdownTextPreview() {
    HoldoutTheme {
        androidx.compose.foundation.layout.Column {
            CountdownText(4 * 3_600L + 6 * 60)
            CountdownText(9 * 60L + 30)
        }
    }
}
