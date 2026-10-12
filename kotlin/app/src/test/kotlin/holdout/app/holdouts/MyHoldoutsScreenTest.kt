package holdout.app.holdouts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.chain.ChainView
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.ChipTone
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.MembershipSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** H0, My Holdouts (board 10 and its boards "Out and settling", "Locked lobby", "Empty"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class MyHoldoutsScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<NavEvent>()
    private val joins = mutableListOf<String>()

    private fun show(state: MyHoldoutsState?) {
        compose.setContent { HoldoutTheme { MyHoldoutsScreen(state, onEvent = { events += it }, onJoin = { joins += it }) } }
    }

    private val running = YourHoldout("r", "Habit · 21-day", YourKind.Running, ChipTone.Success, "Day 12 of 21 · 189 still standing", "15 push-ups to go today", false, NavEvent.Open(Screen.Detail("r")))
    private val lobby = YourHoldout("l", "Spark · 7-day", YourKind.Lobby, ChipTone.Accent, "Joined · 1,250 SKR staked", "starts in 2d 4h", false, NavEvent.Open(Screen.Lobby("l")))
    private val locked = YourHoldout("k", "Spark · 7-day", YourKind.Locked, ChipTone.Neutral, "Joining closed · 14 in · 1,250 SKR staked", "starts in 41m 10s", false, NavEvent.Open(Screen.Lobby("k")))
    private val out = YourHoldout("o", "Habit · 21-day", YourKind.Out, ChipTone.Warning, "Out after 6 days", "Win back up to 250 SKR", false, NavEvent.Open(Screen.Forfeit("o")), detailTone = ChipTone.Success)
    private val settling = YourHoldout("s", "Spark · 7-day", YourKind.Settling, ChipTone.Success, "You finished · settling, usually within 11m", null, false, NavEvent.Open(Screen.Detail("s")))
    private val claim = YourHoldout("c", "Spark · 7-day", YourKind.Claim, ChipTone.Success, "Held out 7 of 7", "1,412 SKR waiting for you", false, NavEvent.Open(Screen.Claim("c")))
    private val habitOffer = OpenHoldout("b", "Habit · 21-day", "9,000 SKR", "at stake · 9 joined", "Prize boost: up to 1,200 SKR", "Joining closes in 2d 3h", false)
    private val free = OpenHoldout("f", "Spark · 7-day", "12,500 SKR", "at stake · 14 joined", "Prize boost: up to 480 SKR", "Joining closes in 2d 3h", false)

    @Test
    fun `running, lobby and claim - each card says its state and opens its screen`() {
        show(MyHoldoutsState(yours = listOf(running, lobby, claim)))

        compose.onNodeWithText("YOURS").assertIsDisplayed()
        compose.onNodeWithText("RUNNING").assertIsDisplayed()
        compose.onNodeWithText("Day 12 of 21 · 189 still standing").assertIsDisplayed()
        compose.onNodeWithText("15 push-ups to go today").assertIsDisplayed()
        compose.onNodeWithText("starts in 2d 4h").assertIsDisplayed()
        compose.onNodeWithText("1,412 SKR waiting for you").assertIsDisplayed()

        compose.onNodeWithText("Day 12 of 21 · 189 still standing").performClick()
        compose.onNodeWithText("Held out 7 of 7").performClick()
        compose.onNodeWithText("Joined · 1,250 SKR staked").performClick()

        assertEquals(listOf(running.go, claim.go, lobby.go), events)
    }

    @Test
    fun `out and settling - the forfeit's way back and the settling line`() {
        show(MyHoldoutsState(yours = listOf(out, settling)))

        compose.onNodeWithText("OUT").assertIsDisplayed()
        compose.onNodeWithText("Out after 6 days").assertIsDisplayed()
        compose.onNodeWithText("Win back up to 250 SKR").assertIsDisplayed().performClick()
        compose.onNodeWithText("You finished · settling, usually within 11m").assertIsDisplayed()
        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Forfeit("o"))), events)
    }

    @Test
    fun `a locked lobby says joining closed, how many are in, and when it starts`() {
        show(MyHoldoutsState(yours = listOf(locked)))

        compose.onNodeWithText("LOCKED").assertIsDisplayed()
        compose.onNodeWithText("Joining closed · 14 in · 1,250 SKR staked").assertIsDisplayed()
        compose.onNodeWithText("starts in 41m 10s").assertIsDisplayed()
    }

    @Test
    fun `open to join - the level, what is at stake, the boost, how long, and Join`() {
        show(MyHoldoutsState(open = listOf(free)))

        compose.onNodeWithText("OPEN TO JOIN").assertIsDisplayed()
        compose.onNodeWithText("Holdouts need a Seeker.").assertIsDisplayed()
        compose.onNodeWithText("12,500 SKR").assertIsDisplayed()
        compose.onNodeWithText("at stake · 14 joined").assertIsDisplayed()
        compose.onNodeWithText("Prize boost: up to 480 SKR").assertIsDisplayed()
        compose.onNodeWithText("Joining closes in 2d 3h").assertIsDisplayed()
        compose.onNodeWithText("Join").assertIsEnabled().performClick()

        assertEquals(listOf("f"), joins)
    }

    @Test
    fun `while a lobby place is held there is no Open to join section at all`() {
        show(MyHoldoutsState(yours = listOf(lobby), holdsLobbyPlace = true))

        compose.onNodeWithText("YOURS").assertIsDisplayed()
        compose.onNodeWithText("OPEN TO JOIN").assertDoesNotExist()
        compose.onNodeWithText("Holdouts need a Seeker.").assertDoesNotExist()
        compose.onNodeWithText("The next Holdout opens soon.").assertDoesNotExist()
        compose.onNodeWithText("Join").assertDoesNotExist()
        assertTrue(joins.isEmpty())
    }

    @Test
    fun `with nothing joinable the section says the next Holdout opens soon`() {
        show(MyHoldoutsState(yours = listOf(running)))

        compose.onNodeWithText("OPEN TO JOIN").assertIsDisplayed()
        compose.onNodeWithText("The next Holdout opens soon.").assertIsDisplayed()
    }

    @Test
    fun `no offer carries a dollar figure`() {
        show(MyHoldoutsState(yours = listOf(running), open = listOf(free, habitOffer)))

        compose.onNodeWithText("$", substring = true).assertDoesNotExist()
        compose.onNodeWithText("≈", substring = true).assertDoesNotExist()
    }

    @Test
    fun `finished - one quiet line each, going to its detail`() {
        show(MyHoldoutsState(finished = listOf(FinishedHoldout("f", "Spark · 7-day · finished · claimed 1,180 SKR", NavEvent.Open(Screen.Detail("f"))))))

        compose.onNodeWithText("FINISHED").assertIsDisplayed()
        compose.onNodeWithText("Spark · 7-day · finished · claimed 1,180 SKR →").performClick()

        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Detail("f"))), events)
    }

    @Test
    fun `empty - no Holdouts yet, with the sell and the refund, and the offers under it`() {
        show(MyHoldoutsState(open = listOf(free)))

        compose.onNodeWithText("No Holdouts yet.").assertIsDisplayed()
        compose.onNodeWithText("Stake on yourself for 7 or 21 days. Full refund until joining closes.").assertIsDisplayed()
        compose.onNodeWithText("YOURS").assertDoesNotExist()
        compose.onNodeWithText("Join").assertIsDisplayed()
    }

    @Test
    fun `running now - the Holdouts to watch, every wallet, no Join in them`() {
        val watch = RunningHoldout("w", "Spark · 7-day · day 3 of 7", "9 still standing of 12 · prize pool 2,400 SKR", false)
        show(MyHoldoutsState(running = listOf(watch)))

        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
        compose.onNodeWithText("9 still standing of 12 · prize pool 2,400 SKR").performClick()

        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Detail("w"))), events)
        assertEquals(0, compose.onAllNodes(hasText("Join")).fetchSemanticsNodes().size)
    }

    @Test
    fun `reading - before the chain has answered`() {
        show(null)
        compose.onNodeWithText("READING THE CHAIN…").assertIsDisplayed()
    }

    @Test
    fun `a Hackathon Holdout wears its badge`() {
        show(MyHoldoutsState(yours = listOf(lobby.copy(name = MyHoldoutsModel.TEST_NAME, test = true, testDayLength = 600))))

        compose.onNodeWithText("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        compose.onNodeWithText("Hackathon Holdout").assertIsDisplayed()
    }

    // ---- the model's new words ---------------------------------------------------------------------

    @Test
    fun `model - a locked lobby is its own card with the number in`() {
        val c = Fx.cohort(joined = 14, start = Fx.START + 2_470).copy(joinCutoff = Fx.START)
        val h = Fx.holdout(c, Fx.member())
        val s = MyHoldoutsModel.build(ChainView(null, listOf(h), null, 0uL, 0L), Fx.START + 60)

        val card = s.yours.single()
        assertEquals("locked", card.status)
        assertEquals("Joining closed · 14 in · 1,250 SKR staked", card.line)
        assertEquals("starts in 40m 10s", card.detail)
    }

    @Test
    fun `model - a short test Holdout is a Hackathon Holdout on its card and its offer`() {
        val test = Fx.cohort(days = 3, joined = 3, leagueId = 100, dayLength = 600, form = 1, start = Fx.START - 300)
        val mine = Fx.holdout(test, Fx.member())
        val open = Fx.holdout(Fx.cohort(days = 3, joined = 1, leagueId = 100, dayLength = 600, form = 1, start = Fx.START + 7_200).copy(index = 2, joinCutoff = Fx.START + 7_200), null)
        val s = MyHoldoutsModel.build(ChainView(null, listOf(mine, open), null, 0uL, 0L), Fx.START)

        assertEquals("Hackathon Holdout", s.yours.single().name)
        assertTrue(s.yours.single().test)
        assertEquals(listOf("Hackathon Holdout"), s.open.map { it.name })
    }

    @Test
    fun `model - a forfeit with its comeback window closed becomes a finished line`() {
        val c = Fx.cohort(days = 7, joined = 12, start = Fx.START - 40 * Fx.DAY)
        val h = Fx.holdout(c, Fx.member(days = 2, state = MembershipSnapshot.State.Forfeited))
        val s = MyHoldoutsModel.build(ChainView(null, listOf(h), null, 0uL, 0L), Fx.START)
        assertTrue(s.yours.isEmpty())
        assertEquals(listOf("Spark · 7-day · finished · out after 2 days"), s.finished.map { it.line })
    }
}
