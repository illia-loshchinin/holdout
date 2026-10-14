package holdout.app.me

import holdout.app.testing.Fx
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Leaderboards row's meta on Me (FR-069d): the member's best rank across the boards, or nothing. */
class MeRankTest {
    private fun row(n: Int, cleared: Int, forfeited: Int) = Leaderboard.Row(Fx.key(n), cleared, forfeited, 1, cleared, 0, false)

    private val me = Fx.key(2)

    @Test
    fun `ranked on one board - number and level with length`() {
        val spark = Leaderboard.rank(listOf(row(1, 5, 0), row(2, 4, 1), row(3, 3, 1)))
        assertEquals("#2 in Spark · 7-day", MeRank.meta(mapOf(Board.EASY_7D to spark), me))
    }

    @Test
    fun `ranked on several - the best place wins, Spark first on a tie`() {
        val spark = Leaderboard.rank(listOf(row(1, 5, 0), row(3, 4, 1), row(2, 3, 1)))
        val habit = Leaderboard.rank(listOf(row(2, 5, 0), row(1, 4, 1), row(3, 3, 1)))
        assertEquals("#1 in Habit · 21-day", MeRank.meta(mapOf(Board.EASY_7D to spark, Board.HARD_21D to habit), me))

        val other = Leaderboard.rank(listOf(row(2, 5, 0), row(1, 4, 1), row(3, 3, 1)))
        assertEquals("#1 in Spark · 7-day", MeRank.meta(mapOf(Board.EASY_7D to other, Board.HARD_21D to habit), me))
    }

    @Test
    fun `the Hackathon board reads as Hackathon`() {
        val test = Leaderboard.rank(listOf(row(2, 3, 0), row(1, 3, 1), row(3, 3, 2)))
        assertEquals("#1 in Hackathon", MeRank.meta(mapOf(Board.TEST to test), me))
    }

    @Test
    fun `not ranked yet, absent, updating or not read - no meta`() {
        // Two played: not ranked yet (RANKED_AFTER is 3).
        val notYet = Leaderboard.rank(listOf(row(1, 3, 0), row(2, 2, 0)))
        assertNull(MeRank.meta(mapOf(Board.EASY_7D to notYet), me))
        assertNull(MeRank.meta(mapOf(Board.EASY_7D to Leaderboard.rank(listOf(row(1, 3, 0)))), me))
        assertNull(MeRank.meta(mapOf(Board.EASY_7D to Leaderboard.View.Updating), me))
        assertNull(MeRank.meta(emptyMap(), me))
    }
}
