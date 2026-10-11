package holdout.app.shell

import holdout.app.testutil.ChainFixtures.DAY
import holdout.app.testutil.ChainFixtures.START
import holdout.app.testutil.ChainFixtures.holdout
import holdout.app.testutil.ChainFixtures.member
import holdout.app.testutil.ChainFixtures.view
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Which Holdout the Today and Standing roots are about (FR-069a, FR-069b,
 * rulings G1, D-D9): Today is the Holdout's day (T2) only while the member is
 * standing in a running one; Standing is that one, else the last they ran,
 * else its empty state. Pure, so no screen is needed.
 */
class TabRootsTest {
    private val running = holdout(1, START - 2 * DAY, member(2))
    private val lobby = holdout(2, START + 3 * DAY, member(0))
    private val notMine = holdout(3, START - DAY, null)

    @Test
    fun `nothing is chosen before the chain has answered`() {
        assertNull(TabRoots.active(null, START))
        assertNull(TabRoots.active(view(running), null))
        assertNull(TabRoots.standing(null, START))
        assertNull(TabRoots.standing(view(running), null))
    }

    @Test
    fun `Today is T2 only while the member is standing in a running Holdout`() {
        val (h, stage) = assertNotNull(TabRoots.active(view(running, lobby, notMine), START))
        assertEquals(running.address, h.address)
        assertEquals(2, stage.dayIndex)
        assertNull(TabRoots.active(view(lobby, notMine), START), "a lobby place is not a running Holdout")
        assertNull(TabRoots.active(view(notMine), START), "watching a Holdout is not being in it")
    }

    @Test
    fun `a member who fell out is no longer in T2`() {
        val out = holdout(4, START - 4 * DAY, member(1))
        assertNull(TabRoots.active(view(out), START))
    }

    @Test
    fun `Standing is the running Holdout's detail`() {
        assertEquals(running.address, TabRoots.standing(view(running, lobby, notMine), START)?.address)
    }

    @Test
    fun `Standing is the last one the member ran when none is running`() {
        val older = holdout(5, START - 20 * DAY, member(7, MembershipSnapshot.State.Finished))
        val newer = holdout(4, START - 12 * DAY, member(1), days = 21, form = LeagueParams.FORM_TOES, leagueId = LeagueParams.LEAGUE_21D)
        assertEquals(newer.address, TabRoots.standing(view(older, newer, lobby, notMine), START)?.address)
    }

    @Test
    fun `a lobby place alone is not a Holdout to stand in, and watching is not a place`() {
        assertNull(TabRoots.standing(view(lobby, notMine), START))
        assertNull(TabRoots.standing(view(), START))
    }
}
