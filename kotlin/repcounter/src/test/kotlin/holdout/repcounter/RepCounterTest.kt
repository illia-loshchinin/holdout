package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T049. **These assert the rule, never the trigger values.**
 *
 * The rule is FR-021 and research R4: top above 160°, bottom below 90°, body line
 * within ±10° of straight, cycle between 0.8 and 6 seconds. The triggers (165°
 * / 85°) are an implementation detail that spike S2 is expected to move once it
 * has measured a real model on a real Seeker. A test that pinned 165 would have
 * to be rewritten every time the counter is tuned, which is how a suite stops
 * being evidence and becomes a chore.
 *
 * So every case here is stated in terms of a movement — "a proper push-up", "a
 * half rep", "sagging hips" — and the last test runs the honest set again under
 * deliberately different triggers to prove the suite is not measuring them.
 */
class RepCounterTest {

    private fun run(frames: List<PoseFrame>, rules: RepRules = RepRules()): Pair<Int, List<RepHint?>> {
        val counter = RepCounter(rules)
        val hints = mutableListOf<RepHint?>()
        for (frame in frames) {
            when (val event = counter.onFrame(frame)) {
                is RepEvent.Discarded -> hints += event.hint
                else -> Unit
            }
        }
        return counter.total to hints
    }

    @Test
    fun `a set of 25 proper push-ups counts 25`() {
        val (count, hints) = run(Traces.set(count = 25))
        assertEquals(25, count)
        assertTrue(hints.isEmpty(), "a clean set should produce no hints, got $hints")
    }

    @Test
    fun `the honest count is within 1 rep of the truth - the spike's bar`() {
        // S2's pass bar, applied to the shapes a synthetic trace can carry:
        // varying tempo and depth across the set, as a real person does.
        val frames = buildList {
            addAll(Traces.settleAtTop(0))
            var t = last().timestampMillis + Traces.FRAME_MILLIS
            repeat(25) { i ->
                val rep = Traces.pushUp(
                    startMillis = t,
                    top = if (i % 3 == 0) 170f else 178f,
                    bottom = if (i % 4 == 0) 80f else 65f,
                    cycleMillis = 1_400L + (i % 5) * 400L,
                )
                addAll(rep)
                t = rep.last().timestampMillis + Traces.FRAME_MILLIS
            }
        }
        val (count, _) = run(frames)
        assertTrue(count in 24..26, "expected 25 ± 1, got $count")
    }

    @Test
    fun `half reps are rejected, with Go lower`() {
        // Down to 110° — a clear descent, nowhere near the rule's 90°.
        val (count, hints) = run(Traces.set(count = 10, bottom = 110f))
        assertEquals(0, count)
        assertTrue(
            hints.isNotEmpty() && hints.all { it == RepHint.GO_LOWER },
            "expected only Go lower, got $hints",
        )
    }

    @Test
    fun `sagging hips are rejected, with Keep your body straight`() {
        // The hips drop to 25° off straight at the bottom — outside the ±10°.
        val (count, hints) = run(Traces.set(count = 10, bodyLineAtBottom = 155f))
        assertEquals(0, count)
        assertTrue(
            hints.contains(RepHint.KEEP_BODY_STRAIGHT),
            "expected Keep your body straight, got $hints",
        )
    }

    @Test
    fun `reps faster than the rule allows are rejected, with Slow down`() {
        val (count, hints) = run(Traces.set(count = 10, cycleMillis = 500))
        assertEquals(0, count)
        assertTrue(hints.contains(RepHint.SLOW_DOWN), "expected Slow down, got $hints")
    }

    @Test
    fun `a cycle slower than the rule allows is rejected in silence`() {
        // Over six seconds. None of the three hints tells the member anything
        // useful here, so the rep is dropped without one.
        val (count, hints) = run(Traces.set(count = 3, cycleMillis = 9_000))
        assertEquals(0, count)
        assertTrue(hints.isNotEmpty() && hints.all { it == null }, "expected silence, got $hints")
    }

    @Test
    fun `frames the model could not read are dropped, not scored`() {
        // Every third frame is blind — someone passing the camera, a hand over
        // the lens. The count must not change.
        val clean = Traces.set(count = 25)
        val withDropouts = clean.flatMapIndexed { i, frame ->
            if (i % 3 == 2) listOf(frame, Traces.blind(frame.timestampMillis + 1)) else listOf(frame)
        }
        assertEquals(25, run(withDropouts).first)
    }

    @Test
    fun `jitter at the top cannot count a rep`() {
        // The model's own error, around 2°, wobbling around the top. This is
        // the failure the hysteresis and the one-euro filter exist for: without
        // them this trace scores reps that never happened.
        val frames = buildList {
            var t = 0L
            repeat(400) { i ->
                val wobble = if (i % 2 == 0) 2.5f else -2.5f
                add(Traces.frame(t, 166f + wobble, 180f))
                t += Traces.FRAME_MILLIS
            }
        }
        assertEquals(0, run(frames).first)
    }

    @Test
    fun `the suite measures the rule, not the triggers`() {
        // Same honest set, three trigger settings. If any assertion above were
        // really pinned to 165 or 85, this would fail.
        val frames = Traces.set(count = 25)
        for (rules in listOf(
            RepRules(),
            RepRules(topTriggerDegrees = 162f, bottomTriggerDegrees = 88f),
            RepRules(topTriggerDegrees = 168f, bottomTriggerDegrees = 78f),
        )) {
            val (count, _) = run(frames, rules)
            assertEquals(25, count, "triggers $rules changed the honest count")
        }
    }

    @Test
    fun `a trigger looser than the rule is refused outright`() {
        // The triggers exist to be stricter than the rule. Letting S2 set one
        // the other way would let a rep the rule forbids be counted, and no
        // test downstream would notice.
        val tooLoose = runCatching { RepRules(topTriggerDegrees = 155f) }
        assertTrue(tooLoose.isFailure, "a top trigger below the rule's 160° must be refused")
        val tooDeep = runCatching { RepRules(bottomTriggerDegrees = 95f) }
        assertTrue(tooDeep.isFailure, "a bottom trigger above the rule's 90° must be refused")
    }
}
