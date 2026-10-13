package holdout.app.home

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
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
 * [countdownColor] is `accent`, or `warning` in a Holdout day's last 10 minutes — always beside the
 * countdown's own words, never alone.
 */
@Composable
fun DayHero(
    label: String,
    countdown: String?,
    number: String,
    unit: String,
    bars: @Composable () -> Unit,
    sub: @Composable () -> Unit,
    button: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    countdownColor: Color = HoldoutColors.Accent,
) {
    HoldoutCard(modifier = modifier.height(HERO_HEIGHT).testTag(DAY_HERO_TAG), padding = PaddingValues()) {
        // One child, so the card's own gap between children does not widen the frame's three parts.
        Column(Modifier.fillMaxSize()) {
            HeroBody(label, countdown, number, unit, bars, sub, button, countdownColor, Modifier.weight(1f))
            HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
            Box(
                modifier = Modifier.fillMaxWidth().height(Spacing.touchTarget).padding(horizontal = Spacing.cardPadding),
                contentAlignment = Alignment.CenterStart,
            ) { footer() }
        }
    }
}

@Composable
private fun HeroBody(
    label: String,
    countdown: String?,
    number: String,
    unit: String,
    bars: @Composable () -> Unit,
    sub: @Composable () -> Unit,
    button: @Composable () -> Unit,
    countdownColor: Color,
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
            if (countdown != null) Text(text = countdown, style = LabelStyle, color = countdownColor, maxLines = 1)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, style = CounterStyle, color = HoldoutColors.TextPrimary, maxLines = 1)
            Text(unit.uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary, maxLines = 1)
        }
        bars()
        Box(Modifier.fillMaxWidth().height(SUB_HEIGHT), contentAlignment = Alignment.Center) { sub() }
        button()
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
                countdown = "14h 02m left",
                number = "10",
                unit = "of 25 push-ups",
                bars = { BankBars(target = 25, banked = 10) },
                sub = { Text("10 banked for good", style = LabelStyle, color = HoldoutColors.TextSecondary) },
                button = { PrimaryButton("Keep going", onClick = {}) },
                footer = { Text("12-day streak ›", style = LabelStyle, color = HoldoutColors.TextSecondary) },
            )
        }
    }
}
