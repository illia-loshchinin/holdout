package holdout.app.tour

import androidx.compose.runtime.mutableFloatStateOf
import holdout.app.ui.TOP_VIEW_STILL
import kotlin.test.Test
import kotlin.test.assertEquals

/** The "from above" panel of tour card 3 (board 2c) runs its 1.5 s loop in step with the figure's 12 s one. */
class FromAboveTest {
    private fun at(millis: Long) = FigureClock(still = false, millis = mutableFloatStateOf(millis.toFloat()))

    @Test
    fun `it restarts every one and a half seconds, eight times in the figure's loop`() {
        assertEquals(0f, topViewProgress(at(0)))
        assertEquals(0.5f, topViewProgress(at(750)), 1e-6f)
        assertEquals(0f, topViewProgress(at(1_500)), 1e-6f)
        assertEquals(topViewProgress(at(300)), topViewProgress(at(300 + 6 * 1_500)), 1e-6f)
        assertEquals(0.56f, topViewProgress(at(9_840)), 1e-6f)
    }

    @Test
    fun `with animations off it stays at the bottom of the push-up`() {
        assertEquals(TOP_VIEW_STILL, topViewProgress(FigureClock(still = true, millis = null)))
    }
}
