package holdout.app.sitting

import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The marker's lifecycle in the sitting (T209, ES-1, ES-2): set when counting
 * starts, cleared on every clean exit. The member app writes it; Holdout Lab
 * (which has a recorder and no wallet) does not.
 */
class SittingMarkerWiringTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"

    @Test
    fun `starting is stamped with the clock and stopping clears it`() {
        val store = InMemoryStore()
        val marker = SittingMarker(store, wallet, Store.CHALLENGE) { 1234L }

        marker.start(21_000L)
        assertEquals(1234L, store.sittingStartedAt(wallet, Store.CHALLENGE))
        assertEquals(21_000L, store.sittingDay(wallet, Store.CHALLENGE))

        marker.moveTo(21_001L)
        assertEquals(21_001L, store.sittingDay(wallet, Store.CHALLENGE))

        marker.clear()
        assertNull(store.sittingStartedAt(wallet, Store.CHALLENGE))
        assertNull(store.sittingDay(wallet, Store.CHALLENGE))
    }

    /** A store whose process dies at the first marker write after the day: nothing after it is written. */
    private class DiesAtMarker(private val inner: Store) : Store by inner {
        override fun setSittingStartedAt(wallet: String, scope: String, startedAtMillis: Long?): Unit = throw IllegalStateException("killed")
    }

    @Test
    fun `the day is written before the marker, so a marker is never found without its day`() {
        val store = InMemoryStore()
        runCatching { SittingMarker(DiesAtMarker(store), wallet, Store.CHALLENGE) { 1234L }.start(21_000L) }

        // Killed between the two writes: a day with no marker, which nothing reads.
        assertNull(store.sittingStartedAt(wallet, Store.CHALLENGE))
        assertEquals(21_000L, store.sittingDay(wallet, Store.CHALLENGE))
    }

    @Test
    fun `a sitting that ends, however it is left, clears the marker`() {
        val store = InMemoryStore()
        val vm = SittingViewModel(
            store = store,
            streaks = StreakStore(store),
            wallet = wallet,
            scope = Store.CHALLENGE,
            dayIndex = 21_000L,
            target = 25,
            chainSecondsNow = { null },
        )
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)
        vm.endSitting()
        assertNull(store.sittingStartedAt(wallet, Store.CHALLENGE))

        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)
        vm.release()
        assertNull(store.sittingStartedAt(wallet, Store.CHALLENGE))
    }

    @Test
    fun `ending the sitting changes no banked figure`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, Store.CHALLENGE, 21_000L, 10)
        val vm = SittingViewModel(
            store = store,
            streaks = StreakStore(store),
            wallet = wallet,
            scope = Store.CHALLENGE,
            dayIndex = 21_000L,
            target = 25,
            chainSecondsNow = { null },
        )
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1L)

        vm.endSitting()

        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, 21_000L))
        assertEquals(10, vm.uiState.value.banked)
    }

    @Test
    fun `the daily 25 and a Holdout day read different clocks`() {
        val store = InMemoryStore()
        val daily = SittingViewModel(store, StreakStore(store), wallet, Store.CHALLENGE, 21_000L, 25, chainSecondsNow = { 21_000L * 86_400 + 5 * 3_600 + 60 })
        val holdout = SittingViewModel(
            store, StreakStore(store), wallet, "cohort-1", 0L, 25,
            chainSecondsNow = { 1_000L },
            rollsOver = false,
            timeLeftAt = { now -> 5_000L - now },
        )

        assertEquals(false, daily.isHoldoutDay)
        assertEquals(true, holdout.isHoldoutDay)
        assertEquals(1, holdout.dayNumber)
        assertNull(daily.dayNumber)
        assertEquals(4_000L, holdout.timeLeftSeconds())
        assertEquals(true, daily.timeLeftSeconds() != null)
    }
}
