package holdout.app.standing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import holdout.app.holdouts.RunningHoldout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.Leaderboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Standing with nothing of the member's to show (FR-069b, board "nothing of yours yet"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class StandingEmptyScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    private val running = listOf(
        RunningHoldout("a", "Habit · 21-day · day 12 of 21", "189 still standing of 312 · prize pool 48,200 SKR", false),
        RunningHoldout("b", "Spark · 7-day · day 7 of 7", "13 still standing of 20 · prize pool 6,125 SKR", false),
    )

    private fun show(running: List<RunningHoldout>, lobby: Pair<String, String>?, events: MutableList<NavEvent>) {
        compose.setContent { HoldoutTheme { StandingEmptyScreen(running, lobby, onEvent = { events += it }) } }
    }

    @Test
    fun `nothing of mine - the running Holdouts to watch, then Join one to have a crowd`() {
        val events = mutableListOf<NavEvent>()
        show(running, null, events)

        compose.onNodeWithText("Standing").assertIsDisplayed()
        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
        compose.onNodeWithText("Habit · 21-day · day 12 of 21").assertIsDisplayed()
        compose.onNodeWithText("189 still standing of 312 · prize pool 48,200 SKR").assertIsDisplayed()
        compose.onNodeWithText("Join one to have a crowd.").assertIsDisplayed()
        compose.onNodeWithText("Everyone who starts together thins out together.").assertIsDisplayed()

        compose.onNodeWithText("See Holdouts").performClick()
        assertEquals(listOf<NavEvent>(NavEvent.SelectTab(Tab.Holdouts)), events)
    }

    @Test
    fun `a running Holdout opens its detail as a watcher`() {
        val events = mutableListOf<NavEvent>()
        show(running, null, events)

        compose.onNodeWithText("Spark · 7-day · day 7 of 7").performClick()

        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Detail("b"))), events)
    }

    @Test
    fun `only a lobby place - the line that opens it, and no Join one to have a crowd`() {
        val events = mutableListOf<NavEvent>()
        show(running, "Yours starts in 2d 4h" to "lobby-1", events)

        compose.onNodeWithText("Join one to have a crowd.").assertDoesNotExist()
        compose.onNodeWithText("Yours starts in 2d 4h").performClick()

        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Lobby("lobby-1"))), events)
    }

    @Test
    fun `a lobby place and nothing running - the line stands alone`() {
        show(emptyList(), "Yours starts in 41m 10s" to "l", mutableListOf())

        compose.onNodeWithText("RUNNING NOW").assertDoesNotExist()
        compose.onNodeWithText("Yours starts in 41m 10s").assertIsDisplayed()
    }

    @Test
    fun `the lobby line sets only the countdown in the mono face, the words around it stay in the text face`() {
        show(emptyList(), "Yours starts in 6m 29s" to "l", mutableListOf())

        compose.onNodeWithText("Yours starts in 6m 29s").assertIsDisplayed()
        val result = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Yours starts in 6m 29s").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        val text = result.first().layoutInput.text
        val mono = text.spanStyles.filter { it.item.fontFamily == HoldoutFonts.Mono }
        assertEquals(1, mono.size, "one mono span")
        assertEquals("6m 29s", text.text.substring(mono.single().start, mono.single().end))
        assertNotEquals(HoldoutFonts.Mono, result.first().layoutInput.style.fontFamily, "the base style is not mono")
    }

    @Test
    fun `nothing running and no place - only the way to a crowd`() {
        show(emptyList(), null, mutableListOf())

        compose.onNodeWithText("RUNNING NOW").assertDoesNotExist()
        compose.onNodeWithText("Join one to have a crowd.").assertIsDisplayed()
    }

    @Test
    fun `the leaderboards stay reachable from here`() {
        val events = mutableListOf<NavEvent>()
        show(emptyList(), null, events)

        compose.onNodeWithText("Leaderboards →").performClick()

        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.EASY_7D))), events)
    }

    @Test
    fun `a short test Holdout among them wears its badge`() {
        show(listOf(RunningHoldout("t", "Test Holdout · day 1 of 3", "3 still standing of 3 · prize pool 0 SKR", test = true, testDayLength = 600)), null, mutableListOf())

        compose.onNodeWithText("SHORT TEST COHORT · 10-MINUTE DAYS · DEVNET").assertIsDisplayed()
    }
}
