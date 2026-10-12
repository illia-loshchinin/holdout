package holdout.app.tour

import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import holdout.app.BuildConfig
import holdout.app.ui.TOP_VIEW_HEIGHT
import holdout.app.ui.TOP_VIEW_STILL
import holdout.app.ui.TOP_VIEW_WIDTH
import holdout.app.ui.drawTopView
import holdout.app.ui.theme.HoldoutColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Where the figure is in its 12 s loop, read at draw time so a frame never recomposes anything. */
internal class FigureClock(val still: Boolean, private val millis: State<Float>?) {
    fun now(): Long = millis?.value?.toLong() ?: 0L

    fun overlay(): FigureOverlay = if (still) FigureTimeline.still else FigureTimeline.at(now())
}

/** The loop's clock; still when the phone's animations are off (reduced motion shows the top pose and finished checks, DS TourFigure). */
@Composable
internal fun rememberFigureClock(): FigureClock {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    if (!animate) return remember { FigureClock(still = true, millis = null) }
    val millis = rememberInfiniteTransition(label = "figure").animateFloat(
        initialValue = 0f,
        targetValue = FigureTimeline.LOOP_MILLIS.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = FigureTimeline.LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "figure",
    )
    return remember(millis) { FigureClock(still = false, millis = millis) }
}

/**
 * The side-view push-up of "How reps are counted" (DS TourFigure, tour card 3):
 * a Seeker leans on a low stool, a figure kneels and gets into a push-up inside
 * an outline that turns green when the whole body is in frame, four checks tick,
 * the top is held for a second, 3 · 2 · 1, the checks clear and three reps count;
 * a small panel at the top left shows the push-up from above, with the phone turned 45°
 * ahead of the shoulder ([drawFromAbove]: the set-up guide's top view, held at the bottom of the push-up).
 *
 * **Generated, never redrawn:** the poses are the Holdout Rig's, read frame by
 * frame from `assets/tour_figure.txt` ([FigureData], made by
 * `scripts/gen-tour-figure.py`) and drawn in a Canvas. Only the body keeps the
 * figure's own colours; the camera cone, outline, check pills, countdown digits
 * and rep count use `accent` and `success`. With animations off it shows the top
 * pose and the finished checks, and the top view at the bottom of its push-up.
 *
 * The generator leaves the "from above" panel out of the data (it holds an animated
 * `<svg>`); it is drawn here, at the board's position and size, in the figure's scale.
 */
@Composable
internal fun TourFigure(clock: FigureClock, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data by produceState<FigureData?>(null) {
        value = withContext(Dispatchers.Default) { FigureCache.of(context).get() }
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(WIDTH / HEIGHT)
            .semantics { contentDescription = DESCRIPTION },
    ) {
        val figure = data ?: return@Canvas
        scale(size.width / WIDTH, pivot = Offset.Zero) {
            val overlay = clock.overlay()
            val frame = if (clock.still) FigureTimeline.STILL_FRAME else figure.schedule.frameAt(clock.now())
            drawOps(figure.scene, measurer, density)
            // Held at the bottom of the push-up, elbows bent at 45° (owner, 12 Oct): the side view carries the motion.
            drawFromAbove(TOP_VIEW_STILL, measurer, density)
            drawOutline(figure.outline, overlay.inFrame)
            drawOps(figure.frames[frame], measurer, density)
            if (overlay.kneesTag) drawOps(figure.knee, measurer, density)
            if (overlay.panel) drawPanel(overlay, measurer, density)
            overlay.countdown?.let { digit(it.toString(), 200f, 104f, DIGIT_SIZE, FontWeight.Light, false, HoldoutColors.Accent, measurer, density, center = true) }
            overlay.reps?.let { count(it, measurer, density) }
        }
    }
}

/**
 * The "from above" panel (the board's `translate(6 8)` group, top left): a black 120 × 74 card, its title, the shared
 * top view, held at the bottom of the push-up, in a 116 × 50 viewport and the distance under it. It sits left of the rep count (x 130) and never covers it.
 */
private fun DrawScope.drawFromAbove(progress: Float, measurer: TextMeasurer, density: Density) {
    translate(PANEL_X, PANEL_Y) {
        drawRoundRect(Color.Black, Offset.Zero, Size(PANEL_WIDTH, PANEL_HEIGHT), CornerRadius(PANEL_RADIUS))
        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset.Zero, Size(PANEL_WIDTH, PANEL_HEIGHT), CornerRadius(PANEL_RADIUS), style = Stroke(1f))
        digit("from above", 8f, 12f, PANEL_TITLE_SIZE, FontWeight.Normal, false, HoldoutColors.TextTertiary, measurer, density, center = false)
        // The board's nested <svg x="2" y="12" width="116" height="50" viewBox="0 0 350 150">: fitted whole, centred, clipped.
        translate(2f, 12f) {
            clipRect(0f, 0f, VIEWPORT_WIDTH, VIEWPORT_HEIGHT) {
                val fit = minOf(VIEWPORT_WIDTH / TOP_VIEW_WIDTH, VIEWPORT_HEIGHT / TOP_VIEW_HEIGHT)
                translate((VIEWPORT_WIDTH - TOP_VIEW_WIDTH * fit) / 2f, (VIEWPORT_HEIGHT - TOP_VIEW_HEIGHT * fit) / 2f) {
                    scale(fit, pivot = Offset.Zero) {
                        drawTopView(progress)
                        drawAngles(measurer, density)
                    }
                }
            }
        }
        digit("~1.5m ahead of your shoulder", 8f, 69f, PANEL_NOTE_SIZE, FontWeight.Normal, true, HoldoutColors.Accent, measurer, density, center = false)
    }
}

/**
 * The two 45°s of the position, as the board marks them (owner, 12 Oct; board 2c): beside the elbow, the angle the
 * upper arm makes with the body, and beside the phone, the angle it is turned toward the member. In the top view's units.
 */
private fun DrawScope.drawAngles(measurer: TextMeasurer, density: Density) {
    digit("45°", 192f, 44f, 20f, FontWeight.Normal, true, HoldoutColors.Accent, measurer, density, center = false)
    digit("45°", 88f, 124f, 20f, FontWeight.Normal, true, HoldoutColors.Accent, measurer, density, center = false)
}

private fun DrawScope.drawOutline(outline: FigureOp.Rect, inFrame: Boolean) {
    val topLeft = Offset(outline.x, outline.y)
    val size = Size(outline.width, outline.height)
    val radius = CornerRadius(outline.radius)
    if (inFrame) {
        drawRoundRect(HoldoutColors.Success, topLeft, size, radius, style = Stroke(2f))
    } else {
        drawRoundRect(HoldoutColors.Accent, topLeft, size, radius, style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
    }
}

/** The panel of four checks and the hold (the board's top right). */
private fun DrawScope.drawPanel(o: FigureOverlay, measurer: TextMeasurer, density: Density) {
    val white = HoldoutColors.TextPrimary
    drawRoundRect(HoldoutColors.Canvas.copy(alpha = 0.6f), Offset(246f, 12f), Size(142f, 104f), CornerRadius(12f))
    drawRoundRect(white.copy(alpha = 0.12f), Offset(246f, 12f), Size(142f, 104f), CornerRadius(12f), style = Stroke(1f))
    CHECKS.forEachIndexed { i, label ->
        val y = 30f + 18f * i
        val done = i < o.checksDone
        if (done) {
            tick(y)
        } else {
            drawCircle(white.copy(alpha = 0.24f), 6f, Offset(260f, y), style = Stroke(1.5f))
            if (i == o.checking) arc(y, 133.7f, 1.5f)
        }
        label(label, y + 4f, if (done) 1f else 0.55f, measurer, density)
    }
    // The hold: a ring that fills, then the tick.
    if (o.holdDone) {
        tick(102f)
    } else {
        drawCircle(white.copy(alpha = 0.18f), 6f, Offset(260f, 102f), style = Stroke(2f))
        if (o.holdProgress > 0f) arc(102f, 360f * o.holdProgress, 2f)
    }
    label("Hold the top · 1 s", 106f, if (o.holdDone) 1f else 0.55f, measurer, density)
}

private fun DrawScope.arc(y: Float, sweep: Float, width: Float) {
    drawArc(HoldoutColors.Accent, -90f, sweep, false, Offset(254f, y - 6f), Size(12f, 12f), style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.tick(y: Float) {
    drawCircle(HoldoutColors.Success.copy(alpha = 0.16f), 6.5f, Offset(260f, y))
    val path = PathParser().parsePathString("M256.8 ${y + 0.2f} l2.2 2.2 4-4.4").toPath()
    drawPath(path, HoldoutColors.Success, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.label(text: String, baseline: Float, alpha: Float, measurer: TextMeasurer, density: Density) {
    digit(text, 272f, baseline, LABEL_SIZE, FontWeight.Normal, false, HoldoutColors.TextPrimary.copy(alpha = alpha), measurer, density, center = false)
}

private fun DrawScope.count(reps: Int, measurer: TextMeasurer, density: Density) {
    digit(reps.toString(), 130f, 44f, COUNT_SIZE, FontWeight.Normal, true, HoldoutColors.TextPrimary, measurer, density, center = false)
    digit("reps", 148f, 44f, REPS_SIZE, FontWeight.Normal, false, HoldoutColors.TextTertiary, measurer, density, center = false)
}

/** One run of text at [x], on the baseline [y], sized in the board's units (1 unit = 1 px before the canvas scales it). */
private fun DrawScope.digit(
    text: String,
    x: Float,
    y: Float,
    size: Float,
    weight: FontWeight,
    mono: Boolean,
    color: Color,
    measurer: TextMeasurer,
    density: Density,
    center: Boolean,
) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = with(density) { size.toSp() }, fontWeight = weight, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default))
    drawText(layout, topLeft = Offset(if (center) x - layout.size.width / 2f else x, y - layout.firstBaseline))
}

private fun DrawScope.drawOps(ops: List<FigureOp>, measurer: TextMeasurer, density: Density) {
    var depth = 0
    for (op in ops) {
        when (op) {
            is FigureOp.Push -> {
                drawContext.canvas.save()
                drawContext.canvas.translate(op.tx, op.ty)
                drawContext.canvas.rotate(op.rotation)
                depth++
            }
            FigureOp.Pop -> if (depth > 0) {
                drawContext.canvas.restore()
                depth--
            }
            is FigureOp.PathOp -> paint(op.style) { fillColour, stroke -> shape(op.path, fillColour, stroke) }
            is FigureOp.Rect -> paint(op.style) { fillColour, stroke ->
                val topLeft = Offset(op.x, op.y)
                val size = Size(op.width, op.height)
                val radius = CornerRadius(op.radius)
                fillColour?.let { drawRoundRect(it, topLeft, size, radius) }
                stroke?.let { (colour, style) -> drawRoundRect(colour, topLeft, size, radius, style = style) }
            }
            is FigureOp.Circle -> paint(op.style) { fillColour, stroke ->
                fillColour?.let { drawCircle(it, op.r, Offset(op.cx, op.cy)) }
                stroke?.let { (colour, style) -> drawCircle(colour, op.r, Offset(op.cx, op.cy), style = style) }
            }
            is FigureOp.Ellipse -> rotate(op.rotation, Offset(op.cx, op.cy)) {
                paint(op.style) { fillColour, stroke ->
                    val topLeft = Offset(op.cx - op.rx, op.cy - op.ry)
                    val size = Size(op.rx * 2, op.ry * 2)
                    fillColour?.let { drawOval(it, topLeft, size) }
                    stroke?.let { (colour, style) -> drawOval(colour, topLeft, size, style = style) }
                }
            }
            is FigureOp.Text -> drawFigureText(op, measurer, density)
        }
    }
    while (depth-- > 0) drawContext.canvas.restore()
}

private fun DrawScope.drawFigureText(op: FigureOp.Text, measurer: TextMeasurer, density: Density) {
    val color = (op.style.fill?.color ?: HoldoutColors.TextPrimary).let { it.copy(alpha = it.alpha * op.style.fillOpacity * op.style.opacity) }
    val layout = measurer.measure(
        op.text,
        TextStyle(
            color = color,
            fontSize = with(density) { op.size.toSp() },
            fontWeight = FontWeight(op.weight),
            fontFamily = if (op.mono) FontFamily.Monospace else FontFamily.Default,
            letterSpacing = with(density) { op.spacing.toSp() },
        ),
    )
    val left = if (op.middle) op.x - layout.size.width / 2f else op.x
    val top = if (op.centredOnY) op.y - layout.size.height / 2f else op.y - layout.firstBaseline
    rotate(op.rotation, Offset(op.x, op.y)) { drawText(layout, topLeft = Offset(left, top)) }
}

private fun DrawScope.shape(path: androidx.compose.ui.graphics.Path, fillColour: Color?, stroke: Pair<Color, Stroke>?) {
    fillColour?.let { drawPath(path, it) }
    stroke?.let { (colour, style) -> drawPath(path, colour, style = style) }
}

/** Resolves a shape's [FigureStyle] into the colours and stroke to draw with, then calls [block]. */
private inline fun paint(style: FigureStyle, block: (Color?, Pair<Color, Stroke>?) -> Unit) {
    val fillColour = style.fill?.color?.let { it.copy(alpha = it.alpha * style.fillOpacity * style.opacity) }
    val stroke = style.stroke?.let { s ->
        val colour = s.color.let { it.copy(alpha = it.alpha * style.strokeOpacity * style.opacity) }
        colour to Stroke(
            width = style.strokeWidth,
            cap = if (style.roundCap) StrokeCap.Round else StrokeCap.Butt,
            join = if (style.roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
            pathEffect = style.dash?.let { PathEffect.dashPathEffect(it) },
        )
    }
    block(fillColour, stroke)
}

internal const val ASSET = "tour_figure.txt"

/**
 * The parsed figure, kept for the life of the process: [FigureData] is immutable and the asset
 * is about 650 KB, so card 3 must not read and parse it again on every entry. A load that fails is
 * reported through [report]; a debug build also throws it, so a missing or malformed asset is
 * seen, not an empty canvas. A failed load is not kept, so the next entry tries again.
 */
internal class FigureCache(
    private val open: () -> InputStream,
    private val debug: Boolean,
    private val report: (Throwable) -> Unit,
) {
    private var data: FigureData? = null

    @Synchronized
    fun get(): FigureData? {
        data?.let { return it }
        return try {
            open().use { FigureData.parse(it) }.also { data = it }
        } catch (e: Exception) {
            report(e)
            if (debug) throw e
            null
        }
    }

    companion object {
        private var shared: FigureCache? = null

        /** The process's one cache, over the app's assets. */
        @Synchronized
        fun of(context: Context): FigureCache = shared ?: context.applicationContext.let { app ->
            FigureCache(
                open = { app.assets.open(ASSET) },
                debug = BuildConfig.DEBUG,
                report = { Log.e("TourFigure", "The tour figure could not be loaded from $ASSET", it) },
            ).also { shared = it }
        }
    }
}

private const val WIDTH = 400f
private const val HEIGHT = 240f
private const val PANEL_X = 6f
private const val PANEL_Y = 8f
private const val PANEL_WIDTH = 120f
private const val PANEL_HEIGHT = 74f
private const val PANEL_RADIUS = 10f
private const val PANEL_TITLE_SIZE = 8f
private const val PANEL_NOTE_SIZE = 6f
private const val VIEWPORT_WIDTH = 116f
private const val VIEWPORT_HEIGHT = 50f
private const val DIGIT_SIZE = 64f
private const val LABEL_SIZE = 11f
private const val COUNT_SIZE = 22f
private const val REPS_SIZE = 12f
private val CHECKS = listOf("Lighting", "Whole body in frame", "Camera view · 45°", "Background still")

internal const val DESCRIPTION =
    "Animation, side view: a Seeker leans on a low stool, its front camera facing you. You step in, kneel and get into a push-up; " +
        "your whole body, head to feet and knees included, fits the outline. The checks tick green — lighting, whole body, knees, " +
        "camera view, background — you hold the top for a second, then a 3, 2, 1 countdown, the checks clear and three reps count. " +
        "A small panel at the top left shows the push-up from above, animated: the phone stands on the floor turned 45 degrees, " +
        "about 1.5 metres ahead of your shoulder, with its sight line to you; on the way down your elbows bend outward and your " +
        "head moves a little toward the phone, on the way up your arms straighten."
