package holdout.app.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.chain.Holdout
import holdout.app.home.HoldoutOffer
import holdout.app.home.RunningHoldoutCard
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.Wordmark
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.CohortStatus

/**
 * Connect wallet — required before anything else, **even for the free Daily 25**
 * (FR-001a, screen map A1, T039, T041).
 *
 * The wordmark, the promise in two lines, one line each for the Daily 25 and a Holdout, and
 * the Holdouts live right now — still standing and prize pool for the running
 * ones, joined count and **at stake** for the open ones — read from devnet
 * with no wallet. Then the button. Nothing else is reachable before a wallet
 * is connected (FR-001a, amended 2026-10-09 by spec 004).
 *
 * With no wallet on the phone ([noWallet], ES-0): what a wallet is and how to
 * get one.
 */
@Composable
fun ConnectScreen(
    busy: Boolean,
    message: String?,
    noWallet: Boolean,
    live: List<Holdout>,
    now: Long?,
    /** Rosters read so far, by cohort: what a running Holdout's figures are derived from. */
    rosters: Map<com.solana.publickey.SolanaPublicKey, List<holdout.core.MembershipSnapshot>> = emptyMap(),
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems, Alignment.CenterVertically),
    ) {
        Wordmark()
        Text(
            text = "Stake on yourself.\nProve it every day.",
            style = MaterialTheme.typography.headlineMedium,
            color = HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Body("The Daily 25 — 25 push-ups, free, counted on this phone.", center = true)
        Body("A Holdout — put SKR on it and finish with everyone who started with you.", center = true)

        if (now != null && live.isNotEmpty()) {
            SectionLabel("Live now")
            live.forEach { h ->
                if (h.status(now) == CohortStatus.Running) {
                    RunningHoldoutCard(h, now, rosters[h.address], onClick = null)
                } else {
                    HoldoutOffer(h, now, onClick = null)
                }
            }
        }

        if (noWallet) {
            HoldoutCard {
                Text("You need a Solana wallet", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                Body("A wallet is an app that holds your keys and signs for you. Holdout never holds them.")
                Body("On a Seeker, Seed Vault Wallet is built in. On other phones, install a Solana wallet that supports Mobile Wallet Adapter, set it to devnet, then come back.")
            }
        }

        PrimaryButton(if (busy) Format.WAITING_FOR_WALLET else "Connect wallet", onConnect, busy = busy)
        Text("TEST NETWORK · TEST TOKEN", style = LabelStyle, color = HoldoutColors.TextTertiary)
        Notice(message, warning = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ConnectPreview() {
    HoldoutTheme { ConnectScreen(busy = false, message = null, noWallet = false, live = emptyList(), now = null, onConnect = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun NoWalletPreview() {
    HoldoutTheme { ConnectScreen(busy = false, message = null, noWallet = true, live = emptyList(), now = null, onConnect = {}) }
}
