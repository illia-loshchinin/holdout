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
        // Head-on: the Daily 25's placement, which `Preflight()` checks for.
        image: PoseFrame? = Traces.imageFrame(shoulderSpan = 0.7f),
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
    fun `a moving background is shown but never blocks the start`() {
        val report = preflight.evaluate(good(backgroundMotion = 0.5f))
        assertTrue(report.canStart)
        assertFalse(report.statuses.single { it.check == PreflightCheck.BACKGROUND_STILL }.passing)
        assertEquals(listOf("Find a spot with nothing moving behind you"), report.fixes)
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
    fun `a joint the model places outside the picture is not in view, however sure it is`() {
        // MediaPipe extrapolates a cropped ankle with high presence; only its
        // position in the picture says it is not there.
        val image = Traces.imageFrame(shoulderSpan = 0.7f)
        val cropped = PoseFrame(
            image.timestampMillis,
            image.landmarks.mapIndexed { i, l -> if (i == PoseIndex.LEFT_ANKLE) l.copy(y = 1.08f) else l },
        )
        val report = preflight.evaluate(good(image = cropped))
        assertFalse(report.canStart)
        assertTrue(report.fixes.contains("Step back so your whole body is visible"))
    }

    @Test
    fun `every placement is named`() {
        assertEquals(CameraView.SIDE, preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.05f))).view)
        assertEquals(CameraView.OBLIQUE, preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.4f))).view)
        assertEquals(CameraView.FRONT, preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.7f))).view)
    }

    @Test
    fun `45 degrees is not mistaken for head-on`() {
        // The bug the 1.55 cut-off fixed: at 45° the shoulder span against the
        // torso reached 1.35 on a real recording, and the old 0.9 called most
        // of those frames head-on — refusing a correctly placed phone.
        val atFortyFive = Traces.imageFrame(shoulderSpan = 0.4f * 1.35f)
        assertEquals(CameraView.OBLIQUE, preflight.evaluate(good(image = atFortyFive)).view)
    }

    @Test
    fun `the Daily 25 starts head-on or at 45 degrees, and square side-on says where the phone goes`() {
        assertTrue(preflight.evaluate(good()).canStart)
        assertTrue(preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.4f))).canStart)
        val report = preflight.evaluate(good(image = Traces.imageFrame(shoulderSpan = 0.05f)))
        assertFalse(report.canStart)
        assertEquals(listOf(Stakes.DAILY.cameraFix), report.fixes)
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
    fun `the same body is the same placement in the sensor's full 3 by 4 picture`() {
        // The 9:16 picture the thresholds were set in is the middle three
        // quarters of the 3:4 one, at the same height.
        fun widened(frame: PoseFrame) = PoseFrame(frame.timestampMillis, frame.landmarks.map { it.copy(x = 0.125f + 0.75f * it.x) })
        val xScale = Aspect.xScale(width = 960, height = 1280)
        assertEquals(0.75f, xScale)
        for (span in listOf(0.05f, 0.4f, 0.4f * 1.35f, 0.7f)) {
            val narrow = Traces.imageFrame(shoulderSpan = span)
            assertEquals(preflight.classifyView(narrow), preflight.classifyView(widened(narrow), xScale), "shoulder span $span")
        }
    }

    private fun wholeBody(report: PreflightReport) = report.statuses.single { it.check == PreflightCheck.WHOLE_BODY }

    @Test
    fun `the hold starts only with the head clearly in the picture`() {
        val image = Traces.imageFrame(shoulderSpan = 0.7f)
        fun withNose(y: Float) = PoseFrame(image.timestampMillis, image.landmarks.mapIndexed { i, l -> if (i == PoseIndex.NOSE) l.copy(y = y) else l })
        assertTrue(wholeBody(preflight.evaluate(good(image = withNose(0.3f)))).passing)
        val atTheEdge = wholeBody(preflight.evaluate(good(image = withNose(0.01f))))
        assertFalse(atTheEdge.passing, "a head at the picture's edge is on its way out of it")
        assertEquals(Preflight.HEAD_FIX, atTheEdge.fix)
    }

    @Test
    fun `a staked sitting starts only with the near knee clear of the picture's edges`() {
        val staked = Preflight(stakes = Stakes.STAKED)
        fun pose(kneeX: Float): ImagePose {
            val base = Traces.angledImage(0, elbowDegrees = 175f)
            val landmarks = base.frame.landmarks.mapIndexed { i, l ->
                when (i) {
                    PoseIndex.LEFT_KNEE -> Landmark(kneeX, 0.5f, 0f, 1f)
                    PoseIndex.NOSE -> Landmark(0.5f, 0.3f, 0f, 1f)
                    else -> l
                }
            }
            return ImagePose(PoseFrame(0, landmarks), base.width, base.height)
        }
        fun report(kneeX: Float) = pose(kneeX).let { p -> staked.evaluate(PreflightInput(0.5f, 0f, Traces.frame(0, 175f, 180f), p.frame, p)) }
        assertTrue(wholeBody(report(0.5f)).passing)
        // Where the model puts a knee that has left the frame: just inside the edge.
        val guessed = wholeBody(report(0.05f))
        assertFalse(guessed.passing)
        assertEquals(Preflight.KNEES_FIX, guessed.fix)
        // A knee the model cannot find at all is hidden behind the body: turning shows it.
        val hidden = pose(0.5f).let { p ->
            ImagePose(PoseFrame(0, p.frame.landmarks.mapIndexed { i, l -> if (i == PoseIndex.LEFT_KNEE) l.copy(presence = 0.2f) else l }), p.width, p.height)
        }
        // This synthetic body shows its left side to the lens: the phone is on its left, so it turns right.
        assertEquals(Preflight.KNEES_TURN_RIGHT_FIX, wholeBody(staked.evaluate(PreflightInput(0.5f, 0f, Traces.frame(0, 175f, 180f), hidden.frame, hidden))).fix)
        // The Daily 25 does not judge knees, so it does not ask for them.
        assertTrue(wholeBody(Preflight().evaluate(PreflightInput(0.5f, 0f, Traces.frame(0, 175f, 180f), pose(0.05f).frame, pose(0.05f)))).passing)
    }

    @Test
    fun `a staked sitting starts only with the near foot clear of the picture's edges`() {
        val staked = Preflight(stakes = Stakes.STAKED)
        fun pose(ankleX: Float): ImagePose {
            val base = Traces.angledImage(0, elbowDegrees = 175f)
            val landmarks = base.frame.landmarks.mapIndexed { i, l ->
                when (i) {
                    PoseIndex.LEFT_ANKLE -> l.copy(x = ankleX)
                    PoseIndex.NOSE -> Landmark(0.5f, 0.3f, 0f, 1f)
                    else -> l
                }
            }
            return ImagePose(PoseFrame(0, landmarks), base.width, base.height)
        }
        fun report(stakes: Preflight, ankleX: Float) = pose(ankleX).let { p -> stakes.evaluate(PreflightInput(0.5f, 0f, Traces.frame(0, 175f, 180f), p.frame, p)) }
        assertTrue(wholeBody(report(staked, 0.8f)).passing)
        // In the picture, but at its edge: the model drops it every few frames once the reps start.
        val atTheEdge = wholeBody(report(staked, 0.97f))
        assertFalse(atTheEdge.passing)
        assertEquals(Preflight.FEET_FIX, atTheEdge.fix)
        assertTrue(wholeBody(report(Preflight(), 0.97f)).passing, "the Daily 25 does not judge the legs")
    }

    @Test
    fun `a staked sitting refuses a phone too far in front, and says which way to turn`() {
        val staked = Preflight(stakes = Stakes.STAKED)
        fun report(image: PoseFrame) = staked.evaluate(PreflightInput(0.5f, 0f, Traces.frame(0, 175f, 180f), image, ImagePose(image, 720, 1280)))
        fun cameraFix(report: PreflightReport) = report.statuses.single { it.check == PreflightCheck.CAMERA_ANGLE }.fix

        // Ratio 1.25 — about 30° on the floor: the Daily 25 counts from there, a staked sitting does not start.
        val tooFarInFront = Traces.imageFrame(shoulderSpan = 0.5f)
        assertEquals(CameraView.OBLIQUE, preflight.evaluate(good(image = tooFarInFront)).view)
        val refused = report(tooFarInFront)
        assertEquals(CameraView.FRONT, refused.view)
        assertFalse(refused.canStart)
        // Away from the phone, whichever side it stands on.
        val phoneOn = Angles.nearSide(ImagePose(tooFarInFront, 720, 1280))
        assertEquals(if (phoneOn == Side.RIGHT) Preflight.TOO_FRONT_TURN_LEFT_FIX else Preflight.TOO_FRONT_TURN_RIGHT_FIX, cameraFix(refused))

        // Beside the member: towards it.
        val beside = Traces.imageFrame(shoulderSpan = 0.05f)
        val besideSide = Angles.nearSide(ImagePose(beside, 720, 1280))
        assertEquals(if (besideSide == Side.RIGHT) Preflight.TOO_SIDE_TURN_RIGHT_FIX else Preflight.TOO_SIDE_TURN_LEFT_FIX, cameraFix(report(beside)))

        // Square head-on neither side is nearer: only the phone moving will do.
        assertEquals(Stakes.STAKED.cameraFix, cameraFix(report(Traces.imageFrame(shoulderSpan = 0.7f))))
        // And at 45° it starts.
        assertEquals(CameraView.OBLIQUE, report(Traces.imageFrame(shoulderSpan = 0.3f)).view)
    }

    @Test
    fun `a green pill carries no fix`() {
        assertTrue(preflight.evaluate(good()).statuses.all { it.fix == null })
    }
}
