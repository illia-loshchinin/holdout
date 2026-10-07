package holdout.app.ladder

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
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.LeagueParams

/**
 * The ladder (US-1, FR-003, T044, T085): the Daily 25 at the bottom rung, both
 * Holdout lengths open, and the Human Flag locked above them.
 *
 * The 21-day league's requirement — a 7-day clear — is stated and recorded but
 * not enforced in this build (`ladder_gate_enabled = false`), and the screen
 * says both: that it is the rule, and that it is off here.
 */
@Composable
fun LadderScreen(
    open: List<Holdout>,
    qualified: Boolean,
    now: Long,
    onDaily25: () -> Unit,
    onHoldout: (Holdout) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        TextButton(onClick = onBack) { Text("← Home", color = HoldoutColors.TextSecondary) }
        Text("The ladder", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)

        SectionLabel("Locked")
        HoldoutCard {
            Text("Human Flag", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextTertiary)
            Body("Coming after the 21-day Holdout. Not in this build.")
        }

        SectionLabel("21-day Holdout")
        Body(
            if (qualified) {
                "Unlocked: you've cleared a 7-day Holdout."
            } else {
                "The rule: clear a 7-day Holdout first. In this build both lengths are open from the start — your unlock is still recorded."
            },
        )
        HoldoutList(open.filter { it.cohort.leagueId == LeagueParams.LEAGUE_21D }, now, onHoldout)

        SectionLabel("7-day Holdout")
        Body("The entry rung and the comeback lane. A new one starts every Monday 05:00 UTC.")
        HoldoutList(open.filter { it.cohort.leagueId == LeagueParams.LEAGUE_7D }, now, onHoldout)

        val tests = open.filter { it.isTest }
        if (tests.isNotEmpty()) {
            SectionLabel("Short test cohort")
            HoldoutList(tests, now, onHoldout)
        }

        SectionLabel("The Daily 25")
        HoldoutCard(Modifier.clickable(onClick = onDaily25)) {
            Text("25 push-ups, free, every day", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
            Body("Builds your streak. Stakes nothing, pays nothing.")
        }
    }
}

@Composable
private fun HoldoutList(holdouts: List<Holdout>, now: Long, onHoldout: (Holdout) -> Unit) {
    if (holdouts.isEmpty()) {
        Body("None open to join right now.")
        return
    }
    holdouts.forEach { HoldoutOffer(it, now, onClick = { onHoldout(it) }) }
}

/**
 * A Holdout open to join, as the ladder, home and the connect screen show it:
 * **at stake** — the stakes already held — joined count and the
 * time until joining closes (FR-053). Never a promised amount.
 */
@Composable
fun HoldoutOffer(h: Holdout, now: Long, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = h.cohort
    HoldoutCard(if (onClick != null) modifier.clickable(onClick = onClick) else modifier) {
        if (h.isTest) TestCohortBadge(c.dayLength)
        Text(
            "${Format.holdoutTitle(c)} · ${c.repsPerDay} push-ups a day",
            style = MaterialTheme.typography.titleMedium,
            color = HoldoutColors.TextPrimary,
        )
        Text("${Format.skr(Derive.atStake(c))} at stake · ${c.memberCount} joined", style = AmountStyle, color = HoldoutColors.Accent)
        Body("Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))} · starts in ${Format.countdown(c.start - now)}")
    }
}
