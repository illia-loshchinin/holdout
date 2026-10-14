package holdout.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The milestone flame pulses once when it first shows, and not at all when the phone's animations are off (AC-26, FR-049a). */
@RunWith(RobolectricTestRunner::class)
class MilestoneFlamePulseTest {
    @get:Rule
    val compose = createComposeRule()

    /** The scales the flame's pulse passes through while [animatorsEnabled]. */
    private fun scalesSeen(animatorsEnabled: Boolean): List<Float> {
        val seen = mutableListOf<Float>()
        val off = AnimationsOff()
        if (!animatorsEnabled) off.setScale(0f)
        try {
            val pulse = Animatable(1f)
            compose.setContent {
                HoldoutTheme { MilestoneFlame(chip = false, pulse = pulse) }
                LaunchedEffect(Unit) { snapshotFlow { pulse.value }.collect { seen += it } }
            }
            compose.waitForIdle()
        } finally {
            off.setScale(1f)
        }
        return seen
    }

    @Test
    fun `with animations on the flame pulses up and settles back`() {
        val seen = scalesSeen(animatorsEnabled = true)

        assertTrue(seen.max() > 1f, "the pulse grows past 1: $seen")
        assertEquals(1f, seen.last(), "and settles back to 1")
    }

    @Test
    fun `with animations off the flame renders without a pulse`() {
        val seen = scalesSeen(animatorsEnabled = false)

        assertEquals(listOf(1f), seen.distinct(), "the scale never leaves 1")
    }

    @Test
    fun `the flame of the day-complete row is small, with no 72 dp ring around it`() {
        compose.setContent { HoldoutTheme { MilestoneFlame(chip = false) } }

        val bounds = compose.onNodeWithTag("milestone-flame").getBoundsInRoot()
        assertTrue(bounds.width <= 28.dp && bounds.height <= 28.dp, "a small flame beside the headline: $bounds")
    }

    @Test
    fun `the Today chip's flame keeps its size`() {
        compose.setContent { HoldoutTheme { MilestoneFlame(chip = true) } }

        val bounds = compose.onNodeWithTag("milestone-flame").getBoundsInRoot()
        assertEquals(24.dp, bounds.width)
    }
}
