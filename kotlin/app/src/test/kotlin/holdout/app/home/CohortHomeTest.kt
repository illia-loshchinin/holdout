package holdout.app.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Today with a Holdout running (T2, board 5 · 5b; FR-038, FR-046, FR-047,
 * FR-024b): the counter and pool from the roster, the day button in each state,
 * and the callbacks. Habit · 21-day, day 12, with 312 started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class CohortHomeTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L

    /** Day 12 (index 11), 4h 06m before its deadline. */
    private val now = start + 12 * day - 14_760 - 1

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    private val me = MembershipSnapshot(key(1), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined)

    private val cohort = CohortSnapshot(
        LeagueParams.LEAGUE_21D,
        1,
        start,
        start - 3_600,
        21,
        25,
        day,
        0,
        listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr),
        memberCount = 5,
        pool = 100uL * skr,
        form = LeagueParams.FORM_TOES,
    )

    private val h = Holdout(key(9), cohort, LeagueSnapshot(LeagueParams.judged(LeagueParams.LEAGUE_21D, start), nextIndex = 3).copy(boostReserve = 1_200uL * skr), me)

    /** Four standing at 11 days; one out whose day-11 deadline fell this morning: "1 dropped since yesterday". */
    private val roster = listOf(
        me,
        MembershipSnapshot(key(2), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined),
        MembershipSnapshot(key(3), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined),
        MembershipSnapshot(key(4), 1, 1_250uL * skr, start - day, 11, MembershipSnapshot.State.Joined),
        MembershipSnapshot(key(5), 1, 1_250uL * skr, start - day, 10, MembershipSnapshot.State.Joined),
    )

    private var started = 0
    private val submitted = mutableListOf<Int>()
    private var details = 0
    private var tokens = 0
    private var checks = 0

    private fun show(
        banked: Int = 0,
        todayDone: Boolean = false,
        pendingDay: Int? = null,
        autoSendTried: Boolean = false,
        offline: Boolean = false,
        busy: Boolean = false,
        canPayFees: Boolean = true,
        at: Long = now,
        members: List<MembershipSnapshot>? = roster,
        stillUnreachable: Boolean = false,
        holdout: Holdout = h,
        newPhone: Boolean = false,
    ) {
        compose.setContent {
            HoldoutTheme {
                CohortHome(
                    h = holdout,
                    members = members,
                    stage = Stage.Active(11, todayDone),
                    now = at,
                    banked = banked,
                    offline = offline,
                    pendingDay = pendingDay,
                    autoSendTried = autoSendTried,
                    busy = busy,
                    message = null,
                    messageIsWarning = false,
                    onStart = { started++ },
                    onSubmit = { submitted += it },
                    onDetail = { details++ },
                    onGetTokens = { tokens++ },
                    streak = 12,
                    newPhone = newPhone,
                    canPayFees = canPayFees,
                    stillUnreachable = stillUnreachable,
                    onCheck = { checks++ },
                )
            }
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onNode(hasText(s, substring = substring))

    @Test
    fun `the header names the level and the day, with no date`() {
        show()
        text("HABIT · 21-DAY · DAY 12 OF 21").assertIsDisplayed()
    }

    @Test
    fun `the counter is the screen - standing of started - and a link to who is still standing`() {
        show()
        compose.onNodeWithContentDescription("4 of 5, still standing").assertExists()
        compose.onNodeWithContentDescription("4 of 5, still standing").performClick()
        assertEquals(1, details)
    }

    @Test
    fun `the pool and the member's share, with the short condition, and no dollars`() {
        show()
        text("Prize pool").assertIsDisplayed()
        text("Your share now").assertIsDisplayed()
        text("if today's standing holds").assertIsDisplayed()
        text("+ about 97.5 SKR boost if anyone finishes").assertIsDisplayed()
        text("≈", substring = true).assertDoesNotExist()
        text("on top of your", substring = true).assertDoesNotExist()
    }

    @Test
    fun `members who dropped in the last day are counted, with what the share gained`() {
        // key(5) completed 10 days: its day-11 deadline (start + 11 days - 1) is within the last day of `now`.
        show()
        text("1 dropped since yesterday").assertIsDisplayed()
        // Pool 975 SKR over 5,000 standing: 243.75 for the member, against 20 a day ago.
        text("+223.75 SKR since yesterday").assertIsDisplayed()
    }

    @Test
    fun `nothing dropped, nothing said`() {
        show(members = roster.take(4))
        text("dropped since yesterday", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a new phone says nothing is banked on this device, with the Holdout's own target`() {
        show(newPhone = true)
        text("No banked reps on this device today. 25 to go.").assertIsDisplayed()
        text("Start today's 25").assertIsDisplayed()
    }

    @Test
    fun `the new-phone banner takes the target from the Holdout`() {
        show(newPhone = true, holdout = h.copy(cohort = cohort.copy(repsPerDay = 15)))
        text("No banked reps on this device today. 15 to go.").assertIsDisplayed()
    }

    @Test
    fun `the new-phone banner is not drawn on a phone that is not new`() {
        show()
        text("No banked reps on this device", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the new-phone banner leaves once today is done, whatever it was told`() {
        show(newPhone = true, todayDone = true)
        text("No banked reps on this device", substring = true).assertDoesNotExist()
    }

    @Test
    fun `to do - nothing banked, the way to do it and Start today's 25`() {
        show()
        text("Nothing banked yet today").assertIsDisplayed()
        text("all at once or 10 at a time").assertIsDisplayed()
        text("Start today's 25").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `part-way - what is banked, what is left and Keep going`() {
        show(banked = 10)
        text("10 banked today").assertIsDisplayed()
        text("15 to go").assertIsDisplayed()
        text("Keep going").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the last 10 minutes warn that unfinished reps won't count, and never refuse to start`() {
        show(banked = 10, at = start + 12 * day - 1 - 510)
        text("15 push-ups still needed").assertIsDisplayed()
        text("Under 10 minutes left. Unfinished reps won't count.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Under 10 minutes left, 8m 30s").assertExists()
        text("Keep going").assertIsEnabled()
        text("Keep going").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `offline before the sitting - the chain can't be reached, the reps still count once sent, and Check again`() {
        show(offline = true)
        text("${Deployment.networkName} can't be reached right now.").assertIsDisplayed()
        text("You can still do today's reps. The day only counts once it reaches the chain before the deadline.").assertIsDisplayed()
        text("Check again").performClick()
        assertEquals(1, checks)
        text("Start today's 25").assertIsDisplayed()
    }

    @Test
    fun `a failed check says the chain still can't be reached`() {
        show(offline = true, stillUnreachable = true)
        text("${Deployment.networkName} still can't be reached.").assertIsDisplayed()
    }

    @Test
    fun `done and offline - waiting for network, sent when Holdout is open, with the time left`() {
        show(pendingDay = 11, offline = true)
        text("Done — waiting for network").assertIsDisplayed()
        text("Your 25 are saved on this phone. It only counts once it reaches the chain before the deadline: it's sent when Holdout is open and back online, and you approve it in your wallet.").assertIsDisplayed()
        text("Check again").performClick()
        assertEquals(1, checks)
        text("4h 06m").assertIsDisplayed()
    }

    @Test
    fun `done and online, the send under way - the wallet is out and the button says so`() {
        show(pendingDay = 11, busy = true)
        text("Done — sending to the chain").assertIsDisplayed()
        text("Waiting for your wallet…").assertIsNotEnabled()
        text("Your 25 are counted on this phone. It only counts once it reaches the chain before the deadline: approve it in your wallet.").assertIsDisplayed()
    }

    @Test
    fun `done and the send did not land - tap to submit day 12`() {
        show(pendingDay = 11, autoSendTried = true)
        text("Done — tap to submit").assertIsDisplayed()
        text("Submit day 12").performClick()
        assertEquals(listOf(11), submitted)
    }

    @Test
    fun `done but short of SOL - the day can't be submitted, and Get test tokens is the way`() {
        show(pendingDay = 11, canPayFees = false)
        text("Today's 25 are done").assertIsDisplayed()
        text("Not on chain yet.").assertIsDisplayed()
        text("Not enough devnet SOL for the fee. Test tokens include some.").assertIsDisplayed()
        text("Submit day 12").assertIsNotEnabled()
        text("Get test tokens").performClick()
        assertEquals(1, tokens)
        assertEquals(emptyList(), submitted)
    }

    @Test
    fun `submitted - today is on chain and the next day opens in a countdown`() {
        show(todayDone = true)
        text("Today is done and on chain.").assertIsDisplayed()
        text("Next day opens in 4h 06m.").assertIsDisplayed()
        text("Start today's 25").assertDoesNotExist()
    }

    @Test
    fun `the footer carries the streak and the time left`() {
        show()
        compose.onNodeWithContentDescription("12-day streak").assertExists()
        text("left").assertExists()
    }

    @Test
    fun `no Daily 25 link and no roster or details buttons - the counter is the way to Standing`() {
        show()
        text("The Daily 25", substring = true).assertDoesNotExist()
        text("Who's still standing").assertDoesNotExist()
        text("Holdout details").assertDoesNotExist()
        text("About Holdout").assertDoesNotExist()
        text("HOLDOUTS OPEN TO JOIN").assertDoesNotExist()
    }

    @Test
    fun `a short test Holdout wears its badge`() {
        val test = h.copy(cohort = cohort.copy(leagueId = LeagueParams.TEST_LEAGUE_MIN, dayLength = 600))
        show(holdout = test, at = start + 5 * 600 + 100, members = null)
        text("SHORT TEST COHORT · 10-MINUTE DAYS · DEVNET").performScrollTo().assertIsDisplayed()
    }
}
