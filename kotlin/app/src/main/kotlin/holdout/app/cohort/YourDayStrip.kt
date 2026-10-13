package holdout.app.cohort

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import holdout.app.home.DayActions
import holdout.app.home.DayPhase
import holdout.app.home.DayState
import holdout.app.home.color
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.LabelStyle

/**
 * The member's day at the top of a Holdout's screen while their Holdout runs and today's
 * day is open (FR-069b): `DAY 2 · 5 push-ups · 6m 24s left` and the same day
 * button as Today — Start, Keep going, Submit day N — so the day starts here
 * without a trip to the other tab. On the chain it is one quiet line.
 * Unsent, it says so (Done — waiting for network, tap to submit).
 *
 * ```
 *  ┌ DAY 2 · 5 push-ups · 6m 24s left ┐   label, countdown in mono
 *  │ [ Start ]                         │   the day button (DayActions)
 *  └───────────────────────────────────┘
 *  Day 2 is done and on chain.              on chain: no card, no button
 * ```
 */
@Composable
internal fun YourDayStrip(day: DayState, onStart: () -> Unit, onSubmit: (dayIndex: Int) -> Unit, onGetTokens: () -> Unit, modifier: Modifier = Modifier) {
    if (day.phase == DayPhase.OnChain) {
        Body("Day ${day.day} is done and on chain.", modifier = modifier)
        return
    }
    HoldoutCard(modifier) {
        day.timeLeft?.let { Text(label(day, it), style = LabelStyle, color = HoldoutColors.TextSecondary) }
        // A finished day not on the chain says so before its button.
        if (day.phase != DayPhase.ToDo) Text(day.title, style = MaterialTheme.typography.titleMedium, color = day.tone.color())
        if (day.warning) Notice(day.text, warning = true)
        DayActions(day, onStart, onSubmit, onGetTokens)
    }
}

/** `DAY 2 · 5 push-ups · 6m 24s left`, the countdown in the amount face, in warning inside the last 10 minutes. */
private fun label(day: DayState, timeLeft: Long) = buildAnnotatedString {
    append("DAY ${day.day} · ${day.target} push-ups · ")
    withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = if (day.warning) HoldoutColors.Warning else HoldoutColors.Accent)) {
        append(Format.countdown(timeLeft))
    }
    append(" left")
}
