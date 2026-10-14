package holdout.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.bankBlocks
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * The bank on Today's hero (001 FR-069a, research R25): the day's target cut into the blocks that bank for
 * good (reps bank in tens, FR-023) — 25 is `[10, 10, 5]`, 5 is `[5]`, 10 is `[10]`. A block is filled only
 * when its whole amount is banked, so reps short of the next block are never drawn as banked.
 */
object BankBars {
    /** The blocks of a day of [target] reps, in order; the last may be short. */
    fun blocks(target: Int): List<Int> = bankBlocks(target)

    /** Which blocks of a [target]-rep day are filled when [banked] reps have been banked. */
    fun filled(target: Int, banked: Int): List<Boolean> {
        var upTo = 0
        return blocks(target).map { share ->
            upTo += share
            banked >= upTo
        }
    }
}

/**
 * The bars themselves: one per block, as wide as the block's share of the day, [fill] once banked and
 * `hairline` until then. [fill] is `accent` while the day is being done, `success` once it is done or on
 * chain, and `warning` only in a Holdout day's last 10 minutes, where the countdown beside it says so in
 * words. The accessible name says how many blocks are banked.
 */
@Composable
fun BankBars(target: Int, banked: Int, modifier: Modifier = Modifier, fill: Color = HoldoutColors.Accent) {
    val blocks = BankBars.blocks(target)
    val filled = BankBars.filled(target, banked)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${filled.count { it }} of ${blocks.size} blocks banked" },
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
    ) {
        blocks.forEachIndexed { i, share ->
            Box(
                Modifier
                    .weight(share.toFloat())
                    .height(BAR_HEIGHT)
                    .background(if (filled[i]) fill else HoldoutColors.Hairline, CircleShape),
            )
        }
    }
}

private val BAR_HEIGHT = 6.dp

/** Between the blocks: 6 dp on the boards. */
private val BAR_GAP = 6.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BankBarsPreview() {
    HoldoutTheme {
        androidx.compose.foundation.layout.Column(Modifier.padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            BankBars(target = 25, banked = 0)
            BankBars(target = 25, banked = 19)
            BankBars(target = 25, banked = 25, fill = HoldoutColors.Success)
            BankBars(target = 5, banked = 5)
            BankBars(target = 10, banked = 10, fill = HoldoutColors.Warning)
        }
    }
}
