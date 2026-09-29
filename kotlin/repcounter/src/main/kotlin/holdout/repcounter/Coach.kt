package holdout.repcounter

/**
 * The one thing to do next while getting into position — **three short words at most**,
 * shown big enough to read from the floor two metres away, and spoken.
 *
 * The four pre-flight pills and the guide each have their own full sentence;
 * nobody lying on the floor can read those. This picks the single most useful
 * correction, in the order they have to happen: light before anything the
 * model reads, the phone before the body, the body down before it is lined up,
 * distance before left and right. When everything passes it says [HOLD]: the
 * ready gate is filling its ring.
 */
enum class Coach(val words: String) {
    MORE_LIGHT("More light"),
    AIM_AT_FLOOR("Stand phone up"),
    GET_IN_VIEW("Get in view"),
    TURN_45("Phone to 45°"),
    GET_DOWN("Get down"),
    FURTHER("Further"),

    /** Staked: the near knee is hidden behind the body — turning away from the phone shows it. The direction when the phone's side is known. */
    TURN_LEFT("Turn slightly left"),
    TURN_RIGHT("Turn slightly right"),
    TURN_AWAY("Turn slightly away"),
    CLOSER("Closer"),
    LEFT("Move left"),
    RIGHT("Move right"),
    HOLD("Hold"),
    ;

    companion object {
        fun next(report: PreflightReport?, guidance: Guidance?): Coach {
            if (report == null || guidance == null) return GET_IN_VIEW
            fun failing(check: PreflightCheck) = report.statuses.any { it.check == check && !it.passing }
            val wholeBodyFix = report.statuses.firstOrNull { it.check == PreflightCheck.WHOLE_BODY }?.fix

            return when {
                failing(PreflightCheck.LIGHTING) -> MORE_LIGHT
                guidance.hint == PositionHint.AIM_AT_THE_FLOOR -> AIM_AT_FLOOR
                guidance.hint == PositionHint.GET_IN_FRAME -> GET_IN_VIEW
                failing(PreflightCheck.CAMERA_ANGLE) && report.view == null -> GET_IN_VIEW
                guidance.hint == PositionHint.PHONE_AT_45 || failing(PreflightCheck.CAMERA_ANGLE) -> TURN_45
                guidance.hint == PositionHint.GET_DOWN -> GET_DOWN
                wholeBodyFix == Preflight.KNEES_TURN_LEFT_FIX -> TURN_LEFT
                wholeBodyFix == Preflight.KNEES_TURN_RIGHT_FIX -> TURN_RIGHT
                wholeBodyFix == Preflight.KNEES_TURN_FIX -> TURN_AWAY
                guidance.hint == PositionHint.MOVE_BACK || failing(PreflightCheck.WHOLE_BODY) -> FURTHER
                guidance.hint == PositionHint.MOVE_CLOSER -> CLOSER
                guidance.hint == PositionHint.MOVE_LEFT -> LEFT
                guidance.hint == PositionHint.MOVE_RIGHT -> RIGHT
                else -> HOLD
            }
        }
    }
}
