package holdout.repcounter

/** Where the member is in the picture, normalised 0..1 — the part of the frame that is not background. */
data class BodyBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    companion object {
        /**
         * The box around every clearly present landmark, widened by [pad] of
         * the frame on each side: limbs are thicker than the points on them.
         * Null when nobody is in the picture.
         */
        fun of(image: PoseFrame, pad: Float = 0.08f): BodyBox? {
            val present = image.landmarks.filter { it.presence >= Angles.PRESENCE_THRESHOLD }
            if (present.isEmpty()) return null
            return BodyBox(
                left = present.minOf { it.x } - pad,
                top = present.minOf { it.y } - pad,
                right = present.maxOf { it.x } + pad,
                bottom = present.maxOf { it.y } + pad,
            )
        }
    }
}

/**
 * The "Background still" pre-flight check's number (FR-019): the share of the
 * background that changed since the last frame, smoothed over a few frames.
 *
 * The analyser hands over a coarse grid of luma values, one per cell. A cell
 * counts as changed when it moved by more than [cellChange] once the whole
 * frame's shift is taken out — auto-exposure brightening everything is not
 * motion. Cells inside the member's own [BodyBox] are skipped: they are
 * supposed to move while the member gets into position.
 */
class BackgroundMotion(
    private val cellChange: Float = 0.08f,
    /** Weight of the newest frame in the running figure. */
    private val smoothing: Float = 0.3f,
) {
    private var previous: FloatArray? = null
    private var previousCols = 0
    private var smoothed = 0f

    // Reused frame to frame: this runs on the camera thread thirty times a
    // second, next to the pose model, and allocates nothing once warm.
    private var differences = FloatArray(0)
    private var sorted = FloatArray(0)

    fun update(grid: FloatArray, cols: Int, rows: Int, body: BodyBox?): Float {
        require(grid.size == cols * rows) { "a $cols×$rows grid has ${cols * rows} cells, got ${grid.size}" }
        val last = previous
        if (last == null || last.size != grid.size || previousCols != cols) {
            previous = grid.copyOf()
            previousCols = cols
            differences = FloatArray(grid.size)
            sorted = FloatArray(grid.size)
            smoothed = 0f
            return 0f
        }

        var n = 0
        for (i in grid.indices) {
            val x = (i % cols + 0.5f) / cols
            val y = (i / cols + 0.5f) / rows
            if (body?.contains(x, y) != true) differences[n++] = grid[i] - last[i]
        }
        grid.copyInto(last)
        if (n == 0) return smoothed

        // The median difference is the whole frame's exposure shift. A primitive
        // sort of a reused buffer: no boxing, no garbage.
        differences.copyInto(sorted, endIndex = n)
        java.util.Arrays.sort(sorted, 0, n)
        val shift = sorted[n / 2]
        var changed = 0
        for (k in 0 until n) if (kotlin.math.abs(differences[k] - shift) > cellChange) changed++
        smoothed += smoothing * (changed.toFloat() / n - smoothed)
        return smoothed
    }
}
