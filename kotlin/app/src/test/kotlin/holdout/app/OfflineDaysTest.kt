package holdout.app

import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.streak.OfflineDays
import holdout.app.streak.StreakStore
import holdout.app.streak.ToesMeter
import holdout.core.HoldoutDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Daily 25 done before the chain has ever answered (FR-024b: "a Daily 25
 * day needs no connection at all") is banked under [Store.UNKNOWN_DAY], then
 * moved onto the day it was really done once chain time is known.
 */
class OfflineDaysTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val store = InMemoryStore()
    private val streaks = StreakStore(store)
    private val days = OfflineDays(store, streaks, target = 25)

    private val day = 20_000L

    /** Chain time [secondsIntoDay] after the 05:00 UTC boundary that starts [day]. */
    private fun chainAt(day: Long, secondsIntoDay: Long) = HoldoutDay.endOf(day - 1) + secondsIntoDay

    @Test
    fun `reps banked before the chain answered move to the day they were done`() {
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 1_000_000)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)

        // The chain answers two hours later, still the same day.
        val moved = days.settle(wallet, boot = 7, elapsedMillis = 1_000_000 + 7_200_000, chainNow = chainAt(day, 10_000))

        assertEquals(day, moved)
        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, day))
        assertEquals(0, store.bankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY))
        assertNull(days.settle(wallet, boot = 7, elapsedMillis = 9_000_000, chainNow = chainAt(day, 12_000)), "nothing left to move")
    }

    @Test
    fun `a sitting done before the turnover goes to the day before`() {
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 20)

        // Answered an hour into the next day, three hours after the sitting.
        assertEquals(day - 1, days.settle(wallet, boot = 7, elapsedMillis = 3 * 3_600_000L, chainNow = chainAt(day, 3_600)))
        assertEquals(20, store.bankedReps(wallet, Store.CHALLENGE, day - 1))
    }

    @Test
    fun `a completed offline day completes the real day and extends the streak`() {
        streaks.recordCompletedDay(wallet, day - 1)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 25)
        store.markDayComplete(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY)

        days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600))

        assertTrue(store.isDayComplete(wallet, Store.CHALLENGE, day))
        assertFalse(store.isDayComplete(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY))
        assertEquals(2, streaks.read(wallet, day).current)
    }

    @Test
    fun `offline reps add to what the day already had, up to the target`() {
        store.setBankedReps(wallet, Store.CHALLENGE, day, 20)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)

        days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600))

        assertEquals(25, store.bankedReps(wallet, Store.CHALLENGE, day))
        assertTrue(store.isDayComplete(wallet, Store.CHALLENGE, day))
        assertEquals(1, streaks.read(wallet, day).current)
    }

    @Test
    fun `the toes meter moves with the reps, so the day it lands on is a Hard day`() {
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 25)
        store.markDayComplete(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY)
        store.setToesDay(wallet, Store.UNKNOWN_DAY, 25, 25)

        days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600))

        assertEquals(25 to 25, store.toesDay(wallet, day))
        assertEquals(0 to 0, store.toesDay(wallet, Store.UNKNOWN_DAY))
        assertTrue(ToesMeter(store, target = 25).read(wallet, day).hardDay(25))
    }

    @Test
    fun `offline toes add to what the day already measured, up to the target`() {
        store.setBankedReps(wallet, Store.CHALLENGE, day, 20)
        store.setToesDay(wallet, day, 12, 20)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)
        store.setToesDay(wallet, Store.UNKNOWN_DAY, 10, 10)

        days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600))

        val (toes, measured) = store.toesDay(wallet, day)
        assertEquals(25, measured)
        assertTrue(toes <= measured, "never more from the toes than were measured")
    }

    @Test
    fun `only the offline reps that fit bring their stance with them`() {
        // 20 from the knees already banked; 10 from the toes done offline. The
        // Daily 25 keeps 5 of the offline reps, so 5 toes, not 10.
        store.setBankedReps(wallet, Store.CHALLENGE, day, 20)
        store.setToesDay(wallet, day, 0, 20)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)
        store.setToesDay(wallet, Store.UNKNOWN_DAY, 10, 10)

        days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600))

        assertEquals(25, store.bankedReps(wallet, Store.CHALLENGE, day))
        assertEquals(5 to 25, store.toesDay(wallet, day))
        assertEquals(5, store.bestToes(wallet))
    }

    @Test
    fun `when it is unknown which offline reps fit, the ones left out are counted against the toes`() {
        // 20 from the toes already; offline, 5 from the toes and 5 from the
        // knees. Which 5 the Daily 25 kept is not recorded, so the meter never
        // claims a toe rep it cannot be sure of — and so no Hard day either.
        store.setBankedReps(wallet, Store.CHALLENGE, day, 20)
        store.setToesDay(wallet, day, 20, 20)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)
        store.setToesDay(wallet, Store.UNKNOWN_DAY, 5, 10)

        days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600))

        assertEquals(20 to 25, store.toesDay(wallet, day))
        assertFalse(ToesMeter(store, target = 25).read(wallet, day).hardDay(25))
    }

    @Test
    fun `a later day already on the streak is never walked back`() {
        streaks.recordCompletedDay(wallet, day)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 25)
        store.markDayComplete(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY)

        // The offline day turns out to be the day before.
        days.settle(wallet, boot = 7, elapsedMillis = 86_400_000L, chainNow = chainAt(day, 600))

        assertEquals(day, store.lastStreakDay(wallet))
        assertTrue(store.isDayComplete(wallet, Store.CHALLENGE, day - 1))
    }

    @Test
    fun `after a reboot the offline sitting cannot be dated, and is left where it is`() {
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 5_000)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)

        assertNull(days.settle(wallet, boot = 8, elapsedMillis = 1_000, chainNow = chainAt(day, 600)))
        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY))
    }

    @Test
    fun `an unknown boot never dates an offline sitting`() {
        days.sittingStarted(wallet, boot = -1, elapsedMillis = 5_000)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)
        assertNull(days.settle(wallet, boot = -1, elapsedMillis = 60_000, chainNow = chainAt(day, 600)))
        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY))
    }

    /** A store whose first clearDay dies, as a process killed mid-settlement would. */
    private class DiesOnceClearing(private val inner: Store) : Store by inner {
        var dies = true

        override fun clearDay(wallet: String, scope: String, dayIndex: Long) {
            if (dies) {
                dies = false
                throw IllegalStateException("killed")
            }
            inner.clearDay(wallet, scope, dayIndex)
        }
    }

    @Test
    fun `a settlement killed half-way and run again moves the reps once`() {
        val dying = DiesOnceClearing(store)
        val days = OfflineDays(dying, StreakStore(dying), target = 25)
        store.setBankedReps(wallet, Store.CHALLENGE, day, 10)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)

        kotlin.runCatching { days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600)) }
        assertEquals(day, days.settle(wallet, boot = 7, elapsedMillis = 90_000, chainNow = chainAt(day, 630)))

        assertEquals(20, store.bankedReps(wallet, Store.CHALLENGE, day), "10 dated + 10 offline, not 10 + 10 + 10")
        assertFalse(store.isDayComplete(wallet, Store.CHALLENGE, day))
        assertEquals(0, store.bankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY))
    }

    @Test
    fun `a settlement killed half-way and run again moves the toes once`() {
        val dying = DiesOnceClearing(store)
        val days = OfflineDays(dying, StreakStore(dying), target = 25)
        store.setToesDay(wallet, day, 10, 10)
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 0)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)
        store.setToesDay(wallet, Store.UNKNOWN_DAY, 10, 10)

        kotlin.runCatching { days.settle(wallet, boot = 7, elapsedMillis = 60_000, chainNow = chainAt(day, 600)) }
        days.settle(wallet, boot = 7, elapsedMillis = 90_000, chainNow = chainAt(day, 630))

        assertEquals(20 to 20, store.toesDay(wallet, day), "10 dated + 10 offline, not 10 + 10 + 10")
        assertEquals(0 to 0, store.toesDay(wallet, Store.UNKNOWN_DAY))
        assertEquals(20, store.bestToes(wallet))
    }

    @Test
    fun `a Daily 25 killed before the day was known moves its crash marker to the day its reps went to`() {
        days.sittingStarted(wallet, boot = 7, elapsedMillis = 1_000_000)
        store.setSittingDay(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY)
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)

        days.settle(wallet, boot = 7, elapsedMillis = 1_000_000 + 7_200_000, chainNow = chainAt(day, 10_000))

        assertEquals(day, store.sittingDay(wallet, Store.CHALLENGE))
        // Read the next day, the crash page still finds the reps where they went.
        val ui = holdout.app.sitting.InterruptedSitting.consume(store, wallet, Store.CHALLENGE, day + 1, 25, { "" }, { null })
        assertEquals(10, ui?.banked)
    }
}
