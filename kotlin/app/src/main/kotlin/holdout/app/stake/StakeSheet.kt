package holdout.app.stake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.Journey
import holdout.repcounter.Stakes

/**
 * Stake confirm (US-6, FR-041, FR-059, T059): the four chips with their exact
 * SKR amounts and approximate dollar labels, the daily obligation, time to the
 * cutoff and the start, and the pool as it stands.
 *
 * **The counting rule is read here, before anything is staked** (FR-021a,
 * 002 FR-012/FR-014): at 45° only, full push-ups from the toes — not
 * discovered at the first sitting.
 */
@Composable
fun StakeSheet(
    h: Holdout,
    skrBalance: ULong,
    /** The wallet's SOL, in lamports: the member pays the join's fee and rent. */
    lamports: Long,
    now: Long,
    joinBlocker: Journey.JoinBlocker?,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onStake: (tier: Int) -> Unit,
    onGetTokens: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = h.cohort
    var tier by remember(h.address) { mutableIntStateOf(0) }
    val chip = c.chips[tier]
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = HoldoutColors.TextSecondary) }
        if (h.isTest) TestCohortBadge(c.dayLength)
        Text(Format.holdoutTitle(c), style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)

        SectionLabel("Your stake")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            c.chips.forEachIndexed { i, amount ->
                val selected = i == tier
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (selected) HoldoutColors.Surface3 else HoldoutColors.Surface1, MaterialTheme.shapes.medium)
                        .border(BorderStroke(1.dp, if (selected) HoldoutColors.Accent else HoldoutColors.Hairline), MaterialTheme.shapes.medium)
                        .clickable { tier = i }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Format.skr(amount), style = AmountStyle, color = if (selected) HoldoutColors.Accent else HoldoutColors.TextPrimary)
                    Text(Format.tierUsd(i), style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
        }
        Body("Dollar figures are approximate. Everything is staked and paid in SKR, on a test network with a test token.")

        HoldoutCard {
            SectionLabel("Every day")
            Text("${c.repsPerDay} push-ups before each day's deadline, for ${c.durationDays} days", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
            Body("Miss one and you lose your stake. ${Format.lostStakeSplit(c, "the prize pool")}")
            Body("Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))} · starts in ${Format.countdown(c.start - now)}")
            Body("You can leave for a full refund until joining closes.")
            Body("If you allow notifications, this phone reminds you when it starts, halfway through each day and before each deadline you haven't met yet, and every hour, silently, once you've done a day that isn't on chain yet. The reminders never leave the phone.")
        }

        HoldoutCard {
            SectionLabel("The pool")
            Text("${Format.skr(Derive.atStake(c))} at stake · ${c.memberCount} joined", style = AmountStyle, color = HoldoutColors.Accent)
            Body("Finish and you take back your stake plus your share of what the members who miss a day leave behind.")
            Body("If everyone finishes, you get exactly your stake back.")
        }

        HoldoutCard {
            SectionLabel("How reps are counted here")
            Text(
                if (Stakes.ofForm(c.form) == Stakes.EASY) Stakes.EASY_RULE else Stakes.STAKED_RULE,
                style = MaterialTheme.typography.bodyLarge,
                color = HoldoutColors.TextPrimary,
            )
        }

        val short = skrBalance < chip
        // Enough SKR is not enough: the join is the member's own transaction, paid in SOL.
        val noFees = lamports < holdout.app.faucet.FaucetClient.MEMBER_CAN_PAY_LAMPORTS
        when {
            joinBlocker != null -> Notice(
                when (joinBlocker) {
                    Journey.JoinBlocker.JoiningClosed -> "Joining has closed for this Holdout."
                    Journey.JoinBlocker.LobbyPlaceTaken -> "You already hold a place in a Holdout that hasn't started."
                    Journey.JoinBlocker.Overlaps -> "This Holdout starts before your current one ends."
                },
                warning = true,
            )
            short -> {
                Notice("You hold ${Format.skr(skrBalance)}. This chip needs ${Format.skr(chip)}.", warning = true)
                SecondaryButton("Get test tokens", onGetTokens)
            }
            noFees -> {
                Notice("Your wallet has too little devnet SOL to pay for joining. The faucet sends some with the test tokens.", warning = true)
                SecondaryButton("Get test tokens", onGetTokens)
            }
        }
        Notice(message, messageIsWarning)
        PrimaryButton(
            text = if (busy) "Waiting for the wallet…" else "Stake ${Format.skr(chip)}",
            onClick = { onStake(tier) },
            enabled = joinBlocker == null && !short && !noFees,
            busy = busy,
        )
    }
}
