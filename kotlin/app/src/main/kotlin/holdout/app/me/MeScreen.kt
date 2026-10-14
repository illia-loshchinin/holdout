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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.streak.StreakStore
import holdout.app.streak.ToesMeter
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
    /** The longest streak this wallet has had on this phone (never below [streak]). */
    val longest: Int = 0,
    /** Today's toes reading and the last seven days (FR-052a); null until the phone has read it. */
    val toes: ToesMeter.Meter? = null,
    /** The Daily 25's target, for the toes meter's "of 25". */
    val toesTarget: Int = 25,
    /** The Leaderboards row's meta, "#4 in Spark · 7-day": the member's best rank; null when they hold none (FR-069d). */
    val leaderboardMeta: String? = null,
) {
    /**
     * Days of streak still to go before the next freeze is earned (one for every seventh day, FR-049), or null
     * while the most are held ([StreakStore.MAX_FREEZES]) and there is none to wait for.
     */
    val nextFreezeInDays: Int? get() = StreakStore.daysToNextFreeze(streak, freezes)
}

/**
 * W1 Me, the Me tab root (screen map W1, board Me; FR-069d, FR-052a, FR-049, FR-060).
 *
 * ```
 *  Me                       ( 7xQp…3fA9 ⧉ ) [DEVNET]      headlineMedium · copy pill · StatusChip
 *  nova.skr                                               Seeker ID, accent (none: nothing)
 *  ┌ THE DAILY 25 ─────────────────────────────────────┐
 *  │ (flame) 12  day streak                            │   StreakFlame
 *  │ Longest 21 days · Freezes 1 of 2                  │   Freezes {n} of 2
 *  │ Next freeze in 2 days                             │   only while fewer than 2 are held
 *  │ 18 of 25 from your toes   ▂▃▅▅▁▇▇  Best: 25       │   the toes meter, moved from Today
 *  └───────────────────────────────────────────────────┘
 *  ( Leaderboards                  #4 in Spark · 7-day › )  MenuRow, the only way to the boards
 *  WALLET                                                 SectionLabel
 *  ┌ Test SKR              4,750 SKR ┐                    KeyValueRow
 *  │ Devnet SOL for fees   0.012 SOL │
 *  └ One tap: … ┘
 *  Too little SKR or SOL …                                the Me dot's meaning (only when low)
 *  ( Get test tokens           5,000 SKR + SOL › )        MenuRow; the PrimaryButton only while low
 *  ( About Holdout                   how it works › )     MenuRow
 *                  Disconnect wallet                      QuietAction, warning
 *       Switched networks? Disconnect and connect again.
 * ```
 */
@Composable
fun MeScreen(
    state: MeState,
    onGetTokens: () -> Unit,
    onAbout: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    /** The only way to the leaderboards (FR-060, FR-069d). */
    onLeaderboards: () -> Unit = {},
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
            DailyCard(state)
            MenuRow("Leaderboards", state.leaderboardMeta, onClick = onLeaderboards)
            Holdings(state)
            if (state.lowOnFunds) {
                Notice("Too little SKR or SOL to stake or submit. Get test tokens first.", warning = true)
                // The one white button on the screen, only while funds are low (FR-069d, 2026-10-13).
                PrimaryButton("Get test tokens", onGetTokens)
            } else {
                MenuRow("Get test tokens", state.faucetSkr?.let { "${Format.skr(it)} + SOL" }, onClick = onGetTokens)
            }
            MenuRow("About Holdout", "how it works", onClick = onAbout)
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

/** The Daily 25's card: the streak, the longest, the freezes and the next one, and the toes meter (FR-049, FR-052a). */
@Composable
private fun DailyCard(state: MeState) {
    HoldoutCard {
        SectionLabel("The Daily 25")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            StreakFlame(state.streak, state.streakState)
            Body("day streak")
        }
        Stat("Longest ", state.longest.toString(), " ${if (state.longest == 1) "day" else "days"}")
        Stat("Freezes ", state.freezes.toString(), " of ${StreakStore.MAX_FREEZES}")
        state.nextFreezeInDays?.let { Stat("Next freeze in ", it.toString(), " ${if (it == 1) "day" else "days"}") }
        state.toes?.let {
            HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
            ToesMeterCard(it, state.toesTarget)
        }
    }
}

/** One quiet line of the Daily 25's figures, the figure itself in the mono face: one text node for the screen reader. */
@Composable
private fun Stat(before: String, figure: String, after: String) {
    Text(
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.TextPrimary)) { append(figure) }
            append(after)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = HoldoutColors.TextSecondary,
    )
}

/** What the wallet holds on devnet and what one faucet tap gives. */
@Composable
private fun Holdings(state: MeState) {
    SectionLabel("Wallet")
    HoldoutCard {
        if (state.skr == null || state.lamports == null) {
            Reading { Body("Reading devnet…") }
        } else {
            KeyValueRow("Test SKR", Format.skr(state.skr))
            KeyValueRow("Devnet SOL for fees", Format.sol(state.lamports))
        }
        Body(
            state.faucetSkr?.let { "One tap: ${Format.skr(it).removeSuffix(" SKR")} test SKR, plus SOL for fees. Test network, test token." }
                ?: "One tap: test SKR, plus SOL for fees. Test network, test token.",
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
            MeState(
                "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", 4_750_000_000uL, 12_000_000L, 5_000_000_000uL, streak = 12, freezes = 1, longest = 21,
                toes = ToesMeter.Meter(toes = 18, measured = 25, week = listOf(0, 10, 25, 25, 0, 18, 18), best = 25), leaderboardMeta = "#4 in Spark · 7-day",
            ),
            onGetTokens = {},
            onAbout = {},
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
            onDisconnect = {},
        )
    }
}
