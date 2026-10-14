package holdout.app.sitting

import holdout.repcounter.Guidance
import holdout.repcounter.GuideBox
import holdout.repcounter.PositionHint

/**
 * What the pre-flight draws over the camera to show where to lie (001 FR-019, amended 2026-10-13): the floor
 * line, found from the phone's own tilt, and a faint outline of the body shaped for the placement in use. **No
 * frame is drawn around it** — the skeleton and that faint outline show the place, and the check pills say what
 * is wrong. Pure, so a test can say what is drawn without a camera.
 */
internal object PositionOverlay {
    sealed interface Part {
        /** The floor starts at [y], 0 to 1 down the frame. */
        data class Horizon(val y: Float) : Part

        /** A faint outline of a body lying inside [box] (frame fractions), the placement's target. */
        data class BodyOutline(val box: GuideBox) : Part
    }

    /**
     * The parts to draw for [guidance]: the floor line when the tilt places one, and the body outline. Nothing
     * while the phone does not see the floor (there is no floor to lie on, and the outline would only cover the
     * words that say so), and nothing once the member is in position: it disappears then and does not return.
     */
    fun parts(guidance: Guidance): List<Part> {
        if (guidance.aligned || guidance.hint == PositionHint.AIM_AT_THE_FLOOR) return emptyList()
        return listOfNotNull(guidance.horizonY?.let { Part.Horizon(it) }, Part.BodyOutline(guidance.target))
    }

    /**
     * The outline's closed path in the box's own unit square (x across, y down): a tapered slab, the
     * shoulders and back rising on the left and the legs lying out to the right, as the board draws it.
     */
    val OUTLINE_UNITS: List<Segment> = listOf(
        Segment.Move(0f, 0.667f),
        Segment.Curve(0.174f, 0.4f, 0.348f, 0.1f, 0.626f, 0f),
        Segment.Line(1f, 0.4f),
        Segment.Line(1f, 0.767f),
        Segment.Line(0f, 1f),
    )

    sealed interface Segment {
        data class Move(val x: Float, val y: Float) : Segment

        data class Line(val x: Float, val y: Float) : Segment

        data class Curve(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Segment
    }
}
