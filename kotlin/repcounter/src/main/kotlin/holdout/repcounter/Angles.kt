package holdout.repcounter

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The two angles the push-up rule is written in (FR-021, research R4):
 *
 *   elbow      shoulder–elbow–wrist
 *   body line  shoulder–hip–ankle; 180° is straight — or, where push-ups
 *              from the knees count, shoulder–hip–knee if that reads
 *              straighter ([bodyLineToKneeInImage], `ViewReading`)
 *
 * On a phone they are read **in the picture**, at 45°, on the near side
 * ([nearSide]); head-on is not read (since 8 Oct). The 3D world versions are what the synthetic traces use,
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
    fun bodyLine(frame: PoseFrame, side: Side): Float? = inWorld(frame, TRUNK.getValue(side))

    /**
     * The shoulder–hip–knee angle for one side, or `null` when it is not
     * clearly in frame: the body line where push-ups from the knees count
     * ([bodyLineToKneeInImage]).
     */
    fun bodyLineToKnee(frame: PoseFrame, side: Side): Float? = inWorld(frame, TRUNK_TO_KNEE.getValue(side))

    private fun inWorld(frame: PoseFrame, joints: Triple<Int, Int, Int>): Float? {
        val a = frame[joints.first]
        val b = frame[joints.second]
        val c = frame[joints.third]
        if (!visible(a, b, c)) return null
        return angleAt(a!!, b!!, c!!).takeIf { !it.isNaN() }
    }

    /**
     * One number per frame from whichever sides are visible.
     *
     * Averaging both sides when both are present steadies the reading; falling
     * back to one side keeps a sitting filmed from one side working at all,
     * since the far arm is frequently occluded by the near one.
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
    private val TRUNK_TO_KNEE = mapOf(
        Side.LEFT to Triple(PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_HIP, PoseIndex.LEFT_KNEE),
        Side.RIGHT to Triple(PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_KNEE),
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
     * Shoulder–hip–**knee** on [side], as the picture shows it. 180° is
     * straight. Enough for a body line where push-ups from the knees count —
     * Easy and the Daily 25 (FR-021a): a push-up from the knees promises a straight line
     * from shoulder to knee and nothing about the lower legs, which may be
     * raised or crossed behind it and pull shoulder–hip–ankle far under any
     * floor. A push-up from the toes keeps the knee on the same line, so a
     * sag or a fold at the hips reads here just as it does to the ankle.
     */
    fun bodyLineToKneeInImage(image: ImagePose, side: Side): Float? = inImage(image, TRUNK_TO_KNEE.getValue(side))

    /**
     * Hip–knee–ankle on [side], as the picture shows it. From the side or at
     * 45° the leg bends in a plane turned towards the lens, so a bent knee
     * cannot project straight; head-on it can, which is one reason head-on is
     * never read ([Stakes]).
     */
    fun kneeInImage(image: ImagePose, side: Side): Float? = inImage(image, LEG.getValue(side))

    /**
     * Whether the knee on [side] is clearly in the picture: confidently placed,
     * and at least [margin] of the frame inside every edge. The model does not
     * drop a knee that leaves the frame — it puts one just inside the edge, at
     * full confidence — so only the margin tells a seen knee from a guessed one.
     */
    fun kneeClearOfEdge(image: ImagePose, side: Side, margin: Float): Boolean {
        val knee = image.frame[LEG.getValue(side).second]
        if (!visible(knee)) return false
        return knee!!.x in margin..(1f - margin) && knee.y in margin..(1f - margin)
    }

    /**
     * Whether the knee on [side] is placed where a knee can be: the shin at
     * least [MIN_SHIN_SHARE] of the thigh in the picture. In loose trousers the
     * model cannot find the knee and puts it on the ankle — full confidence, a
     * shin of no length — and the knee angle then reads anything from 10° to
     * 120° on a body held in a clean plank. On 2 Oct a second person in loose
     * white trousers read under the line in 60–67 % of frames; every other
     * recording on the 4:3 picture in 0–1 %.
     */
    fun kneeReadable(image: ImagePose, side: Side): Boolean = legSeen(image, side) && !kneeCollapsed(image, side)

    /** The whole leg found with confidence, and the knee put where no knee can be — on the ankle. */
    fun kneeCollapsed(image: ImagePose, side: Side): Boolean {
        if (!legSeen(image, side)) return false
        val (h, k, a) = LEG.getValue(side)
        val knee = image.frame[k]!!
        return knee.distanceTo(image.frame[a]!!, image) < MIN_SHIN_SHARE * image.frame[h]!!.distanceTo(knee, image)
    }

    private fun legSeen(image: ImagePose, side: Side): Boolean {
        val (h, k, a) = LEG.getValue(side)
        return visible(image.frame[h], image.frame[k], image.frame[a])
    }

    const val MIN_SHIN_SHARE = 0.35f

    /**
     * At 45°: how high the near hip stands above the near hand, in lengths of
     * the near arm. Read against the shoulder's own height, it says whether the
     * body comes up as one piece ([RepRules.minHipRise]).
     */
    fun hipAboveHandInImage(image: ImagePose, side: Side): Float? {
        val (s, e, w) = ARM.getValue(side)
        val shoulder = image.frame[s]
        val elbow = image.frame[e]
        val wrist = image.frame[w]
        val hip = image.frame[LEG.getValue(side).first]
        if (!visible(shoulder, elbow, wrist, hip)) return null
        val arm = shoulder!!.distanceTo(elbow!!, image) + elbow.distanceTo(wrist!!, image)
        if (arm <= 0f) return null
        return (wrist.y - hip!!.y) * image.height / arm
    }

    /**
     * At 45°: how high the near knee stands above the near hip, in lengths of
     * the near arm. Face down — on the toes or on the knees — the knee is never
     * above the hip; lying on the back with the knees up it is well above it
     * ([RepRules.maxKneeAboveHip]). `null` when any of those joints is not
     * clearly in frame.
     */
    fun kneeAboveHipInImage(image: ImagePose, side: Side): Float? {
        val (s, e, w) = ARM.getValue(side)
        val (h, k, _) = LEG.getValue(side)
        val shoulder = image.frame[s]
        val elbow = image.frame[e]
        val wrist = image.frame[w]
        val hip = image.frame[h]
        val knee = image.frame[k]
        if (!visible(shoulder, elbow, wrist, hip, knee)) return null
        val arm = shoulder!!.distanceTo(elbow!!, image) + elbow.distanceTo(wrist!!, image)
        if (arm <= 0f) return null
        return (hip!!.y - knee!!.y) * image.height / arm
    }

    /**
     * At 45°: how far the near ankle stands below the near hand, in lengths of
     * the near arm. In a push-up both are on the floor, and the feet, further
     * from the lens, read a little higher; standing, the ankle is a whole leg
     * below the hand ([RepRules.maxAnkleBelowHand]). `null` when any of those
     * joints is not clearly in frame.
     */
    fun ankleBelowHandInImage(image: ImagePose, side: Side): Float? {
        val (s, e, w) = ARM.getValue(side)
        val ankle = image.frame[LEG.getValue(side).third]
        val shoulder = image.frame[s]
        val elbow = image.frame[e]
        val wrist = image.frame[w]
        if (!visible(shoulder, elbow, wrist, ankle)) return null
        val arm = shoulder!!.distanceTo(elbow!!, image) + elbow.distanceTo(wrist!!, image)
        if (arm <= 0f) return null
        return (ankle!!.y - wrist.y) * image.height / arm
    }

    /**
     * Whether the foot on [side] is clearly in the picture: the ankle
     * confidently placed and at least [margin] inside every edge. A foot at
     * the edge flickers in and out of the model's confidence, and a frame
     * without it has no body line and no knee angle — on 2 Oct two honest
     * reps of eleven were refused that way, the ankle 0.02–0.05 from the edge.
     */
    fun ankleClearOfEdge(image: ImagePose, side: Side, margin: Float): Boolean {
        val ankle = image.frame[LEG.getValue(side).third]
        if (!visible(ankle)) return false
        return ankle!!.x in margin..(1f - margin) && ankle.y in margin..(1f - margin)
    }

    /** Whether the model found the knee on [side] with confidence, wherever it is. */
    fun kneeSeen(image: ImagePose, side: Side): Boolean = visible(image.frame[LEG.getValue(side).second])

    /**
     * At 45°: how high the near knee and ankle stand above the near hand in the
     * picture, added together, in lengths of the near arm. The hands are on the
     * floor, so this is the legs' height off it — a knee on the floor sits at
     * the hands' height; on the toes knees and ankles are raised. Measured on
     * the push back up on the phone: honest reps 0.44–0.65, push-ups from the
     * knees 0.32–0.39, cobras 0.17–0.32. `null` when any of those joints is
     * not clearly in frame — **or when the legs read below the hand**: the hand
     * is on the floor, so that is a misplaced landmark, not knees down. On the
     * Seeker such frames read −2.6 to −6.8 with the knee at 174–178°, and
     * refused honest reps (27 Sep); the knee angle decides them instead.
     */
    fun legsAboveHandsInImage(image: ImagePose, side: Side): Float? {
        val (s, e, w) = ARM.getValue(side)
        val (_, k, a) = LEG.getValue(side)
        val shoulder = image.frame[s]
        val elbow = image.frame[e]
        val wrist = image.frame[w]
        val knee = image.frame[k]
        val ankle = image.frame[a]
        if (!visible(shoulder, elbow, wrist, knee, ankle)) return null
        val arm = shoulder!!.distanceTo(elbow!!, image) + elbow.distanceTo(wrist!!, image)
        if (arm <= 0f) return null
        // y points down the picture, so "above the hand" is the hand's y minus the joint's.
        val above = ((wrist.y - knee!!.y) + (wrist.y - ankle!!.y)) * image.height
        return (above / arm).takeIf { it >= 0f }
    }

    /**
     * At 45°: how high the near shoulder stands above the near hand in the
     * picture, in lengths of the near arm. At the bottom of a full push-up the
     * chest is near the floor, so this is small; a half push-up stops with the
     * shoulder well above the hand. On the phone, at the bottom: honest reps
     * 0.20–0.28, half push-ups 0.43–0.68 — where the elbow angle alone had
     * read some half reps as deep as honest ones (55–67°). `null` when the
     * near arm is not clearly in frame.
     */
    fun shoulderOverHandInImage(image: ImagePose, side: Side): Float? {
        val (s, e, w) = ARM.getValue(side)
        val shoulder = image.frame[s]
        val elbow = image.frame[e]
        val wrist = image.frame[w]
        if (!visible(shoulder, elbow, wrist)) return null
        val arm = shoulder!!.distanceTo(elbow!!, image) + elbow.distanceTo(wrist!!, image)
        if (arm <= 0f) return null
        return (wrist.y - shoulder.y) * image.height / arm
    }

    /**
     * Whether the head is clearly inside the picture. At the bottom of a rep,
     * with the phone set too close, the head drops out of frame first — and
     * the model then guesses the rest of the body.
     */
    fun headInFrame(image: PoseFrame, margin: Float = 0.02f): Boolean {
        val nose = image[PoseIndex.NOSE] ?: return false
        return nose.presence >= PRESENCE_THRESHOLD &&
            nose.x in margin..(1f - margin) &&
            nose.y in margin..(1f - margin)
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

    fun bodyLineToKnee(frame: PoseFrame): Float? = bothSides(bodyLineToKnee(frame, Side.LEFT), bodyLineToKnee(frame, Side.RIGHT))
}
