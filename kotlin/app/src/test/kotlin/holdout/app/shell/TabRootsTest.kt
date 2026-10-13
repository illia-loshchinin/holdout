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
    fun `Standing lets the last one go once it is over for the member - claimed, or settled after falling out`() {
        val claimed = holdout(5, START - 20 * DAY, member(7, MembershipSnapshot.State.Claimed))
        assertNull(TabRoots.standing(view(claimed, lobby, notMine), START), "claimed: the empty state, the Holdout is on Past")
        val out = holdout(4, START - 12 * DAY, member(1, MembershipSnapshot.State.Forfeited))
        assertEquals(out.address, TabRoots.standing(view(out), START)?.address, "fell out, not settled yet: the crowd is still worth watching")
        val settledOut = out.copy(cohort = out.cohort.copy(settled = true))
        assertNull(TabRoots.standing(view(settledOut), START), "fell out and settled: nothing left to see")
        val toClaim = holdout(6, START - 20 * DAY, member(7, MembershipSnapshot.State.Finished))
        assertEquals(toClaim.address, TabRoots.standing(view(toClaim.copy(cohort = toClaim.cohort.copy(settled = true))), START)?.address, "a claim waiting keeps it")
    }

    @Test
    fun `a running Holdout's roster is read every 30 seconds, a lobby's or a finished one's only slowly`() {
        assertEquals(ROSTER_REFRESH_MILLIS, TabRoots.rosterEveryMillis(running, START))
        // The 7-day Holdout started two days before START: it has ended five days later.
        val ended = START + 6 * DAY
        assertEquals(CHAIN_REFRESH_MILLIS, TabRoots.rosterEveryMillis(running, ended))
        // A lobby's joins and leaves move its member count, which reads its roster at once (FR-071).
        assertEquals(CHAIN_REFRESH_MILLIS, TabRoots.rosterEveryMillis(holdout(9, START + DAY), START))
    }

    @Test
    fun `Holdouts reads the rosters of the Holdouts the member claimed, for the Past tab`() {
        val claimed = holdout(5, START - 20 * DAY, member(7, MembershipSnapshot.State.Claimed))
        // Running, lobby and watched Holdouts have no row on the Past tab: nothing to read for them.
        assertEquals(listOf(claimed.address), TabRoots.claimedOnHoldouts(view(running, lobby, notMine, claimed), START))
        assertEquals(emptyList(), TabRoots.claimedOnHoldouts(view(running, lobby, notMine), START))
        assertEquals(emptyList(), TabRoots.claimedOnHoldouts(null, START))
    }

    @Test
    fun `a lobby place alone is not a Holdout to stand in, and watching is not a place`() {
        assertNull(TabRoots.standing(view(lobby, notMine), START))
        assertNull(TabRoots.standing(view(), START))
    }
}
