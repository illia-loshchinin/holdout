package holdout.app.sitting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import holdout.app.ui.bankBlocks
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing

/**
 * How full each bar under the count is, 0..1: one bar per block of the day,
 * proportional to its reps (25 → 10 / 10 / 5, ruling C-D7 and the design book's
 * Bank ring). The reps done so far — banked and in this set — fill the blocks
 * in order, so seven loose reps on ten banked read as a full bar, then seven
 * tenths of the next. Never above 1.
 */
internal fun blockFills(target: Int, banked: Int, loose: Int): List<Float> {
    val done = (banked + loose).coerceAtLeast(0)
    var start = 0
    return bankBlocks(target).map { size ->
        val fill = ((done - start).toFloat() / size).coerceIn(0f, 1f)
        start += size
        fill
    }
}

/** What a bar on the Stopped screen holds. */
internal enum class StoppedBar {
    /** A full block that banked: kept. */
    Banked,

    /** Reps of this sitting that were not in a full block: gone. */
    NotKept,

    /** Nothing yet. */
    Empty,
}

/** One bar per block: banked ones first, then the block the not-kept reps were in, then empty ones. */
internal fun stoppedBars(target: Int, banked: Int, notKept: Int): List<StoppedBar> {
    var start = 0
    return bankBlocks(target).map { size ->
        val bar = when {
            start + size <= banked -> StoppedBar.Banked
            start < banked + notKept -> StoppedBar.NotKept
            else -> StoppedBar.Empty
        }
        start += size
        bar
    }
}

/**
 * The bars of a counting sitting (board 6): `accent` fill on a `hairline`
 * track, each as wide as its block's share of the day. Named for how many
 * blocks are banked and how far the day is.
 */
@Composable
internal fun CountingBars(target: Int, banked: Int, loose: Int, modifier: Modifier = Modifier) {
    val fills = blockFills(target, banked, loose)
    val blocks = bankBlocks(target)
    val bankedBlocks = (banked / BLOCK_REPS).coerceAtMost(blocks.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$bankedBlocks of ${blocks.size} blocks banked, ${banked + loose} of $target reps" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        blocks.forEachIndexed { i, size ->
            Box(
                Modifier
                    .weight(size.toFloat())
                    .height(CountingBarHeight)
                    .background(HoldoutColors.Hairline, CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fills[i])
                        .background(HoldoutColors.Accent, CircleShape),
                )
            }
        }
    }
}

/**
 * The bars of the Stopped screen (board 20): banked blocks in `success`, the
 * block of reps that were not kept as a dashed `warning` outline, the rest on
 * the `hairline` track. [description] is the accessible name.
 */
@Composable
internal fun StoppedBars(target: Int, banked: Int, notKept: Int, description: String, modifier: Modifier = Modifier) {
    val bars = stoppedBars(target, banked, notKept)
    val blocks = bankBlocks(target)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        bars.forEachIndexed { i, bar ->
            val base = Modifier
                .weight(blocks[i].toFloat())
                .height(StoppedBarHeight)
            when (bar) {
                StoppedBar.Banked -> Box(base.background(HoldoutColors.Success, CircleShape))
                StoppedBar.Empty -> Box(base.background(HoldoutColors.Hairline, CircleShape))
                StoppedBar.NotKept -> Box(
                    base
                        .drawBehind {
                            val stroke = DashStroke.toPx()
                            drawRoundRect(
                                color = HoldoutColors.Warning,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
                            )
                        },
                )
            }
        }
    }
}

private val CountingBarHeight = 6.dp
private val StoppedBarHeight = 8.dp
private val DashStroke = 1.5f.dp
private val DashOn = 6.dp
private val DashOff = 4.dp
private const val BLOCK_REPS = holdout.app.ui.BLOCK_REPS
