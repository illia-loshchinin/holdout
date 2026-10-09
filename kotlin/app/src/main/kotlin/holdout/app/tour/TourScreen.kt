package holdout.app.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import holdout.app.ui.Body
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.repcounter.Stakes

/**
 * The first-run tour (FR-067, T042): three cards, skippable at any card,
 * re-openable from About Holdout. Members meet the product here, and anyone
 * can run the whole cycle on a short test Holdout (FR-044a).
 */
@Composable
fun TourScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var card by remember { mutableIntStateOf(0) }
    val cards = TOUR
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections, Alignment.CenterVertically),
    ) {
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onDone) { Text("Skip", color = HoldoutColors.TextSecondary) }
        }
        SectionLabel("${card + 1} of ${cards.size}")
        Text(cards[card].title, style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        cards[card].lines.forEach { Body(it) }
        PrimaryButton(if (card == cards.lastIndex) "Start" else "Next", { if (card == cards.lastIndex) onDone() else card++ })
        Text("TEST NETWORK · TEST TOKEN", style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

private data class Card(val title: String, val lines: List<String>)

private val TOUR = listOf(
    Card(
        "The Daily 25",
        listOf(
            "25 push-ups, free, every day, counted by this phone's camera.",
            "Do them at once or 10 at a time. It builds your streak and stakes nothing.",
        ),
    ),
    Card(
        "A Holdout",
        listOf(
            "Put SKR on yourself with everyone who starts that Monday: Easy, 7 days, push-ups from the knees count; Hard, 21 days, from your toes.",
            "Miss a day and your stake goes to the prize pool. Finish, and take back your stake plus a share of what the others left.",
            "Leave any time before joining closes, for a full refund.",
        ),
    ),
    Card(
        "How reps are counted",
        listOf(
            "The Daily 25: the phone at 45°, as in a Holdout. Push-ups from the knees are welcome — it tells you how many came from your toes.",
            "Easy Holdouts: the phone at 45°, push-ups from the knees count.",
            Stakes.STAKED_RULE.replace("Staked Holdouts", "Hard Holdouts"),
            "The camera never leaves the phone — no video, no landmarks, nothing uploaded.",
        ),
    ),
)
