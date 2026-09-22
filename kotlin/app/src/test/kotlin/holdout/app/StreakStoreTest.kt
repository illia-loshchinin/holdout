package holdout.app

import holdout.app.storage.InMemoryStore
import holdout.app.streak.StreakStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T052 — the daily streak (FR-049…FR-052, ES-9). */
class StreakStoreTest {

    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val store = InMemoryStore()
    private val streaks = StreakStore(store)

    @Test
    fun `a new wallet has no streak`() {
        val streak = streaks.read(wallet, dayIndex = 100)
        assertEquals(0, streak.current)
        assertFalse(streak.completedToday)
    }

    @Test
    fun `consecutive days build it`() {
        assertEquals(1, streaks.recordCompletedDay(wallet, 100).current)
        assertEquals(2, streaks.recordCompletedDay(wallet, 101).current)
        assertEquals(3, streaks.recordCompletedDay(wallet, 102).current)
    }

    @Test
    fun `a missed day breaks it`() {
        streaks.recordCompletedDay(wallet, 100)
        streaks.recordCompletedDay(wallet, 101)
        assertEquals(0, streaks.read(wallet, dayIndex = 103).current)
        assertEquals(1, streaks.recordCompletedDay(wallet, 103).current)
    }

    @Test
    fun `yesterday's streak still shows while today is open`() {
        streaks.recordCompletedDay(wallet, 100)
        val today = streaks.read(wallet, dayIndex = 101)
        assertEquals(1, today.current, "the streak is alive until the day it needs ends")
        assertFalse(today.completedToday)
    }

    @Test
    fun `a cohort day and a Daily 25 are the same day's training`() {
        // FR-050: nobody trains twice in one day to hold both.
        val first = streaks.recordCompletedDay(wallet, 100)
        val second = streaks.recordCompletedDay(wallet, 100)
        assertEquals(1, first.current)
        assertEquals(1, second.current, "the second completed day of the same date changes nothing")
    }

    @Test
    fun `the longest is remembered after the current one breaks`() {
        repeat(5) { streaks.recordCompletedDay(wallet, 100L + it) }
        assertEquals(5, streaks.read(wallet, 104).longest)

        val afterGap = streaks.recordCompletedDay(wallet, 120)
        assertEquals(1, afterGap.current)
        assertEquals(5, afterGap.longest)
    }

    @Test
    fun `reading a lapsed streak does not erase it from disk`() {
        streaks.recordCompletedDay(wallet, 100)
        assertEquals(0, streaks.read(wallet, 200).current)
        // Nothing was written, so the longest is intact and a late opener has
        // lost only what they actually missed.
        assertEquals(1, store.streak(wallet))
        assertEquals(1, streaks.read(wallet, 101).current)
    }

    @Test
    fun `completedToday is what stops a stale done state at the boundary`() {
        streaks.recordCompletedDay(wallet, 100)
        assertTrue(streaks.read(wallet, 100).completedToday)
        // 05:00 UTC passes. Today is not done, however recently it was (ES-9).
        assertFalse(streaks.read(wallet, 101).completedToday)
    }

    @Test
    fun `two wallets keep separate streaks`() {
        streaks.recordCompletedDay("walletA", 100)
        assertEquals(0, streaks.read("walletB", 100).current)
    }

    @Test
    fun `there is no way for a forfeit to touch it`() {
        // FR-052 is enforced by the API surface: StreakStore offers reading and
        // recording a completed day, and nothing else. If this test ever has to
        // change, something has been added that should not exist.
        val methods = StreakStore::class.java.declaredMethods
            .filter { it.modifiers and java.lang.reflect.Modifier.PUBLIC != 0 }
            .map { it.name }
            .toSet()
        assertEquals(setOf("read", "recordCompletedDay"), methods)
    }
}
