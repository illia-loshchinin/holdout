package holdout.repcounter

/**
 * What a sitting has riding on it — which decides where the phone may go.
 *
 * | | placements | what is verified |
 * |---|---|---|
 * | [DAILY] — the free Daily 25 | **head-on or at 45°** | depth, lockout, tempo, torso down / no hip fold. **Push-ups from the knees count** — a free habit for building strength |
 * | [STAKED] — money on it | **at 45° only** | all of that, and **knees off the floor** |
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
    ;

    /** The views a sitting at these stakes may be measured from. */
    val allowedViews: Set<CameraView>
        get() = when (this) {
            DAILY -> setOf(CameraView.FRONT, CameraView.OBLIQUE)
            STAKED -> setOf(CameraView.OBLIQUE)
        }

    /** Whether a sitting at these stakes may be measured from [view]. */
    fun accepts(view: CameraView?): Boolean = view in allowedViews

    /** What the camera check says when the phone is anywhere else. */
    val cameraFix: String
        get() = when (this) {
            DAILY -> "Put the phone in front of you, or at 45°"
            STAKED -> "Turn the phone to 45°"
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
                "and do full push-ups from your toes. Push-ups from the knees do not count."
    }
}
