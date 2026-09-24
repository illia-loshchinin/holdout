package holdout.repcounter

/**
 * One frame reduced to what the push-up rule reads, **measured the way the
 * camera's placement can actually see it**. There are two placements, one per
 * stakes ([Stakes]):
 *
 * **Head-on — the Daily 25.** The body points into the lens, and the 3D
 * estimate of everything along the depth axis is poor. Measured on a real
 * head-on recording (`fixtures/rep-traces/front-honest-01.csv`), the world
 * landmarks read the elbow 155–163° at the top and ≈ 87° at the bottom — so
 * nothing counted — while the picture read 175–179° and ≈ 40°. So head-on:
 *
 * - **the elbow is read from the picture**, both arms averaged. The elbows
 *   flare across the image as they bend, and a straight arm is straight in any
 *   projection;
 * - **the body line is not judged** — shoulder, hip and ankle lie in a plane
 *   through the lens, so a sag moves along the line of sight. A coarser check
 *   the camera *can* make takes its place: the torso is down, not upright
 *   ([Angles.torsoElevation]), so arm bends while standing count nothing;
 * - **knee push-ups are not caught.** Stated, not hidden, and the reason money
 *   is never staked on this placement.
 *
 * **At 45° — staked sittings.** The plane the body bends in is turned towards
 * the lens, so a straight arm, body and leg read straight and a bend reads as
 * a bend. Everything is read **in the picture, on the side nearer the lens**
 * ([Angles.nearSide]): the far limbs are hidden behind the body and the model
 * guesses them, and on the angled recording the 3D elbow topped out at
 * 150–155° on straight arms while the picture read 162–170°. The knee is read
 * too, so a push-up from the knees is discarded.
 *
 * World landmarks are the fallback only when there is no picture at all — the
 * synthetic traces that test the state machine. On a phone there always is one,
 * and a frame whose joints are not clearly visible in it is dropped, not scored
 * from another source, so the reading never jumps between the two.
 */
internal sealed interface ViewReading {
    val elbow: Float

    /** The number the posture check smooths and compares. */
    val posture: Float

    /** Hip–knee–ankle on the near leg, when this placement can see it. */
    val knee: Float?

    /** At 45° (or side-on): the rule's own angles, near side, in the picture. */
    data class Angled(override val elbow: Float, val bodyLine: Float, override val knee: Float?) : ViewReading {
        override val posture: Float get() = bodyLine
    }

    /** Head-on: the picture's elbow, and whether the torso is down. */
    data class HeadOn(override val elbow: Float, val torsoElevation: Float) : ViewReading {
        override val posture: Float get() = torsoElevation
        override val knee: Float? get() = null
    }

    companion object {
        /** `null` when the frame cannot be read in this view — dropped, not scored. */
        fun of(view: CameraView, world: PoseFrame, image: ImagePose?): ViewReading? = when (view) {
            CameraView.FRONT -> {
                val elbow = image?.let(Angles::elbowInImage)
                val torso = Angles.torsoElevation(world)
                if (elbow == null || torso == null) null else HeadOn(elbow, torso)
            }

            CameraView.SIDE, CameraView.OBLIQUE -> if (image == null) {
                val elbow = Angles.elbow(world)
                val bodyLine = Angles.bodyLine(world)
                if (elbow == null || bodyLine == null) null else Angled(elbow, bodyLine, knee = null)
            } else {
                Angles.nearSide(image)?.let { side ->
                    val elbow = Angles.elbowInImage(image, side)
                    val bodyLine = Angles.bodyLineInImage(image, side)
                    if (elbow == null || bodyLine == null) null else Angled(elbow, bodyLine, Angles.kneeInImage(image, side))
                }
            }
        }
    }
}

/** Whether [knee] — already smoothed — is a leg straight enough to be on its toes. Unknown passes. */
internal fun kneesStraight(knee: Float?, rules: RepRules): Boolean = knee == null || knee >= rules.minKneeDegrees

/** Whether [posture] — already smoothed — is a body in push-up position, by this reading's check. */
internal fun ViewReading.postureHeld(posture: Float, rules: RepRules): Boolean = when (this) {
    is ViewReading.Angled -> kotlin.math.abs(180f - posture) <= rules.bodyLineToleranceDegrees
    is ViewReading.HeadOn -> posture <= rules.maxTorsoElevationDegrees
}
