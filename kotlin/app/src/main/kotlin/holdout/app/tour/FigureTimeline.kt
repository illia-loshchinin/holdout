package holdout.app.tour

/** The pose shown from [startMillis] up to [endMillis] of the 12 s loop. */
internal data class FrameSpan(val startMillis: Int, val endMillis: Int, val frame: Int)

/** Which of the Rig's poses is on screen when: the board's `hf0` … `hf35` keyframes, made contiguous. */
internal class FrameSchedule(private val spans: List<FrameSpan>) {
    /** The pose at [millis] into the loop (any time: it wraps). */
    fun frameAt(millis: Long): Int {
        val t = (((millis % FigureTimeline.LOOP_MILLIS) + FigureTimeline.LOOP_MILLIS) % FigureTimeline.LOOP_MILLIS).toInt()
        var low = 0
        var high = spans.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (spans[mid].startMillis <= t) low = mid else high = mid - 1
        }
        return spans[low].frame
    }
}

/**
 * What the UI around the figure shows at one moment of the loop (the board's keyframes,
 * in milliseconds): whether the body is in the outline, the checks and the hold,
 * the 3 · 2 · 1, the rep count, and how lit each of the three numbered steps is.
 */
internal data class FigureOverlay(
    /** The whole body is in the outline: it turns from dashed to solid. */
    val inFrame: Boolean,
    val kneesTag: Boolean,
    /** The panel of four checks and the hold is up. */
    val panel: Boolean,
    /** How many of the four checks have ticked. */
    val checksDone: Int,
    /** The check being read now (0–3), or -1. */
    val checking: Int,
    /** The hold's ring, 0 → 1. */
    val holdProgress: Float,
    val holdDone: Boolean,
    /** 3, 2, 1 while the countdown runs. */
    val countdown: Int?,
    /** The rep count once the checks have cleared, or null before. */
    val reps: Int?,
    /** The opacity of steps 1–3: the one the figure is on is full. */
    val stepAlpha: List<Float>,
)

/** The 12 s loop of the tour figure (DS TourFigure), as the canvas keyframes have it. */
internal object FigureTimeline {
    const val LOOP_MILLIS = 12_000

    /** The four checks tick one after another, 400 ms each, from here. */
    private const val CHECKS_START = 3_500
    private const val CHECK_MILLIS = 400
    private const val IN_FRAME = 4_300
    private const val KNEES_END = 5_300
    private const val HOLD_START = 5_100
    private const val HOLD_END = 6_600
    private const val PANEL_END = 7_000
    private val COUNTDOWN_ENDS = listOf(5_600, 6_100, 6_600)
    private val REP_STARTS = listOf(8_400, 9_800, 11_200)

    private const val DIM = 0.35f
    private val STEP_1 = listOf(0 to 1f, 800 to 1f, 1_249 to DIM, LOOP_MILLIS to DIM)
    private val STEP_2 = listOf(0 to DIM, 800 to DIM, 1_249 to 1f, 3_500 to 1f, 3_799 to DIM, LOOP_MILLIS to DIM)
    private val STEP_3 = listOf(0 to DIM, 3_500 to DIM, 3_799 to 1f, LOOP_MILLIS to 1f)

    /** Reduced motion, or no animation: the top pose with the checks finished, nothing moving. */
    val still = FigureOverlay(
        inFrame = true,
        kneesTag = false,
        panel = true,
        checksDone = 4,
        checking = -1,
        holdProgress = 1f,
        holdDone = true,
        countdown = null,
        reps = null,
        stepAlpha = listOf(1f, 1f, 1f),
    )

    /** The pose the figure holds when nothing moves. */
    const val STILL_FRAME = 28

    fun at(millis: Long): FigureOverlay {
        val t = (((millis % LOOP_MILLIS) + LOOP_MILLIS) % LOOP_MILLIS).toInt()
        val checks = (0 until 4).count { t >= CHECKS_START + CHECK_MILLIS * (it + 1) }
        val checking = (0 until 4).firstOrNull { t >= CHECKS_START + CHECK_MILLIS * it && t < CHECKS_START + CHECK_MILLIS * (it + 1) } ?: -1
        return FigureOverlay(
            inFrame = t >= IN_FRAME,
            kneesTag = t in IN_FRAME until KNEES_END,
            panel = t in CHECKS_START until PANEL_END,
            checksDone = checks,
            checking = checking,
            holdProgress = when {
                t < HOLD_START -> 0f
                t >= HOLD_END -> 1f
                else -> (t - HOLD_START).toFloat() / (HOLD_END - HOLD_START)
            },
            holdDone = t >= HOLD_END,
            countdown = when {
                t < HOLD_START || t >= HOLD_END -> null
                t < COUNTDOWN_ENDS[0] -> 3
                t < COUNTDOWN_ENDS[1] -> 2
                else -> 1
            },
            reps = if (t < PANEL_END) null else REP_STARTS.count { t >= it },
            stepAlpha = listOf(interpolate(STEP_1, t), interpolate(STEP_2, t), interpolate(STEP_3, t)),
        )
    }

    private fun interpolate(keys: List<Pair<Int, Float>>, t: Int): Float {
        val i = keys.indexOfLast { it.first <= t }.coerceAtLeast(0)
        val (t0, v0) = keys[i]
        val (t1, v1) = keys.getOrElse(i + 1) { keys[i] }
        if (v0 == v1 || t1 == t0) return v0
        return v0 + (v1 - v0) * (t - t0).toFloat() / (t1 - t0)
    }
}
