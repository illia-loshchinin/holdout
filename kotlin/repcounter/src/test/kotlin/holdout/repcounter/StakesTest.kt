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
    fun `the staked ready position needs the knees in the picture`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val unseen = { t: Long -> withoutKnee(Traces.angledImage(t, elbowDegrees = 175f)) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = unseen(0))
        assertFalse(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = unseen(2_000)))
    }

    @Test
    fun `the rule a member reads before staking names the placement and the knees`() {
        val rule = Stakes.STAKED_RULE.lowercase()
        assertTrue("45" in rule && "not in front" in rule, "the placement")
        assertTrue("toes" in rule && "knees" in rule, "the knees")
    }
}
