package holdout.app.home

import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.testutil.ChainFixtures
import holdout.app.testutil.ChainFixtures.DAY
import holdout.app.testutil.ChainFixtures.START
import holdout.core.HoldoutDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gap 3 — the new-phone banner (ES-3a): "No banked reps on this device today." shows when this phone
 * first saw the wallet today, the chain has the wallet in a Holdout that started before today, and
 * nothing is banked here today.
 */
class NewPhoneTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val store = InMemoryStore()

    /** The Holdout's start day; "today" in these tests is a few days into it. */
    private val startDay = HoldoutDay.indexOf(START)
    private val today = startDay + 3

    private val inStarted = ChainFixtures.view(ChainFixtures.holdout(1, START, member = ChainFixtures.member(days = 3)))

    private fun shows(
        day: Long = today,
        banked: Int = 0,
        chain: holdout.app.chain.ChainView? = inStarted,
    ) = NewPhone.shows(store, wallet, day, chain, banked)

    // ---- the key -------------------------------------------------------------------------------------

    @Test
    fun `a wallet this phone has never seen has no first-seen day`() {
        assertNull(store.firstSeenDay(wallet))
    }

    @Test
    fun `the first connect keeps today as the first-seen day, and a second connect keeps the first`() {
        NewPhone.markSeen(store, wallet, today)
        NewPhone.markSeen(store, wallet, today + 5)

        assertEquals(today, store.firstSeenDay(wallet))
    }

    @Test
    fun `wallets are seen one by one`() {
        NewPhone.markSeen(store, wallet, today)

        assertNull(store.firstSeenDay("another"))
    }

    @Test
    fun `an unknown day keeps the wallet as pending, and the first known day becomes its day`() {
        NewPhone.markSeen(store, wallet, Store.UNKNOWN_DAY)
        assertEquals(Store.SEEN_PENDING, store.firstSeenDay(wallet))

        NewPhone.markSeen(store, wallet, Store.UNKNOWN_DAY)
        assertEquals(Store.SEEN_PENDING, store.firstSeenDay(wallet), "still pending while the day is unknown")

        NewPhone.markSeen(store, wallet, today)
        assertEquals(today, store.firstSeenDay(wallet))

        NewPhone.markSeen(store, wallet, today + 2)
        assertEquals(today, store.firstSeenDay(wallet), "resolved once")
    }

    @Test
    fun `an offline first run that saw the tour still gets the banner once the day is known`() {
        NewPhone.markSeen(store, wallet, Store.UNKNOWN_DAY)
        // The member's own first run goes on offline: the tour, and then the chain answers.
        store.setSeenTour(wallet)

        NewPhone.markSeen(store, wallet, today)

        assertEquals(today, store.firstSeenDay(wallet), "what this run left is not knowing the wallet from before")
        assertTrue(shows())
    }

    @Test
    fun `an offline first run that trained here on an earlier day is not new to the wallet once the day is known`() {
        NewPhone.markSeen(store, wallet, Store.UNKNOWN_DAY)
        // Days went by offline: a Daily 25 done here, filed under its own day once the chain answered.
        StreakStore(store).recordCompletedDay(wallet, today - 1)

        NewPhone.markSeen(store, wallet, today)

        assertEquals(Store.SEEN_EARLIER, store.firstSeenDay(wallet))
        assertFalse(shows())
    }

    @Test
    fun `an offline first run on a phone that knew the wallet before is still seen before`() {
        store.setSeenTour(wallet)
        NewPhone.markSeen(store, wallet, Store.UNKNOWN_DAY)

        assertEquals(Store.SEEN_EARLIER, store.firstSeenDay(wallet))
    }

    // ---- the upgrade rule ----------------------------------------------------------------------------

    @Test
    fun `a wallet whose tour was seen here counts as seen before`() {
        store.setSeenTour(wallet)

        NewPhone.markSeen(store, wallet, today)

        assertEquals(Store.SEEN_EARLIER, store.firstSeenDay(wallet))
        assertFalse(shows())
    }

    @Test
    fun `a wallet with a streak here counts as seen before`() {
        StreakStore(store).recordCompletedDay(wallet, today - 1)

        NewPhone.markSeen(store, wallet, today)

        assertEquals(Store.SEEN_EARLIER, store.firstSeenDay(wallet))
        assertFalse(shows())
    }

    @Test
    fun `a wallet with banked reps here counts as seen before`() {
        store.setBankedReps(wallet, Store.CHALLENGE, today - 1, 10)

        NewPhone.markSeen(store, wallet, today)

        assertEquals(Store.SEEN_EARLIER, store.firstSeenDay(wallet))
    }

    @Test
    fun `banked reps from before the chain was read count as seen before`() {
        store.setBankedReps(wallet, Store.CHALLENGE, Store.UNKNOWN_DAY, 10)

        NewPhone.markSeen(store, wallet, Store.UNKNOWN_DAY)

        assertEquals(Store.SEEN_EARLIER, store.firstSeenDay(wallet))
    }

    @Test
    fun `a wallet that already has its first-seen day keeps it through an update`() {
        NewPhone.markSeen(store, wallet, today)
        store.setSeenTour(wallet)

        NewPhone.markSeen(store, wallet, today + 1)

        assertEquals(today, store.firstSeenDay(wallet))
    }

    // ---- the signal ----------------------------------------------------------------------------------

    @Test
    fun `first seen today, in a Holdout that started before today, nothing banked - the banner shows`() {
        NewPhone.markSeen(store, wallet, today)

        assertTrue(shows())
    }

    @Test
    fun `not first seen today - no banner`() {
        NewPhone.markSeen(store, wallet, today - 1)

        assertFalse(shows())
    }

    @Test
    fun `never marked seen - no banner`() {
        assertFalse(shows())
    }

    @Test
    fun `a wallet in no Holdout - no banner`() {
        NewPhone.markSeen(store, wallet, today)

        assertFalse(shows(chain = ChainFixtures.view()))
        assertFalse(shows(chain = ChainFixtures.view(ChainFixtures.holdout(1, START))))
    }

    @Test
    fun `before the chain has answered - no banner`() {
        NewPhone.markSeen(store, wallet, today)

        assertFalse(shows(chain = null))
    }

    @Test
    fun `a Holdout that starts today is not one that started before today`() {
        NewPhone.markSeen(store, wallet, startDay)

        assertFalse(shows(day = startDay))
    }

    @Test
    fun `a Holdout in the lobby that starts later is not a use on another phone`() {
        NewPhone.markSeen(store, wallet, today)
        val lobby = ChainFixtures.view(ChainFixtures.holdout(1, START + 20 * DAY, member = ChainFixtures.member(days = 7)))

        assertFalse(shows(chain = lobby))
    }

    @Test
    fun `a Holdout long over still shows the wallet used Holdout elsewhere`() {
        NewPhone.markSeen(store, wallet, today + 30)
        val finished = ChainFixtures.view(ChainFixtures.holdout(1, START, member = ChainFixtures.member(days = 7)))

        assertTrue(shows(day = today + 30, chain = finished))
    }

    @Test
    fun `something banked here today - the banner is gone`() {
        NewPhone.markSeen(store, wallet, today)
        assertTrue(shows(banked = 0))

        assertFalse(shows(banked = 10))
    }

    @Test
    fun `the next day - the banner is gone`() {
        NewPhone.markSeen(store, wallet, today)

        assertFalse(shows(day = today + 1))
    }

    @Test
    fun `an unknown day shows nothing`() {
        NewPhone.markSeen(store, wallet, today)

        assertFalse(shows(day = Store.UNKNOWN_DAY))
    }

    @Test
    fun `the banner copy names the target`() {
        assertEquals("No banked reps on this device today. 25 to go.", NewPhone.line(25))
        assertEquals("No banked reps on this device today. 10 to go.", NewPhone.line(10))
    }
}
