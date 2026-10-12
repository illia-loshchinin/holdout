package holdout.app.sitting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.CountdownText
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * What the Stopped page says (board 20; ES-1, ES-2). [banked] reps were saved in
 * full blocks; [notKept] were done in the sitting that ended and are gone. After a
 * [crash] only the banked figure is known and nothing was done "just now".
 * [secondsLeft] is the day's deadline (a Holdout) or the end of today (the Daily
 * 25), null while the chain is unread. [reps] is the day's target.
 */
data class StoppedUi(
    val header: String,
    val reps: Int,
    val banked: Int,
    val notKept: Int,
    val secondsLeft: Long?,
    val crash: Boolean = false,
) {
    val done: Int get() = banked + notKept
    val stillToDo: Int get() = (reps - banked).coerceAtLeast(0)

    companion object {
        /**
         * The page for a sitting the member just stopped, or null when there is
         * nothing to say: nothing was banked and nothing discarded *in that
         * sitting* (ruling C-D11), so the member goes straight back.
         */
        fun afterStop(header: String, reps: Int, bankedNow: Int, bankedAtStart: Int, discarded: Int, secondsLeft: Long?): StoppedUi? {
            if (bankedNow <= bankedAtStart && discarded <= 0) return null
            return StoppedUi(header, reps, banked = bankedNow, notKept = discarded, secondsLeft = secondsLeft)
        }
    }
}

/**
 * Stopped part-way (board 20), and the crash variant: what was banked, what was
 * not kept, the time left and what is still to do — never a way to lose the
 * banked reps, which stay until the deadline. One button, back to today.
 *
 * ```
 *               DAY 12 · 25 REPS
 *   17 done today                    Your 10 banked reps are safe.   ← crash
 *   ━━━━━━━  ┅┅┅┅┅┅┅  ░░░░░
 *   10 banked            7 not kept
 *   Only full blocks of 10 are kept. Come back before the day ends.
 *   ┌ Left today              5h 12m ┐
 *   │ Still to do        15 push-ups │
 *   [          Back to today          ]
 * ```
 */
@Composable
internal fun StoppedScreen(ui: StoppedUi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.betweenSections),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text(ui.header.uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections, Alignment.CenterVertically),
        ) {
            if (ui.crash) CrashSummary(ui) else StoppedSummary(ui)
            HoldoutCard {
                ui.secondsLeft?.let { left ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Left today", style = LabelStyle, color = HoldoutColors.TextSecondary)
                        CountdownText(left)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Still to do", style = LabelStyle, color = HoldoutColors.TextSecondary)
                    Text(if (ui.stillToDo == 1) "1 push-up" else "${ui.stillToDo} push-ups", style = AmountStyle, color = HoldoutColors.TextPrimary)
                }
            }
        }
        PrimaryButton("Back to today", onBack)
    }
}

@Composable
private fun StoppedSummary(ui: StoppedUi) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Text("${ui.done}", style = CounterStyle.copy(textAlign = TextAlign.Start), color = HoldoutColors.TextPrimary)
        Text("done today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = HoldoutColors.TextSecondary, modifier = Modifier.padding(bottom = Spacing.base))
    }
    StoppedBars(ui.reps, ui.banked, ui.notKept, "${ui.banked} banked, ${ui.notKept} not kept, ${ui.reps} push-ups in the day")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (ui.banked > 0) Text("${ui.banked} banked", style = LabelStyle, color = HoldoutColors.Success) else Spacer(Modifier)
        if (ui.notKept > 0) Text("${ui.notKept} not kept", style = LabelStyle, color = HoldoutColors.Warning)
    }
    Text(
        "Only full blocks of 10 are kept. Come back before the day ends.",
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextSecondary,
    )
}

@Composable
private fun CrashSummary(ui: StoppedUi) {
    Text(
        text = if (ui.banked == 1) "Your 1 banked rep is safe." else "Your ${ui.banked} banked reps are safe.",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Light,
        color = HoldoutColors.TextPrimary,
    )
    StoppedBars(ui.reps, ui.banked, 0, "${ui.banked} banked, ${ui.reps} push-ups in the day")
    Text(
        "The app closed mid-sitting. Reps after your last 10 are gone.",
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextSecondary,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun StoppedPreview() {
    HoldoutTheme { StoppedScreen(StoppedUi(SittingCopy.countingLabel(12, 25), 25, banked = 10, notKept = 7, secondsLeft = 5 * 3_600L + 12 * 60), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun StoppedDailyPreview() {
    HoldoutTheme { StoppedScreen(StoppedUi(SittingCopy.countingLabel(null, 25), 25, banked = 20, notKept = 0, secondsLeft = 5 * 3_600L + 12 * 60), {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun StoppedCrashPreview() {
    HoldoutTheme { StoppedScreen(StoppedUi(SittingCopy.countingLabel(12, 25), 25, banked = 10, notKept = 0, secondsLeft = 5 * 3_600L + 12 * 60, crash = true), {}) }
}
