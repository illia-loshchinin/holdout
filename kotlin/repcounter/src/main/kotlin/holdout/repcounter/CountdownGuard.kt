package holdout.repcounter

/**
 * The three-second countdown keeps checking what started it (FR-019): the
 * gating pre-flight checks, and that the camera still sees the placement
 * counting was locked to. Checks failing for longer than [graceMillis] stop
 * the countdown — the phone was moved, or the member left the frame. One bad
 * frame does not: placement near a boundary can flicker for a frame.
 *
 * The held plank is not demanded again: people start moving at "1", and every
 * rep is judged on its own anyway.
 */
class CountdownGuard(private val graceMillis: Long = 500) {
    private var failingSince: Long? = null

    /** Whether the countdown should stop, given whether the checks [holds] at [nowMillis]. */
    fun shouldAbort(nowMillis: Long, holds: Boolean): Boolean {
        if (holds) {
            failingSince = null
            return false
        }
        val since = failingSince ?: nowMillis.also { failingSince = it }
        return nowMillis - since >= graceMillis
    }

    /** A check is failing right now, grace or not — the counter must not start on it. */
    val isFailing: Boolean get() = failingSince != null

    fun reset() {
        failingSince = null
    }
}
