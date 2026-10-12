package holdout.app.sitting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Board 20, stopped part-way, and its crash variant (ES-1, ES-2; rulings C-D11, C-D12). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class StoppedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun ui(
        banked: Int = 10,
        notKept: Int = 7,
        crash: Boolean = false,
        left: Long? = 5 * 3_600L + 12 * 60,
        header: String = SittingCopy.countingLabel(12, 25),
        reps: Int = 25,
    ) = StoppedUi(header = header, reps = reps, banked = banked, notKept = notKept, secondsLeft = left, crash = crash)

    private fun show(ui: StoppedUi) {
        backs = 0
        compose.setContent { HoldoutTheme { StoppedScreen(ui, onBack = { backs++ }) } }
    }

    @Test
    fun `stopped at 17 says what was banked and what was not kept`() {
        show(ui())

        compose.onNodeWithText("17").assertIsDisplayed()
        compose.onNodeWithText("done today").assertIsDisplayed()
        compose.onNodeWithText("10 banked").assertIsDisplayed()
        compose.onNodeWithText("7 not kept").assertIsDisplayed()
        compose.onNodeWithText("Only full blocks of 10 are kept. Come back before the day ends.").assertIsDisplayed()
    }

    @Test
    fun `the card has the time left today and what is still to do`() {
        show(ui())

        compose.onNodeWithText("Left today").assertIsDisplayed()
        compose.onNodeWithContentDescription("5h 12m left").assertExists()
        compose.onNodeWithText("Still to do").assertIsDisplayed()
        compose.onNodeWithText("15 push-ups").assertIsDisplayed()
    }

    @Test
    fun `one push-up to do is singular`() {
        show(ui(banked = 20, notKept = 4, reps = 21))
        compose.onNodeWithText("1 push-up").assertIsDisplayed()
    }

    @Test
    fun `the bars name what they show`() {
        show(ui())
        compose.onNodeWithContentDescription("10 banked, 7 not kept, 25 push-ups in the day").assertExists()
    }

    @Test
    fun `with only full blocks there is no not kept line`() {
        show(ui(banked = 20, notKept = 0))

        compose.onNodeWithText("20 banked").assertIsDisplayed()
        compose.onNodeWithText("not kept", substring = true).assertDoesNotExist()
        compose.onNodeWithText("20").assertIsDisplayed()
    }

    @Test
    fun `with nothing banked, the reps done this sitting were not kept`() {
        show(ui(banked = 0, notKept = 6))

        compose.onNodeWithText("6 not kept").assertIsDisplayed()
        compose.onNodeWithText("0 banked").assertDoesNotExist()
        compose.onNodeWithText("Still to do").assertIsDisplayed()
        compose.onNodeWithText("25 push-ups").assertIsDisplayed()
    }

    @Test
    fun `the label names the day, or the Daily 25`() {
        show(ui())
        compose.onNodeWithText("DAY 12 · 25 REPS").assertIsDisplayed()
    }

    @Test
    fun `the time left is not drawn when the chain is unread`() {
        show(ui(left = null))
        compose.onNodeWithText("Left today").assertDoesNotExist()
        compose.onNodeWithText("Still to do").assertIsDisplayed()
    }

    @Test
    fun `Back to today leaves`() {
        show(ui())
        compose.onNodeWithText("Back to today").performClick()
        assertEquals(1, backs)
    }

    // ---- when it is shown (C-D11) -------------------------------------------------------------------

    private fun after(bankedNow: Int, bankedAtStart: Int, discarded: Int) = StoppedUi.afterStop("day 12 · 25 reps", reps = 25, bankedNow = bankedNow, bankedAtStart = bankedAtStart, discarded = discarded, secondsLeft = 100L)

    @Test
    fun `stopping with seven loose reps shows the screen with them as not kept`() {
        val stopped = after(bankedNow = 10, bankedAtStart = 10, discarded = 7)!!
        assertEquals(7, stopped.notKept)
        assertEquals(10, stopped.banked)
        assertEquals(17, stopped.done)
        assertEquals(15, stopped.stillToDo)
    }

    @Test
    fun `stopping after banking a block shows the screen even with nothing loose`() {
        assertEquals(10, after(bankedNow = 10, bankedAtStart = 0, discarded = 0)!!.banked)
    }

    @Test
    fun `stopping with nothing done in this sitting goes straight back, whatever was banked before`() {
        assertEquals(null, after(bankedNow = 0, bankedAtStart = 0, discarded = 0))
        assertEquals(null, after(bankedNow = 20, bankedAtStart = 20, discarded = 0))
    }

    // ---- after a crash ------------------------------------------------------------------------------

    @Test
    fun `after a crash the banked reps are safe`() {
        show(ui(banked = 10, notKept = 0, crash = true))

        compose.onNodeWithText("Your 10 banked reps are safe.").assertIsDisplayed()
        compose.onNodeWithText("The app closed mid-sitting. Reps after your last 10 are gone.").assertIsDisplayed()
        compose.onNodeWithText("done today").assertDoesNotExist()
        compose.onNodeWithText("not kept", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the crash variant keeps the card and the button`() {
        show(ui(banked = 10, notKept = 0, crash = true))

        compose.onNodeWithText("Left today").assertIsDisplayed()
        compose.onNodeWithText("Still to do").assertIsDisplayed()
        compose.onNodeWithText("15 push-ups").assertIsDisplayed()
        compose.onNodeWithText("Back to today").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the Daily 25 has its own label`() {
        show(ui(header = SittingCopy.countingLabel(null, 25)))
        compose.onNodeWithText("THE DAILY 25").assertIsDisplayed()
    }
}
