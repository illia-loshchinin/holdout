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
     * Legs straight, on the toes: the knee, hip–knee–ankle, never below this.
     * A push-up from the knees bends them to 90–120°, so the margin to an
     * honest plank's ~175° is wide on purpose — this has to catch the cheat
     * without ever docking an honest rep. Judged at 45°, the placement in
     * which the picture shows it ([Stakes]). Provisional until S2 records an
     * honest angled set.
     */
    val minKneeDegrees: Float = 150f,
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
    CameraView.SIDE, CameraView.OBLIQUE, CameraView.FRONT -> this
}

/** What the sitting screen shows when a rep is thrown away (PRD §8.4). */
enum class RepHint(val message: String) {
    GO_LOWER("Go lower"),
    KEEP_BODY_STRAIGHT("Keep your body straight"),
    SLOW_DOWN("Slow down"),
    UP_ON_YOUR_TOES("Up on your toes, knees off the floor"),
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

    private var phase = Phase.AWAITING_TOP
    private var count = 0

    /** Cycle state, all reset by [startCycle]. */
    private var cycleStartMillis = 0L
    private var minElbowInCycle = Float.MAX_VALUE
    private var bodyLineHeld = true
    private var kneesDown = false

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
    fun onFrame(frame: PoseFrame, image: ImagePose? = null): RepEvent? {
        val reading = ViewReading.of(view, frame, image) ?: return null

        val elbow = elbowFilter.filter(reading.elbow, frame.timestampMillis)
        val posture = postureFilter.filter(reading.posture, frame.timestampMillis)
        val knee = reading.knee?.let { kneeFilter.filter(it, frame.timestampMillis) }
        val straight = reading.postureHeld(posture, rules)
        val onToes = kneesStraight(knee, rules)
        readout = Readout(view, elbow, posture, knee)

        return when (phase) {
            Phase.AWAITING_TOP -> {
                // Body line and knees are judged per cycle, not here: the ready
                // gate already held a proper plank before counting began, and
                // somebody who then drops to their knees or sags should hear why
                // nothing counts, not silence.
                if (elbow >= rules.topTriggerDegrees) phase = Phase.AT_TOP
                null
            }

            Phase.AT_TOP -> {
                if (cycleStartMillis == 0L && elbow < rules.topTriggerDegrees) {
                    startCycle(frame.timestampMillis)
                }
                if (cycleStartMillis != 0L) {
                    observe(elbow, straight, onToes)
                    if (elbow <= rules.bottomTriggerDegrees) {
                        phase = Phase.AT_BOTTOM
                    } else if (elbow >= rules.topTriggerDegrees) {
                        // Back at the top without ever reaching the bottom.
                        return abandonCycle(
                            if (minElbowInCycle <= rules.descentAttemptDegrees) RepHint.GO_LOWER else null,
                        )
                    }
                }
                null
            }

            Phase.AT_BOTTOM -> {
                observe(elbow, straight, onToes)
                if (elbow < rules.topTriggerDegrees) return null

                // A full cycle: top → bottom → top. Now judge it.
                val cycleMillis = frame.timestampMillis - cycleStartMillis
                // Depth needs no check here: reaching this phase means the elbow
                // passed `bottomTriggerDegrees`, which `RepRules` requires to be
                // at least as strict as the rule's `bottomDegrees`. The same
                // holds for the top. That is the whole point of the triggers
                // being wider — the rule is satisfied by construction, and S2
                // can move them without weakening it.
                val verdict = when {
                    // Knees first: a push-up from the knees is a different
                    // exercise, and "keep your body straight" would not say so.
                    kneesDown -> RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES)
                    !bodyLineHeld -> RepEvent.Discarded(RepHint.KEEP_BODY_STRAIGHT)
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

    private fun startCycle(timestampMillis: Long) {
        cycleStartMillis = timestampMillis
        minElbowInCycle = Float.MAX_VALUE
        bodyLineHeld = true
        kneesDown = false
    }

    private fun observe(elbow: Float, straight: Boolean, onToes: Boolean) {
        if (elbow < minElbowInCycle) minElbowInCycle = elbow
        if (!straight) bodyLineHeld = false
        if (!onToes) kneesDown = true
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
        kneesDown = false
    }
}
