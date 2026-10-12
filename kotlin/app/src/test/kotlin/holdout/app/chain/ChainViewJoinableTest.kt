package holdout.app.chain

import com.solana.publickey.SolanaPublicKey
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import holdout.core.ProfileSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the app offers to join is what the program would take (FR-006a, FR-006b): [ChainView.joinable] is
 * the open Holdouts minus every one [holdout.core.Journey.joinBlocker] would refuse now, and
 * [ChainView.holdsLobbyPlace] says the wallet waits in a lobby, where no join is offered at all.
 */
class ChainViewJoinableTest {
    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val me = SolanaPublicKey(ByteArray(32) { 0x11 })

    private fun holdout(n: Int, cohortStart: Long, membership: MembershipSnapshot? = null) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { n.toByte() }),
        cohort = CohortSnapshot(
            LeagueParams.LEAGUE_7D,
            n.toLong(),
            cohortStart,
            cohortStart - 3_600,
            7,
            25,
            day,
            0,
            listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr),
            memberCount = 12,
            form = LeagueParams.FORM_KNEES_ALLOWED,
        ),
        league = null,
        membership = membership,
    )

    private fun member(days: Int = 0) = MembershipSnapshot(me, 1, 1_250uL * skr, start - 10 * day, days, MembershipSnapshot.State.Joined)

    private fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(me, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending)

    private fun view(vararg h: Holdout, profile: ProfileSnapshot? = null) = ChainView(null, h.toList(), profile, 0uL, 0L)

    @Test
    fun `without a place anywhere, every Holdout open to join is joinable`() {
        val a = holdout(1, start + 2 * day)
        val b = holdout(2, start + 3 * day)
        val v = view(a, b, profile = profile())
        assertEquals(v.openToJoin(start), v.joinable(start))
        assertEquals(listOf(a, b), v.joinable(start))
    }

    @Test
    fun `a held lobby place leaves nothing joinable - the one place is taken`() {
        val mine = holdout(1, start + 2 * day, member())
        val other = holdout(2, start + 3 * day)
        val v = view(mine, other, profile = profile(pending = ProfileSnapshot.Pending(mine.address, start + 2 * day, start + 9 * day)))
        assertEquals(listOf(other), v.openToJoin(start))
        assertTrue(v.joinable(start).isEmpty())
    }

    @Test
    fun `while one runs, only a Holdout starting at or after its end is joinable`() {
        val running = holdout(1, start - 2 * day, member(2))
        val overlapping = holdout(2, start + 3 * day)
        val backToBack = holdout(3, start + 5 * day)
        val later = holdout(4, start + 6 * day)
        val v = view(running, overlapping, backToBack, later, profile = profile(running = ProfileSnapshot.Running(running.address, start + 5 * day)))
        assertEquals(listOf(overlapping, backToBack, later), v.openToJoin(start))
        assertEquals(listOf(backToBack, later), v.joinable(start))
    }

    @Test
    fun `no profile read yet leaves the open ones joinable - the program re-checks`() {
        val a = holdout(1, start + 2 * day)
        assertEquals(listOf(a), view(a).joinable(start))
    }

    @Test
    fun `a Holdout whose joining has closed is never joinable`() {
        val closed = holdout(1, start + 1_800)
        assertTrue(view(closed, profile = profile()).joinable(start).isEmpty())
    }

    @Test
    fun `the wallet holds a lobby place while it is in a Holdout not yet started, open or locked`() {
        val open = holdout(1, start + 2 * day, member())
        val locked = holdout(2, start + 1_800, member())
        assertTrue(view(open).holdsLobbyPlace(start))
        assertTrue(view(locked).holdsLobbyPlace(start))
    }

    @Test
    fun `a running Holdout, or none, is not a lobby place`() {
        val running = holdout(1, start - 2 * day, member(2))
        val notIn = holdout(2, start + 2 * day)
        assertFalse(view(running).holdsLobbyPlace(start))
        assertFalse(view(notIn).holdsLobbyPlace(start))
        assertFalse(view().holdsLobbyPlace(start))
    }
}
