package holdout.repcounter

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The two angles the push-up rule is written in (FR-021, PRD §8.4):
 *
 *   elbow      shoulder–elbow–wrist
 *   body line  shoulder–hip–ankle; 180° is straight
 *
 * On a phone they are read **in the picture**: at 45° on the near side
 * ([nearSide]), head-on the elbow from both arms and the body line replaced
 * by the torso check. The 3D world versions are what the synthetic traces use,
 * and what the app read before two recordings showed the picture is better
 * (`ViewReading`).
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

    // --- In the picture, on the side nearer the lens (`ViewReading`) -----------

    private val ARM = mapOf(
        Side.LEFT to Triple(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST),
        Side.RIGHT to Triple(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST),
    )
    private val LEG = mapOf(
        Side.LEFT to Triple(PoseIndex.LEFT_HIP, PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE),
        Side.RIGHT to Triple(PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_KNEE, PoseIndex.RIGHT_ANKLE),
    )
    private val TRUNK = mapOf(
        Side.LEFT to Triple(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_HIP, PoseIndex.LEFT_ANKLE),
        Side.RIGHT to Triple(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_ANKLE),
    )

    /**
     * The side of the body nearer the lens: the one whose clearly visible limbs
     * are **longer in the picture**. Nearer is bigger, and the far limbs are
     * partly hidden behind the body, so the model draws them short or folded.
     * `null` when neither side shows a single clear limb.
     */
    fun nearSide(image: ImagePose): Side? {
        fun length(side: Side): Float {
            var total = 0f
            for ((a, b, c) in listOf(ARM.getValue(side), LEG.getValue(side))) {
                val pa = image.frame[a]
                val pb = image.frame[b]
                val pc = image.frame[c]
                if (!visible(pa, pb, pc)) continue
                total += pa!!.distanceTo(pb!!, image) + pb.distanceTo(pc!!, image)
            }
            return total
        }
        val left = length(Side.LEFT)
        val right = length(Side.RIGHT)
        return when {
            left == 0f && right == 0f -> null
            left >= right -> Side.LEFT
            else -> Side.RIGHT
        }
    }

    /** Shoulder–elbow–wrist on [side], as the picture shows it. */
    fun elbowInImage(image: ImagePose, side: Side): Float? = inImage(image, ARM.getValue(side))

    /** Shoulder–hip–ankle on [side], as the picture shows it. 180° is straight. */
    fun bodyLineInImage(image: ImagePose, side: Side): Float? = inImage(image, TRUNK.getValue(side))

    /**
     * Hip–knee–ankle on [side], as the picture shows it. From the side or at
     * 45° the leg bends in a plane turned towards the lens, so a bent knee
     * cannot project straight; head-on it can, which is one reason head-on is
     * never read ([Stakes]).
     */
    fun kneeInImage(image: ImagePose, side: Side): Float? = inImage(image, LEG.getValue(side))

    /**
     * Head-on: the elbow as the picture shows it, **both arms averaged**, or
     * `null` when either is not clearly in frame. From the front both arms are
     * always visible, the elbows flare across the image as they bend, and
     * averaging halves the error of one.
     */
    fun elbowInImage(image: ImagePose): Float? {
        val left = elbowInImage(image, Side.LEFT) ?: return null
        val right = elbowInImage(image, Side.RIGHT) ?: return null
        return (left + right) / 2f
    }

    /**
     * Head-on: how steeply the torso rises from hips to shoulders, in degrees
     * above the horizontal — near 0 lying flat, 30–40 at the top of a push-up,
     * 65–90 crouching, kneeling up or standing. `null` when the shoulders or
     * hips are not clearly in frame.
     *
     * From 3D world landmarks, whose y axis points down the camera's picture:
     * with the phone standing on the floor, close enough to gravity to tell a
     * body down in a plank from one that is upright — all this is asked to do.
     */
    fun torsoElevation(frame: PoseFrame): Float? {
        val ls = frame[PoseIndex.LEFT_SHOULDER]
        val rs = frame[PoseIndex.RIGHT_SHOULDER]
        val lh = frame[PoseIndex.LEFT_HIP]
        val rh = frame[PoseIndex.RIGHT_HIP]
        if (!visible(ls, rs, lh, rh)) return null
        val dx = (ls!!.x + rs!!.x - lh!!.x - rh!!.x) / 2f
        val dy = (ls.y + rs.y - lh.y - rh.y) / 2f
        val dz = (ls.z + rs.z - lh.z - rh.z) / 2f
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length == 0f) return null
        // y points down, so a shoulder above the hip has a negative dy.
        return Math.toDegrees(asin((-dy / length).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /**
     * Head-on: how high the shoulders stand above the hands in the picture, in
     * pixels — the wrists' height minus the shoulders', both sides averaged.
     * `null` when a shoulder or wrist is not clearly in frame.
     *
     * Head-on the elbow angle in the picture exaggerates the bend: on a real
     * recording half push-ups read 51–60° at the elbow. How far the shoulders
     * came down towards the hands did not lie — 0.16–0.19 of their top height
     * on honest reps, 0.26–0.53 on the half ones.
     */
    fun shoulderHeightInImage(image: ImagePose): Float? {
        val ls = image.frame[PoseIndex.LEFT_SHOULDER]
        val rs = image.frame[PoseIndex.RIGHT_SHOULDER]
        val lw = image.frame[PoseIndex.LEFT_WRIST]
        val rw = image.frame[PoseIndex.RIGHT_WRIST]
        if (!visible(ls, rs, lw, rw)) return null
        return ((lw!!.y + rw!!.y) - (ls!!.y + rs!!.y)) / 2f * image.height
    }

    /** The near leg's knee. */
    fun kneeInImage(image: ImagePose): Float? = nearSide(image)?.let { kneeInImage(image, it) }

    private fun inImage(image: ImagePose, joints: Triple<Int, Int, Int>): Float? {
        val a = image.frame[joints.first]
        val b = image.frame[joints.second]
        val c = image.frame[joints.third]
        if (!visible(a, b, c)) return null
        return angleAt(a!!.inPixels(image), b!!.inPixels(image), c!!.inPixels(image)).takeIf { !it.isNaN() }
    }

    /**
     * Normalised coordinates stretched back to pixels. x and y are normalised to
     * different lengths on a portrait frame, and an angle measured without this
     * is squashed ([ImagePose]).
     */
    private fun Landmark.inPixels(image: ImagePose) = Landmark(x * image.width, y * image.height, 0f, presence)

    private fun Landmark.distanceTo(other: Landmark, image: ImagePose): Float = hypot((x - other.x) * image.width, (y - other.y) * image.height)

    fun bodyLine(frame: PoseFrame): Float? = bothSides(bodyLine(frame, Side.LEFT), bodyLine(frame, Side.RIGHT))
}
