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
        label = "Camera view",
        fix = "Move the phone so it can see all of you",
    ),
    BACKGROUND_STILL(
        label = "Background still",
        fix = "Find a spot with nothing moving behind you",
    ),
}

/** One pill on the pre-flight screen, updating in real time. */
data class PreflightStatus(
    val check: PreflightCheck,
    val passing: Boolean,
    /** A fix more specific than the check's own, when the reason it failed calls for one. */
    private val specificFix: String? = null,
) {
    /** What the pill says when it is red; null when green. */
    val fix: String? get() = if (passing) null else specificFix ?: check.fix
}

data class PreflightReport(
    val statuses: List<PreflightStatus>,
    /** Which way the camera is looking at the member; null when it cannot tell. */
    val view: CameraView? = null,
) {
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
     * Shoulder span against torso length, at or below which the camera is
     * beside the member rather than in front of them.
     */
    private val sideRatio: Float = 0.5f,
    /**
     * At or above which it is in front of them. Between the two is oblique —
     * the 45° placement staked sittings use. Set from two recordings: at 45°
     * the ratio stayed at or under 1.35 (95th percentile), head-on it never
     * fell below 1.73 (5th). The old 0.9 called most 45° frames head-on, which
     * refused a correctly placed phone.
     */
    private val frontRatio: Float = 1.55f,
    /**
     * A staked sitting accepts the side view only, and its camera pill says so
     * in those words — a member told only "move the phone" would move it and
     * still be refused ([Stakes]).
     */
    private val stakes: Stakes = Stakes.DAILY,
) {

    fun evaluate(input: PreflightInput): PreflightReport {
        val view = classifyView(input.image)
        return PreflightReport(
            statuses = listOf(
                PreflightStatus(
                    PreflightCheck.LIGHTING,
                    input.brightness in minBrightness..maxBrightness,
                ),
                PreflightStatus(PreflightCheck.WHOLE_BODY, wholeBodyVisible(input.world)),
                // One placement per stakes ([Stakes]): head-on for the Daily 25,
                // 45° when money is on it. Anywhere else, the pill says which.
                PreflightStatus(
                    PreflightCheck.CAMERA_ANGLE,
                    passing = stakes.accepts(view),
                    specificFix = if (view != null) stakes.cameraFix else null,
                ),
                PreflightStatus(
                    PreflightCheck.BACKGROUND_STILL,
                    input.backgroundMotion <= maxBackgroundMotion,
                ),
            ),
            view = view,
        )
    }

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
     * Which way is the camera looking at the member?
     *
     * The signal is how wide the shoulders look against how long the torso
     * looks, both measured in the picture. Side-on stacks one shoulder behind
     * the other and the span collapses; head-on leaves them spread right across
     * the frame, and foreshortens the torso as well, which pushes the ratio the
     * same way twice.
     *
     * **Both are distances, so the answer does not depend on which way up the
     * phone is.** An earlier version of this check compared the shoulder-to-hip
     * line against the horizontal, which sounds equivalent and is not: frames
     * are rotated to the display's orientation, the app is portrait-locked, and
     * a phone propped in landscape therefore made a perfectly positioned body
     * read as vertical. The only way to pass it was to lie along one axis of
     * the frame.
     *
     * Returns null when there is not enough of a body to judge — which is the
     * only thing the camera-view check now fails on.
     */
    fun classifyView(frame: PoseFrame?): CameraView? {
        if (frame == null) return null
        val leftShoulder = frame[PoseIndex.LEFT_SHOULDER]
        val rightShoulder = frame[PoseIndex.RIGHT_SHOULDER]
        val shoulder = midpoint(leftShoulder, rightShoulder) ?: return null
        val hip = midpoint(frame[PoseIndex.LEFT_HIP], frame[PoseIndex.RIGHT_HIP]) ?: return null

        val torsoLength = hypot(shoulder.first - hip.first, shoulder.second - hip.second)
        if (torsoLength < MIN_TORSO_LENGTH) return null

        // One shoulder occluded by the other is the strongest side-on signal
        // there is: the model cannot see through a torso.
        if (leftShoulder == null || rightShoulder == null) return CameraView.SIDE
        if (leftShoulder.presence < Angles.PRESENCE_THRESHOLD ||
            rightShoulder.presence < Angles.PRESENCE_THRESHOLD
        ) {
            return CameraView.SIDE
        }

        val shoulderSpan = hypot(
            leftShoulder.x - rightShoulder.x,
            leftShoulder.y - rightShoulder.y,
        )
        val ratio = shoulderSpan / torsoLength
        return when {
            ratio <= sideRatio -> CameraView.SIDE
            ratio >= frontRatio -> CameraView.FRONT
            else -> CameraView.OBLIQUE
        }
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
