package holdout.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.chain.Deployment
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.nav.NavEvent
import holdout.app.nav.Tab
import holdout.app.streak.StreakMilestones
import holdout.app.streak.ToesMeter
import holdout.app.ui.DevnetBar
import holdout.app.ui.Format
import holdout.app.ui.PrimaryButton
import holdout.app.ui.Reading
import holdout.app.ui.SectionLabel
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * What Today (T1, no Holdout running) shows: the Daily 25 in the hero, then the member's
 * nudges, the comeback card and the Holdouts open to join (FR-053, FR-069a, FR-048…FR-052,
 * ES-3, ES-15).
 */
data class HomeState(
    val remainingToday: Int,
    val bankedToday: Int,
    val target: Int,
    val completedToday: Boolean,
    val streak: Int,
    /** The longest streak this member ever had: a 0 streak with a longest above 0 is a broken one, a 0 with none is a member who never trained (as on Me). */
    val longestStreak: Int = 0,
    /** Null while chain time is unknown — the screen says nothing rather than guessing. */
    val secondsLeftToday: Long?,
    /** Streak freezes held, 0–2 (H35): each keeps the streak through one missed day. */
    val freezes: Int = 0,
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
    /** The last chain read failed: the devnet bar shows (FR-069h). Also implied by an unreachable or stale [holdouts]. */
    val offline: Boolean = false,
    /** The devnet bar's Retry is out / found the chain still silent (spec 005). */
    val checking: Boolean = false,
    val stillUnreachable: Boolean = false,
    /** False once this session's Seeker check found no Genesis Token: the offer shows without a Join, and one line says why (G12). */
    val canJoin: Boolean = true,
) {
    /** Whether devnet cannot be reached, by any of the ways the host says so. */
    val chainDown: Boolean get() = offline || holdouts is HoldoutsBlock.Unreachable || (holdouts as? HoldoutsBlock.Open)?.stale == true
}

/**
 * ```
 *  TODAY                                              🔥 12     header · the streak chip, a button to Me
 *  ( ● Devnet isn't answering              Retry )               DevnetBar, only while devnet is silent
 *  ┌ THE DAILY 25                         4h 06m left ┐   DayHero, 312 dp in every state
 *  │                    25                           │
 *  │                  PUSH-UPS                       │
 *  │ ▬▬▬▬▬▬▬▬ ▬▬▬▬▬▬▬▬ ▬▬▬▬                          │   BankBars 10 / 10 / 5
 *  │ Free · knees allowed · in blocks of 10          │
 *  │ ( Start )                                       │
 *  ├─────────────────────────────────────────────────┤
 *  │ 2 more days to a streak freeze                ›  │   the foot, a link to Me
 *  └─────────────────────────────────────────────────┘
 *  ( You finished · claim 1,412 SKR  › )                          the rows group, a little apart
 *  ( Get back 125 SKR of your lost stake: …  › )
 *  OPEN TO JOIN
 *  ┌ Spark · 7-day                          ( Join ) ┐   one compact OfferCard, no boost line
 *  └ 14 joined · closes in 2d 3h ─────────────────────┘
 *    1 more open ›                                          the quiet row to Holdouts
 * ```
 * Never empty and never "come back later": when today is already done the hero says so and the
 * streak carries it. Running Holdouts to watch live on the Holdouts tab (ruling G14), not here.
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
        Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TODAY", style = LabelStyle, color = HoldoutColors.TextTertiary)
            StreakFlame(
                days = state.streak,
                state = when {
                    state.milestoneToday -> StreakState.Milestone
                    state.completedToday -> StreakState.ExtendedToday
                    state.streak > 0 -> StreakState.Active
                    // Broken only once there was a streak to break; a member who never trained has a plain 0.
                    state.longestStreak > 0 -> StreakState.Broken
                    else -> StreakState.Active
                },
                chip = true,
                onClick = { onEvent(NavEvent.SelectTab(Tab.Me)) },
            )
        }
        if (state.chainDown) DevnetBar(Deployment.networkName, state.stillUnreachable, state.checking, DEVNET_SUB, onCheck)

        DailyHero(state, onStart) { onEvent(NavEvent.SelectTab(Tab.Me)) }

        // What else needs a tap: its own group, a little apart from the hero.
        if (state.nudges.isNotEmpty() || state.comeback != null) {
            Column(Modifier.padding(top = Spacing.betweenSections - Spacing.betweenItems), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                state.nudges.forEach { NudgeRow(it) { onEvent(it.go) } }
                state.comeback?.let { ComebackCard(it) { onEvent(it.go) } }
            }
        }

        Offers(state, onEvent, onJoin)
    }
}

/** The devnet bar's line for Today with no Holdout running: the Daily 25 needs nothing from the chain. */
private const val DEVNET_SUB = "The Daily 25 still works."

private val HEADER_HEIGHT = 32.dp

/** The Daily 25 in the one hero frame (FR-069a): its content changes with the day, its size never does. */
@Composable
private fun DailyHero(state: HomeState, onStart: () -> Unit, onOpenMe: () -> Unit) {
    val done = state.completedToday
    val foot = if (state.milestoneToday) StreakMilestones.line(state.streak) ?: TodayModel.streakFoot(state.streak, state.freezes) else TodayModel.streakFoot(state.streak, state.freezes)
    DayHero(
        label = "THE DAILY 25",
        // Done, the deadline is behind the member: nothing to count down to; and without chain time there is nothing to say.
        countdown = if (done) null else state.secondsLeftToday?.let { seconds -> { HeroCountdown(seconds) } },
        number = (if (done) state.target else state.remainingToday).toString(),
        unit = when {
            done -> "done"
            state.bankedToday > 0 -> "to go"
            else -> "push-ups"
        },
        numberColor = if (done) HoldoutColors.Success else HoldoutColors.TextPrimary,
        bars = { BankBars(target = state.target, banked = if (done) state.target else state.bankedToday, fill = if (done) HoldoutColors.Success else HoldoutColors.Accent) },
        sub = {
            when {
                done -> HeroSub("All ${state.target} counted. Streak +1.")
                state.bankedToday > 0 -> HeroSub("${state.bankedToday} banked for good")
                state.yesterdayNote != null -> HeroSub(state.yesterdayNote, DayTone.Warning)
                state.newPhone -> HeroSub(NewPhone.line(state.target))
                else -> HeroSub(FREE_LINE)
            }
        },
        button = {
            when {
                done -> HeroPill(nextDaily(state.secondsLeftToday))
                state.bankedToday > 0 -> PrimaryButton("Keep going", onStart)
                else -> PrimaryButton("Start", onStart)
            }
        },
        footer = {
            HeroFooter(onClick = onOpenMe, onClickLabel = "Opens Me", description = "$foot. Opens Me.") {
                Text(foot, style = LabelStyle, color = HoldoutColors.TextSecondary, maxLines = 1)
            }
        },
    )
}

private const val FREE_LINE = "Free · knees allowed · in blocks of 10"

/** "Next Daily 25 in 4h 06m", the time in the mono face; "tomorrow" while chain time is unknown. */
private fun nextDaily(secondsLeft: Long?) = buildAnnotatedString {
    append("Next Daily 25 ")
    if (secondsLeft == null) {
        append("tomorrow")
    } else {
        append("in ")
        withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.TextPrimary)) { append(Format.countdown(secondsLeft)) }
    }
}

/** The offers, last: one compact card and a quiet row, "The next Holdout opens soon.", or nothing (FR-053). */
@Composable
private fun Offers(state: HomeState, onEvent: (NavEvent) -> Unit, onJoin: (cohort: String) -> Unit) {
    val apart = Modifier.padding(top = Spacing.betweenSections - Spacing.betweenItems)
    when (val block = state.holdouts) {
        HoldoutsBlock.Reading -> Reading { SectionLabel("Reading the chain…") }
        // The devnet bar says so; there is nothing read to offer.
        HoldoutsBlock.Unreachable -> Unit
        HoldoutsBlock.NotLive -> SectionLabel("Holdouts are not live on ${Deployment.networkName.lowercase()} yet")
        // A lobby place is held: nothing more to join, so nothing is drawn (the lobby row above says it all).
        HoldoutsBlock.Hidden -> Unit
        is HoldoutsBlock.Open -> {
            val first = block.cards.firstOrNull()
            if (first == null) {
                Text(TodayModel.OPENS_SOON, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary, modifier = apart.padding(horizontal = Spacing.cardPadding))
            } else {
                Column(apart, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    SectionLabel("Open to join")
                    if (!state.canJoin) Text(MyHoldoutsModel.SEEKER_ONLY, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary)
                    CompactOfferCard(first, onJoin = if (state.canJoin) ({ onJoin(first.cohort) }) else null)
                    if (block.cards.size > 1) QuietRow("${block.cards.size - 1} more open ›") { onEvent(NavEvent.SelectTab(Tab.Holdouts)) }
                }
            }
        }
    }
}

/**
 * One of the member's Holdouts that needs a tap: a pill row with a chevron, tinted by what it asks. The
 * sentence is the model's ([TodayModel.nudges]).
 */
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
internal fun ComebackCard(card: ComebackCardState, onClick: () -> Unit) {
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

/**
 * "18 of 25 from your toes", seven bars for the last week and the best day
 * (H35): how far from a Habit today's Daily 25 was. Measured at 45° only;
 * head-on it says how to see it instead of guessing. Not on Today any more
 * (FR-052a, 2026-10-13): Me draws it.
 */
@Composable
internal fun ToesMeterCard(meter: ToesMeter.Meter, target: Int) {
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
    nudges = listOf(NudgeRowState("You finished · claim 1,412 SKR", NavEvent.TourDone, NudgeTone.Success)),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomeFreshPreview() {
    HoldoutTheme { HomeScreen(FRESH, onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomePartwayPreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(remainingToday = 15, bankedToday = 10, streak = 3, freezes = 1), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomeDonePreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 13), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomeMilestonePreview() {
    HoldoutTheme {
        HomeScreen(FRESH.copy(remainingToday = 0, bankedToday = 25, completedToday = true, streak = 7, milestoneToday = true), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomeNewPhonePreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(newPhone = true), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomeUnreachablePreview() {
    HoldoutTheme { HomeScreen(FRESH.copy(secondsLeftToday = null, holdouts = HoldoutsBlock.Unreachable, nudges = emptyList()), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829)
@Composable
private fun HomeLobbyHeldPreview() {
    HoldoutTheme {
        HomeScreen(FRESH.copy(holdouts = HoldoutsBlock.Hidden, nudges = listOf(NudgeRowState("Spark · 7-day lobby · starts in 2d 0h", NavEvent.TourDone))), onStart = {}, onEvent = {}, onJoin = {}, onCheck = {})
    }
}
