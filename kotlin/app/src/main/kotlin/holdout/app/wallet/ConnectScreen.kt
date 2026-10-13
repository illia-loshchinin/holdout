package holdout.app.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.ui.Body
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
import holdout.app.ui.theme.HoldoutFonts
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
 *  Stake on yourself.                                    34 sp display, second line tertiary
 *  Prove it every day.
 *  • The Daily 25 — free, every day: …                   two product lines, an Accent dot each
 *  • A Holdout — stake SKR on a 7-day Spark or a 21-day Habit. …
 *  LIVE RIGHT NOW               Test network, test token.
 *  ┌ SPARK · 7-DAY · DAY 7 OF 7           ● running ┐   one card, a row per running Holdout (live, no wallet)
 *  │ 13 / 20 still standing            6,125 SKR     │   the count with room above it, the pool in Success
 *  │ ┌ NEXT ROUND               closes in 2d 3h ┐   │   surface-2 block, FR-053: the countdown in Accent
 *  │ │ 12,500 SKR at stake · 14 joined   up to 480 boost │   no "+", no boost text while the reserve is 0
 *  │ └────────────────────────────────────────┘   │
 *  └──────────────────────────────────────────────────┘
 *  [ status card: wrong network / could not connect ]    a polite live region, soft-warm ring, no Warning text
 *  [ Connect wallet ]                                    PrimaryButton
 *  No wallet yet?                                        QuietAction → the A3 help
 * ```
 *
 * "No wallet yet?", or a connect that found no wallet app ([noWallet], ES-0),
 * swaps the product lines and the live list for what a wallet is and how to get
 * one. While the wallet is asked ([busy]) the screen is Holdout's own "Waiting
 * for your wallet…" — with no Cancel: the wallet app cannot be told to stop
 * (rulings G7). A [message] is the connect's failure: the wrong network
 * (FR-001b: nothing was signed) or anything else, in one plain line. The first open fits one Seeker
 * screen (400 × 829 dp) with two running Holdouts and the wrong-network notice showing (`ConnectFitTest`).
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
            .padding(horizontal = Spacing.screenGutter)
            .padding(top = Spacing.betweenItems, bottom = Spacing.tight),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
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
        style = HeadlineStyle,
        color = HoldoutColors.TextPrimary,
    )
}

/** What each product is, in a few short lines; one accent dot for both (board Connect, FR-053). */
@Composable
private fun Products() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Product(HoldoutColors.Accent, "The Daily 25", " — free, every day: get stronger at your own pace. 25 push-ups, counted by the camera.\nYour streak grows.")
        Product(
            HoldoutColors.Accent,
            "A Holdout",
            " — stake SKR on a 7-day Spark or a 21-day Habit.\nMiss a day: your stake joins the prize pool.\nFinish: take it back plus a share of the pool.",
        )
    }
}

@Composable
private fun Product(dot: Color, lead: String, rest: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Box(Modifier.padding(top = DOT_TOP).size(DOT).background(dot, CircleShape).testTag(PRODUCT_DOT_TAG))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = HoldoutColors.TextPrimary, fontWeight = FontWeight.Medium)) { append(lead) }
                append(rest)
            },
            style = ProductStyle,
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

/**
 * A connect that did not work, in one plain line: the wrong network says nothing was signed (FR-001b). A calm status
 * card, not an alarm (board Connect, 2026-10-14): `surface-1` with a thin soft-warm ring, the sentence in the primary
 * colour, a polite live region — nothing in `warning`.
 */
@Composable
private fun ConnectNotice(message: String?) {
    if (message == null) return
    val text = if (message == WalletRepository.WRONG_NETWORK) WRONG_NETWORK_NOTICE else COULD_NOT_CONNECT
    val shape = MaterialTheme.shapes.small
    Text(
        text = text,
        style = NoticeStyle,
        color = HoldoutColors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface1, shape)
            .border(Spacing.border, HoldoutColors.SoftWarm.copy(alpha = NOTICE_BORDER), shape)
            .padding(horizontal = Spacing.betweenItems + Spacing.base, vertical = Spacing.tight + Spacing.base / 2)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
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
 * own level and sort last. A level with several running Holdouts (the 21-day
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
    return rows.sortedWith(compareBy({ (it.running ?: it.open)!!.isTest }, { (it.running ?: it.open)!!.cohort.durationDays }, { it.running == null }))
}

/** "Live right now" (FR-001a, FR-062a): read from devnet with no wallet; the figures derive from the rosters as every screen's do. */
@Composable
private fun LiveRightNow(live: List<Holdout>, now: Long, rosters: Map<SolanaPublicKey, List<MembershipSnapshot>>) {
    val rows = liveRows(live, now)
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Live right now", Modifier.weight(1f))
            Text("Test network, test token.", style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
        // A slim card, as the board draws it: the rows' own padding sets the rhythm.
        HoldoutCard(padding = PaddingValues(horizontal = Spacing.cardPadding, vertical = Spacing.base / 2)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(Spacing.border).background(HoldoutColors.Hairline))
                LiveRow(row, now, row.running?.let { rosters[it.address] })
            }
        }
    }
}

/**
 * One Holdout of the card (board Connect, FR-053): its label with "● running", the count with room above it and the
 * pool in the money colour, and — for the next round of the level — the tonal "NEXT ROUND" block.
 */
@Composable
private fun LiveRow(row: LiveRowData, now: Long, roster: List<MembershipSnapshot>?) {
    val running = row.running
    val shown = running ?: row.open!!
    val c = shown.cohort
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight), modifier = Modifier.padding(vertical = Spacing.tight + Spacing.base)) {
        if (shown.isTest) TestCohortBadge(c.dayLength)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            val day = (Derive.dayIndex(c, now) + 1).coerceIn(1, c.durationDays)
            Text(
                text = if (running != null) Format.headerLabel(c, day) else Format.level(c).uppercase(),
                style = LabelStyle,
                color = HoldoutColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            if (running != null) StateWord("running", HoldoutColors.Success, RUNNING_DOT_TAG) else StateWord("open", HoldoutColors.Accent)
        }
        if (running != null) {
            val figures = roster?.let { Derive.figures(c, it, now) }
            val standing = figures?.survivors?.standing
            val starters = figures?.survivors?.starters ?: c.memberCount
            val pool = figures?.projectedPool ?: c.pool
            // The count has room above it: a full item gap under the label (board Connect, 2026-10-14).
            Row(Modifier.padding(top = Spacing.base), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
                Text(
                    text = if (standing != null) figureText("$standing", " / $starters still standing") else figureText("$starters", " started"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = HoldoutColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(pool), style = PoolStyle, color = HoldoutColors.Success)
                    Text("prize pool", style = LabelStyle.copy(fontFamily = HoldoutFonts.Mono, fontWeight = FontWeight.Normal), color = HoldoutColors.TextTertiary)
                }
            }
        }
        row.open?.let { NextRound(it, now) }
    }
}

/** A state in a word beside a small dot of its colour: the word is the meaning, the dot only the cue (never a chip). */
@Composable
private fun StateWord(word: String, dot: Color, dotTag: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Box(Modifier.size(STATE_DOT).background(dot, CircleShape).then(if (dotTag != null) Modifier.testTag(dotTag) else Modifier))
        Text(word, style = LabelStyle.copy(fontWeight = FontWeight.Normal), color = HoldoutColors.TextSecondary)
    }
}

/** The big number in a figure line: "13" in the headline weight, light, then the words. */
@Composable
private fun figureText(number: String, words: String): AnnotatedString = buildAnnotatedString {
    withStyle(MaterialTheme.typography.headlineMedium.toSpanStyle().copy(fontWeight = FontWeight.Light, color = HoldoutColors.TextPrimary)) { append(number) }
    append(words)
}

/**
 * The next round of a level (FR-053), on a `surface-2` block: "NEXT ROUND" and how long joining stays open
 * (the countdown in the accent), what is at stake and who joined, and — only while the level's reserve holds
 * more than 0 — "up to {boost} boost", never a "+" and never a promise.
 */
@Composable
private fun NextRound(h: Holdout, now: Long) {
    val c = h.cohort
    val boost = h.league?.boostReserve?.takeIf { it > 0uL }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface2, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.betweenItems, vertical = Spacing.tight),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("next round".uppercase(), style = LabelStyle, color = HoldoutColors.TextTertiary)
            Text(
                buildAnnotatedString {
                    append("closes in ")
                    withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.Accent)) { append(Format.countdown(Derive.timeUntilJoiningCloses(c, now))) }
                },
                style = BlockStyle,
                color = HoldoutColors.TextTertiary,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.TextPrimary)) { append(Format.skr(Derive.atStake(c))) }
                    append(" at stake · ${c.memberCount} joined")
                },
                style = BlockStyle,
                color = HoldoutColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (boost != null) {
                Text(
                    buildAnnotatedString {
                        append("up to ")
                        withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.TextSecondary)) { append(Format.skr(boost).removeSuffix(" SKR")) }
                        append(" boost")
                    },
                    style = BlockStyle,
                    color = HoldoutColors.TextTertiary,
                    softWrap = false,
                )
            }
        }
    }
}

private const val WRONG_NETWORK_NOTICE = "Your wallet isn't on devnet, so nothing was signed. Switch it to devnet, then connect again."
private const val COULD_NOT_CONNECT = "We couldn't connect to your wallet. Try again."
private const val NOTICE_BORDER = 0.28f

/** The board's smaller type, so the first-open card holds two live rows and a notice on a Seeker (layout only; the words are the book's). */
private val HeadlineStyle = DisplayStyle.copy(fontSize = 34.sp, lineHeight = 37.sp, textAlign = TextAlign.Start)
private val ProductStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 21.sp)
private val NoticeStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 19.sp)
private val BlockStyle = LabelStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
private val PoolStyle = AmountStyle.copy(fontSize = 15.sp, lineHeight = 20.sp)
private val STATE_DOT = 6.dp

/** Test tags on the dots whose colour is the point: the product bullets and a running Holdout's dot. */
const val PRODUCT_DOT_TAG = "product-dot"
const val RUNNING_DOT_TAG = "running-dot"
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
