package holdout.repcounter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The outline the member lies into, and the one thing to tell them next
 * (T046, T050).
 *
 * The pre-flight checks say *what is wrong*. They never said *where to
 * put your body*, which is the question somebody standing over a phone on the
 * floor is actually asking. This answers it with a shape on the floor and a
 * single correction at a time.
 *
 * **It guides; it does not gate.** [Guidance.aligned] turns the outline green
 * and nothing more — `ReadyGate` still starts the sitting on the pre-flight
 * checks plus a held plank. An outline placed by estimated geometry must not be
 * able to lock anybody out of their own workout, and a view too poor to measure
 * already fails in a way the member can see: the reps do not count.
 */
data class GuideBox(
    val centreX: Float,
    val centreY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
) {
    val left get() = centreX - halfWidth
    val right get() = centreX + halfWidth
    val top get() = centreY - halfHeight
    val bottom get() = centreY + halfHeight
    val area get() = (4f * halfWidth * halfHeight).coerceAtLeast(1e-6f)

    /** Intersection over union with [other], 0..1. */
    fun overlap(other: GuideBox): Float {
        val w = max(0f, min(right, other.right) - max(left, other.left))
        val h = max(0f, min(bottom, other.bottom) - max(top, other.top))
        val intersection = w * h
        val union = area + other.area - intersection
        return if (union <= 0f) 0f else (intersection / union).coerceIn(0f, 1f)
    }
}

/** One correction, spoken and shown. Never more than one at a time. */
enum class PositionHint(val message: String) {
    AIM_AT_THE_FLOOR("Stand the phone up so it can see the floor"),
    GET_IN_FRAME("Get in front of the camera"),
    GET_DOWN("Get down into a plank"),
    MOVE_BACK("Move back"),
    MOVE_CLOSER("Move closer"),
    MOVE_LEFT("Move left"),
    MOVE_RIGHT("Move right"),
    PHONE_AT_45("Turn the phone to 45°"),
}

data class Guidance(
    /** Where the floor starts, for drawing. Null when the whole frame is floor or none is. */
    val horizonY: Float?,
    val target: GuideBox,
    /** Where the member is now, or null when the model sees nobody. */
    val bodyBox: GuideBox?,
    val fit: Float,
    val hint: PositionHint?,
    val aligned: Boolean,
)

class PositionGuide(
    /** Overlap at which the outline goes green. Provisional — spike S2 sets it. */
    private val alignedFit: Float = 0.55f,
    private val offCentreTolerance: Float = 0.12f,
) {

    fun guide(
        pose: PoseFrame?,
        view: CameraView?,
        floor: FloorView,
        /**
         * Whether the preview the member is looking at is mirrored, as it is
         * for the front camera. Left and right are theirs, not the sensor's,
         * and getting this backwards sends people the wrong way.
         */
        mirrored: Boolean = true,
        /**
         * The placements these stakes allow, or null when any will do. A staked
         * sitting is side-on or angled, never head-on ([Stakes]); in any other
         * view it is shown the angled outline and told to move the phone
         * before anything else.
         */
        allowedViews: Set<CameraView>? = null,
        /**
         * [Aspect.xScale] of the picture: the outline was sized in 9:16, and in
         * a wider picture the same body takes a narrower share of it, so the
         * outline narrows with it and still asks for the same distance.
         */
        xScale: Float = 1f,
    ): Guidance {
        val horizonY = (floor as? FloorView.Horizon)?.y
        val wrongPlacement = allowedViews != null && view != null && view !in allowedViews
        // The outline for where the phone is if that is allowed, else for 45° —
        // the one placement every stakes accepts.
        val target = targetFor(if (wrongPlacement) CameraView.OBLIQUE else view, horizonY).let { it.copy(halfWidth = it.halfWidth * xScale) }
        val body = boundingBox(pose)
        val fit = body?.let { target.overlap(it) } ?: 0f

        val hint = when {
            floor is FloorView.NoFloor -> PositionHint.AIM_AT_THE_FLOOR
            body == null -> PositionHint.GET_IN_FRAME
            // Standing up: the whole body sits above where the floor begins.
            horizonY != null && body.bottom < horizonY -> PositionHint.GET_DOWN
            wrongPlacement -> PositionHint.PHONE_AT_45
            body.area > target.area * TOO_BIG -> PositionHint.MOVE_BACK
            body.area < target.area * TOO_SMALL -> PositionHint.MOVE_CLOSER
            else -> sideways(body, target, mirrored, xScale)
        }

        return Guidance(
            horizonY = horizonY,
            target = target,
            bodyBox = body,
            fit = fit,
            // The wrong placement is never "aligned", however well the body
            // happens to fill the outline: it is the one thing that must change.
            hint = if (fit >= alignedFit && !wrongPlacement) null else hint,
            aligned = fit >= alignedFit && !wrongPlacement,
        )
    }

    /**
     * The outline's shape is the point of classifying the view at all: a body
     * seen from the side lies long and flat across the frame, and the same body
     * seen head-on is foreshortened into something much narrower and taller.
     * Showing one shape for both would send half the members to the wrong spot.
     */
    private fun targetFor(view: CameraView?, horizonY: Float?): GuideBox {
        val (halfWidth, halfHeight) = when (view) {
            CameraView.SIDE -> 0.42f to 0.15f
            CameraView.FRONT -> 0.22f to 0.27f
            // Unknown until somebody is in frame to classify, so start between
            // the two rather than guessing and then jumping.
            else -> 0.34f to 0.21f
        }

        val floorTop = horizonY ?: DEFAULT_FLOOR_TOP
        val band = (1f - floorTop).coerceAtLeast(2f * halfHeight)
        val centreY = (floorTop + band * 0.55f).coerceIn(halfHeight, 1f - halfHeight)
        return GuideBox(0.5f, centreY, halfWidth, halfHeight)
    }

    private fun sideways(body: GuideBox, target: GuideBox, mirrored: Boolean, xScale: Float): PositionHint? {
        val offset = body.centreX - target.centreX
        if (abs(offset) <= offCentreTolerance * xScale) return null
        // In the sensor's frame the member is at `offset`; in a mirrored
        // preview they see themselves on the other side, and the instruction
        // has to match what they see.
        val asSeen = if (mirrored) -offset else offset
        return if (asSeen > 0f) PositionHint.MOVE_LEFT else PositionHint.MOVE_RIGHT
    }

    /** The box around every landmark the model is confident about. */
    private fun boundingBox(pose: PoseFrame?): GuideBox? {
        val visible = pose?.landmarks?.filter { it.presence >= Angles.PRESENCE_THRESHOLD }
        if (visible.isNullOrEmpty()) return null

        val minX = visible.minOf { it.x }
        val maxX = visible.maxOf { it.x }
        val minY = visible.minOf { it.y }
        val maxY = visible.maxOf { it.y }
        return GuideBox(
            centreX = (minX + maxX) / 2f,
            centreY = (minY + maxY) / 2f,
            halfWidth = ((maxX - minX) / 2f).coerceAtLeast(1e-3f),
            halfHeight = ((maxY - minY) / 2f).coerceAtLeast(1e-3f),
        )
    }

    private companion object {
        /** Where the floor is assumed to start when the tilt is unknown. */
        const val DEFAULT_FLOOR_TOP = 0.3f
        const val TOO_BIG = 1.45f
        const val TOO_SMALL = 0.55f
    }
}
