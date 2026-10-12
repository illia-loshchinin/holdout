package holdout.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import holdout.app.ui.theme.HoldoutColors

/*
 * The push-up seen from above (canvas board 16, "Set-up guide", the "At 45°" card; board 2c, tour card 3, the
 * "from above" panel): the member lying across a camera frame, the phone ahead of the shoulder at 45° with its
 * sight line. One drawing for both places. It is a faithful port of the board's SVG: the same shapes and the same
 * animated values, each one a keyframe per `keyTimes` "0;0.12;0.56;1" of a 1.5 s loop, eased between keyframes by
 * the board's `keySplines` (linear, then ease-in-out twice). On the way down the elbows bend outward and the head
 * moves a little toward the phone; on the way up the arms straighten.
 */

/** The drawing's own units: the board's 350 × 150 view box. */
internal const val TOP_VIEW_WIDTH = 350f
internal const val TOP_VIEW_HEIGHT = 150f

/** Nothing is drawn below this line (the phone's lower corner is the lowest point); the set-up card crops to it. */
internal const val TOP_VIEW_CONTENT_HEIGHT = 124f

/** The loop's length. */
internal const val TOP_VIEW_LOOP_MILLIS = 1_500

/** Where a still frame stops: the bottom of the push-up (the third key time), where the elbows are out. */
internal const val TOP_VIEW_STILL = 0.56f

internal const val TOP_VIEW_DESCRIPTION =
    "Animation, seen from above: a person does push-ups, head toward a phone standing on the floor at 45 degrees " +
        "ahead of their shoulder. On the way down the elbows bend outward and the head moves a little toward the phone; " +
        "on the way up the arms straighten."

/** The board's `keyTimes`, and the `keySplines` between each pair of them. */
private val KEY_TIMES = floatArrayOf(0f, 0.12f, 0.56f, 1f)
private val SPLINES = arrayOf(
    CubicBezierEasing(0f, 0f, 1f, 1f),
    CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
    CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
)

/** One animated value: its four keyframes, read at a [progress] of the loop (0..1) the way an SVG `<animate calcMode="spline">` does. */
internal class Keyframes(private vararg val values: Float) {
    init {
        require(values.size == KEY_TIMES.size) { "A value has one keyframe per key time" }
    }

    fun at(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val i = when {
            p < KEY_TIMES[1] -> 0
            p < KEY_TIMES[2] -> 1
            else -> 2
        }
        val eased = SPLINES[i].transform((p - KEY_TIMES[i]) / (KEY_TIMES[i + 1] - KEY_TIMES[i]))
        return values[i] + (values[i + 1] - values[i]) * eased
    }
}

/** Four keyframes, or one value that never changes. */
internal fun kf(a: Float, b: Float, c: Float, d: Float) = Keyframes(a, b, c, d)

internal fun kf(value: Float) = Keyframes(value, value, value, value)

/** A path whose `d` is animated: the same commands (M, L, Q, Z) in all four keyframes, each coordinate eased on its own. */
internal class KeyPath(private val commands: String, private val coordinates: List<Keyframes>) {
    /** Every coordinate at [progress], in the order the path reads them. */
    fun coordinatesAt(progress: Float): FloatArray = FloatArray(coordinates.size) { coordinates[it].at(progress) }

    fun toPath(progress: Float, into: Path): Path {
        val c = coordinatesAt(progress)
        var n = 0
        into.rewind()
        commands.forEach { command ->
            when (command) {
                'M' -> into.moveTo(c[n], c[n + 1]).also { n += 2 }
                'L' -> into.lineTo(c[n], c[n + 1]).also { n += 2 }
                'Q' -> into.quadraticTo(c[n], c[n + 1], c[n + 2], c[n + 3]).also { n += 4 }
                'Z' -> into.close()
            }
        }
        return into
    }
}

private val TOKEN = Regex("[MLQZ]|-?\\d*\\.?\\d+")

/** The path data of the four keyframes (an SVG `values` of a `d`) as one [KeyPath]. */
internal fun keyPath(vararg frames: String): KeyPath {
    require(frames.size == KEY_TIMES.size) { "A path has one keyframe per key time" }
    val parsed = frames.map { frame -> TOKEN.findAll(frame).map { it.value }.toList() }
    val commands = parsed[0].filter { it[0].isLetter() }.joinToString("")
    parsed.forEach { tokens ->
        require(tokens.filter { it[0].isLetter() }.joinToString("") == commands) { "Every keyframe of a path has the same commands" }
    }
    val numbers = parsed.map { tokens -> tokens.filter { !it[0].isLetter() }.map { it.toFloat() } }
    return KeyPath(commands, numbers[0].indices.map { i -> Keyframes(*FloatArray(KEY_TIMES.size) { numbers[it][i] }) })
}

/** A drawn piece of the member; [draw] reads its animated values at [progress]. */
private sealed interface Piece {
    fun draw(scope: DrawScope, progress: Float)
}

private fun Color.faded(opacity: Float) = copy(alpha = alpha * opacity)

private class Rect(
    val x: Keyframes,
    val y: Keyframes,
    val w: Keyframes,
    val h: Keyframes,
    val rx: Float,
    val fill: Color? = null,
    val stroke: Color? = null,
    val width: Float = 1f,
    val opacity: Keyframes = kf(1f),
) : Piece {
    override fun draw(scope: DrawScope, progress: Float) {
        with(scope) {
            val topLeft = Offset(x.at(progress), y.at(progress))
            val size = Size(w.at(progress), h.at(progress))
            val alpha = opacity.at(progress)
            fill?.let { drawRoundRect(it.faded(alpha), topLeft, size, CornerRadius(rx)) }
            stroke?.let { drawRoundRect(it.faded(alpha), topLeft, size, CornerRadius(rx), style = Stroke(width)) }
        }
    }
}

private class Line(
    val d: KeyPath,
    val fill: Color? = null,
    val stroke: Color? = null,
    val width: Float = 1f,
    val round: Boolean = false,
    val joined: Boolean = false,
    val opacity: Float = 1f,
) : Piece {
    private val path = Path()

    override fun draw(scope: DrawScope, progress: Float) {
        with(scope) {
            d.toPath(progress, path)
            fill?.let { drawPath(path, it.faded(opacity)) }
            stroke?.let {
                val style = Stroke(width, cap = if (round) StrokeCap.Round else StrokeCap.Butt, join = if (joined) StrokeJoin.Round else StrokeJoin.Miter)
                drawPath(path, it.faded(opacity), style = style)
            }
        }
    }
}

private class Dot(
    val cx: Keyframes,
    val cy: Keyframes,
    val r: Keyframes,
    val fill: Color,
    val stroke: Color,
    val width: Float,
) : Piece {
    override fun draw(scope: DrawScope, progress: Float) {
        with(scope) {
            val centre = Offset(cx.at(progress), cy.at(progress))
            val radius = r.at(progress)
            drawCircle(fill, radius, centre)
            drawCircle(stroke, radius, centre, style = Stroke(width))
        }
    }
}

/** The member, as the board draws it, back to front: the shadow, the legs and feet, the torso, the arms, the head and the tracked points. */
private val MEMBER: List<Piece> = listOf(
    Rect(x = kf(128f, 128f, 134f, 128f), y = kf(36f, 36f, 40f, 36f), w = kf(190f, 190f, 182f, 190f), h = kf(44f, 44f, 36f, 44f), rx = 12f, fill = Color.Black, opacity = kf(0.1f, 0.1f, 0.42f, 0.1f)),
    Line(d = keyPath("M250 49 L304 52.5 L304 57.5 L250 57.5 Z", "M250 49 L304 52.5 L304 57.5 L250 57.5 Z", "M247 49.7 L304 52.5 L304 57.5 L247 57.5 Z", "M250 49 L304 52.5 L304 57.5 L250 57.5 Z"), fill = Color(0xFF355666), stroke = Color(0xFF070B0D), width = 1.6f, joined = true),
    Line(d = keyPath("M250 58.5 L304 58.5 L304 63.5 L250 67 Z", "M250 58.5 L304 58.5 L304 63.5 L250 67 Z", "M247 58.5 L304 58.5 L304 63.5 L247 66.3 Z", "M250 58.5 L304 58.5 L304 63.5 L250 67 Z"), fill = Color(0xFF355666), stroke = Color(0xFF070B0D), width = 1.6f, joined = true),
    Rect(x = kf(304f), y = kf(52.5f), w = kf(9f), h = kf(5f), rx = 2f, fill = Color(0xFF596166), stroke = Color(0xFF070B0D), width = 1.2f),
    Rect(x = kf(304f), y = kf(58.5f), w = kf(9f), h = kf(5f), rx = 2f, fill = Color(0xFF596166), stroke = Color(0xFF070B0D), width = 1.2f),
    Line(d = keyPath("M170 43 Q210 49 250 49 L250 67 Q210 67 170 73 Q164 58 170 43 Z", "M170 43 Q210 49 250 49 L250 67 Q210 67 170 73 Q164 58 170 43 Z", "M160 45.2 Q203.5 50.32 247 49.7 L247 66.3 Q203.5 65.68 160 70.8 Q154 58 160 45.2 Z", "M170 43 Q210 49 250 49 L250 67 Q210 67 170 73 Q164 58 170 43 Z"), fill = Color(0xFF48748A), stroke = Color(0xFF070B0D), width = 2f, joined = true),
    Line(d = keyPath("M174 58 L246 58", "M174 58 L246 58", "M164 58 L243 58", "M174 58 L246 58"), stroke = Color(0xFF243A45), width = 1.05f, round = true, opacity = 0.85f),
    Line(d = keyPath("M161 58 L171 58", "M161 58 L171 58", "M148.4 58 L161 58", "M161 58 L171 58"), stroke = Color(0xFFD0DEE6), width = 7f, round = true),
    Line(d = keyPath("M170 43 L173 40 L174 36", "M170 43 L173 40 L174 36", "M160 45.2 L184 25.5 L174 36", "M170 43 L173 40 L174 36"), stroke = Color(0xFF070B0D), width = 8.4f, round = true, joined = true),
    Line(d = keyPath("M170 43 L173 40 L174 36", "M170 43 L173 40 L174 36", "M160 45.2 L184 25.5 L174 36", "M170 43 L173 40 L174 36"), stroke = Color(0xFF568AA4), width = 5.2f, round = true, joined = true),
    Line(d = keyPath("M170 73 L173 76 L174 80", "M170 73 L173 76 L174 80", "M160 70.8 L184 90.5 L174 80", "M170 73 L173 76 L174 80"), stroke = Color(0xFF070B0D), width = 8.4f, round = true, joined = true),
    Line(d = keyPath("M170 73 L173 76 L174 80", "M170 73 L173 76 L174 80", "M160 70.8 L184 90.5 L174 80", "M170 73 L173 76 L174 80"), stroke = Color(0xFF568AA4), width = 5.2f, round = true, joined = true),
    Dot(cx = kf(174f), cy = kf(36f), r = kf(3.4f), fill = Color(0xFFD0DEE6), stroke = Color(0xFF070B0D), width = 1.2f),
    Dot(cx = kf(174f), cy = kf(80f), r = kf(3.4f), fill = Color(0xFFD0DEE6), stroke = Color(0xFF070B0D), width = 1.2f),
    Dot(cx = kf(153f, 153f, 141f, 153f), cy = kf(58f), r = kf(9f, 9f, 8.4f, 9f), fill = Color(0xFF3E4549), stroke = Color(0xFF070B0D), width = 1.6f),
    Dot(cx = kf(170f, 170f, 160f, 170f), cy = kf(43f, 43f, 45.2f, 43f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(173f, 173f, 184f, 173f), cy = kf(40f, 40f, 25.5f, 40f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(250f, 250f, 247f, 250f), cy = kf(53.5f, 53.5f, 53.85f, 53.5f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(278f), cy = kf(55f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(170f, 170f, 160f, 170f), cy = kf(73f, 73f, 70.8f, 73f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(173f, 173f, 184f, 173f), cy = kf(76f, 76f, 90.5f, 76f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(250f, 250f, 247f, 250f), cy = kf(62.5f, 62.5f, 62.15f, 62.5f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(278f), cy = kf(61f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
    Dot(cx = kf(171f, 171f, 161f, 171f), cy = kf(58f), r = kf(2.2f), fill = HoldoutColors.Accent, stroke = Color(0xFF070B0D), width = 0.8f),
)

/**
 * The top view at [progress] of its loop (0..1), in [TOP_VIEW_WIDTH] × [TOP_VIEW_HEIGHT] units from the scope's origin:
 * the camera frame, the member, and the phone at 45° with its sight line, those two in [accent] (green once the camera
 * sees the phone there). The caller sizes it (scale) and clips it.
 */
internal fun DrawScope.drawTopView(progress: Float, accent: Color = HoldoutColors.Accent) {
    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(120f, 20f), Size(200f, 76f), CornerRadius(10f), style = Stroke(1.5f))
    MEMBER.forEach { it.draw(this, progress) }
    translate(70f, 100f) {
        rotate(-45f, Offset.Zero) {
            drawRoundRect(accent, Offset(-8f, -14f), Size(16f, 28f), CornerRadius(3f), style = Stroke(1.8f))
        }
    }
    drawLine(accent, Offset(80f, 90f), Offset(150f, 68f), strokeWidth = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
}

/**
 * Where the top view is in its 1.5 s loop (0..1), read at draw time so a frame recomposes nothing. With animations
 * turned off, as for the other animated marks (`ValueAnimator.areAnimatorsEnabled`), it stays on [TOP_VIEW_STILL].
 */
@Composable
internal fun rememberTopViewProgress(): State<Float> {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    if (!animate) return remember { mutableFloatStateOf(TOP_VIEW_STILL) }
    return rememberInfiniteTransition(label = "top view").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = TOP_VIEW_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "top view",
    )
}

/** The animated top view as a block of the set-up guide, as wide as its parent; [accent] is the phone's colour. */
@Composable
internal fun TopView(modifier: Modifier = Modifier, accent: Color = HoldoutColors.Accent) {
    val progress = rememberTopViewProgress()
    Canvas(
        modifier
            .aspectRatio(TOP_VIEW_WIDTH / TOP_VIEW_CONTENT_HEIGHT)
            .semantics { contentDescription = TOP_VIEW_DESCRIPTION },
    ) {
        scale(size.width / TOP_VIEW_WIDTH, pivot = Offset.Zero) { drawTopView(progress.value, accent) }
    }
}
