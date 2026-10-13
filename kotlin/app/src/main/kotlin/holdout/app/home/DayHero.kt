package holdout.app.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * the slots hold, so the rows under it never move.
 *
 * ```
 *  ┌──────────────────────────────────────────────┐ ─┐
 *  │ THE DAILY 25                    14h 02m left │  │ header: [label], [countdown]
 *  │                    25                        │  │ [number] in CounterStyle, [unit] under it
 *  │                 PUSH-UPS                     │  │
 *  │ ▬▬▬▬▬▬▬▬ ▬▬▬▬▬▬▬▬ ▬▬▬▬                       │  │ [bars]: BankBars 10 / 10 / 5
 *  │ Counts as your Daily 25 too.                 │  │ [sub]: a 40 dp line
 *  │ ( Start )                                    │  │ [button]: the day's one primary button
 *  ├──────────────────────────────────────────────┤  │ hairline
 *  │ 12-day streak ›                              │  │ [footer]: T1 the link to Me, T2 the crowd strip
 *  └──────────────────────────────────────────────┘ ─┘ 312 dp, testTag "day-hero"
 * ```
 *
 * [countdown] is the header's right-hand slot ([HeroCountdown]: accent, or warning in the last 10 minutes
 * — always beside its own words, never alone), or null when there is no deadline to show. [footer] fills
 * the bottom strip edge to edge; [HeroFooter] draws the tappable one both screens use.
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
) {
    HoldoutCard(modifier = modifier.height(HERO_HEIGHT).testTag(DAY_HERO_TAG), padding = PaddingValues()) {
        // One child, so the card's own gap between children does not widen the frame's three parts.
        Column(Modifier.fillMaxSize()) {
            HeroBody(label, countdown, number, unit, bars, sub, button, numberColor, Modifier.weight(1f))
            HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
            Box(modifier = Modifier.fillMaxWidth().height(Spacing.touchTarget), contentAlignment = Alignment.CenterStart) { footer() }
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.betweenItems),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
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
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, style = CounterStyle, color = numberColor, maxLines = 1)
            Text(unit.uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary, maxLines = 1)
        }
        bars()
        Box(Modifier.fillMaxWidth().height(SUB_HEIGHT), contentAlignment = Alignment.Center) { sub() }
        button()
    }
}

/**
 * The header's "4h 06m left": the time in the mono face, accent — warning inside the last 10 minutes, where
 * its accessible name says so in words ([CountdownText]) — and "left" beside it.
 */
@Composable
fun HeroCountdown(seconds: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.base), verticalAlignment = Alignment.Bottom) {
        CountdownText(seconds, style = LabelStyle.copy(fontFamily = HoldoutFonts.Mono))
        Text("left", style = LabelStyle, color = HoldoutColors.TextSecondary)
    }
}

/** The hero's sub line: one or two short lines, centred, in the secondary colour unless the state asks for another (its words carry the state). */
@Composable
fun HeroSub(text: String, tone: DayTone = DayTone.Plain) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (tone == DayTone.Plain) HoldoutColors.TextSecondary else tone.color(),
        textAlign = TextAlign.Center,
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
            .height(Spacing.primaryButtonHeight)
            .background(HoldoutColors.Surface3, MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, maxLines = 1)
    }
}

@Composable
fun HeroPill(text: String) = HeroPill(AnnotatedString(text))

/**
 * The hero's footer as one tappable strip with a chevron: the frame's whole bottom row, [Spacing.touchTarget]
 * high. [description] is its accessible name when its words alone do not say where it goes.
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Box(Modifier.weight(1f)) { content() }
        Text("›", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextTertiary)
    }
}

/** `testTag` of the hero's frame: the fit tests measure it. */
const val DAY_HERO_TAG = "day-hero"

/** The frame's height in every state (owner, 13 Oct, the v2 canvas). */
val HERO_HEIGHT = 312.dp

/** The sub line's slot: one or two short lines, centred. */
private val SUB_HEIGHT = 40.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DayHeroPreview() {
    HoldoutTheme {
        Column(Modifier.padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections)) {
            DayHero(
                label = "THE DAILY 25",
                countdown = { HeroCountdown(50_520) },
                number = "10",
                unit = "of 25 push-ups",
                bars = { BankBars(target = 25, banked = 10) },
                sub = { HeroSub("10 banked for good") },
                button = { PrimaryButton("Keep going", onClick = {}) },
                footer = {
                    HeroFooter(onClick = {}, onClickLabel = "Open Me") {
                        Text("2 more days to a streak freeze", style = LabelStyle, color = HoldoutColors.TextSecondary)
                    }
                },
            )
        }
    }
}
