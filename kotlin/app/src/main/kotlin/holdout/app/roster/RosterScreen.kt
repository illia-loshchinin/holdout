package holdout.app.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.MemberIdentity
import holdout.app.ui.Reading
import holdout.app.ui.ReportShownWallets
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.ScriptedWallets

/**
 * The roster (FR-062, FR-064, T074, T089): still standing first, then the
 * fallen by days lasted. **No amounts anywhere** — a roster ranks nobody by
 * money (FR-063, constitution XIII). Every scripted member is labelled, from
 * the bundled list, with no server (FR-043a).
 *
 * Members show by Seeker ID where they have one (spec 003 FR-002), looked up
 * only for the rows on screen ([onShowing], FR-012). Names never change the
 * order or a label (FR-008).
 */
@Composable
fun RosterScreen(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    me: String?,
    now: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowing: (Collection<String>) -> Unit = {},
) {
    val c = h.cohort
    val roster = members?.let { Derive.roster(c, it, now, ScriptedWallets.keys) }
    val list = rememberLazyListState()
    // Member rows are keyed by their wallet; the header rows have no key of their own.
    ReportShownWallets(list, onShowing) { it as? String }
    LazyColumn(
        state = list,
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        item { TextButton(onClick = onBack) { Text("← Back", color = HoldoutColors.TextSecondary) } }
        if (h.isTest) item { TestCohortBadge(c.dayLength) }
        item { Text("${Format.holdoutTitle(c)} · roster", style = MaterialTheme.typography.headlineSmall, color = HoldoutColors.TextPrimary) }
        item { Body("Scripted members are real wallets under the same rules; their days are not camera-verified. They top a Holdout up to its minimum only.") }
        if (roster == null) {
            item { Reading { Body("Reading devnet…") } }
            return@LazyColumn
        }
        val standing = roster.count { it.standing }
        item { SectionLabel("Still standing · $standing") }
        items(roster, key = { it.member.wallet.base58() }) { entry ->
            val address = entry.member.wallet.base58()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MemberIdentity(
                    address,
                    isYou = address == me,
                    budget = 20,
                    color = if (entry.standing) HoldoutColors.TextPrimary else HoldoutColors.TextTertiary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    buildString {
                        if (entry.scripted) append("SCRIPTED · ")
                        append(if (entry.standing) "standing" else "out after ${entry.daysLasted} day${if (entry.daysLasted == 1) "" else "s"}")
                    },
                    style = LabelStyle,
                    color = if (entry.standing) HoldoutColors.Success else HoldoutColors.Warning,
                )
            }
        }
    }
}
