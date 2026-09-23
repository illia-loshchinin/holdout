package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T046 — the four environment checks, and what each red pill says (FR-019, PRD §8.2). */
class PreflightTest {

    private val preflight = Preflight()

    private fun good(
        brightness: Float = 0.5f,
        backgroundMotion: Float = 0.0f,
        world: PoseFrame? = Traces.frame(0, elbowDegrees = 175f, bodyLineDegrees = 180f),
        image: PoseFrame? = Traces.imageFrame(shoulderSpan = 0.05f),
    ) = PreflightInput(brightness, backgroundMotion, world, image)

    @Test
    fun `four checks, no more - there is no audio check`() {
        val report = preflight.evaluate(good())
        assertEquals(4, report.statuses.size)
        assertEquals(
            setOf(
                PreflightCheck.LIGHTING,
                PreflightCheck.WHOLE_BODY,
                PreflightCheck.CAMERA_ANGLE,
                PreflightCheck.BACKGROUND_STILL,
            ),
            report.statuses.map { it.check }.toSet(),
        )
    }

    @Test
    fun `a good frame starts the sitting`() {
        assertTrue(preflight.evaluate(good()).canStart)
    }

    @Test
    fun `a dark room fails, and says to add light`() {
        val report = preflight.evaluate(good(brightness = 0.05f))
        assertFalse(report.canStart)
        assertEquals(listOf("Add light"), report.fixes)
    }

    @Test
    fun `a blown-out frame fails the same check`() {
        assertFalse(preflight.evaluate(good(brightness = 0.99f)).canStart)
    }

    @Test
    fun `nobody in frame fails the body and angle checks, and says to step back`() {
        val report = preflight.evaluate(good(world = null, image = null))
        assertFalse(report.canStart)
        assertTrue(report.fixes.contains("Step back so your whole body is visible"))
    }

    @Test
    fun `a body with its ankles out of frame fails`() {
        val frame = Traces.frame(0, 175f, 180f)
        val cropped = PoseFrame(
            frame.timestampMillis,
            frame.landmarks.mapIndexed { i, landmark ->
                if (i == PoseIndex.LEFT_ANKLE || i == PoseIndex.RIGHT_ANKLE) {
                    landmark.copy(presence = 0.2f)
                } else {
                    landmark
                }
            },
        )
        val report = preflight.evaluate(good(world = cropped))
        assertFalse(report.canStart)
        assertTrue(report.fixes.contains("Step back so your whole body is visible"))
    }

    @Test
    fun `both placements are accepted, and each is named`() {
        // The whole point of the 2026-09-22 change: people put the phone on the
        // floor in front of them, and a check that blocks the commonest
        // placement stops the app working.
        val sideOn = preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.05f)))
        assertTrue(sideOn.canStart)
        assertEquals(CameraView.SIDE, sideOn.view)

        val faceOn = preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.5f)))
        assertTrue(faceOn.canStart)
        assertEquals(CameraView.FRONT, faceOn.view)
    }

    @Test
    fun `a view between the two is named oblique and still accepted`() {
        val report = preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.28f)))
        assertEquals(CameraView.OBLIQUE, report.view)
        assertTrue(report.canStart, "whether oblique should block is spike S2's question, not a guess")
    }

    @Test
    fun `the view is the same however the phone is propped`() {
        // The bug this replaced: frames are rotated to the display's
        // orientation and the app is portrait-locked, so a phone propped in
        // landscape made a correctly positioned member read as vertical. The
        // only way to pass was to lie along one axis of the frame.
        for (rotation in listOf(0f, 30f, 45f, 90f, 135f, 180f, 270f)) {
            assertEquals(
                CameraView.SIDE,
                preflight.evaluate(
                    good(image = Traces.imageFrame(shoulderSpan = 0.05f, torsoDegrees = rotation)),
                ).view,
                "a side-on body at $rotation° in frame should still read as side-on",
            )
        }
    }

    @Test
    fun `a body too small to judge fails the view check, and says so`() {
        // The one thing this pill still gates on: not knowing what it is
        // looking at. "Move the phone so it can see all of you" is the fix for
        // somebody too far away to measure.
        val tiny = Traces.imageFrame(shoulderSpan = 0.5f).let { frame ->
            PoseFrame(
                frame.timestampMillis,
                frame.landmarks.map { it.copy(x = it.x * 0.01f, y = it.y * 0.01f) },
            )
        }
        val report = preflight.evaluate(good(image = tiny))
        assertEquals(null, report.view)
        assertFalse(report.canStart)
        assertTrue(report.fixes.contains("Move the phone so it can see all of you"))
    }

    @Test
    fun `a busy background fails, and says to move`() {
        val report = preflight.evaluate(good(backgroundMotion = 0.6f))
        assertFalse(report.canStart)
        assertEquals(listOf("Find a spot with nothing moving behind you"), report.fixes)
    }

    @Test
    fun `a green pill carries no fix`() {
        assertTrue(preflight.evaluate(good()).statuses.all { it.fix == null })
    }
}
