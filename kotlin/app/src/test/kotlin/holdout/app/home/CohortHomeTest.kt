package holdout.app.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
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
import kotlin.test.assertTrue

/**
 * Today with a Holdout running (T2, board Today in a Holdout; FR-038, FR-046, FR-047, FR-024b, FR-069a,
 * FR-069h): the Holdout's day in the one 312 dp hero — the number, the bars, the sub line and the day
 * button in each state — with the crowd as the hero's footer strip, the rows group, the one quiet offers
 * row and the devnet bar, and the callbacks. Habit · 21-day, day 12, with 5 started.
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
    private val events = mutableListOf<NavEvent>()
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
        checking: Boolean = false,
        mayStillLand: Boolean = false,
        holdout: Holdout = h,
        newPhone: Boolean = false,
        message: String? = null,
        messageIsWarning: Boolean = false,
        index: Int = 11,
        offers: OffersRow? = null,
        rows: List<String> = emptyList(),
    ) {
        compose.setContent {
            HoldoutTheme {
                CohortHome(
                    h = holdout,
                    members = members,
                    stage = Stage.Active(index, todayDone),
                    now = at,
                    banked = banked,
                    offline = offline,
                    pendingDay = pendingDay,
                    autoSendTried = autoSendTried,
                    busy = busy,
                    message = message,
                    messageIsWarning = messageIsWarning,
                    onStart = { started++ },
                    onSubmit = { submitted += it },
                    onEvent = { events += it },
                    onGetTokens = { tokens++ },
                    streak = 12,
                    newPhone = newPhone,
                    canPayFees = canPayFees,
                    mayStillLand = mayStillLand,
                    checking = checking,
                    stillUnreachable = stillUnreachable,
                    onCheck = { checks++ },
                    offers = offers,
                    nudges = rows.map { NudgeRowState(it, NavEvent.Open(Screen.Claim("c"))) },
                )
            }
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onNode(hasText(s, substring = substring))

    private fun colourOf(s: String): androidx.compose.ui.graphics.Color {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(s)).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        return result.first().layoutInput.style.color
    }

    // ---- the hero's frame and header ------------------------------------------------------------------

    @Test
    fun `the Holdout's day is in the one 312 dp hero, labelled with the level and the day, no date`() {
        show()
        text("TODAY").assertIsDisplayed()
        text("HABIT · 21-DAY · DAY 12 OF 21").assertIsDisplayed()
        assertEquals(312f, compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot().height.value)
    }

    @Test
    fun `the streak chip is a button to Me`() {
        show()
        compose.onNodeWithContentDescription("12-day streak. Opens your streak on Me.").performClick()
        assertEquals(listOf<NavEvent>(NavEvent.SelectTab(Tab.Me)), events)
    }

    @Test
    fun `on chain the chip shows the day extended`() {
        show(todayDone = true)
        compose.onNodeWithContentDescription("12-day streak, extended today. Opens your streak on Me.").assertExists()
    }

    // ---- the crowd strip: the hero's footer ----------------------------------------------------------

    @Test
    fun `the crowd is the hero's footer strip - standing of started, the member's share - and opens the Holdout's screen`() {
        show()
        // Pool 975 SKR over 5,000 standing: 243.75 for the member.
        text("4 of 5 still standing · your share 243.75 SKR").assertIsDisplayed()
        text("4 of 5 still standing", substring = true).performClick()
        assertEquals(listOf<NavEvent>(NavEvent.Open(Screen.Detail(h.address.base58()))), events)
    }

    @Test
    fun `the strip sits inside the hero frame`() {
        show()
        val hero = compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot()
        val strip = text("4 of 5 still standing", substring = true).getUnclippedBoundsInRoot()
        assertTrue(strip.top >= hero.top && strip.bottom <= hero.bottom, "strip $strip in hero $hero")
    }

    @Test
    fun `the counter, the pool card and the boost line are the Holdout screen's, not Today's`() {
        show()
        text("Prize pool").assertDoesNotExist()
        text("Your share now").assertDoesNotExist()
        text("if today's standing holds").assertDoesNotExist()
        text("boost", substring = true).assertDoesNotExist()
        text("STILL STANDING").assertDoesNotExist()
        compose.onNodeWithContentDescription("4 of 5, still standing").assertDoesNotExist()
        text("≈", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the strip says who dropped in the last day, under its line`() {
        // key(5) completed 10 days: its day-11 deadline (start + 11 days - 1) is within the last day of `now`.
        show()
        text("1 dropped since yesterday").assertIsDisplayed()
    }

    @Test
    fun `the strip leaves the share out while the roster is not read`() {
        show(members = null)
        text("5 of 5 still standing").assertIsDisplayed()
        text("your share", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the strip leaves the share out while nobody has dropped and there is nothing to share`() {
        val allStanding = roster.map { it.copy(daysCompleted = 11) }
        val noPool = h.copy(cohort = cohort.copy(pool = 0uL), league = h.league?.copy(boostReserve = 0uL))
        show(holdout = noPool, members = allStanding)
        text("5 of 5 still standing").assertIsDisplayed()
        text("your share", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing dropped, nothing said`() {
        show(members = roster.take(4))
        text("dropped since yesterday", substring = true).assertDoesNotExist()
    }

    // ---- the day, state by state ------------------------------------------------------------------------

    @Test
    fun `to do - 25 push-ups, the bars empty, it counts as the Daily 25 too, and Start`() {
        show()
        text("25").assertIsDisplayed()
        text("push-ups").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 3 blocks banked").assertExists()
        text("Counts as your Daily 25 too.").assertIsDisplayed()
        text("Start").performClick()
        assertEquals(1, started)
        text("Start today's 25").assertDoesNotExist()
    }

    @Test
    fun `part-way - what is left, the first bar, what is banked for good and Keep going`() {
        show(banked = 10)
        text("15").assertIsDisplayed()
        text("to go").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 blocks banked").assertExists()
        text("10 banked for good").assertIsDisplayed()
        text("Keep going").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the last 10 minutes warn in words, that unfinished reps won't count, and never refuse to start`() {
        show(banked = 10, at = start + 12 * day - 1 - 510)
        text("15").assertIsDisplayed()
        text("still needed").assertIsDisplayed()
        text("Under 10 minutes left. Unfinished reps won't count.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Under 10 minutes left, 8m 30s").assertExists()
        text("Keep going").assertIsEnabled()
        text("Keep going").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the time left sits in the hero header while the day is open`() {
        show()
        text("left").assertExists()
        compose.onNodeWithContentDescription("4h 06m left").assertExists()
    }

    @Test
    fun `done and online, the send under way - the wallet is out and the button says so`() {
        show(pendingDay = 11, busy = true)
        text("done").assertIsDisplayed()
        text("Waiting for your wallet…").assertIsNotEnabled()
        text("Approve it in your wallet to put day 12 on chain.").assertIsDisplayed()
    }

    @Test
    fun `done and the send did not land - tap to submit day 12`() {
        show(pendingDay = 11, autoSendTried = true)
        text("Counted on this phone. It counts once it is on chain.").assertIsDisplayed()
        text("Submit day 12").performClick()
        assertEquals(listOf(11), submitted)
    }

    @Test
    fun `done and a send that may still land - Submit is shut and the day says why`() {
        show(pendingDay = 11, autoSendTried = true, mayStillLand = true)
        text("Day 12 may still land. Sending opens again once devnet answers.").assertIsDisplayed()
        text("Submit day 12").assertIsNotEnabled()
    }

    @Test
    fun `done but short of SOL - Get test tokens is the button and the sub line says why`() {
        show(pendingDay = 11, canPayFees = false)
        text("done").assertIsDisplayed()
        text("Not enough devnet SOL to put day 12 on chain.").assertIsDisplayed()
        text("Submit day 12").assertDoesNotExist()
        text("Get test tokens").performClick()
        assertEquals(1, tokens)
        assertEquals(emptyList(), submitted)
    }

    @Test
    fun `submitted - on chain, the bars full, and day 13 opens in a countdown instead of a deadline`() {
        show(todayDone = true)
        text("on chain").assertIsDisplayed()
        compose.onNodeWithContentDescription("3 of 3 blocks banked").assertExists()
        text("Streak +1. It counted as your Daily 25.").assertIsDisplayed()
        text("Day 13 opens in 4h 06m.").assertIsDisplayed()
        text("Start").assertDoesNotExist()
        text("left").assertDoesNotExist()
        text("Next day opens", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the last day on chain says it was the last, never that the next day opens`() {
        show(todayDone = true, index = 20, at = start + 21 * day - 14_760 - 1)
        text("That was the last day. It settles soon after the end, then your claim is ready.").assertIsDisplayed()
        text("opens in", substring = true).assertDoesNotExist()
        text("left").assertDoesNotExist()
    }

    @Test
    fun `the landed day's message is not repeated under the done card`() {
        show(todayDone = true, message = "Day 12 is on chain.", messageIsWarning = false)
        text("on chain").assertIsDisplayed()
        text("Day 12 is on chain.").assertDoesNotExist()
    }

    @Test
    fun `a warning from the last action still gets its line`() {
        show(message = "Declined in your wallet.", messageIsWarning = true)
        text("Declined in your wallet.").assertIsDisplayed()
    }

    // ---- a new phone ----------------------------------------------------------------------------------

    @Test
    fun `a new phone says nothing is banked on this device, with the Holdout's own target, and keeps Start`() {
        show(newPhone = true)
        text("No banked reps on this device today. 25 to go.").assertIsDisplayed()
        text("Start").assertIsDisplayed()
    }

    @Test
    fun `the new-phone line takes the target from the Holdout`() {
        show(newPhone = true, holdout = h.copy(cohort = cohort.copy(repsPerDay = 15)))
        text("No banked reps on this device today. 15 to go.").assertIsDisplayed()
    }

    @Test
    fun `the new-phone line is not drawn on a phone that is not new`() {
        show()
        text("No banked reps on this device", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the new-phone line leaves once today is done, whatever it was told`() {
        show(newPhone = true, todayDone = true)
        text("No banked reps on this device", substring = true).assertDoesNotExist()
    }

    // ---- the rows group and the quiet offers row ------------------------------------------------------

    @Test
    fun `the rows group sits after the hero with the section gap above it`() {
        show(rows = listOf("Claim 1,412 SKR"))
        val hero = compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot()
        val row = text("Claim 1,412 SKR").getUnclippedBoundsInRoot()
        assertEquals(Spacing.betweenSections.value, (row.top - hero.bottom).value, 0.5f)
    }

    @Test
    fun `while a Holdout runs the offers are one quiet row in the secondary colour, and it opens Holdouts`() {
        show(offers = OffersRow(open = 2, from = "9,000 SKR"))
        val line = "2 open to join · from 9,000 SKR"
        text(line).assertIsDisplayed()
        assertEquals(HoldoutColors.TextSecondary, colourOf(line))
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(line)).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(emptyList(), layout.first().layoutInput.text.spanStyles.filter { it.item.color != Color.Unspecified }, "no accent or money-colour span: the amount is in the mono face only")
        text(line).performClick()
        assertEquals(listOf<NavEvent>(NavEvent.SelectTab(Tab.Holdouts)), events)
    }

    @Test
    fun `no offers row without offers, and no cards ever`() {
        show()
        text("open to join", substring = true).assertDoesNotExist()
        text("Join").assertDoesNotExist()
        text("The Daily 25", substring = true).assertDoesNotExist()
        text("HOLDOUTS OPEN TO JOIN").assertDoesNotExist()
    }

    @Test
    fun `no roster or details buttons - the strip is the way to the Holdout`() {
        show()
        text("Who's still standing").assertDoesNotExist()
        text("Holdout details").assertDoesNotExist()
        text("About Holdout").assertDoesNotExist()
    }

    // ---- the devnet bar (FR-069h) ---------------------------------------------------------------------

    @Test
    fun `offline before the sitting - the bar says devnet isn't answering, the reps still count, and Retry, and Start stays`() {
        show(offline = true)
        text("${Deployment.networkName} isn't answering").assertIsDisplayed()
        text("Your reps still count on this phone.").assertIsDisplayed()
        text("Retry").performClick()
        assertEquals(1, checks)
        text("Start").assertIsEnabled()
    }

    @Test
    fun `the bar sits directly under the header, above the hero`() {
        show(offline = true)
        val header = text("TODAY").getUnclippedBoundsInRoot()
        val bar = text("${Deployment.networkName} isn't answering").getUnclippedBoundsInRoot()
        val hero = compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot()
        assertTrue(bar.top > header.bottom, "bar ${bar.top} under header ${header.bottom}")
        assertTrue(bar.bottom <= hero.top && hero.top - bar.bottom < 80.dp, "bar ${bar.bottom}, hero ${hero.top}")
    }

    @Test
    fun `no can't-be-reached row, no Check again button and no old notice anywhere`() {
        show(offline = true)
        text("can't be reached", substring = true).assertDoesNotExist()
        text("Check again").assertDoesNotExist()
        text("You can still do today's reps", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a failed check says devnet still isn't answering`() {
        show(offline = true, stillUnreachable = true)
        text("${Deployment.networkName} still isn't answering").assertIsDisplayed()
    }

    @Test
    fun `while checking the bar reads Checking and cannot be pressed`() {
        show(offline = true, checking = true)
        text("Checking…").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `done and offline - the disabled waiting button over what happens next, and the bar`() {
        show(pendingDay = 11, offline = true)
        text("Done — waiting for network").assertIsDisplayed().assertIsNotEnabled()
        text("Saved on this phone. It sends when devnet answers.").assertIsDisplayed()
        text("${Deployment.networkName} isn't answering").assertIsDisplayed()
        text("Retry").performClick()
        assertEquals(1, checks)
        text("4h 06m").assertIsDisplayed()
        assertEquals(emptyList(), submitted)
    }

    @Test
    fun `on chain and offline the bar still tells the figures are an earlier read`() {
        show(todayDone = true, offline = true)
        text("${Deployment.networkName} isn't answering").assertIsDisplayed()
        text("Your reps still count on this phone.").assertIsDisplayed()
    }

    @Test
    fun `the bar is not drawn while devnet answers`() {
        show()
        text("isn't answering", substring = true).assertDoesNotExist()
    }

    // ---- a Hackathon round ----------------------------------------------------------------------------

    @Test
    fun `a Hackathon Holdout wears its badge in the header and its own name on the hero`() {
        val test = h.copy(cohort = cohort.copy(leagueId = LeagueParams.TEST_LEAGUE_MIN, dayLength = 600, durationDays = 3, repsPerDay = 5))
        show(holdout = test, at = start + 1 * 600 + 100, members = null, index = 1)
        text("DEVNET COHORT · 10-MINUTE DAYS").assertIsDisplayed()
        text("HACKATHON · 3-DAY · DAY 2 OF 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 1 blocks banked").assertExists()
        assertEquals(312f, compose.onNodeWithTag("day-hero").getUnclippedBoundsInRoot().height.value)
    }
}
