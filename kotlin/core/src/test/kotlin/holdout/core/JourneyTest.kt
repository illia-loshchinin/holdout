package holdout.core

import com.solana.publickey.SolanaPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every row of contracts/android-app §5 a Holdout can be in, from chain state
 * and chain time — and a breach read from the deadline before any crank has
 * recorded it (FR-038a).
 */
class JourneyTest {
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val c = CohortSnapshot(
        leagueId = 0, index = 0, start = start, joinCutoff = start - 3_600, durationDays = 7, repsPerDay = 25, dayLength = day, exercise = 0,
        chips = listOf(10uL, 25uL, 50uL, 100uL),
    )
    private val wallet = SolanaPublicKey(ByteArray(32) { 1 })

    private fun m(days: Int, state: MembershipSnapshot.State = MembershipSnapshot.State.Joined, tier: Int = 1) = MembershipSnapshot(wallet, tier, c.chips[tier], start - day, days, state)

    @Test
    fun `before the start - not joined, the lobby, then the locked lobby`() {
        assertEquals(Stage.NotJoined, Journey.stage(c, null, start - day))
        assertEquals(Stage.Lobby, Journey.stage(c, m(0), start - 3_601))
        assertEquals(Stage.LobbyLocked, Journey.stage(c, m(0), start - 3_600))
    }

    @Test
    fun `running - today's day, done or not, and a breach the moment a deadline passes`() {
        assertEquals(Stage.Active(0, todayDone = false), Journey.stage(c, m(0), start))
        assertEquals(Stage.Active(0, todayDone = true), Journey.stage(c, m(1), start + 60))
        assertEquals(Stage.Active(1, todayDone = false), Journey.stage(c, m(1), start + day))
        // Day 0's deadline is start + day − 1: one second later the member is out, crank or no crank.
        assertEquals(Stage.Forfeited(0, recorded = false), Journey.stage(c, m(0), start + day))
        assertEquals(Stage.Forfeited(2, recorded = true), Journey.stage(c, m(2, MembershipSnapshot.State.Forfeited), start + 3 * day))
    }

    @Test
    fun `after the end - finishers wait for settlement, then claim stake plus their tier's share`() {
        val end = start + 7 * day
        assertEquals(Stage.Settling, Journey.stage(c, m(7), end))
        assertEquals(Stage.Forfeited(6, recorded = false), Journey.stage(c, m(6), end), "short a day and not yet counted")
        assertEquals(Stage.Settling, Journey.stage(c, m(7, MembershipSnapshot.State.Finished), end))
        val settled = c.copy(settled = true, sharePerTier = listOf(2uL, 7uL, 14uL, 28uL))
        assertEquals(Stage.ReadyToClaim(32uL, 25uL, 7uL), Journey.stage(settled, m(7, MembershipSnapshot.State.Finished), end + 60))
        assertEquals(Stage.Claimed(32uL), Journey.stage(settled, m(7, MembershipSnapshot.State.Claimed), end + 120))
    }

    @Test
    fun `a join is blocked by closed joining, a held lobby place, or an overlap`() {
        val other = SolanaPublicKey(ByteArray(32) { 2 })
        fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(wallet, false, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending, -1)
        val lobby = start - day
        assertNull(Journey.joinBlocker(c, null, null, lobby))
        assertEquals(Journey.JoinBlocker.JoiningClosed, Journey.joinBlocker(c, null, null, start - 3_600))
        assertEquals(Journey.JoinBlocker.LobbyPlaceTaken, Journey.joinBlocker(c, profile(pending = ProfileSnapshot.Pending(other, start + 7 * day, start + 14 * day)), null, lobby))
        assertEquals(Journey.JoinBlocker.Overlaps, Journey.joinBlocker(c, profile(running = ProfileSnapshot.Running(other, start + 1)), start + 1, lobby))
        assertNull(Journey.joinBlocker(c, profile(running = ProfileSnapshot.Running(other, start)), start, lobby), "back to back is fine")
        assertEquals(other, Journey.runningCohortFor(profile(running = ProfileSnapshot.Running(other, start)), lobby))
    }
}
