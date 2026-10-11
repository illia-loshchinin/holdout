package holdout.repcounter

/**
 * What the rep counter consumes: one frame of pose landmarks, and nothing that
 * knows about MediaPipe, Android or a camera.
 *
 * The module is plain JVM on purpose (research R12, T049): the push-up rule is
 * tested against recorded traces, including deliberate cheats, without a phone.
 * The app adapts MediaPipe's `PoseLandmarkerResult` into these types at the
 * edge; everything below is arithmetic.
 *
 * A [PoseFrame] holds either MediaPipe's **3D world landmarks** — metres,
 * origin at the hip midpoint — or, wrapped in [ImagePose], its image
 * landmarks. On a phone the angles are read from the image (`ViewReading`):
 * the world estimate's depth is the model's weakest output (002 research D1,
 * superseding research R4's world-landmark bullet). World landmarks alone are
 * what the synthetic traces carry.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    /** The model's confidence that this landmark exists in frame, 0..1. */
    val presence: Float,
)

/**
 * One frame. `landmarks` is indexed by [PoseIndex]; MediaPipe's pose model emits
 * 33 of them.
 */
data class PoseFrame(
    val timestampMillis: Long,
    val landmarks: List<Landmark>,
) {
    operator fun get(index: Int): Landmark? = landmarks.getOrNull(index)
}

/**
 * The same instant as the picture sees it: MediaPipe's **image** landmarks,
 * normalised 0..1 across each axis, and the size of the frame they came from.
 *
 * The size is not decoration. x and y are normalised to different lengths on
 * a portrait frame, so an angle measured in raw normalised units is squashed —
 * [Angles.elbowInImage] scales both back to pixels before measuring anything.
 */
data class ImagePose(
    val frame: PoseFrame,
    val width: Int,
    val height: Int,
)

/**
 * The picture shape the placement thresholds were measured in: portrait 9:16
 * (width ÷ height), the analysis frame until 29 Sep. The camera now gives the
 * sensor's full 3:4, the same height and a third wider. Checks that were
 * tuned in normalised picture units — the placement ratio, the guide's
 * outline — read x through [calibratedX] so they keep meaning what they were
 * measured to mean; checks about the picture's **edges** use the real frame,
 * which is what the wider view is for.
 */
object Aspect {
    const val CALIBRATED = 9f / 16f

    /** How much narrower a normalised x-length is here than it was in 9:16: 1 there, 0.75 at 3:4. */
    fun xScale(width: Int, height: Int): Float = if (width <= 0 || height <= 0) 1f else CALIBRATED / (width.toFloat() / height)

    /** A normalised x-length here, as it would have read in the 9:16 picture. */
    fun calibratedX(dx: Float, xScale: Float): Float = dx / xScale
}

/** MediaPipe pose landmark indices, for the joints the rule names. */
object PoseIndex {
    /** The head, as MediaPipe marks it: the nose. */
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    const val COUNT = 33
}

/** Which side of the body a measurement came from. */
enum class Side { LEFT, RIGHT }
