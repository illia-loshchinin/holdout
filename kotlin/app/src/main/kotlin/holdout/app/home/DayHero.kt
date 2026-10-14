package holdout.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import holdout.app.ui.Chevron
import holdout.app.ui.CountdownText
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * Today's hero (001 FR-069a, research R25): one frame of fixed height, shared by the Daily 25 (T1) and a
 * Holdout's day (T2). **A state changes its content, never its size** — the frame is [HERO_HEIGHT] whatever
 * the slots hold, so the rows under it never move. Sizes and gaps are the v2 canvas boards' (400 × 829 dp).
 *
 * ```
 *  ┌──────────────────────────────────────────────┐ ─┐  18 dp top, 20 dp sides
 *  │ THE DAILY 25                    14h 02m left │  │ header, 24 dp: [label], [countdown]
 *  │                                              │  │ 20 dp
 *  │ 25 push-ups                                  │  │ 48 dp: [number] 48 sp light, [unit] 16 sp on its baseline
 *  │ ▬▬▬▬▬▬▬▬ ▬▬▬▬▬▬▬▬ ▬▬▬▬                       │  │ 14 dp above and below: [bars], 6 dp
 *  │ Counts as your Daily 25 too.                 │  │ [sub]: a 40 dp slot, left
 *  │ ( Start )                                    │  │ [button]: 52 dp, 16 dp above the footer
 *  ├──────────────────────────────────────────────┤  │ hairline
 *  │ 12-day streak ›                              │  │ [footer]: 52 dp (T1, the link to Me) or 60 dp (T2, the crowd)
 *  └──────────────────────────────────────────────┘ ─┘  312 dp, testTag "day-hero"
 * ```
 *
 * [countdown] is the header's right-hand slot ([HeroCountdown]: accent, or warning in the last 10 minutes
 * — always beside its own words, never alone), or null when there is no deadline to show. [footer] fills
 * the bottom strip edge to edge, [footerHeight] high; [HeroFooter] draws the tappable one both screens use.
 */
@Composable
fun DayHero(
    label: String,
    countdown: (@Composable () -> Unit)?,
    number: String,
    unit: String,
    bars: @Composable () -> Unit,
    sub: @Composable () -> Unit,
    button: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    numberColor: Color = HoldoutColors.TextPrimary,
    footerHeight: Dp = HERO_FOOTER_HEIGHT,
) {
    HoldoutCard(modifier = modifier.height(HERO_HEIGHT).testTag(DAY_HERO_TAG), padding = PaddingValues()) {
        // One child, so the card's own gap between children does not widen the frame's three parts.
        Column(Modifier.fillMaxSize()) {
            HeroBody(label, countdown, number, unit, bars, sub, button, numberColor, Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().height(footerHeight)) {
                HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) { footer() }
            }
        }
    }
}

@Composable
private fun HeroBody(
    label: String,
    countdown: (@Composable () -> Unit)?,
    number: String,
    unit: String,
    bars: @Composable () -> Unit,
    sub: @Composable () -> Unit,
    button: @Composable () -> Unit,
    numberColor: Color,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = Spacing.cardPadding, end = Spacing.cardPadding, top = HERO_TOP)) {
        Row(Modifier.fillMaxWidth().height(HEADER_ROW), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Text(
                text = label,
                style = LabelStyle,
                color = HoldoutColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            countdown?.invoke()
        }
        Spacer(Modifier.height(NUMBER_ABOVE))
        // The number and its unit, left, the unit on the number's baseline.
        val unitDrop = with(LocalDensity.current) { UNIT_DROP.roundToPx() }
        Row(Modifier.fillMaxWidth().height(NUMBER_ROW).offset(y = NUMBER_LIFT), horizontalArrangement = Arrangement.spacedBy(NUMBER_GAP), verticalAlignment = Alignment.Bottom) {
            Text(number, style = HeroNumberStyle, color = numberColor, maxLines = 1, modifier = Modifier.alignByBaseline())
            Text(unit, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp), color = HoldoutColors.TextSecondary, maxLines = 1, modifier = Modifier.alignBy { it[FirstBaseline] - unitDrop })
        }
        Spacer(Modifier.height(BARS_GAP))
        bars()
        Spacer(Modifier.height(BARS_GAP))
        Box(Modifier.fillMaxWidth().height(SUB_HEIGHT).offset(y = SUB_LIFT), contentAlignment = Alignment.TopStart) { sub() }
        Spacer(Modifier.weight(1f))
        button()
        Spacer(Modifier.height(BUTTON_ABOVE_FOOTER))
    }
}

/** The board's hero number: 48 sp, light, tight tracking, left (the counter's 56 sp is for the sitting). */
private val HeroNumberStyle = CounterStyle.copy(fontFamily = HoldoutFonts.FlexHero, fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em, textAlign = TextAlign.Start)

/**
 * The header's "4h 06m left": the time in the mono face, regular weight, accent — warning inside the last 10
 * minutes, where its accessible name says so in words ([CountdownText]) — and "left" in the secondary colour beside it.
 */
@Composable
fun HeroCountdown(seconds: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(HERO_LEFT_GAP), verticalAlignment = Alignment.Bottom) {
        CountdownText(seconds, style = HeroSmall.copy(fontFamily = HoldoutFonts.Mono))
        Text("left", style = HeroSmall, color = HoldoutColors.TextSecondary)
    }
}

/** The hero's small print: 13 sp regular, as the boards set the countdown and the footer. */
internal val HeroSmall = LabelStyle.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)

/** The hero's sub line: one or two short lines, left, in the secondary colour unless the state asks for another (its words carry the state). */
@Composable
fun HeroSub(text: String, tone: DayTone = DayTone.Plain) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (tone == DayTone.Plain) HoldoutColors.TextSecondary else tone.color(),
        textAlign = TextAlign.Start,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** What stands where the button was once there is nothing to press: a tonal pill at the button's height, not clickable. */
@Composable
fun HeroPill(text: AnnotatedString) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_PILL_HEIGHT)
            .background(HoldoutColors.Surface3, MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, maxLines = 1)
    }
}

@Composable
fun HeroPill(text: String) = HeroPill(AnnotatedString(text))

/** The hero's primary button: the board's 52 dp pill. */
@Composable
fun HeroButton(text: String, onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false) = PrimaryButton(text, onClick, enabled = enabled, busy = busy, compact = true)

/**
 * The hero's footer as one tappable strip with a drawn chevron: the frame's whole bottom row.
 * [description] is its accessible name when its words alone do not say where it goes.
 */
@Composable
fun HeroFooter(onClick: () -> Unit, onClickLabel: String, modifier: Modifier = Modifier, description: String? = null, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_FOOTER_GAP),
    ) {
        Box(Modifier.weight(1f)) { content() }
        Chevron()
    }
}

/** `testTag` of the hero's frame: the fit tests measure it. */
const val DAY_HERO_TAG = "day-hero"

/** The frame's height in every state (owner, 13 Oct, the v2 canvas). */
val HERO_HEIGHT = 312.dp

/** The footer strip's height: 52 dp on the Daily 25 (one line), [HERO_FOOTER_TALL] on a Holdout's day (two lines). */
val HERO_FOOTER_HEIGHT = 52.dp
val HERO_FOOTER_TALL = 60.dp

/** Between the items of a Today screen: 16 dp on the boards ([Spacing.betweenItems] is 12), and [TODAY_APART] more above a group set a little apart. */
internal val TODAY_GAP = 16.dp
internal val TODAY_APART = 8.dp

private val HERO_TOP = 18.dp
private val HEADER_ROW = 24.dp
private val NUMBER_ABOVE = 20.dp
private val NUMBER_ROW = 48.dp

/** The number sits this much higher than Compose sets a 48 sp line: the boards trim the line to the text. */
private val NUMBER_LIFT = (-8).dp

/** The unit sits 2.5 dp lower and 3 dp nearer the number than a bare baseline alignment draws it. */
private val UNIT_DROP = 2.2.dp
private val NUMBER_GAP = 5.dp
private val BARS_GAP = 14.dp
private val BUTTON_ABOVE_FOOTER = 16.dp
private val HERO_PILL_HEIGHT = 52.dp
private val HERO_LEFT_GAP = 4.dp
private val HERO_FOOTER_GAP = 10.dp

/** The sub line's slot: one or two short lines, left. */
private val SUB_HEIGHT = 40.dp
private val SUB_LIFT = (-2).dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DayHeroPreview() {
    HoldoutTheme {
        Column(Modifier.padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections)) {
            DayHero(
                label = "THE DAILY 25",
                countdown = { HeroCountdown(50_520) },
                number = "15",
                unit = "to go",
                bars = { BankBars(target = 25, banked = 10) },
                sub = { HeroSub("10 banked for good") },
                button = { HeroButton("Keep going", onClick = {}) },
                footer = {
                    HeroFooter(onClick = {}, onClickLabel = "Open Me") {
                        Text("2 more days to a streak freeze", style = HeroSmall, color = HoldoutColors.TextSecondary)
                    }
                },
            )
        }
    }
}
