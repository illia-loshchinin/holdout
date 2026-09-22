package holdout.repcounter

import kotlin.math.abs

/**
 * The push-up rule, and the machine that applies it (FR-021, PRD §8.4, T048).
 *
 * **The rule** — what the product promises, and what the tests assert:
 *
 *   1. Top     elbow > 160°, shoulder–hip–ankle within ±10° of straight
 *   2. Bottom  elbow < 90°, body line held
 *   3. Top     elbow > 160°, body line held
 *   4. Cycle   between 0.8 and 6 seconds
 *
 * **The implementation** — how it survives the model's own error (research R4):
 * angles come from 3D world landmarks, are one-euro smoothed before any
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

/** What the sitting screen shows when a rep is thrown away (PRD §8.4). */
enum class RepHint(val message: String) {
    GO_LOWER("Go lower"),
    KEEP_BODY_STRAIGHT("Keep your body straight"),
    SLOW_DOWN("Slow down"),
}

sealed interface RepEvent {
    /** A rep met every rule. [total] counts reps in this sitting. */
    data class Counted(val total: Int, val cycleMillis: Long) : RepEvent

    /**
     * A cycle was attempted and thrown away. Never surfaced as an error.
     *
     * [hint] is null when none of the three the copy deck defines fits — a
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
class RepCounter(private val rules: RepRules = RepRules()) {

    private enum class Phase {
        /** Nothing yet: waiting for the body to be at the top, straight. */
        AWAITING_TOP,

        /** At the top of a rep, straight. A descent from here starts a cycle. */
        AT_TOP,

        /** Below the bottom trigger. The return to the top completes the cycle. */
        AT_BOTTOM,
    }

    private val elbowFilter = OneEuroFilter()
    private val bodyLineFilter = OneEuroFilter()

    private var phase = Phase.AWAITING_TOP
    private var count = 0

    /** Cycle state, all reset by [startCycle]. */
    private var cycleStartMillis = 0L
    private var minElbowInCycle = Float.MAX_VALUE
    private var bodyLineHeld = true

    val total: Int get() = count

    /**
     * Frames whose joints are not clearly visible, and frames where the angles
     * cannot be computed, are **dropped rather than scored** — they do not
     * advance the machine and they do not break a cycle in progress. Someone
     * walking briefly in front of the camera costs a frame, not a rep.
     */
    fun onFrame(frame: PoseFrame): RepEvent? {
        val rawElbow = Angles.elbow(frame) ?: return null
        val rawBodyLine = Angles.bodyLine(frame) ?: return null

        val elbow = elbowFilter.filter(rawElbow, frame.timestampMillis)
        val bodyLine = bodyLineFilter.filter(rawBodyLine, frame.timestampMillis)
        val straight = abs(180f - bodyLine) <= rules.bodyLineToleranceDegrees

        return when (phase) {
            Phase.AWAITING_TOP -> {
                if (elbow >= rules.topTriggerDegrees && straight) phase = Phase.AT_TOP
                null
            }

            Phase.AT_TOP -> {
                if (cycleStartMillis == 0L && elbow < rules.topTriggerDegrees) {
                    startCycle(frame.timestampMillis)
                }
                if (cycleStartMillis != 0L) {
                    observe(elbow, straight)
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
                observe(elbow, straight)
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
    }

    private fun observe(elbow: Float, straight: Boolean) {
        if (elbow < minElbowInCycle) minElbowInCycle = elbow
        if (!straight) bodyLineHeld = false
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
    }
}
