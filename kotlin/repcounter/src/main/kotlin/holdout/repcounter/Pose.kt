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
 * Coordinates are **3D world landmarks** — metres, origin at the hip midpoint —
 * not image coordinates, so an angle does not change when the phone moves
 * (PRD §8.4, research R4).
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

/** MediaPipe pose landmark indices, for the joints the rule names. */
object PoseIndex {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    const val COUNT = 33
}

/** Which side of the body a measurement came from. */
enum class Side { LEFT, RIGHT }
