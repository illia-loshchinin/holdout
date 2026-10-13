package holdout.app.lobby

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.holdout.ProgramErrors
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The lobby, open and locked (board 8; US-2, FR-009, ES-12, ES-12a, rulings G2, G3, G7, G9). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class LobbyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L

    /** Two days, four hours before the start; joining closes an hour before it. */
    private val openNow = start - 2 * day - 4 * 3_600

    /** Forty-one minutes before the start: joining closed 23 minutes ago. */
    private val lockedNow = start - 41 * 60 - 10

    private fun holdout(form: Int = LeagueParams.FORM_KNEES_ALLOWED, days: Int = 7, leagueId: Int = LeagueParams.LEAGUE_7D, dayLength: Long = day, member: Boolean = true, cutoff: Long = start - 3_600, reps: Int = 25) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { 4 }),
        cohort = CohortSnapshot(leagueId, 1, start, cutoff, days, reps, dayLength, 0, listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr), memberCount = 14, heldStake = 12_500uL * skr, form = form),
        league = if (leagueId < LeagueParams.TEST_LEAGUE_MIN) LeagueSnapshot(LeagueParams.judged(leagueId, start), nextIndex = 3) else null,
        membership = if (member) MembershipSnapshot(SolanaPublicKey(ByteArray(32) { 9 }), 1, 1_250uL * skr, start - 3 * day, 0, MembershipSnapshot.State.Joined) else null,
    )

    private var leaves = 0
    private var rosters = 0
    private var backs = 0

    private fun show(
        h: Holdout = holdout(),
        now: Long = openNow,
        busy: Boolean = false,
        message: String? = null,
        leftStake: String? = null,
        notificationsOn: Boolean = false,
    ) {
        compose.setContent {
            HoldoutTheme {
                LobbyScreen(h, now, busy, message, true, onLeave = { leaves++ }, onRoster = { rosters++ }, onBack = { backs++ }, leftStake = leftStake, notificationsOn = notificationsOn)
            }
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onNode(hasText(s, substring = substring))

    @Test
    fun `the header names the level and counts down to the start, with no date`() {
        show()
        text("SPARK · 7-DAY · STARTS IN 2D 4H").assertIsDisplayed()
        text("Spark · 7-day").assertIsDisplayed()
        text("2d 4h").assertIsDisplayed()
        text("Everyone starts together.").assertIsDisplayed()
    }

    @Test
    fun `joined count, what is at stake, and that nothing is in the prize pool yet`() {
        show()
        text("14").assertIsDisplayed()
        text("joined, you too").assertIsDisplayed()
        text("12,500 SKR").assertIsDisplayed()
        text("at stake").assertIsDisplayed()
        text("No prize pool until someone misses a day.").assertIsDisplayed()
    }

    @Test
    fun `a visitor who is not in it sees joined, not you too`() {
        show(holdout(member = false))
        text("joined").assertIsDisplayed()
        text("joined, you too").assertDoesNotExist()
    }

    @Test
    fun `See who's in opens the roster`() {
        show()
        text("See who's in →").performClick()
        assertEquals(1, rosters)
    }

    @Test
    fun `the member's stake is held by the contract, and joining closes in a countdown`() {
        show()
        text("Your stake").assertIsDisplayed()
        text("1,250 SKR").assertIsDisplayed()
        text("held by the contract, not by us").assertIsDisplayed()
        text("Joining closes in").assertIsDisplayed()
        text("2d 3h").assertIsDisplayed()
        // The lobby shows SKR only, so it carries no dollar disclaimer (owner, 12 Oct).
        text("approximate", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the Spark lobby reads the Spark rule and never the Habit sentence`() {
        show()
        text("25 push-ups a day for 7 days. At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count.").assertExists()
        text("don't count", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the Habit lobby reads the Habit rule`() {
        show(holdout(form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D))
        text("SPARK", substring = true).assertDoesNotExist()
        text("HABIT · 21-DAY · STARTS IN 2D 4H").assertIsDisplayed()
        text("25 push-ups a day for 21 days. At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count.").assertExists()
    }

    @Test
    fun `Leave - full refund until joining closes, with the warning action`() {
        show()
        text("Leave — full refund").assertIsDisplayed().performClick()
        text("Until joining closes.").assertIsDisplayed()
        assertEquals(1, leaves)
    }

    @Test
    fun `while the wallet is out the button waits for it and says where to approve`() {
        show(busy = true)
        text("Waiting for your wallet…").assertIsNotEnabled()
        text("Approve in your wallet.").assertIsDisplayed()
        text("Leave — full refund").assertDoesNotExist()
    }

    @Test
    fun `once the leave landed - the stake is back and Back to Holdouts is the way on`() {
        show(leftStake = "1,250 SKR")
        text("You left. All 1,250 SKR is back in your wallet.").assertIsDisplayed()
        text("Back to Holdouts").performClick()
        assertEquals(1, backs)
        text("Leave — full refund").assertDoesNotExist()
    }

    @Test
    fun `locked - the final roster, no leave, and the reminder only when notifications are on`() {
        show(now = lockedNow, notificationsOn = true)
        text("SPARK · 7-DAY · LOCKED · STARTS IN 41M 10S").assertIsDisplayed()
        text("Joining closed. This is the final roster.").assertIsDisplayed()
        text("Leave — full refund").assertDoesNotExist()
        text("Until it starts, just keep your streak. We'll remind you.").assertIsDisplayed()
    }

    @Test
    fun `locked with notifications off promises no reminder (G9)`() {
        show(now = lockedNow, notificationsOn = false)
        text("Until it starts, just keep your streak.").assertIsDisplayed()
        text("remind", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a leave that landed after the cutoff says the member is still in (ES-12)`() {
        show(now = lockedNow, message = ProgramErrors.explain(6021))
        text("Leaving closed before your transaction landed. You're still in.").performScrollTo().assertIsDisplayed()
        text(ProgramErrors.explain(6021)!!).assertDoesNotExist()
    }

    @Test
    fun `a Hackathon Holdout wears its badge and its own name`() {
        show(holdout(leagueId = LeagueParams.TEST_LEAGUE_MIN, days = 3, dayLength = 600, reps = 5, cutoff = start), now = start - 750)
        text("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        text("HACKATHON · SPARK · STARTS IN 12M 30S").assertIsDisplayed()
        compose.onAllNodes(hasText("12m 30s")).assertCountEquals(2)
    }

    @Test
    fun `the back arrow goes back`() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `no calendar date or clock time, no dollar sign`() {
        show()
        text("UTC", substring = true).assertDoesNotExist()
        text("Oct", substring = true).assertDoesNotExist()
        text("≈", substring = true).assertDoesNotExist()
    }

    @Test
    fun `lobby words`() {
        assertTrue(LobbyText.isLateLeave(ProgramErrors.explain(6021)))
        assertFalse(LobbyText.isLateLeave("Devnet did not answer."))
        assertFalse(LobbyText.isLateLeave(null))
    }
}
