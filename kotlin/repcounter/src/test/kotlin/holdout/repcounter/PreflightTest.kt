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
    fun `a camera looking at the member face-on fails the angle check`() {
        // Shoulders spread right across the frame: the phone is in front of
        // them, where it cannot see an elbow bend at all (research R4).
        val report = preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.5f)))
        assertFalse(report.canStart)
        assertTrue(report.fixes.contains("Put the phone to your side, not in front of you"))
    }

    @Test
    fun `a side-on body passes whichever way the phone is propped`() {
        // The bug this replaced: frames are rotated to the display's
        // orientation and the app is portrait-locked, so a phone propped in
        // landscape made a correctly positioned member read as vertical. The
        // only way to pass was to lie along one axis of the frame.
        for (rotation in listOf(0f, 30f, 45f, 90f, 135f, 180f, 270f)) {
            assertTrue(
                preflight.evaluate(
                    good(image = Traces.imageFrame(shoulderSpan = 0.05f, torsoDegrees = rotation)),
                ).canStart,
                "a side-on body at $rotation° in frame should still pass",
            )
        }
    }

    @Test
    fun `a body too small in frame to judge passes rather than failing twice`() {
        // WHOLE_BODY already owns "the model cannot see you". This pill going
        // red for the same reason would give the member two fixes for one
        // problem, and one of them would be the wrong fix.
        val tiny = Traces.imageFrame(shoulderSpan = 0.5f, torsoDegrees = 0f).let { frame ->
            PoseFrame(
                frame.timestampMillis,
                frame.landmarks.map { it.copy(x = it.x * 0.01f, y = it.y * 0.01f) },
            )
        }
        val angle = preflight.evaluate(good(image = tiny))
            .statuses.single { it.check == PreflightCheck.CAMERA_ANGLE }
        assertTrue(angle.passing)
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
