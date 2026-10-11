package holdout.app.faucet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.HoldoutSheet
import holdout.app.ui.KeyValueRow
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.HoldoutDay

/**
 * The faucet, as the content of a [HoldoutSheet] (ES-0, ES-0a, ES-0b, T043;
 * rulings G8; board FaucetSheet, DS Sheet): one tap funds a whole cycle — test
 * SKR for the top chip, and devnet SOL for fees when the wallet has none.
 *
 * ```
 *  Get test tokens                                   titleMedium
 *  ┌ Test SKR              4,750 SKR ┐               HoldoutCard · KeyValueRow
 *  │ Devnet SOL for fees   0.012 SOL │
 *  │ One tap: 5,000 test SKR, plus SOL for fees. │
 *  │ Claimed today. Back in 6h 40m.   (outcome)  │
 *  └─────────────────────────────────┘
 *  [ Get them ]       or  Waiting for your wallet…   PrimaryButton
 *  Back to the stake sheet                           QuietAction, only from the stake sheet
 *  Once a day · test tokens have no value            tertiary note
 * ```
 *
 * No "← Back" inside the sheet: it closes on a drag or a tap on the scrim.
 * Opened from the stake sheet it offers [backLabel] (ES-0a) — quietly, and as the
 * main button once the tokens are in. The used-today variant says when the
 * faucet returns; the can't-pay-out variant never blames the member.
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
    /** "Back to the stake sheet" when the sheet opened from there; null when it opened from Me. */
    backLabel: String? = null,
) {
    val spent = outcome is FaucetOutcome.Funded || outcome is FaucetOutcome.UsedToday || (claimedToday && outcome == null)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Get test tokens", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        HoldoutCard {
            KeyValueRow("Test SKR", Format.skr(skr))
            KeyValueRow("Devnet SOL for fees", "%.3f SOL".format(lamports / LAMPORTS_PER_SOL))
            Body(if (maxTopTier > 0uL) "One tap: ${Format.skr(maxTopTier).removeSuffix(" SKR")} test SKR, plus SOL for fees." else "One tap: test SKR, plus SOL for fees.")
            Outcome(outcome, claimedToday, now)
        }
        when {
            busy -> PrimaryButton(Format.WAITING_FOR_WALLET, onClaim, busy = true)
            spent && backLabel != null -> PrimaryButton(backLabel, onBack)
            spent -> PrimaryButton("Get them", onClaim, enabled = false)
            else -> {
                PrimaryButton("Get them", onClaim)
                if (backLabel != null) QuietAction(backLabel, onBack, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        Text(
            "Once a day · test tokens have no value",
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the last tap said, in one line: [Body] colours are the board's — done in `success`, anything refused in `warning`. */
@Composable
private fun Outcome(outcome: FaucetOutcome?, claimedToday: Boolean, now: Long) {
    when (outcome) {
        is FaucetOutcome.Funded -> Line("Done. The tokens are in your wallet.", HoldoutColors.Success)
        is FaucetOutcome.UsedToday -> Line("Claimed today. Back in ${Format.countdown(outcome.returnsAt - now)}.", HoldoutColors.Warning)
        FaucetOutcome.CannotPayOut -> Line("The faucet can't pay out right now. Try again later.", HoldoutColors.Warning)
        FaucetOutcome.NoWallet -> Line("No Solana wallet on this phone.", HoldoutColors.Warning)
        is FaucetOutcome.Failed -> Line(outcome.message, HoldoutColors.Warning)
        null -> if (claimedToday) Line("Claimed today. Back in ${Format.countdown(HoldoutDay.secondsLeftIn(now))}.", HoldoutColors.Warning)
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
}

private const val LAMPORTS_PER_SOL = 1e9

private const val PREVIEW_NOW = 1_791_176_400L + 3 * 86_400L

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetReadyPreview() = Over { FaucetSheet(4_750_000_000uL, 12_000_000L, 5_000_000_000uL, false, PREVIEW_NOW, false, null, {}, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetFromStakePreview() = Over { FaucetSheet(0uL, 1_000L, 5_000_000_000uL, false, PREVIEW_NOW, false, FaucetOutcome.CannotPayOut, {}, {}, backLabel = "Back to the stake sheet") }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetFundedPreview() = Over { FaucetSheet(9_750_000_000uL, 12_000_000L, 5_000_000_000uL, true, PREVIEW_NOW, false, FaucetOutcome.Funded("sig"), {}, {}, backLabel = "Back to the stake sheet") }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetWaitingPreview() = Over { FaucetSheet(4_750_000_000uL, 12_000_000L, 5_000_000_000uL, false, PREVIEW_NOW, true, null, {}, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetUsedPreview() = Over { FaucetSheet(4_750_000_000uL, 12_000_000L, 5_000_000_000uL, true, PREVIEW_NOW, false, null, {}, {}) }

@Composable
private fun Over(content: @Composable () -> Unit) {
    HoldoutTheme {
        Box(Modifier.fillMaxSize()) {
            Text("The screen that opened it", color = HoldoutColors.TextPrimary, modifier = Modifier.padding(Spacing.screenGutter))
            HoldoutSheet(onDismiss = {}) { content() }
        }
    }
}
