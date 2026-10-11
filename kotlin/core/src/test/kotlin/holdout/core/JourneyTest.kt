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
    fun `given a finisher owed a comeback, when the cohort settles short, then the claim adds the pro-rata refund`() {
        val end = start + 7 * day
        // Owed 4 in all, the reserve funded 2: an entitlement of 2 refunds 1.
        val settled = c.copy(settled = true, sharePerTier = listOf(2uL, 7uL, 14uL, 28uL), comebackOwed = 4uL, comebackFunded = 2uL)
        val finisher = m(7, MembershipSnapshot.State.Finished).copy(comebackEntitled = 2uL)
        assertEquals(Stage.ReadyToClaim(33uL, 25uL, 7uL, comeback = 1uL), Journey.stage(settled, finisher, end + 60))
        assertEquals(Stage.Claimed(33uL), Journey.stage(settled, finisher.copy(state = MembershipSnapshot.State.Claimed), end + 120))
    }

    @Test
    fun `a join is blocked by closed joining, a held lobby place, or an overlap`() {
        val other = SolanaPublicKey(ByteArray(32) { 2 })
        fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(wallet, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending, -1)
        val lobby = start - day
        assertNull(Journey.joinBlocker(c, null, null, lobby))
        assertEquals(Journey.JoinBlocker.JoiningClosed, Journey.joinBlocker(c, null, null, start - 3_600))
        assertEquals(Journey.JoinBlocker.LobbyPlaceTaken, Journey.joinBlocker(c, profile(pending = ProfileSnapshot.Pending(other, start + 7 * day, start + 14 * day)), null, lobby))
        assertEquals(Journey.JoinBlocker.Overlaps, Journey.joinBlocker(c, profile(running = ProfileSnapshot.Running(other, start + 1)), start + 1, lobby))
        assertNull(Journey.joinBlocker(c, profile(running = ProfileSnapshot.Running(other, start)), start, lobby), "back to back is fine")
        assertEquals(other, Journey.runningCohortFor(profile(running = ProfileSnapshot.Running(other, start)), lobby))
    }

    @Test
    fun `a running cohort the member is in breach of does not block a join, one they are standing in still does (ES-17, ES-18)`() {
        val other = SolanaPublicKey(ByteArray(32) { 2 })
        fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(wallet, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending, -1)
        val lobby = start - day
        val overlapping = profile(running = ProfileSnapshot.Running(other, start + 1))
        // Unrecorded or recorded, the breach is read by the caller from chain time; the slot is freed by the join's own record.
        assertNull(Journey.joinBlocker(c, overlapping, start + 1, lobby, runningInBreach = true))
        assertNull(Journey.joinBlocker(c, profile(), null, lobby, runningInBreach = true))
        assertEquals(Journey.JoinBlocker.Overlaps, Journey.joinBlocker(c, overlapping, start + 1, lobby, runningInBreach = false))
        assertEquals(Journey.JoinBlocker.Overlaps, Journey.joinBlocker(c, overlapping, start + 1, lobby))
        // The rest is unchanged: a held lobby place and closed joining still block.
        val pending = profile(running = ProfileSnapshot.Running(other, start + 1), pending = ProfileSnapshot.Pending(other, start + 7 * day, start + 14 * day))
        assertEquals(Journey.JoinBlocker.LobbyPlaceTaken, Journey.joinBlocker(c, pending, start + 1, lobby, runningInBreach = true))
        assertEquals(Journey.JoinBlocker.JoiningClosed, Journey.joinBlocker(c, overlapping, start + 1, start - 3_600, runningInBreach = true))
    }

    @Test
    fun `after a forfeit the way back is the same league's next Holdout, never another league's (US-4)`() {
        fun open(leagueId: Int, weeks: Int) = c.copy(leagueId = leagueId, start = start + weeks * 7 * day)
        val sevenSoon = open(LeagueParams.LEAGUE_7D, 1)
        val twentyOneLater = open(LeagueParams.LEAGUE_21D, 3)
        val twentyOneSoon = open(LeagueParams.LEAGUE_21D, 2)
        val testLobby = open(LeagueParams.TEST_LEAGUE_MIN + 4, 0)
        val offers = listOf(sevenSoon, twentyOneLater, testLobby, twentyOneSoon)

        assertEquals(twentyOneSoon, Journey.wayBack(c.copy(leagueId = LeagueParams.LEAGUE_21D), offers) { it }, "a 21-day forfeit: the earliest 21-day Holdout, though a 7-day one starts sooner")
        assertEquals(sevenSoon, Journey.wayBack(c, offers) { it })
        // Every test league is one lobby: a test cohort's way back is the test lobby, whatever its id.
        assertEquals(testLobby, Journey.wayBack(c.copy(leagueId = LeagueParams.TEST_LEAGUE_MIN + 1), offers) { it })
        assertNull(Journey.wayBack(c.copy(leagueId = LeagueParams.LEAGUE_21D), listOf(sevenSoon, testLobby)) { it }, "none of its league open: no offer, not another league's")
        assertNull(Journey.wayBack(c, emptyList<CohortSnapshot>()) { it })
    }
}
