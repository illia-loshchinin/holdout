package holdout.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import holdout.app.ui.theme.HoldoutColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The push-up seen from above (boards 16 and 2c), ported from the board's SVG: each animated value has four
 * keyframes at the key times 0, 0.12, 0.56 and 1 of a 1.5 s loop, eased by the board's key splines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopViewTest {
    private val ramp = Keyframes(0f, 10f, 20f, 30f)

    @Test
    fun `a value is at its keyframes at the key times`() {
        assertEquals(0f, ramp.at(0f))
        assertEquals(10f, ramp.at(0.12f), 1e-4f)
        assertEquals(20f, ramp.at(0.56f), 1e-4f)
        assertEquals(30f, ramp.at(1f))
    }

    @Test
    fun `the first stretch is linear and the other two ease in and out`() {
        assertEquals(5f, ramp.at(0.06f), 1e-3f)
        assertEquals(2.5f, ramp.at(0.03f), 1e-3f)
        // Ease-in-out (0.42 0 0.58 1) is symmetric: the midpoint of a stretch is the plain midpoint...
        assertEquals(15f, ramp.at(0.34f), 1e-2f)
        assertEquals(25f, ramp.at(0.78f), 1e-2f)
        // ...a quarter of the way it is still behind the straight line, three quarters of the way ahead of it.
        assertTrue(ramp.at(0.12f + 0.44f / 4) < 12.5f)
        assertTrue(ramp.at(0.12f + 0.44f * 3 / 4) > 17.5f)
        assertTrue(ramp.at(0.56f + 0.44f / 4) < 22.5f)
        assertTrue(ramp.at(0.56f + 0.44f * 3 / 4) > 27.5f)
    }

    @Test
    fun `a value outside the loop is held at its ends, and a fixed value never moves`() {
        assertEquals(0f, ramp.at(-0.5f))
        assertEquals(30f, ramp.at(1.5f))
        assertEquals(7f, kf(7f).at(0.3f))
    }

    @Test
    fun `a value moves monotonically between rising keyframes`() {
        val samples = (0..100).map { ramp.at(it / 100f) }
        assertEquals(samples.sorted(), samples)
    }

    @Test
    fun `the upper arm goes out and comes back as the board animates it`() {
        // The board's upper arm: shoulder (170 43), elbow (173 40 -> 184 25.5, out and forward), hand (174 36).
        val arm = keyPath("M170 43 L173 40 L174 36", "M170 43 L173 40 L174 36", "M160 45.2 L184 25.5 L174 36", "M170 43 L173 40 L174 36")
        val begin = floatArrayOf(170f, 43f, 173f, 40f, 174f, 36f)
        val bottom = floatArrayOf(160f, 45.2f, 184f, 25.5f, 174f, 36f)

        assertCloseTo(begin, arm.coordinatesAt(0f))
        assertCloseTo(begin, arm.coordinatesAt(0.12f))
        assertCloseTo(bottom, arm.coordinatesAt(0.56f))
        assertCloseTo(begin, arm.coordinatesAt(1f))
        assertCloseTo(floatArrayOf(165f, 44.1f, 178.5f, 32.75f, 174f, 36f), arm.coordinatesAt(0.34f))
    }

    @Test
    fun `a path reads its curves and closes, and its keyframes must share their commands`() {
        val body = keyPath(
            "M170 43 Q210 49 250 49 Z",
            "M170 43 Q210 49 250 49 Z",
            "M160 45.2 Q203.5 50.32 247 49.7 Z",
            "M170 43 Q210 49 250 49 Z",
        )
        assertEquals(6, body.coordinatesAt(0.3f).size)
        assertEquals(203.5f, body.coordinatesAt(0.56f)[2], 1e-3f)
        assertFailsWith<IllegalArgumentException> { keyPath("M0 0 L1 1", "M0 0 L1 1", "M0 0 Q1 1 2 2", "M0 0 L1 1") }
        assertFailsWith<IllegalArgumentException> { keyPath("M0 0 L1 1") }
    }

    @Test
    fun `the loop is one and a half seconds and a still frame is the bottom of the push-up`() {
        assertEquals(1_500, TOP_VIEW_LOOP_MILLIS)
        assertEquals(0.56f, TOP_VIEW_STILL)
    }

    @Test
    fun `the drawing moves, comes back to where it began, and takes the phone's colour`() {
        val top = draw(0f)
        val bottom = draw(TOP_VIEW_STILL)
        val back = draw(1f)

        // Floating point leaves the last keyframe a hair off the first (an alpha of 0.1 comes out a step apart): the same picture, give or take rounding.
        val worst = top.indices.maxOf { i -> (0..3).maxOf { c -> kotlin.math.abs(((top[i] ushr (8 * c)) and 0xFF) - ((back[i] ushr (8 * c)) and 0xFF)) } }
        assertTrue(worst <= 4, "the first and last frames differ by $worst")
        assertNotEquals(top.toList(), bottom.toList())
        assertNotEquals(draw(0f, HoldoutColors.Accent).toList(), draw(0f, HoldoutColors.Success).toList())
        assertTrue(top.any { it != 0 })
    }

    private fun assertCloseTo(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertEquals(expected[it], actual[it], 1e-2f, "coordinate $it") }
    }

    private fun draw(progress: Float, accent: Color = HoldoutColors.Accent): IntArray {
        val width = TOP_VIEW_WIDTH.toInt()
        val height = TOP_VIEW_HEIGHT.toInt()
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(TOP_VIEW_WIDTH, TOP_VIEW_HEIGHT)) {
            drawTopView(progress, accent)
        }
        return IntArray(width * height).also { bitmap.asAndroidBitmap().getPixels(it, 0, width, 0, 0, width, height) }
    }
}
