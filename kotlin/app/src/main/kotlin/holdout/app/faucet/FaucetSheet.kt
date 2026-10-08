package holdout.app.faucet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.HoldoutDay

/**
 * The faucet (ES-0, ES-0a, ES-0b, T043): one tap funds a whole cycle — test
 * SKR for the top chip, and devnet SOL for fees when the wallet has none. The
 * used-today variant says when it returns; the can't-pay-out variant never
 * blames the member.
 */
@Composable
fun FaucetSheet(
    skr: ULong,
    lamports: Long,
    maxTopTier: ULong,
    claimedToday: Boolean,
    now: Long,
    busy: Boolean,
    outcome: FaucetOutcome?,
    onClaim: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Back", color = HoldoutColors.TextSecondary) }
        SectionLabel("Test tokens")
        Text("You hold ${Format.skr(skr)}", style = AmountStyle, color = HoldoutColors.TextPrimary)
        Text("and %.3f devnet SOL for fees".format(lamports / 1e9), style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        HoldoutCard {
            Body("One tap a day gives you ${Format.skr(maxTopTier)} — enough for the largest chip — and devnet SOL for fees if you have none.")
            Body("It's a test network with a test token. Nothing here has value.")
        }
        when (outcome) {
            is FaucetOutcome.Funded -> Notice("Done. The tokens are in your wallet.")
            is FaucetOutcome.UsedToday -> Notice("Today's tokens are already claimed. Back in ${Format.countdown(outcome.returnsAt - now)}.", warning = true)
            FaucetOutcome.CannotPayOut -> Notice("The faucet can't pay out right now. It isn't something you did — it refills automatically, try again later today.", warning = true)
            FaucetOutcome.NoWallet -> Notice("No Solana wallet on this phone.", warning = true)
            is FaucetOutcome.Failed -> Notice(outcome.message, warning = true)
            null -> Unit
        }
        if (claimedToday && outcome == null) {
            Notice("Today's tokens are already claimed. Back in ${Format.countdown(HoldoutDay.secondsLeftIn(now))}.", warning = true)
        }
        PrimaryButton(if (busy) "Sending…" else "Get test tokens", onClaim, enabled = !claimedToday, busy = busy)
    }
}
