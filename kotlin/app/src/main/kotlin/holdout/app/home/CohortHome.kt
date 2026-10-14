package holdout.app.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.ui.ChainCheckBox
import holdout.app.ui.DevnetBar
import holdout.app.ui.Format
import holdout.app.ui.Notice
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * Today, with a Holdout running (screen map T2, FR-038, FR-046, FR-047, FR-069a, FR-069h, T073, T067):
 * **the day is the screen** — what to do now and how long there is to do it, in the same 312 dp
 * [DayHero] as the Daily 25 (a Holdout's day also counts as the Daily 25, FR-050). The crowd shrinks to
 * the hero's footer strip, which opens the Holdout's screen above Today, where the counter, the thinning,
 * the prize pool, the share and the boost live (FR-069b). The strip's figures come from `Derive` over
 * chain accounts and chain time, so they move at the deadline itself, not when a crank gets round to
 * recording it (FR-038a).
 *
 * ```
 *  TODAY  [DEVNET COHORT badge]                           🔥 12     header · the streak chip, a button to Me
 *  ( ● Devnet isn't answering              Retry )                  DevnetBar, only while devnet is silent
 *  ┌ HABIT · 21-DAY · DAY 12 OF 21          4h 06m left ┐   DayHero, 312 dp in every state
 *  │                    25                              │
 *  │                  PUSH-UPS                          │
 *  │ ▬▬▬▬▬▬▬▬ ▬▬▬▬▬▬▬▬ ▬▬▬▬▬                            │   BankBars 10 / 10 / 5
 *  │ Counts as your Daily 25 too.                       │
 *  │ ( Start )                                          │
 *  ├────────────────────────────────────────────────────┤
 *  │ 189 of 312 still standing · your share 255 SKR   ›  │   the crowd strip → the Holdout's screen
 *  │ ▲ 3 dropped since yesterday                         │
 *  └────────────────────────────────────────────────────┘
 *  ( Claim 1,412 SKR      Spark · 7-day  › )                            the rows group, a little apart
 *    2 open to join · from 9,000 SKR ›                              one quiet offers row → Holdouts
 * ```
 *
 * The day itself — its number, words, button and deadline — is [DayState], the same model the Holdout's
 * "your day" strip draws. The day button is *Start*, *Keep going* or *Submit day N*; a completed day
 * waiting to reach the chain reads *Done — waiting for network* (not pressable) while it cannot, is sent
 * by itself once it can, and shows *Submit day N* only if that send did not land (FR-024b, T098). Once
 * the day is on the chain the deadline countdown goes: the next day's opening (or, on the last day, that
 * the end settles) takes its place. Inside the last 10 minutes the number, the bars and the line turn
 * warning and say unfinished reps won't count — never a refusal to start (FR-047). While devnet can't be
 * reached the devnet bar sits under the header and a pull down checks too (spec 005, FR-069h).
 */
@Composable
fun CohortHome(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    stage: Stage.Active,
    now: Long,
    /** Reps banked on this phone toward today's day. */
    banked: Int,
    /** The last chain read failed: what is shown is the previous one, and nothing can be submitted until it answers. */
    offline: Boolean,
    /** A completed day waiting to be submitted, or null. */
    pendingDay: Int?,
    /** The pending day was already sent by itself since the chain last answered: the tap is the way left. */
    autoSendTried: Boolean,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onStart: () -> Unit,
    onSubmit: (day: Int) -> Unit,
    /** The crowd strip opens the Holdout's screen (`Open(Screen.Detail)`), the streak chip and the offers row switch tabs. */
    onEvent: (NavEvent) -> Unit,
    /** The faucet: a member whose SOL has run out cannot pay to submit a day. */
    onGetTokens: () -> Unit,
    modifier: Modifier = Modifier,
    /** The new-phone signal (`NewPhone.shows`): this phone first saw the wallet today and has nothing banked, so say so before the day starts (ES-3a). */
    newPhone: Boolean = false,
    /** The daily streak, for the chip. */
    streak: Int = 0,
    /** The wallet holds enough SOL for the submit's fee (ES-0a). */
    canPayFees: Boolean = true,
    /** A send of today's day may still land: Submit stays shut, and the day says so (FR-070a). */
    mayStillLand: Boolean = false,
    /** A member's "Retry" is out (spec 005). */
    checking: Boolean = false,
    /** The last "Retry" found the chain still silent. */
    stillUnreachable: Boolean = false,
    /** Reads the chain now; offered only while [offline]. */
    onCheck: () -> Unit = {},
    /** The member's other Holdouts — a lobby joined for later, a claim waiting — one row each. */
    nudges: List<NudgeRowState> = emptyList(),
    /** The way back after a lost stake, one tap (FR-037g). */
    comeback: ComebackCardState? = null,
    /** The offers the running Holdout allows, as one quiet row; null when there are none or a lobby place is held. */
    offers: OffersRow? = null,
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now, boostReserve = h.league?.boostReserve) }
    val since = if (figures != null && members != null) Derive.sinceYesterday(figures, members) else null
    val d = DayState.of(c, stage, now, banked, pendingDay, autoSendTried, offline, busy, canPayFees, mayStillLand)
    val me = h.membership
    val crowd = CrowdStrip(
        standing = figures?.survivors?.standing ?: c.memberCount.toInt(),
        total = c.memberCount,
        share = if (figures != null && me != null) figures.projectedShare(me) else null,
        dropped = since?.dropped ?: 0,
    )
    val apart = Modifier.padding(top = TODAY_APART)

    ChainCheckBox(offline, checking, onCheck, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HoldoutColors.Canvas)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(TODAY_GAP),
        ) {
            Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    Text("TODAY", style = LabelStyle, color = HoldoutColors.TextTertiary)
                    if (h.isTest) TestCohortBadge(c.dayLength)
                }
                StreakFlame(streak, if (d.phase == DayPhase.OnChain) StreakState.ExtendedToday else StreakState.Active, chip = true, onClick = { onEvent(NavEvent.SelectTab(Tab.Me)) })
            }
            // Said once, under the header, whatever the day is doing: a sitting can still be done, and the button over the line says what happens to a finished day.
            if (offline) DevnetBar(Deployment.networkName, stillUnreachable, checking, DEVNET_SUB, onCheck)

            DayHero(
                label = Format.headerLabel(c, d.day, h.isTest),
                countdown = d.timeLeft?.let { seconds -> { HeroCountdown(seconds) } },
                number = d.number,
                unit = d.unit,
                numberColor = d.numberTone.color(),
                bars = {
                    BankBars(
                        target = d.target,
                        banked = if (d.phase == DayPhase.ToDo) d.banked else d.target,
                        fill = when {
                            d.warning -> HoldoutColors.Warning
                            d.phase == DayPhase.ToDo -> HoldoutColors.Accent
                            else -> HoldoutColors.Success
                        },
                    )
                },
                // The new-phone line takes the sub line before the first rep (FR-069f).
                sub = { HeroSub(if (newPhone && d.phase == DayPhase.ToDo && d.banked == 0) NewPhone.line(d.target) else d.sub, d.subTone) },
                button = { DayButtonSlot(d, offline, onStart, onSubmit, onGetTokens) },
                footerHeight = HERO_FOOTER_TALL,
                footer = {
                    HeroFooter(
                        onClick = { onEvent(NavEvent.Open(Screen.Detail(h.address.base58()))) },
                        onClickLabel = "Who is still standing",
                        description = crowd.spoken,
                    ) { Crowd(crowd) }
                },
            )
            // "Day N is on chain." repeats the done card's title: only a warning gets a line of its own.
            Notice(message.takeIf { messageIsWarning }, messageIsWarning)

            // The member's other Holdouts that need a tap: their own group, a little apart from the hero.
            if (nudges.isNotEmpty() || comeback != null) {
                Column(apart, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    nudges.forEach { NudgeRow(it) { onEvent(it.go) } }
                    comeback?.let { ComebackCard(it) { onEvent(it.go) } }
                }
            }
            // The offers a running Holdout allows are one quiet row; the cards are on Holdouts.
            offers?.let { QuietRow(it.line, Modifier, mono = it.from) { onEvent(NavEvent.SelectTab(Tab.Holdouts)) } }
        }
    }
}

/** The devnet bar's line for Today in a Holdout: the reps are counted on the phone whatever devnet does. */
private const val DEVNET_SUB = "Your reps still count on this phone."

private val HEADER_HEIGHT = 32.dp

/**
 * The hero's one button, whatever the day is doing: the day's own button ([DayState.button]); *Get test
 * tokens* when a finished day cannot pay its fee; the disabled *Done — waiting for network* while it
 * cannot be sent; and, once the day is on chain, the tonal pill with when the next day opens.
 */
@Composable
private fun DayButtonSlot(d: DayState, offline: Boolean, onStart: () -> Unit, onSubmit: (dayIndex: Int) -> Unit, onGetTokens: () -> Unit) {
    val button = d.button
    when {
        d.phase == DayPhase.OnChain -> d.pill?.let { HeroPill(it) }
        d.phase == DayPhase.ShortOfSol -> HeroButton("Get test tokens", onGetTokens)
        d.phase == DayPhase.Unsent && offline && button == null -> HeroButton(d.title, onClick = {}, enabled = false)
        button != null -> HeroButton(
            button.label,
            {
                when (button.action) {
                    DayAction.Start -> onStart()
                    DayAction.Submit -> onSubmit(d.dayIndex)
                    DayAction.None -> Unit
                }
            },
            enabled = button.enabled,
            busy = button.busy,
        )
    }
}

/**
 * What the crowd strip says: [standing] of [total], the member's [share] if they stand in it and there is
 * one to show (nothing has dropped yet, so nothing to share: no "your share 0 SKR"), and who [dropped] since yesterday.
 */
private data class CrowdStrip(val standing: Int, val total: Long, val share: ULong?, val dropped: Int) {
    val line: String get() = "$standing of $total still standing" + (share?.takeIf { it > 0uL }?.let { " · your share ${Format.skr(it)}" } ?: "")

    /** [line] as the boards set it: the counts and the share in white, the share in the mono face, the words between in the secondary colour. */
    val styled: AnnotatedString get() = buildAnnotatedString {
        withStyle(SpanStyle(color = HoldoutColors.TextPrimary)) { append("$standing of $total") }
        append(" still standing")
        share?.takeIf { it > 0uL }?.let {
            append(" · your share ")
            withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.TextPrimary)) { append(Format.skr(it)) }
        }
    }

    /** The strip's accessible name: the line, who dropped, and where it goes. */
    val spoken: String get() = line + (if (dropped > 0) ", $dropped dropped since yesterday" else "") + ". Opens the Holdout."
}

/**
 * The crowd, shrunk to the hero's footer (Decision 1): the line and, under it, `▲ n dropped since yesterday`
 * when anyone did. The strip opens the Holdout's screen, where the counter, the thinning, the pool and the
 * members live.
 */
@Composable
private fun Crowd(crowd: CrowdStrip) {
    Column(verticalArrangement = Arrangement.spacedBy(CROWD_GAP)) {
        Text(crowd.styled, style = HeroSmall, color = HoldoutColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (crowd.dropped > 0) Dropped(crowd.dropped)
    }
}

/** `▲ 3 dropped since yesterday`: 12 sp in the tertiary colour, the triangle with it (the boards draw it as a glyph in the same grey). */
@Composable
private fun Dropped(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.base)) {
        Canvas(Modifier.size(TRIANGLE)) {
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height * TRIANGLE_DROP)
                lineTo(0f, size.height * TRIANGLE_DROP)
                close()
            }
            drawPath(path, HoldoutColors.TextTertiary)
        }
        Text("$count dropped since yesterday", style = HeroSmall.copy(fontSize = 12.sp, lineHeight = 14.sp), color = HoldoutColors.TextTertiary)
    }
}

private val CROWD_GAP = 6.dp

private const val TRIANGLE_DROP = 0.8f
private val TRIANGLE = 8.dp
