package holdout.app.shell

import holdout.app.testutil.ChainFixtures.DAY
import holdout.app.testutil.ChainFixtures.START
import holdout.app.testutil.ChainFixtures.holdout
import holdout.app.testutil.ChainFixtures.member
import holdout.app.testutil.ChainFixtures.view
import holdout.core.MembershipSnapshot
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Which Holdout Today is about (FR-069a, rulings G1, D-D9): the Holdout's day
 * (T2) only while the member is standing in a running one; and when the last
 * day is on chain (FR-069a). Standing, a fourth tab until 13 October, is gone
 * (FR-069b): a Holdout's screen is pushed over whatever opened it. Pure, so no
 * screen is needed.
 */
class TabRootsTest {
    private val running = holdout(1, START - 2 * DAY, member(2))
    private val lobby = holdout(2, START + 3 * DAY, member(0))
    private val notMine = holdout(3, START - DAY, null)

    @Test
    fun `nothing is chosen before the chain has answered`() {
        assertNull(TabRoots.active(null, START))
        assertNull(TabRoots.active(view(running), null))
        assertEquals(emptyList(), TabRoots.lastDayOnChain(null, START))
        assertEquals(emptyList(), TabRoots.lastDayOnChain(view(running), null))
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
    fun `the last day is on chain when the member's final day is sent - and not before`() {
        // The 7-day Holdout started two days before START, so its last day (index 6) is open from START + 4 days.
        val lastDay = START + 4 * DAY
        assertEquals(emptyList(), TabRoots.lastDayOnChain(view(running, lobby, notMine), START), "day 3 of 7 is open, not done")
        val sent = holdout(1, START - 2 * DAY, member(7))
        assertEquals(listOf(sent.address.base58()), TabRoots.lastDayOnChain(view(sent, lobby, notMine), lastDay), "7 of 7 on chain: the last one is sent")
        val toSend = holdout(2, START - 2 * DAY, member(6))
        assertEquals(emptyList(), TabRoots.lastDayOnChain(view(toSend), lastDay), "6 of 7 on chain: the last day is still to send")
        val dayBefore = holdout(3, START - 2 * DAY, member(6))
        assertEquals(emptyList(), TabRoots.lastDayOnChain(view(dayBefore), START + 3 * DAY), "day 6 of 7 sent: not the last")
        val notIn = holdout(4, START - 2 * DAY, null)
        assertEquals(emptyList(), TabRoots.lastDayOnChain(view(notIn), lastDay), "watching is not being in it")
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
        assertEquals(emptyList(), TabRoots.lastDayOnChain(view(lobby, notMine), START))
        assertNull(TabRoots.active(view(lobby, notMine), START))
        assertNull(TabRoots.active(view(), START))
    }
}
