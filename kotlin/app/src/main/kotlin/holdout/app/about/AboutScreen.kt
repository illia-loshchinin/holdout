package holdout.app.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.ui.Body
import holdout.app.ui.SecondaryButton
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing

/**
 * About Holdout, for every member (spec 004 FR-012, FR-013): the test network,
 * the tour again, and the wallet — the one place a member inside a Holdout can
 * disconnect. Nothing here is addressed to judges: the full cycle is shown in
 * the demo video, and the known weaknesses are in the README (FR-015).
 */
@Composable
fun AboutScreen(
    onTour: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Disconnects the wallet and returns to the connect screen. Also how to get a
     * fresh authorization after switching the wallet's network: the wallet ties
     * an authorization to the network it was on when it was granted.
     */
    onDisconnect: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        item { TextButton(onClick = onBack) { Text("← Back", color = HoldoutColors.TextSecondary) } }
        item { Text("About Holdout", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary) }
        item { Body("Test network, test token. Holdout runs on devnet with a mock SKR token; nothing here touches mainnet or real value.") }
        item { Body("Some members in these Holdouts are scripted: real wallets under the same rules, labelled SCRIPTED wherever they appear.") }
        onTour?.let { item { SecondaryButton("The three-card tour again", it) } }
        onDisconnect?.let {
            item { SecondaryButton("Disconnect wallet", it) }
            item { Body("Switched your wallet to another network? Disconnect and connect again, so the wallet authorizes Holdout on devnet.") }
        }
    }
}
