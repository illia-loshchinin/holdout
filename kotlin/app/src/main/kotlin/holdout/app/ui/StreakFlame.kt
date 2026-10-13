package holdout.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/** What a streak chip that is a button says it does, at the end of its accessible name (001 FR-069a). */
private const val OPENS_ME = "Opens your streak on Me"

/** The five states of the daily streak flame (design book, Streak flame). */
enum class StreakState {
    /** A white flame, the number in `text-primary`. */
    Active,

    /** Today's day is done: the flame and the number turn `success`. */
    ExtendedToday,

    /** A cracked, outlined flame and the number in `text-tertiary`; no save button, no grace day. */
    Broken,

    /** The flame in `celebration` with a **PB** pill. */
    PersonalBest,

    /**
     * A milestone day (7, 14, 21 … `StreakMilestones`): a larger white flame in an
     * `accent` ring that pulses once; no pulse when the phone's animations are off.
     */
    Milestone,
}

/**
 * The daily streak: days in a row with a completed day. Free, personal and
 * cosmetic: never on a leaderboard, never paid against, and never conflated
 * with the Holdout streak, which lives on chain. The flame is drawn from the
 * design system's own path (an icon, not an emoji); the number is a `display`.
 *
 * The accessible name carries the state, since the flame's colour is never
 * the only signal: "12-day streak", "13-day streak, extended today",
 * "Streak broken", "31-day streak, personal best", "7-day streak, milestone".
 * With [onClick] (Today's chip, which opens Me) the chip is a button and its name ends
 * "Opens your streak on Me."; the chip's touch target is lifted to 48 dp by Compose.
 *
 * [chip] draws it small, in a pill on `surface-3` (the Today header, board 5):
 * a 16 dp flame and the number at label size. As a milestone the flame is
 * larger and the pill has an `accent` border.
 */
@Composable
fun StreakFlame(days: Int, state: StreakState, modifier: Modifier = Modifier, chip: Boolean = false, onClick: (() -> Unit)? = null) {
    val spoken = when (state) {
        StreakState.Active -> "$days-day streak"
        StreakState.ExtendedToday -> "$days-day streak, extended today"
        StreakState.Broken -> "Streak broken"
        StreakState.PersonalBest -> "$days-day streak, personal best"
        StreakState.Milestone -> "$days-day streak, milestone"
    }
    val milestone = state == StreakState.Milestone
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClickLabel = OPENS_ME, role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = if (onClick != null) "$spoken. $OPENS_ME." else spoken }
            .then(
                if (chip) {
                    Modifier
                        .background(HoldoutColors.Surface3, MaterialTheme.shapes.extraLarge)
                        .then(if (milestone) Modifier.border(Spacing.border, HoldoutColors.Accent, MaterialTheme.shapes.extraLarge) else Modifier)
                        .padding(horizontal = Spacing.betweenItems, vertical = Spacing.tight)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        if (milestone) {
            MilestoneFlame(chip)
        } else {
            Flame(state, if (chip) CHIP_FLAME_SIZE else FLAME_SIZE)
        }
        Text(
            text = "$days",
            style = if (chip) AmountStyle.copy(fontSize = LabelStyle.fontSize) else DisplayStyle,
            color = when (state) {
                StreakState.ExtendedToday -> HoldoutColors.Success
                StreakState.Broken -> HoldoutColors.TextTertiary
                else -> HoldoutColors.TextPrimary
            },
        )
        if (state == StreakState.PersonalBest) {
            Text(
                text = "PB",
                style = LabelStyle,
                color = HoldoutColors.OnLight,
                modifier = Modifier
                    .background(HoldoutColors.TextPrimary, MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = Spacing.tight),
            )
        }
    }
}

/**
 * The milestone flame: larger, in an `accent` ring (the chip has its ring on the
 * pill instead), and one pulse when it first shows — none when the phone's
 * animations are off, as for the loaders (`ValueAnimator.areAnimatorsEnabled`).
 */
@Composable
internal fun MilestoneFlame(chip: Boolean, pulse: Animatable<Float, AnimationVector1D> = remember { Animatable(1f) }) {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(Unit) {
        if (animate) {
            pulse.animateTo(PULSE_SCALE, tween(PULSE_MILLIS))
            pulse.animateTo(1f, tween(PULSE_MILLIS))
        }
    }
    val pulsing = Modifier.graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
    }
    if (chip) {
        Box(pulsing) { Flame(StreakState.Milestone, MILESTONE_CHIP_FLAME_SIZE) }
    } else {
        Box(
            modifier = pulsing
                .size(MILESTONE_RING_SIZE)
                .border(Spacing.ringStroke, HoldoutColors.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Flame(StreakState.Milestone, MILESTONE_FLAME_SIZE)
        }
    }
}

@Composable
private fun Flame(state: StreakState, flameSize: Dp) {
    val outline = remember { PathParser().parsePathString(FLAME).toPath() }
    val crack = remember { PathParser().parsePathString(CRACK).toPath() }
    val fill: Color? = when (state) {
        StreakState.Active, StreakState.Milestone -> HoldoutColors.TextPrimary
        StreakState.ExtendedToday -> HoldoutColors.Success
        StreakState.PersonalBest -> HoldoutColors.Celebration
        StreakState.Broken -> null
    }
    Canvas(Modifier.size(flameSize)) {
        scale(scale = size.minDimension / VIEWBOX, pivot = Offset.Zero) {
            if (fill != null) {
                drawPath(outline, fill)
            } else {
                val stroke = Stroke(width = BROKEN_STROKE, cap = StrokeCap.Round)
                drawPath(outline, HoldoutColors.TextTertiary, style = stroke)
                drawPath(crack, HoldoutColors.TextTertiary, style = stroke)
            }
        }
    }
}

private val FLAME_SIZE = 36.dp
private val CHIP_FLAME_SIZE = 16.dp
private val MILESTONE_FLAME_SIZE = 44.dp
private val MILESTONE_RING_SIZE = 72.dp
private val MILESTONE_CHIP_FLAME_SIZE = 24.dp
private const val PULSE_SCALE = 1.15f
private const val PULSE_MILLIS = 300
private const val VIEWBOX = 24f
private const val BROKEN_STROKE = 1.5f

/** The design system's flame (`components/StreakFlame/preview.html`), 24 × 24. */
private const val FLAME = "M12 2.5c.6 3 4.9 5.6 4.9 10.9a4.9 4.9 0 0 1-9.8 0c0-2.4 1.1-4 2.4-5.4.2 1.6.9 2.7 2.2 3.2-.4-3-.6-5.8.3-8.7z"

/** The crack drawn across a broken flame. */
private const val CRACK = "M12.6 9.5l-1.6 3 2 1.6-1.4 3.6"

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StreakFlamePreview() {
    HoldoutTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenSections)) {
            StreakFlame(12, StreakState.Active)
            StreakFlame(13, StreakState.ExtendedToday)
            StreakFlame(0, StreakState.Broken)
            StreakFlame(31, StreakState.PersonalBest)
            StreakFlame(30, StreakState.Milestone)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StreakFlameChipPreview() {
    HoldoutTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            StreakFlame(12, StreakState.Active, chip = true)
            StreakFlame(14, StreakState.Milestone, chip = true)
        }
    }
}
