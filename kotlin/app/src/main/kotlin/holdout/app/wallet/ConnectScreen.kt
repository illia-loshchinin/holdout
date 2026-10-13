package holdout.app.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.home.boostLine
import holdout.app.ui.Body
import holdout.app.ui.ChipTone
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.ProgressRing
import holdout.app.ui.QuietAction
import holdout.app.ui.SectionLabel
import holdout.app.ui.StatusChip
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.Wordmark
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.CohortStatus
import holdout.core.Derive
import holdout.core.MembershipSnapshot

/**
 * A1 First open and A3 No wallet — required before anything else, **even for the
 * free Daily 25** (FR-001a, screen map A1, T039, T041; boards Connect, NoWallet,
 * WalletConnect).
 *
 * ```
 *  Holdout                               [DEVNET]        Wordmark · StatusChip
 *  Stake on yourself.                                    DisplayStyle, second line tertiary
 *  Prove it every day.
 *  • The Daily 25 — free, every day: …                   two product lines, a dot each
 *  • A Holdout — stake SKR on a 7-day Spark or a 21-day Habit. …
 *  LIVE RIGHT NOW               Test network, test token.
 *  ┌ SPARK · 7-DAY · DAY 7 OF 7            [RUNNING] ┐   one card, a row per level
 *  │ 13 / 20 still standing            6,125 SKR       │   (live from devnet with no wallet)
 *  │ Next: 12,500 SKR at stake · 14 joined             │   FR-053: at stake, joined,
 *  │ Joining closes in 2d 3h · Prize boost: up to …    │   how long joining stays open, boost
 *  └──────────────────────────────────────────────────┘
 *  [ notice: wrong network / could not connect ]
 *  [ Connect wallet ]                                    PrimaryButton
 *  No wallet yet?                                        QuietAction → the A3 help
 * ```
 *
 * "No wallet yet?", or a connect that found no wallet app ([noWallet], ES-0),
 * swaps the product lines and the live list for what a wallet is and how to get
 * one. While the wallet is asked ([busy]) the screen is Holdout's own "Waiting
 * for your wallet…" — with no Cancel: the wallet app cannot be told to stop
 * (rulings G7). A [message] is the connect's failure: the wrong network
 * (FR-001b: nothing was signed) or anything else, in one plain line.
 */
@Composable
fun ConnectScreen(
    busy: Boolean,
    message: String?,
    noWallet: Boolean,
    live: List<Holdout>,
    now: Long?,
    /** Rosters read so far, by cohort: what a running Holdout's figures are derived from. */
    rosters: Map<SolanaPublicKey, List<MembershipSnapshot>> = emptyMap(),
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (busy) {
        WaitingForWallet(modifier)
        return
    }
    var helpOpened by rememberSaveable { mutableStateOf(false) }
    val help = noWallet || helpOpened
    // Back from the help returns to the first open; the help a failed connect opened stays until a wallet answers.
    BackHandler(enabled = helpOpened && !noWallet) { helpOpened = false }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Wordmark()
                StatusChip(Deployment.networkName.lowercase())
            }
            Headline()
            if (help) {
                NoWalletHelp()
            } else {
                Products()
                if (now != null && live.isNotEmpty()) LiveRightNow(live, now, rosters)
            }
        }
        ConnectNotice(message)
        PrimaryButton("Connect wallet", onConnect)
        if (help) {
            CenteredLabel("Test network · test token", Modifier.padding(bottom = Spacing.tight))
        } else {
            QuietAction("No wallet yet?", { helpOpened = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** "Stake on yourself. Prove it every day." — the second line a step quieter. */
@Composable
private fun Headline() {
    Text(
        text = buildAnnotatedString {
            append("Stake on yourself.\n")
            withStyle(SpanStyle(color = HoldoutColors.TextTertiary)) { append("Prove it every day.") }
        },
        style = DisplayStyle.copy(textAlign = TextAlign.Start),
        color = HoldoutColors.TextPrimary,
    )
}

/** What each product is, in a few short lines: the Daily 25 in the accent dot, a Holdout in the Solana one. */
@Composable
private fun Products() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Product(HoldoutColors.Accent, "The Daily 25", " — free, every day: get stronger at your own pace. 25 push-ups, counted by the camera.\nYour streak grows.")
        Product(
            HoldoutColors.Success,
            "A Holdout",
            " — stake SKR on a 7-day Spark or a 21-day Habit.\nMiss a day: your stake joins the prize pool.\nFinish: take it back plus a share of the pool.",
        )
    }
}

@Composable
private fun Product(dot: Color, lead: String, rest: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Box(Modifier.padding(top = DOT_TOP).size(DOT).background(dot, CircleShape))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = HoldoutColors.TextPrimary, fontWeight = FontWeight.Medium)) { append(lead) }
                append(rest)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
        )
    }
}

/** A3: what a wallet is and how to get one, for a phone that has none (ES-0). Text only; no store link. */
@Composable
private fun NoWalletHelp() {
    HoldoutCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            WalletMark()
            Text("You need a Solana wallet", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        }
        Body("Even the free Daily 25 needs one. It holds your keys — Holdout never does.")
        Product(HoldoutColors.Accent, "On a Seeker", " — Seed Vault Wallet is built in.")
        Product(HoldoutColors.Accent, "On other phones", " — install a Solana wallet with Mobile Wallet Adapter, set it to devnet, come back.")
    }
}

/** The board's wallet outline in `warning`: drawn, 20 dp. */
@Composable
private fun WalletMark() {
    Canvas(Modifier.size(WALLET_MARK)) {
        val u = size.minDimension / WALLET_UNITS
        val stroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round)
        drawRoundRect(HoldoutColors.Warning, Offset(2.5f * u, 5f * u), Size(15f * u, 11f * u), CornerRadius(2.5f * u), style = stroke)
        drawLine(HoldoutColors.Warning, Offset(13f * u, 10.5f * u), Offset(15f * u, 10.5f * u), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        val flap = Path().apply {
            moveTo(5f * u, 5f * u)
            lineTo(5f * u, 4f * u)
            lineTo(14f * u, 2.5f * u)
        }
        drawPath(flap, HoldoutColors.Warning, style = Stroke(width = 1.5f * u))
    }
}

/** A connect that did not work, in one plain line: the wrong network says nothing was signed (FR-001b). */
@Composable
private fun ConnectNotice(message: String?) {
    if (message == null) return
    val text = if (message == WalletRepository.WRONG_NETWORK) WRONG_NETWORK_NOTICE else COULD_NOT_CONNECT
    val shape = MaterialTheme.shapes.small
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.Warning,
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Warning.copy(alpha = NOTICE_FILL), shape)
            .border(Spacing.border, HoldoutColors.Warning.copy(alpha = NOTICE_BORDER), shape)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.betweenItems),
    )
}

/** A centred uppercase label, as the board's footers and the wait screen's top line. */
@Composable
private fun CenteredLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}

/** The wallet app has the screen: Holdout waits, and offers no way to cancel — MWA cannot be aborted. */
@Composable
private fun WaitingForWallet(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
    ) {
        CenteredLabel("Connecting")
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.cardPadding, Alignment.CenterVertically),
        ) {
            ProgressRing(Format.WAITING_FOR_WALLET.removeSuffix("…"))
            Text(Format.WAITING_FOR_WALLET, style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary, textAlign = TextAlign.Center)
            Body("Approve or decline in your wallet. Nothing moves until you approve.", center = true)
        }
        CenteredLabel("Test network · test token")
    }
}

// ---- the live list ------------------------------------------------------------------------------

/** One row of the live card: the running Holdout of a level and the next one open at the same level, either of which may be missing. */
internal data class LiveRowData(val running: Holdout?, val open: Holdout?)

/**
 * Groups the live Holdouts into the card's rows, one per level: a running one,
 * with the next open one of its level hanging under it; an open one with no
 * running Holdout at its level gets its own row. Short test Holdouts are their
 * own level and sort last; the two Hackathon lobbies (one per form, open at once) each get
 * their own row, Spark first. A level with several running Holdouts (the 21-day
 * one overlaps) gets a row for each, the open one under the first.
 */
internal fun liveRows(live: List<Holdout>, now: Long): List<LiveRowData> {
    fun key(h: Holdout) = Triple(h.cohort.form, h.cohort.durationDays, h.isTest)
    val running = live.filter { it.status(now) == CohortStatus.Running }
    val open = live.filter { it.status(now) == CohortStatus.Open }.sortedBy { it.cohort.start }
    val attached = mutableSetOf<Holdout>()
    val rows = mutableListOf<LiveRowData>()
    val levelsWithOpen = mutableSetOf<Triple<Int, Int, Boolean>>()
    running.sortedBy { it.cohort.start }.forEach { r ->
        val next = if (levelsWithOpen.add(key(r))) open.firstOrNull { key(it) == key(r) } else null
        next?.let { attached += it }
        rows += LiveRowData(r, next)
    }
    open.filter { it !in attached }.forEach { rows += LiveRowData(null, it) }
    return rows.sortedWith(compareBy({ (it.running ?: it.open)!!.isTest }, { (it.running ?: it.open)!!.cohort.durationDays }, { -(it.running ?: it.open)!!.cohort.form }, { it.running == null }))
}

/** "Live right now" (FR-001a, FR-062a): read from devnet with no wallet; the figures derive from the rosters as every screen's do. */
@Composable
private fun LiveRightNow(live: List<Holdout>, now: Long, rosters: Map<SolanaPublicKey, List<MembershipSnapshot>>) {
    val rows = liveRows(live, now)
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Live right now", Modifier.weight(1f))
            Text("Test network, test token.", style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
        HoldoutCard {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(Spacing.border).background(HoldoutColors.Hairline))
                LiveRow(row, now, row.running?.let { rosters[it.address] })
            }
        }
    }
}

@Composable
private fun LiveRow(row: LiveRowData, now: Long, roster: List<MembershipSnapshot>?) {
    val running = row.running
    val shown = running ?: row.open!!
    val c = shown.cohort
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight), modifier = Modifier.padding(vertical = Spacing.tight)) {
        if (shown.isTest) TestCohortBadge(c.dayLength)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            val day = (Derive.dayIndex(c, now) + 1).coerceIn(1, c.durationDays)
            Text(
                text = if (running != null) Format.headerLabel(c, day) else Format.level(c).uppercase(),
                style = LabelStyle,
                color = HoldoutColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            if (running != null) StatusChip("running", tone = ChipTone.Success) else StatusChip("open", tone = ChipTone.Accent)
        }
        if (running != null) {
            val figures = roster?.let { Derive.figures(c, it, now) }
            val standing = figures?.survivors?.standing
            val starters = figures?.survivors?.starters ?: c.memberCount
            val pool = figures?.projectedPool ?: c.pool
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
                Text(
                    text = if (standing != null) figureText("$standing", " / $starters still standing") else figureText("$starters", " started"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = HoldoutColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(pool), style = AmountStyle, color = HoldoutColors.TextPrimary)
                    Text("prize pool", style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
        }
        row.open?.let { OpenLines(it, now, lead = if (running != null) "Next: " else "") }
    }
}

/** The big number in a figure line: "13" in the headline weight, light, then the words. */
@Composable
private fun figureText(number: String, words: String): AnnotatedString = buildAnnotatedString {
    withStyle(MaterialTheme.typography.headlineMedium.toSpanStyle().copy(fontWeight = FontWeight.Light, color = HoldoutColors.TextPrimary)) { append(number) }
    append(words)
}

/** An open Holdout's offer (FR-053): at stake, joined, how long joining stays open, and the boost. */
@Composable
private fun OpenLines(h: Holdout, now: Long, lead: String) {
    val c = h.cohort
    Column {
        Text("$lead${Format.skr(Derive.atStake(c))} at stake · ${c.memberCount} joined", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Accent)
        Text("Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))}", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Accent)
        boostLine(h.league?.boostReserve)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success) }
    }
}

private const val WRONG_NETWORK_NOTICE = "Your wallet isn't on devnet, so nothing was signed. Switch it to devnet, then connect again."
private const val COULD_NOT_CONNECT = "We couldn't connect to your wallet. Try again."
private const val NOTICE_FILL = 0.10f
private const val NOTICE_BORDER = 0.32f
private val DOT = 6.dp
private val DOT_TOP = 9.dp
private val WALLET_MARK = 20.dp
private const val WALLET_UNITS = 20f

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 844)
@Composable
private fun ConnectPreview() {
    HoldoutTheme { ConnectScreen(busy = false, message = null, noWallet = false, live = emptyList(), now = null, onConnect = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 844)
@Composable
private fun ConnectWrongNetworkPreview() {
    HoldoutTheme { ConnectScreen(busy = false, message = WalletRepository.WRONG_NETWORK, noWallet = false, live = emptyList(), now = null, onConnect = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 844)
@Composable
private fun ConnectWaitingPreview() {
    HoldoutTheme { ConnectScreen(busy = true, message = null, noWallet = false, live = emptyList(), now = null, onConnect = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 844)
@Composable
private fun NoWalletPreview() {
    HoldoutTheme { ConnectScreen(busy = false, message = null, noWallet = true, live = emptyList(), now = null, onConnect = {}) }
}
