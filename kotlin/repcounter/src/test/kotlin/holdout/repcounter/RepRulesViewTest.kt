package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The guard on per-view tuning.
 *
 * Both camera placements are accepted, and head-on is the harder one to measure.
 * When spike S2 gives the front view its own trigger set, the tempting fix for a
 * body line that will not read head-on is to widen the trigger past the rule's
 * ±10°. That is not tuning — it counts reps FR-021 forbids.
 *
 * This test fails the day somebody tries.
 *
 * Head-on the body line is not judged at all, and that is a different thing
 * from widening it: the camera cannot see it from there, so no tolerance would
 * make the reading mean anything. `ViewReading` names what that costs.
 */
class RepRulesViewTest {

    @Test
    fun `every view has rules, and none of them is looser than the rule`() {
        val base = RepRules()
        for (view in CameraView.entries) {
            val rules = base.forView(view)

            assertTrue(
                rules.topTriggerDegrees >= rules.topDegrees,
                "$view: the top trigger ${rules.topTriggerDegrees} is looser than the rule's ${rules.topDegrees}",
            )
            assertTrue(
                rules.bottomTriggerDegrees <= rules.bottomDegrees,
                "$view: the bottom trigger ${rules.bottomTriggerDegrees} is looser than the rule's ${rules.bottomDegrees}",
            )
            assertTrue(
                rules.bodyLineToleranceDegrees <= base.bodyLineToleranceDegrees,
                "$view: the body line tolerance was widened past the rule",
            )
            assertTrue(
                rules.minCycleMillis >= base.minCycleMillis && rules.maxCycleMillis <= base.maxCycleMillis,
                "$view: the cycle bounds were widened past the rule",
            )
        }
    }

    @Test
    fun `the rule itself is what FR-021 and the PRD say`() {
        // If this changes, the product changed — not the tuning.
        val rules = RepRules()
        assertTrue(rules.topDegrees == 160f)
        assertTrue(rules.bottomDegrees == 90f)
        assertTrue(rules.bodyLineToleranceDegrees == 10f)
        assertTrue(rules.minCycleMillis == 800L && rules.maxCycleMillis == 6_000L)
    }
}
