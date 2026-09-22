package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plank that starts a sitting, in place of the palm that used to.
 *
 * Stated as a movement, like every other rule test: "held at the top of a
 * push-up". The trigger values come from [RepRules] and spike S2 will move
 * them; nothing here names one.
 */
class ReadyGateTest {

    private val gate = ReadyGate(holdMillis = 1_000)

    /** The top of a push-up: arms extended, body straight. */
    private fun top(t: Long) = Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f)

    /** Half way down — a real position, but not the ready one. */
    private fun midRep(t: Long) = Traces.frame(t, elbowDegrees = 120f, bodyLineDegrees = 180f)

    /** At the top, but sagging badly. */
    private fun sagging(t: Long) = Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 150f)

    @Test
    fun `holding the top for a second opens the gate`() {
        assertFalse(gate.onFrame(top(0), 0, pillsGreen = true))
        assertFalse(gate.onFrame(top(500), 500, pillsGreen = true))
        assertTrue(gate.onFrame(top(1_000), 1_000, pillsGreen = true))
    }

    @Test
    fun `it does not open early`() {
        gate.onFrame(top(0), 0, pillsGreen = true)
        assertFalse(gate.onFrame(top(999), 999, pillsGreen = true))
    }

    @Test
    fun `leaving the position restarts the hold`() {
        gate.onFrame(top(0), 0, pillsGreen = true)
        gate.onFrame(top(900), 900, pillsGreen = true)
        gate.onFrame(midRep(950), 950, pillsGreen = true)
        assertFalse(
            gate.onFrame(top(1_100), 1_100, pillsGreen = true),
            "the second must be counted from scratch after dropping out of position",
        )
        assertTrue(gate.onFrame(top(2_100), 2_100, pillsGreen = true))
    }

    @Test
    fun `sagging hips are not the ready position`() {
        gate.onFrame(sagging(0), 0, pillsGreen = true)
        assertFalse(gate.onFrame(sagging(2_000), 2_000, pillsGreen = true))
    }

    @Test
    fun `a red pill restarts the hold, however long the position was held`() {
        gate.onFrame(top(0), 0, pillsGreen = true)
        gate.onFrame(top(900), 900, pillsGreen = true)
        gate.onFrame(top(950), 950, pillsGreen = false)
        assertFalse(gate.onFrame(top(1_100), 1_100, pillsGreen = true))
    }

    @Test
    fun `a frame the model could not read neither advances nor breaks the hold`() {
        gate.onFrame(top(0), 0, pillsGreen = true)
        assertFalse(gate.onFrame(Traces.blind(500), 500, pillsGreen = true))
        // Somebody walking past the lens costs a frame, not the countdown.
        assertTrue(gate.onFrame(top(1_000), 1_000, pillsGreen = true))
    }

    @Test
    fun `progress reports the hold so a ring can fill`() {
        gate.onFrame(top(0), 0, pillsGreen = true)
        gate.onFrame(top(500), 500, pillsGreen = true)
        assertTrue(gate.progress in 0.4f..0.6f, "expected about half, got ${gate.progress}")
        gate.onFrame(midRep(600), 600, pillsGreen = true)
        assertTrue(gate.progress == 0f, "dropping out should empty the ring")
    }

    @Test
    fun `reset clears the hold`() {
        gate.onFrame(top(0), 0, pillsGreen = true)
        gate.onFrame(top(900), 900, pillsGreen = true)
        gate.reset()
        assertFalse(gate.onFrame(top(1_000), 1_000, pillsGreen = true))
    }
}
