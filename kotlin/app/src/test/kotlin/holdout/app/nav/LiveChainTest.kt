package holdout.app.nav

import holdout.core.Leaderboard
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which screens keep the app's 30-second pulse reading (FR-071): only those showing live chain data. */
class LiveChainTest {
    private val home = NavState(connected = true)

    @Test
    fun `the connect screen, Today and Holdouts read`() {
        assertTrue(NavState(connected = false).showsLiveChain)
        assertTrue(home.showsLiveChain)
        assertTrue(home.copy(tab = Tab.Holdouts).showsLiveChain)
    }

    @Test
    fun `a Holdout's screen pushed over Today, and the leaderboards pushed over Me, read`() {
        assertTrue(home.copy(tab = Tab.Today, stacks = mapOf(Tab.Today to listOf(Screen.Detail("c")))).showsLiveChain)
        assertTrue(home.copy(tab = Tab.Me, stacks = mapOf(Tab.Me to listOf(Screen.Leaderboards(Leaderboard.Board.entries.first())))).showsLiveChain)
    }

    @Test
    fun `the screens pushed on them and the leaderboards read`() {
        listOf(Screen.Lobby("c"), Screen.Detail("c"), Screen.Roster("c"), Screen.Forfeit("c"), Screen.Claim("c"), Screen.Leaderboards(Leaderboard.Board.entries.first())).forEach {
            assertTrue(home.copy(tab = Tab.Holdouts, stacks = mapOf(Tab.Holdouts to listOf(it))).showsLiveChain, "$it")
        }
    }

    @Test
    fun `the signing flow reads, over any tab`() {
        assertTrue(home.copy(tab = Tab.Me, tx = TxFlow(TxKind.Claim("c"))).showsLiveChain)
    }

    @Test
    fun `Me, About, the tour and a sitting do not`() {
        assertFalse(home.copy(tab = Tab.Me).showsLiveChain)
        assertFalse(home.copy(tab = Tab.Me, stacks = mapOf(Tab.Me to listOf(Screen.About))).showsLiveChain)
        assertFalse(home.copy(tour = Tour(firstRun = true)).showsLiveChain)
        assertFalse(home.copy(sitting = Sitting.Daily25(day = 1)).showsLiveChain)
    }
}
