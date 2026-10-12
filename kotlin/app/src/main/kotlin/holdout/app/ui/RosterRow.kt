package holdout.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * One member of a Holdout on the roster (design book, Roster row; FR-062,
 * spec 003): `size-button` tall with a `hairline` divider. The member is named
 * by [MemberIdentity], so a Seeker ID and a shortened address look like the same
 * kind of row, and "· you" follows the connected wallet ([isYou]). While
 * [standing] the row ends in the days, mono, `success`, with a tick
 * ("12 days ✓"); once out the identity drops to `text-tertiary` and the row
 * ends in "lasted N days". A [scripted] member carries `◇ SCRIPTED` on every
 * roster.
 *
 * **No stake, prize or payout amount, ever**, and no mark that a name is
 * verified or an address missing. The accessible name carries the state:
 * "mira.skr, still standing, 12 days" / "Ty7A…kM4s, scripted, out, lasted 6 days".
 */
@Composable
fun RosterRow(wallet: String, isYou: Boolean, standing: Boolean, days: Int, scripted: Boolean, modifier: Modifier = Modifier) {
    val who = Format.identity(wallet, LocalSeekerNames.current[wallet], ROW_NAME_BUDGET) + if (isYou) " · you" else ""
    val spoken = buildString {
        append(who)
        if (scripted) append(", scripted")
        append(if (standing) ", still standing, ${dayCount(days)}" else ", out, lasted ${dayCount(days)}")
    }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.primaryButtonHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            MemberIdentity(
                wallet = wallet,
                isYou = isYou,
                budget = ROW_NAME_BUDGET,
                color = if (standing) HoldoutColors.TextPrimary else HoldoutColors.TextTertiary,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f),
            )
            if (scripted) ScriptedChip()
            if (standing) {
                Text("${dayCount(days)} ✓", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"), color = HoldoutColors.Success)
            } else {
                Text("lasted ${dayCount(days)}", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextTertiary)
            }
        }
        HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
    }
}

private fun dayCount(days: Int) = if (days == 1) "1 day" else "$days days"

/** Characters reserved for a name in a row, so a Seeker ID arriving never moves the days beside it. */
private const val ROW_NAME_BUDGET = 14

private const val PREVIEW_WALLET = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RosterRowPreview() {
    HoldoutTheme {
        CompositionLocalProvider(LocalSeekerNames provides mapOf(PREVIEW_WALLET to "nova.skr")) {
            Column {
                RosterRow(PREVIEW_WALLET, isYou = true, standing = true, days = 12, scripted = false)
                RosterRow("4KdRaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa91X", isYou = false, standing = true, days = 12, scripted = true)
                RosterRow("Lx3Faaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa0qBe", isYou = false, standing = false, days = 3, scripted = false)
            }
        }
    }
}
