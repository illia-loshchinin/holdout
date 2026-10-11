package holdout.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/** The four states of the daily streak flame (design book, Streak flame). */
enum class StreakState {
    /** A white flame, the number in `text-primary`. */
    Active,

    /** Today's day is done: the flame and the number turn `success`. */
    ExtendedToday,

    /** A cracked, outlined flame and the number in `text-tertiary`; no save button, no grace day. */
    Broken,

    /** The flame in `celebration` with a **PB** pill. */
    PersonalBest,
}

/**
 * The daily streak: days in a row with a completed day. Free, personal and
 * cosmetic: never on a leaderboard, never paid against, and never conflated
 * with the Holdout streak, which lives on chain. The flame is drawn from the
 * design system's own path (an icon, not an emoji); the number is a `display`.
 *
 * The accessible name carries the state, since the flame's colour is never
 * the only signal: "12-day streak", "13-day streak, extended today",
 * "Streak broken", "31-day streak, personal best".
 */
@Composable
fun StreakFlame(days: Int, state: StreakState, modifier: Modifier = Modifier) {
    val spoken = when (state) {
        StreakState.Active -> "$days-day streak"
        StreakState.ExtendedToday -> "$days-day streak, extended today"
        StreakState.Broken -> "Streak broken"
        StreakState.PersonalBest -> "$days-day streak, personal best"
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Flame(state)
        Text(
            text = "$days",
            style = DisplayStyle,
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

@Composable
private fun Flame(state: StreakState) {
    val outline = remember { PathParser().parsePathString(FLAME).toPath() }
    val crack = remember { PathParser().parsePathString(CRACK).toPath() }
    val fill: Color? = when (state) {
        StreakState.Active -> HoldoutColors.TextPrimary
        StreakState.ExtendedToday -> HoldoutColors.Success
        StreakState.PersonalBest -> HoldoutColors.Celebration
        StreakState.Broken -> null
    }
    Canvas(Modifier.size(FLAME_SIZE)) {
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
        }
    }
}
