package holdout.app.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.KeyValueRow
import holdout.app.ui.LocalSeekerNames
import holdout.app.ui.MenuRow
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
import holdout.app.ui.theme.HoldoutFonts
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
 * W1 Me, the Me tab root (screen map W1, board Me; FR-069d; ruling "Onboarding / Me").
 *
 * ```
 *  Me                       ( 7xQp…3fA9 ⧉ ) [DEVNET]      headlineMedium · copy pill · StatusChip
 *  nova.skr                                               Seeker ID, accent (none: nothing)
 *  ┌ (flame) 12  day streak                           ┐   StreakFlame in a card
 *  └ The Daily 25 · a forfeit never breaks it · 1 freeze held ┘
 *  YOU HOLD                                               SectionLabel
 *  ┌ Test SKR              4,750 SKR ┐                    KeyValueRow
 *  │ Devnet SOL for fees   0.012 SOL │
 *  └ One tap a day: … ┘
 *  Too little SKR or SOL …                                the Me dot's meaning (only when low)
 *  [ Get test tokens ]                                    PrimaryButton → the faucet sheet
 *  ( About Holdout            devnet · test token › )     MenuRow
 *  ( How Holdout works                  4 cards  › )
 *                  Disconnect wallet                      QuietAction, warning
 *       Switched networks? Disconnect and connect again.
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            Identity(state)
            Streak(state)
            Holdings(state)
            if (state.lowOnFunds) Notice("Too little SKR or SOL to stake or submit. Get test tokens first.", warning = true)
            PrimaryButton("Get test tokens", onGetTokens)
            MenuRow("About Holdout", "devnet · test token", onClick = onAbout)
            MenuRow("How Holdout works", "4 cards", onClick = onTour)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            QuietAction("Disconnect wallet", onDisconnect, warning = true)
            Text("Switched networks? Disconnect and connect again.", style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center)
        }
    }
}

/** The title, the Seeker ID under it when there is one, and the short address to copy beside the network. */
@Composable
private fun Identity(state: MeState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Column(Modifier.weight(1f)) {
            Text("Me", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
            LocalSeekerNames.current[state.wallet]?.let { Text(it, style = AmountStyle, color = HoldoutColors.Accent, maxLines = 1) }
        }
        CopyAddress(state.wallet)
        StatusChip(state.network)
    }
}

/** The address as a pill with a copy mark; a tap copies the whole wallet and the pill says "Copied" for 1.5 s. */
@Composable
private fun CopyAddress(wallet: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MILLIS)
            copied = false
        }
    }
    val shape = MaterialTheme.shapes.extraLarge
    // A 48 dp touch target around the 32 dp pill of the board.
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .clip(shape)
            .clickable(role = Role.Button) {
                clipboard.setText(AnnotatedString(wallet))
                copied = true
            }
            .semantics { contentDescription = "Copy wallet address" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(PILL_HEIGHT)
                .background(HoldoutColors.Surface1, shape)
                .border(Spacing.border, HoldoutColors.Hairline, shape)
                .padding(horizontal = Spacing.betweenItems),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text(
                if (copied) "Copied" else Format.short(wallet),
                style = LabelStyle.copy(fontFamily = HoldoutFonts.Mono),
                color = HoldoutColors.TextPrimary,
                maxLines = 1,
            )
            Canvas(Modifier.size(COPY_MARK)) {
                val u = size.minDimension / COPY_UNITS
                val stroke = Stroke(width = 1.2f * u)
                drawRoundRect(HoldoutColors.TextSecondary, Offset(3.5f * u, 3.5f * u), Size(7f * u, 7f * u), CornerRadius(1.5f * u), style = stroke)
                drawRoundRect(HoldoutColors.TextSecondary, Offset(1.5f * u, 1.5f * u), Size(7f * u, 7f * u), CornerRadius(1.5f * u), style = stroke)
            }
        }
    }
}

/** The daily streak: its flame, the words, what keeps it, and the freezes held (FR-049, FR-050). */
@Composable
private fun Streak(state: MeState) {
    HoldoutCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            StreakFlame(state.streak, state.streakState)
            Body("day streak")
        }
        Text("The Daily 25 · a forfeit never breaks it", style = LabelStyle, color = HoldoutColors.TextTertiary)
        if (state.freezes > 0) Body(if (state.freezes == 1) "1 freeze held" else "${state.freezes} freezes held")
    }
}

/** What the wallet holds on devnet and what one faucet tap gives. */
@Composable
private fun Holdings(state: MeState) {
    SectionLabel("You hold")
    HoldoutCard {
        if (state.skr == null || state.lamports == null) {
            Reading { Body("Reading devnet…") }
        } else {
            KeyValueRow("Test SKR", Format.skr(state.skr))
            KeyValueRow("Devnet SOL for fees", Format.sol(state.lamports))
        }
        Body(
            state.faucetSkr?.let { "One tap a day: ${Format.skr(it).removeSuffix(" SKR")} test SKR, plus SOL for fees. Test network, test token." }
                ?: "One tap a day: test SKR, plus SOL for fees. Test network, test token.",
        )
    }
}

private const val COPIED_MILLIS = 1_500L
private val PILL_HEIGHT = 32.dp
private val COPY_MARK = 12.dp
private const val COPY_UNITS = 12f

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
