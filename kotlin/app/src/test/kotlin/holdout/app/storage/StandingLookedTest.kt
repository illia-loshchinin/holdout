package holdout.app.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `looked:{wallet}:{cohort}` (data-model §4, FR-069): how many were standing in
 * a Holdout when the member last had its detail on screen. The Standing tab's
 * dot compares the live count with it (`BadgesTest`); this pins the record.
 */
class StandingLookedTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val cohort = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

    @Test
    fun `a Holdout never looked at has no record`() {
        assertNull(InMemoryStore().standingLooked(wallet, cohort))
    }

    @Test
    fun `looking keeps the standing seen, and the next look replaces it`() {
        val store = InMemoryStore()
        store.setStandingLooked(wallet, cohort, 12)
        assertEquals(12, store.standingLooked(wallet, cohort))
        store.setStandingLooked(wallet, cohort, 9)
        assertEquals(9, store.standingLooked(wallet, cohort))
    }

    @Test
    fun `a record is per wallet and per Holdout`() {
        val store = InMemoryStore()
        store.setStandingLooked(wallet, cohort, 12)
        assertNull(store.standingLooked(wallet, "another-cohort"))
        assertNull(store.standingLooked("another-wallet", cohort))
    }

    @Test
    fun `the record changes no banked figure`() {
        val store = InMemoryStore()
        store.setStandingLooked(wallet, cohort, 3)
        assertEquals(0, store.bankedReps(wallet, Store.CHALLENGE, 1L))
        assertEquals(false, store.isDayComplete(wallet, Store.CHALLENGE, 1L))
    }
}
