package holdout.repcounter

import kotlin.math.abs
import kotlin.math.tan

/**
 * Where the phone is, and therefore where the floor is (T046, research R4).
 *
 * Two independent questions, answered from two independent sources:
 *
 * - **Which way is the camera looking at the member?** From the pose itself —
 *   [CameraView], classified in [Preflight].
 * - **Where does the floor start in the picture?** From gravity and the lens,
 *   which needs no body at all. That matters: a phone lying on its back is
 *   looking at the ceiling, and the member should be told so *before* they get
 *   down on the floor to find out.
 */
enum class CameraView {
    /** The camera is beside the member: one shoulder behind the other. */
    SIDE,

    /** Somewhere between the two. */
    OBLIQUE,

    /** The camera is in front of or behind the member: shoulders spread wide. */
    FRONT,
}

/** The camera's optical axis against the horizontal. Positive is tilted up. */
data class Tilt(val pitchDegrees: Float)

/** The lens's vertical field of view, in the frame's own orientation. */
data class Lens(val verticalFovDegrees: Float) {
    init {
        require(verticalFovDegrees > 0f && verticalFovDegrees < 180f) {
            "a vertical field of view of $verticalFovDegrees° is not a lens"
        }
    }

    companion object {
        /**
         * When the camera characteristics cannot be read. A typical phone front
         * camera is near enough this that the horizon lands in the right half
         * of the frame, which is all the guide needs.
         */
        val DEFAULT = Lens(55f)
    }
}

/** What the camera can see of the floor. */
sealed interface FloorView {
    /** The horizon is in frame at [y], normalised 0..1. Below it is floor. */
    data class Horizon(val y: Float) : FloorView

    /** The camera is angled down far enough that the whole frame is floor. */
    data object AllFloor : FloorView

    /** The camera is looking above the horizontal: there is no floor in frame. */
    data object NoFloor : FloorView
}

object Placement {

    /**
     * Where the floor begins, from the phone's tilt and its lens alone.
     *
     * Pinhole projection: a ray [pitch] below the optical axis lands at
     * `0.5 + 0.5 · tan(pitch) / tan(vFov / 2)`. The horizontal ray is the
     * horizon, so tilting the camera up pushes the horizon *down* the frame and
     * leaves less floor in view.
     *
     * This is an estimate, not a survey. It assumes a flat floor and a level
     * roll, which is what a phone propped against something actually gives.
     * Its job is to place a guide outline in roughly the right half of the
     * screen and to catch a phone aimed at the ceiling — both of which survive
     * a few degrees of error comfortably.
     */
    fun floorView(tilt: Tilt, lens: Lens): FloorView {
        val half = lens.verticalFovDegrees / 2f

        // Straight up or straight down: no projection needed, and tan() is
        // about to be useless anyway.
        if (tilt.pitchDegrees - half >= 0f) return FloorView.NoFloor
        if (tilt.pitchDegrees + half <= 0f) return FloorView.AllFloor

        val y = 0.5f + 0.5f * (tan(radians(tilt.pitchDegrees)) / tan(radians(half)))
        return when {
            y <= 0f -> FloorView.AllFloor
            y >= 1f -> FloorView.NoFloor
            else -> FloorView.Horizon(y)
        }
    }

    /**
     * The pitch of the camera's optical axis, from a gravity reading in the
     * device's own coordinates.
     *
     * Android's sensor frame with the phone held upright: +x right, +y up the
     * screen, +z out of the screen towards the viewer. The **front** camera
     * looks along +z, so the angle between +z and the horizontal plane is the
     * pitch, and gravity's z component gives it directly: lying on its back the
     * phone has gravity along −z and the camera points straight up.
     */
    fun pitchFromGravity(x: Float, y: Float, z: Float): Tilt {
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
        if (magnitude < 1e-3f) return Tilt(0f)
        // −z·g normalised is sin(pitch): +1 looking straight up, −1 straight down.
        val sine = (-z / magnitude).coerceIn(-1f, 1f)
        return Tilt(degrees(kotlin.math.asin(sine)))
    }

    private fun radians(degrees: Float) = Math.toRadians(degrees.toDouble()).toFloat()

    private fun degrees(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

    /** True when [a] and [b] are the same angle to within [tolerance] degrees. */
    internal fun sameAngle(a: Float, b: Float, tolerance: Float = 0.5f) = abs(a - b) <= tolerance
}
