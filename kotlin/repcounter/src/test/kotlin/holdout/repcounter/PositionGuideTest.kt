package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The outline the member lies into, and the one correction shown at a time. */
class PositionGuideTest {

    private val guide = PositionGuide()
    private val floor = FloorView.Horizon(0.35f)

    /** A body filling [halfWidth] x [halfHeight] of the frame at ([cx], [cy]). */
    private fun bodyAt(cx: Float, cy: Float, halfWidth: Float, halfHeight: Float): PoseFrame {
        val corners = listOf(
            cx - halfWidth to cy - halfHeight,
            cx + halfWidth to cy + halfHeight,
            cx - halfWidth to cy + halfHeight,
            cx + halfWidth to cy - halfHeight,
        )
        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(cx, cy, 0f, 1f) }
        corners.forEachIndexed { i, (x, y) -> landmarks[i] = Landmark(x, y, 0f, 1f) }
        return PoseFrame(0, landmarks)
    }

    private fun matching(view: CameraView): PoseFrame {
        val target = guide.guide(null, view, floor).target
        return bodyAt(target.centreX, target.centreY, target.halfWidth, target.halfHeight)
    }

    @Test
    fun `in the wider 3 by 4 picture the outline asks for the same body, so it is narrower in it`() {
        val narrow = guide.guide(null, CameraView.OBLIQUE, floor).target
        val wide = guide.guide(null, CameraView.OBLIQUE, floor, xScale = 0.75f).target
        assertEquals(narrow.halfWidth * 0.75f, wide.halfWidth, 1e-6f)
        assertEquals(narrow.halfHeight, wide.halfHeight, "the height is the same picture")
        // A body sized for the outline is still aligned, and a small sideways offset still is too.
        val body = bodyAt(wide.centreX + 0.05f, wide.centreY, wide.halfWidth, wide.halfHeight)
        assertTrue(guide.guide(body, CameraView.OBLIQUE, floor, xScale = 0.75f).aligned)
    }

    @Test
    fun `the outline is long and flat from the side, foreshortened from the front`() {
        val side = guide.guide(null, CameraView.SIDE, floor).target
        val front = guide.guide(null, CameraView.FRONT, floor).target

        assertTrue(side.halfWidth > side.halfHeight, "a body seen from the side lies across the frame")
        assertTrue(front.halfHeight > front.halfWidth, "head-on, the same body is foreshortened")
        assertTrue(side.halfWidth > front.halfWidth)
    }

    @Test
    fun `the outline sits on the floor, below the horizon`() {
        val target = guide.guide(null, CameraView.SIDE, FloorView.Horizon(0.3f)).target
        assertTrue(target.centreY > 0.3f, "the member lies on the floor, not on the wall")
        assertTrue(target.bottom <= 1f && target.top >= 0f, "the outline has to be on screen")
    }

    @Test
    fun `a phone looking at the ceiling says so before anybody is in frame`() {
        val guidance = guide.guide(null, null, FloorView.NoFloor)
        assertEquals(PositionHint.AIM_AT_THE_FLOOR, guidance.hint)
        assertNull(guidance.horizonY)
    }

    @Test
    fun `nobody in frame asks them to get in front of the camera`() {
        assertEquals(PositionHint.GET_IN_FRAME, guide.guide(null, null, floor).hint)
    }

    @Test
    fun `standing up asks them to get down`() {
        // The whole body sits above where the floor begins.
        val standing = bodyAt(cx = 0.5f, cy = 0.15f, halfWidth = 0.06f, halfHeight = 0.12f)
        assertEquals(PositionHint.GET_DOWN, guide.guide(standing, CameraView.SIDE, floor).hint)
    }

    @Test
    fun `too close says move back, too far says move closer`() {
        val target = guide.guide(null, CameraView.SIDE, floor).target

        val tooBig = bodyAt(target.centreX, target.centreY, target.halfWidth * 1.6f, target.halfHeight * 1.6f)
        assertEquals(PositionHint.MOVE_BACK, guide.guide(tooBig, CameraView.SIDE, floor).hint)

        val tooSmall = bodyAt(target.centreX, target.centreY, target.halfWidth * 0.4f, target.halfHeight * 0.4f)
        assertEquals(PositionHint.MOVE_CLOSER, guide.guide(tooSmall, CameraView.SIDE, floor).hint)
    }

    @Test
    fun `left and right are the member's own, because the preview is mirrored`() {
        val target = guide.guide(null, CameraView.SIDE, floor).target
        val towardsHighSensorX = bodyAt(
            cx = target.centreX + 0.3f,
            cy = target.centreY,
            halfWidth = target.halfWidth,
            halfHeight = target.halfHeight,
        )

        // The chain, spelled out because a sign error here sends people the
        // wrong way and looks like a bug in the camera:
        //
        //   landmarks are raw sensor coordinates, never mirrored
        //   the preview IS mirrored, so screen x = 1 − sensor x
        //   a mirror preserves the member's own left and right — that is what
        //     mirroring the preview is for
        //
        // So a body at high sensor x appears on the LEFT of the screen, which
        // the member reads as their own left, and the fix is to move right.
        assertEquals(
            PositionHint.MOVE_RIGHT,
            guide.guide(towardsHighSensorX, CameraView.SIDE, floor, mirrored = true).hint,
        )

        // Unmirrored, the screen shows the sensor's own framing and the
        // instruction flips with it.
        assertEquals(
            PositionHint.MOVE_LEFT,
            guide.guide(towardsHighSensorX, CameraView.SIDE, floor, mirrored = false).hint,
        )
    }

    @Test
    fun `a body in the outline is aligned, and stops being nagged`() {
        val guidance = guide.guide(matching(CameraView.SIDE), CameraView.SIDE, floor)
        assertTrue(guidance.aligned, "fit was ${guidance.fit}")
        assertNull(guidance.hint, "a member who is already there should not be told anything")
        assertNotNull(guidance.bodyBox)
    }

    @Test
    fun `the same body is aligned head-on against the head-on outline`() {
        val guidance = guide.guide(matching(CameraView.FRONT), CameraView.FRONT, floor)
        assertTrue(guidance.aligned, "fit was ${guidance.fit}")
    }

    @Test
    fun `an unknown view still gives something to aim at`() {
        // Before anybody is in frame there is nothing to classify, so the
        // outline starts between the two rather than guessing and then jumping.
        val unknown = guide.guide(null, null, floor).target
        val side = guide.guide(null, CameraView.SIDE, floor).target
        val front = guide.guide(null, CameraView.FRONT, floor).target
        assertTrue(unknown.halfWidth in front.halfWidth..side.halfWidth)
    }
}
