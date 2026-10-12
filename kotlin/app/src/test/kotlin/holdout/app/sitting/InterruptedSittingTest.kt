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
        header = SittingCopy.countingLabel(12, target),
        secondsLeft = secondsLeft,
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
}
