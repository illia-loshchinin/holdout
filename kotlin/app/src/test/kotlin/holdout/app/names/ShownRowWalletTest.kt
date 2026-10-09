package holdout.app.names

import holdout.app.leaderboard.leaderboardRowWallet
import holdout.app.roster.rosterRowWallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which wallet each visible row asks a Seeker ID for (spec 003 FR-012): the
 * row keys the roster and the leaderboard give their `LazyColumn`s, turned
 * back into wallets. A header row asks for nothing.
 */
class ShownRowWalletTest {

    private val wallet = "CkJn1a53c1ZXe5XLZ38ZgsPa1WX28PUiMWHqtJayjfWE"

    /** What Compose gives an `item { }` with no key of its own: not a String. */
    private val headerKey = Any()

    @Test
    fun `a roster member row names its wallet, a header row nothing`() {
        assertEquals(wallet, rosterRowWallet(wallet))
        assertNull(rosterRowWallet(headerKey))
        assertNull(rosterRowWallet(3))
    }

    @Test
    fun `a leaderboard row names its wallet without the ranked or not-ranked prefix`() {
        assertEquals(wallet, leaderboardRowWallet("r$wallet"))
        assertEquals(wallet, leaderboardRowWallet("n$wallet"))
    }

    @Test
    fun `a leaderboard key that is no member row names nothing`() {
        assertNull(leaderboardRowWallet(headerKey))
        assertNull(leaderboardRowWallet("r"))
        assertNull(leaderboardRowWallet(""))
        assertNull(leaderboardRowWallet("x$wallet"))
    }
}
