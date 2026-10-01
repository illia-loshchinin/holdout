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
enum class PreflightCheck(
    val label: String,
    val fix: String,
    /**
     * Shown and never blocking. Background motion is measured from a coarse
     * luma grid with a threshold set on no real homes yet, so a TV or a
     * passer-by must not keep a member from starting (decided 2026-09-26).
     */
    val advisory: Boolean = false,
) {
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
        advisory = true,
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
    /** Every gating check passes; an advisory one may still be red. */
    val canStart: Boolean get() = statuses.all { it.passing || it.check.advisory }
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
    /** [image] with the picture's size, which the near side is read from. Without it the staked knee check is skipped. */
    val imagePose: ImagePose? = null,
)

class Preflight(
    private val minBrightness: Float = 0.25f,
    private val maxBrightness: Float = 0.92f,
    private val maxBackgroundMotion: Float = DEFAULT_MAX_BACKGROUND_MOTION,
    /**
     * Shoulder span against torso length, at or below which the camera is
     * beside the member rather than in front of them.
     *
     * **0.25, provisional (2026-10-01).** At 0.5 a phone measured at 45° on
     * the floor read side-on in 12 % of counting frames and at 50° in 35 %
     * (ratio 5th percentile 0.46 and 0.36), which kept resetting the ready
     * hold. 55°, 60° and a square side-on hold are still to be recorded
     * (002 T034c); the line is set from them.
     */
    private val sideRatio: Float = 0.25f,
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
    /** Staked: how far inside the picture the near knee must be to start — the counter's own margin. */
    private val legEdgeMargin: Float = RepRules().legEdgeMargin,
) {

    fun evaluate(input: PreflightInput): PreflightReport {
        val view = classifyView(input.image, input.imagePose?.let { Aspect.xScale(it.width, it.height) } ?: 1f)
        return PreflightReport(
            statuses = listOf(
                PreflightStatus(
                    PreflightCheck.LIGHTING,
                    input.brightness in minBrightness..maxBrightness,
                ),
                wholeBody(input),
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
     * The whole-body pill, which the ready hold and the countdown both need
     * green. On top of every joint being present:
     *
     * - **the head clearly inside the picture** — the entry is held with the
     *   head in frame. Once counting starts the head is not read at all: a head
     *   that dips out at the bottom of a rep costs nothing;
     * - **staked: the near knee clear of the edges** by the counter's own
     *   margin. A knee out of frame is placed just inside the edge at full
     *   confidence, and a rep read from it is refused ([RepHint.LEGS_IN_VIEW]),
     *   so the sitting does not start that way.
     */
    private fun wholeBody(input: PreflightInput): PreflightStatus {
        fun failing(fix: String? = null) = PreflightStatus(PreflightCheck.WHOLE_BODY, passing = false, specificFix = fix)
        if (!wholeBodyVisible(input.world, input.image)) return failing()
        if (input.image != null && !Angles.headInFrame(input.image)) return failing(HEAD_FIX)
        val pose = input.imagePose
        if (stakes == Stakes.STAKED && pose != null) {
            // Two different problems, two different fixes: a knee the model
            // cannot find is hidden behind the body — turning a little away from
            // the phone shows it; one at the picture's edge needs more room.
            val side = Angles.nearSide(pose) ?: return failing(KNEES_TURN_FIX)
            // The side facing the phone is the side the phone is on; turning the
            // other way opens the angle and brings the near knee out.
            if (!Angles.kneeSeen(pose, side)) return failing(if (side == Side.RIGHT) KNEES_TURN_LEFT_FIX else KNEES_TURN_RIGHT_FIX)
            if (!Angles.kneeClearOfEdge(pose, side, legEdgeMargin)) return failing(KNEES_FIX)
        }
        return PreflightStatus(PreflightCheck.WHOLE_BODY, passing = true)
    }

    /**
     * Every keypoint the rule reads has to be clearly present — both arms, both
     * hips, both ankles. Half a body in frame is the most common reason a count
     * goes wrong, and it is invisible to the member until the reps do not
     * register.
     */
    private fun wholeBodyVisible(frame: PoseFrame?, image: PoseFrame?): Boolean {
        if (frame == null) return false
        val required = intArrayOf(
            // The head too: at the bottom of a rep it is the first thing to
            // leave a frame that was set up too tight.
            PoseIndex.NOSE,
            PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER,
            PoseIndex.LEFT_ELBOW, PoseIndex.RIGHT_ELBOW,
            PoseIndex.LEFT_WRIST, PoseIndex.RIGHT_WRIST,
            PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP,
            PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE,
        )
        val present = required.all { (frame[it]?.presence ?: 0f) >= Angles.PRESENCE_THRESHOLD }
        // Presence is the model's confidence, and it stays high for a joint it
        // extrapolated past the edge of a cropped picture. Only the joint's
        // place in the picture says it is really in view.
        val inPicture = image == null || required.all { i -> image[i]?.let { it.x in 0f..1f && it.y in 0f..1f } == true }
        return present && inPicture
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
     * **Both are distances in the model's normalised picture coordinates**,
     * where a portrait frame's x runs over 720 pixels and y over 1 280, so a
     * sideways distance counts for more than an upright one. That is kept on
     * purpose: head-on the shoulders run across the frame and the torso runs
     * up it, so the skew widens the gap between placements. On the phone's
     * sixteen recordings head-on read 1.4–1.8 at its 5th percentile against a
     * 45° median near 1.1; measured in pixels the two ranges overlap (head-on
     * 0.8–1.0 against 45° up to 1.6). The thresholds are set in these units.
     *
     * Distances rather than a direction: an earlier version of this check compared the shoulder-to-hip
     * line against the horizontal, which sounds equivalent and is not: frames
     * are rotated to the display's orientation, the app is portrait-locked, and
     * a phone propped in landscape therefore made a perfectly positioned body
     * read as vertical. The only way to pass it was to lie along one axis of
     * the frame.
     *
     * Returns null when there is not enough of a body to judge — which is the
     * only thing the camera-view check now fails on.
     */
    fun classifyView(frame: PoseFrame?, xScale: Float = 1f): CameraView? {
        val ratio = placementRatio(frame, xScale) ?: return null
        return when {
            ratio <= sideRatio -> CameraView.SIDE
            ratio >= frontRatio -> CameraView.FRONT
            else -> CameraView.OBLIQUE
        }
    }

    /**
     * What [classifyView] decides on: shoulder span over torso length, in the
     * 9:16 units the thresholds were set in. `0` when one shoulder hides the
     * other — the model cannot see through a torso, so that is side-on — and
     * null when there is not enough of a body to judge. Public for calibration
     * (`replayTraces`), which groups it by the placement a person labelled.
     */
    fun placementRatio(frame: PoseFrame?, xScale: Float = 1f): Float? {
        if (frame == null) return null
        val leftShoulder = frame[PoseIndex.LEFT_SHOULDER]
        val rightShoulder = frame[PoseIndex.RIGHT_SHOULDER]
        val shoulder = midpoint(leftShoulder, rightShoulder) ?: return null
        val hip = midpoint(frame[PoseIndex.LEFT_HIP], frame[PoseIndex.RIGHT_HIP]) ?: return null

        // x read as it was in the 9:16 picture the thresholds were set in ([Aspect]).
        val torsoLength = hypot(Aspect.calibratedX(shoulder.first - hip.first, xScale), shoulder.second - hip.second)
        if (torsoLength < MIN_TORSO_LENGTH) return null

        if (leftShoulder == null || rightShoulder == null) return 0f
        if (leftShoulder.presence < Angles.PRESENCE_THRESHOLD || rightShoulder.presence < Angles.PRESENCE_THRESHOLD) return 0f

        val shoulderSpan = hypot(
            Aspect.calibratedX(leftShoulder.x - rightShoulder.x, xScale),
            leftShoulder.y - rightShoulder.y,
        )
        return shoulderSpan / torsoLength
    }

    companion object {
        /** Share of the background ([BackgroundMotion]) allowed to change between frames. */
        const val DEFAULT_MAX_BACKGROUND_MOTION = 0.12f

        /** The whole-body pill's fix when the head is at or past the picture's edge. */
        const val HEAD_FIX = "Keep your head in the picture"

        /** Staked: the fix when the near knee is at or past the edge. */
        const val KNEES_FIX = "Keep your knees in the picture — move the phone further away"

        /** Staked: the near knee cannot be found — hidden behind the body — and which side faces the phone is unclear. */
        const val KNEES_TURN_FIX = "Turn your body a little away from the phone, so it sees your knees"

        /** Staked: as [KNEES_TURN_FIX], with the phone on the member's right — they turn left. */
        const val KNEES_TURN_LEFT_FIX = "Turn your body slightly to your left, away from the phone, so it sees your knees"

        /** Staked: as [KNEES_TURN_FIX], with the phone on the member's left — they turn right. */
        const val KNEES_TURN_RIGHT_FIX = "Turn your body slightly to your right, away from the phone, so it sees your knees"

        /** Below this the torso is too short in frame for the ratio to mean anything. */
        private const val MIN_TORSO_LENGTH = 0.02f
    }

    private fun midpoint(a: Landmark?, b: Landmark?): Pair<Float, Float>? {
        val present = listOfNotNull(a, b).filter { it.presence >= Angles.PRESENCE_THRESHOLD }
        if (present.isEmpty()) return null
        return present.map { it.x }.average().toFloat() to present.map { it.y }.average().toFloat()
    }
}
