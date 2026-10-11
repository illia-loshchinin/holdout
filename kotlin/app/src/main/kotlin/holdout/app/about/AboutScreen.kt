package holdout.app.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.MenuRow
import holdout.app.ui.QuietAction
import holdout.app.ui.ScreenHeader
import holdout.app.ui.ScriptedChip
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * X1 About Holdout, for every member (spec 004 FR-012, FR-013; board About): the
 * test network, the two things read from mainnet, the scripted members, the
 * tour again and the wallet — the one place a member inside a Holdout can
 * disconnect. Nothing here is addressed to judges: the full cycle is shown in
 * the demo video, and the known weaknesses are in the README (FR-015).
 *
 * ```
 *  ‹                    ABOUT                  ScreenHeader
 *  About Holdout                               headlineMedium
 *  Test network, test token. Nothing here moves real value.      Body
 *  Read from mainnet, never written to it: Seeker ID names, …
 *  ┌ ◇ SCRIPTED  Some members in these Holdouts are scripted. … ┐   HoldoutCard
 *  ( How Holdout works                      4 cards  › )          MenuRow
 *
 *          Disconnect wallet                   QuietAction, warning
 *   Switched networks? Disconnect and connect again.
 * ```
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        ScreenHeader("About", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            Text("About Holdout", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
            Body("Test network, test token. Nothing here moves real value.\nRead from mainnet, never written to it: Seeker ID names, and whether you hold a Seeker Genesis Token.")
            HoldoutCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems), verticalAlignment = Alignment.Top) {
                    ScriptedChip()
                    Body("Some members in these Holdouts are scripted. Real wallets, same rules, labelled SCRIPTED.", Modifier.weight(1f))
                }
            }
            onTour?.let { MenuRow("How Holdout works", "4 cards", onClick = it) }
        }
        onDisconnect?.let {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                QuietAction("Disconnect wallet", it, warning = true)
                Text("Switched networks? Disconnect and connect again.", style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun AboutPreview() {
    HoldoutTheme { AboutScreen(onTour = {}, onBack = {}, onDisconnect = {}) }
}
