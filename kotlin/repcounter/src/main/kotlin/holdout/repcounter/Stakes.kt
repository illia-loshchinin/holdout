package holdout.repcounter

/**
 * What a sitting has riding on it — which decides where the phone may go.
 *
 * | | placements | what is verified |
 * |---|---|---|
 * | [STAKED] — a **Hard** Holdout (form 0, 21-day) | **at 45° only** | depth, lockout, tempo, the body line, the hip rise, the legs and knees in view, face down, and **knees off the floor** |
 * | [EASY] — an **Easy** Holdout (form 1, 7-day; H35) | **at 45°, and no more frontal than about 40°** | everything Hard checks **except** knees off the floor: push-ups from the knees count |
 * | [DAILY] — the free Daily 25 | **as Easy** | **as Easy**: an Easy Holdout with nothing staked |
 *
 * The Daily 25 is where members practise for a Holdout, so since 8 Oct it is
 * held to the Easy set-up (002 FR-001, FR-007): head-on it had counted what a
 * Holdout refuses, knees, sags and cobras unseen. Knee push-ups are a choice
 * for the Daily 25 and Easy, not a limitation they live with: members building
 * up to full push-ups keep their habit. Hard is where they are refused, and
 * 45° is the placement that can see them — head-on the legs point into the
 * lens, and on the phone every leg measure read the same for knee push-ups as
 * for honest ones (`fixtures/rep-traces/README.md`). At 45° the plane the body bends in is turned towards the
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

    /**
     * Money is on it: a rule sentence read before staking (FR-021a). The set-up
     * and the checks no longer depend on it — the Daily 25 is held to Easy's.
     */
    val staked: Boolean get() = this != DAILY

    /** Knees must be off the floor: Hard only. Easy and the Daily 25 count push-ups from the knees. */
    val judgesKnees: Boolean get() = this == STAKED

    /** The views a sitting at these stakes may be measured from: 45°, whatever the stakes (002 FR-001, FR-002). */
    val allowedViews: Set<CameraView>
        get() = setOf(CameraView.OBLIQUE)

    /** Whether a sitting at these stakes may be measured from [view]. */
    fun accepts(view: CameraView?): Boolean = view in allowedViews

    /** What the camera check says when the phone is anywhere else (002 FR-004). */
    val cameraFix: String
        get() = "Turn the phone to 45°"

    companion object {
        /**
         * The rule a member must read **before** they stake anything — on the
         * join screen, beside the stake chips — and again on the camera check if
         * the phone is in the wrong place. One sentence per level, one source:
         * every screen that states it reads it from here (through
         * `Format.levelRule`). A Habit · 21-day Holdout counts push-ups from the
         * toes only; the words are the design book's, word for word
         * (`docs/design/README.md`, Levels).
         */
        const val STAKED_RULE =
            "At 45° only: phone on the floor ahead of your shoulder. " +
                "Full push-ups from your toes — push-ups from the knees don't count."

        /**
         * The rule of a Spark · 7-day Holdout (H35): inform, never encourage —
         * the toes come first, the knees are a permitted fallback.
         */
        const val EASY_RULE =
            "At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count."

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
