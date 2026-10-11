package holdout.app.cohort

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * How the crowd thinned (H4, board 13): one point for the start and one after
 * each deadline that has passed, [points] being the members still standing.
 * Dotted gridlines at round values, the baseline with `start` · a middle day ·
 * the last day under it, a line in `accent` with a soft fill under it, and the
 * last point ringed with its value.
 *
 * Drawn only from two points up; the caller shows nothing before the first
 * deadline has passed. The accessible name carries the whole story in a
 * sentence ([DetailText.thinningDescription]): the chart itself is not read.
 */
@Composable
fun ThinningChart(points: List<Int>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val measurer = rememberTextMeasurer()
    val labels = LabelStyle.copy(color = HoldoutColors.TextTertiary)
    val strong = LabelStyle.copy(color = HoldoutColors.TextPrimary)
    val description = DetailText.thinningDescription(points)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        val left = GUTTER.toPx()
        // Room right of the last point for its value, so the label never sits on the line.
        val right = size.width - END_PAD.toPx()
        val top = TOP_PAD.toPx()
        val base = size.height - LABEL_BAND.toPx()
        val hi = points.max()
        val lo = DetailText.floorValue(points.min(), hi)
        fun x(i: Int) = left + (right - left) * i / points.lastIndex
        fun y(v: Int) = if (hi == lo) base else base - (base - top) * (v - lo) / (hi - lo)

        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        DetailText.gridValues(points.min(), hi).forEach { v ->
            drawLine(HoldoutColors.Hairline, Offset(left, y(v)), Offset(right, y(v)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            label(measurer, v.toString(), labels, Offset(left - 6.dp.toPx(), y(v)), alignEnd = true, centreY = true)
        }
        drawLine(HoldoutColors.Hairline, Offset(left, base), Offset(right, base), strokeWidth = 1.dp.toPx())
        val last = points.lastIndex
        label(measurer, "start", labels, Offset(left, base + 6.dp.toPx()))
        if (last >= 4) label(measurer, "day ${last / 2}", labels, Offset(x(last / 2), base + 6.dp.toPx()), centre = true)
        label(measurer, "day $last", labels, Offset(right, base + 6.dp.toPx()), alignEnd = true)

        val line = Path().apply { points.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val area = Path().apply {
            addPath(line)
            lineTo(x(last), base)
            lineTo(x(0), base)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(HoldoutColors.Accent.copy(alpha = FILL_ALPHA), Color.Transparent), startY = top, endY = base))
        drawPath(line, HoldoutColors.Accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, v -> if (i != last) drawCircle(HoldoutColors.Accent, 2.2.dp.toPx(), Offset(x(i), y(v))) }
        drawCircle(HoldoutColors.Canvas, 5.dp.toPx(), Offset(x(last), y(points[last])))
        drawCircle(HoldoutColors.Accent, 5.dp.toPx(), Offset(x(last), y(points[last])), style = Stroke(width = 2.dp.toPx()))
        label(measurer, points[last].toString(), strong, Offset(x(last) + 10.dp.toPx(), y(points[last])), centreY = true)
        // The first value sits under the line's start; where the line drops away steeply it would cross the label, so it moves right of the line.
        val drop = if (points.size > 1) (y(points[1]) - y(points[0])) / (x(1) - x(0)) else 0f
        if (drop >= STEEP) {
            label(measurer, points[0].toString(), labels, Offset(left + (LABEL_HALF_HEIGHT.toPx() / drop + 4.dp.toPx()).coerceAtMost(x(1) - left), y(points[0])), centreY = true)
        } else {
            label(measurer, points[0].toString(), labels, Offset(left + 6.dp.toPx(), y(points[0]) + 12.dp.toPx()), centreY = true)
        }
    }
}

private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    at: Offset,
    alignEnd: Boolean = false,
    centre: Boolean = false,
    centreY: Boolean = false,
) {
    val laid = measurer.measure(text, style)
    val x = when {
        alignEnd -> at.x - laid.size.width
        centre -> at.x - laid.size.width / 2f
        else -> at.x
    }
    val y = if (centreY) at.y - laid.size.height / 2f else at.y
    drawText(laid, topLeft = Offset(x, y))
}

private val CHART_HEIGHT = 124.dp
private val GUTTER = 32.dp
private val END_PAD = 28.dp
private val TOP_PAD = Spacing.tight
private val LABEL_BAND = 20.dp
private const val FILL_ALPHA = 0.28f

/** A line that falls this many pixels per pixel across would run through a label set just under its start. */
private const val STEEP = 0.25f
private val LABEL_HALF_HEIGHT = 7.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ThinningChartPreview() {
    HoldoutTheme { ThinningChart(listOf(312, 301, 288, 270, 259, 243, 231, 222, 210, 203, 195, 189), Modifier.padding(Spacing.screenGutter)) }
}
