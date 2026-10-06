package holdout.core

import com.solana.publickey.SolanaPublicKey
import holdout.core.Leaderboard.Standing
import holdout.core.Leaderboard.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The league leaderboard's ranking (T112, US-10, FR-060…FR-060c, AC-14) —
 * written once here and read by the app. The figures come straight from
 * each wallet's on-chain `Profile`; the contract suite (T096) proves those.
 */
class LeaderboardTest {

    private val day = 86_400L
    private val start = 1_791_176_400L // Monday 5 October 2026, 05:00 UTC

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    private fun progress(cleared: Int, forfeited: Int, current: Int = 0, longest: Int = current, lastChanged: Long = start) = ProfileSnapshot.LeagueProgress(cleared, forfeited, current, longest, lastChanged)

    private val none = progress(0, 0)

    private fun profile(n: Int, sevenDay: ProfileSnapshot.LeagueProgress = none, twentyOneDay: ProfileSnapshot.LeagueProgress = none, test: ProfileSnapshot.LeagueProgress = none) = ProfileSnapshot(key(n), qualified = false, leagues = listOf(sevenDay, twentyOneDay, test), running = null, pending = null, lastFaucetDay = -1)

    private fun cohort(leagueId: Int, counted: Long = 0, settled: Boolean = false, cohortStart: Long = start) = CohortSnapshot(
        leagueId = leagueId,
        index = 0,
        start = cohortStart,
        joinCutoff = cohortStart - 3_600,
        durationDays = 7,
        repsPerDay = 25,
        dayLength = day,
        exercise = 0,
        chips = listOf(10uL, 25uL, 50uL, 100uL),
        memberCount = 10,
        counted = counted,
        settled = settled,
    )

    private fun sevenDay(vararg profiles: ProfileSnapshot, scripted: Set<SolanaPublicKey> = emptySet()): View.Ready = assertIs<View.Ready>(Leaderboard.view(Leaderboard.Board.EASY_7D, profiles.toList(), emptyList(), start, scripted::contains))

    private fun View.Ready.rankedWallets() = ranked.map { it.wallet }

    // ---- AC-14 ----------------------------------------------------------

    @Test
    fun `given 4 of 4 and 9 of 10, when ranked, then the perfect record ranks first`() {
        val board = sevenDay(profile(1, progress(9, 1, current = 5, longest = 6)), profile(2, progress(4, 0, current = 4)))
        assertEquals(listOf(key(2), key(1)), board.rankedWallets())
    }

    @Test
    fun `given 3 of 3 and 4 of 4, when ranked, then more cohorts played breaks the tie`() {
        val board = sevenDay(profile(1, progress(3, 0, current = 3)), profile(2, progress(4, 0, current = 4)))
        assertEquals(listOf(key(2), key(1)), board.rankedWallets())
    }

    @Test
    fun `given 2 of 2, when ranked, then the member is not ranked yet and needs 1 more cohort`() {
        val board = sevenDay(profile(1, progress(2, 0, current = 2)), profile(2, progress(3, 0, current = 3)))
        assertEquals(listOf(key(2)), board.rankedWallets())
        assertEquals(listOf(key(1)), board.notRanked.map { it.wallet })
        assertEquals(1, board.notRanked.single().cohortsNeeded)
        assertEquals(Standing.NotRanked(cohortsNeeded = 1), board.standingOf(key(1)))
        assertEquals(Standing.Ranked(position = 1), board.standingOf(key(2)))
    }

    @Test
    fun `given a forfeit after a streak, when shown, then the current streak is 0, the best is kept and the rate falls`() {
        val row = sevenDay(profile(1, progress(4, 1, current = 0, longest = 4))).ranked.single()
        assertEquals(0, row.currentStreak)
        assertEquals(4, row.longestStreak)
        assertEquals(5, row.played)
        assertEquals(80, row.survivalPercent)
    }

    @Test
    fun `given a scripted wallet, when ranked, then it is ranked like anyone and labelled`() {
        val board = sevenDay(profile(1, progress(3, 0, current = 3)), profile(2, progress(3, 0, current = 3)), scripted = setOf(key(2)))
        assertEquals(listOf(false, true), board.ranked.map { it.scripted })
    }

    // ---- FR-060b tie-breaks ----------------------------------------------

    @Test
    fun `given equal rates by different fractions, when ranked, then rates compare exactly and not as rounded percentages`() {
        // 2/3 and 667/1000 both show as 66%; 667/1000 is the larger.
        val board = sevenDay(profile(1, progress(2, 1, longest = 2)), profile(2, progress(667, 333, longest = 2)))
        assertEquals(listOf(key(2), key(1)), board.rankedWallets())
        assertEquals(listOf(66, 66), board.ranked.map { it.survivalPercent })
    }

    @Test
    fun `given the same rate and cohorts played, when ranked, then the longer best streak goes first`() {
        val board = sevenDay(profile(1, progress(3, 1, current = 1, longest = 2)), profile(2, progress(3, 1, current = 0, longest = 3)))
        assertEquals(listOf(key(2), key(1)), board.rankedWallets())
    }

    @Test
    fun `given everything else equal, when ranked, then whoever reached the figures first goes first`() {
        val board = sevenDay(profile(1, progress(3, 0, current = 3, lastChanged = start + 2 * day)), profile(2, progress(3, 0, current = 3, lastChanged = start + day)))
        assertEquals(listOf(key(2), key(1)), board.rankedWallets())
    }

    @Test
    fun `given outcomes at the same instant, when ranked, then wallet address ascending decides, the same in any input order`() {
        val a = profile(7, progress(3, 0, current = 3))
        val b = profile(3, progress(3, 0, current = 3))
        val c = profile(5, progress(3, 0, current = 3))
        val expected = listOf(a, b, c).map { it.wallet }.sortedBy { it.base58() }
        assertEquals(expected, sevenDay(a, b, c).rankedWallets())
        assertEquals(expected, sevenDay(c, a, b).rankedWallets())
    }

    // ---- FR-060c not ranked yet -------------------------------------------

    @Test
    fun `given members below the minimum, when listed, then by cohorts played then survival rate, with what each still needs`() {
        val board = sevenDay(
            profile(1, progress(1, 0, current = 1)),
            profile(2, progress(1, 1)),
            profile(3, progress(0, 1)),
            profile(4, progress(2, 0, current = 2)),
        )
        assertEquals(listOf(key(4), key(2), key(1), key(3)), board.notRanked.map { it.wallet })
        assertEquals(listOf(1, 1, 2, 2), board.notRanked.map { it.cohortsNeeded })
        assertTrue(board.ranked.isEmpty())
    }

    @Test
    fun `given a wallet that never finished a cohort of the league, when listed, then it appears on neither list`() {
        val board = sevenDay(profile(1, sevenDay = none, twentyOneDay = progress(5, 0, current = 5)), profile(2, progress(3, 0, current = 3)))
        assertEquals(listOf(key(2)), board.rankedWallets())
        assertTrue(board.notRanked.isEmpty())
        assertEquals(Standing.Absent(cohortsNeeded = 3), board.standingOf(key(1)))
    }

    @Test
    fun `given a fraction, when shown, then the whole percentage is rounded down so nothing short of perfect reads 100`() {
        val row = sevenDay(profile(1, progress(199, 1, longest = 100))).ranked.single()
        assertEquals(99, row.survivalPercent)
    }

    // ---- boards ------------------------------------------------------------

    @Test
    fun `given one profile, when each board is built, then each reads its own slot`() {
        val p = profile(1, sevenDay = progress(3, 0, current = 3), twentyOneDay = progress(0, 3), test = progress(4, 0, current = 4))
        fun ready(board: Leaderboard.Board) = assertIs<View.Ready>(Leaderboard.view(board, listOf(p), emptyList(), start) { false }).ranked.single()
        assertEquals(3, ready(Leaderboard.Board.EASY_7D).cleared)
        assertEquals(3, ready(Leaderboard.Board.HARD_21D).forfeited)
        assertEquals(4, ready(Leaderboard.Board.TEST).cleared)
    }

    @Test
    fun `given league ids, when mapped to boards, then every test league shares the one test board`() {
        assertEquals(Leaderboard.Board.EASY_7D, Leaderboard.Board.of(LeagueParams.LEAGUE_7D))
        assertEquals(Leaderboard.Board.HARD_21D, Leaderboard.Board.of(LeagueParams.LEAGUE_21D))
        assertEquals(Leaderboard.Board.TEST, Leaderboard.Board.of(100))
        assertEquals(Leaderboard.Board.TEST, Leaderboard.Board.of(101))
    }

    // ---- FR-060a mid-count --------------------------------------------------

    @Test
    fun `given a cohort of the league part-way through counting, when the board is built, then it holds instead of ranking`() {
        val p = listOf(profile(1, progress(3, 0, current = 3)))
        val counting = cohort(LeagueParams.LEAGUE_7D, counted = 4)
        assertEquals(View.Updating, Leaderboard.view(Leaderboard.Board.EASY_7D, p, listOf(counting), start + 8 * day) { false })
    }

    @Test
    fun `given a cohort past its end but not yet counted, when the board is built, then it ranks, since nothing of it is counted`() {
        val p = listOf(profile(1, progress(3, 0, current = 3)))
        val ended = cohort(LeagueParams.LEAGUE_7D, counted = 0)
        assertIs<View.Ready>(Leaderboard.view(Leaderboard.Board.EASY_7D, p, listOf(ended), start + 8 * day) { false })
    }

    @Test
    fun `given a settled cohort or another league mid-count, when the board is built, then it ranks`() {
        val p = listOf(profile(1, progress(3, 0, current = 3)))
        val settled = cohort(LeagueParams.LEAGUE_7D, counted = 10, settled = true)
        val otherLeague = cohort(LeagueParams.LEAGUE_21D, counted = 4)
        assertIs<View.Ready>(Leaderboard.view(Leaderboard.Board.EASY_7D, p, listOf(settled, otherLeague), start + 8 * day) { false })
        assertEquals(View.Updating, Leaderboard.view(Leaderboard.Board.HARD_21D, p, listOf(settled, otherLeague), start + 22 * day) { false })
    }

    @Test
    fun `given a test cohort part-way through counting, when the test board is built, then it holds`() {
        val p = listOf(profile(1, test = progress(3, 0, current = 3)))
        val counting = cohort(101, counted = 1).copy(dayLength = 600, durationDays = 3)
        assertEquals(View.Updating, Leaderboard.view(Leaderboard.Board.TEST, p, listOf(counting), start + 3_600) { false })
    }
}
