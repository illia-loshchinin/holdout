package holdout.repcounter

import kotlin.math.hypot

/**
 * The four environment checks that gate every sitting (FR-019, PRD §8.2).
 *
 * They run before **every** sitting, not only the day's first, and the sitting
 * cannot start until all four are green. Each failure says exactly what to fix
 * rather than that something is wrong.
 *
 * **No audio.** There is no fifth check and there never was one here: the app
 * does not request the microphone at all (FR-022a), and the missing
 * anti-spoofing filter is named in the README as a known weakness.
 */
enum class PreflightCheck(val label: String, val fix: String) {
    LIGHTING(
        label = "Lighting",
        fix = "Add light",
    ),
    WHOLE_BODY(
        label = "Whole body in frame",
        fix = "Step back so your whole body is visible",
    ),
    CAMERA_ANGLE(
        label = "Camera angle",
        fix = "Put the phone to your side, not in front of you",
    ),
    BACKGROUND_STILL(
        label = "Background still",
        fix = "Find a spot with nothing moving behind you",
    ),
}

/** One pill on the pre-flight screen, updating in real time. */
data class PreflightStatus(val check: PreflightCheck, val passing: Boolean) {
    /** What the pill says when it is red; null when green. */
    val fix: String? get() = if (passing) null else check.fix
}

data class PreflightReport(val statuses: List<PreflightStatus>) {
    val canStart: Boolean get() = statuses.all { it.passing }
    val fixes: List<String> get() = statuses.mapNotNull { it.fix }
}

/**
 * What one frame of the live preview tells us. The app measures [brightness] and
 * [backgroundMotion] from the image; this module only decides.
 *
 * **Two sets of landmarks, on purpose.** MediaPipe emits both, and they answer
 * different questions:
 *
 * - [world] is metres, origin at the hip midpoint. Rotation-invariant, so it is
 *   what every *angle* is measured from — an elbow is the same elbow whichever
 *   way the phone lies (research R4). It cannot tell you where the phone is.
 * - [image] is normalised frame coordinates, 0..1. It is the only one that
 *   knows how the body sits *in the picture*, which is exactly what the camera
 *   angle check is asking.
 *
 * Using `world` for the camera check would be asking the wrong question of the
 * right data: it would pass a phone propped anywhere at all.
 */
data class PreflightInput(
    /** Mean luma of the frame, 0..1. */
    val brightness: Float,
    /**
     * How much of the frame moved that is not the person, 0..1 — the fraction of
     * sampled pixels outside the pose's bounding box that changed since the
     * previous frame.
     */
    val backgroundMotion: Float,
    /** World landmarks: metres, hip-centred. Null when the model found nobody. */
    val world: PoseFrame?,
    /** Image landmarks: normalised 0..1. Null when the model found nobody. */
    val image: PoseFrame? = world,
)

class Preflight(
    private val minBrightness: Float = 0.25f,
    private val maxBrightness: Float = 0.92f,
    private val maxBackgroundMotion: Float = 0.12f,
    /**
     * How wide the shoulders may look, relative to the torso's length in the
     * picture, before the camera counts as front-on.
     *
     * Deliberately generous. A check that blocks a correctly positioned member
     * is worse than one that passes a slightly-off one, because the rep counter
     * already refuses reps it cannot measure — a bad angle should cost reps,
     * not access. Spike S2 tightens this against real video.
     */
    private val maxShoulderSpanRatio: Float = 0.8f,
) {

    fun evaluate(input: PreflightInput): PreflightReport = PreflightReport(
        listOf(
            PreflightStatus(
                PreflightCheck.LIGHTING,
                input.brightness in minBrightness..maxBrightness,
            ),
            PreflightStatus(PreflightCheck.WHOLE_BODY, wholeBodyVisible(input.world)),
            PreflightStatus(PreflightCheck.CAMERA_ANGLE, cameraIsSideOn(input.image)),
            PreflightStatus(
                PreflightCheck.BACKGROUND_STILL,
                input.backgroundMotion <= maxBackgroundMotion,
            ),
        ),
    )

    /**
     * Every keypoint the rule reads has to be clearly present — both arms, both
     * hips, both ankles. Half a body in frame is the most common reason a count
     * goes wrong, and it is invisible to the member until the reps do not
     * register.
     */
    private fun wholeBodyVisible(frame: PoseFrame?): Boolean {
        if (frame == null) return false
        val required = intArrayOf(
            PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER,
            PoseIndex.LEFT_ELBOW, PoseIndex.RIGHT_ELBOW,
            PoseIndex.LEFT_WRIST, PoseIndex.RIGHT_WRIST,
            PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP,
            PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE,
        )
        return required.all { (frame[it]?.presence ?: 0f) >= Angles.PRESENCE_THRESHOLD }
    }

    /**
     * Is the camera looking at the member from the **side**?
     *
     * That is the only thing this check has ever been about: a front-on phone
     * cannot see elbow flexion at all, so the counter would score nothing it
     * could measure (research R4).
     *
     * The signal is how wide the shoulders look against how long the torso
     * looks, both measured in the picture. Side-on stacks one shoulder behind
     * the other and the span collapses towards zero; front-on leaves them
     * spread right across the frame.
     *
     * **Both are distances, so the answer does not depend on which way up the
     * phone is.** The check this replaced compared the shoulder-to-hip line
     * against the horizontal, which sounds equivalent and is not: frames are
     * rotated to the display's orientation, the app is portrait-locked, and a
     * phone propped in landscape therefore made a perfectly positioned body
     * read as vertical. The only way to pass it was to lie along one axis of
     * the frame.
     */
    private fun cameraIsSideOn(frame: PoseFrame?): Boolean {
        if (frame == null) return false
        val leftShoulder = frame[PoseIndex.LEFT_SHOULDER]
        val rightShoulder = frame[PoseIndex.RIGHT_SHOULDER]
        val shoulder = midpoint(leftShoulder, rightShoulder) ?: return true
        val hip = midpoint(frame[PoseIndex.LEFT_HIP], frame[PoseIndex.RIGHT_HIP]) ?: return true

        val torsoLength = hypot(shoulder.first - hip.first, shoulder.second - hip.second)

        // Too small to judge — the member is far away, or folded towards the
        // lens. `WHOLE_BODY` already owns "the model cannot see you"; this pill
        // must not fail red for the same reason a second time.
        if (torsoLength < MIN_TORSO_LENGTH) return true

        // One shoulder occluded is the strongest possible side-on signal.
        if (leftShoulder == null || rightShoulder == null) return true
        if (leftShoulder.presence < Angles.PRESENCE_THRESHOLD ||
            rightShoulder.presence < Angles.PRESENCE_THRESHOLD
        ) {
            return true
        }

        val shoulderSpan = hypot(
            leftShoulder.x - rightShoulder.x,
            leftShoulder.y - rightShoulder.y,
        )
        return shoulderSpan <= maxShoulderSpanRatio * torsoLength
    }

    private companion object {
        /** Below this the torso is too short in frame for the ratio to mean anything. */
        const val MIN_TORSO_LENGTH = 0.02f
    }

    private fun midpoint(a: Landmark?, b: Landmark?): Pair<Float, Float>? {
        val present = listOfNotNull(a, b).filter { it.presence >= Angles.PRESENCE_THRESHOLD }
        if (present.isEmpty()) return null
        return present.map { it.x }.average().toFloat() to present.map { it.y }.average().toFloat()
    }
}
