package holdout.app.holdouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.chain.Deployment
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.ui.Body
import holdout.app.ui.Chevron
import holdout.app.ui.ChipTone
import holdout.app.ui.DevnetBar
import holdout.app.ui.DevnetBarCopy
import holdout.app.ui.HoldoutCard
import holdout.app.ui.NoticeCard
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.Segment
import holdout.app.ui.SegmentedSwitch
import holdout.app.ui.StatusChip
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * H0 My Holdouts, the Holdouts tab root (FR-069c, boards 10 and 10b): two tabs, Now and Past.
 *
 * ```
 *  Holdouts                                        headline
 *  ( Now · 3 )  Past · 4                           SegmentedSwitch (only with a Holdout of the member's own)
 *  ┌ Habit · 21-day                 [RUNNING] ┐    Now: HoldoutCard · StatusChip
 *  │ Day 12 of 21 · 189 still standing        │    body, text-secondary
 *  └ 15 push-ups to go today                  ┘    amount, the state's colour → its screen
 *  (empty: NoticeCard "No Holdouts yet.", no switch)
 *  OPEN TO JOIN                    Holdouts need a Seeker.
 *  ┌ Spark · 7-day                          [ Join ] ┐   level · 9,000 SKR at stake · 9 joined
 *  └ Prize boost: up to 480 SKR · Joining closes in 2d 3h ┘   (the whole card joins; only what can be joined; none at all while a lobby place is held)
 *
 *  WON                                             Past: a section label over one card of rows
 *  ┌ Spark · 7-day                  1,180 SKR  › ┐  name · "Held out 7 of 7 · 12 of 14 finished" → H4
 *  └                                   claimed    ┘  amount in mono, success
 *  LOST                                            "Out on day 5 of 21", the stake in warning → H6
 * ```
 *
 * Stateless: [state] is null while nothing has been read; every tap is a
 * [NavEvent] the shell reduces, or [onJoin] for an offer (the shell knows the
 * Seeker gate). While devnet cannot be reached the one devnet bar sits under the title (FR-069h);
 * [header] holds the "not live" line.
 */
@Composable
fun MyHoldoutsScreen(
    state: MyHoldoutsState?,
    onEvent: (NavEvent) -> Unit,
    onJoin: (cohort: String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
    /** False once this session's Seeker check found no Genesis Token: the offers show without a Join, and one line says why (G12). */
    canJoin: Boolean = true,
    /** The running Holdouts the member is not in, to watch (FR-062a): they end Now, for every wallet, and open H4 on this tab. */
    running: List<RunningHoldout> = emptyList(),
    /** The last chain read failed (FR-069h): the devnet bar directly under the title; the figures stay. */
    offline: Boolean = false,
    stillUnreachable: Boolean = false,
    checking: Boolean = false,
    onCheck: () -> Unit = {},
    /** How old the read the screen shows is, in seconds; null when no read time is on record. */
    readAgoSeconds: Long? = null,
) {
    // The choice survives rotation and a process death; it is Now until the member picks Past.
    var tab by rememberSaveable { mutableIntStateOf(NOW) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Holdouts", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        if (offline) DevnetBar(Deployment.networkName, stillUnreachable, checking, DevnetBarCopy.showing(readAgoSeconds), onCheck)
        header()
        if (state == null) {
            Reading { SectionLabel("Reading the chain…") }
            return@Column
        }

        if (state.empty) {
            NoticeCard(MyHoldoutsModel.EMPTY_TITLE, MyHoldoutsModel.EMPTY_TEXT)
        } else {
            SegmentedSwitch(listOf(Segment("Now", state.nowCount), Segment("Past", state.pastCount)), selected = tab, onSelect = { tab = it })
        }

        if (tab == PAST && !state.empty) {
            PastTab(state, onEvent)
            return@Column
        }

        state.yours.forEach { YourCard(it) { onEvent(it.go) } }

        // A lobby place held means nothing more can be joined: no section at all, not even its header (owner, 12 Oct).
        if (!state.holdsLobbyPlace) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                SectionLabel("Open to join")
                Text(MyHoldoutsModel.SEEKER_LINE, style = LabelStyle, color = HoldoutColors.TextTertiary)
            }
            if (state.open.isEmpty()) Body("The next Holdout opens soon.")
            if (!canJoin && state.open.isNotEmpty()) Body(MyHoldoutsModel.SEEKER_ONLY)
            state.open.forEach { OfferCard(it, onJoin = if (canJoin) ({ onJoin(it.cohort) }) else null) }
        }

        if (running.isNotEmpty()) {
            SectionLabel("Running now")
            running.forEach { r -> RunningNowCard(r, onOpen = { onEvent(r.go) }) }
        }
    }
}

private const val NOW = 0
private const val PAST = 1

/** The Past tab: the Holdouts the member won, then the ones they lost; a group with no rows is not drawn. */
@Composable
private fun ColumnScope.PastTab(state: MyHoldoutsState, onEvent: (NavEvent) -> Unit) {
    if (state.won.isEmpty() && state.lost.isEmpty()) Body("Nothing finished yet.")
    if (state.won.isNotEmpty()) PastGroup("Won", state.won, "claimed", HoldoutColors.Success, onEvent)
    if (state.lost.isNotEmpty()) PastGroup("Lost", state.lost, "lost", HoldoutColors.Warning, onEvent)
}

/** A section label over one card of rows with hairlines between them (board 10b). */
@Composable
private fun PastGroup(title: String, rows: List<PastHoldout>, caption: String, amountColor: Color, onEvent: (NavEvent) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        SectionLabel(title)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(HoldoutColors.Surface1, shape)
                .border(Spacing.border, HoldoutColors.Hairline, shape)
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.base),
        ) {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
                PastRow(row, caption, amountColor) { onEvent(row.go) }
            }
        }
    }
}

/** One past Holdout: name over its line, the amount in mono over a quiet caption, a chevron; the whole row opens it. */
@Composable
private fun PastRow(row: PastHoldout, caption: String, amountColor: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PAST_ROW_HEIGHT)
            .clickable(onClickLabel = "Open", onClick = onClick)
            .padding(vertical = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = HoldoutColors.TextPrimary)
            Text(row.line, style = LabelStyle, color = HoldoutColors.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.amount, style = AmountStyle.copy(fontSize = 15.sp, lineHeight = 20.sp), color = amountColor)
            Text(caption, style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
        Chevron()
    }
}

private val PAST_ROW_HEIGHT = 56.dp

/** One of the member's Holdouts: level and chip, the state in a line, and the next thing in the state's colour. */
@Composable
private fun YourCard(card: YourHoldout, onClick: () -> Unit) {
    HoldoutCard(Modifier.clickable(onClickLabel = "Open", onClick = onClick)) {
        if (card.test) TestCohortBadge(card.testDayLength)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
            StatusChip(card.status, tone = card.tone)
        }
        Body(card.line)
        card.detail?.let { Text(it, style = AmountStyle.copy(fontSize = LabelStyle.fontSize, lineHeight = LabelStyle.lineHeight), color = card.detailTone.color()) }
    }
}

/**
 * An offer: the level, what is at stake and how many joined, the boost, how long
 * joining stays open — and a Join that goes through: an offer the member could not
 * take is not listed (FR-006a, FR-006b). The Join is a 44 dp pill at the card's end; a tap anywhere on the card does the same.
 */
@Composable
private fun OfferCard(offer: OpenHoldout, onJoin: (() -> Unit)?) {
    // The whole card joins, as the pill does (owner, 12 Oct canvas): the stake sheet is the confirm. Without a Join it is only read.
    HoldoutCard(if (onJoin == null) Modifier else Modifier.clickable(onClickLabel = "Join ${offer.name}", onClick = onJoin)) {
        if (offer.test) TestCohortBadge(offer.testDayLength)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Text(offer.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
            onJoin?.let { JoinPill(onClick = it) }
        }
        // Under the title row, not beside the Join: the stake line gets the card's whole width and stays on one line.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight), verticalAlignment = Alignment.Bottom) {
            Text(offer.amount, style = AmountStyle, color = HoldoutColors.TextPrimary)
            Text(offer.note, style = LabelStyle, color = HoldoutColors.TextSecondary)
        }
        offer.boost?.let { Text(it, style = LabelStyle, color = HoldoutColors.Success) }
        Text(offer.closesIn, style = LabelStyle, color = HoldoutColors.Accent)
    }
}

/** "Join": an outlined pill 44 dp tall inside a 48 dp target. */
@Composable
private fun JoinPill(onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        Modifier
            .height(Spacing.touchTarget)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(PILL_HEIGHT)
                .background(HoldoutColors.Canvas, shape)
                .border(Spacing.border, HoldoutColors.Hairline, shape)
                .padding(horizontal = Spacing.cardPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Join", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        }
    }
}

private val PILL_HEIGHT = 44.dp

private fun ChipTone.color() = when (this) {
    ChipTone.Success -> HoldoutColors.Success
    ChipTone.Warning -> HoldoutColors.Warning
    ChipTone.Accent -> HoldoutColors.Accent
    ChipTone.Neutral -> HoldoutColors.TextSecondary
}

private val PREVIEW = MyHoldoutsState(
    yours = listOf(
        YourHoldout("r", "Habit · 21-day", YourKind.Running, ChipTone.Success, "Day 12 of 21 · 189 still standing", "15 push-ups to go today", false, NavEvent.Open(Screen.Detail("r"))),
        YourHoldout("o", "Habit · 21-day", YourKind.Out, ChipTone.Warning, "Lost 1,250 SKR · out on day 7", "Win back 125 SKR", false, NavEvent.Open(Screen.Forfeit("o")), detailTone = ChipTone.Success),
        YourHoldout("l", "Spark · 7-day", YourKind.Lobby, ChipTone.Accent, "Joined · 1,250 SKR staked", "starts in 2d 4h", false, NavEvent.Open(Screen.Lobby("l"))),
        YourHoldout("k", "Spark · 7-day", YourKind.Locked, ChipTone.Neutral, "Joining closed · 14 in · 1,250 SKR staked", "starts in 41m 10s", false, NavEvent.Open(Screen.Lobby("k"))),
        YourHoldout("c", "Spark · 7-day", YourKind.Claim, ChipTone.Success, "Held out 7 of 7", "1,412 SKR waiting for you", false, NavEvent.Open(Screen.Claim("c"))),
    ),
    open = listOf(
        OpenHoldout("o", "Habit · 21-day", "9,000 SKR", "at stake · 9 joined", "Prize boost: up to 1,200 SKR", "Joining closes in 2d 3h", false),
        OpenHoldout("p", "Spark · 7-day", "12,500 SKR", "at stake · 14 joined", "Prize boost: up to 480 SKR", "Joining closes in 2d 3h", false),
    ),
    won = listOf(
        PastHoldout("f", "Spark · 7-day", "Held out 7 of 7 · 12 of 14 finished", "1,180 SKR", NavEvent.Open(Screen.Detail("f"))),
        PastHoldout("g", "Hackathon · 3-day", "Held out 3 of 3 · 4 of 5 finished", "605.42 SKR", NavEvent.Open(Screen.Detail("g"))),
    ),
    lost = listOf(PastHoldout("x", "Habit · 21-day", "Out on day 5 of 21", "2,500 SKR", NavEvent.Open(Screen.Forfeit("x")))),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 1700)
@Composable
private fun MyHoldoutsPreview() {
    HoldoutTheme { MyHoldoutsScreen(PREVIEW, onEvent = {}, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun MyHoldoutsEmptyPreview() {
    HoldoutTheme { MyHoldoutsScreen(PREVIEW.copy(yours = emptyList(), won = emptyList(), lost = emptyList()), onEvent = {}, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun MyHoldoutsPastPreview() {
    HoldoutTheme {
        Column(Modifier.background(HoldoutColors.Canvas).padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            SegmentedSwitch(listOf(Segment("Now", PREVIEW.nowCount), Segment("Past", PREVIEW.pastCount)), selected = PAST, onSelect = {})
            PastTab(PREVIEW, onEvent = {})
        }
    }
}
