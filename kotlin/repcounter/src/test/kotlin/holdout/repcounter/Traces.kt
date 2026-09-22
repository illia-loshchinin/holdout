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
        return PoseFrame(timestampMillis, landmarks)
    }

    /** A frame the model could not read — every landmark below the presence threshold. */
    fun blind(timestampMillis: Long): PoseFrame = PoseFrame(timestampMillis, List(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0.1f) })
}
