package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The "Background still" pre-flight check's number: how much of the scene behind the member moved. */
class BackgroundMotionTest {
    private val cols = 10
    private val rows = 10

    private fun grid(fill: (col: Int, row: Int) -> Float) = FloatArray(cols * rows) { fill(it % cols, it / cols) }

    private fun settled(motion: BackgroundMotion, frame: FloatArray, body: BodyBox? = null): Float {
        var last = 0f
        repeat(20) { last = motion.update(frame, cols, rows, body) }
        return last
    }

    @Test
    fun `a still scene reads still`() {
        val m = BackgroundMotion()
        val scene = grid { c, r -> 0.3f + 0.04f * ((c + r) % 5) }
        assertEquals(0f, settled(m, scene))
    }

    @Test
    fun `the whole frame brightening — auto-exposure — is not motion`() {
        val m = BackgroundMotion()
        val scene = grid { c, r -> 0.3f + 0.04f * ((c + r) % 5) }
        m.update(scene, cols, rows, null)
        val brighter = grid { c, r -> 0.45f + 0.04f * ((c + r) % 5) }
        assertTrue(m.update(brighter, cols, rows, null) < 0.05f)
    }

    @Test
    fun `something moving behind the member fails the check`() {
        val m = BackgroundMotion()
        var flip = false
        var last = 0f
        repeat(20) {
            flip = !flip
            // A quarter of the frame, top-left, changing every frame: a TV, a person walking past.
            val frame = grid { c, r -> if (c < 5 && r < 5) (if (flip) 0.9f else 0.1f) else 0.4f }
            last = m.update(frame, cols, rows, null)
        }
        assertTrue(last > Preflight.DEFAULT_MAX_BACKGROUND_MOTION, "read $last")
    }

    @Test
    fun `the member moving inside their own box is not background motion`() {
        val m = BackgroundMotion()
        val body = BodyBox(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        var flip = false
        var last = 1f
        repeat(20) {
            flip = !flip
            val frame = grid { c, r -> if (c < 5 && r < 5) (if (flip) 0.9f else 0.1f) else 0.4f }
            last = m.update(frame, cols, rows, body)
        }
        assertEquals(0f, last)
    }

    @Test
    fun `a new frame size starts over rather than comparing different grids`() {
        val m = BackgroundMotion()
        m.update(FloatArray(100) { 0.2f }, 10, 10, null)
        assertEquals(0f, m.update(FloatArray(64) { 0.9f }, 8, 8, null))
    }
}
