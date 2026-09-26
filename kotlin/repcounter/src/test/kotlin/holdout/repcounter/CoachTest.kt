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
            assertTrue(coach.words.split(' ').size <= 3 && coach.words.length <= 18, "\"${coach.words}\" is too long")
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
    fun `all green says hold`() {
        assertEquals(Coach.HOLD, Coach.next(report(), guidance(null)))
    }
}
