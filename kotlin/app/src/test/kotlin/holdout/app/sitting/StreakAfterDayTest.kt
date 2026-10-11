package holdout.app.sitting

import holdout.app.storage.InMemoryStore
import holdout.app.streak.StreakStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Daily 25's finished screen shows "+1" only when the streak actually grew (FR-050, gap C §6 (d)). */
class StreakAfterDayTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"

    @Test
    fun `the next day in a row grows the streak by one`() {
        val store = InMemoryStore()
        val streaks = StreakStore(store)
        (100L..111L).forEach { streaks.recordCompletedDay(wallet, it) }

        val after = StreakAfter.record(streaks, wallet, 112L)

        assertEquals(13, after.days)
        assertTrue(after.grew)
    }

    @Test
    fun `a first day starts a streak of one and that is growth`() {
        val after = StreakAfter.record(StreakStore(InMemoryStore()), wallet, 100L)

        assertEquals(1, after.days)
        assertTrue(after.grew)
    }

    @Test
    fun `a day already counted today - a Holdout day done first - does not grow it again`() {
        val store = InMemoryStore()
        val streaks = StreakStore(store)
        (100L..111L).forEach { streaks.recordCompletedDay(wallet, it) }
        streaks.recordCompletedDay(wallet, 112L)

        val after = StreakAfter.record(streaks, wallet, 112L)

        assertEquals(13, after.days)
        assertFalse(after.grew)
    }

    @Test
    fun `a streak that broke starts again at one`() {
        val store = InMemoryStore()
        val streaks = StreakStore(store)
        (100L..111L).forEach { streaks.recordCompletedDay(wallet, it) }

        val after = StreakAfter.record(streaks, wallet, 120L)

        assertEquals(1, after.days)
    }
}
