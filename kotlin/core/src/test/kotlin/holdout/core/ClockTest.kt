package holdout.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T033 — chain time on the device, and the 05:00 UTC boundary (research R7). */
class ClockTest {

    private var elapsed = 0L
    private val clock = ChainClock { elapsed }

    @Test
    fun `with no reading it admits it does not know`() {
        assertNull(clock.nowSeconds())
        assertFalse(clock.hasReading)
    }

    @Test
    fun `it advances with monotonic time, not the wall clock`() {
        elapsed = 10_000
        clock.observe(chainSeconds = 1_800_000_000, slot = 500)
        assertEquals(1_800_000_000, clock.nowSeconds())

        elapsed = 10_000 + 45_000 // 45 real seconds later
        assertEquals(1_800_000_045, clock.nowSeconds())
        assertEquals(500, clock.slot)
    }

    @Test
    fun `a later reading replaces the earlier one`() {
        elapsed = 0
        clock.observe(1_800_000_000, 1)
        elapsed = 60_000
        clock.observe(1_800_000_100, 2) // the cluster was ahead of our estimate
        assertEquals(1_800_000_100, clock.nowSeconds())
        elapsed = 70_000
        assertEquals(1_800_000_110, clock.nowSeconds())
    }

    @Test
    fun `an elapsed source that jumps backwards never rewinds chain time`() {
        elapsed = 100_000
        clock.observe(1_800_000_000, 1)
        elapsed = 90_000
        assertEquals(1_800_000_000, clock.nowSeconds())
    }

    @Test
    fun `the day turns over at 05 00 UTC`() {
        // 2026-09-22T04:59:59Z and 05:00:00Z, either side of the boundary.
        val justBefore = 1_790_398_799L
        val atBoundary = 1_790_398_800L
        assertEquals(
            HoldoutDay.indexOf(justBefore) + 1,
            HoldoutDay.indexOf(atBoundary),
            "05:00 UTC must start a new day",
        )
    }

    @Test
    fun `a day's end is the next day's start`() {
        val day = HoldoutDay.indexOf(1_790_400_000L)
        val end = HoldoutDay.endOf(day)
        assertEquals(day + 1, HoldoutDay.indexOf(end))
        assertEquals(day, HoldoutDay.indexOf(end - 1))
    }

    @Test
    fun `time left in the day counts down, and resets at the boundary`() {
        val day = HoldoutDay.indexOf(1_790_400_000L)
        val end = HoldoutDay.endOf(day)

        assertEquals(3_600, HoldoutDay.secondsLeftIn(end - 3_600))
        assertEquals(1, HoldoutDay.secondsLeftIn(end - 1))

        // At the boundary the member is already in the next day, with a whole
        // one ahead of them — not at zero. This is what stops the home screen
        // showing a stale "done" state at 05:00 (ES-9).
        assertEquals(HoldoutDay.SECONDS_PER_DAY, HoldoutDay.secondsLeftIn(end))
        assertTrue(HoldoutDay.secondsLeftIn(end + 5) in 1..HoldoutDay.SECONDS_PER_DAY)
    }

    @Test
    fun `the day index matches the program's own formula`() {
        // `schedule.rs`: floor((now − 18,000) ÷ 86,400), including before the
        // epoch's first boundary, where truncating division would round the
        // wrong way.
        for (t in listOf(0L, 17_999L, 18_000L, 18_001L, 1_790_398_800L)) {
            assertEquals(Math.floorDiv(t - 18_000L, 86_400L), HoldoutDay.indexOf(t))
        }
    }
}
