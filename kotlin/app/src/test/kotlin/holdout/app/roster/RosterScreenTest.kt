package holdout.app.roster

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.chain.Holdout
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.MembershipSnapshot
import holdout.core.ScriptedWallets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** H5, the roster (board 14): running, reading, in a lobby, and the scripted disclosure. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class RosterScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    private val c = Fx.cohort(days = 21, joined = 6)
    private val now = c.start + 11 * Fx.DAY + 600
    private val h = Fx.holdout(c, Fx.member(days = 11))

    private fun show(h: Holdout = this.h, members: List<MembershipSnapshot>?, at: Long = now, onBack: () -> Unit = {}) {
        compose.setContent { HoldoutTheme { RosterScreen(h, members, Fx.me.base58(), at, onBack) } }
    }

    private val running = listOf(
        Fx.member(Fx.me, days = 11),
        Fx.member(Fx.key(1), days = 11),
        Fx.member(Fx.key(2), days = 11),
        Fx.member(Fx.key(100), days = 9, state = MembershipSnapshot.State.Forfeited),
        Fx.member(Fx.key(101), days = 3, state = MembershipSnapshot.State.Forfeited),
        Fx.member(Fx.key(102), days = 6, state = MembershipSnapshot.State.Forfeited),
    )

    @Test
    fun `running - the title carries the day, no date, and the two sections are counted`() {
        show(members = running)

        compose.onNodeWithText("Habit · 21-day · day 12 of 21").assertIsDisplayed()
        compose.onNodeWithText("STILL STANDING · 3").assertIsDisplayed()
        compose.onNodeWithText("OUT · 3").assertIsDisplayed()
    }

    @Test
    fun `the standing come first and the out by days lasted, the connected member marked you`() {
        show(members = running)

        compose.onNode(hasContentDescription("${Fx.me.base58().let { it.take(4) + "…" + it.takeLast(4) }} · you, still standing, 11 days")).assertExists()
        compose.onNodeWithText("lasted 9 days", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("lasted 6 days", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("lasted 3 days", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the note says nobody is ranked by money and that scripted days are not camera-verified`() {
        show(members = running)

        assertTrue(RosterText.NOTE.contains("not camera-verified"))
        assertTrue(RosterText.NOTE.contains("◇ SCRIPTED"))
        compose.onNodeWithText(RosterText.NOTE).assertIsDisplayed()
    }

    @Test
    fun `a scripted member carries the scripted chip, in or out`() {
        val scripted = ScriptedWallets.keys.firstOrNull() ?: return
        show(members = listOf(Fx.member(Fx.me, days = 11), Fx.member(scripted, days = 11)))

        compose.onNodeWithText("◇ SCRIPTED", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `no amount appears on the roster`() {
        show(members = running)

        compose.onNodeWithText("SKR", substring = true).assertDoesNotExist()
    }

    @Test
    fun `reading - says so and has no sections yet`() {
        show(members = null)

        compose.onNodeWithText("READING THE ROSTER…").assertIsDisplayed()
        compose.onNodeWithText("OUT · 0").assertDoesNotExist()
    }

    @Test
    fun `in a lobby everyone is in and the note says nobody has had a deadline yet`() {
        val lobby = Fx.cohort(days = 7, joined = 3, start = Fx.START + 3 * Fx.DAY)
        val members = listOf(Fx.member(Fx.me), Fx.member(Fx.key(1)), Fx.member(Fx.key(2)))
        show(h = Fx.holdout(lobby, Fx.member()), members = members, at = Fx.START)

        compose.onNodeWithText("Spark · 7-day").assertIsDisplayed()
        compose.onNodeWithText("IN THE LOBBY · 3").assertIsDisplayed()
        compose.onNodeWithText("Everyone is standing until the first deadline.").assertIsDisplayed()
        compose.onNodeWithText("STILL STANDING · 3").assertDoesNotExist()
    }

    @Test
    fun `back goes to where the roster was opened`() {
        var backs = 0
        show(members = running, onBack = { backs++ })

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `a Hackathon Holdout wears its badge on the roster`() {
        val test = Fx.cohort(days = 3, joined = 3, leagueId = 100, dayLength = 300, form = 1)
        show(h = Fx.holdout(test, Fx.member()), members = Fx.roster(3, 0, lasted = 1), at = test.start + 650)

        compose.onNodeWithText("DEVNET COHORT · 5-MINUTE DAYS").assertIsDisplayed()
    }

    @Test
    fun `the page's words never mention a date or a clock time`() {
        val words = listOf(RosterText.NOTE, RosterText.LOBBY_NOTE, RosterText.title(c, now), RosterText.standingHeader(3), RosterText.outHeader(3), RosterText.lobbyHeader(3))
        val dateOrTime = Regex("""\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun|UTC)\b|\d{1,2}:\d{2}""")
        words.forEach { assertTrue(!dateOrTime.containsMatchIn(it), "\"$it\"") }
    }
}
