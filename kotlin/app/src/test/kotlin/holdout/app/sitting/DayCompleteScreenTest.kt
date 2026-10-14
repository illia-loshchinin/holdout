package holdout.app.sitting

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Board 19, day complete: the Holdout card and "Submit day 12", the Daily 25's streak and upsell, the offline variants (FR-024b). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class DayCompleteScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var finished = 0
    private var offers = 0

    private val header = "HABIT · 21-DAY · DAY 12 OF 21"

    private fun holdoutUi(
        left: Long? = 4 * 3_600L + 6 * 60,
        onChain: Boolean = false,
        note: Pair<String, String>? = null,
    ) = DayCompleteUi(
        header = header,
        reps = 25,
        holdout = HoldoutDayCard(dayNumber = 12, secondsLeft = left, onChain = onChain, note = note),
        streak = null,
        offer = null,
        button = DayCompleteUi.buttonFor(holdoutDay = 12, note = note, onChain = onChain),
    )

    private fun dailyUi(
        streak: StreakAfter? = StreakAfter(days = 13, grew = true),
        offer: DayCompleteOffer? = DayCompleteOffer("Stake on yourself next", "Spark · 7-day · joining closes in 1d 3h"),
    ) = DayCompleteUi(
        header = "THE DAILY 25",
        reps = 25,
        holdout = null,
        streak = streak,
        offer = offer,
        button = DayCompleteUi.buttonFor(holdoutDay = null, note = null, onChain = false),
    )

    private fun show(ui: DayCompleteUi) {
        finished = 0
        offers = 0
        compose.setContent { HoldoutTheme { DayCompleteContent(ui, onFinish = { finished++ }, onOffer = { offers++ }) } }
    }

    // ---- a Holdout day ------------------------------------------------------------------------------

    @Test
    fun `a Holdout day shows the header and the celebration's headline`() {
        show(holdoutUi())
        compose.onNodeWithText(header).assertIsDisplayed()
        compose.onNodeWithText("Nailed it.").assertIsDisplayed()
        compose.onNodeWithText("25 push-ups done").assertIsDisplayed()
    }

    // ---- the celebration (FR-069g) ------------------------------------------------------------------

    @Test
    fun `a Holdout day's page holds the celebration in a 168 dp square`() {
        show(holdoutUi())
        compose.onNodeWithTag("celebration").assertWidthIsEqualTo(168.dp).assertHeightIsEqualTo(168.dp)
    }

    @Test
    fun `the Daily 25's page holds the same celebration`() {
        show(dailyUi())
        compose.onNodeWithTag("celebration").assertWidthIsEqualTo(168.dp).assertHeightIsEqualTo(168.dp)
        compose.onNodeWithText("Nailed it.").assertIsDisplayed()
        compose.onNodeWithText("25 push-ups done").assertIsDisplayed()
    }

    @Test
    fun `the headline says the day's own reps`() {
        show(dailyUi().copy(reps = 5))
        compose.onNodeWithText("5 push-ups done").assertIsDisplayed()
    }

    @Test
    fun `the count is no longer a big number with DONE under it`() {
        show(holdoutUi())
        compose.onNodeWithText("25").assertDoesNotExist()
        compose.onNodeWithText("DONE").assertDoesNotExist()
    }

    @Test
    fun `the buttons are enabled from the first frame, before the ring has drawn`() {
        compose.mainClock.autoAdvance = false
        show(holdoutUi())

        compose.onNodeWithText("Submit day 12").assertIsEnabled()
        compose.onNodeWithText("Submit day 12").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `the offer is tappable from the first frame too`() {
        compose.mainClock.autoAdvance = false
        show(dailyUi())

        compose.onNodeWithText("Stake on yourself next").performClick()
        assertEquals(1, offers)
    }

    @Test
    fun `with animations on the ring starts empty and draws itself, then the tick`() {
        lateinit var progress: CelebrationProgress
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HoldoutTheme {
                progress = rememberCelebrationProgress()
                DayCompleteContent(holdoutUi(), {}, {}, celebration = progress)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, progress.ring.value, "nothing is drawn at the first frame")
        assertEquals(0f, progress.tick.value)

        compose.mainClock.advanceTimeBy(300)
        assertTrue(progress.ring.value in 0.05f..0.95f, "the ring is part-way at 300 ms: ${progress.ring.value}")
        assertEquals(0f, progress.tick.value, "the tick has not started")

        compose.mainClock.advanceTimeBy(2_500)
        assertEquals(1f, progress.ring.value)
        assertEquals(1f, progress.tick.value)
        assertEquals(1f, progress.rise.value)
        assertEquals(1f, progress.burst.value)
    }

    @Test
    fun `with animations off the ring and the tick are drawn complete at the first frame`() {
        val off = AnimationsOff()
        off.setScale(0f)
        try {
            lateinit var progress: CelebrationProgress
            compose.mainClock.autoAdvance = false
            compose.setContent {
                HoldoutTheme {
                    progress = rememberCelebrationProgress()
                    DayCompleteContent(holdoutUi(), {}, {}, celebration = progress)
                }
            }
            compose.mainClock.advanceTimeByFrame()

            assertEquals(1f, progress.ring.value, "the ring is complete")
            assertEquals(1f, progress.tick.value, "the tick is complete")
            assertEquals(1f, progress.rise.value, "the headline is in place")
            assertEquals(1f, progress.burst.value, "the burst is over")
            compose.onNodeWithText("Nailed it.").assertIsDisplayed()
        } finally {
            off.setScale(1f)
        }
    }

    @Test
    fun `the burst is ten dots in the two accent colours, none visible before or after it`() {
        assertEquals(10, celebrationDots(0.4f).size)
        assertEquals(setOf(HoldoutColors.Success, HoldoutColors.Accent), celebrationDots(0.4f).map { it.color }.toSet())
        assertTrue(celebrationDots(0f).all { it.alpha == 0f }, "invisible before the burst")
        assertTrue(celebrationDots(1f).all { it.alpha == 0f }, "gone when it is over")
    }

    @Test
    fun `the dots leave one after another and fly out from the ring's centre`() {
        val early = celebrationDots(0.25f)
        assertTrue(early.any { it.alpha > 0f }, "some are in flight")

        fun distance(dot: CelebrationDot) = kotlin.math.hypot(dot.dx, dot.dy)
        assertTrue(distance(early.first()) > distance(early.last()), "the first dot is ahead of the last: ${early.map { distance(it) }}")
        assertTrue(celebrationDots(0.99f).isNotEmpty() && celebrationDots(0.99f).all { distance(it) < 130f }, "they stay near the ring")
    }

    @Test
    fun `the card says counted on this phone, not on chain yet, and the time left to submit`() {
        show(holdoutUi())

        compose.onNodeWithText("Counted on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Not on chain yet.").assertIsDisplayed()
        compose.onNodeWithText("Left to submit").assertIsDisplayed()
        compose.onNodeWithContentDescription("4h 06m left").assertExists()
    }

    @Test
    fun `the button reads Submit day 12 and submits`() {
        show(holdoutUi())

        compose.onNodeWithText("Submit day 12").performClick()

        assertEquals(1, finished)
    }

    @Test
    fun `no calendar date is in the header`() {
        show(holdoutUi())
        compose.onNodeWithText("from", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `the time left to submit is hidden while the chain is unread`() {
        show(holdoutUi(left = null))
        compose.onNodeWithText("Left to submit").assertDoesNotExist()
    }

    @Test
    fun `a day already on chain says so and leaves nothing to submit`() {
        show(holdoutUi(onChain = true))

        compose.onNodeWithText("Counted on this phone.").assertIsDisplayed()
        compose.onNodeWithText("On chain.").assertIsDisplayed()
        compose.onNodeWithText("Not on chain yet.").assertDoesNotExist()
        compose.onNodeWithText("Left to submit").assertDoesNotExist()
        compose.onNodeWithText("Submit day 12").assertDoesNotExist()
        compose.onNodeWithText("Back to today").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `offline - done, not sent yet - keeps its note and goes back to today`() {
        val note = "Done — not sent yet" to "Devnet can't be reached, so day 12 isn't on chain yet. Your 25 are saved on this phone."
        show(holdoutUi(note = note))

        compose.onNodeWithText("Done — not sent yet").assertIsDisplayed()
        compose.onNodeWithText(note.second).assertIsDisplayed()
        compose.onNodeWithText("Not on chain yet.").assertDoesNotExist()
        compose.onNodeWithText("Submit day 12").assertDoesNotExist()
        compose.onNodeWithText("Back to today").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `too late to send keeps its note and sends nothing`() {
        val note = "Done — too late to send" to "Day 12's deadline has passed, so it can no longer be sent. Your 25 are saved on this phone."
        show(holdoutUi(note = note))

        compose.onNodeWithText("Done — too late to send").assertIsDisplayed()
        compose.onNodeWithText(note.second).assertIsDisplayed()
        compose.onNodeWithText("Left to submit").assertDoesNotExist()
        compose.onNodeWithText("Back to today").assertIsDisplayed()
    }

    @Test
    fun `a Holdout day has no streak row and no upsell`() {
        show(holdoutUi())
        compose.onNodeWithText("Stake on yourself next").assertDoesNotExist()
        compose.onNodeWithText("day streak", substring = true).assertDoesNotExist()
    }

    // ---- the Daily 25 -------------------------------------------------------------------------------

    @Test
    fun `the Daily 25 shows the streak with its flame and the plus one`() {
        show(dailyUi())

        compose.onNodeWithText("THE DAILY 25").assertIsDisplayed()
        compose.onNodeWithContentDescription("13-day streak").assertExists()
        compose.onNodeWithText("day streak · +1").assertIsDisplayed()
    }

    @Test
    fun `no plus one when the streak did not grow`() {
        show(dailyUi(streak = StreakAfter(days = 13, grew = false)))
        compose.onNodeWithText("day streak").assertIsDisplayed()
        compose.onNodeWithText("+1", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the Daily 25 button is Done`() {
        show(dailyUi())
        compose.onNodeWithText("Done", ignoreCase = false).performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `the upsell shows level and a countdown, never a date, and opens the stake`() {
        show(dailyUi())

        compose.onNodeWithText("Stake on yourself next").assertIsDisplayed()
        compose.onNodeWithText("Spark · 7-day · joining closes in 1d 3h").assertIsDisplayed()
        compose.onNodeWithText("Stake on yourself next").performClick()

        assertEquals(1, offers)
        assertEquals(0, finished)
    }

    @Test
    fun `no upsell offered, none drawn`() {
        show(dailyUi(offer = null))
        compose.onNodeWithText("Stake on yourself next").assertDoesNotExist()
    }

    @Test
    fun `a comeback offer takes the upsell's place`() {
        show(dailyUi(offer = DayCompleteOffer("Your comeback is open", "Get back 250 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h.")))

        compose.onNodeWithText("Your comeback is open").assertIsDisplayed()
        compose.onNodeWithText("Stake on yourself next").assertDoesNotExist()
    }

    @Test
    fun `without a streak - Lab, or a day not dated yet - there is no streak row`() {
        show(dailyUi(streak = null, offer = null))
        compose.onNodeWithText("day streak", substring = true).assertDoesNotExist()
    }

    // ---- a streak milestone (Gap 2, S6) ---------------------------------------------------------------

    @Test
    fun `a day that reaches seven shows the milestone state, the headline and the line, in place of the plain streak line`() {
        show(dailyUi(streak = StreakAfter(days = 7, grew = true)))

        compose.onNodeWithContentDescription("7-day streak, milestone").assertExists()
        compose.onNodeWithText("7-day streak").assertIsDisplayed()
        compose.onNodeWithText("A week, every day. That's how habits start.").assertIsDisplayed()
        compose.onNodeWithText("day streak · +1").assertDoesNotExist()
        compose.onNodeWithText("day streak").assertDoesNotExist()
    }

    @Test
    fun `the milestone is one row - a small flame beside the headline - with the line under it`() {
        show(dailyUi(streak = StreakAfter(days = 7, grew = true)))

        val flame = compose.onNodeWithTag("milestone-flame", useUnmergedTree = true).getBoundsInRoot()
        val headline = compose.onNodeWithText("7-day streak", useUnmergedTree = true).getBoundsInRoot()
        val line = compose.onNodeWithText("A week, every day. That's how habits start.").getBoundsInRoot()

        assertTrue(flame.right <= headline.left, "the flame is beside the headline, on its left: $flame $headline")
        assertTrue(flame.top < headline.bottom && flame.bottom > headline.top, "on the same row: $flame $headline")
        assertTrue(line.top >= headline.bottom, "the line is under the headline: $line $headline")
    }

    @Test
    fun `the milestone's flame is small - the old 72 dp accent ring is not drawn`() {
        show(dailyUi(streak = StreakAfter(days = 7, grew = true)))

        val flame = compose.onNodeWithTag("milestone-flame", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(flame.width <= 28.dp && flame.height <= 28.dp, "a small flame, not a ring: $flame")
    }

    @Test
    fun `a Holdout day has no milestone flame`() {
        show(holdoutUi())
        compose.onNodeWithTag("milestone-flame", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a hundredth multiple shows the still-here line`() {
        show(dailyUi(streak = StreakAfter(days = 300, grew = true)))

        compose.onNodeWithText("300-day streak").assertIsDisplayed()
        compose.onNodeWithText("300 days. Still here, still going.").assertIsDisplayed()
    }

    @Test
    fun `a plain day keeps the plain streak line and no milestone`() {
        show(dailyUi(streak = StreakAfter(days = 8, grew = true)))

        compose.onNodeWithContentDescription("8-day streak").assertExists()
        compose.onNodeWithContentDescription("8-day streak, milestone").assertDoesNotExist()
        compose.onNodeWithText("day streak · +1").assertIsDisplayed()
        compose.onNodeWithText("8-day streak").assertDoesNotExist()
    }

    @Test
    fun `a streak already at a milestone that did not grow today shows the plain line`() {
        show(dailyUi(streak = StreakAfter(days = 7, grew = false)))

        compose.onNodeWithContentDescription("7-day streak, milestone").assertDoesNotExist()
        compose.onNodeWithText("day streak").assertIsDisplayed()
    }

    @Test
    fun `the milestone page still has the upsell and its button`() {
        show(dailyUi(streak = StreakAfter(days = 7, grew = true)))

        compose.onNodeWithText("Stake on yourself next").assertIsDisplayed()
        compose.onNodeWithText("Done", ignoreCase = false).performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `the finished screen of Holdout Lab is the celebration and its button`() {
        show(DayCompleteUi(header = null, reps = 25, holdout = null, streak = null, offer = null, button = "Back to Lab"))

        compose.onNodeWithTag("celebration").assertExists()
        compose.onNodeWithText("25 push-ups done").assertIsDisplayed()
        compose.onNodeWithText("Back to Lab").performClick()
        assertEquals(1, finished)
    }

    // ---- the button label ---------------------------------------------------------------------------

    @Test
    fun `the button label follows the state - Submit day 12, Back to today, Done`() {
        assertEquals("Submit day 12", DayCompleteUi.buttonFor(holdoutDay = 12, note = null, onChain = false))
        assertEquals("Back to today", DayCompleteUi.buttonFor(holdoutDay = 12, note = "a" to "b", onChain = false))
        assertEquals("Back to today", DayCompleteUi.buttonFor(holdoutDay = 12, note = null, onChain = true))
        assertEquals("Done", DayCompleteUi.buttonFor(holdoutDay = null, note = null, onChain = false))
    }
}
