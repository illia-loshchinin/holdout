package holdout.app.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.WalletUiState
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * Connect wallet — required before anything else, **even for the free Daily 25**
 * (FR-001a, PRD §10.1 screen 2).
 *
 * One identity from the first tap. Guest mode was considered and dropped, and
 * the README names the cost of that honestly.
 *
 * This is the Daily 25's share of the screen: the promise, the two paths, and
 * the button. The live Holdouts, the judges' walkthrough link and the no-wallet
 * variant are the rest of T039 and T041, and arrive with the cohort screens
 * that supply their figures.
 */
@Composable
fun ConnectScreen(
    state: WalletUiState,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems, Alignment.CenterVertically),
    ) {
        Text(
            text = "Stake on yourself.\nProve it every day.",
            style = MaterialTheme.typography.headlineMedium,
            color = HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "The Daily 25 — 25 push-ups, free, counted on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "A Holdout — put SKR on it and finish with the cohort.",
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onConnect,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.primaryButtonHeight),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = HoldoutColors.TextPrimary,
                contentColor = HoldoutColors.OnLight,
            ),
        ) {
            Text("Connect wallet", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            text = "TEST NETWORK · TEST TOKEN",
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
        )

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = HoldoutColors.Warning,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ConnectPreview() {
    HoldoutTheme { ConnectScreen(state = WalletUiState(), onConnect = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun NoWalletPreview() {
    HoldoutTheme {
        ConnectScreen(
            state = WalletUiState(message = "Holdout needs a Solana wallet — even for the free Daily 25."),
            onConnect = {},
        )
    }
}
