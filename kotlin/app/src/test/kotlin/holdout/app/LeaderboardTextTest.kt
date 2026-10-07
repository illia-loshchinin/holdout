package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.leaderboard.LeaderboardText
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Standing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The leaderboard's words (T097, FR-060…FR-064): discipline only, never an amount. */
class LeaderboardTextTest {
    private fun row(cleared: Int, forfeited: Int, current: Int = 0, longest: Int = current, scripted: Boolean = false) = Leaderboard.Row(SolanaPublicKey(ByteArray(32) { 1 }), cleared, forfeited, current, longest, 0L, scripted)

    @Test
    fun `given a ranked member, when shown, then the rate, played, cleared and both streaks read plainly`() {
        val r = row(4, 1, current = 0, longest = 4)
        assertEquals("80% survived", LeaderboardText.rate(r))
        assertEquals("4 of 5 cleared · streak 0 · best 4", LeaderboardText.figures(r))
    }

    @Test
    fun `given a member below the minimum, when shown, then the line says how many more cohorts they need`() {
        assertEquals("2 of 2 cleared · streak 2 · best 2 · 1 more to be ranked", LeaderboardText.figures(row(2, 0, current = 2)))
    }

    @Test
    fun `given a scripted member, when shown, then the line is labelled`() {
        assertEquals("SCRIPTED · 3 of 3 cleared · streak 3 · best 3", LeaderboardText.figures(row(3, 0, current = 3, scripted = true)))
    }

    @Test
    fun `given each standing, when shown, then the member reads where they are`() {
        assertEquals("You're ranked #2", LeaderboardText.standing(Standing.Ranked(2)))
        assertEquals("You're 1 Holdout away from being ranked", LeaderboardText.standing(Standing.NotRanked(1)))
        assertEquals("You're 2 Holdouts away from being ranked", LeaderboardText.standing(Standing.NotRanked(2)))
        assertEquals("Finish 3 of these Holdouts to be ranked", LeaderboardText.standing(Standing.Absent()))
    }

    @Test
    fun `given every board, when its words are read, then none mentions money or the daily streak`() {
        val words = Leaderboard.Board.entries.flatMap { listOf(LeaderboardText.tab(it), LeaderboardText.about(it)) } +
            LeaderboardText.UPDATING + LeaderboardText.figures(row(3, 1, current = 1, longest = 3))
        val banned = listOf("SKR", "$", "prize", "pool", "stake", "payout", "Daily 25", "winnings", "luck", "odds")
        words.forEach { w -> banned.forEach { b -> assertFalse("\"$w\" mentions $b", w.contains(b, ignoreCase = true)) } }
    }
}
