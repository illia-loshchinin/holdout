package holdout.app.me

import com.solana.publickey.SolanaPublicKey
import holdout.app.leaderboard.LeaderboardText
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Board

/**
 * The Leaderboards row's meta on Me (FR-069d): "#4 in Spark · 7-day", the member's best place across the
 * boards read so far — the lowest number, Spark before Habit before Hackathon on a tie — or null when they
 * hold no rank (not ranked yet, not on a board, a board still updating, or nothing read).
 */
object MeRank {
    fun meta(views: Map<Board, Leaderboard.View>, me: SolanaPublicKey): String? = best(views, me)?.let { (board, place) -> "#$place in ${LeaderboardText.tab(board)}" }

    /** The board the best rank is on, to open Leaderboards there; null without a rank. */
    fun board(views: Map<Board, Leaderboard.View>, me: SolanaPublicKey): Board? = best(views, me)?.first

    private fun best(views: Map<Board, Leaderboard.View>, me: SolanaPublicKey): Pair<Board, Int>? = Board.entries
        .mapNotNull { board ->
            val ready = views[board] as? Leaderboard.View.Ready ?: return@mapNotNull null
            (ready.standingOf(me) as? Leaderboard.Standing.Ranked)?.let { board to it.position }
        }
        .minByOrNull { it.second }
}
