package holdout.app.shell

import com.solana.publickey.SolanaPublicKey
import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.testutil.ChainFixtures
import holdout.app.testutil.ChainFixtures.START
import holdout.core.HoldoutDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the shell feeds the new-phone banner (ES-3a, Gap 3): the connect path keeps the wallet's
 * first-seen day under its base58 key, and Today shows the banner from the same facts for T1 and T2.
 * The rule itself is `NewPhoneTest`'s; this is the wiring around it, without composing the app.
 */
class NewPhoneWiringTest {
    private val store = InMemoryStore()
    private val address = SolanaPublicKey(ByteArray(32) { 7 })
    private val wallet = address.base58()
    private val today = HoldoutDay.indexOf(START) + 3
    private val inStarted = ChainFixtures.view(ChainFixtures.holdout(1, START, member = ChainFixtures.member(days = 3)))

    @Test
    fun `connecting a wallet keeps its first-seen day under its base58 address`() {
        NewPhoneWiring.onConnect(store, address, today)

        assertEquals(today, store.firstSeenDay(wallet))
    }

    @Test
    fun `no wallet connected keeps nothing`() {
        NewPhoneWiring.onConnect(store, null, today)

        assertNull(store.firstSeenDay(wallet))
    }

    @Test
    fun `the day changing under a connected wallet does not move its first-seen day`() {
        NewPhoneWiring.onConnect(store, address, today)
        NewPhoneWiring.onConnect(store, address, today + 1)

        assertEquals(today, store.firstSeenDay(wallet))
    }

    @Test
    fun `Today shows the banner the connect path armed, for T1 and for T2 alike`() {
        NewPhoneWiring.onConnect(store, address, today)

        assertTrue(NewPhoneWiring.shows(store, wallet, today, inStarted, banked = 0), "nothing banked: the banner")
        assertFalse(NewPhoneWiring.shows(store, wallet, today, inStarted, banked = 5), "a block banked here ends it")
    }

    @Test
    fun `Today shows no banner for a wallet the connect path never saw, or before the chain has answered`() {
        assertFalse(NewPhoneWiring.shows(store, wallet, today, inStarted, banked = 0))

        NewPhoneWiring.onConnect(store, address, today)
        assertFalse(NewPhoneWiring.shows(store, wallet, today, null, banked = 0))
        assertFalse(NewPhoneWiring.shows(store, wallet, Store.UNKNOWN_DAY, inStarted, banked = 0))
    }

    @Test
    fun `connecting offline keeps the wallet pending, and the first known day arms the banner`() {
        NewPhoneWiring.onConnect(store, address, Store.UNKNOWN_DAY)
        assertEquals(Store.SEEN_PENDING, store.firstSeenDay(wallet))
        assertFalse(NewPhoneWiring.shows(store, wallet, Store.UNKNOWN_DAY, inStarted, banked = 0))

        store.setSeenTour(wallet)
        NewPhoneWiring.onConnect(store, address, today)

        assertEquals(today, store.firstSeenDay(wallet))
        assertTrue(NewPhoneWiring.shows(store, wallet, today, inStarted, banked = 0))
    }
}
