package holdout.app.holdouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * One running Holdout the member is not in, to watch (FR-062a, ruling G14):
 * `Habit · 21-day · day 12 of 21` over `189 still standing of 312 · prize pool 48,200 SKR`,
 * a chevron at the end. It opens that Holdout's detail and from there its roster;
 * it offers no join. The Standing tab's empty state and Holdouts show the same card.
 */
@Composable
fun RunningNowCard(row: RunningHoldout, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    HoldoutCard(modifier.clickable(onClickLabel = "Watch this Holdout", onClick = onOpen)) {
        if (row.test) TestCohortBadge(row.testDayLength)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                Body(row.line)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextTertiary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RunningNowCardPreview() {
    HoldoutTheme {
        RunningNowCard(RunningHoldout("w", "Habit · 21-day · day 12 of 21", "189 still standing of 312 · prize pool 48,200 SKR", false), onOpen = {})
    }
}
