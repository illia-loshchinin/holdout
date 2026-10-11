package holdout.app.tour

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import holdout.app.ui.theme.HoldoutColors
import java.io.InputStream

/** How a shape is painted: the Rig's colours for the body, the app's own for the UI around it ([FigureColor]). */
internal class FigureStyle(
    val fill: FigureColor? = null,
    val fillOpacity: Float = 1f,
    val stroke: FigureColor? = null,
    val strokeOpacity: Float = 1f,
    val strokeWidth: Float = 1f,
    val dash: FloatArray? = null,
    val roundCap: Boolean = false,
    val roundJoin: Boolean = false,
    val opacity: Float = 1f,
)

/** A colour from the data file: an ARGB value of the figure's own, or a token the app resolves in its theme. */
internal sealed interface FigureColor {
    val color: Color

    class Argb(argb: Long) : FigureColor {
        override val color = Color(argb.toInt())
    }

    data object Accent : FigureColor {
        override val color get() = HoldoutColors.Accent
    }

    data object Success : FigureColor {
        override val color get() = HoldoutColors.Success
    }
}

/** One thing to draw, in the board's 400 × 240 units. */
internal sealed interface FigureOp {
    class Push(val tx: Float, val ty: Float, val rotation: Float) : FigureOp

    data object Pop : FigureOp

    class PathOp(val data: String, val style: FigureStyle) : FigureOp {
        /** Parsed on first draw, so the 36 poses cost nothing until they are shown. */
        val path: Path by lazy { PathParser().parsePathString(data).toPath() }
    }

    class Rect(val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float, val style: FigureStyle) : FigureOp

    class Circle(val cx: Float, val cy: Float, val r: Float, val style: FigureStyle) : FigureOp

    class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rotation: Float, val style: FigureStyle) : FigureOp

    class Text(
        val x: Float,
        val y: Float,
        val rotation: Float,
        val size: Float,
        val weight: Int,
        val spacing: Float,
        val middle: Boolean,
        val centredOnY: Boolean,
        val mono: Boolean,
        val style: FigureStyle,
        val text: String,
    ) : FigureOp
}

/**
 * The tour figure's data, read from `assets/tour_figure.txt`, which
 * `scripts/gen-tour-figure.py` generates from the canvas board — the Holdout
 * Rig's 36 poses and the scene around them. Never redrawn by hand (DS TourFigure).
 */
internal class FigureData(
    /** The floor, the camera's cone, the stool, the phone and the "from above" inset. */
    val scene: List<FigureOp>,
    /** The rectangle the body fits in: dashed accent until the whole body is in frame, then success. */
    val outline: FigureOp.Rect,
    /** The "knees in view" tag. */
    val knee: List<FigureOp>,
    val frames: List<List<FigureOp>>,
    val schedule: FrameSchedule,
) {
    companion object {
        fun parse(input: InputStream): FigureData = parse(input.bufferedReader(Charsets.UTF_8).lineSequence())

        fun parse(lines: Sequence<String>): FigureData {
            val paths = HashMap<Int, String>()
            val spans = ArrayList<FrameSpan>()
            val sections = LinkedHashMap<String, MutableList<FigureOp>>()
            var current: MutableList<FigureOp>? = null
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val tag = line[0]
                val rest = line.drop(2)
                when (tag) {
                    'V' -> require(rest.trim() == "1") { "tour figure data version $rest" }
                    'D' -> paths[rest.substringBefore(' ').toInt()] = rest.substringAfter(' ')
                    'K' -> rest.split(' ').let { spans += FrameSpan(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
                    'N' -> current = mutableListOf<FigureOp>().also { sections[rest.trim()] = it }
                    else -> requireNotNull(current) { "op before a section: $line" } += op(tag, rest, paths)
                }
            }
            val outline = sections.getValue("outline").single() as FigureOp.Rect
            val frames = List(FRAME_COUNT) { sections.getValue("frame$it") }
            return FigureData(sections.getValue("scene"), outline, sections.getValue("knee"), frames, FrameSchedule(spans))
        }

        const val FRAME_COUNT = 36

        private fun op(tag: Char, rest: String, paths: Map<Int, String>): FigureOp {
            if (tag == 'Z') return FigureOp.Pop
            if (tag == 'T') {
                val (head, text) = rest.split(" | ", limit = 2)
                val f = head.split(' ')
                return FigureOp.Text(
                    x = f[0].toFloat(), y = f[1].toFloat(), rotation = f[2].toFloat(), size = f[3].toFloat(), weight = f[4].toFloat().toInt(), spacing = f[5].toFloat(),
                    middle = f[6][0] == 'm', centredOnY = f[6][1] == 'c', mono = f[7] == "mono", style = style(f.drop(8)), text = text,
                )
            }
            val f = rest.split(' ')
            return when (tag) {
                'G' -> FigureOp.Push(f[0].toFloat(), f[1].toFloat(), f[2].toFloat())
                'P' -> FigureOp.PathOp(paths.getValue(f[0].toInt()), style(f.drop(1)))
                'R' -> FigureOp.Rect(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), style(f.drop(5)))
                'C' -> FigureOp.Circle(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), style(f.drop(3)))
                'E' -> FigureOp.Ellipse(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), style(f.drop(5)))
                else -> error("unknown tour figure op: $tag $rest")
            }
        }

        private fun style(tokens: List<String>): FigureStyle {
            var fill: FigureColor? = null
            var fillOpacity = 1f
            var stroke: FigureColor? = null
            var strokeOpacity = 1f
            var strokeWidth = 1f
            var dash: FloatArray? = null
            var roundCap = false
            var roundJoin = false
            var opacity = 1f
            for (token in tokens) {
                if (token.isEmpty()) continue
                val value = token.substringAfter('=')
                when (token.substringBefore('=')) {
                    "f" -> fill = colour(value)
                    "fo" -> fillOpacity = value.toFloat()
                    "s" -> stroke = colour(value)
                    "so" -> strokeOpacity = value.toFloat()
                    "sw" -> strokeWidth = value.toFloat()
                    "sd" -> dash = value.split('/').map { it.toFloat() }.toFloatArray()
                    "lc" -> roundCap = value == "round"
                    "lj" -> roundJoin = value == "round"
                    "o" -> opacity = value.toFloat()
                }
            }
            return FigureStyle(fill, fillOpacity, stroke, strokeOpacity, strokeWidth, dash, roundCap, roundJoin, opacity)
        }

        private fun colour(value: String): FigureColor = when (value) {
            "@accent" -> FigureColor.Accent
            "@success" -> FigureColor.Success
            else -> FigureColor.Argb(value.removePrefix("#").toLong(16))
        }
    }
}
