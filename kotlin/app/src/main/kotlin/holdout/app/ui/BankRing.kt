package holdout.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * A day's reps cut into the blocks that bank (a block of 10 banks for good):
 * 25 reps are `[10, 10, 5]`. The last block may be short; reps short of the
 * next block are never kept, so they are never drawn as banked.
 */
fun bankBlocks(reps: Int, blockSize: Int = BLOCK_REPS): List<Int> {
    if (reps <= 0) return emptyList()
    return List(reps / blockSize) { blockSize } + listOfNotNull((reps % blockSize).takeIf { it > 0 })
}

/** Reps in one banked block. */
const val BLOCK_REPS = 10

/**
 * A progress ring around the rep count that shows how much of today is safely
 * banked (design book, Bank ring): a circle split into one segment per block,
 * each arc proportional to its reps with a small gap between. The track is
 * `hairline`, a banked segment `accent`, both `size-ring-stroke` wide with round
 * caps. Inside: the [count] in `display` and a [caption] (`banked`,
 * `day complete`).
 *
 * The consumer provides the day's [reps] and how many blocks are
 * [bankedBlocks]. Reps short of the next block are not drawn. The accessible
 * name carries what the arcs show: "10 banked, 1 of 3 blocks banked".
 */
@Composable
fun BankRing(
    reps: Int,
    bankedBlocks: Int,
    count: Int,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = RING_SIZE,
) {
    val blocks = bankBlocks(reps)
    val banked = bankedBlocks.coerceIn(0, blocks.size)
    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) { contentDescription = "$count $caption, $banked of ${blocks.size} blocks banked" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Spacing.ringStroke.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val radius = arc.width / 2
            // The gap between segments, and the round caps that eat into it.
            val gapDegrees = Math.toDegrees(((Spacing.base.toPx() + stroke) / radius).toDouble()).toFloat()
            var start = START_DEGREES
            blocks.forEachIndexed { i, share ->
                val full = 360f * share / reps
                // A lone block closes the circle; several leave a gap between them.
                val single = blocks.size == 1
                drawArc(
                    color = if (i < banked) HoldoutColors.Accent else HoldoutColors.Hairline,
                    startAngle = if (single) start else start + gapDegrees / 2,
                    sweepAngle = if (single) full else (full - gapDegrees).coerceAtLeast(MIN_SWEEP),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                start += full
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = DisplayStyle, color = HoldoutColors.TextPrimary)
            Text(caption.uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary)
        }
    }
}

/**
 * The three bars of a counting sitting: one per block, as wide as the block's
 * share of the day (10 / 10 / 5 for 25 reps), `accent` once banked and
 * `hairline` until then. The accessible name says how many blocks are banked.
 */
@Composable
fun BlockBars(reps: Int, bankedBlocks: Int, modifier: Modifier = Modifier) {
    val blocks = bankBlocks(reps)
    val banked = bankedBlocks.coerceIn(0, blocks.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$banked of ${blocks.size} blocks banked" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        blocks.forEachIndexed { i, share ->
            Box(
                Modifier
                    .weight(share.toFloat())
                    .height(BAR_HEIGHT)
                    .background(if (i < banked) HoldoutColors.Accent else HoldoutColors.Hairline, CircleShape),
            )
        }
    }
}

private val RING_SIZE = 160.dp
private val BAR_HEIGHT = 6.dp
private const val START_DEGREES = -90f

/** A segment never shrinks below a dot, however many blocks the day has. */
private const val MIN_SWEEP = 1f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BankRingPreview() {
    HoldoutTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems), horizontalAlignment = Alignment.CenterHorizontally) {
            BankRing(reps = 25, bankedBlocks = 1, count = 10, caption = "banked")
            BankRing(reps = 25, bankedBlocks = 3, count = 25, caption = "day complete")
            BlockBars(reps = 25, bankedBlocks = 1)
        }
    }
}
