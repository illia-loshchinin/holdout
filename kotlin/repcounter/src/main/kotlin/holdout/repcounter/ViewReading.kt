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
    data class Angled(
        override val elbow: Float,
        val bodyLine: Float,
        override val knee: Float?,
        /** Near knee + ankle height above the near hand, in arm lengths; null when not all visible. */
        val legsAboveHands: Float? = null,
        /** Near shoulder height above the near hand, in arm lengths — depth at 45°. */
        val shoulderOverHand: Float? = null,
        /** The leg is in the picture but its knee is not where a knee can be ([Angles.kneeReadable]): nothing about the knee is read. */
        val kneeUnreadable: Boolean = false,
        /** Near hip height above the near hand, in arm lengths; null when the hip is not clearly in frame. */
        val hipAboveHand: Float? = null,
    ) : ViewReading {
        override val posture: Float get() = bodyLine
    }

    /** Head-on: the picture's elbow, whether the torso is down, and how high the shoulders stand over the hands. */
    data class HeadOn(
        override val elbow: Float,
        val torsoElevation: Float,
        /** Pixels; null when a shoulder or wrist is not clearly in frame. */
        val shoulderHeight: Float? = null,
    ) : ViewReading {
        override val posture: Float get() = torsoElevation
        override val knee: Float? get() = null
    }

    companion object {
        /**
         * `null` when the frame cannot be read in this view — dropped, not scored.
         * [side], when given, is the side to read at 45°; otherwise the side the
         * frame itself shows longer.
         */
        fun of(view: CameraView, world: PoseFrame, image: ImagePose?, side: Side? = null): ViewReading? = when (view) {
            CameraView.FRONT -> {
                val elbow = image?.let(Angles::elbowInImage)
                val torso = Angles.torsoElevation(world)
                if (elbow == null || torso == null) null else HeadOn(elbow, torso, Angles.shoulderHeightInImage(image))
            }

            CameraView.SIDE, CameraView.OBLIQUE -> if (image == null) {
                val elbow = Angles.elbow(world)
                val bodyLine = Angles.bodyLine(world)
                if (elbow == null || bodyLine == null) null else Angled(elbow, bodyLine, knee = null)
            } else {
                (side ?: Angles.nearSide(image))?.let { side ->
                    val elbow = Angles.elbowInImage(image, side)
                    val bodyLine = Angles.bodyLineInImage(image, side)
                    if (elbow == null || bodyLine == null) {
                        null
                    } else {
                        // A knee the model put on the ankle is no knee: its angle and
                        // its height are both the ankle's.
                        val readable = Angles.kneeReadable(image, side)
                        Angled(
                            elbow,
                            bodyLine,
                            if (readable) Angles.kneeInImage(image, side) else null,
                            if (readable) Angles.legsAboveHandsInImage(image, side) else null,
                            Angles.shoulderOverHandInImage(image, side),
                            kneeUnreadable = Angles.kneeCollapsed(image, side),
                            hipAboveHand = Angles.hipAboveHandInImage(image, side),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether the knees are off the floor — asked of staked sittings only; the
 * Daily 25 counts push-ups from the knees. Two independent signals, either of
 * which says "knees down": the legs stand low over the hands (the hands are on
 * the floor), or the knee is bent a long way. Unknown passes. The rule is
 * **knees off the floor**, not legs dead straight: a leg bent a little in the
 * air is allowed. The thresholds and the data behind them are on [RepRules].
 */
internal fun kneesOffFloor(knee: Float?, legsAboveHands: Float?, rules: RepRules, descentKnee: Float? = null): Boolean = (knee == null || knee >= rules.kneesDownDegrees) &&
    (legsAboveHands == null || legsAboveHands >= rules.minLegsAboveHands) &&
    // A bent knee has to stand clearly higher: kneeling with the feet raised lifts the ankle, and with it the sum.
    (knee == null || legsAboveHands == null || legsAboveHands >= rules.minLegsAboveHandsBentKnee || !bentKnee(knee, descentKnee, rules))

/** A knee bent on the push — unless it came down near straight: kneeling is lowered onto a bent knee. */
private fun bentKnee(knee: Float, descentKnee: Float?, rules: RepRules): Boolean = knee < rules.bentKneeDegrees &&
    (knee < rules.bentKneeAlwaysDegrees || descentKnee == null || descentKnee < rules.bentKneeDescentDegrees)

/**
 * [kneesOffFloor], where an unknown does **not** pass: staked sittings, where
 * a knee the camera never saw cannot be taken as off the floor.
 */
internal fun kneesSeenOffFloor(knee: Float?, legsAboveHands: Float?, rules: RepRules): Boolean = (knee != null || legsAboveHands != null) && kneesOffFloor(knee, legsAboveHands, rules)

/**
 * Whether [posture] — already smoothed — is a body in push-up position, by
 * what [view] can measure:
 *
 * - **head-on**, the torso is down rather than upright;
 * - **at 45°**, the body line clears [RepRules.angledBodyLineFloorDegrees] — a
 *   coarse check, because at 45° the picture read honest planks at 152–167°
 *   and the rule's ±10° cannot be held there (a named weakness, README);
 * - **square side-on** — synthetic traces only; the app accepts it nowhere —
 *   the rule's own ±10°.
 */
internal fun postureHeld(view: CameraView, reading: ViewReading, posture: Float, rules: RepRules): Boolean = when (reading) {
    is ViewReading.HeadOn -> posture <= rules.maxTorsoElevationDegrees
    is ViewReading.Angled -> if (view == CameraView.OBLIQUE) {
        posture >= rules.angledBodyLineFloorDegrees
    } else {
        kotlin.math.abs(180f - posture) <= rules.bodyLineToleranceDegrees
    }
}
