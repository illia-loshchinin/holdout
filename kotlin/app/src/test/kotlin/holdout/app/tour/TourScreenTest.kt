package holdout.app.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * B1/B2 the first-run tour (boards TourDaily, Tour, TourCounting, Disclosure;
 * FR-067, spec 004 FR-012/FR-013): four cards, "how it works · N of 4", Skip on
 * the first three goes to the fourth (the scripted-member note is required on
 * first run), Back steps back a card, Start on the last one finishes. Nothing
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

    private fun show() {
        done = 0
        compose.setContent { HoldoutTheme { TourScreen(onDone = { done++ }) } }
    }

    private fun next() = compose.onNodeWithText("Next").performClick()

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun assertLabel(n: Int) = compose.onNodeWithText("How it works · $n of 4", ignoreCase = true).assertIsDisplayed()

    @Test
    fun `card 1 is the Daily 25, free, banked in tens, with a streak`() {
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
        compose.onNodeWithText("Each day done grows your streak.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Card 1 of 4").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText("Test network · test token", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `card 2 is a Holdout with a worked example and says a Seeker is needed`() {
        show()
        next()

        assertLabel(2)
        compose.onNodeWithContentDescription("Card 2 of 4").assertIsDisplayed()
        compose.onNodeWithText("A Holdout").assertIsDisplayed()
        compose.onNodeWithText("Stake SKR with everyone who starts on Monday. Finish, and split what the others lost.").assertIsDisplayed()
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
        compose.onNodeWithText("Spark · 7-day lights it up — knees allowed.\nHabit · 21-day makes it stick — toes only.").assertExists()
        compose.onNodeWithText("Changed your mind? Full refund until joining closes.", substring = true).assertExists()
        compose.onNodeWithText("Holdouts need a Seeker.", substring = true).assertExists()
        compose.onNodeWithText("The Daily 25 is free for everyone.", substring = true).assertExists()
    }

    @Test
    fun `card 3 shows how a rep is counted, with the figure, and promises privacy`() {
        show()
        next()
        next()

        assertLabel(3)
        compose.onNodeWithContentDescription("Card 3 of 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Animation, side view", substring = true).assertExists()
        compose.onNodeWithText("Phone on the floor, at 45°, ahead of your shoulder.", substring = true).assertExists()
        compose.onNodeWithText("Get into a push-up: head to feet, knees included, inside the outline.", substring = true).assertExists()
        compose.onNodeWithText("The checks tick green. Hold the top for a second, then 3 · 2 · 1 and every rep counts.", substring = true).assertExists()
        compose.onNodeWithText("How reps are counted").assertExists()
        compose.onNodeWithText("The Daily 25 and Spark: aim for your toes; knees are allowed.", substring = true).assertExists()
        compose.onNodeWithText("Habit: full push-ups from your toes.", substring = true).assertExists()
        compose.onNodeWithText("Private by design").assertExists()
        compose.onNodeWithText("No microphone, ever. Reps are counted on this phone — no video leaves the app.").assertExists()
        compose.onAllNodes(hasText("Lab", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `card 4 names scripted members, their minimum, and that their days are not camera-checked`() {
        show()
        next()
        next()
        next()

        assertLabel(4)
        compose.onNodeWithContentDescription("Card 4 of 4").assertIsDisplayed()
        compose.onNodeWithText("Scripted members").assertIsDisplayed()
        compose.onNodeWithText("Some members in these Holdouts are scripted.\nReal wallets, same rules, labelled SCRIPTED.").assertExists()
        compose.onNodeWithText("They only fill a Holdout to its minimum:\n20 per Spark and 12 per Habit.\nTheir days aren't camera-checked.").assertExists()
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Next").assertDoesNotExist()
        compose.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun `nothing is meant for judges, names Holdout Lab or carries a date`() {
        show()
        repeat(4) {
            listOf("judge", "hackathon", "Holdout Lab", "Easy", "Hard", "cohort", "league", "earn", "≈").forEach {
                compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).assertCountEquals(0)
            }
            if (it < 3) next()
        }
    }

    @Test
    fun `skip on a first card goes to the scripted-member card, not out of the tour`() {
        show()
        compose.onNodeWithText("Skip").performClick()

        assertLabel(4)
        assertEquals(0, done)
        compose.onNodeWithText("Scripted members").assertIsDisplayed()
    }

    @Test
    fun `skip on the third card also goes to the fourth`() {
        show()
        next()
        next()
        compose.onNodeWithText("Skip").performClick()

        assertLabel(4)
        assertEquals(0, done)
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
}
