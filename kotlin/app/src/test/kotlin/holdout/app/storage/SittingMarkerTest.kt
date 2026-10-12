package holdout.app.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `sitting:{wallet}:{scope}` (data-model §4, ES-1, ES-2): the sitting-in-progress
 * marker. Written when counting starts, removed on every clean exit; found set at
 * the next open it means the app died mid-sitting. It never touches a banked figure.
 */
class SittingMarkerTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"

    @Test
    fun `a wallet with no sitting has no marker`() {
        assertNull(InMemoryStore().sittingStartedAt(wallet, Store.CHALLENGE))
    }

    @Test
    fun `the marker keeps when the sitting started and clears on a clean exit`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, store.sittingStartedAt(wallet, Store.CHALLENGE))

        store.setSittingStartedAt(wallet, Store.CHALLENGE, null)
        assertNull(store.sittingStartedAt(wallet, Store.CHALLENGE))
    }

    @Test
    fun `the marker is per wallet and per scope`() {
        val store = InMemoryStore()
        store.setSittingStartedAt(wallet, Store.CHALLENGE, 5L)

        assertNull(store.sittingStartedAt(wallet, "cohort-1"))
        assertNull(store.sittingStartedAt("another-wallet", Store.CHALLENGE))
        assertEquals(5L, store.sittingStartedAt(wallet, Store.CHALLENGE))
    }

    @Test
    fun `the marker changes no banked figure and no completion`() {
        val store = InMemoryStore()
        store.setBankedReps(wallet, Store.CHALLENGE, 21_000L, 10)

        store.setSittingStartedAt(wallet, Store.CHALLENGE, 7L)
        store.setSittingStartedAt(wallet, Store.CHALLENGE, null)

        assertEquals(10, store.bankedReps(wallet, Store.CHALLENGE, 21_000L))
        assertEquals(false, store.isDayComplete(wallet, Store.CHALLENGE, 21_000L))
    }
}
