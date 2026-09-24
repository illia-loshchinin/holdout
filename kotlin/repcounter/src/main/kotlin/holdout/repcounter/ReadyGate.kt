package holdout.repcounter

/**
 * What starts a sitting: the four pre-flight pills green **and** the member
 * held at the top of a push-up, together, for about a second. No tap.
 *
 * **This replaces the palm liveness check** (was FR-020, PRD §8.3). The palm
 * asked someone already face-down on the floor to lift a hand off it, which is
 * the same mistake as the "I'm ready" button it followed. Being in position is
 * a better readiness signal than a gesture, because it is the position the
 * counting is about to measure.
 *
 * **What that costs is written down, not glossed over.** The palm was a
 * liveness check: it confirmed a live person was present at the start of every
 * sitting, and that the person banking the first ten reps was the one who set
 * the phone up. This confirms a body in push-up position and nothing more — it
 * cannot tell a live person from a photograph held in front of the lens, or one
 * member from another. Audio anti-spoofing in a later phase is the real fix
 * (spec OD-3, README known weaknesses).
 *
 * **The ready position is the rule's own top**, from the same [RepRules] the
 * counter uses: elbow past `topTriggerDegrees`, body line within
 * `bodyLineToleranceDegrees` of straight — or, head-on, where the body line
 * cannot be seen, arms straight in the picture and the torso down
 * (`ViewReading`). There is deliberately no second idea of what a plank is — spike S2 retunes both at once, and they can never drift
 * apart. It also means [RepCounter] begins already at the top rather than
 * hunting for one.
 */
class ReadyGate(
    /** How long the pills and the position must hold together. */
    private val holdMillis: Long = 1_000,
    private val rules: RepRules = RepRules(),
    /** Staked sittings start from the toes; the Daily 25 may start from the knees. */
    private val stakes: Stakes = Stakes.DAILY,
) {
    /**
     * The rules in force for the view the camera currently has. Goes through
     * [forView] rather than using [rules] directly, so that when spike S2 gives
     * the front view its own trigger set, the ready position moves with the
     * counting rule instead of drifting away from it.
     */
    private fun rulesFor(view: CameraView?) = rules.forView(view ?: CameraView.SIDE)

    private var heldSinceMillis: Long? = null

    /** How far through the hold, 0..1 — for a progress ring on the screen. */
    var progress: Float = 0f
        private set

    /**
     * One frame. [frame] is the **world** landmarks, as every angle is.
     * Returns true on the frame the sitting should start.
     *
     * Frames the model could not read neither advance the hold nor break it:
     * the same drop-don't-score rule [RepCounter] follows, so somebody walking
     * past the camera costs a frame rather than the whole countdown.
     *
     * [image] is the same frame as the picture saw it, which a head-on view
     * reads the elbow from — exactly as [RepCounter] will once counting starts.
     */
    fun onFrame(
        frame: PoseFrame,
        nowMillis: Long,
        pillsGreen: Boolean,
        view: CameraView? = null,
        image: ImagePose? = null,
    ): Boolean {
        if (!pillsGreen) {
            reset()
            return false
        }

        val measuredFrom = view ?: CameraView.SIDE
        val reading = ViewReading.of(measuredFrom, frame, image) ?: return false

        if (!isAtTop(measuredFrom, reading, rulesFor(view))) {
            reset()
            return false
        }

        val since = heldSinceMillis ?: nowMillis.also { heldSinceMillis = it }
        val held = nowMillis - since
        progress = (held.toFloat() / holdMillis).coerceIn(0f, 1f)
        return held >= holdMillis
    }

    fun reset() {
        heldSinceMillis = null
        progress = 0f
    }

    private fun isAtTop(view: CameraView, reading: ViewReading, rules: RepRules): Boolean = reading.elbow >= rules.topTriggerDegrees &&
        postureHeld(view, reading, reading.posture, rules) &&
        (stakes != Stakes.STAKED || kneesOffFloor(reading.knee, rules))
}
