package holdout.app.cohort

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.Stage

/**
 * The forfeit (US-4, FR-052, T070): what happened, plainly; that **the streak
 * is still alive** — a forfeit ends a Holdout, never the Daily 25 streak — and
 * the two ways back: today's Daily 25, and the next 7-day Holdout.
 */
@Composable
fun ForfeitScreen(
    h: Holdout,
    stage: Stage.Forfeited,
    streak: Int,
    onDaily25: () -> Unit,
    /** The next 7-day Holdout's stake sheet; null when none is open to join. */
    onNextHoldout: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = h.cohort
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Home", color = HoldoutColors.TextSecondary) }
        if (h.isTest) TestCohortBadge(c.dayLength)
        SectionLabel(Format.holdoutTitle(c))
        Text("You missed day ${stage.daysLasted + 1}.", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.Warning)
        HoldoutCard {
            Body("You lasted ${stage.daysLasted} of ${c.durationDays} days. The deadline passed before day ${stage.daysLasted + 1} reached the chain, so your stake of ${h.membership?.let { Format.skr(it.stake) } ?: "—"} went into this Holdout's prize pool — 70 % to the pool, 30 % fee.")
            if (!stage.recorded) Body("It is final from the deadline; the record catches up on the next automation pass.")
        }
        HoldoutCard {
            Text(if (streak > 0) "Your $streak-day streak is still alive." else "Your streak is untouched.", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
            Body("A forfeit ends a Holdout, never your streak. The Daily 25 keeps it going.")
        }
        SectionLabel("Two ways back")
        PrimaryButton("Today's Daily 25", onDaily25)
        onNextHoldout?.let { SecondaryButton("The next 7-day Holdout", it) }
    }
}
