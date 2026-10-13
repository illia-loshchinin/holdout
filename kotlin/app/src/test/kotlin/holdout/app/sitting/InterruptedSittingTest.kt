package holdout.app.sitting

import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ES-1, ES-2: the marker found set at the next open is the crash variant of the Stopped screen. */
class InterruptedSittingTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val scope = "cohort-1"
    private val day = 11L

    private fun consume(store: Store, target: Int = 25, secondsLeft: Long? = 18_720) = InterruptedSitting.consume(
        store,
        wallet,
        scope,
        dayIndex = day,
        target = target,
        header = { SittingCopy.countingLabel(it.toInt() + 1, target) },
        secondsLeft = { secondsLeft },
    )

    @Test
    fun `no marker, nothing to say`() {
        val store = InMemoryStore().also { it.setBankedReps(wallet, scope, day, 10) }
        assertNull(consume(store))
    }

    @Test
    fun `a marker found with a block banked says what was kept`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 10)
        store.setSittingStartedAt(wallet, scope, 1L)

        val stopped = assertNotNull(consume(store))

        assertTrue(stopped.crash)
        assertEquals(10, stopped.banked)
        assertEquals(0, stopped.notKept)
        assertEquals(25, stopped.reps)
        assertEquals(18_720L, stopped.secondsLeft)
        assertEquals("day 12 · 25 reps", stopped.header)
    }

    @Test
    fun `reading it clears the marker, so the message comes once`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 20)
        store.setSittingStartedAt(wallet, scope, 1L)

        assertNotNull(consume(store))

        assertNull(store.sittingStartedAt(wallet, scope))
        assertNull(consume(store))
    }

    @Test
    fun `a crash with nothing banked has nothing to say, and the marker is still cleared`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, scope, 1L)

        assertNull(consume(store))
        assertNull(store.sittingStartedAt(wallet, scope))
    }

    @Test
    fun `a day that was completed before the crash has nothing to say`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 25)
        store.markDayComplete(wallet, scope, day)
        store.setSittingStartedAt(wallet, scope, 1L)

        assertNull(consume(store))
        assertNull(store.sittingStartedAt(wallet, scope))
    }

    @Test
    fun `reading the marker changes no banked figure`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 10)
        store.setSittingStartedAt(wallet, scope, 1L)

        consume(store)

        assertEquals(10, store.bankedReps(wallet, scope, day))
    }

    @Test
    fun `another scope's marker is left alone`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 10)
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)

        assertNull(consume(store))
        assertEquals(1L, store.sittingStartedAt(wallet, Store.CHALLENGE))
    }

    @Test
    fun `a sitting from an earlier day still says what that day banked`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 10)
        store.setSittingStartedAt(wallet, scope, 1L)
        store.setSittingDay(wallet, scope, day)

        val stopped = assertNotNull(InterruptedSitting.consume(store, wallet, scope, dayIndex = day + 1, target = 25, header = { "day ${it + 1}" }, secondsLeft = { null }))

        assertEquals(10, stopped.banked)
        assertEquals("day 12", stopped.header)
        assertNull(store.sittingStartedAt(wallet, scope))
        assertNull(store.sittingDay(wallet, scope), "the day goes with the marker")
    }

    @Test
    fun `a marker without a day, from an older build, reads today's`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day + 1, 7)
        store.setSittingStartedAt(wallet, scope, 1L)

        val stopped = assertNotNull(InterruptedSitting.consume(store, wallet, scope, dayIndex = day + 1, target = 25, header = { "day ${it + 1}" }, secondsLeft = { null }))

        assertEquals(7, stopped.banked)
        assertEquals("day 13", stopped.header)
    }

    @Test
    fun `an unknown stored day falls back to today's too`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, scope, day, 7)
        store.setSittingStartedAt(wallet, scope, 1L)
        store.setSittingDay(wallet, scope, Store.UNKNOWN_DAY)

        assertEquals(7, assertNotNull(consume(store)).banked)
    }

    @Test
    fun `a sitting started before the day was known, read before its reps were moved, reads them where they are`() {
        val store = InMemoryStore()
        store.setSittingDay(wallet, scope, Store.UNKNOWN_DAY)
        store.setSittingStartedAt(wallet, scope, 1L)
        store.setBankedReps(wallet, scope, Store.UNKNOWN_DAY, 10)

        assertEquals(10, consume(store)?.banked)
    }
}
