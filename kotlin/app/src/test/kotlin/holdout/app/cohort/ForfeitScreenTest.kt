package holdout.app.cohort

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import holdout.app.chain.Holdout
import holdout.app.stake.StakeText
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.Journey
import holdout.core.MembershipSnapshot
import holdout.core.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H6, the forfeit (board 9): the facts, the Stake lost row, the streak, and each state of the comeback offer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class ForfeitScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    // A 21-day Habit that has run 11 days; I missed day 12.
    private val c = Fx.cohort(days = 21, joined = 20, pool = 4_820_000_000uL)
    private val now = c.start + 11 * Fx.DAY + 3_600
    private val h = Fx.holdout(c, Fx.member(days = 11, state = MembershipSnapshot.State.Forfeited))
    private val stage = Stage.Forfeited(daysLasted = 11, recorded = true)

    /** The Habit round that opens next, and a Habit I could join in it. */
    private val nextCohort = Fx.cohort(start = now + 2 * Fx.DAY + 4 * 3_600, days = 21, joined = 9).copy(joinCutoff = now + 27 * 3_600)
    private val next = Comeback.Entry(Fx.holdout(nextCohort).address, nextCohort, null)
    private val round = Comeback.Round(start = nextCohort.start, opensAt = nextCohort.start - 7 * Fx.DAY, entry = next, status = CohortStatus.Open)
    private val forfeited = Comeback.Entry(h.address, c, h.membership)

    private fun offer(held: Comeback.Round? = null, upcoming: Comeback.Round? = null, rounds: List<Comeback.Round> = listOf(round)): Comeback.Offer {
        val missed = c.start + 12 * Fx.DAY - 1
        return Comeback.Offer(slot = 1, lost = 1_250_000_000uL, tier = 1, missed = missed, until = c.start + 26 * Fx.DAY, forfeited = forfeited, rounds = rounds, held = held, upcoming = upcoming)
    }

    private class Taps {
        var daily = 0
        var join = 0
        var next = 0
        var place = 0
        var back = 0
    }

    private fun show(
        taps: Taps = Taps(),
        stage: Stage.Forfeited = this.stage,
        streak: Int = 12,
        offer: Comeback.Offer? = null,
        blocker: Journey.JoinBlocker? = null,
        nextOpen: Boolean = true,
        members: List<MembershipSnapshot>? = Fx.roster(standing = 19, out = 1, lasted = 11, going = 11),
        place: Boolean = false,
        h: Holdout = this.h,
        doneOnPhone: Boolean = true,
        daily25Done: Boolean = false,
    ) {
        compose.setContent {
            HoldoutTheme {
                ForfeitScreen(
                    h = h,
                    stage = stage,
                    streak = streak,
                    onDaily25 = { taps.daily++ },
                    onNextHoldout = if (nextOpen) ({ taps.next++ }) else null,
                    onBack = { taps.back++ },
                    offer = offer,
                    now = now,
                    joinBlocker = blocker,
                    onJoin = { taps.join++ },
                    members = members,
                    onSeePlace = if (place) ({ taps.place++ }) else null,
                    doneOnPhone = doneOnPhone,
                    daily25Done = daily25Done,
                )
            }
        }
    }

    @Test
    fun `a day done on this phone but not sent says it did not reach the chain in time`() {
        show(doneOnPhone = true)

        compose.onNodeWithText("HABIT · 21-DAY · DAY 12 OF 21").assertIsDisplayed()
        compose.onNodeWithText("You missed day 12.").assertIsDisplayed()
        compose.onNodeWithText("It didn't reach the chain in time. Your run has ended.").assertIsDisplayed()
    }

    @Test
    fun `a day never done says the member did not finish it before its deadline`() {
        show(doneOnPhone = false)

        compose.onNodeWithText("You missed day 12.").assertIsDisplayed()
        compose.onNodeWithText("You didn't finish day 12 before its deadline. Your run has ended.").assertIsDisplayed()
        compose.onNodeWithText("It didn't reach the chain in time. Your run has ended.").assertDoesNotExist()
    }

    @Test
    fun `the cause sentence by whether the day was done on the phone`() {
        assertEquals("It didn't reach the chain in time. Your run has ended.", ForfeitText.body(daysLasted = 11, doneOnPhone = true))
        assertEquals("You didn't finish day 12 before its deadline. Your run has ended.", ForfeitText.body(daysLasted = 11, doneOnPhone = false))
    }

    @Test
    fun `the Stake lost row carries the stake and the fee sentence for this level`() {
        show()

        compose.onNodeWithText("Stake lost").assertIsDisplayed()
        compose.onNodeWithText("1,250 SKR").assertIsDisplayed()
        compose.onNodeWithText("It goes 70 % to this Holdout's prize pool, 10 % to Habit · 21-day boosts and comebacks, 20 % to the project. If nobody finishes, the pool goes to the project too.").assertIsDisplayed()
    }

    @Test
    fun `before the record catches up it says the forfeit is final from the deadline`() {
        show(stage = Stage.Forfeited(11, recorded = false))
        compose.onNodeWithText("Final as of the deadline. The chain shows it shortly.").assertIsDisplayed()
    }

    @Test
    fun `recorded - no pending line`() {
        show()
        compose.onNodeWithText("Final as of the deadline. The chain shows it shortly.").assertDoesNotExist()
    }

    @Test
    fun `the summary names the crowd and the pool and where the member fell out`() {
        show()

        compose.onNodeWithContentDescription("19 of 20 still standing; prize pool 4,820 SKR; you were out on day 12 of 21.").assertExists()
        compose.onNodeWithText("19 / 20").assertIsDisplayed()
        compose.onNodeWithText("prize pool").assertIsDisplayed()
        compose.onNodeWithText("Day 1").assertIsDisplayed()
        compose.onNodeWithText("You: out on day 12").assertIsDisplayed()
        compose.onNodeWithText("Day 21").assertIsDisplayed()
    }

    @Test
    fun `before the roster is read the strip stands alone`() {
        show(members = null)

        compose.onNodeWithText("You: out on day 12").assertIsDisplayed()
        compose.onNodeWithText("prize pool").assertDoesNotExist()
    }

    @Test
    fun `the streak is still alive - one quiet line, no card and no body sentence`() {
        show(streak = 12)

        val note = compose.onNodeWithText("Your 12-day streak is still alive.")
        note.assertExists()
        compose.onNodeWithText("A run can end; your streak didn't. That's the habit, and it's yours.").assertDoesNotExist()
        // No card around it: it starts where the headline starts, at the screen's gutter, not inset by a card's padding.
        assertEquals(
            compose.onNodeWithText("You missed day 12.").getBoundsInRoot().left,
            note.getBoundsInRoot().left,
        )
    }

    @Test
    fun `the order - Stake lost, the comeback with its Join, the streak note, then the Daily 25 button`() {
        show(offer = offer(), daily25Done = false)

        val tops = listOf("Stake lost", "COMEBACK", "Join the next Habit · 21-day", "Your 12-day streak is still alive.", "Do today's Daily 25")
            .map { compose.onNodeWithText(it).getBoundsInRoot().top }
        assertEquals(tops.sorted(), tops, "the sections run in this order")
        assertEquals(tops.distinct(), tops)
    }

    @Test
    fun `today's Daily 25 not done - it is offered as a secondary button, with the same action`() {
        val taps = Taps()
        show(taps, offer = offer(), daily25Done = false)

        compose.onNodeWithText("Do today's Daily 25").performClick()
        compose.onNodeWithText("Or keep the streak: today's Daily 25").assertDoesNotExist()
        assertEquals(1, taps.daily)
    }

    @Test
    fun `today's Daily 25 done - no Daily 25 offer at all`() {
        show(offer = offer(), daily25Done = true)

        compose.onNodeWithText("Do today's Daily 25").assertDoesNotExist()
        compose.onNodeWithText("Join the next Habit · 21-day").assertIsDisplayed()
        compose.onNodeWithText("Your 12-day streak is still alive.").assertExists()
    }

    @Test
    fun `no streak yet - it is untouched`() {
        show(streak = 0)
        compose.onNodeWithText("Your streak is untouched.").assertIsDisplayed()
    }

    @Test
    fun `back goes to Holdouts`() {
        val taps = Taps()
        show(taps)

        compose.onNodeWithContentDescription("Back to Holdouts").performClick()

        assertEquals(1, taps.back)
    }

    @Test
    fun `the comeback timing sets only its two countdowns in the mono face`() {
        show(offer = offer())

        val node = compose.onNodeWithText("Starts in 2d 4h · joining closes in 1d 3h")
        val result = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        val text = result.first().layoutInput.text
        val mono = text.spanStyles.filter { it.item.fontFamily == HoldoutFonts.Mono }
        assertEquals(listOf("2d 4h", "1d 3h"), mono.map { text.text.substring(it.start, it.end) })
        assertNotEquals(HoldoutFonts.Mono, result.first().layoutInput.style.fontFamily, "the base style is not mono")
    }

    @Test
    fun `a round to join - win back up to, the timing, Join the next, and the quiet Daily 25`() {
        val taps = Taps()
        show(taps, offer = offer())

        compose.onNodeWithText("COMEBACK").assertIsDisplayed()
        compose.onNodeWithText("Win back up to 250 SKR").assertIsDisplayed()
        compose.onNodeWithText("Finish a Habit · 21-day starting within 14 days: up to 250 SKR comes back.").assertIsDisplayed()
        compose.onNodeWithText("Starts in 2d 4h · joining closes in 1d 3h").assertIsDisplayed()

        compose.onNodeWithText("Join the next Habit · 21-day").performClick()
        compose.onNodeWithText("Do today's Daily 25").performClick()

        assertEquals(1, taps.join)
        assertEquals(1, taps.daily)
    }

    @Test
    fun `a place held in the lobby - You're in it, and See your place`() {
        val taps = Taps()
        val placeCohort = nextCohort
        val placeEntry = Comeback.Entry(Fx.holdout(placeCohort).address, placeCohort, Fx.member(days = 0))
        val held = Comeback.Round(placeCohort.start, placeCohort.start - 7 * Fx.DAY, placeEntry, CohortStatus.Open)
        show(taps, offer = offer(held = held, rounds = listOf(held)), place = true)

        compose.onNodeWithText("You're in the Habit · 21-day starting in 2d 4h. Finish it and up to 250 SKR of the stake you lost comes back.").assertIsDisplayed()
        compose.onNodeWithText("See your place").performClick()
        compose.onNodeWithText("Do today's Daily 25").performClick()

        assertEquals(1, taps.place)
        assertEquals(1, taps.daily)
        assertEquals(0, taps.join)
    }

    @Test
    fun `a round not opened yet - says when it opens, no Join, the Daily 25 is the button`() {
        val taps = Taps()
        val upcoming = Comeback.Round(start = now + 2 * Fx.DAY + 4 * 3_600, opensAt = now + 6 * 3_600, entry = null, status = null)
        show(taps, offer = offer(upcoming = upcoming, rounds = listOf(upcoming)))

        compose.onNodeWithText("The next Habit · 21-day opens for joining in 6h 00m and starts in 2d 4h. Finish it and up to 250 SKR of the stake you lost comes back.").assertIsDisplayed()
        compose.onNodeWithText("Join the next Habit · 21-day").assertDoesNotExist()
        compose.onNodeWithText("Do today's Daily 25").performClick()
        assertEquals(1, taps.daily)
    }

    @Test
    fun `a round opening shortly - never a countdown stuck at zero`() {
        val upcoming = Comeback.Round(start = now + 27 * 3_600, opensAt = now - 60, entry = null, status = null)
        show(offer = offer(upcoming = upcoming, rounds = listOf(upcoming)))

        compose.onNodeWithText("The next Habit · 21-day opens for joining shortly and starts in 1d 3h. Finish it and up to 250 SKR of the stake you lost comes back.").assertIsDisplayed()
    }

    @Test
    fun `a lobby place elsewhere - leave it first, no Join`() {
        show(offer = offer(), blocker = Journey.JoinBlocker.LobbyPlaceTaken)

        compose.onNodeWithText("You hold a place in a Holdout that starts later. Leave it first to join one that can bring your stake back.").assertIsDisplayed()
        compose.onNodeWithText("Join the next Habit · 21-day").assertDoesNotExist()
        compose.onNodeWithText("Do today's Daily 25").assertIsDisplayed()
    }

    @Test
    fun `a round that starts before the current one ends - says so and has no Join`() {
        show(offer = offer(), blocker = Journey.JoinBlocker.Overlaps)

        compose.onNodeWithText("This Holdout starts before your current one ends.").assertIsDisplayed()
        compose.onNodeWithText("Join the next Habit · 21-day").assertDoesNotExist()
    }

    @Test
    fun `no offer - two ways back, the next Holdout of the level first and the Daily 25 quietly`() {
        val taps = Taps()
        show(taps, offer = null)

        compose.onNodeWithText("TWO WAYS BACK").assertIsDisplayed()
        compose.onNodeWithText("Today's Daily 25 keeps your streak going. The next Habit · 21-day starts a new run.").assertIsDisplayed()
        compose.onNodeWithText("The next Habit · 21-day").performClick()
        compose.onNodeWithText("Do today's Daily 25").performClick()

        assertEquals(1, taps.next)
        assertEquals(1, taps.daily)
    }

    @Test
    fun `no offer, Daily 25 done - the next Holdout alone, no two ways back`() {
        show(offer = null, daily25Done = true)

        compose.onNodeWithText("TWO WAYS BACK").assertDoesNotExist()
        compose.onNodeWithText("The next Habit · 21-day starts a new run.").assertIsDisplayed()
        compose.onNodeWithText("Do today's Daily 25").assertDoesNotExist()
    }

    @Test
    fun `no offer and no Holdout open - only the Daily 25`() {
        val taps = Taps()
        show(taps, offer = null, nextOpen = false)

        compose.onNodeWithText("Today's Daily 25 keeps your streak going.").assertIsDisplayed()
        compose.onNodeWithText("Do today's Daily 25").performClick()
        assertEquals(1, taps.daily)
    }

    @Test
    fun `the offer copy never carries a date, a clock time or a dollar figure`() {
        val upcoming = Comeback.Round(start = now + 2 * Fx.DAY, opensAt = now + 6 * 3_600, entry = null, status = null)
        val copies = listOf(
            ForfeitText.offer(offer(), c, now, null),
            ForfeitText.offer(offer(), c, now, Journey.JoinBlocker.LobbyPlaceTaken),
            ForfeitText.offer(offer(upcoming = upcoming, rounds = listOf(upcoming)), c, now, null),
        )
        val words = copies.flatMap { listOfNotNull(it.headline, it.body, it.timing, it.after, it.notice) } + ForfeitText.body(11, true) + ForfeitText.body(11, false) + ForfeitText.headline(11)
        words.forEach { w ->
            assertFalse(Regex("""\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun|UTC|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b|\d{1,2}:\d{2}|\$|≈""").containsMatchIn(w), "\"$w\"")
        }
    }

    @Test
    fun `a short test Holdout names the boosts after a short test Holdout, as the stake sheet does`() {
        val test = Fx.cohort(joined = 10, leagueId = 100, dayLength = 600, days = 3, form = 1)
        val testHoldout = Fx.holdout(test, Fx.member(days = 1, state = MembershipSnapshot.State.Forfeited))
        show(stage = Stage.Forfeited(daysLasted = 1, recorded = true), h = testHoldout)

        val forfeit = "It goes 70 % to this Holdout's prize pool, 10 % to short test Holdout boosts and comebacks, " +
            "20 % to the project. If nobody finishes, the pool goes to the project too."
        compose.onNodeWithText(forfeit).assertExists()
        // The stake sheet's sentence names the same noun.
        assertTrue("10 % to short test Holdout boosts and comebacks" in StakeText.fee(test, test = true))
    }

    @Test
    fun `a short test Holdout names the next one as a short test Holdout`() {
        assertEquals("The next short test Holdout", ForfeitText.nextHoldout(c, test = true))
        assertEquals("The next Habit · 21-day", ForfeitText.nextHoldout(c, test = false))
        assertNull(ForfeitText.offer(offer(), c, now, Journey.JoinBlocker.LobbyPlaceTaken).headline)
        assertTrue(ForfeitText.offer(offer(), c, now, null).headline!!.startsWith("Win back up to"))
    }
}
