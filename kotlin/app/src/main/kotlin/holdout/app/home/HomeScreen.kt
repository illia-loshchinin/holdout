package holdout.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.chain.Deployment
import holdout.app.nav.NavEvent
import holdout.app.streak.StreakMilestones
import holdout.app.streak.ToesMeter
import holdout.app.ui.BlockBars
import holdout.app.ui.Body
import holdout.app.ui.ChainUnreachable
import holdout.app.ui.ChipTone
import holdout.app.ui.CountdownText
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.StatusChip
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * What Today (T1, no Holdout running) shows: the Daily 25, then the member's
 * nudges, the comeback card and the Holdouts open to join (board 3 · 3b · 3c;
 * FR-053, FR-048…FR-052, ES-3, ES-15).
 */
data class HomeState(
    val remainingToday: Int,
    val bankedToday: Int,
    val target: Int,
    val completedToday: Boolean,
    val streak: Int,
    /** Null while chain time is unknown — the screen says nothing rather than guessing. */
    val secondsLeftToday: Long?,
    /** Streak freezes held, 0–2 (H35): each keeps the streak through one missed day. */
    val freezes: Int = 0,
    /** Today's toes meter (H35), or null before any Daily 25 rep is counted. */
    val toes: ToesMeter.Meter? = null,
    /** "Yesterday ended at 20 of 25. Today starts fresh." ([TodayModel.yesterdayNote]), or null. */
    val yesterdayNote: String? = null,
    /** "No banked reps on this device today. 25 to go." — a phone with nothing banked for a streak that is running (ES-3a). */
    val newPhone: Boolean = false,
    /** Today's day is done and took the streak to a milestone (`StreakMilestones.reachedToday`): the chip shows the milestone state and its line, for the rest of the day (Gap 2). */
    val milestoneToday: Boolean = false,
    /** The member's other Holdouts that need a tap, one line each (screen map §3 rule 4). */
    val nudges: List<NudgeRowState> = emptyList(),
    /** The way back after a lost stake, one tap (FR-037g). */
    val comeback: ComebackCardState? = null,
    val holdouts: HoldoutsBlock = HoldoutsBlock.Reading,
    /** "Check again" is out / found the chain still silent (spec 005). */
    val checking: Boolean = false,
    val stillUnreachable: Boolean = false,
)

/**
 * ```
 *  TODAY                                              🔥 12     ScreenLabel · StreakFlame
 *                          A week, every day. That's how habits start.   milestone day only
 *  ┌ THE DAILY 25 [FREE]                 4h 06m left today ─┐   HoldoutCard (compact) · StatusChip · CountdownText
 *  │ Yesterday ended at 20 of 25. Today starts fresh.        │   banner (warning)
 *  │                 25 push-ups                             │   CounterStyle, 44 sp
 *  │ all at once, or 10 at a time · knees allowed            │
 *  │ [ Start ]                                               │   PrimaryButton, 48 dp
 *  └─────────────────────────────────────────────────────────┘
 *  ( You finished · claim 1,412 SKR  › )                          nudge rows
 *  ( Get back 125 SKR of your lost stake: …  › )                 comeback card
 *  HOLDOUTS OPEN TO JOIN                    Holdouts need a Seeker.
 *  [ Spark · 7-day … (Join) ]  [ Habit · 21-day … (Join) ]        OfferCard
 *  ( ⓘ How Holdout works                          5 cards )
 * ```
 * Never empty and never "come back later": when today is already done the card
 * says so and the streak carries it. Running Holdouts to watch live on the
 * Holdouts tab (ruling G14), not here.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onStart: () -> Unit,
    onEvent: (NavEvent) -> Unit,
    onJoin: (cohort: String) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TODAY", style = LabelStyle, color = HoldoutColors.TextTertiary)
            StreakFlame(
                days = state.streak,
                state = when {
                    state.milestoneToday -> StreakState.Milestone
                    state.completedToday -> StreakState.ExtendedToday
                    state.streak > 0 -> StreakState.Active
                    else -> StreakState.Broken
                },
                chip = true,
            )
        }
        // The milestone's line, under the chip, for the rest of the day it was reached (the next day it is the plain chip).
        if (state.milestoneToday) {
            StreakMilestones.line(state.streak)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }

        DailyCard(state, onStart)

        state.nudges.forEach { NudgeRow(it) { onEvent(it.go) } }
        state.comeback?.let { ComebackCard(it) { onEvent(it.go) } }

        when (val block = state.holdouts) {
            HoldoutsBlock.Reading -> Reading { SectionLabel("Reading the chain…") }
            HoldoutsBlock.Unreachable -> {
                SectionLabel("${Deployment.networkName} unreachable — the Daily 25 still works")
                ChainUnreachable(state.checking, state.stillUnreachable, onCheck)
            }
            HoldoutsBlock.NotLive -> SectionLabel("Holdouts are not live on ${Deployment.networkName.lowercase()} yet")
            // A lobby place is held: nothing more to join, so nothing is drawn (the lobby line above says it all).
            HoldoutsBlock.Hidden -> Unit
            is HoldoutsBlock.Open -> {
                if (block.stale) ChainUnreachable(state.checking, state.stillUnreachable, onCheck)
                if (block.cards.isEmpty()) {
                    HoldoutCard {
                        Text(TodayModel.OPENS_SOON, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
                        Body(TodayModel.OPENS_SOON_DETAIL)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("Holdouts open to join")
                        Text(TodayModel.SEEKER_LINE, style = LabelStyle, color = HoldoutColors.TextTertiary)
                    }
                    block.cards.forEach { OfferCard(it, onJoin = { onJoin(it.cohort) }) }
                }
            }
        }

        TourRow { onEvent(NavEvent.OpenTour) }
    }
}

@Composable
private fun DailyCard(state: HomeState, onStart: () -> Unit) {
    // Compact (owner, 12 Oct): the time left sits in the header row, a smaller counter and button, tighter insets.
    HoldoutCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    SectionLabel("The Daily 25")
                    StatusChip("free", tone = ChipTone.Accent)
                }
                if (!state.completedToday) {
                    state.secondsLeftToday?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.base), verticalAlignment = Alignment.Bottom) {
                            CountdownText(it, style = LabelStyle.copy(fontFamily = HoldoutFonts.Mono))
                            Text("left today", style = LabelStyle, color = HoldoutColors.TextSecondary)
                        }
                    }
                }
            }
            state.yesterdayNote?.let { Banner(it, warning = true) }
            if (state.newPhone) Banner(NewPhone.line(state.target), warning = false)

            when {
                state.completedToday -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                        Text("${state.target}", style = DailyCounter, color = HoldoutColors.Success)
                        Text("done", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, modifier = Modifier.padding(bottom = Spacing.base))
                    }
                    Body("Streak +1. See you tomorrow.", center = true)
                    state.toes?.let { ToesMeterCard(it, state.target) }
                }
                state.bankedToday > 0 -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                        Text("${state.remainingToday}", style = DailyCounter, color = HoldoutColors.TextPrimary)
                        Text("to go", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, modifier = Modifier.padding(bottom = Spacing.base))
                    }
                    BlockBars(reps = state.target, bankedBlocks = state.bankedToday / BANK_BLOCK)
                    Body("${state.bankedToday} banked for good", center = true)
                    PrimaryButton("Keep going", onStart, Modifier.height(Spacing.touchTarget))
                }
                else -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                        Text("${state.remainingToday}", style = DailyCounter, color = HoldoutColors.TextPrimary)
                        Text("push-ups", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, modifier = Modifier.padding(bottom = Spacing.base))
                    }
                    Body("all at once, or $BANK_BLOCK at a time · knees allowed", center = true)
                    PrimaryButton("Start", onStart, Modifier.height(Spacing.touchTarget))
                }
            }

            if (state.streak > 0) {
                Text(
                    text = when (state.freezes) {
                        0 -> "Seven days in a row give you a freeze for a missed day"
                        1 -> "1 freeze held — a missed day won't break the streak"
                        else -> "${state.freezes} freezes held — two missed days won't break the streak"
                    },
                    style = LabelStyle,
                    color = HoldoutColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The Daily 25 card's counter: [CounterStyle] at 44 sp, so the card stays compact. */
private val DailyCounter = CounterStyle.copy(fontSize = 44.sp, lineHeight = 48.sp)

/** A one-line note in the card, tinted by its tone; the colour is never the only signal, the words carry it. */
@Composable
internal fun Banner(text: String, warning: Boolean) {
    val tint = if (warning) HoldoutColors.Warning.copy(alpha = BANNER_TINT) else HoldoutColors.Surface3
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(tint)
            .padding(horizontal = Spacing.betweenItems, vertical = Spacing.tight),
    ) {
        Text(
            text,
            style = LabelStyle,
            color = if (warning) HoldoutColors.Warning else HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One of the member's Holdouts that needs a tap: a pill row with a chevron, tinted by what it asks. */
@Composable
fun NudgeRow(nudge: NudgeRowState, onClick: () -> Unit) {
    val color = when (nudge.tone) {
        NudgeTone.Success -> HoldoutColors.Success
        NudgeTone.Warning -> HoldoutColors.Warning
        NudgeTone.Neutral -> HoldoutColors.TextPrimary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (nudge.tone == NudgeTone.Neutral) HoldoutColors.Surface1 else color.copy(alpha = BANNER_TINT))
            .border(Spacing.border, if (nudge.tone == NudgeTone.Neutral) HoldoutColors.Hairline else color.copy(alpha = BORDER_TINT), MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text(nudge.text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** The way back after a lost stake (FR-037g): green, the whole card one tap. */
@Composable
private fun ComebackCard(card: ComebackCardState, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HoldoutColors.Success.copy(alpha = BANNER_TINT), shape)
            .border(Spacing.border, HoldoutColors.Success.copy(alpha = BORDER_TINT), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.betweenItems),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text(card.text, style = LabelStyle, color = HoldoutColors.Success, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
    }
}

/** "How Holdout works · 5 cards" (board 3): the tour, again, from Today. */
@Composable
private fun TourRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraLarge)
            .border(Spacing.border, HoldoutColors.Hairline, MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("How Holdout works", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("5 cards", style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

/**
 * "18 of 25 from your toes", seven bars for the last week and the best day
 * (H35): how far from a Habit today's Daily 25 was. Measured at 45° only;
 * head-on it says how to see it instead of guessing.
 */
@Composable
private fun ToesMeterCard(meter: ToesMeter.Meter, target: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
        if (meter.measured == 0) {
            Text(
                "Do it with the phone at 45° to see how many come from your toes",
                style = LabelStyle,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        Text(
            if (meter.measured < target) "${meter.toes} of ${meter.measured} seen at 45° from your toes" else "${meter.toes} of ${meter.measured} from your toes",
            style = AmountStyle,
            color = if (meter.hardDay(target)) HoldoutColors.Success else HoldoutColors.TextPrimary,
        )
        if (meter.hardDay(target)) {
            Text("That's a Habit day.", style = LabelStyle, color = HoldoutColors.Success)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(METER_HEIGHT).padding(top = Spacing.base),
        ) {
            meter.week.forEach { toes ->
                Box(
                    modifier = Modifier
                        .width(METER_BAR)
                        .height(METER_FLOOR + (METER_HEIGHT - METER_FLOOR) * toes.coerceAtMost(target) / target.coerceAtLeast(1))
                        .background(if (toes >= target) HoldoutColors.Success else HoldoutColors.Accent),
                )
            }
        }
        Text("Best: ${meter.best} from your toes", style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

private const val BANK_BLOCK = 10
private const val BANNER_TINT = 0.10f
private const val BORDER_TINT = 0.32f
private val METER_HEIGHT = 32.dp
private val METER_FLOOR = 4.dp
private val METER_BAR = 10.dp

private val OFFER = OfferCardState(
    cohort = "o1",
    title = "Spark · 7-day",
    tagline = "Light it up · knees allowed",
    atStake = "12,500 SKR",
    joined = "at stake · 14 joined",
    timing = "Joining closes in 2d 3h",
    boost = "Prize boost: up to 480 SKR",
    test = false,
    testDayLength = 0,
)

private val FRESH = HomeState(
    remainingToday = 25,
    bankedToday = 0,
    target = 25,
    completedToday = false,
    streak = 12,
    secondsLeftToday = 14_760,
    holdouts = HoldoutsBlock.Open(listOf(OFFER, OFFER.copy(cohort = "o2", title = "Habit · 21-day", tagline = "Make it stick · toes only", atStake = "9,000 SKR", joined = "at stake · 9 joined", boost = null))),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun HomeFreshPreview() {
    HoldoutTheme { HomeScreen(FRESH, onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun HomePartwayPreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(remainingToday = 15, bankedToday = 10, streak = 3, freezes = 1), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun HomeDonePreview() {
    HoldoutTheme {
        HomeScreen(
            FRESH.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 13, nudges = listOf(NudgeRowState("You finished · claim 1,412 SKR", NavEvent.TourDone, NudgeTone.Success))),
            onStart = {},
            onEvent = {},
            onJoin = {},
            onCheck = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun HomeMilestonePreview() {
    HoldoutTheme {
        HomeScreen(FRESH.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 7, milestoneToday = true), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun HomeNewPhonePreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(newPhone = true), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun HomeUnreachablePreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}
