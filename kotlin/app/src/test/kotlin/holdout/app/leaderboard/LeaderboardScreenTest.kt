package holdout.app.leaderboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Board
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** L1, the leaderboards (board 29): the picker, the board's copy, the five variants. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class LeaderboardScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    private fun row(n: Int, cleared: Int, forfeited: Int, scripted: Boolean = false) = Leaderboard.Row(Fx.key(n), cleared, forfeited, 1, cleared, 0, scripted)

    private val ranked = Leaderboard.rank(listOf(row(1, 5, 0), row(2, 4, 1), row(3, 3, 1, scripted = true), row(4, 1, 0)))
    private val boards = listOf(Board.EASY_7D, Board.HARD_21D, Board.TEST)

    private fun show(
        board: Board = Board.EASY_7D,
        view: Leaderboard.View? = ranked,
        me: com.solana.publickey.SolanaPublicKey? = Fx.key(2),
        shown: List<Board> = boards,
        onBoard: (Board) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        compose.setContent { HoldoutTheme { LeaderboardScreen(board, shown, view, me, onBoard, onBack, testDayLengthSeconds = 600) } }
    }

    @Test
    fun `the picker names the levels, Spark first, and marks the shown board`() {
        show()

        compose.onNodeWithText("Spark · 7-day").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Habit · 21-day").assertIsDisplayed()
        compose.onNodeWithText("Hackathon").assertIsDisplayed()
        compose.onNodeWithText("Leaderboards").assertIsDisplayed()
    }

    @Test
    fun `picking a chip swaps to that board`() {
        val picked = mutableListOf<Board>()
        show(onBoard = { picked += it })

        compose.onNodeWithText("Habit · 21-day").performClick()
        compose.onNodeWithText("Hackathon").performClick()

        assertEquals(listOf(Board.HARD_21D, Board.TEST), picked)
    }

    @Test
    fun `the test board is offered only when the host lists it`() {
        show(shown = listOf(Board.EASY_7D, Board.HARD_21D))
        compose.onNodeWithText("Hackathon").assertDoesNotExist()
    }

    @Test
    fun `back goes to where the board was opened`() {
        var backs = 0
        show(onBack = { backs++ })

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `ranked - the member is told their place and the sections are counted`() {
        show()

        compose.onNodeWithText("YOU'RE RANKED #2").assertIsDisplayed()
        compose.onNodeWithText("RANKED · 3").assertIsDisplayed()
        compose.onNodeWithText("NOT RANKED YET · 1").assertIsDisplayed()
        compose.onNodeWithText("All-time, from the chain. Ranked by share survived, after 3 played.").assertIsDisplayed()
    }

    @Test
    fun `rows show the survival rate, the figures and the scripted label, never an amount`() {
        show()

        // The first and the one with a single Holdout played both have a perfect record.
        compose.onAllNodesWithText("100% survived").assertCountEquals(2)
        compose.onNodeWithText("SCRIPTED · 3 of 4 cleared · streak 1 · best 3").assertExists()
        compose.onNodeWithText("SKR", substring = true).assertDoesNotExist()
    }

    @Test
    fun `not ranked yet - the member is told how far they are`() {
        show(me = Fx.key(4))
        compose.onNodeWithText("YOU'RE 2 HOLDOUTS AWAY FROM BEING RANKED").assertIsDisplayed()
    }

    @Test
    fun `a member with no record is asked to play`() {
        show(me = Fx.key(99))
        compose.onNodeWithText("PLAY 3 OF THESE HOLDOUTS TO BE RANKED").assertIsDisplayed()
    }

    @Test
    fun `empty board - says nobody has played three yet`() {
        show(view = Leaderboard.rank(emptyList()), me = null)
        compose.onNodeWithText("Nobody has played 3 of these yet.").assertIsDisplayed()
    }

    @Test
    fun `updating - the board waits while a Holdout settles`() {
        show(view = Leaderboard.View.Updating)

        compose.onNodeWithText("Updating while a Holdout settles.").assertIsDisplayed()
        compose.onNodeWithText("RANKED · 3").assertDoesNotExist()
    }

    @Test
    fun `reading - before the snapshot arrives`() {
        show(view = null)
        compose.onNodeWithText("Reading devnet…").assertIsDisplayed()
    }

    @Test
    fun `the test board wears the test badge and says Hackathon rounds, together`() {
        show(board = Board.TEST)

        compose.onNodeWithText("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        compose.onNodeWithText("Every Hackathon round on devnet, together. Ranked by share survived, after 3 played.").assertIsDisplayed()
    }

    @Test
    fun `the level boards carry no test badge`() {
        show(board = Board.HARD_21D)
        compose.onNodeWithText("DEVNET COHORT", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no board's words say cohort or league, or Easy and Hard`() {
        val words = Board.entries.flatMap { listOf(LeaderboardText.tab(it), LeaderboardText.about(it)) } +
            listOf(LeaderboardText.UPDATING, LeaderboardText.emptyBoard(), LeaderboardText.standing(Leaderboard.Standing.Absent()), LeaderboardText.standing(Leaderboard.Standing.NotRanked(2)))
        words.forEach { w -> listOf("cohort", "league", "Easy", "Hard ").forEach { assertFalse(w.contains(it, ignoreCase = it != "Hard "), "\"$w\" says $it") } }
    }
}
