package holdout.app.cohort

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.ChainCheckBox
import holdout.app.ui.ChainUnreachable
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.CohortSnapshot
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * Cohort detail (US-7, T075, T082): days remaining, the member's own record,
 * the projected share, and how the crowd thinned day by day. It also carries
 * the two quiet end states: **"You finished · settlement in progress"** until
 * finalisation (contracts §5), and **claimed**.
 */
@Composable
fun CohortDetailScreen(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    stage: Stage,
    now: Long,
    lastClaimSignature: String?,
    onRoster: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The last chain read failed (spec 005): the line, Check again and the pull. */
    offline: Boolean = false,
    checking: Boolean = false,
    stillUnreachable: Boolean = false,
    onCheck: () -> Unit = {},
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now) }
    ChainCheckBox(offline, checking, onCheck, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HoldoutColors.Canvas)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = HoldoutColors.TextSecondary) }
            if (h.isTest) TestCohortBadge(c.dayLength)
            Text(Format.holdoutTitle(c), style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
            if (offline) ChainUnreachable(checking, stillUnreachable, onCheck)

            when (stage) {
                Stage.Settling -> HoldoutCard {
                    Text("You finished · settlement in progress", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
                    Body("Every member is being counted on chain. Your claim opens the moment it's done — nobody can change what you're owed.")
                }
                is Stage.Claimed -> HoldoutCard {
                    Text("Claimed · ${Format.skr(stage.amount)}", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
                    val uriHandler = LocalUriHandler.current
                    lastClaimSignature?.let { signature ->
                        val url = Format.explorer(signature)
                        // As on the claim screen: tapped, it opens in the browser; a phone with none leaves the address to read.
                        Body(
                            "Transaction (tap to open): $url",
                            modifier = Modifier.clickable(onClickLabel = "Open the transaction in the explorer") { runCatching { uriHandler.openUri(url) } },
                        )
                    }
                }
                else -> Unit
            }

            val daysLeft = (c.durationDays - Derive.dayIndex(c, now)).coerceIn(0, c.durationDays)
            HoldoutCard {
                SectionLabel("Where it stands")
                figures?.let { Text("${it.survivors.standing} still standing of ${c.memberCount}", style = AmountStyle, color = HoldoutColors.TextPrimary) }
                Text("Prize pool: ${figures?.let { Format.skr(it.projectedPool) } ?: Format.skr(c.pool)}", style = AmountStyle, color = HoldoutColors.Accent)
                if (now < Derive.end(c)) Body("$daysLeft day${if (daysLeft == 1) "" else "s"} to go.")
            }

            h.membership?.let { m ->
                HoldoutCard {
                    SectionLabel("Your record")
                    Text("${m.daysCompleted} of ${c.durationDays} days · stake ${Format.skr(m.stake)}", style = AmountStyle, color = HoldoutColors.TextPrimary)
                    if (figures != null && Derive.standing(m, c, now) && !c.settled) Body("Projected share if the crowd held here: ${Format.skr(figures.projectedShare(m))}")
                }
            }

            members?.let { list ->
                HoldoutCard {
                    SectionLabel("How the crowd thinned")
                    survivorHistory(c, list, now).forEach { (day, standing) -> Body("After day ${day + 1}: $standing standing") }
                }
            }
            SecondaryButton("The roster", onRoster)
        }
    }
}

/**
 * Members still standing after each deadline that has passed: those who had
 * completed more than that day, or finished. Read from `days_completed` alone,
 * so it needs no history the chain does not keep.
 */
internal fun survivorHistory(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): List<Pair<Int, Int>> {
    val passed = (0 until c.durationDays).filter { now > Derive.deadlineOf(c, it) }
    return passed.map { day -> day to members.count { it.daysCompleted > day } }
}
