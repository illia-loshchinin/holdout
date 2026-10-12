package holdout.app.sitting

import kotlin.test.Test
import kotlin.test.assertEquals

/** The bars under the count (rulings C-D7: proportional 10 / 10 / 5) and on the Stopped screen. */
class SittingBarsTest {
    @Test
    fun `17 done of 25 fills the first bar, seven tenths of the second and none of the third`() {
        assertEquals(listOf(1f, 0.7f, 0f), blockFills(target = 25, banked = 10, loose = 7))
    }

    @Test
    fun `nothing done leaves every bar empty`() {
        assertEquals(listOf(0f, 0f, 0f), blockFills(target = 25, banked = 0, loose = 0))
    }

    @Test
    fun `the last bar is the short block - five of five fills it`() {
        assertEquals(listOf(1f, 1f, 0.4f), blockFills(target = 25, banked = 20, loose = 2))
        assertEquals(listOf(1f, 1f, 1f), blockFills(target = 25, banked = 25, loose = 0))
    }

    @Test
    fun `a Holdout of another size gets bars to match - a day of 5 is one bar`() {
        assertEquals(listOf(0.6f), blockFills(target = 5, banked = 0, loose = 3))
        assertEquals(listOf(1f, 0.5f), blockFills(target = 20, banked = 10, loose = 5))
    }

    @Test
    fun `more reps than the target never overfills`() {
        assertEquals(listOf(1f, 1f, 1f), blockFills(target = 25, banked = 20, loose = 9))
    }

    @Test
    fun `stopped at 17 - ten banked, seven not kept`() {
        assertEquals(
            listOf(StoppedBar.Banked, StoppedBar.NotKept, StoppedBar.Empty),
            stoppedBars(target = 25, banked = 10, notKept = 7),
        )
    }

    @Test
    fun `stopped with only full blocks - nothing not kept`() {
        assertEquals(
            listOf(StoppedBar.Banked, StoppedBar.Banked, StoppedBar.Empty),
            stoppedBars(target = 25, banked = 20, notKept = 0),
        )
    }

    @Test
    fun `stopped with nothing banked - the not kept reps sit in the first bar`() {
        assertEquals(
            listOf(StoppedBar.NotKept, StoppedBar.Empty, StoppedBar.Empty),
            stoppedBars(target = 25, banked = 0, notKept = 6),
        )
    }

    @Test
    fun `after a crash only the banked blocks are drawn`() {
        assertEquals(
            listOf(StoppedBar.Banked, StoppedBar.Empty, StoppedBar.Empty),
            stoppedBars(target = 25, banked = 10, notKept = 0),
        )
    }
}
