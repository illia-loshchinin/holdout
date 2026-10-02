package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One short correction at a time, in the order they have to happen. */
class CoachTest {

    private fun report(vararg failing: PreflightCheck, view: CameraView? = CameraView.FRONT) = PreflightReport(
        statuses = PreflightCheck.entries.map { PreflightStatus(it, passing = it !in failing) },
        view = view,
    )

    private fun guidance(hint: PositionHint?) = Guidance(
        horizonY = 0.35f,
        target = GuideBox(0.5f, 0.6f, 0.3f, 0.2f),
        bodyBox = null,
        fit = if (hint == null) 1f else 0f,
        hint = hint,
        aligned = hint == null,
    )

    @Test
    fun `every correction fits on the screen from the floor - three short words at most`() {
        for (coach in Coach.entries) {
            // 19 characters: "Turn slightly right", still three words.
            assertTrue(coach.words.split(' ').size <= 3 && coach.words.length <= 19, "\"${coach.words}\" is too long")
        }
    }

    @Test
    fun `nothing seen yet says get in view`() {
        assertEquals(Coach.GET_IN_VIEW, Coach.next(null, null))
    }

    @Test
    fun `light comes before everything the model reads`() {
        assertEquals(Coach.MORE_LIGHT, Coach.next(report(PreflightCheck.LIGHTING, PreflightCheck.WHOLE_BODY), guidance(PositionHint.MOVE_BACK)))
    }

    @Test
    fun `the phone comes before the body`() {
        assertEquals(Coach.TURN_45, Coach.next(report(PreflightCheck.CAMERA_ANGLE), guidance(PositionHint.GET_DOWN)))
    }

    @Test
    fun `too close and too far are one word each`() {
        assertEquals(Coach.FURTHER, Coach.next(report(), guidance(PositionHint.MOVE_BACK)))
        assertEquals(Coach.FURTHER, Coach.next(report(PreflightCheck.WHOLE_BODY), guidance(null)))
        assertEquals(Coach.CLOSER, Coach.next(report(), guidance(PositionHint.MOVE_CLOSER)))
    }

    @Test
    fun `a hidden knee asks the member to turn away, a knee at the edge asks for the phone further off`() {
        fun kneesReport(fix: String) = PreflightReport(
            statuses = PreflightCheck.entries.map { if (it == PreflightCheck.WHOLE_BODY) PreflightStatus(it, passing = false, specificFix = fix) else PreflightStatus(it, passing = true) },
            view = CameraView.OBLIQUE,
        )
        assertEquals(Coach.TURN_LEFT, Coach.next(kneesReport(Preflight.KNEES_TURN_LEFT_FIX), guidance(null)), "phone on the right: turn left, away from it")
        assertEquals(Coach.TURN_RIGHT, Coach.next(kneesReport(Preflight.KNEES_TURN_RIGHT_FIX), guidance(null)))
        assertEquals(Coach.TURN_AWAY, Coach.next(kneesReport(Preflight.KNEES_TURN_FIX), guidance(null)), "which side faces the phone is unclear")
        assertEquals(Coach.FURTHER, Coach.next(kneesReport(Preflight.KNEES_FIX), guidance(null)))
    }

    @Test
    fun `all green says hold`() {
        assertEquals(Coach.HOLD, Coach.next(report(), guidance(null)))
    }

    @Test
    fun `all green and not yet in position says what the position lacks`() {
        assertEquals(Coach.ARMS_STRAIGHT, Coach.next(report(), guidance(null), HoldBlocker.ARMS))
        assertEquals(Coach.KNEES_UP, Coach.next(report(), guidance(null), HoldBlocker.KNEES))
        assertEquals(Coach.KNEES_UNCLEAR, Coach.next(report(), guidance(null), HoldBlocker.KNEES_UNREADABLE))
        assertEquals(Coach.BODY_STRAIGHT, Coach.next(report(), guidance(null), HoldBlocker.BODY))
        // A pill's own fix comes first: the position is not judged until the picture is right.
        assertEquals(Coach.FURTHER, Coach.next(report(PreflightCheck.WHOLE_BODY), guidance(null), HoldBlocker.ARMS))
        assertEquals(Coach.CLOSER, Coach.next(report(), guidance(PositionHint.MOVE_CLOSER), HoldBlocker.KNEES))
    }
}
