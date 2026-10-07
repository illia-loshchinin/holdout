package holdout.repcounter

import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic landmark traces, so the rules can be tested before spike S2 records
 * real ones (T012b, T049).
 *
 * These are **not** a substitute for the ten honest videos and three cheats. A
 * synthetic trace proves the state machine does what the rule says; only real
 * video proves the rule survives a real body, real light and a real model's
 * error. When `fixtures/rep-traces/s2-angles.json` lands, it is replayed
 * alongside these, and these stay — they are the cases a recording cannot
 * contain on purpose, like a frame with no landmarks at all.
 */
object Traces {

    /** 40 FPS, comfortably above the ≥ 15 FPS the spike requires on a Seeker. */
    const val FRAME_MILLIS = 25L

    /**
     * Landmarks placed so that [Angles] reads back exactly [elbowDegrees] and
     * [bodyLineDegrees]. Both sides are given the same geometry, so the average
     * of the two is the value asked for.
     */
    fun frame(
        timestampMillis: Long,
        elbowDegrees: Float,
        bodyLineDegrees: Float,
        presence: Float = 1.0f,
    ): PoseFrame {
        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }

        val e = Math.toRadians(elbowDegrees.toDouble())
        val b = Math.toRadians(bodyLineDegrees.toDouble())

        // Shoulder at the origin, elbow one unit along +x. Placing the wrist at
        // this offset from the elbow makes the angle shoulder–elbow–wrist
        // exactly `elbowDegrees`.
        val shoulder = Landmark(0f, 0f, 0f, presence)
        val elbow = Landmark(1f, 0f, 0f, presence)
        val wrist = Landmark(1f - cos(e).toFloat(), sin(e).toFloat(), 0f, presence)

        // Hip one unit below the shoulder, so hip→shoulder is (0, 1, 0); the
        // ankle then sets shoulder–hip–ankle to `bodyLineDegrees`.
        val hip = Landmark(0f, -1f, 0f, presence)
        val ankle = Landmark(sin(b).toFloat(), -1f + cos(b).toFloat(), 0f, presence)

        for (side in listOf(
            intArrayOf(
                PoseIndex.LEFT_SHOULDER,
                PoseIndex.LEFT_ELBOW,
                PoseIndex.LEFT_WRIST,
                PoseIndex.LEFT_HIP,
                PoseIndex.LEFT_ANKLE,
            ),
            intArrayOf(
                PoseIndex.RIGHT_SHOULDER,
                PoseIndex.RIGHT_ELBOW,
                PoseIndex.RIGHT_WRIST,
                PoseIndex.RIGHT_HIP,
                PoseIndex.RIGHT_ANKLE,
            ),
        )) {
            landmarks[side[0]] = shoulder
            landmarks[side[1]] = elbow
            landmarks[side[2]] = wrist
            landmarks[side[3]] = hip
            landmarks[side[4]] = ankle
        }
        // The head, just beyond the shoulders: the whole-body check needs it.
        landmarks[PoseIndex.NOSE] = Landmark(-0.3f, 0.2f, 0f, presence)

        return PoseFrame(timestampMillis, landmarks)
    }

    /**
     * One push-up: a smooth cosine from [top] down to [bottom] and back, over
     * [cycleMillis]. Real descents are not sinusoidal, but they are continuous,
     * and continuity is what the one-euro filter and the hysteresis care about.
     *
     * [bodyLineAtBottom] sags the hips in the middle of the cycle, which is the
     * sagging-hips cheat.
     */
    fun pushUp(
        startMillis: Long,
        top: Float = 175f,
        bottom: Float = 70f,
        cycleMillis: Long = 2_000,
        bodyLine: Float = 180f,
        bodyLineAtBottom: Float = bodyLine,
        presence: Float = 1.0f,
    ): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        var t = 0L
        while (t <= cycleMillis) {
            // phase 0 → 1 → 0 across the cycle.
            val phase = (1.0 - cos(2.0 * Math.PI * t / cycleMillis)) / 2.0
            val elbow = top - (top - bottom) * phase.toFloat()
            val line = bodyLine - (bodyLine - bodyLineAtBottom) * phase.toFloat()
            frames += frame(startMillis + t, elbow, line, presence)
            t += FRAME_MILLIS
        }
        return frames
    }

    /** Long enough at the top for the machine to settle there before the first rep. */
    fun settleAtTop(startMillis: Long, millis: Long = 500, elbow: Float = 175f): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        var t = 0L
        while (t <= millis) {
            frames += frame(startMillis + t, elbow, 180f)
            t += FRAME_MILLIS
        }
        return frames
    }

    /** [count] identical push-ups back to back, after a settle at the top. */
    fun set(
        count: Int,
        top: Float = 175f,
        bottom: Float = 70f,
        cycleMillis: Long = 2_000,
        bodyLine: Float = 180f,
        bodyLineAtBottom: Float = bodyLine,
    ): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        frames += settleAtTop(0)
        var t = frames.last().timestampMillis + FRAME_MILLIS
        repeat(count) {
            val rep = pushUp(t, top, bottom, cycleMillis, bodyLine, bodyLineAtBottom)
            frames += rep
            t = rep.last().timestampMillis + FRAME_MILLIS
        }
        return frames
    }

    /**
     * **Image** landmarks — normalised 0..1, the picture rather than the body.
     *
     * [Traces.frame] builds world landmarks, where only the angles matter. The
     * camera-angle check is the one thing that reads the picture, and what it
     * reads is [shoulderSpan]: how far apart the two shoulders sit on screen,
     * against the torso's length. Zero is perfectly side-on, one shoulder
     * exactly behind the other; a wide span is a camera looking at the member
     * face-on.
     *
     * [torsoDegrees] rotates the whole body within the frame. Nothing should
     * depend on it — that is the bug this replaced.
     */
    fun imageFrame(
        timestampMillis: Long = 0,
        shoulderSpan: Float = 0f,
        torsoDegrees: Float = 0f,
        presence: Float = 1f,
    ): PoseFrame {
        val angle = Math.toRadians(torsoDegrees.toDouble())
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()

        val hipX = 0.3f
        val hipY = 0.5f
        val torso = 0.4f
        val shoulderX = hipX + dx * torso
        val shoulderY = hipY + dy * torso

        // The shoulders separate across the torso, so a rotation carries them
        // with it and the span stays the span.
        val halfSpan = shoulderSpan / 2f
        val left = Landmark(shoulderX - dy * halfSpan, shoulderY + dx * halfSpan, 0f, presence)
        val right = Landmark(shoulderX + dy * halfSpan, shoulderY - dx * halfSpan, 0f, presence)
        val hip = Landmark(hipX, hipY, 0f, presence)
        val limb = Landmark(0.5f, 0.6f, 0f, presence)

        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
        landmarks[PoseIndex.LEFT_SHOULDER] = left
        landmarks[PoseIndex.RIGHT_SHOULDER] = right
        for (i in listOf(PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP)) landmarks[i] = hip
        for (i in listOf(
            PoseIndex.LEFT_ELBOW,
            PoseIndex.RIGHT_ELBOW,
            PoseIndex.LEFT_WRIST,
            PoseIndex.RIGHT_WRIST,
            PoseIndex.LEFT_ANKLE,
            PoseIndex.RIGHT_ANKLE,
        )) {
            landmarks[i] = limb
        }
        // The head, just beyond the shoulders.
        landmarks[PoseIndex.NOSE] = Landmark(shoulderX + dx * 0.05f, shoulderY + dy * 0.05f, 0f, presence)
        return PoseFrame(timestampMillis, landmarks)
    }

    /** A portrait 9:16 picture, so a measurement that forgets to un-squash the axes reads wrong. */
    const val IMAGE_WIDTH = 900
    const val IMAGE_HEIGHT = 1600

    /**
     * One head-on frame: world landmarks whose torso rises [torsoDegrees] above
     * the horizontal, and a picture in which both elbows read exactly
     * [elbowDegrees].
     *
     * Deliberately, the world arms are left **straight** whatever the picture
     * says. Head-on, the world elbow is the reading that failed; a front-view
     * test that passed while it was still being read would prove nothing.
     */
    fun frontFrame(
        timestampMillis: Long,
        elbowDegrees: Float,
        torsoDegrees: Float = 35f,
        presence: Float = 1.0f,
    ): Pair<PoseFrame, ImagePose> {
        val world = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
        val t = Math.toRadians(torsoDegrees.toDouble())
        // Hips at the origin; the shoulders half a metre along the torso,
        // towards the camera (-z) and up (-y, since y points down).
        val sy = (-0.5 * sin(t)).toFloat()
        val sz = (-0.5 * cos(t)).toFloat()
        for ((sign, s, e, w, h) in listOf(
            listOf(-1, PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST, PoseIndex.LEFT_HIP),
            listOf(1, PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST, PoseIndex.RIGHT_HIP),
        )) {
            val x = 0.2f * sign
            world[h] = Landmark(x / 2, 0f, 0f, presence)
            world[s] = Landmark(x, sy, sz, presence)
            world[e] = Landmark(x, sy + 0.3f, sz, presence)
            world[w] = Landmark(x, sy + 0.6f, sz, presence)
        }
        for (a in listOf(PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE)) world[a] = Landmark(0f, 0.1f, 1.5f, presence)

        // The picture, built in pixels and then normalised: each arm bends
        // outward by `elbowDegrees`, the way flared elbows look head-on.
        val picture = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
        val e = Math.toRadians(elbowDegrees.toDouble())
        for ((sign, s, el, w) in listOf(
            listOf(-1, PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST),
            listOf(1, PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST),
        )) {
            val shoulderX = 450.0 + sign * 120.0
            val shoulderY = 800.0
            // Elbow 200 px out and up from the shoulder at 45°; the wrist
            // placed so the angle at the elbow is exactly `elbowDegrees`.
            val ux = -sign * cos(Math.PI / 4)
            val uy = sin(Math.PI / 4)
            val elbowX = shoulderX - ux * 200
            val elbowY = shoulderY - uy * 200
            // Rotate the elbow→shoulder direction by the elbow angle, away from the body.
            val rot = -sign * e
            val fx = ux * cos(rot) - uy * sin(rot)
            val fy = ux * sin(rot) + uy * cos(rot)
            val wristX = elbowX + fx * 200
            val wristY = elbowY + fy * 200
            picture[s] = Landmark((shoulderX / IMAGE_WIDTH).toFloat(), (shoulderY / IMAGE_HEIGHT).toFloat(), 0f, presence)
            picture[el] = Landmark((elbowX / IMAGE_WIDTH).toFloat(), (elbowY / IMAGE_HEIGHT).toFloat(), 0f, presence)
            picture[w] = Landmark((wristX / IMAGE_WIDTH).toFloat(), (wristY / IMAGE_HEIGHT).toFloat(), 0f, presence)
        }
        return PoseFrame(timestampMillis, world) to
            ImagePose(PoseFrame(timestampMillis, picture), IMAGE_WIDTH, IMAGE_HEIGHT)
    }

    /** [count] head-on push-ups after a settle at the top, shaped as [pushUp] is. */
    fun frontSet(
        count: Int,
        top: Float = 175f,
        bottom: Float = 45f,
        cycleMillis: Long = 2_000,
        torsoDegrees: Float = 35f,
    ): List<Pair<PoseFrame, ImagePose>> {
        val frames = mutableListOf<Pair<PoseFrame, ImagePose>>()
        var t = 0L
        while (t <= 500) {
            frames += frontFrame(t, top, torsoDegrees)
            t += FRAME_MILLIS
        }
        repeat(count) {
            var c = 0L
            while (c <= cycleMillis) {
                val phase = (1.0 - cos(2.0 * Math.PI * c / cycleMillis)) / 2.0
                frames += frontFrame(t + c, top - (top - bottom) * phase.toFloat(), torsoDegrees)
                c += FRAME_MILLIS
            }
            t += cycleMillis + FRAME_MILLIS
        }
        return frames
    }

    /**
     * The picture of a side-on body, as far as the knee check reads it: the
     * near leg drawn in pixels with the knee at exactly [kneeDegrees], the far
     * leg hidden behind it as it is from the side.
     */
    fun sideImage(timestampMillis: Long, kneeDegrees: Float, presence: Float = 1f): ImagePose {
        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
        val k = Math.toRadians(kneeDegrees.toDouble())
        // Hip up and to the left of the knee along the thigh; the shin leaves
        // the knee at `kneeDegrees` from the thigh.
        val kneeX = 500.0
        val kneeY = 1_000.0
        val hipX = kneeX - 250.0
        val hipY = kneeY - 60.0
        val tx = (hipX - kneeX) / 257.1
        val ty = (hipY - kneeY) / 257.1
        val ankleX = kneeX + 250.0 * (tx * cos(k) - ty * sin(k))
        val ankleY = kneeY + 250.0 * (tx * sin(k) + ty * cos(k))
        fun at(x: Double, y: Double) = Landmark((x / IMAGE_WIDTH).toFloat(), (y / IMAGE_HEIGHT).toFloat(), 0f, presence)
        landmarks[PoseIndex.LEFT_HIP] = at(hipX, hipY)
        landmarks[PoseIndex.LEFT_KNEE] = at(kneeX, kneeY)
        landmarks[PoseIndex.LEFT_ANKLE] = at(ankleX, ankleY)
        return ImagePose(PoseFrame(timestampMillis, landmarks), IMAGE_WIDTH, IMAGE_HEIGHT)
    }

    /**
     * The picture of a body at 45°, as far as the angled reading needs it: the
     * near side only (the far one is hidden, as it is on a phone), drawn in
     * pixels with the elbow at [elbowDegrees], shoulder–hip–knee at
     * [hipDegrees] and the knee at [kneeDegrees].
     *
     * Shoulder, hip and knee stay on one line whatever the knee does — what a
     * push-up from the knees promises — and a bent knee raises the lower leg
     * off the floor behind it, so shoulder–hip–ankle bends with it: at 70° it
     * reads about 125°. [hipDegrees] under 180 drops the hips towards the
     * floor, a sag, and bends both lines.
     */
    fun angledImage(
        timestampMillis: Long,
        elbowDegrees: Float,
        kneeDegrees: Float = 178f,
        presence: Float = 1f,
        hipDegrees: Float = 180f,
    ): ImagePose {
        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }

        // Drawn at half size, lower in the frame, so the whole body — knees and
        // ankles too — lies well inside the picture, as it must on the phone.
        // Every reading here is an angle or a ratio of lengths: scaling changes none.
        fun at(x: Double, y: Double) = Landmark((x * 0.5 / IMAGE_WIDTH).toFloat(), ((y * 0.5 + 200) / IMAGE_HEIGHT).toFloat(), 0f, presence)
        // Arm: the hand stays on the floor and the shoulder comes down towards
        // it as the elbow bends, as on the phone — at the bottom of an honest
        // rep the shoulder stood 0.20–0.28 arm-lengths over the hand at an
        // elbow of about 50°, and a half rep stopped at 0.43–0.68.
        val upper = 170.0
        val wrist = 200.0 to 1_300.0
        val span = 2 * upper * sin(Math.toRadians(elbowDegrees / 2.0))
        val rise = minOf(span, 2 * upper * ((elbowDegrees - 40.0) / 140.0).coerceIn(0.02, 1.0))
        val ahead = kotlin.math.sqrt(maxOf(span * span - rise * rise, 0.0))
        val shoulder = wrist.first + ahead to wrist.second - rise
        // The elbow at the apex of the arm's triangle, bent outward.
        val mid = (shoulder.first + wrist.first) / 2 to (shoulder.second + wrist.second) / 2
        val apex = kotlin.math.sqrt(maxOf(upper * upper - (span / 2) * (span / 2), 0.0))
        val (dx, dy) = if (span > 0) (wrist.first - shoulder.first) / span to (wrist.second - shoulder.second) / span else 0.0 to 1.0
        val elbow = mid.first - dy * apex to mid.second + dx * apex

        // Body: shoulder, hip and knee on one line running away from the
        // lens — the legs are farther off, so they sit higher in the picture
        // than the hands, as on the phone. y points down the picture, so a
        // turn by a negative angle lifts a limb off the floor.
        fun turn(v: Pair<Double, Double>, degrees: Double): Pair<Double, Double> {
            val a = Math.toRadians(degrees)
            return v.first * cos(a) - v.second * sin(a) to v.first * sin(a) + v.second * cos(a)
        }
        val trunk = 0.97 to -0.24
        val hip = shoulder.first + 330 * trunk.first to shoulder.second + 330 * trunk.second
        // A sag: the hips drop, so the thigh leaves them pointing up off the trunk's line.
        val thigh = turn(trunk, -(180.0 - hipDegrees))
        val knee = hip.first + 230 * thigh.first to hip.second + 230 * thigh.second
        // The lower leg raised behind the knee by however far the knee bends.
        val shin = turn(thigh, -(180.0 - kneeDegrees))
        val ankle = knee.first + 230 * shin.first to knee.second + 230 * shin.second

        landmarks[PoseIndex.LEFT_SHOULDER] = at(shoulder.first, shoulder.second)
        landmarks[PoseIndex.LEFT_ELBOW] = at(elbow.first, elbow.second)
        landmarks[PoseIndex.LEFT_WRIST] = at(wrist.first, wrist.second)
        landmarks[PoseIndex.LEFT_HIP] = at(hip.first, hip.second)
        landmarks[PoseIndex.LEFT_KNEE] = at(knee.first, knee.second)
        landmarks[PoseIndex.LEFT_ANKLE] = at(ankle.first, ankle.second)
        return ImagePose(PoseFrame(timestampMillis, landmarks), IMAGE_WIDTH, IMAGE_HEIGHT)
    }

    /** A frame the model could not read — every landmark below the presence threshold. */
    fun blind(timestampMillis: Long): PoseFrame = PoseFrame(timestampMillis, List(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0.1f) })
}
