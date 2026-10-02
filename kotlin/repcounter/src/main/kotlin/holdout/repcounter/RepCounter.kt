package holdout.repcounter

/**
 * The push-up rule, and the machine that applies it (FR-021, PRD §8.4, T048).
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
     * Head-on only: the steepest the torso may rise from hips to shoulders and
     * still be a body down in push-up position. It stands in for the body line,
     * which a front camera cannot see (`ViewReading`). Measured on
     * `front-honest-01`: 0–41° through every rep, 65–87° crouching and standing
     * up. Provisional, like the triggers, until S2 has more than one recording.
     */
    val maxTorsoElevationDegrees: Float = 55f,
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
     * Head-on depth: at the bottom the shoulders must come down to within this
     * share of their height above the hands at the top. The elbow angle alone
     * over-reads the bend head-on; on the phone honest reps reached 0.16–0.19
     * and half reps stopped at 0.26–0.53.
     */
    val maxShoulderHeightAtBottom: Float = 0.25f,
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

/** What the sitting screen shows when a rep is thrown away (PRD §8.4). */
enum class RepHint(val message: String) {
    GO_LOWER("Go lower"),
    KEEP_BODY_STRAIGHT("Keep your body straight"),
    SLOW_DOWN("Slow down"),
    UP_ON_YOUR_TOES("Knees off the floor"),

    /** Staked only: the knee was not in the picture on the push — unseen, or at its edge, where the model guesses — so nothing proved it off the floor. */
    LEGS_IN_VIEW("Keep your knees in the picture"),
}

/**
 * What the counter read from the last frame it scored, smoothed exactly as the
 * rule sees it. For tuning on a phone — a debug readout — never for deciding
 * anything; the rule lives in [RepCounter] alone.
 */
data class Readout(
    val view: CameraView,
    val elbow: Float,
    /** At 45°: the near side's shoulder–hip–ankle line. Head-on: the torso's rise above horizontal. */
    val posture: Float,
    /** Near leg at 45°; null head-on, or when the leg is not clearly in frame. */
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
    val legsUnseenShare: Float,
    val posture: Float?,
    val pushPosture: Float?,
    val cycleMillis: Long,
)

sealed interface RepEvent {
    /** A rep met every rule. [total] counts reps in this sitting. */
    data class Counted(val total: Int, val cycleMillis: Long) : RepEvent

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
        // Pre-flight already refuses to start a staked sitting anywhere but at
        // 45°. This is the second lock on the same door: a staked counter
        // measured from a view that cannot see the knees must not exist.
        require(stakes != Stakes.STAKED || stakes.accepts(view)) { "$stakes sittings cannot be counted from the $view view" }
    }

    private val rules = rules.forView(view)

    /**
     * Knees are judged only when money is on it. The Daily 25 counts push-ups
     * from the knees on purpose: it is a free habit, and a member building up
     * to full push-ups should be able to keep it (FR-021a).
     */
    private val judgeKnees = stakes == Stakes.STAKED

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
        if (image == null || view == CameraView.FRONT) return null
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

    /** The body line while pushing back up — after the bottom, elbow past [PUSH_FROM_ELBOW]. */
    private val pushPostures = mutableListOf<Float>()
    private val pushKnees = mutableListOf<Float>()
    private val pushLegs = mutableListOf<Float>()

    /** Push frames, and those whose near knee was unseen or at the picture's edge (staked). */
    private var pushFrames = 0
    private var pushLegsUnseen = 0
    private var minShoulderHeightInCycle = Float.MAX_VALUE
    private var minShoulderOverHandInCycle = Float.MAX_VALUE
    private var topShoulderHeight: Float? = null

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
        legsUnseenShare = legsUnseenShare(),
        posture = cyclePostures.medianOrNull(),
        pushPosture = pushPostures.medianOrNull(),
        cycleMillis = cycleMillis,
    )

    fun onFrame(frame: PoseFrame, image: ImagePose? = null): RepEvent? {
        val side = nearSideFor(image)
        val reading = ViewReading.of(view, frame, image, side = side) ?: run {
            // A frame that cannot be read is dropped, not scored — but where
            // money is staked, one on the way back up is a frame the knee was
            // not seen in: a leg hidden through the push must not leave only
            // the frames it showed in to be judged.
            if (judgeKnees && !priming && phase == Phase.AT_BOTTOM) {
                pushFrames++
                pushLegsUnseen++
            }
            return null
        }

        val elbow = elbowFilter.filter(reading.elbow, frame.timestampMillis)
        val posture = postureFilter.filter(reading.posture, frame.timestampMillis)
        val knee = reading.knee?.let { kneeFilter.filter(it, frame.timestampMillis) }
        calibrateTop(elbow)
        val straight = postureHeld(view, reading, posture, rules)
        val shoulderHeight = (reading as? ViewReading.HeadOn)?.shoulderHeight
        (reading as? ViewReading.Angled)?.shoulderOverHand?.let {
            if (cycleStartMillis != 0L && it < minShoulderOverHandInCycle) minShoulderOverHandInCycle = it
        }
        readout = Readout(view, elbow, posture, knee)
        // The shoulders' height over the hands at the top — the reference the
        // head-on depth check is a share of. Tracked while the arms are straight.
        if (elbow >= top && shoulderHeight != null) {
            topShoulderHeight = topShoulderHeight?.let { it * 0.9f + shoulderHeight * 0.1f } ?: shoulderHeight
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
                    observe(frame.timestampMillis, elbow, straight, posture, knee, shoulderHeight)
                    if (elbow <= rules.bottomTriggerDegrees) {
                        phase = Phase.AT_BOTTOM
                    } else if (elbow >= top) {
                        // Back at the top without ever reaching the bottom.
                        lastCycle = cycleReadout(frame.timestampMillis - cycleStartMillis)
                        return abandonCycle(
                            if (minElbowInCycle <= rules.descentAttemptDegrees) RepHint.GO_LOWER else null,
                        )
                    }
                }
                null
            }

            Phase.AT_BOTTOM -> {
                observe(frame.timestampMillis, elbow, straight, posture, knee, shoulderHeight)
                if (elbow >= PUSH_FROM_ELBOW) {
                    pushPostures += posture
                    // A knee at the edge of the picture is the model's guess, not
                    // a knee seen: it neither passes nor fails the knee check.
                    val kneeSeen = !judgeKnees || (image != null && side != null && Angles.kneeClearOfEdge(image, side, rules.legEdgeMargin))
                    pushFrames++
                    if (kneeSeen) {
                        knee?.let { pushKnees += it }
                        (reading as? ViewReading.Angled)?.legsAboveHands?.let { pushLegs += it }
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
                    // Knees first: a push-up from the knees is a different
                    // exercise, and "keep your body straight" would not say so.
                    // Where money is staked, knees never seen on the push are
                    // not knees off the floor: the rep cannot be judged. Nor
                    // can one whose knee spent much of the push out of the
                    // picture or at its edge — the knees may have been down.
                    judgeKnees && (pushKnees.isEmpty() && pushLegs.isEmpty() || legsUnseenShare() > rules.maxLegsUnseenShare) ->
                        RepEvent.Discarded(RepHint.LEGS_IN_VIEW)
                    judgeKnees && !kneesOffFloor(pushKnees.medianOrNull(), pushLegs.medianOrNull(), rules) ->
                        RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES)
                    tooShallowHeadOn() || tooShallowAngled() -> RepEvent.Discarded(RepHint.GO_LOWER)
                    !bodyHeld() -> RepEvent.Discarded(RepHint.KEEP_BODY_STRAIGHT)
                    cycleMillis < rules.minCycleMillis -> RepEvent.Discarded(RepHint.SLOW_DOWN)
                    cycleMillis > rules.maxCycleMillis -> RepEvent.Discarded(null)
                    else -> {
                        count += 1
                        RepEvent.Counted(count, cycleMillis)
                    }
                }
                phase = Phase.AT_TOP
                resetCycle()
                verdict
            }
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
        shoulderHeight: Float?,
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
        if (shoulderHeight != null && shoulderHeight < minShoulderHeightInCycle) minShoulderHeightInCycle = shoulderHeight
    }

    /** Head-on only: the shoulders never came down far enough towards the hands. */
    private fun tooShallowHeadOn(): Boolean {
        if (view != CameraView.FRONT) return false
        val top = topShoulderHeight ?: return false
        if (minShoulderHeightInCycle == Float.MAX_VALUE || top <= 0f) return false
        return minShoulderHeightInCycle / top > rules.maxShoulderHeightAtBottom
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
        pushPostures.clear()
        pushKnees.clear()
        pushLegs.clear()
        pushFrames = 0
        pushLegsUnseen = 0
        minShoulderHeightInCycle = Float.MAX_VALUE
        minShoulderOverHandInCycle = Float.MAX_VALUE
    }
}
