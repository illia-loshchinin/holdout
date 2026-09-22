package holdout.repcounter

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * The two angles the push-up rule is written in (FR-021, PRD §8.4):
 *
 *   elbow      shoulder–elbow–wrist
 *   body line  shoulder–hip–ankle; 180° is straight
 *
 * Both are measured from 3D world landmarks, so they describe the body rather
 * than its projection onto the camera (research R4).
 *
 * A frame whose joints are not clearly visible produces `null` rather than a
 * number. Scoring a guess is how a counter invents reps that never happened —
 * those frames are dropped, not scored (T048).
 */
object Angles {

    /**
     * How confident the model has to be in a landmark before it is allowed to
     * decide anything. MediaPipe's own examples treat presence below ~0.5 as
     * absent, and this is deliberately stricter.
     */
    const val PRESENCE_THRESHOLD = 0.6f

    /** Degrees in the angle at [b], between the rays b→a and b→c. */
    fun angleAt(a: Landmark, b: Landmark, c: Landmark): Float {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val abz = a.z - b.z
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val cbz = c.z - b.z

        val dot = abx * cbx + aby * cby + abz * cbz
        val magnitude = sqrt(abx * abx + aby * aby + abz * abz) *
            sqrt(cbx * cbx + cby * cby + cbz * cbz)
        if (magnitude == 0f) return Float.NaN

        // Floating point can push the quotient a hair outside acos's domain.
        val cosine = (dot / magnitude).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    private fun visible(vararg landmarks: Landmark?): Boolean = landmarks.all { it != null && it.presence >= PRESENCE_THRESHOLD }

    /**
     * The elbow angle for one side, or `null` when that arm is not clearly in
     * frame.
     */
    fun elbow(frame: PoseFrame, side: Side): Float? {
        val (s, e, w) = when (side) {
            Side.LEFT -> Triple(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST)
            Side.RIGHT -> Triple(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST)
        }
        val shoulder = frame[s]
        val elbow = frame[e]
        val wrist = frame[w]
        if (!visible(shoulder, elbow, wrist)) return null
        return angleAt(shoulder!!, elbow!!, wrist!!).takeIf { !it.isNaN() }
    }

    /** The shoulder–hip–ankle angle for one side, or `null` when it is not clearly in frame. */
    fun bodyLine(frame: PoseFrame, side: Side): Float? {
        val (s, h, a) = when (side) {
            Side.LEFT -> Triple(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_HIP, PoseIndex.LEFT_ANKLE)
            Side.RIGHT -> Triple(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_ANKLE)
        }
        val shoulder = frame[s]
        val hip = frame[h]
        val ankle = frame[a]
        if (!visible(shoulder, hip, ankle)) return null
        return angleAt(shoulder!!, hip!!, ankle!!).takeIf { !it.isNaN() }
    }

    /**
     * One number per frame from whichever sides are visible.
     *
     * Averaging both sides when both are present steadies the reading; falling
     * back to one side keeps a sitting filmed side-on — which is how the app
     * asks people to film it (PRD §8.2) — working at all, since the far arm is
     * frequently occluded by the near one.
     */
    private fun bothSides(left: Float?, right: Float?): Float? = when {
        left != null && right != null -> (left + right) / 2f
        else -> left ?: right
    }

    fun elbow(frame: PoseFrame): Float? = bothSides(elbow(frame, Side.LEFT), elbow(frame, Side.RIGHT))

    fun bodyLine(frame: PoseFrame): Float? = bothSides(bodyLine(frame, Side.LEFT), bodyLine(frame, Side.RIGHT))
}
