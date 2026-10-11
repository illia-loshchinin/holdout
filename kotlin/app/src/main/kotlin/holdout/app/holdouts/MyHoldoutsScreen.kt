package holdout.app.holdouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.ui.Body
import holdout.app.ui.ChipTone
import holdout.app.ui.HoldoutCard
import holdout.app.ui.NoticeCard
import holdout.app.ui.QuietAction
import holdout.app.ui.Reading
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.StatusChip
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * H0 My Holdouts, the Holdouts tab root (screen map H0, board 10).
 *
 * ```
 *  Holdouts                                        headlineMedium
 *  YOURS                                           SectionLabel
 *  ┌ Habit · 21-day                 [running] ┐    HoldoutCard · StatusChip
 *  │ Day 12 of 21 · 189 still standing        │    bodyLarge, text-secondary
 *  └ 15 push-ups to go today                  ┘    amount, the state's colour → its screen
 *  (empty: NoticeCard "No Holdouts yet.")
 *  OPEN TO JOIN · Holdouts need a Seeker.          SectionLabel · Body
 *  ┌ Spark · 7-day / 9,000 SKR at stake · 9 joined / boost / joining closes in 2d 3h / [Join] ┐
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

        SectionLabel("Yours")
        if (state.empty) NoticeCard(MyHoldoutsModel.EMPTY_TITLE, MyHoldoutsModel.EMPTY_TEXT)
        state.yours.forEach { YourCard(it) { onEvent(it.go) } }

        SectionLabel("Open to join")
        Body(MyHoldoutsModel.SEEKER_LINE)
        if (state.open.isEmpty()) Body("The next Holdout opens soon.")
        state.open.forEach { OfferCard(it) { onJoin(it.cohort) } }

        if (state.running.isNotEmpty()) {
            SectionLabel("Running now")
            state.running.forEach { r ->
                HoldoutCard(Modifier.clickable(onClickLabel = "Watch this Holdout") { onEvent(r.go) }) {
                    if (r.test) TestCohortBadge(r.testDayLength)
                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                    Text(r.line, style = AmountStyle, color = HoldoutColors.Accent)
                }
            }
        }

        if (state.finished.isNotEmpty()) {
            SectionLabel("Finished")
            state.finished.forEach { f -> QuietAction("${f.line} →", { onEvent(f.go) }) }
        }
    }
}

@Composable
private fun YourCard(card: YourHoldout, onClick: () -> Unit) {
    HoldoutCard(Modifier.clickable(onClickLabel = "Open", onClick = onClick)) {
        if (card.test) TestCohortBadge(card.testDayLength)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
            StatusChip(card.status, tone = card.tone)
        }
        Body(card.line)
        card.detail?.let { Text(it, style = AmountStyle, color = card.tone.color()) }
    }
}

@Composable
private fun OfferCard(offer: OpenHoldout, onJoin: () -> Unit) {
    HoldoutCard {
        if (offer.test) TestCohortBadge(offer.testDayLength)
        Text(offer.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        Text(offer.atStake, style = AmountStyle, color = HoldoutColors.TextPrimary)
        offer.boost?.let { Text(it, style = LabelStyle, color = HoldoutColors.Success) }
        Text(offer.closesIn, style = AmountStyle, color = HoldoutColors.Accent)
        offer.blocker?.let { Body(it) }
        SecondaryButton("Join", onJoin, enabled = offer.blocker == null)
    }
}

private fun ChipTone.color() = when (this) {
    ChipTone.Success -> HoldoutColors.Success
    ChipTone.Warning -> HoldoutColors.Warning
    ChipTone.Accent -> HoldoutColors.Accent
    ChipTone.Neutral -> HoldoutColors.TextSecondary
}

private val PREVIEW = MyHoldoutsState(
    yours = listOf(
        YourHoldout("r", "Habit · 21-day", "running", ChipTone.Success, "Day 12 of 21 · 189 still standing", "15 push-ups to go today", false, NavEvent.SelectTab(Tab.Today)),
        YourHoldout("l", "Spark · 7-day", "lobby", ChipTone.Accent, "Joined · 1,250 SKR staked", "starts in 2d 4h", false, NavEvent.Open(Screen.Lobby("l"))),
        YourHoldout("c", "Spark · 7-day", "claim", ChipTone.Success, "Held out 7 of 7", "1,412 SKR waiting for you", false, NavEvent.Open(Screen.Claim("c"))),
    ),
    open = listOf(OpenHoldout("o", "Habit · 21-day", "9,000 SKR at stake · 9 joined", "Prize boost: up to 1,200 SKR", "joining closes in 2d 3h", "You already hold a place in one that hasn't started.", false)),
    finished = listOf(FinishedHoldout("f", "Spark · 7-day · claimed 1,180 SKR", NavEvent.Open(Screen.Detail("f")))),
    running = listOf(RunningHoldout("w", "Spark · 7-day · day 3 of 7", "9 still standing of 12 · prize pool 2,400 SKR", false)),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 1400)
@Composable
private fun MyHoldoutsPreview() {
    HoldoutTheme { MyHoldoutsScreen(PREVIEW, onEvent = {}, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun MyHoldoutsEmptyPreview() {
    HoldoutTheme { MyHoldoutsScreen(PREVIEW.copy(yours = emptyList(), finished = emptyList()), onEvent = {}, onJoin = {}) }
}
