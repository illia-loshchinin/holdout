package holdout.repcounter

/**
 * The push-up rule, and the machine that applies it (FR-021, research R4, T048).
 *
 * **The rule** — what the product promises, and what the tests assert:
 *
 *   1. Top     elbow > 160°, shoulder–hip–ankle within ±10° of straight
 *   2. Bottom  elbow < 90°, body line held
 *   3. Top     elbow > 160°, body line held
 *   4. Cycle   between 0.8 and 6 seconds
 *   5. Legs    straight, on the toes — judged at 45°, the placement that
 *              shows it; staked sittings use it, head-on cannot ([Stakes])
 *
 * **The implementation** — how it survives the model's own error (research R4):
 * angles are read in the picture (`ViewReading`; 3D world landmarks only for
 * synthetic traces), are one-euro smoothed before any
 * comparison, and the machine triggers on *wider* thresholds than the rule
 * states — 165° at the top, 85° at the bottom — because a hard edge on a
 * reading with ~2° of error counts one descent twice.
 *
 * **The trigger values in [RepRules] are provisional until spike S2** (T012b).
 * They come from research R4; S2's measured angle table replaces them, and
 * `fixtures/rep-traces/README.md` says so. Nothing else has to change when it
 * does — [RepCounterTest] asserts the *rule*, never the triggers, so tuning
 * them does not rewrite a single test.
 *
 * A rep that misses a rule is discarded silently with a hint, never an error
 * (FR-021).
 */
data class RepRules(
    /** The rule's top. A rep is only ever credited for a cycle that clears this. */
    val topDegrees: Float = 160f,
    /**
     * The **floor** of the rule's top at 45° (decided 2026-10-01, product
     * owner): 148°. Seen from 45–50° measured on the floor, a locked-out arm
     * reads 150–158° in the picture — the forearm near vertical, the upper arm
     * leaning forward about 25° — and the model's 3D reading agrees. At 160°
     * two honest sets of ten counted nothing and the ready hold took a minute
     * (fixtures `angled45-right-mixed-04`, `angled50-right-mixed-05`).
     *
     * It is a floor, not the line: from a more frontal spot the same arm reads
     * 167–173°, and a flat 148° there cut each rep short — three honest sets
     * lost reps to the body-line check and two counted too many. So the line is
     * set per sitting from the arm the member holds through the countdown
     * ([RepCounter]'s `top`), between this and [topDegrees]. The ready hold,
     * which comes before any of that, accepts this floor.
     */
    val obliqueTopDegrees: Float = 148f,
    /**
     * At 45°: how far under the arm held through the countdown a rep's top may
     * fall. Set by replaying every staked phone trace.
     */
    val obliqueTopMarginDegrees: Float = 10f,
    /** The rule's bottom. */
    val bottomDegrees: Float = 90f,
    /** The rule's body-line tolerance, either side of straight. */
    val bodyLineToleranceDegrees: Float = 10f,
    val minCycleMillis: Long = 800,
    val maxCycleMillis: Long = 6_000,

    /** Trigger: entering the top. Wider than [topDegrees] — S2 sets the final value. */
    val topTriggerDegrees: Float = 165f,
    /** Trigger: reaching the bottom. Wider than [bottomDegrees] — S2 sets the final value. */
    val bottomTriggerDegrees: Float = 85f,
    /**
     * Below this the member is clearly attempting a descent rather than shifting
     * at the top. Coming back up from here without reaching the bottom trigger
     * is what earns "Go lower" instead of silence.
     */
    val descentAttemptDegrees: Float = 140f,
    /**
     * Knees off the floor, staked sittings, judged on the **push back up**
     * (the same window as the cobra check — over the whole rep the flat bottom
     * hides a bent knee). Either signal below its line means knees down:
     *
     * - the **median knee angle** (hip–knee–ankle, near leg) below this — a
     *   deep kneel or feet raised behind (40–98°). It is low on purpose: with
     *   the phone on the member's right, honest reps read the near knee at
     *   99–125°, so any higher line refuses honest push-ups. Knees on the
     *   floor with the feet down are caught by the legs' height instead;
     * - the **median legs-above-hands** below [minLegsAboveHands].
     *
     * Both are stopgaps measured on one person (fixtures/rep-traces); the
     * knee-on-the-floor image classifier replaces them.
     */
    val kneesDownDegrees: Float = 90f,
    /**
     * Near knee + ankle height over the near hand, in arm lengths, median over
     * the push. The hands are on the floor, so this is the legs' height off
     * it. On the phone: honest 0.44–0.65 (three sets, 30 reps), push-ups from
     * the knees 0.32–0.39 (two sets), cobras 0.17–0.32.
     */
    val minLegsAboveHands: Float = 0.41f,
    /**
     * A knee bent under [bentKneeDegrees] on the push needs the legs this high
     * instead. Kneeling with the feet half raised reads between the two lines
     * above: on 2 Oct, from a spot more frontal than 40°, five such reps
     * counted at a knee of 90–112° with the legs at 0.43–0.45, the raised
     * ankle carrying the sum. The one honest set that reads a bent knee
     * (`angled-right-honest-01`, 98–134°) has the legs at 0.51–0.56. Late in
     * a set, on 3 Oct, honest reps read the knee at 130–135° with the legs at
     * 0.46–0.47 — see [bentKneeDescentDegrees].
     */
    val bentKneeDegrees: Float = 135f,
    val minLegsAboveHandsBentKnee: Float = 0.48f,
    /**
     * Staked: the legs on the push, against the same person's legs held in the
     * plank through the countdown — at least this share. The lines above are
     * one body's: on 4 Oct a second person's knee push-ups read the legs at
     * 0.42–0.44, over [minLegsAboveHands], because his honest reps stand at
     * 0.51–0.79. Against his own plank (0.70) those knee reps read 0.60–0.63;
     * honest reps on every recording read 0.70–1.13 of their plank. An extra
     * check, not a replacement: kneeling with the feet raised keeps the
     * share high (0.74–1.19), and the lines above catch it.
     */
    val minLegsShareOfHeld: Float = 0.66f,
    /**
     * The bent-knee line judges the push alone, where a leg a little bent on
     * the toes reads like kneeling with the feet raised: on 3 Oct a full rep
     * on the toes read knee 130°, legs 0.47, and was refused. The way down
     * tells them apart. Every push-up from the knees recorded between the
     * lines was lowered onto a bent knee, 61–106°; every rep on the toes there
     * came down at 117–147°. So from [bentKneeAlwaysDegrees] up to
     * [bentKneeDegrees], the bent-knee line refuses a rep only if the knee
     * was also under this on the way down.
     *
     * Under [bentKneeAlwaysDegrees] the push alone refuses: pushes from the
     * knees with the feet raised read 90–112° there, so lowering on the toes
     * and dropping to the knees at the bottom does not get past. Reps on the
     * toes between the lines read 127–134°; the one knee push-up recorded
     * above 120° (`angled30-right-knees-14`, 126°) came down at 104°.
     */
    val bentKneeDescentDegrees: Float = 112f,
    val bentKneeAlwaysDegrees: Float = 120f,
    /**
     * At 45°: **the body comes up as one piece.** Half-way up the push (elbow
     * 110–140°) the hips must have risen as far as the shoulders have, each
     * against its own height at the end of that push: hip share over shoulder
     * share, at least this. A rep pushed up from lying on the floor, and a
     * cobra done with the body nearly straight, lift the chest first and bring
     * the hips after — and both passed the body-line check, which reads
     * differently from every spot and on every body (decided 2026-10-02,
     * product owner: a push-up that touched the floor does not count).
     *
     * On the phone, 40–60° on the 4:3 picture: honest reps 0.92–1.06 (all but
     * one of ninety at 0.96 or more); a rep pushed up off the floor 0.87, and
     * two more like it 0.83 and 0.86; a second person's near-straight cobras
     * 0.63–0.72; cobras with the hips left down 0.37–0.67.
     *
     * Judged only inside [maxHipRisePlacementRatio]: from further in front the
     * hips sit behind the shoulders in the picture and the reading drops —
     * honest reps recorded there read down to 0.84. A staked sitting cannot
     * start from there (`Preflight.stakedFrontRatio`, the same line), nor since
     * 8 Oct a Daily 25, so this exempts only recordings older than the line.
     */
    val minHipRise: Float = 0.90f,
    /** The placement ratio (`Preflight.placementRatio`, held through the countdown) under which [minHipRise] is judged: about 35° and further round to the side. */
    val maxHipRisePlacementRatio: Float = 1.1f,
    /**
     * Staked: how far inside the picture the near knee must stay, as a share of
     * each side. A knee that leaves the frame is not dropped by the model: it
     * is placed just inside the edge, with full confidence, and the knee check
     * then judges a guess. On 29 Sep a set with the knees out of camera had the
     * knee within 12 % of the edge in 28–50 % of every rep's frames; honest sets
     * at 45° had it there in 0–3 %.
     */
    val legEdgeMargin: Float = 0.13f,
    /**
     * Staked: the largest share of the push back up the near knee may spend
     * inside [legEdgeMargin] (or unseen) before the rep cannot be judged and is
     * refused with [RepHint.LEGS_IN_VIEW]. Calibrated on the phone's traces.
     */
    val maxLegsUnseenShare: Float = 0.25f,
    /**
     * Depth at 45°: at the bottom the near shoulder must come down to within
     * this many arm-lengths of the near hand. On the phone honest reps reached
     * 0.20–0.28; half push-ups stopped at 0.43–0.68, and the elbow angle alone
     * had counted most of them.
     */
    val maxAngledShoulderOverHand: Float = 0.32f,
    /**
     * Depth at 45°, the doubtful band: a shoulder that stopped just over
     * [maxAngledShoulderOverHand] — up to this — is still a full rep when the
     * elbow itself closed to [deepElbowDegrees]. On 2 Oct, from an unmeasured
     * spot near 40°, three honest reps of ten were refused at 0.33–0.34 with
     * the elbow at 62–64°; half push-ups in the same band (0.34–0.35, two
     * sets) kept the elbow at 68–71°. Either reading alone has counted a half
     * rep or refused a full one; both must say shallow here.
     */
    val maxAngledShoulderOverHandDeepElbow: Float = 0.36f,
    val deepElbowDegrees: Float = 66f,
    /**
     * At 45°, the lowest **median** body line a rep may have. Coarse on
     * purpose: honest planks read 152–167° there, so this catches a hip fold,
     * not a slight sag.
     */
    val angledBodyLineFloorDegrees: Float = 140f,
    /**
     * At 45°, the lowest median body line allowed **while pushing up** — from
     * the bottom back to the top, once the elbow is past 100°. This is what
     * refuses a "cobra": lying flat, then pressing the chest up with the hips
     * left on the floor. Over the whole rep the flat part hides the arch; on
     * the push it cannot. On the phone the push read 157–174° on honest reps
     * and 120–134° on cobra reps.
     */
    val angledPushBodyLineFloorDegrees: Float = 145f,
    /**
     * At 45°, every sitting: **face down, hands on the floor.** The body line
     * cannot say it — lying on the back with the knees up, and standing, both
     * read near straight — and on 7 Oct a Daily 25 at 45° counted sixteen
     * crunches and two kettlebell stands among seven push-ups. Medians over the
     * rep, in arm lengths:
     *
     * - the near knee above the hip: every labelled push-up, on the toes or
     *   the knees, −0.59 to 0.02 (one rep labelled unsure 0.14); crunches
     *   0.51–0.81;
     * - the near ankle below the hand: push-ups −0.38 to 0.04 (unsure up to
     *   0.34); standing 1.87–1.94, crunches with the arms up 0.28–0.76.
     */
    val maxKneeAboveHip: Float = 0.3f,
    val maxAnkleBelowHand: Float = 0.6f,
) {
    init {
        require(topTriggerDegrees >= topDegrees) {
            "the top trigger must be at least as strict as the rule it enforces"
        }
        require(bottomTriggerDegrees <= bottomDegrees) {
            "the bottom trigger must be at least as strict as the rule it enforces"
        }
    }
}

/**
 * The rules to use for a given camera view.
 *
 * **It returns the same rules for every view, and that is deliberate.** The
 * first front-view recording showed that head-on needed a different
 * *measurement*, not different numbers: read in the picture, the elbow clears
 * the same 165° / 85° triggers with room to spare (`ViewReading`). So the view
 * changes what is read, and this stays the one place that would change the
 * thresholds, should S2's traces ever show a view needs its own.
 *
 * **What must never happen** is loosening a front-view trigger past the rule to
 * make head-on counting work. The triggers exist to be *stricter* than the rule
 * so jitter cannot count a rep; a trigger looser than the rule counts reps the
 * rule forbids, which is not tuning but a change to what a rep is. `RepRules`'s
 * own `require` refuses it, and `RepRulesViewTest` checks every view's set.
 */
fun RepRules.forView(view: CameraView): RepRules = when (view) {
    CameraView.SIDE, CameraView.FRONT -> this
    // At 45° the trigger is the rule's own top, not the stricter head-on one — and
    // the counter then sets the sitting's line under it from the held arm, down to
    // [RepRules.obliqueTopDegrees] (see RepCounter.top). A rep still needs a full
    // bottom to count, so jitter at the top cannot add one.
    CameraView.OBLIQUE -> copy(topTriggerDegrees = topDegrees)
}

/** What the sitting screen shows when a rep is thrown away (002 contracts/words.md). */
enum class RepHint(val message: String) {
    GO_LOWER("Go lower"),
    KEEP_BODY_STRAIGHT("Keep your body straight"),
    SLOW_DOWN("Slow down"),
    UP_ON_YOUR_TOES("Knees off the floor"),

    /**
     * Every sitting (the Daily 25 since 8 Oct, 002 FR-007): the near leg was
     * not readable on the push or on the way down — the knee unseen, or at the
     * picture's edge where the model guesses, or the foot slipping out of it —
     * so nothing proved the knee off the floor (Hard), nor let the body line
     * and the hip rise be judged.
     */
    LEGS_IN_VIEW("Keep your legs in the picture"),

    /**
     * Staked only: the body rested on the floor at the bottom — hips or thighs
     * carrying weight on it, then pushed back up (owner, 2026-10-04: resting,
     * not a chest brushing the floor). **Not said yet**: no landmark reading
     * tells a hip resting on the floor from a deep honest bottom
     * (`angled40-right-floor-20`), so nothing refuses with it until the
     * knee-on-the-floor image classifier also judges the hips.
     */
    HIPS_OFF_FLOOR("Hips off the floor"),
}

/**
 * What the counter read from the last frame it scored, smoothed exactly as the
 * rule sees it. For tuning on a phone — a debug readout — never for deciding
 * anything; the rule lives in [RepCounter] alone.
 */
data class Readout(
    val view: CameraView,
    val elbow: Float,
    /** At 45°: the near side's body line — to the ankle on Hard, the straighter of ankle and knee where knees count. */
    val posture: Float,
    /** Near leg at 45°; null when the leg is not clearly in frame. */
    val knee: Float?,
)

/**
 * What the counter judged the last cycle on, as the rule saw it. Like
 * [Readout], for tuning from recorded traces — never for deciding anything.
 */
data class CycleReadout(
    val top: Float,
    val minElbow: Float,
    /** At 45°: the nearest the shoulder came to the hand, in arm lengths. */
    val shoulderOverHand: Float?,
    val pushKnee: Float?,
    val pushLegsAboveHands: Float?,
    /** At 45°: how far the hips had risen half-way up the push, against the shoulders ([RepRules.minHipRise]). */
    val hipRise: Float? = null,
    val descentKnee: Float?,
    val descentLegsAboveHands: Float?,
    val legsUnseenShare: Float,
    val posture: Float?,
    val pushPosture: Float?,
    val cycleMillis: Long,
    /** The plank held through the countdown that the push is measured against ([RepRules.minLegsShareOfHeld]); null with none. */
    val heldLegsAboveHands: Float? = null,
    /** The placement ratio held through the countdown ([RepRules.maxHipRisePlacementRatio]); null with none. */
    val heldPlacementRatio: Float? = null,
)

/**
 * How a counted rep would fare under Hard's knee rule, for the Daily 25's toes
 * meter (H35). It never changes what counts: the Daily 25 counts push-ups from
 * the knees either way.
 */
enum class RepStance {
    /** Hard's rule would have counted it: knees off the floor, legs in view. */
    TOES,

    /** Down on the knees for some of it. */
    KNEES,

    /**
     * Not enough to say: the legs were not in the picture enough, or no plank on
     * the toes was held through the countdown to measure the push against.
     */
    NOT_VISIBLE,
}

sealed interface RepEvent {
    /**
     * A rep met every rule. [total] counts reps in this sitting. [stance] is
     * read at 45° only — head-on the legs point into the lens — and null there.
     */
    data class Counted(val total: Int, val cycleMillis: Long, val stance: RepStance? = null) : RepEvent

    /**
     * A cycle was attempted and thrown away. Never surfaced as an error.
     *
     * [hint] is null when none of the hints the copy deck defines fits — a
     * cycle slower than six seconds fails rule 4, and there is no "speed up"
     * hint to show, so it is rejected in silence rather than under a hint that
     * tells the member to do the opposite of what they need.
     */
    data class Discarded(val hint: RepHint?) : RepEvent
}

/**
 * Feed it every frame of a sitting, in order. It returns an event on the frame
 * that completes or rejects a cycle, and `null` on every other frame.
 *
 * One instance per sitting: the counter is not the banker. Reps survive across
 * sittings only as banked blocks of ten, which is [BankingController]'s job.
 */
class RepCounter(
    rules: RepRules = RepRules(),
    /**
     * Where the phone was when the sitting started. It decides what is read
     * from each frame (`ViewReading`); it is fixed for the sitting, because a
     * cycle measured half one way and half the other is not measured at all.
     */
    private val view: CameraView = CameraView.SIDE,
    stakes: Stakes = Stakes.DAILY,
) {
    init {
        // Pre-flight already refuses to start a sitting anywhere but at 45°. This
        // is the second lock on the same door: no counter is measured head-on,
        // which cannot see the knees, a sag or a cobra (002 FR-006, retired 8 Oct).
        // Side-on is the synthetic traces' state-machine view only.
        require(view != CameraView.FRONT) { "no sitting is counted from the $view view" }
        require(!stakes.staked || stakes.accepts(view)) { "$stakes sittings cannot be counted from the $view view" }
    }

    private val rules = rules.forView(view)

    /**
     * Knees are judged only when money is on it. The Daily 25 counts push-ups
     * from the knees on purpose: it is a free habit, and a member building up
     * to full push-ups should be able to keep it (FR-021a).
     */
    private val judgeKnees = stakes.judgesKnees

    /**
     * And where push-ups from the knees count, a body straight from shoulder
     * to **knee** is a body held, whatever the lower legs do behind it: raised
     * or crossed, they pull shoulder–hip–ankle under the floor and refused
     * reps the rule allows (FR-021a; Codex P1 on PR #18). The line read is
     * the straighter of the two ([ViewReading.of] says why not the knee's
     * alone). A sag or a fold at the hips bends both, so it is still caught.
     * Every posture check — [bodyHeld], the push floor, the ready hold — reads
     * this line; the hip rise reads hip and shoulder heights and needs neither.
     */
    private val bodyLineToKnee = !judgeKnees

    /**
     * The legs must stay in view, whatever the stakes — the Daily 25 is held to
     * Easy since 8 Oct (002 FR-007): a rep whose knees spent the push out of
     * the picture cannot be judged for the body line or the hip rise either (H35).
     * Every sitting is at 45°; the other views are the synthetic tests' only.
     */
    private val watchLegs = view == CameraView.OBLIQUE

    /**
     * At 45° the legs are followed whatever the stakes, so every counted rep
     * can be tagged for the toes meter ([RepStance]); only [watchLegs] lets
     * them decide anything.
     */
    private val followLegs = watchLegs || view == CameraView.OBLIQUE

    private enum class Phase {
        /** Nothing yet: waiting for the body to be at the top, straight. */
        AWAITING_TOP,

        /** At the top of a rep, straight. A descent from here starts a cycle. */
        AT_TOP,

        /** Below the bottom trigger. The return to the top completes the cycle. */
        AT_BOTTOM,
    }

    private val elbowFilter = OneEuroFilter()
    private val postureFilter = OneEuroFilter()
    private val kneeFilter = OneEuroFilter()

    /**
     * Frames so far that showed each side nearer the lens. At 45° a sitting is
     * read from one side: the one most frames agree on. A frame where that
     * side is hidden is dropped rather than read from the far side, which is
     * MediaPipe guessing through the body — a far arm guessed straight in the
     * middle of a descent can undo a whole set.
     */
    private val sideVotes = IntArray(2)

    private fun nearSideFor(image: ImagePose?): Side? {
        if (image == null) return null
        val seen = Angles.nearSide(image) ?: return null
        sideVotes[seen.ordinal]++
        return if (sideVotes[Side.LEFT.ordinal] >= sideVotes[Side.RIGHT.ordinal]) Side.LEFT else Side.RIGHT
    }

    private var phase = Phase.AWAITING_TOP
    private var count = 0

    /** Cycle state, all reset by [startCycle]. */
    private var cycleStartMillis = 0L
    private var minElbowInCycle = Float.MAX_VALUE
    private var bodyLineHeld = true
    private val cycleKnees = mutableListOf<Float>()
    private val cyclePostures = mutableListOf<Float>()

    /** At 45°, over the whole rep: knee above hip and ankle below hand ([RepRules.maxKneeAboveHip]). */
    private val cycleKneeAboveHip = mutableListOf<Float>()
    private val cycleAnkleBelowHand = mutableListOf<Float>()

    /** The body line while pushing back up — after the bottom, elbow past [PUSH_FROM_ELBOW]. */
    private val pushPostures = mutableListOf<Float>()
    private val pushKnees = mutableListOf<Float>()
    private val pushLegs = mutableListOf<Float>()

    /**
     * Hip and shoulder heights half-way up the push, and at the end of the
     * same push. Its own top, not the sitting's: between reps a member rests,
     * shifts, drops to the knees, and none of that is this rep's plank.
     */
    private val midHips = mutableListOf<Float>()
    private val midShoulders = mutableListOf<Float>()
    private val topHips = mutableListOf<Float>()
    private val topShoulders = mutableListOf<Float>()

    /** The placement ratio held through the countdown; null with no countdown, and then [RepRules.minHipRise] is not judged. */
    private val placement = Preflight()
    private val heldRatios = mutableListOf<Float>()

    /**
     * Legs above hands through the countdown with the arms straight and the knees
     * seen off the floor: this person's plank on the toes ([RepRules.minLegsShareOfHeld]).
     * Where knees count, a member may hold the countdown from the knees; no
     * reference is taken from that, so a knee rep cannot match a kneeling plank.
     */
    private val heldLegs = mutableListOf<Float>()
    private var heldRatio: Float? = null

    private fun hipRiseTooLow(): Boolean {
        if (view != CameraView.OBLIQUE) return false
        val ratio = heldRatio ?: return false
        if (ratio >= rules.maxHipRisePlacementRatio) return false
        // A rise that could not be read is not a body that came up as one piece, as a
        // knee never seen is not off the floor. On every recording only one counted rep
        // had none: a cobra (4 Oct). The rule is Easy's as much as Hard's, and the Daily 25
        // is an Easy Holdout with nothing staked, so none lets an unknown pass (review, PR #14).
        return hipRise()?.let { it < rules.minHipRise } ?: true
    }

    /**
     * A countdown frame fit to calibrate from: the ready gate's own checks
     * (`ReadyGate.blockerOf`) — where money is staked, knees readable and seen
     * off the floor; the body in one line.
     */
    private fun readyPlank(reading: ViewReading, posture: Float): Boolean {
        val angled = reading as? ViewReading.Angled
        if (judgeKnees && (angled?.kneeUnreadable == true || !kneesSeenOffFloor(reading.knee, angled?.legsAboveHands, rules))) return false
        return postureHeld(view, reading, posture, rules)
    }

    /**
     * Enough of a plank on the toes, read through the countdown, for the legs on
     * each push to be judged against it ([RepRules.minLegsShareOfHeld]). Where
     * knees are judged, counting must not start without it: the check would pass
     * every rep (review, PR #14).
     */
    val plankCalibrated: Boolean get() = heldLegs.size >= MIN_HELD_FRAMES

    /**
     * Enough of a plank read through the countdown to know where the phone stands
     * ([heldRatio]), which the hip-rise check needs: without it [hipRiseTooLow] is not
     * judged, and near-straight cobras count. No sitting starts counting without it,
     * the Daily 25 included (review, PR #14).
     */
    val placementCalibrated: Boolean get() = heldRatios.size >= MIN_HELD_FRAMES

    /** The placement ratio of the plank held through the countdown so far (median), or null before [placementCalibrated]. */
    val calibratedPlacementRatio: Float? get() = heldRatios.takeIf { it.size >= MIN_HELD_FRAMES }?.sorted()?.let { it[it.size / 2] }

    /** The legs on the push fell well under this person's own plank ([RepRules.minLegsShareOfHeld]). Unknown passes. */
    private fun legsDroppedFromPlank(): Boolean {
        if (heldLegs.size < MIN_HELD_FRAMES) return false
        val held = heldLegs.medianOrNull()?.takeIf { it > 0f } ?: return false
        return pushLegs.medianOrNull()?.let { it / held < rules.minLegsShareOfHeld } == true
    }

    /** Lying on the back, or standing: not a body in push-up position at all ([RepRules.maxKneeAboveHip]). Unknown passes. */
    private fun notFaceDown(): Boolean = cycleKneeAboveHip.medianOrNull()?.let { it > rules.maxKneeAboveHip } == true ||
        cycleAnkleBelowHand.medianOrNull()?.let { it > rules.maxAnkleBelowHand } == true

    private fun hipRise(): Float? {
        val hipAtTop = topHips.medianOrNull()?.takeIf { it > 0f } ?: return null
        val shoulderAtTop = topShoulders.medianOrNull()?.takeIf { it > 0f } ?: return null
        val hip = midHips.medianOrNull() ?: return null
        val shoulder = midShoulders.medianOrNull()?.takeIf { it > 0f } ?: return null
        return (hip / hipAtTop) / (shoulder / shoulderAtTop)
    }

    /**
     * The same two readings on the way **down**, until the elbow passes
     * [PUSH_FROM_ELBOW]. A rep let down from the knees and pushed up on the
     * toes showed honest legs on the push alone, and counted (2 Oct, 60°).
     * Only the knee angle is judged here: on that descent it read 77°, on
     * honest ones 108° at the least (135° but for one) — while the legs'
     * height on the way down read as low on honest reps with the phone on the
     * left (0.33–0.40) as on that one (0.34), so it decides nothing. The
     * height is kept for the readout.
     */
    private val descentKnees = mutableListOf<Float>()
    private val descentLegs = mutableListOf<Float>()

    /** Descent frames, and those whose near knee was unseen, unreadable or at the picture's edge (staked). */
    private var descentFrames = 0
    private var descentKneeUnseen = 0

    /** Push frames, and those whose near knee was unseen or at the picture's edge (staked). */
    private var pushFrames = 0
    private var pushLegsUnseen = 0
    private var minShoulderOverHandInCycle = Float.MAX_VALUE

    val total: Int get() = count

    /** The last frame scored, for a debug readout. Null until one is. */
    var readout: Readout? = null
        private set

    /**
     * Frames whose joints are not clearly visible, and frames where the angles
     * cannot be computed, are **dropped rather than scored** — they do not
     * advance the machine and they do not break a cycle in progress. Someone
     * walking briefly in front of the camera costs a frame, not a rep.
     *
     * [frame] is the world landmarks. [image] is the same instant as the
     * picture saw it; head-on it is required, since that is where the elbow is
     * read, and a front-view frame without it is dropped.
     */
    /**
     * Follow a frame without judging it: the countdown. The member may start
     * down on "1", so the counter tracks the top and the descent from the
     * start of the countdown; a rep that would finish before "go" is neither
     * counted nor refused. Always returns null.
     */
    fun prime(frame: PoseFrame, image: ImagePose? = null): RepEvent? {
        priming = true
        try {
            onFrame(frame, image)
        } finally {
            priming = false
        }
        return null
    }

    private var priming = false

    /** The last cycle judged or abandoned, for a debug readout. Null until one is. */
    var lastCycle: CycleReadout? = null
        private set

    private fun cycleReadout(cycleMillis: Long) = CycleReadout(
        top = top,
        minElbow = minElbowInCycle,
        shoulderOverHand = minShoulderOverHandInCycle.takeIf { it != Float.MAX_VALUE },
        pushKnee = pushKnees.medianOrNull(),
        pushLegsAboveHands = pushLegs.medianOrNull(),
        hipRise = hipRise(),
        descentKnee = descentKnees.medianOrNull(),
        descentLegsAboveHands = descentLegs.medianOrNull(),
        legsUnseenShare = legsUnseenShare(),
        posture = cyclePostures.medianOrNull(),
        pushPosture = pushPostures.medianOrNull(),
        cycleMillis = cycleMillis,
        heldLegsAboveHands = heldLegs.takeIf { it.size >= MIN_HELD_FRAMES }?.medianOrNull(),
        heldPlacementRatio = heldRatio,
    )

    fun onFrame(frame: PoseFrame, image: ImagePose? = null): RepEvent? {
        val side = nearSideFor(image)
        val reading = ViewReading.of(view, frame, image, side = side, bodyLineToKnee = bodyLineToKnee) ?: run {
            // A frame that cannot be read is dropped, not scored — but where
            // money is staked, one on the way back up is a frame the knee was
            // not seen in: a leg hidden through the push must not leave only
            // the frames it showed in to be judged.
            // During the countdown too: a rep begun on "1" is judged once counting
            // starts, on everything since its bottom.
            if (followLegs && phase == Phase.AT_BOTTOM) {
                pushFrames++
                pushLegsUnseen++
            }
            // The same on the way down, once the arms have really started.
            if (followLegs && phase == Phase.AT_TOP && minElbowInCycle <= rules.descentAttemptDegrees) {
                descentFrames++
                descentKneeUnseen++
            }
            return null
        }

        val elbow = elbowFilter.filter(reading.elbow, frame.timestampMillis)
        val posture = postureFilter.filter(reading.posture, frame.timestampMillis)
        val knee = reading.knee?.let { kneeFilter.filter(it, frame.timestampMillis) }
        // The plank this sitting is measured against is only one the ready gate
        // would accept: on the toes, the body in one line. A member who kneels
        // through the countdown must not set a kneeling reference that their
        // knee reps then match (review, 4 Oct).
        if (priming && image != null && elbow >= rules.obliqueTopDegrees && readyPlank(reading, posture)) {
            placement.placementRatio(image.frame, Aspect.xScale(image.width, image.height))?.let { heldRatios += it }
            (reading as? ViewReading.Angled)?.let { angled ->
                if (kneesSeenOffFloor(angled.knee, angled.legsAboveHands, rules)) angled.legsAboveHands?.let { heldLegs += it }
            }
        }
        calibrateTop(elbow)
        val straight = postureHeld(view, reading, posture, rules)
        (reading as? ViewReading.Angled)?.shoulderOverHand?.let {
            if (cycleStartMillis != 0L && it < minShoulderOverHandInCycle) minShoulderOverHandInCycle = it
        }
        readout = Readout(view, elbow, posture, knee)
        (reading as? ViewReading.Angled)?.let { angled ->
            val hip = angled.hipAboveHand
            val shoulder = angled.shoulderOverHand
            if (hip != null && shoulder != null) {
                when {
                    phase == Phase.AT_BOTTOM && elbow >= MID_PUSH_TO -> {
                        topHips += hip
                        topShoulders += shoulder
                    }
                    phase == Phase.AT_BOTTOM && elbow >= MID_PUSH_FROM -> {
                        midHips += hip
                        midShoulders += shoulder
                    }
                }
            }
        }

        return when (phase) {
            Phase.AWAITING_TOP -> {
                // Body line and knees are judged per cycle, not here: the ready
                // gate already held a proper plank before counting began, and
                // somebody who then drops to their knees or sags should hear why
                // nothing counts, not silence.
                if (elbow >= top) phase = Phase.AT_TOP
                null
            }

            Phase.AT_TOP -> {
                if (cycleStartMillis == 0L && elbow < top) {
                    startCycle(frame.timestampMillis)
                }
                if (cycleStartMillis != 0L) {
                    observe(frame.timestampMillis, elbow, straight, posture, knee, reading as? ViewReading.Angled)
                    if (followLegs && elbow >= PUSH_FROM_ELBOW) {
                        descentFrames++
                        if (knee != null && image != null && side != null && Angles.kneeClearOfEdge(image, side, rules.legEdgeMargin)) {
                            descentKnees += knee
                            (reading as? ViewReading.Angled)?.legsAboveHands?.let { descentLegs += it }
                        } else {
                            descentKneeUnseen++
                        }
                    }
                    if (elbow <= rules.bottomTriggerDegrees) {
                        phase = Phase.AT_BOTTOM
                    } else if (elbow >= top) {
                        // Back at the top without ever reaching the bottom. On the back or
                        // standing it was no push-up to go lower in: nothing is said.
                        lastCycle = cycleReadout(frame.timestampMillis - cycleStartMillis)
                        return abandonCycle(
                            if (minElbowInCycle <= rules.descentAttemptDegrees && !notFaceDown()) RepHint.GO_LOWER else null,
                        )
                    }
                }
                null
            }

            Phase.AT_BOTTOM -> {
                observe(frame.timestampMillis, elbow, straight, posture, knee, reading as? ViewReading.Angled)
                if (elbow >= PUSH_FROM_ELBOW) {
                    pushPostures += posture
                    // A knee at the edge of the picture is the model's guess, not
                    // a knee seen: it neither passes nor fails the knee check.
                    // Nor is one the model could not place (loose trousers).
                    val angled = reading as? ViewReading.Angled
                    val kneeSeen = !followLegs || (image != null && side != null && angled?.kneeUnreadable != true && Angles.kneeClearOfEdge(image, side, rules.legEdgeMargin))
                    pushFrames++
                    if (kneeSeen) {
                        knee?.let { pushKnees += it }
                        angled?.legsAboveHands?.let { pushLegs += it }
                    } else {
                        pushLegsUnseen++
                    }
                }
                if (elbow < top) return null

                // A full cycle: top → bottom → top. Now judge it — unless it
                // happened during the countdown, before counting began.
                if (priming) {
                    phase = Phase.AT_TOP
                    resetCycle()
                    return null
                }
                val cycleMillis = frame.timestampMillis - cycleStartMillis
                lastCycle = cycleReadout(cycleMillis)
                // Depth needs no check here: reaching this phase means the elbow
                // passed `bottomTriggerDegrees`, which `RepRules` requires to be
                // at least as strict as the rule's `bottomDegrees`. The same
                // holds for the top. That is the whole point of the triggers
                // being wider — the rule is satisfied by construction, and S2
                // can move them without weakening it.
                val verdict = when {
                    // Not a push-up at all — on the back, or standing: nothing to coach,
                    // so nothing is said, whatever else the rep got wrong. Sit-ups on
                    // 7 Oct were each told "Go lower" or "Up on your toes" out loud
                    // (reviewer, 8 Oct). Unknown passes on to the checks below.
                    notFaceDown() -> RepEvent.Discarded(null)
                    // Where money is staked, knees never seen on the push are
                    // not knees off the floor: the rep cannot be judged. Nor
                    // can one whose knee spent much of the push out of the
                    // picture or at its edge — the knees may have been down.
                    watchLegs && (pushKnees.isEmpty() && pushLegs.isEmpty() || legsUnseenShare() > rules.maxLegsUnseenShare) ->
                        RepEvent.Discarded(RepHint.LEGS_IN_VIEW)
                    // The way down is held to the same: a knee hidden while
                    // lowering onto it, then shown for the push, proves nothing.
                    watchLegs && (descentKnees.isEmpty() || descentKneeUnseen > rules.maxLegsUnseenShare * descentFrames) ->
                        RepEvent.Discarded(RepHint.LEGS_IN_VIEW)
                    // Depth before knees: it is read off the arms, which the
                    // camera sees best, while a half push-up on the toes reads
                    // the legs lower and the knee more bent than a full one —
                    // on 3 Oct four were told "Knees off the floor". No knee
                    // push-up recorded has stopped short. A rep failing both
                    // is refused either way; only the words change.
                    tooShallowAngled() -> RepEvent.Discarded(RepHint.GO_LOWER)
                    // Knees before the body line: a push-up from the knees is a
                    // different exercise, and "keep your body straight" would
                    // not say so.
                    judgeKnees && !kneesOffFloor(pushKnees.medianOrNull(), pushLegs.medianOrNull(), rules, descentKnees.medianOrNull()) ->
                        RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES)
                    // Down from the knees, up on the toes: half of it was on the floor.
                    judgeKnees && !kneesOffFloor(descentKnees.medianOrNull(), null, rules) ->
                        RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES)
                    // Legs well under this person's own plank: knees down, whatever the body.
                    judgeKnees && legsDroppedFromPlank() -> RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES)
                    !bodyHeld() -> RepEvent.Discarded(RepHint.KEEP_BODY_STRAIGHT)
                    // The chest first and the hips after: off the floor, or a cobra.
                    hipRiseTooLow() -> RepEvent.Discarded(RepHint.KEEP_BODY_STRAIGHT)
                    cycleMillis < rules.minCycleMillis -> RepEvent.Discarded(RepHint.SLOW_DOWN)
                    cycleMillis > rules.maxCycleMillis -> RepEvent.Discarded(null)
                    else -> {
                        count += 1
                        RepEvent.Counted(count, cycleMillis, stance())
                    }
                }
                phase = Phase.AT_TOP
                resetCycle()
                verdict
            }
        }
    }

    /** Hard's knee rule applied to the rep just counted, at 45° only ([RepStance]). */
    private fun stance(): RepStance? {
        if (view != CameraView.OBLIQUE) return null
        val unseen = (pushKnees.isEmpty() && pushLegs.isEmpty()) ||
            legsUnseenShare() > rules.maxLegsUnseenShare ||
            descentKnees.isEmpty() ||
            descentKneeUnseen > rules.maxLegsUnseenShare * descentFrames
        return when {
            unseen -> RepStance.NOT_VISIBLE
            !kneesOffFloor(pushKnees.medianOrNull(), pushLegs.medianOrNull(), rules, descentKnees.medianOrNull()) -> RepStance.KNEES
            !kneesOffFloor(descentKnees.medianOrNull(), null, rules) -> RepStance.KNEES
            // No plank on the toes held through the countdown — the Daily 25 may start from
            // the knees — so Hard's own-plank rule cannot be applied: not claimed for the toes
            // (review, PR #14). Hard never starts without one ([plankCalibrated]).
            !plankCalibrated -> RepStance.NOT_VISIBLE
            // Hard's own-plank rule too: a second person's knee reps clear the line set on
            // one body, and would be claimed for the toes (PR #14 review).
            legsDroppedFromPlank() -> RepStance.KNEES
            else -> RepStance.TOES
        }
    }

    /**
     * The top of a rep in force. Head-on it is the view's trigger. **At 45° it
     * is this sitting's own**: how a locked-out arm reads depends on exactly
     * where the phone stands — 150–158° at 45–50° measured on the floor,
     * 167–173° from a more frontal spot — so no one number serves. The member
     * holds the top through the countdown ([prime]); the line is that reading
     * less [RepRules.obliqueTopMarginDegrees], never under
     * [RepRules.obliqueTopDegrees] and never over the rule's own top. Until it
     * is set the floor is used, so the countdown is followed; with no
     * countdown to read, the rule's own top stands.
     */
    private var top = if (view == CameraView.OBLIQUE) this.rules.obliqueTopDegrees else this.rules.topTriggerDegrees
    private var topSet = view != CameraView.OBLIQUE
    private val heldElbows = mutableListOf<Float>()

    private fun calibrateTop(elbow: Float) {
        if (topSet) return
        if (priming) {
            if (elbow >= rules.obliqueTopDegrees) heldElbows += elbow
            return
        }
        heldRatio = heldRatios.takeIf { it.size >= MIN_HELD_FRAMES }?.let { it.sorted()[it.size / 2] }
        top = if (heldElbows.size < MIN_HELD_FRAMES) {
            rules.topTriggerDegrees
        } else {
            (heldElbows.sorted()[heldElbows.size / 2] - rules.obliqueTopMarginDegrees).coerceIn(rules.obliqueTopDegrees, rules.topTriggerDegrees)
        }
        topSet = true
    }

    private fun legsUnseenShare(): Float = if (pushFrames == 0) 0f else pushLegsUnseen.toFloat() / pushFrames

    private fun startCycle(timestampMillis: Long) {
        resetCycle()
        cycleStartMillis = timestampMillis
    }

    private fun observe(
        timestampMillis: Long,
        elbow: Float,
        straight: Boolean,
        posture: Float,
        knee: Float?,
        angled: ViewReading.Angled?,
    ) {
        // Until the arms have really started down, the rep's clock keeps
        // restarting: resting at the top with the elbow wobbling around the
        // trigger must not count as time spent in the rep (rule 4).
        if (minElbowInCycle > rules.descentAttemptDegrees && elbow > rules.descentAttemptDegrees) {
            cycleStartMillis = timestampMillis
        }
        if (elbow < minElbowInCycle) minElbowInCycle = elbow
        if (!straight) bodyLineHeld = false
        cyclePostures += posture
        knee?.let { cycleKnees += it }
        angled?.kneeAboveHip?.let { cycleKneeAboveHip += it }
        angled?.ankleBelowHand?.let { cycleAnkleBelowHand += it }
    }

    /** At 45°: the near shoulder never came down close enough to the hand. */
    private fun tooShallowAngled(): Boolean {
        if (view != CameraView.OBLIQUE || minShoulderOverHandInCycle == Float.MAX_VALUE) return false
        if (minShoulderOverHandInCycle <= rules.maxAngledShoulderOverHand) return false
        return minShoulderOverHandInCycle > rules.maxAngledShoulderOverHandDeepElbow || minElbowInCycle > rules.deepElbowDegrees
    }

    /**
     * The body held through the rep. At 45° judged on the rep's median, which
     * one glitching frame cannot move; elsewhere every frame must hold.
     */
    private fun bodyHeld(): Boolean = if (view == CameraView.OBLIQUE) {
        (cyclePostures.medianOrNull() ?: 180f) >= rules.angledBodyLineFloorDegrees &&
            (pushPostures.medianOrNull() ?: 180f) >= rules.angledPushBodyLineFloorDegrees
    } else {
        bodyLineHeld
    }

    private fun List<Float>.medianOrNull(): Float? = if (isEmpty()) null else sorted()[size / 2]

    private companion object {
        /** Past this on the way up, the arms are pushing the body, not still at the bottom. */
        const val PUSH_FROM_ELBOW = 100f

        /** Half-way up the push: where the hips are read against the shoulders. */
        const val MID_PUSH_FROM = 110f
        const val MID_PUSH_TO = 140f

        /** Countdown frames at the top needed to set the 45° top from: about half a second. */
        const val MIN_HELD_FRAMES = 10
    }

    /**
     * A descent that came back up without reaching the bottom. Silent unless it
     * went far enough to have been a real attempt — shifting at the top is not
     * a failed rep and must not produce a hint every second.
     */
    private fun abandonCycle(hint: RepHint?): RepEvent? {
        resetCycle()
        return hint?.let { RepEvent.Discarded(it) }
    }

    private fun resetCycle() {
        cycleStartMillis = 0L
        minElbowInCycle = Float.MAX_VALUE
        bodyLineHeld = true
        cycleKnees.clear()
        cyclePostures.clear()
        cycleKneeAboveHip.clear()
        cycleAnkleBelowHand.clear()
        pushPostures.clear()
        pushKnees.clear()
        pushLegs.clear()
        midHips.clear()
        midShoulders.clear()
        topHips.clear()
        topShoulders.clear()
        descentKnees.clear()
        descentLegs.clear()
        descentFrames = 0
        descentKneeUnseen = 0
        pushFrames = 0
        pushLegsUnseen = 0
        minShoulderOverHandInCycle = Float.MAX_VALUE
    }
}
