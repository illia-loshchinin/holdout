package holdout.app.lobby

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
import holdout.app.ui.Notice
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.CohortStatus
import holdout.core.Derive

/**
 * The lobby (US-2, ES-12a, T060): countdowns to the cutoff and the start, the
 * roster count, the held stake, and **unconditional leave** until joining
 * closes (FR-009). Once it closes: the locked variant — the final roster and
 * no actions, until the start.
 */
@Composable
fun LobbyScreen(
    h: Holdout,
    now: Long,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onLeave: () -> Unit,
    onRoster: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = h.cohort
    val locked = h.status(now) == CohortStatus.Locked
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
        SectionLabel(if (locked) "Lobby · locked" else "Lobby")
        Text("${c.durationDays}-day Holdout", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        Text("starts in", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        Text(Format.countdown(c.start - now), style = DisplayStyle, color = HoldoutColors.Accent)

        HoldoutCard {
            Text("${c.memberCount} members · ${Format.skr(Derive.atStake(c))} at stake", style = AmountStyle, color = HoldoutColors.TextPrimary)
            h.membership?.let { Body("Your stake: ${Format.skr(it.stake)} — held by the contract, not by us.") }
            if (locked) {
                Body("Joining has closed. This is the final roster.")
            } else {
                Body("Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}. Until then you can leave with your full stake back.")
            }
            Body("From the start: ${c.repsPerDay} push-ups before each day's deadline, at 45°, from your toes.")
        }
        SecondaryButton("See who's in", onRoster)
        Notice(message, messageIsWarning)
        if (!locked) SecondaryButton(if (busy) "Waiting for the wallet…" else "Leave — full refund", onLeave, enabled = !busy)
    }
}
