package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the phone may go: head-on or at 45° for the free Daily 25, at 45° only
 * when money is on it, and square side-on for neither.
 *
 * Head-on the legs point into the lens and a push-up from the knees is
 * invisible — fine for a free habit, not when one member's knee push-up takes
 * another member's stake. At 45° the picture shows the knee, so it is judged.
 */
class StakesTest {

    private fun input(shoulderSpan: Float) = PreflightInput(
        brightness = 0.5f,
        backgroundMotion = 0f,
        world = Traces.frame(0, elbowDegrees = 175f, bodyLineDegrees = 180f),
        image = Traces.imageFrame(shoulderSpan = shoulderSpan),
    )

    private val sideOn = input(shoulderSpan = 0.05f)
    private val atFortyFive = input(shoulderSpan = 0.4f)
    private val headOn = input(shoulderSpan = 0.7f)

    @Test
    fun `a staked sitting starts at 45 degrees`() {
        val report = Preflight(stakes = Stakes.STAKED).evaluate(atFortyFive)
        assertEquals(CameraView.OBLIQUE, report.view)
        assertTrue(report.canStart)
    }

    @Test
    fun `a staked sitting will not start head-on or square side-on, and says turn to 45`() {
        for (frame in listOf(headOn, sideOn)) {
            val report = Preflight(stakes = Stakes.STAKED).evaluate(frame)
            assertFalse(report.canStart, "${report.view} must not start a staked sitting")
            assertEquals(listOf(Stakes.STAKED.cameraFix), report.fixes)
        }
    }

    @Test
    fun `the Daily 25 starts head-on or at 45 degrees, never square side-on`() {
        assertTrue(Preflight(stakes = Stakes.DAILY).evaluate(headOn).canStart)
        assertTrue(Preflight(stakes = Stakes.DAILY).evaluate(atFortyFive).canStart)
        val sideReport = Preflight(stakes = Stakes.DAILY).evaluate(sideOn)
        assertFalse(sideReport.canStart)
        assertEquals(listOf(Stakes.DAILY.cameraFix), sideReport.fixes)
    }

    @Test
    fun `the guide asks for the stakes' placement before anything else, and never calls the wrong one aligned`() {
        val guide = PositionGuide()
        val floor = FloorView.Horizon(0.35f)
        val allowed = Stakes.STAKED.allowedViews
        val target = guide.guide(null, CameraView.OBLIQUE, floor, allowedViews = allowed).target
        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(target.centreX, target.centreY, 0f, 1f) }
        landmarks[0] = Landmark(target.left, target.top, 0f, 1f)
        landmarks[1] = Landmark(target.right, target.bottom, 0f, 1f)
        val body = PoseFrame(0, landmarks)

        val headOnGuide = guide.guide(body, CameraView.FRONT, floor, allowedViews = allowed)
        assertEquals(PositionHint.PHONE_AT_45, headOnGuide.hint)
        assertFalse(headOnGuide.aligned)

        val angledGuide = guide.guide(body, CameraView.OBLIQUE, floor, allowedViews = allowed)
        assertNull(angledGuide.hint)
        assertTrue(angledGuide.aligned)

        val dailySideOn = guide.guide(body, CameraView.SIDE, floor, allowedViews = Stakes.DAILY.allowedViews)
        assertEquals(PositionHint.PHONE_AT_45, dailySideOn.hint)
    }

    @Test
    fun `a staked counter cannot be built for a view other than 45 degrees`() {
        assertFailsWith<IllegalArgumentException> { RepCounter(view = CameraView.FRONT, stakes = Stakes.STAKED) }
        assertFailsWith<IllegalArgumentException> { RepCounter(view = CameraView.SIDE, stakes = Stakes.STAKED) }
        RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
    }

    /** A set at 45°, read from the picture as on a phone, with the knee held at [kneeDegrees]. */
    private fun angledSet(kneeDegrees: Float, count: Int = 10): Pair<Int, List<RepHint?>> {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
        val hints = mutableListOf<RepHint?>()
        for (frame in Traces.set(count = count)) {
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees)
            (counter.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
        }
        return counter.total to hints
    }

    @Test
    fun `at 45 degrees, full push-ups from the toes count`() {
        val (count, hints) = angledSet(kneeDegrees = 178f)
        assertEquals(10, count)
        assertTrue(hints.isEmpty(), "expected no hints, got $hints")
    }

    @Test
    fun `at 45 degrees, push-ups from the knees count nothing, and say why`() {
        val (count, hints) = angledSet(kneeDegrees = 70f)
        assertEquals(0, count)
        assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.UP_ON_YOUR_TOES }, "got $hints")
    }

    @Test
    fun `a leg bent a little in the air is fine - only knees on the floor are refused`() {
        // The rule is knees off the floor, not legs dead straight.
        assertEquals(10, angledSet(kneeDegrees = 150f).first)
        assertEquals(0, angledSet(kneeDegrees = 70f).first)
    }

    @Test
    fun `at 45 degrees the elbow is read from the picture, not the 3D estimate`() {
        // The world elbow says a full push-up; the picture says the arms barely
        // bent. On a phone the picture decides, so nothing counts.
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
        for (frame in Traces.set(count = 5)) {
            val shallow = 175f - (175f - Angles.elbow(frame)!!) * 0.3f
            counter.onFrame(frame, Traces.angledImage(frame.timestampMillis, shallow))
        }
        assertEquals(0, counter.total)
    }

    @Test
    fun `the near side is read, not the hidden far one`() {
        // The near leg straight and long in the picture; the far leg, which the
        // model guesses, short and folded. Only the near one may decide.
        val near = Traces.angledImage(0, elbowDegrees = 175f, kneeDegrees = 178f)
        val landmarks = near.frame.landmarks.toMutableList()
        val hip = landmarks[PoseIndex.LEFT_HIP]
        landmarks[PoseIndex.RIGHT_HIP] = hip
        landmarks[PoseIndex.RIGHT_KNEE] = Landmark(hip.x + 0.03f, hip.y + 0.02f, 0f, 1f)
        landmarks[PoseIndex.RIGHT_ANKLE] = Landmark(hip.x, hip.y + 0.03f, 0f, 1f)
        val picture = ImagePose(PoseFrame(0, landmarks), near.width, near.height)
        assertEquals(Side.LEFT, Angles.nearSide(picture))
        assertTrue(Angles.kneeInImage(picture)!! > 170f)
    }

    @Test
    fun `the Daily 25 counts push-ups from the knees - staked sittings do not`() {
        val daily = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        for (frame in Traces.set(count = 10)) {
            daily.onFrame(frame, Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = 70f))
        }
        assertEquals(10, daily.total, "the Daily 25 is a habit for building strength; knees are allowed")
        assertEquals(0, angledSet(kneeDegrees = 70f).first, "money is only staked on full push-ups")
    }

    @Test
    fun `the Daily 25 may start from the knees`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.DAILY)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val knees = { t: Long -> Traces.angledImage(t, elbowDegrees = 175f, kneeDegrees = 70f) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(0))
        assertTrue(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(2_000)))
    }

    @Test
    fun `the staked ready position is not held from the knees`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val knees = { t: Long -> Traces.angledImage(t, elbowDegrees = 175f, kneeDegrees = 70f) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(0))
        assertFalse(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(2_000)))
    }

    /** [image] with the near leg read below the hand — a knee and ankle under a hand that is on the floor. */
    private fun legsBelowHand(image: ImagePose): ImagePose {
        val landmarks = image.frame.landmarks.toMutableList()
        val wrist = landmarks[PoseIndex.LEFT_WRIST]
        for (i in listOf(PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE)) landmarks[i] = landmarks[i].copy(y = wrist.y + 0.3f)
        return image.copy(frame = image.frame.copy(landmarks = landmarks))
    }

    @Test
    fun `legs read below the hands are a misread, not knees down - straight legs still count`() {
        // On the Seeker, 27 Sep: honest reps with the knee at 174-178° were refused
        // "Knees off the floor" because the legs read 2.6-6.8 arm-lengths *below* the
        // hands — which are on the floor. Impossible, so it is unreadable, and the
        // knee angle, which saw straight legs, decides.
        val image = legsBelowHand(Traces.angledImage(1_000, elbowDegrees = 170f, kneeDegrees = 178f))
        assertEquals(null, Angles.legsAboveHandsInImage(image, Side.LEFT))
        assertTrue(kneesOffFloor(knee = 176f, legsAboveHands = null, rules = RepRules()))
        // Knees really down still read above zero (0.32-0.39) and are still refused.
        assertEquals(0, angledSet(kneeDegrees = 70f).first)
    }

    /** [image] with the near knee gone from the picture, as when MediaPipe loses it. */
    private fun withoutKnee(image: ImagePose): ImagePose {
        val landmarks = image.frame.landmarks.toMutableList()
        landmarks[PoseIndex.LEFT_KNEE] = landmarks[PoseIndex.LEFT_KNEE].copy(presence = 0f)
        return image.copy(frame = image.frame.copy(landmarks = landmarks))
    }

    @Test
    fun `a staked push with no knee in the picture is not credited - the knees were never seen`() {
        for ((stakes, expected) in listOf(Stakes.STAKED to 0, Stakes.DAILY to 10)) {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
            val hints = mutableListOf<RepHint?>()
            for (frame in Traces.set(count = 10)) {
                val image = withoutKnee(Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = 70f))
                (counter.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
            }
            assertEquals(expected, counter.total, "$stakes")
            if (stakes == Stakes.STAKED) assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.LEGS_IN_VIEW }, "got $hints")
        }
    }

    @Test
    fun `a leg hidden through the push back up is not credited, even though those frames cannot be read at all`() {
        // Knee and ankle gone: no body line, so the whole frame is unreadable and dropped.
        fun withoutLeg(image: ImagePose): ImagePose {
            val landmarks = image.frame.landmarks.toMutableList()
            for (i in listOf(PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE)) landmarks[i] = landmarks[i].copy(presence = 0f)
            return image.copy(frame = image.frame.copy(landmarks = landmarks))
        }
        for ((stakes, expected) in listOf(Stakes.STAKED to 0, Stakes.DAILY to 10)) {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
            val hints = mutableListOf<RepHint?>()
            var previous = 180f
            for (frame in Traces.set(count = 10)) {
                val elbow = Angles.elbow(frame)!!
                // Visible down to the bottom, hidden on the way up, back in view at the top.
                val rising = elbow > previous && elbow in 80f..165f
                previous = elbow
                val image = Traces.angledImage(frame.timestampMillis, elbow)
                (counter.onFrame(frame, if (rising) withoutLeg(image) else image) as? RepEvent.Discarded)?.let { hints += it.hint }
            }
            assertEquals(expected, counter.total, "$stakes")
            if (stakes == Stakes.STAKED) assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.LEGS_IN_VIEW }, "got $hints")
        }
    }

    @Test
    fun `the staked ready position needs the knees in the picture`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val unseen = { t: Long -> withoutKnee(Traces.angledImage(t, elbowDegrees = 175f)) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = unseen(0))
        assertFalse(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = unseen(2_000)))
    }

    /**
     * [image] with the near arm lost and the far arm guessed straight — what
     * MediaPipe does for a moment when the near arm is hidden.
     */
    private fun nearArmLost(image: ImagePose): ImagePose {
        val l = image.frame.landmarks.toMutableList()
        val shoulder = l[PoseIndex.LEFT_SHOULDER]
        listOf(PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST).forEach { l[it] = l[it].copy(presence = 0f) }
        l[PoseIndex.RIGHT_SHOULDER] = shoulder.copy(presence = 1f)
        l[PoseIndex.RIGHT_ELBOW] = shoulder.copy(y = shoulder.y + 0.3f, presence = 1f)
        l[PoseIndex.RIGHT_WRIST] = shoulder.copy(y = shoulder.y + 0.6f, presence = 1f)
        // The far leg guessed where the near one is, so the far side reads longer.
        listOf(PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP, PoseIndex.LEFT_KNEE to PoseIndex.RIGHT_KNEE, PoseIndex.LEFT_ANKLE to PoseIndex.RIGHT_ANKLE)
            .forEach { (near, far) -> l[far] = l[near] }
        return image.copy(frame = image.frame.copy(landmarks = l))
    }

    @Test
    fun `a frame where the near arm is lost is skipped, not read from the far side`() {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        Traces.set(count = 10).forEachIndexed { i, frame ->
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!)
            counter.onFrame(frame, if (i > 10 && i % 4 == 0) nearArmLost(image) else image)
        }
        assertEquals(10, counter.total)
    }

    /** Five reps topping out at [repTop], after a countdown held at [held] (or none). */
    private fun countAt45(held: Float?, repTop: Float): Int {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        var start = 0L
        if (held != null) {
            Traces.settleAtTop(0, millis = 1_000, elbow = held).forEach { counter.prime(it, Traces.angledImage(it.timestampMillis, held)) }
            start = 1_100
        }
        Traces.set(count = 5, top = repTop).forEach { frame ->
            val at = frame.copy(timestampMillis = frame.timestampMillis + start)
            counter.onFrame(at, Traces.angledImage(at.timestampMillis, Angles.elbow(frame)!!))
        }
        return counter.total
    }

    @Test
    fun `at 45 degrees the top of a rep is the member's own held arm, never under 148 nor over the rule`() {
        // From 45–50° on the floor a locked-out arm reads 150–158°: held at 158, reps back to 154 count.
        assertEquals(5, countAt45(held = 158f, repTop = 154f))
        // From a more frontal spot the same arm reads 175°: there the rule's own 160° stands, and 154° is not a lockout.
        assertEquals(0, countAt45(held = 175f, repTop = 154f))
        assertEquals(5, countAt45(held = 175f, repTop = 170f))
        // The floor: an arm held bent does not buy a lower line than 148°.
        assertEquals(0, countAt45(held = 150f, repTop = 144f))
        // No countdown to read (never so on the phone): the rule's own top.
        assertEquals(0, countAt45(held = null, repTop = 154f))
    }

    @Test
    fun `a descent started during the countdown still counts - the counter follows the countdown`() {
        val frames = Traces.set(count = 1)
        val go = frames.size * 2 / 5 // "go" lands part-way down
        fun run(primed: Boolean): Int {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
            frames.forEachIndexed { i, frame ->
                val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!)
                when {
                    i < go && primed -> counter.prime(frame, image)
                    i < go -> Unit
                    else -> counter.onFrame(frame, image)
                }
            }
            return counter.total
        }
        assertEquals(0, run(primed = false), "a counter that starts mid-descent loses the rep")
        assertEquals(1, run(primed = true))
    }

    @Test
    fun `a whole rep during the countdown is not counted before go`() {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        val frames = Traces.set(count = 2)
        val firstRepDone = Traces.set(count = 1).size // the countdown ends as the first rep does
        frames.forEachIndexed { i, frame ->
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!)
            if (i < firstRepDone) assertEquals(null, counter.prime(frame, image)) else counter.onFrame(frame, image)
        }
        assertEquals(1, counter.total)
    }

    @Test
    fun `the rule a member reads before staking names the placement and the knees`() {
        val rule = Stakes.STAKED_RULE.lowercase()
        assertTrue("45" in rule && "not in front" in rule, "the placement")
        assertTrue("toes" in rule && "knees" in rule, "the knees")
    }
}
