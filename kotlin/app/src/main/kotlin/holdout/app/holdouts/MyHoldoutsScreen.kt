package holdout.app.holdouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.ui.Body
import holdout.app.ui.ChipTone
import holdout.app.ui.HoldoutCard
import holdout.app.ui.NoticeCard
import holdout.app.ui.QuietAction
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.StatusChip
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * H0 My Holdouts, the Holdouts tab root (FR-069c, board 10).
 *
 * ```
 *  Holdouts                                        headline
 *  YOURS                                           SectionLabel
 *  ┌ Habit · 21-day                 [RUNNING] ┐    HoldoutCard · StatusChip
 *  │ Day 12 of 21 · 189 still standing        │    body, text-secondary
 *  └ 15 push-ups to go today                  ┘    amount, the state's colour → its screen
 *  (empty: NoticeCard "No Holdouts yet.")
 *  OPEN TO JOIN                    Holdouts need a Seeker.
 *  ┌ Spark · 7-day                          [ Join ] ┐   level · 9,000 SKR at stake · 9 joined
 *  └ Prize boost: up to 480 SKR · Joining closes in 2d 3h ┘   (only what can be joined; none at all while a lobby place is held)
 *  RUNNING NOW                                     read-only, → H4 (FR-062a)
 *  FINISHED                                        one quiet line each → H4 / H6
 * ```
 *
 * Stateless: [state] is null while nothing has been read; every tap is a
 * [NavEvent] the shell reduces, or [onJoin] for an offer (the shell knows the
 * Seeker gate). [header] holds the shell's chain notices (offline, not live).
 */
@Composable
fun MyHoldoutsScreen(
    state: MyHoldoutsState?,
    onEvent: (NavEvent) -> Unit,
    onJoin: (cohort: String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Holdouts", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        header()
        if (state == null) {
            Reading { SectionLabel("Reading the chain…") }
            return@Column
        }

        if (state.empty) {
            NoticeCard(MyHoldoutsModel.EMPTY_TITLE, MyHoldoutsModel.EMPTY_TEXT)
        } else if (state.yours.isNotEmpty()) {
            SectionLabel("Yours")
        }
        state.yours.forEach { YourCard(it) { onEvent(it.go) } }

        // A lobby place held means nothing more can be joined: no section at all, not even its header (owner, 12 Oct).
        if (!state.holdsLobbyPlace) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                SectionLabel("Open to join")
                Text(MyHoldoutsModel.SEEKER_LINE, style = LabelStyle, color = HoldoutColors.TextTertiary)
            }
            if (state.open.isEmpty()) Body("The next Holdout opens soon.")
            state.open.forEach { OfferCard(it) { onJoin(it.cohort) } }
        }

        if (state.running.isNotEmpty()) {
            SectionLabel("Running now")
            state.running.forEach { r -> RunningNowCard(r, onOpen = { onEvent(r.go) }) }
        }

        if (state.finished.isNotEmpty()) {
            SectionLabel("Finished")
            state.finished.forEach { f -> QuietAction("${f.line} →", { onEvent(f.go) }) }
        }
    }
}

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
 * take is not listed (FR-006a, FR-006b). The Join is a 44 dp pill at the card's end.
 */
@Composable
private fun OfferCard(offer: OpenHoldout, onJoin: () -> Unit) {
    HoldoutCard {
        if (offer.test) TestCohortBadge(offer.testDayLength)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Text(offer.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
            JoinPill(onClick = onJoin)
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
        YourHoldout("o", "Habit · 21-day", YourKind.Out, ChipTone.Warning, "Out after 6 days", "Win back up to 250 SKR", false, NavEvent.Open(Screen.Forfeit("o")), detailTone = ChipTone.Success),
        YourHoldout("l", "Spark · 7-day", YourKind.Lobby, ChipTone.Accent, "Joined · 1,250 SKR staked", "starts in 2d 4h", false, NavEvent.Open(Screen.Lobby("l"))),
        YourHoldout("k", "Spark · 7-day", YourKind.Locked, ChipTone.Neutral, "Joining closed · 14 in · 1,250 SKR staked", "starts in 41m 10s", false, NavEvent.Open(Screen.Lobby("k"))),
        YourHoldout("c", "Spark · 7-day", YourKind.Claim, ChipTone.Success, "Held out 7 of 7", "1,412 SKR waiting for you", false, NavEvent.Open(Screen.Claim("c"))),
    ),
    open = listOf(
        OpenHoldout("o", "Habit · 21-day", "9,000 SKR", "at stake · 9 joined", "Prize boost: up to 1,200 SKR", "Joining closes in 2d 3h", false),
        OpenHoldout("p", "Spark · 7-day", "12,500 SKR", "at stake · 14 joined", "Prize boost: up to 480 SKR", "Joining closes in 2d 3h", false),
    ),
    finished = listOf(FinishedHoldout("f", "Spark · 7-day · finished · claimed 1,180 SKR", NavEvent.Open(Screen.Detail("f")))),
    running = listOf(RunningHoldout("w", "Spark · 7-day · day 3 of 7", "9 still standing of 12 · prize pool 2,400 SKR", false)),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 1700)
@Composable
private fun MyHoldoutsPreview() {
    HoldoutTheme { MyHoldoutsScreen(PREVIEW, onEvent = {}, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun MyHoldoutsEmptyPreview() {
    HoldoutTheme { MyHoldoutsScreen(PREVIEW.copy(yours = emptyList(), finished = emptyList()), onEvent = {}, onJoin = {}) }
}
