package holdout.app.testutil

import android.animation.ValueAnimator
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Turns the system's animations off for one test, as a phone with "Remove
 * animations" does. The app's loaders and rings then draw their still marks
 * (`ValueAnimator.areAnimatorsEnabled()` is false), so a screen that waits on
 * the wallet or the chain lets Compose go idle and its words can be asserted.
 */
class AnimationsOff : TestWatcher() {
    override fun starting(description: Description) = scale(0f)

    override fun finished(description: Description) = scale(1f)

    private fun scale(value: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, value)
    }
}
