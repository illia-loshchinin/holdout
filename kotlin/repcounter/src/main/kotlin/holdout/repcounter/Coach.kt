package holdout.repcounter

/**
 * The one thing to do next while getting into position — **three short words at most**,
 * shown big enough to read from the floor two metres away, and spoken.
 *
 * The five pre-flight pills and the guide each have their own full sentence;
 * nobody lying on the floor can read those. This picks the single most useful
 * correction, in the order they have to happen: light before anything the
 * model reads, the phone before the body, the body down before it is lined up,
 * distance before left and right. Then the position itself — arms, knees, the
 * line of the body — and when everything passes it says [HOLD]: the ready gate
 * is filling its ring.
 */
enum class Coach(val words: String) {
    MORE_LIGHT("More light"),

    /** The phone is not standing on the floor yet: the one instruction before any other, said once (owner, 12 Oct). */
    AIM_AT_FLOOR("Place your phone"),
    GET_IN_VIEW("Get in view"),
    TURN_45("Phone to 45°"),
    GET_DOWN("Get down"),
    FURTHER("Further"),

    /**
     * Staked: the near knee is hidden behind the body — turning away from the
     * phone shows it — or the phone is a little too far in front (turn away
     * from it) or beside the member (turn towards it). The direction when the
     * phone's side is known.
     */
    TURN_LEFT("Turn slightly left"),
    TURN_RIGHT("Turn slightly right"),
    TURN_AWAY("Turn slightly away"),
    CLOSER("Closer"),
    LEFT("Move left"),
    RIGHT("Move right"),

    /** The pills are green and the position is not yet the top of a push-up ([HoldBlocker]). */
    ARMS_STRAIGHT("Arms straight"),
    KNEES_UP("Knees up"),
    KNEES_UNCLEAR("Can't see knees"),
    BODY_STRAIGHT("Body straight"),
    HOLD("Hold"),
    ;

    companion object {
        fun next(report: PreflightReport?, guidance: Guidance?, hold: HoldBlocker? = null): Coach {
            if (report == null || guidance == null) return GET_IN_VIEW
            fun failing(check: PreflightCheck) = report.statuses.any { it.check == check && !it.passing }
            // Getting into view comes before turning (002 FR-018): while the whole body (or the head, or
            // a foot) is out of the picture, "Further" is the correction, not which way to turn the knees.
            val kneesFix = report.statuses
                .firstOrNull { it.check == PreflightCheck.KNEES_VISIBLE && !it.passing && !failing(PreflightCheck.WHOLE_BODY) }?.fix
            val cameraFix = report.statuses.firstOrNull { it.check == PreflightCheck.CAMERA_ANGLE && !it.passing }?.fix

            return when {
                failing(PreflightCheck.LIGHTING) -> MORE_LIGHT
                guidance.hint == PositionHint.AIM_AT_THE_FLOOR -> AIM_AT_FLOOR
                guidance.hint == PositionHint.GET_IN_FRAME -> GET_IN_VIEW
                failing(PreflightCheck.CAMERA_ANGLE) && report.view == null -> GET_IN_VIEW
                // Staked, the phone a little too far in front or beside the member: which way to turn.
                cameraFix == Preflight.TOO_FRONT_TURN_LEFT_FIX || cameraFix == Preflight.TOO_SIDE_TURN_LEFT_FIX -> TURN_LEFT
                cameraFix == Preflight.TOO_FRONT_TURN_RIGHT_FIX || cameraFix == Preflight.TOO_SIDE_TURN_RIGHT_FIX -> TURN_RIGHT
                guidance.hint == PositionHint.PHONE_AT_45 || failing(PreflightCheck.CAMERA_ANGLE) -> TURN_45
                guidance.hint == PositionHint.GET_DOWN -> GET_DOWN
                kneesFix == Preflight.KNEES_TURN_LEFT_FIX -> TURN_LEFT
                kneesFix == Preflight.KNEES_TURN_RIGHT_FIX -> TURN_RIGHT
                kneesFix == Preflight.KNEES_TURN_FIX -> TURN_AWAY
                guidance.hint == PositionHint.MOVE_BACK || failing(PreflightCheck.WHOLE_BODY) || failing(PreflightCheck.KNEES_VISIBLE) -> FURTHER
                guidance.hint == PositionHint.MOVE_CLOSER -> CLOSER
                guidance.hint == PositionHint.MOVE_LEFT -> LEFT
                guidance.hint == PositionHint.MOVE_RIGHT -> RIGHT
                // Everything about the phone and the picture passes: what is left is the position.
                report.canStart && hold == HoldBlocker.ARMS -> ARMS_STRAIGHT
                report.canStart && hold == HoldBlocker.KNEES -> KNEES_UP
                report.canStart && hold == HoldBlocker.KNEES_UNREADABLE -> KNEES_UNCLEAR
                report.canStart && hold == HoldBlocker.BODY -> BODY_STRAIGHT
                else -> HOLD
            }
        }
    }
}
