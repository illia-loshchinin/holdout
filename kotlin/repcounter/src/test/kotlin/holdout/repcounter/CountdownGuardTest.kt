package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The countdown keeps checking what started it: the gating pills and the placement counting is locked to. */
class CountdownGuardTest {
    @Test
    fun `a countdown whose checks keep passing runs through`() {
        val guard = CountdownGuard()
        assertTrue((0L..3_000L step 33).none { guard.shouldAbort(it, holds = true) })
    }

    @Test
    fun `one bad frame does not stop it`() {
        val guard = CountdownGuard()
        assertFalse(guard.shouldAbort(0, holds = true))
        assertFalse(guard.shouldAbort(33, holds = false))
        assertFalse(guard.shouldAbort(66, holds = true))
        assertFalse(guard.shouldAbort(600, holds = true))
    }

    @Test
    fun `checks failing for half a second stop it`() {
        val guard = CountdownGuard()
        assertFalse(guard.shouldAbort(0, holds = false))
        assertFalse(guard.shouldAbort(400, holds = false))
        assertTrue(guard.shouldAbort(500, holds = false))
    }

    @Test
    fun `a reset forgets an earlier failure`() {
        val guard = CountdownGuard()
        guard.shouldAbort(0, holds = false)
        guard.reset()
        assertFalse(guard.shouldAbort(600, holds = false))
    }

    @Test
    fun `a check failing when the countdown ends is known, even inside the grace`() {
        val guard = CountdownGuard()
        assertFalse(guard.shouldAbort(2_900, holds = false))
        assertTrue(guard.isFailing, "the counter must not start on a failing check")
        guard.shouldAbort(2_933, holds = true)
        assertFalse(guard.isFailing)
    }
}
