package holdout.app.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.KeyValueRow
import holdout.app.ui.LocalSeekerNames
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.StatusChip
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/** W1 Me, as the screen shows it. Balances are null until the chain has been read. */
data class MeState(
    /** The connected wallet, base58. */
    val wallet: String,
    val skr: ULong?,
    val lamports: Long?,
    /** What one faucet tap gives: enough for the largest chip (FR-042a); null before the config is read. */
    val faucetSkr: ULong?,
    val streak: Int,
    val streakState: StreakState = StreakState.Active,
    /** Streak freezes held, 0–2. */
    val freezes: Int = 0,
    /** SKR or SOL too low to stake or submit: the tab's dot, said here too. */
    val lowOnFunds: Boolean = false,
    /** Devnet, or the local validator in a local test build. */
    val network: String = "devnet",
)

/**
 * W1 Me, the Me tab root (screen map W1, board 26; ruling A "Me").
 *
 * ```
 *  Me                                    headlineMedium
 *  nova.skr                              Seeker ID, accent (none: nothing)
 *  7xQp…3fA9 · Copy address  [devnet]    amount · QuietAction · StatusChip
 *  ┌ (flame) 12 day streak  ┐            StreakFlame in a card
 *  └ The Daily 25 · a forfeit never breaks it · freezes ┘
 *  YOU HOLD                              SectionLabel
 *  ┌ Test SKR          4,750 SKR ┐       KeyValueRow
 *  │ Devnet SOL for fees 0.012 SOL │
 *  └ One tap a day: … ┘
 *  [ Get test tokens ]                   PrimaryButton → the faucet sheet
 *  About Holdout →  ·  How Holdout works →   QuietAction
 *  Disconnect wallet                     QuietAction, warning
 *  Switched networks? Disconnect and connect again.
 * ```
 */
@Composable
fun MeScreen(
    state: MeState,
    onGetTokens: () -> Unit,
    onAbout: () -> Unit,
    onTour: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MILLIS)
            copied = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Me", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        LocalSeekerNames.current[state.wallet]?.let { Text(it, style = AmountStyle, color = HoldoutColors.Accent) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(Format.short(state.wallet), style = AmountStyle, color = HoldoutColors.TextPrimary)
            QuietAction(if (copied) "Copied" else "Copy address", {
                clipboard.setText(AnnotatedString(state.wallet))
                copied = true
            }, modifier = Modifier.weight(1f))
            StatusChip(state.network)
        }

        HoldoutCard {
            StreakFlame(state.streak, state.streakState)
            Text("The Daily 25 · a forfeit never breaks it", style = LabelStyle, color = HoldoutColors.TextSecondary)
            if (state.freezes > 0) Body(if (state.freezes == 1) "1 freeze held" else "${state.freezes} freezes held")
        }

        SectionLabel("You hold")
        HoldoutCard {
            if (state.skr == null || state.lamports == null) {
                Reading { Body("Reading devnet…") }
            } else {
                KeyValueRow("Test SKR", Format.skr(state.skr))
                KeyValueRow("Devnet SOL for fees", "%.3f SOL".format(state.lamports / LAMPORTS_PER_SOL))
            }
            Body(state.faucetSkr?.let { "One tap a day: ${Format.skr(it).removeSuffix(" SKR")} test SKR, plus SOL for fees. Test network, test token." } ?: "One tap a day: test SKR, plus SOL for fees. Test network, test token.")
        }
        if (state.lowOnFunds) Notice("Too little SKR or SOL to stake or submit. Get test tokens first.", warning = true)
        PrimaryButton("Get test tokens", onGetTokens)

        QuietAction("About Holdout →", onAbout)
        QuietAction("How Holdout works →", onTour)
        QuietAction("Disconnect wallet", onDisconnect, warning = true)
        Text("Switched networks? Disconnect and connect again.", style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

private const val COPIED_MILLIS = 1_500L
private const val LAMPORTS_PER_SOL = 1e9

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun MePreview() {
    HoldoutTheme {
        MeScreen(
            MeState("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", 4_750_000_000uL, 12_000_000L, 5_000_000_000uL, streak = 12, freezes = 1),
            onGetTokens = {},
            onAbout = {},
            onTour = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun MeLowPreview() {
    HoldoutTheme {
        MeScreen(
            MeState("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", 0uL, 1_000L, 5_000_000_000uL, streak = 0, streakState = StreakState.Broken, lowOnFunds = true),
            onGetTokens = {},
            onAbout = {},
            onTour = {},
            onDisconnect = {},
        )
    }
}
