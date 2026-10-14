package holdout.app.holdouts

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

    private var checks = 0

    private fun show(
        state: MyHoldoutsState?,
        canJoin: Boolean = true,
        running: List<RunningHoldout> = emptyList(),
        offline: Boolean = false,
        still: Boolean = false,
        checking: Boolean = false,
        readAgo: Long? = 240,
    ) {
        compose.setContent {
            HoldoutTheme {
                MyHoldoutsScreen(
                    state, onEvent = { events += it }, onJoin = { joins += it }, canJoin = canJoin, running = running,
                    offline = offline, stillUnreachable = still, checking = checking, onCheck = { checks++ }, readAgoSeconds = readAgo,
                )
            }
        }
    }

    private val watched = RunningHoldout("w1", "Habit · 21-day · day 12 of 21", "189 still standing of 312 · prize pool 48,200 SKR", false)
    private val watched2 = RunningHoldout("w2", "Spark · 7-day · day 3 of 7", "9 still standing of 12 · prize pool 2,400 SKR", false)

    private val running = YourHoldout("r", "Habit · 21-day", YourKind.Running, ChipTone.Success, "Day 12 of 21 · 189 still standing", "15 push-ups to go today", false, NavEvent.Open(Screen.Detail("r")))
    private val lobby = YourHoldout("l", "Spark · 7-day", YourKind.Lobby, ChipTone.Accent, "Joined · 1,250 SKR staked", "starts in 2d 4h", false, NavEvent.Open(Screen.Lobby("l")))
    private val locked = YourHoldout("k", "Spark · 7-day", YourKind.Locked, ChipTone.Neutral, "Joining closed · 14 in · 1,250 SKR staked", "starts in 41m 10s", false, NavEvent.Open(Screen.Lobby("k")))
    private val out = YourHoldout("o", "Habit · 21-day", YourKind.Out, ChipTone.Warning, "Lost 1,250 SKR · out on day 7", "Win back 125 SKR", false, NavEvent.Open(Screen.Forfeit("o")), detailTone = ChipTone.Success)
    private val settling = YourHoldout("s", "Spark · 7-day", YourKind.Settling, ChipTone.Success, "You finished · settling, usually within 11m", null, false, NavEvent.Open(Screen.Detail("s")))
    private val claim = YourHoldout("c", "Spark · 7-day", YourKind.Claim, ChipTone.Success, "Held out 7 of 7", "1,412 SKR waiting for you", false, NavEvent.Open(Screen.Claim("c")))
    private val habitOffer = OpenHoldout("b", "Habit · 21-day", "9,000 SKR", "at stake · 9 joined", "Prize boost: up to 1,200 SKR", "Joining closes in 2d 3h", false)
    private val free = OpenHoldout("f", "Spark · 7-day", "12,500 SKR", "at stake · 14 joined", "Prize boost: up to 480 SKR", "Joining closes in 2d 3h", false)

    @Test
    fun `running, lobby and claim - each card says its state and opens its screen`() {
        show(MyHoldoutsState(yours = listOf(running, lobby, claim)))

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
        compose.onNodeWithText("Lost 1,250 SKR · out on day 7").assertIsDisplayed()
        compose.onNodeWithText("Win back 125 SKR").assertIsDisplayed().performClick()
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
    fun `a tap anywhere on an offer card joins, as the Join pill does`() {
        show(MyHoldoutsState(open = listOf(free)))

        compose.onNodeWithText("Spark · 7-day").performClick()
        compose.onNodeWithText("Join").performClick()

        assertEquals(listOf("f", "f"), joins)
    }

    @Test
    fun `a wallet this session found holds no Seeker - the offers show without a Join, and one line says why`() {
        show(MyHoldoutsState(open = listOf(free, habitOffer)), canJoin = false)

        compose.onNodeWithText("12,500 SKR").assertIsDisplayed()
        compose.onNodeWithText(MyHoldoutsModel.SEEKER_ONLY).assertIsDisplayed()
        compose.onNodeWithText("Join").assertDoesNotExist()
        compose.onNodeWithText("Spark · 7-day").performClick()
        assertTrue(joins.isEmpty())
    }

    private val won = PastHoldout("w", "Spark · 7-day", "Held out 7 of 7 · 12 of 14 finished", "1,180 SKR", NavEvent.Open(Screen.Detail("w")))
    private val wonTest = PastHoldout("t", "Hackathon · Spark", "Held out 3 of 3", "605.42 SKR", NavEvent.Open(Screen.Detail("t")))
    private val lost = PastHoldout("x", "Habit · 21-day", "Out on day 5 of 21", "2,500 SKR", NavEvent.Open(Screen.Forfeit("x")))

    private fun past(state: MyHoldoutsState, running: List<RunningHoldout> = emptyList()) {
        show(state, running = running)
        compose.onNodeWithText("Past · ", substring = true).performClick()
    }

    @Test
    fun `the switch shows both counts and opens on Now`() {
        show(MyHoldoutsState(yours = listOf(running, lobby), open = listOf(free), won = listOf(won), lost = listOf(lost)))

        compose.onNodeWithText("Now · 2").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Past · 2").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText("Day 12 of 21 · 189 still standing").assertIsDisplayed()
        compose.onNodeWithText("Held out 7 of 7 · 12 of 14 finished").assertDoesNotExist()
    }

    @Test
    fun `Past shows what was won and what was lost, and Now's cards and offers step aside`() {
        past(MyHoldoutsState(yours = listOf(running), open = listOf(free), won = listOf(won), lost = listOf(lost)))

        compose.onNodeWithText("Past · 2").assertIsSelected()
        compose.onNodeWithText("Now · 1").assertIsNotSelected()
        compose.onNodeWithText("WON").assertIsDisplayed()
        compose.onNodeWithText("Spark · 7-day").assertIsDisplayed()
        compose.onNodeWithText("Held out 7 of 7 · 12 of 14 finished").assertIsDisplayed()
        compose.onNodeWithText("1,180 SKR").assertIsDisplayed()
        compose.onNodeWithText("claimed").assertIsDisplayed()
        compose.onNodeWithText("LOST").assertIsDisplayed()
        compose.onNodeWithText("Out on day 5 of 21").assertIsDisplayed()
        compose.onNodeWithText("2,500 SKR").assertIsDisplayed()
        compose.onNodeWithText("lost").assertIsDisplayed()
        compose.onNodeWithText("Day 12 of 21 · 189 still standing").assertDoesNotExist()
        compose.onNodeWithText("OPEN TO JOIN").assertDoesNotExist()
    }

    @Test
    fun `a Past row opens the screen it names - the detail when won, the forfeit when lost`() {
        past(MyHoldoutsState(won = listOf(won, wonTest), lost = listOf(lost)))

        compose.onNodeWithText("Held out 7 of 7 · 12 of 14 finished").performClick()
        compose.onNodeWithText("Out on day 5 of 21").performClick()

        assertEquals(listOf(won.go, lost.go), events)
    }

    @Test
    fun `a Past group with no rows is not drawn`() {
        past(MyHoldoutsState(yours = listOf(running), won = listOf(won)))
        compose.onNodeWithText("WON").assertIsDisplayed()
        compose.onNodeWithText("LOST").assertDoesNotExist()
        compose.onNodeWithText("Nothing finished yet.").assertDoesNotExist()
    }

    @Test
    fun `Past with nothing finished says so`() {
        past(MyHoldoutsState(yours = listOf(running)))
        compose.onNodeWithText("Past · 0").assertIsSelected()
        compose.onNodeWithText("Nothing finished yet.").assertIsDisplayed()
        compose.onNodeWithText("WON").assertDoesNotExist()
        compose.onNodeWithText("LOST").assertDoesNotExist()
    }

    @Test
    fun `the switch goes back to Now`() {
        past(MyHoldoutsState(yours = listOf(running), open = listOf(free), won = listOf(won)))

        compose.onNodeWithText("Now · 1").performClick()

        compose.onNodeWithText("Day 12 of 21 · 189 still standing").assertIsDisplayed()
        compose.onNodeWithText("OPEN TO JOIN").assertIsDisplayed()
    }

    // ---- Running now ends Now, for every wallet (FR-062a, 2026-10-13) ------------------------------------

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `Running now is the last section of Now - after the member's own cards and Open to join`() {
        show(MyHoldoutsState(yours = listOf(running), open = listOf(free)), running = listOf(watched, watched2))

        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
        compose.onNodeWithText("Habit · 21-day · day 12 of 21").assertIsDisplayed()
        compose.onNodeWithText("189 still standing of 312 · prize pool 48,200 SKR").assertIsDisplayed()
        val own = top("Day 12 of 21 · 189 still standing")
        val offers = top("OPEN TO JOIN")
        val section = top("RUNNING NOW")
        assertTrue(own < offers && offers < section, "own $own, offers $offers, running now $section")
        assertTrue(section < top("Habit · 21-day · day 12 of 21") && top("Habit · 21-day · day 12 of 21") < top("Spark · 7-day · day 3 of 7"))
    }

    @Test
    fun `a row of Running now opens that Holdout's screen on Holdouts and offers no Join`() {
        show(MyHoldoutsState(yours = listOf(running)), running = listOf(watched))

        compose.onNodeWithText("Join").assertDoesNotExist()
        compose.onNodeWithText("Habit · 21-day · day 12 of 21").performClick()
        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Detail("w1"))), events)
        assertTrue(joins.isEmpty())
    }

    @Test
    fun `with no Holdout of the member's own, Running now comes straight under the offers`() {
        show(MyHoldoutsState(open = listOf(free)), running = listOf(watched))

        compose.onNodeWithText("No Holdouts yet.").assertIsDisplayed()
        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
        assertTrue(top("OPEN TO JOIN") < top("RUNNING NOW"))
    }

    @Test
    fun `on the empty state with nothing to join, Running now still shows`() {
        show(MyHoldoutsState(), running = listOf(watched))

        compose.onNodeWithText("No Holdouts yet.").assertIsDisplayed()
        compose.onNodeWithText("The next Holdout opens soon.").assertIsDisplayed()
        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
        compose.onNodeWithText("Habit · 21-day · day 12 of 21").assertIsDisplayed()
    }

    @Test
    fun `without a Seeker Running now is there, and opens, while the offers lose their Join`() {
        show(MyHoldoutsState(open = listOf(free)), canJoin = false, running = listOf(watched))

        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
        compose.onNodeWithText("Join").assertDoesNotExist()
        compose.onNodeWithText("Habit · 21-day · day 12 of 21").performClick()
        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Detail("w1"))), events)
    }

    @Test
    fun `while a lobby place is held there are no offers but Running now remains`() {
        show(MyHoldoutsState(yours = listOf(lobby), holdsLobbyPlace = true), running = listOf(watched))

        compose.onNodeWithText("OPEN TO JOIN").assertDoesNotExist()
        compose.onNodeWithText("RUNNING NOW").assertIsDisplayed()
    }

    @Test
    fun `Past has no Running now`() {
        past(MyHoldoutsState(yours = listOf(running), won = listOf(won)), running = listOf(watched))
        compose.onNodeWithText("RUNNING NOW").assertDoesNotExist()
        compose.onNodeWithText("Habit · 21-day · day 12 of 21").assertDoesNotExist()
    }

    @Test
    fun `without any running Holdout there is no Running now section`() {
        show(MyHoldoutsState(yours = listOf(running), open = listOf(free)))

        compose.onNodeWithText("RUNNING NOW").assertDoesNotExist()
        assertEquals(0, compose.onAllNodes(hasText("prize pool", substring = true)).fetchSemanticsNodes().size)
    }

    // ---- the devnet bar (FR-069h) ---------------------------------------------------------------------

    @Test
    fun `devnet not answering - the bar sits directly under the title with the age of what is shown`() {
        show(MyHoldoutsState(yours = listOf(running)), offline = true)

        compose.onNodeWithText("Devnet isn't answering").assertIsDisplayed()
        compose.onNodeWithText("Showing what it said 4m ago.").assertIsDisplayed()
        val title = top("Holdouts")
        val bar = top("Devnet isn't answering")
        val firstCard = top("Day 12 of 21 · 189 still standing")
        assertTrue(title < bar && bar < firstCard, "title $title, bar $bar, card $firstCard")
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, checks)
    }

    @Test
    fun `after a failed retry it says still`() {
        show(MyHoldoutsState(yours = listOf(running)), offline = true, still = true)
        compose.onNodeWithText("Devnet still isn't answering").assertIsDisplayed()
    }

    @Test
    fun `a check in flight - Retry reads Checking and cannot be pressed`() {
        show(MyHoldoutsState(yours = listOf(running)), offline = true, checking = true)
        compose.onNodeWithText("Checking…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `no old notice - nothing says it cannot be reached and there is no Check again button`() {
        show(MyHoldoutsState(yours = listOf(running)), offline = true)

        compose.onNodeWithText("can't be reached", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Check again").assertDoesNotExist()
    }

    @Test
    fun `devnet answering - no bar`() {
        show(MyHoldoutsState(yours = listOf(running)))
        compose.onNodeWithText("Devnet isn't answering").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `empty - no Holdouts yet, with the sell and the refund, and the offers under it`() {
        show(MyHoldoutsState(open = listOf(free)))

        compose.onNodeWithText("No Holdouts yet.").assertIsDisplayed()
        compose.onNodeWithText("Stake on yourself for 7 or 21 days. Full refund until joining closes.").assertIsDisplayed()
        compose.onNodeWithText("Join").assertIsDisplayed()
        compose.onNodeWithText("Now · ", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Past · ", substring = true).assertDoesNotExist()
    }

    @Test
    fun `reading - before the chain has answered`() {
        show(null)
        compose.onNodeWithText("READING THE CHAIN…").assertIsDisplayed()
    }

    @Test
    fun `a Hackathon Holdout wears its badge`() {
        show(MyHoldoutsState(yours = listOf(lobby.copy(name = "Hackathon · Spark", test = true, testDayLength = 600))))

        compose.onNodeWithText("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        compose.onNodeWithText("Hackathon · Spark").assertIsDisplayed()
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
    fun `model - a short test Holdout is named Hackathon, with its length, on its card and its offer`() {
        val test = Fx.cohort(days = 3, joined = 3, leagueId = 100, dayLength = 600, form = 1, start = Fx.START - 300)
        val mine = Fx.holdout(test, Fx.member())
        val open = Fx.holdout(Fx.cohort(days = 3, joined = 1, leagueId = 100, dayLength = 600, form = 1, start = Fx.START + 7_200).copy(index = 2, joinCutoff = Fx.START + 7_200), null)
        val s = MyHoldoutsModel.build(ChainView(null, listOf(mine, open), null, 0uL, 0L), Fx.START)

        assertEquals("Hackathon · Spark", s.yours.single().name)
        assertTrue(s.yours.single().test)
        assertEquals(listOf("Hackathon · Spark"), s.open.map { it.name })
    }

    @Test
    fun `model - a forfeit with its comeback window closed becomes a finished line`() {
        val c = Fx.cohort(days = 7, joined = 12, start = Fx.START - 40 * Fx.DAY)
        val h = Fx.holdout(c, Fx.member(days = 2, state = MembershipSnapshot.State.Forfeited))
        val s = MyHoldoutsModel.build(ChainView(null, listOf(h), null, 0uL, 0L), Fx.START)
        assertTrue(s.yours.isEmpty())
        assertEquals(listOf("Out on day 3 of 7"), s.lost.map { it.line })
    }
}
