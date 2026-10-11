package holdout.app.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The worked example on tour card 2: 25 members stake 1,250 SKR each on a Habit,
 * 7 finish. Every figure follows from those three numbers and the 70 % that goes
 * to the prize pool; an example, not a promise (the boost and the project's share
 * are left out, as the board leaves them out).
 */
internal object TourExample {
    const val MEMBERS = 25
    const val FINISHERS = 7
    const val STAKE = 1_250L

    /** The prize pool's share of a lost stake, in percent. */
    private const val POOL_PERCENT = 70

    val lost = MEMBERS - FINISHERS
    val lostStakes = lost * STAKE
    val prizePool = lostStakes * POOL_PERCENT / 100
    val sharePerFinisher = prizePool / FINISHERS
    val finisherTakes = STAKE + sharePerFinisher

    /** The seven who finish stand as the Holdout's H on the five-by-five grid: (row, column). */
    val standing = setOf(1 to 1, 1 to 3, 2 to 1, 2 to 2, 2 to 3, 3 to 1, 3 to 3)

    /** `1,250 SKR`, as the rest of the app writes SKR. */
    fun skr(whole: Long): String = Format.skr(whole.toULong() * 1_000_000uL)

    /** `1,250`: the same amount without the unit, where a line already says what it adds up to. */
    fun plain(whole: Long): String = skr(whole).removeSuffix(" SKR")
}

/** The worked example's card (tour card 2). */
@Composable
internal fun ExampleCard() {
    val e = TourExample
    HoldoutCard {
        SectionLabel("Example · Habit · 21-day")
        Row(
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Day 1: ${e.MEMBERS} members stake ${TourExample.skr(e.STAKE)} each. " +
                        "Day 21: ${e.FINISHERS} finish, standing as the Holdout H; ${e.lost} missed a day and faded out."
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text("Day 1", style = LabelStyle, color = HoldoutColors.TextTertiary)
                ExampleGrid { _, _ -> HoldoutColors.TextPrimary }
                Text("${e.MEMBERS} × ${TourExample.skr(e.STAKE)}", style = LabelStyle.copy(fontFamily = FontFamily.Monospace), color = HoldoutColors.TextSecondary)
            }
            Arrow()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text("Day 21", style = LabelStyle, color = HoldoutColors.TextTertiary)
                ExampleGrid { r, c -> if (r to c in e.standing) HoldoutColors.Success else HoldoutColors.TextPrimary.copy(alpha = FADED) }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = HoldoutColors.Success)) { append("${e.FINISHERS} finish") }
                        withStyle(SpanStyle(color = HoldoutColors.TextTertiary)) { append(" · ${e.lost} lost") }
                    },
                    style = LabelStyle,
                )
            }
        }
        Rule()
        ExampleRow("${e.lost} stakes lost", TourExample.skr(e.lostStakes))
        ExampleRow("70 % to the prize pool", TourExample.skr(e.prizePool))
        ExampleRow("Split by ${e.FINISHERS} finishers", "${TourExample.skr(e.sharePerFinisher)} each")
        Rule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Each finisher takes", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
            Text(TourExample.skr(e.finisherTakes), style = AmountStyle, color = HoldoutColors.Success)
        }
        Text(
            "${TourExample.plain(e.STAKE)} stake back + ${TourExample.plain(e.sharePerFinisher)} share. If nobody drops out, you get exactly your stake back.",
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
        )
    }
}

@Composable
private fun ExampleRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        Text(value, style = LabelStyle.copy(fontFamily = FontFamily.Monospace), color = HoldoutColors.TextPrimary)
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(Spacing.border).background(HoldoutColors.Hairline))
}

@Composable
private fun Arrow() {
    Canvas(Modifier.size(ARROW_WIDTH, ARROW_HEIGHT)) {
        val u = size.width / ARROW_UNITS
        val line = Stroke(1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(HoldoutColors.TextTertiary, Offset(2f * u, 7f * u), Offset(24f * u, 7f * u), strokeWidth = line.width, cap = StrokeCap.Round)
        drawLine(HoldoutColors.TextTertiary, Offset(18f * u, 2f * u), Offset(24f * u, 7f * u), strokeWidth = line.width, cap = StrokeCap.Round)
        drawLine(HoldoutColors.TextTertiary, Offset(18f * u, 12f * u), Offset(24f * u, 7f * u), strokeWidth = line.width, cap = StrokeCap.Round)
    }
}

// ---- dot grids ------------------------------------------------------------------------------------

/** One dot of a [DotGrid], in the grid's own units (1 unit = 1 dp). */
internal class DotSpec(val x: Float, val y: Float, val colour: Color)

/** Dots drawn in a [width] × [height] dp box, each [radius] units across from its centre. */
@Composable
internal fun DotGrid(width: Dp, height: Dp, radius: Float, dots: List<DotSpec>, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, height)) {
        val k = size.width / width.value
        dots.forEach { drawCircle(it.colour, radius * k, Offset(it.x * k, it.y * k)) }
    }
}

/** The example's five-by-five grid: 7-unit dots, 18 apart, 8 in, in an 88 dp box. */
@Composable
private fun ExampleGrid(colourOf: (row: Int, column: Int) -> Color) {
    val dots = remember(colourOf) { (0 until 5).flatMap { r -> (0 until 5).map { c -> DotSpec(8f + 18f * c, 8f + 18f * r, colourOf(r, c)) } } }
    DotGrid(88.dp, 88.dp, 7f, dots)
}

private const val FADED = 0.12f
private val ARROW_WIDTH = 28.dp
private val ARROW_HEIGHT = 14.dp
private const val ARROW_UNITS = 28f
