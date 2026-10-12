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
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

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
 *  │ Done. The tokens are in your wallet. (outcome) │
 *  └─────────────────────────────────┘
 *  [ Get them ]       or  Waiting for your wallet…   PrimaryButton
 *  Back to the stake sheet                           QuietAction, only from the stake sheet
 *  Test tokens have no value                         tertiary note
 * ```
 *
 * No "← Back" inside the sheet: it closes on a drag or a tap on the scrim.
 * Opened from the stake sheet it offers [backLabel] (ES-0a) — quietly, and as the
 * main button once the tokens are in. There is no daily limit: the button stays
 * on after a claim, off only while the wallet is asked or the chain is unread.
 * The can't-pay-out variant never blames the member.
 */
@Composable
fun FaucetSheet(
    skr: ULong,
    lamports: Long,
    maxTopTier: ULong,
    busy: Boolean,
    outcome: FaucetOutcome?,
    onClaim: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Back to the stake sheet" when the sheet opened from there; null when it opened from Me. */
    backLabel: String? = null,
    /** The snapshot and chain time are both read; until then the balances are not shown and the tap is off. */
    ready: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Get test tokens", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        HoldoutCard {
            if (ready) {
                KeyValueRow("Test SKR", Format.skr(skr))
                KeyValueRow("Devnet SOL for fees", Format.sol(lamports))
            } else {
                Reading { SectionLabel("Reading the chain…") }
            }
            Body(if (maxTopTier > 0uL) "One tap: ${Format.skr(maxTopTier).removeSuffix(" SKR")} test SKR, plus SOL for fees." else "One tap: test SKR, plus SOL for fees.")
            Outcome(outcome)
        }
        when {
            // The sponsor pays for a member with no SOL, so no wallet may be asked at all: a neutral label.
            busy -> PrimaryButton("Sending…", onClaim, busy = true)
            outcome is FaucetOutcome.Funded && backLabel != null -> PrimaryButton(backLabel, onBack)
            else -> {
                PrimaryButton("Get them", onClaim, enabled = ready)
                if (backLabel != null) QuietAction(backLabel, onBack, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        Text(
            "Test tokens have no value",
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the last tap said, in one line: [Body] colours are the board's — done in `success`, anything refused in `warning`. */
@Composable
private fun Outcome(outcome: FaucetOutcome?) {
    when (outcome) {
        is FaucetOutcome.Funded -> Line("Done. The tokens are in your wallet.", HoldoutColors.Success)
        FaucetOutcome.CannotPayOut -> Line("The faucet can't pay out right now. Try again later.", HoldoutColors.Warning)
        FaucetOutcome.NoWallet -> Line("No Solana wallet on this phone.", HoldoutColors.Warning)
        is FaucetOutcome.Failed -> Line(outcome.message, HoldoutColors.Warning)
        null -> Unit
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetReadyPreview() = Over { FaucetSheet(4_750_000_000uL, 12_000_000L, 5_000_000_000uL, false, null, {}, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetFromStakePreview() = Over { FaucetSheet(0uL, 1_000L, 5_000_000_000uL, false, FaucetOutcome.CannotPayOut, {}, {}, backLabel = "Back to the stake sheet") }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetFundedPreview() = Over { FaucetSheet(9_750_000_000uL, 12_000_000L, 5_000_000_000uL, false, FaucetOutcome.Funded("sig"), {}, {}, backLabel = "Back to the stake sheet") }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetWaitingPreview() = Over { FaucetSheet(4_750_000_000uL, 12_000_000L, 5_000_000_000uL, true, null, {}, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 560)
@Composable
private fun FaucetFundedFromMePreview() = Over { FaucetSheet(9_750_000_000uL, 12_000_000L, 5_000_000_000uL, false, FaucetOutcome.Funded("sig"), {}, {}) }

@Composable
private fun Over(content: @Composable () -> Unit) {
    HoldoutTheme {
        Box(Modifier.fillMaxSize()) {
            Text("The screen that opened it", color = HoldoutColors.TextPrimary, modifier = Modifier.padding(Spacing.screenGutter))
            HoldoutSheet(onDismiss = {}) { content() }
        }
    }
}
