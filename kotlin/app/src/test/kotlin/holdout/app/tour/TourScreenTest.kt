package holdout.app.tour

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.sitting.PHONE
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * B1/B2 the first-run tour (boards TourDaily, Tour, TourPlay, TourMoney,
 * TourCounting, Disclosure; FR-067, spec 004 FR-012/FR-013): six cards, "how it
 * works · N of 6", Skip on the first five goes to the sixth (the scripted-member
 * note is required on first run), Back steps back a card, Start on the last one
 * finishes, and the stake sheet's *How it works* opens it at card 2. Nothing
 * for judges, no Holdout Lab, no dates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class TourScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    private var done = 0
    private var startAt by mutableIntStateOf(1)
    private var shown = false

    /** Shows the tour opened on [startCard]; again in the same test, it opens a new one. */
    private fun show(startCard: Int = 1) {
        done = 0
        if (shown) {
            compose.runOnUiThread { startAt = startCard }
        } else {
            shown = true
            startAt = startCard
            compose.setContent { HoldoutTheme { key(startAt) { TourScreen(onDone = { done++ }, startCard = startAt) } } }
        }
        compose.waitForIdle()
    }

    private fun next() = compose.onNodeWithText("Next").performClick()

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun assertLabel(n: Int) = compose.onNodeWithText("How it works · $n of 6", ignoreCase = true).assertIsDisplayed()

    @Test
    fun `card 1 is the Daily 25, free, banked in tens, with a streak, and no rep rules`() {
        show()

        assertLabel(1)
        compose.onNodeWithText("The Daily 25").assertIsDisplayed()
        compose.onNodeWithText("25 push-ups a day. Free. Your way:").assertIsDisplayed()
        compose.onNodeWithText("All at once").assertIsDisplayed()
        compose.onNodeWithText("One sitting, all 25.").assertIsDisplayed()
        compose.onNodeWithText("At your own pace").assertIsDisplayed()
        compose.onNodeWithText("10 morning").assertExists()
        compose.onNodeWithText("10 lunch").assertExists()
        compose.onNodeWithText("5 evening").assertExists()
        compose.onNodeWithText("Every 10 are banked for good").assertIsDisplayed()
        compose.onNodeWithText("Close the app or stop mid-set — banked reps stay.").assertIsDisplayed()
        compose.onNodeWithText("Each day done grows your streak.").assertExists()
        compose.onNodeWithText("How reps are counted").assertDoesNotExist()
        compose.onNodeWithText("Habit: full push-ups from your toes.", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Card 1 of 6").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText("Test network · test token", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `card 2 is two kinds of Holdout, each with its tag, facts and rule, then the four lines`() {
        show()
        next()

        assertLabel(2)
        compose.onNodeWithContentDescription("Card 2 of 6").assertIsDisplayed()
        compose.onNodeWithText("Two kinds of Holdout").assertIsDisplayed()
        compose.onNodeWithText("Stake SKR on yourself, then do the day's push-ups every day until the end.").assertIsDisplayed()
        // One card each, titled by level and length, tagged with who it suits.
        compose.onNodeWithText("Spark · 7-day").assertIsDisplayed()
        compose.onNodeWithText("Habit · 21-day").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: new to sport").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: already active").assertIsDisplayed()
        compose.onAllNodesWithText("Length").assertCountEquals(2)
        compose.onAllNodesWithText("Form").assertCountEquals(2)
        compose.onAllNodesWithText("Counting").assertCountEquals(2)
        compose.onNodeWithText("7 days").assertExists()
        compose.onNodeWithText("21 days").assertExists()
        compose.onNodeWithText("knees allowed").assertExists()
        compose.onNodeWithText("toes only").assertExists()
        compose.onNodeWithText("as the Daily 25").assertExists()
        compose.onNodeWithText("stricter").assertExists()
        compose.onNodeWithText("Spark · 7-day lights it up — knees allowed.").assertExists()
        compose.onNodeWithText("Habit · 21-day makes it stick — toes only.").assertExists()
        compose.onNodeWithText("A new one of each starts every Monday.").assertExists()
        compose.onNodeWithText("Both ask for 25 push-ups a day.").assertExists()
        compose.onNodeWithText("Full refund until joining closes.").assertExists()
        compose.onNodeWithText("Holdouts need a Seeker.").assertExists()
        // The worked example moved to card 3, and the old card-2 sentences went with it.
        compose.onNodeWithText("Example · Habit · 21-day", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Changed your mind?", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Stake SKR with everyone who starts on Monday.", substring = true).assertDoesNotExist()
    }

    @Test
    fun `card 3 plays a Holdout out - the worked example and the Finish and Miss a day tiles`() {
        show()
        repeat(2) { next() }

        assertLabel(3)
        compose.onNodeWithContentDescription("Card 3 of 6").assertIsDisplayed()
        compose.onNodeWithText("How a Holdout plays out").assertIsDisplayed()
        compose.onNodeWithText("Everyone who starts on Monday stakes together. Do every day to stay in; miss one and you're out.").assertIsDisplayed()
        compose.onNodeWithText("Example · Habit · 21-day", ignoreCase = true).assertExists()
        compose.onNodeWithText("25 × 1,250 SKR").assertExists()
        compose.onNodeWithText("18 stakes lost").assertExists()
        compose.onNodeWithText("22,500 SKR").assertExists()
        compose.onNodeWithText("70 % to the prize pool").assertExists()
        compose.onNodeWithText("15,750 SKR").assertExists()
        compose.onNodeWithText("Split by 7 finishers").assertExists()
        compose.onNodeWithText("2,250 SKR each").assertExists()
        compose.onNodeWithText("Each finisher takes").assertExists()
        compose.onNodeWithText("3,500 SKR").assertExists()
        compose.onNodeWithText("1,250 stake back + 2,250 share. If nobody drops out, you get exactly your stake back.").assertExists()
        compose.onNodeWithText("Finish").assertExists()
        compose.onNodeWithText("Your stake back, plus a share of the pool.").assertExists()
        compose.onNodeWithText("Miss a day").assertExists()
        compose.onNodeWithText("You're out. Your stake feeds the prize pool.").assertExists()
    }

    @Test
    fun `card 4 shows where a lost stake goes, with the comeback finishing the next Holdout of the same kind`() {
        show()
        repeat(3) { next() }

        assertLabel(4)
        compose.onNodeWithContentDescription("Card 4 of 6").assertIsDisplayed()
        compose.onNodeWithText("Where a lost stake goes").assertIsDisplayed()
        compose.onNodeWithText("80 % comes back to members. The rest runs Holdout.").assertExists()
        compose.onNodeWithContentDescription(
            "Of every lost stake: 70 percent to the prize pool, 10 percent to your comeback, 20 percent to the project.",
        ).assertExists()
        compose.onNodeWithText("70 %").assertExists()
        compose.onNodeWithText("This Holdout's prize pool").assertExists()
        compose.onNodeWithText("Split by everyone who finishes.").assertExists()
        compose.onNodeWithText("10 %").assertExists()
        compose.onNodeWithText("Your comeback").assertExists()
        compose.onNodeWithText("Finish your next Holdout of the same kind and it comes back. If not, it joins the boost pool.").assertExists()
        compose.onNodeWithText("Set aside for you.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Don't, and it joins the boost", substring = true).assertDoesNotExist()
        compose.onNodeWithText("20 %").assertExists()
        compose.onNodeWithText("The project").assertExists()
        compose.onNodeWithText("Keeps Holdout running.").assertExists()
        compose.onNodeWithText(
            "Lost on the last day? Join the next one on its first day, or the one after. Nothing is taken from a stake that finishes.",
        ).assertExists()
    }

    @Test
    fun `card 5 shows how a rep is counted, with the figure, and promises privacy in three lines`() {
        show()
        repeat(4) { next() }

        assertLabel(5)
        compose.onNodeWithContentDescription("Card 5 of 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Animation, side view", substring = true).assertExists()
        compose.onNodeWithText("Phone on the floor, at 45°, ahead of your shoulder.", substring = true).assertExists()
        compose.onNodeWithText("Get into a push-up: head to feet, knees included, inside the outline.", substring = true).assertExists()
        compose.onNodeWithText("The checks tick green. Hold the top for a second, then 3 · 2 · 1 and every rep counts.", substring = true).assertExists()
        compose.onNodeWithText("How reps are counted").assertDoesNotExist()
        compose.onNodeWithText("Habit: full push-ups from your toes.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Private and fair by design").assertExists()
        // The note is three lines, one node each, and the long paragraph it replaced is gone.
        compose.onNodeWithText("On-device AI counts your push-ups.").assertExists()
        compose.onNodeWithText("No mic, no video, no body data leaves your phone.").assertExists()
        compose.onNodeWithText("A Solana smart contract holds the stakes and pays out by open, verifiable rules.").assertExists()
        compose.onNodeWithText("An AI model on your phone", substring = true).assertDoesNotExist()
        compose.onAllNodes(hasText("Lab", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `card 6 names scripted members, their minimum, and that their days are not camera-checked`() {
        show()
        repeat(5) { next() }

        assertLabel(6)
        compose.onNodeWithContentDescription("Card 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("Scripted members\nin Hackathon only").assertIsDisplayed()
        compose.onNodeWithText("Some members in these Holdouts are scripted.\nReal wallets, same rules, labelled SCRIPTED.").assertExists()
        compose.onNodeWithText("They only top a Hackathon round up\nto 10, once someone real joins.\nTheir days aren't camera-checked.").assertExists()
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Next").assertDoesNotExist()
        compose.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun `nothing is meant for judges, names Holdout Lab or carries a date`() {
        show()
        repeat(6) {
            // "Hackathon" is a round's name now (card 6 names it beside Spark), so it is not on this list.
            listOf("judge", "Holdout Lab", "Easy", "Hard", "cohort", "league", "earn", "≈").forEach {
                compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).assertCountEquals(0)
            }
            if (it < 5) next()
        }
    }

    @Test
    fun `skip on a first card goes to the scripted-member card, not out of the tour`() {
        show()
        compose.onNodeWithText("Skip").performClick()

        assertLabel(6)
        assertEquals(0, done)
        compose.onNodeWithText("Scripted members\nin Hackathon only").assertIsDisplayed()
    }

    @Test
    fun `skip on cards 1 to 5 all lead to card 6`() {
        for (card in 1..5) {
            show(startCard = card)
            assertLabel(card)
            compose.onNodeWithText("Skip").performClick()
            assertLabel(6)
            assertEquals(0, done)
        }
    }

    @Test
    fun `start on the last card finishes the tour once`() {
        show()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Start").performClick()

        assertEquals(1, done)
    }

    @Test
    fun `back steps back one card at a time`() {
        show()
        next()
        next()
        assertLabel(3)

        back()
        compose.waitForIdle()
        assertLabel(2)
        back()
        compose.waitForIdle()
        assertLabel(1)
        assertEquals(0, done)
    }

    @Test
    fun `the Back button steps back a card, and the first card has none`() {
        show()
        compose.onNodeWithText("Back").assertDoesNotExist()
        next()
        next()
        compose.onNodeWithText("Back").performClick()
        assertLabel(2)
        compose.onNodeWithText("Back").performClick()
        assertLabel(1)
        compose.onNodeWithText("Back").assertDoesNotExist()
        assertEquals(0, done)
    }

    @Test
    fun `the stake sheet opens the tour at card 2, which can still go back to card 1 and on to card 6`() {
        show(startCard = 2)

        assertLabel(2)
        compose.onNodeWithText("Two kinds of Holdout").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        assertLabel(1)
        next()
        repeat(4) { next() }
        assertLabel(6)
        compose.onNodeWithText("Start").performClick()
        assertEquals(1, done)
    }

    @Test
    fun `a start card out of range is kept to the first or last card`() {
        show(startCard = 0)
        assertLabel(1)
        show(startCard = 9)
        assertLabel(6)
    }
}
