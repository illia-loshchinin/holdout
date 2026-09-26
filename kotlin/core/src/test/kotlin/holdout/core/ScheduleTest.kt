package holdout.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cohort schedule, mirrored from `programs/holdout/src/math/schedule.rs`
 * and data-model §1.3. The app shows these figures; the program enforces the
 * same ones, so the two must never disagree about which day it is or when a
 * member is out.
 */
class ScheduleTest {

    /** Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start. */
    private val first = Schedule.FIRST_START
    private val day = Schedule.DAY_LENGTH
    private val cohort = CohortTimes(start = first, durationDays = 7, dayLength = day, joinLock = Schedule.JOIN_LOCK)

    @Test
    fun `the first start is a Monday at 05 00 UTC`() {
        assertEquals(1_790_571_600L, first)
        // 1970-01-01 was a Thursday; Monday is 4 days on.
        assertEquals(4L, Math.floorMod(first / day, 7L))
        assertEquals(Schedule.BOUNDARY_OFFSET, Math.floorMod(first, day))
    }

    @Test
    fun `cohorts start a week apart and close joining an hour before`() {
        assertEquals(first + 2 * 604_800, Schedule.cohortStart(first, 604_800, 2))
        assertEquals(first - 3_600, cohort.joinCutoff)
    }

    @Test
    fun `the deadline of day k is the last second of that day`() {
        assertEquals(first + day - 1, cohort.deadlineOf(0))
        assertEquals(first + 7 * day - 1, cohort.deadlineOf(6))
    }

    @Test
    fun `status moves Open, Locked, Running, then Ended at the exact seconds`() {
        assertEquals(CohortStatus.OPEN, cohort.status(first - 3_601))
        assertEquals(CohortStatus.LOCKED, cohort.status(first - 3_600))
        assertEquals(CohortStatus.LOCKED, cohort.status(first - 1))
        assertEquals(CohortStatus.RUNNING, cohort.status(first))
        assertEquals(CohortStatus.RUNNING, cohort.status(cohort.end - 1))
        assertEquals(CohortStatus.ENDED, cohort.status(cohort.end))
    }

    @Test
    fun `the day index turns over exactly on the boundary and clamps outside the window`() {
        assertEquals(0, cohort.daysElapsed(first - 10))
        assertEquals(0, cohort.daysElapsed(first))
        assertEquals(0, cohort.daysElapsed(first + day - 1))
        assertEquals(1, cohort.daysElapsed(first + day))
        assertEquals(7, cohort.daysElapsed(first + 100 * day))
    }

    @Test
    fun `the day in progress may be completed at any second of it`() {
        assertEquals(0, cohort.maxDaysCompleted(first - 1))
        assertEquals(1, cohort.maxDaysCompleted(first))
        assertEquals(2, cohort.maxDaysCompleted(first + day))
        assertEquals(7, cohort.maxDaysCompleted(first + 100 * day))
    }

    @Test
    fun `a member is out the second after the deadline of the day they have not done`() {
        assertFalse(cohort.inBreach(daysCompleted = 0, now = first - 1), "not before the start")
        assertFalse(cohort.inBreach(daysCompleted = 0, now = cohort.deadlineOf(0)))
        assertTrue(cohort.inBreach(daysCompleted = 0, now = cohort.deadlineOf(0) + 1))
        assertFalse(cohort.inBreach(daysCompleted = 1, now = cohort.deadlineOf(0) + 1))
        assertFalse(cohort.inBreach(daysCompleted = 7, now = cohort.end + 10), "finished is never in breach")
    }

    @Test
    fun `the open cohort is the first whose joining has not closed`() {
        assertEquals(0, Schedule.openIndex(first, 604_800, 3_600, now = first - 10 * day))
        assertEquals(0, Schedule.openIndex(first, 604_800, 3_600, now = first - 3_601))
        assertEquals(1, Schedule.openIndex(first, 604_800, 3_600, now = first - 3_600))
        assertEquals(1, Schedule.openIndex(first, 604_800, 3_600, now = first + 3 * day))
    }
}
