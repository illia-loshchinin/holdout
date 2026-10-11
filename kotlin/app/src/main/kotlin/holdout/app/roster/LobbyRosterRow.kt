package holdout.app.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import holdout.app.ui.Format
import holdout.app.ui.LocalSeekerNames
import holdout.app.ui.MemberIdentity
import holdout.app.ui.ScriptedChip
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing

/**
 * A member of a Holdout that has not started: the same row as the roster's
 * ([holdout.app.ui.RosterRow]), ending in "in" instead of days — nobody has had
 * a deadline yet, so nobody has lasted anything. No amount, `◇ SCRIPTED` where it applies.
 */
@Composable
fun LobbyRosterRow(wallet: String, isYou: Boolean, scripted: Boolean, modifier: Modifier = Modifier) {
    val who = Format.identity(wallet, LocalSeekerNames.current[wallet], NAME_BUDGET) + if (isYou) " · you" else ""
    val spoken = who + (if (scripted) ", scripted" else "") + ", in the lobby"
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.primaryButtonHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            MemberIdentity(
                wallet = wallet,
                isYou = isYou,
                budget = NAME_BUDGET,
                color = HoldoutColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f),
            )
            if (scripted) ScriptedChip()
            Text("in", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success)
        }
        HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
    }
}

private const val NAME_BUDGET = 14
