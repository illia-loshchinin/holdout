package holdout.repcounter

/**
 * What a sitting has riding on it — which decides where the phone may go.
 *
 * | | placements | what is verified |
 * |---|---|---|
 * | [DAILY] — the free Daily 25 | **head-on or at 45°** | depth, lockout, tempo, torso down / no hip fold. **Push-ups from the knees count** — a free habit for building strength |
 * | [STAKED] — a **Hard** Holdout (form 0, 21-day) | **at 45° only** | all of that, and **knees off the floor** |
 * | [EASY] — an **Easy** Holdout (form 1, 7-day; H35) | **at 45°, and no more frontal than about 40°** | everything Hard checks — the legs and knees in view, the body line, the hip rise — **except** knees off the floor: push-ups from the knees count |
 *
 * Knee push-ups are a choice for the Daily 25, not a limitation it lives with:
 * members building up to full push-ups keep their habit. Staked sittings are
 * where they are refused, and 45° is the placement that can see them — head-on
 * the legs point into the lens, and on the phone every leg measure read the
 * same for knee push-ups as for honest ones (`fixtures/rep-traces/README.md`). At 45° the plane the body bends in is turned towards the
 * lens, so a bent knee reads bent, and it needs less floor than square side-on,
 * which is offered to nobody: a body lying across a portrait frame puts the
 * phone about 2 m away.
 *
 * On the one angled recording so far, knee push-ups read 74–102° at the knee
 * in most reps (`fixtures/rep-traces/README.md`); no honest angled set has been
 * recorded yet, so the knee threshold is provisional there.
 */
enum class Stakes {
    DAILY,
    STAKED,
    EASY,
    ;

    /** Money is on it: 45° only, the legs and knees kept in view (FR-021a). */
    val staked: Boolean get() = this != DAILY

    /** Knees must be off the floor: Hard only. Easy and the Daily 25 count push-ups from the knees. */
    val judgesKnees: Boolean get() = this == STAKED

    /** The views a sitting at these stakes may be measured from. */
    val allowedViews: Set<CameraView>
        get() = when (this) {
            DAILY -> setOf(CameraView.FRONT, CameraView.OBLIQUE)
            STAKED, EASY -> setOf(CameraView.OBLIQUE)
        }

    /** Whether a sitting at these stakes may be measured from [view]. */
    fun accepts(view: CameraView?): Boolean = view in allowedViews

    /** What the camera check says when the phone is anywhere else. */
    val cameraFix: String
        get() = when (this) {
            DAILY -> "Put the phone in front of you, or at 45°"
            STAKED, EASY -> "Turn the phone to 45°"
        }

    companion object {
        /**
         * The rule a member must read **before** they stake anything — on the
         * join screen, beside the stake chips — and again on the camera check if
         * the phone is in the wrong place. One sentence, one source: every
         * screen that states it reads it from here.
         */
        const val STAKED_RULE =
            "Staked Holdouts are counted at 45°: stand the phone on the floor ahead of your shoulder, " +
                "turned to look at you at about 45° — not in front, not straight beside you — " +
                "and do full push-ups from your toes. Push-ups from the knees, or with the hips resting on the floor " +
                "at the bottom, do not count."

        /** The Easy rule, read on the join screen of a 7-day Holdout (H35). */
        const val EASY_RULE =
            "Easy Holdouts are counted at 45°: stand the phone on the floor ahead of your shoulder, " +
                "turned to look at you at about 45° — not in front, not straight beside you. " +
                "Push-ups from the knees count; keep your body in one straight line from the knees."

        /**
         * The Easy placement guard (H35): at a placement ratio this high or
         * higher the phone stands more frontal than about 40°, and an Easy
         * sitting will not start. On the recorded traces every 30° set read a
         * median of 0.98–1.03 and every 40–60° set 0.37–0.87; the two cobras
         * that counted with knees allowed were both filmed at about 30°.
         */
        const val EASY_MAX_PLACEMENT_RATIO = 0.90f

        /** The stakes a cohort of this form is counted at: 0 Hard, 1 Easy. Null for a form this build does not know. */
        fun ofForm(form: Int): Stakes? = when (form) {
            0 -> STAKED
            1 -> EASY
            else -> null
        }
    }
}
