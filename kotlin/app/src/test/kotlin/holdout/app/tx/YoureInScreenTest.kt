package holdout.app.tx

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import holdout.app.nav.NavEvent
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** You're in (board 12; G7, B-D19): what a landed join shows, and its two ways on. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class YoureInScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val state = YoureInState("Spark · 7-day", "2d 4h", 15, "13,750 SKR", "1,250 SKR", "2d 3h", daily25Done = false)
    private var daily = 0
    private val events = mutableListOf<NavEvent>()

    private fun show(s: YoureInState = state) {
        compose.setContent { HoldoutTheme { YoureInScreen(s, onDaily25 = { daily++ }, onEvent = { events += it }) } }
    }

    private fun text(s: String) = compose.onNode(hasText(s))

    @Test
    fun `it says the member is in, and when everyone starts together`() {
        show()
        text("You're in.").assertIsDisplayed()
        text("Spark · 7-day. Everyone starts together in 2d 4h.").assertIsDisplayed()
    }

    @Test
    fun `members now, what is at stake in all, the member's own held stake and when joining closes`() {
        show()
        text("Members now").assertExists()
        text("15").assertExists()
        text("At stake now").assertExists()
        text("13,750 SKR").assertExists()
        text("total staked").assertExists()
        text("Your stake").assertExists()
        text("1,250 SKR").assertExists()
        text("held by the contract, not by us").assertExists()
        text("Joining closes in").assertExists()
        text("2d 3h").assertExists()
        text("Until then, leaving refunds it all.").assertExists()
    }

    @Test
    fun `Do today's Daily 25 starts the sitting, See the lobby opens the lobby`() {
        show()
        text("Do today's Daily 25").performScrollTo().performClick()
        text("See the lobby").performScrollTo().performClick()
        assertEquals(1, daily)
        assertEquals(listOf<NavEvent>(NavEvent.SeeLobby), events)
    }

    @Test
    fun `with today's Daily 25 done only the lobby is offered`() {
        show(state.copy(daily25Done = true))
        text("Do today's Daily 25").assertDoesNotExist()
        text("See the lobby").assertExists()
    }

    @Test
    fun `a test Holdout wears its badge, and no dollar figure appears`() {
        show(state.copy(test = true, testDayLength = 600))
        text("SHORT TEST COHORT · 10-MINUTE DAYS · DEVNET").assertIsDisplayed()
        compose.onAllNodes(hasText("≈", substring = true)).assertCountEquals(0)
    }
}
