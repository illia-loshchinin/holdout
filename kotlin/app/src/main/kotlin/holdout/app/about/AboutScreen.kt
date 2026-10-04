package holdout.app.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.ScriptedWallets
import org.json.JSONObject

/** One row of `assets/weaknesses.json` — the file the README's table is rendered from too (T103). */
data class Weakness(val what: String, val why: String, val fix: String)

/**
 * About / for judges (FR-043a, FR-044, constitution XI, T090): the recorded
 * walkthrough, the published scripted-wallet list, and every known weakness
 * with its real fix — **from the same file the README's table is generated
 * from**, so the app and the README can never say different things.
 *
 * Reachable before a wallet is connected, from a quiet link on the connect
 * screen: a judge whose wallet cannot sign on devnet still sees the product.
 */
@Composable
fun AboutScreen(
    onWalkthrough: () -> Unit,
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
    val context = LocalContext.current
    val weaknesses = remember { loadWeaknesses(context.assets.open("weaknesses.json").bufferedReader().use { it.readText() }) }
    val scripted = remember { ScriptedWallets.keys.map { it.base58() }.sorted() }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        item { TextButton(onClick = onBack) { Text("← Back", color = HoldoutColors.TextSecondary) } }
        item { Text("About Holdout", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary) }
        item { Body("Devnet only, with a mock SKR token. Nothing here touches mainnet or real value.") }
        item { SecondaryButton("Watch a full cycle (3 min)", onWalkthrough) }
        onTour?.let { item { SecondaryButton("The three-card tour again", it) } }
        onDisconnect?.let {
            item { SecondaryButton("Disconnect wallet", it) }
            item { Body("Switched your wallet to another network? Disconnect and connect again, so the wallet authorizes Holdout on devnet.") }
        }

        item { SectionLabel("Known weaknesses · ${weaknesses.size}") }
        items(weaknesses) { w ->
            HoldoutCard {
                Text(w.what, style = MaterialTheme.typography.titleSmall, color = HoldoutColors.TextPrimary)
                Body(w.why)
                Text("Real fix: ${w.fix}", style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.Accent)
            }
        }

        item { SectionLabel("Scripted members · ${scripted.size} wallets") }
        item { Body("Real wallets under the same contract rules, filling a Holdout only up to its minimum — 20 in a 7-day, 12 in a 21-day. Their forfeits fund real prizes; their completed days are not camera-verified.") }
        items(scripted) { Text(it, style = LabelStyle, color = HoldoutColors.TextTertiary) }
    }
}

/**
 * The walkthrough player (FR-044, R18). The recording is taken from the
 * disclosed short test cohort and bundled before submission; until then the
 * screen says it is in progress rather than showing a mock-up.
 */
@Composable
fun WalkthroughScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        item { TextButton(onClick = onBack) { Text("← Back", color = HoldoutColors.TextSecondary) } }
        item { SectionLabel("A full cycle") }
        item { Text("Recording in progress", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary) }
        item { Body("The walkthrough is being recorded from the real app on devnet, on a disclosed short test cohort with compressed days. It lands in the build submitted before 9 October.") }
    }
}

/** Markdown in the source (`code`, **bold**) is kept for the README and stripped for the screen. */
internal fun loadWeaknesses(json: String): List<Weakness> {
    val list = JSONObject(json).getJSONArray("weaknesses")
    fun plain(s: String) = s.replace("**", "").replace("`", "")
    return List(list.length()) { i ->
        val o = list.getJSONObject(i)
        Weakness(plain(o.getString("what")), plain(o.getString("why")), plain(o.getString("fix")))
    }
}
