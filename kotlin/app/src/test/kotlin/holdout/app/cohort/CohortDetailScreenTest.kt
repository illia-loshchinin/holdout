package holdout.app.cohort

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.chain.Holdout
import holdout.app.home.DayState
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.Format
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.MembershipSnapshot
import holdout.core.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** H4, the Holdout detail (board 13): the running, watching, settling, late, claimed and out states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class CohortDetailScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    /** Day 5 of a 7-day Spark with 10 members, 7 still going and 3 out; I am in and standing. */
    private val now = Fx.START + 4 * Fx.DAY + 600
    private val cohort = Fx.cohort(joined = 10, pool = 3_000_000_000uL, held = 12_500_000_000uL)
    private val mine = Fx.holdout(cohort, Fx.member(days = 4), boostReserve = 480_000_000uL)
    private val roster = Fx.roster(standing = 7, out = 3)

    private fun show(
        h: Holdout = mine,
        members: List<MembershipSnapshot>? = roster,
        stage: Stage = h.stage(now),
        at: Long = now,
        signature: String? = null,
        onBack: (() -> Unit)? = null,
        onRoster: () -> Unit = {},
        onLeaderboards: (() -> Unit)? = {},
        onForfeit: (() -> Unit)? = null,
        offline: Boolean = false,
        onCheck: () -> Unit = {},
        day: DayState? = null,
        onStartDay: () -> Unit = {},
        onSubmitDay: (Int) -> Unit = {},
        onGetTokens: () -> Unit = {},
    ) {
        compose.setContent {
            HoldoutTheme {
                CohortDetailScreen(
                    h, members, stage, at, signature, onRoster, onBack,
                    offline = offline, onCheck = onCheck, onLeaderboards = onLeaderboards, onForfeit = onForfeit,
                    day = day, onStartDay = onStartDay, onSubmitDay = onSubmitDay, onGetTokens = onGetTokens,
                )
            }
        }
    }

    @Test
    fun `running - the header names the level and the day, with no date, and the counter shows 7 of 10`() {
        show()

        compose.onNodeWithText("SPARK · 7-DAY · DAY 5 OF 7").assertIsDisplayed()
        compose.onNodeWithText("7 / 10").assertIsDisplayed()
        compose.onNodeWithText("STILL STANDING").assertIsDisplayed()
    }

    @Test
    fun `running - pool, the boost line, days to go excluding today, days done and the share if the crowd holds`() {
        show()

        compose.onNodeWithText("Prize pool").assertIsDisplayed()
        // 10 % of the 3,000 SKR pool, under the 480 SKR reserve: the boost is "up to", never a promise.
        compose.onNodeWithText("+ about 300 SKR boost if anyone finishes").assertIsDisplayed()
        compose.onNodeWithText("Days to go").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("4 of 7 days").assertIsDisplayed()
        compose.onNodeWithText("Your share now").assertIsDisplayed()
        compose.onNodeWithText(DetailText.SHARE_NOTE).assertIsDisplayed()
    }

    @Test
    fun `running - the thinning chart is named by what it shows`() {
        show()

        compose.onNodeWithContentDescription("Members still standing after each day: 10 at the start, 7 after day 4").assertExists()
        compose.onNodeWithText("HOW THE CROWD THINNED").assertIsDisplayed()
    }

    @Test
    fun `the Standing root has no back arrow, a pushed detail has one that goes back`() {
        show(onBack = null)
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `a pushed detail goes back`() {
        var backs = 0
        show(onBack = { backs++ })
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the roster and the leaderboards buttons go where they say`() {
        var roster = 0
        var boards = 0
        show(onRoster = { roster++ }, onLeaderboards = { boards++ })

        compose.onNodeWithText("The roster").performClick()
        compose.onNodeWithText("Leaderboards").performClick()

        assertEquals(1, roster)
        assertEquals(1, boards)
    }

    @Test
    fun `no leaderboards button when the host gives none`() {
        show(onLeaderboards = null)
        compose.onNodeWithText("Leaderboards").assertDoesNotExist()
    }

    @Test
    fun `a watcher sees the crowd but no record of their own`() {
        show(h = Fx.holdout(cohort, null, boostReserve = 480_000_000uL))

        compose.onNodeWithText(DetailText.WATCHING).assertIsDisplayed()
        compose.onNodeWithText(DetailText.WATCHER_NOTE).assertIsDisplayed()
        compose.onNodeWithText("Your share now").assertDoesNotExist()
        compose.onNodeWithText("Days done").assertIsDisplayed()
    }

    @Test
    fun `a Hackathon Holdout wears its badge`() {
        val test = Fx.cohort(joined = 10, leagueId = 100, dayLength = 300, days = 3, form = 1)
        val h = Fx.holdout(test, Fx.member(days = 1))
        show(h = h, members = Fx.roster(3, 0, lasted = 1), at = test.start + 650)

        compose.onNodeWithText("DEVNET COHORT · 5-MINUTE DAYS").assertIsDisplayed()
    }

    @Test
    fun `settling - the usual time from the end and what is left of it`() {
        val done = Fx.cohort(joined = 10, start = Fx.START - 7 * Fx.DAY)
        val end = done.start + 7 * Fx.DAY
        val h = Fx.holdout(done, Fx.member(days = 7, state = MembershipSnapshot.State.Finished))
        show(h = h, members = Fx.roster(3, 0, finished = true), at = end + 240)

        compose.onNodeWithText("You finished · settling").assertIsDisplayed()
        compose.onNodeWithText("Automation settles it on its next pass, usually within 15 minutes of the end.").assertIsDisplayed()
        compose.onNodeWithText("Ended 4m ago · 11m left of those 15").assertIsDisplayed()
        compose.onNodeWithText("SPARK · 7-DAY · FINISHED").assertIsDisplayed()
    }

    @Test
    fun `settling late - says it is taking longer and that nothing owed can change`() {
        val done = Fx.cohort(joined = 10, start = Fx.START - 7 * Fx.DAY)
        val end = done.start + 7 * Fx.DAY
        val h = Fx.holdout(done, Fx.member(days = 7, state = MembershipSnapshot.State.Finished))
        show(h = h, members = Fx.roster(3, 0, finished = true), at = end + 19 * 60)

        compose.onNodeWithText("Taking longer than usual. It settles on the next automation pass; nothing you're owed can change.").assertIsDisplayed()
        compose.onNodeWithText("Ended 19m ago").assertIsDisplayed()
    }

    @Test
    fun `claimed - the amount, the three rows and the explorer link`() {
        val done = Fx.cohort(joined = 20, start = Fx.START - 9 * Fx.DAY, settled = true, finishers = 11, share = 162_000_000uL)
        val stage = Stage.Claimed(amount = 1_662_000_000uL, stake = 1_250_000_000uL, share = 162_000_000uL, comeback = 250_000_000uL)
        val h = Fx.holdout(done, Fx.member(days = 7, state = MembershipSnapshot.State.Claimed))
        show(h = h, members = Fx.roster(11, 9, finished = true), stage = stage, at = Fx.START + 10 * Fx.DAY, signature = "4Bx9pQ7Qk2abcdef7Qk2")

        compose.onNodeWithText("Claimed").assertIsDisplayed()
        compose.onNodeWithText("1,662 SKR").assertIsDisplayed()
        compose.onNodeWithText("Stake back").assertIsDisplayed()
        compose.onNodeWithText("Share of the prize pool").assertIsDisplayed()
        compose.onNodeWithText("Comeback").assertIsDisplayed()
        compose.onNodeWithText("4Bx9…7Qk2 · view on explorer →").assertIsDisplayed()
    }

    @Test
    fun `out - a warning card with the days lasted that opens the forfeit screen`() {
        var forfeits = 0
        val h = Fx.holdout(cohort, Fx.member(days = 2, state = MembershipSnapshot.State.Forfeited), boostReserve = 480_000_000uL)
        show(h = h, onForfeit = { forfeits++ })

        compose.onNodeWithText("You're out after 2 days").assertIsDisplayed()
        compose.onNodeWithText("Your way back").performClick()
        assertEquals(1, forfeits)
        compose.onNodeWithText("Your share now").assertDoesNotExist()
    }

    @Test
    fun `reading - before the roster arrives it shows the started count and says it is reading`() {
        show(members = null)

        compose.onNodeWithText("10").assertIsDisplayed()
        compose.onNodeWithText("STARTED").assertIsDisplayed()
        compose.onNodeWithText("Reading devnet…").assertIsDisplayed()
    }

    @Test
    fun `offline - the line stays and Check again asks for a read`() {
        var checks = 0
        show(offline = true, onCheck = { checks++ })

        compose.onNodeWithText("Devnet can't be reached right now.").assertIsDisplayed()
        compose.onNodeWithText("Check again").performClick()
        assertEquals(1, checks)
    }

    // ---- the day strip (FR-069b): the member's day, at the top of Standing while it is open ----

    private fun today(banked: Int = 0, done: Boolean = false, pending: Int? = null, tried: Boolean = false, offline: Boolean = false, fees: Boolean = true) = DayState.of(cohort, Stage.Active(4, done), now, banked, pending, tried, offline, false, fees)

    @Test
    fun `the day strip names the day, the target and the time left, with Start`() {
        var starts = 0
        val day = today()
        show(day = day, onStartDay = { starts++ })

        compose.onNodeWithText("DAY 5 · 25 push-ups · ${Format.countdown(day.timeLeft!!)} left").assertIsDisplayed()
        compose.onNodeWithText("Start today's 25").performClick()
        assertEquals(1, starts)
    }

    @Test
    fun `the day strip sits above the counter`() {
        show(day = today())
        val strip = compose.onNodeWithText("Start today's 25").fetchSemanticsNode().positionInRoot.y
        val counter = compose.onNodeWithText("STILL STANDING").fetchSemanticsNode().positionInRoot.y
        assertTrue(strip < counter)
    }

    @Test
    fun `part-way the strip says Keep going`() {
        show(day = today(banked = 10))
        compose.onNodeWithText("Keep going").assertIsDisplayed()
    }

    @Test
    fun `done and on chain - one quiet line and no button`() {
        show(day = today(done = true), stage = Stage.Active(4, true))

        compose.onNodeWithText("Day 5 is done and on chain.").assertIsDisplayed()
        compose.onNodeWithText("Start today's 25").assertDoesNotExist()
        compose.onNodeWithText("DAY 5 · 25 push-ups", substring = true).assertDoesNotExist()
        compose.onNodeWithText("left", substring = true).assertDoesNotExist()
    }

    @Test
    fun `done and not sent while offline - waiting for network, no button`() {
        show(day = today(pending = 4, offline = true), offline = true)

        compose.onNodeWithText("Done — waiting for network").assertIsDisplayed()
        compose.onNodeWithText("Submit day 5").assertDoesNotExist()
    }

    @Test
    fun `done and the send did not land - Submit day 5 sends it`() {
        val sent = mutableListOf<Int>()
        show(day = today(pending = 4, tried = true), onSubmitDay = { sent += it })

        compose.onNodeWithText("Done — tap to submit").assertIsDisplayed()
        compose.onNodeWithText("Submit day 5").performClick()
        assertEquals(listOf(4), sent)
    }

    @Test
    fun `done but short of SOL - Submit is off and Get test tokens is the way`() {
        var tokens = 0
        show(day = today(pending = 4, fees = false), onGetTokens = { tokens++ })

        compose.onNodeWithText("Submit day 5").assertIsNotEnabled()
        compose.onNodeWithText("Get test tokens").performClick()
        assertEquals(1, tokens)
    }

    @Test
    fun `no strip for a Holdout the member only watches`() {
        show(h = Fx.holdout(cohort, null), day = today())
        compose.onNodeWithText("Start today's 25").assertDoesNotExist()
        compose.onNodeWithText("DAY 5 · 25 push-ups", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no strip after the last day`() {
        val done = Fx.cohort(joined = 10, start = Fx.START - 7 * Fx.DAY)
        val h = Fx.holdout(done, Fx.member(days = 7, state = MembershipSnapshot.State.Finished))
        show(h = h, members = Fx.roster(3, 0, finished = true), at = done.start + 7 * Fx.DAY + 240, day = today())
        compose.onNodeWithText("Start today's 25").assertDoesNotExist()
    }

    @Test
    fun `no strip when the host gives none`() {
        show()
        compose.onNodeWithText("Start today's 25").assertDoesNotExist()
    }
}
