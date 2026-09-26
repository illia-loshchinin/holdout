package holdout.repcounter

import kotlin.math.PI
import kotlin.math.abs

/**
 * One-euro filter (Casiez, Roussel, Vogel 2012), as research R4 specifies for
 * smoothing joint angles before any comparison.
 *
 * The problem it solves: the pose model's joint-angle error is around 2°, so a
 * raw angle crossing a threshold jitters across it several times in a row and a
 * naive counter scores one descent as three reps. A fixed low-pass filter would
 * fix that and add lag, which shows up as reps counted late — at 6 seconds a
 * cycle, lag is what turns a valid rep into a rejected one.
 *
 * One-euro adapts instead: heavy smoothing while the angle is nearly still,
 * light smoothing while it is moving fast. Jitter at the top of a push-up is
 * removed; the fast descent is passed through almost untouched.
 */
class OneEuroFilter(
    /** Cutoff at zero speed, Hz. Lower is smoother and laggier. */
    private val minCutoff: Float = 1.0f,
    /** How much the cutoff opens up with speed. Higher tracks fast motion better. */
    private val beta: Float = 0.007f,
    /** Cutoff for the derivative estimate itself, Hz. */
    private val derivativeCutoff: Float = 1.0f,
) {
    private var lastValue: Float? = null
    private var lastDerivative = 0f
    private var lastTimestampMillis: Long? = null

    fun reset() {
        lastValue = null
        lastDerivative = 0f
        lastTimestampMillis = null
    }

    fun filter(value: Float, timestampMillis: Long): Float {
        val previous = lastValue
        val previousTime = lastTimestampMillis

        if (previous == null || previousTime == null) {
            lastValue = value
            lastTimestampMillis = timestampMillis
            return value
        }

        // A repeated or out-of-order timestamp carries no new information about
        // rate of change; treat the sample as instantaneous rather than dividing
        // by zero.
        val deltaSeconds = (timestampMillis - previousTime) / 1000f
        if (deltaSeconds <= 0f) {
            lastValue = value
            return value
        }
        val rate = 1f / deltaSeconds

        val rawDerivative = (value - previous) * rate
        val derivative = lowPass(rawDerivative, lastDerivative, alpha(derivativeCutoff, rate))
        lastDerivative = derivative

        val cutoff = minCutoff + beta * abs(derivative)
        val filtered = lowPass(value, previous, alpha(cutoff, rate))

        lastValue = filtered
        lastTimestampMillis = timestampMillis
        return filtered
    }

    private fun alpha(cutoff: Float, rate: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        val te = 1f / rate
        return 1f / (1f + tau / te)
    }

    private fun lowPass(value: Float, previous: Float, alpha: Float): Float = alpha * value + (1f - alpha) * previous
}
