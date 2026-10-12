package holdout.app.sitting

/**
 * When the pre-flight tip sheet is offered (screen map S3, Gap 1): in one sitting, once the checks
 * have been short of all green for [SHORT_OF_GREEN_MILLIS] in total, or the [FLIPS]th time a check
 * that was green turns red, whichever comes first. From then on it stays offered until the sitting
 * ends; a new sitting builds a new one, so nothing carries over.
 *
 * Pure: it reads the time off the frames it is fed, never a clock of its own. The pills are the five
 * as the screen draws them, in order, true when green. "All green" is every pill, the advisory
 * background one too, as the screen shows them.
 */
internal class PreflightTipsOffer {
    /** The tip button is shown. Never goes back to false within a sitting. */
    var offered: Boolean = false
        private set

    private var shortOfGreenMillis = 0L
    private var flips = 0
    private var last: List<Boolean>? = null
    private var lastMillis = 0L

    /**
     * One pre-flight frame at [nowMillis]. [reading] is true while the set-up guide covers the
     * screen: nobody is working on the checks then, so that time is not counted (flips still are).
     */
    fun onReport(nowMillis: Long, pills: List<Boolean>, reading: Boolean) {
        val before = last
        if (before != null) {
            val elapsed = nowMillis - lastMillis
            // The time since the previous frame was spent as that frame was: short of green, or not.
            // A pause (the camera restarting) is no time at all, and neither is a clock that went back.
            if (!reading && !before.all { it } && elapsed in 0..MAX_GAP_MILLIS) shortOfGreenMillis += elapsed
            flips += pills.indices.count { i -> before.getOrNull(i) == true && !pills[i] }
        }
        last = pills
        lastMillis = nowMillis
        if (shortOfGreenMillis >= SHORT_OF_GREEN_MILLIS || flips >= FLIPS) offered = true
    }

    private companion object {
        const val SHORT_OF_GREEN_MILLIS = 30_000L
        const val FLIPS = 3

        /** Frames come every ~70 ms; a gap past this is a stall, not a member working on the checks. */
        const val MAX_GAP_MILLIS = 2_000L
    }
}
