package holdout.app.sitting

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.testutil.textColors
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Board 6, counting: the count, the bars, "10 banked · 7 this set", the time left (C-D5, C-D7, FR-047). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class CountingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var stops = 0

    private fun show(
        done: Int = 17,
        banked: Int = 10,
        loose: Int = 7,
        target: Int = 25,
        hint: String? = null,
        justBanked: Boolean = false,
        dayNumber: Int? = 12,
        timeLeft: Long? = 4 * 3_600L + 6 * 60,
        extra: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        stops = 0
        compose.setContent {
            HoldoutTheme {
                CountingContent(
                    ui = CountingUi(
                        header = SittingHeader(SittingCopy.countingLabel(dayNumber, target), timeLeft, holdoutDay = dayNumber != null),
                        done = done,
                        target = target,
                        banked = banked,
                        loose = loose,
                        hint = hint,
                        justBanked = justBanked,
                    ),
                    onStop = { stops++ },
                    extra = extra,
                )
            }
        }
    }

    @Test
    fun `the count is the big number, with of 25 beside it`() {
        show()
        compose.onNodeWithText("17").assertIsDisplayed()
        compose.onNodeWithText("of 25").assertIsDisplayed()
    }

    @Test
    fun `a Holdout of another size says of its own target`() {
        show(done = 3, banked = 0, loose = 3, target = 5)
        compose.onNodeWithText("of 5").assertIsDisplayed()
    }

    @Test
    fun `the caption says what is banked and what is only this set`() {
        show()
        compose.onNodeWithText("10 banked · 7 this set").assertIsDisplayed()
    }

    @Test
    fun `nothing in this set yet`() {
        show(done = 10, banked = 10, loose = 0)
        compose.onNodeWithText("10 banked · 0 this set").assertIsDisplayed()
    }

    @Test
    fun `the bars name how many blocks are banked`() {
        show()
        compose.onNodeWithContentDescription("1 of 3 blocks banked, 17 of 25 reps").assertExists()
    }

    @Test
    fun `the time left is a countdown, in words and in the accessible name`() {
        show()
        compose.onNodeWithContentDescription("4h 06m left").assertExists()
        compose.onNodeWithText("left").assertIsDisplayed()
    }

    @Test
    fun `the label names the day and its reps`() {
        show()
        compose.onNodeWithText("DAY 12 · 25 REPS").assertIsDisplayed()
    }

    @Test
    fun `the Daily 25 is named by its name and shows what is left today`() {
        show(dayNumber = null)
        compose.onNodeWithText("THE DAILY 25").assertIsDisplayed()
        compose.onNodeWithContentDescription("4h 06m left").assertExists()
    }

    @Test
    fun `no time left shown while the chain is unread - Lab has no clock`() {
        show(timeLeft = null)
        compose.onNodeWithText("left").assertDoesNotExist()
    }

    @Test
    fun `in the last ten minutes of a Holdout day the time turns to a warning, with no sentence over the camera`() {
        show(timeLeft = 9 * 60L + 30)
        compose.onNodeWithContentDescription("Under 10 minutes left, 9m 30s").assertExists()
        compose.onNodeWithText(SittingCopy.LAST_MINUTES).assertDoesNotExist()
    }

    @Test
    fun `in the last ten minutes only the time left turns the soft warm tone - never the warning red`() {
        show(timeLeft = 9 * 60L + 30)

        assertEquals(setOf(HoldoutColors.SoftWarm), compose.onNodeWithContentDescription("Under 10 minutes left, 9m 30s").textColors())
    }

    @Test
    fun `earlier in the day the time left is in the accent colour`() {
        show()

        assertEquals(setOf(HoldoutColors.Accent), compose.onNodeWithContentDescription("4h 06m left").textColors())
    }

    @Test
    fun `the Daily 25 turns the same soft warm tone in its last ten minutes`() {
        show(dayNumber = null, timeLeft = 9 * 60L + 30)

        assertEquals(setOf(HoldoutColors.SoftWarm), compose.onNodeWithContentDescription("Under 10 minutes left, 9m 30s").textColors())
    }

    @Test
    fun `earlier in the day there is no warning sentence`() {
        show()
        compose.onNodeWithText(SittingCopy.LAST_MINUTES).assertDoesNotExist()
    }

    @Test
    fun `the Daily 25 has no unfinished reps sentence in its last ten minutes`() {
        show(dayNumber = null, timeLeft = 9 * 60L + 30)
        compose.onNodeWithText(SittingCopy.LAST_MINUTES).assertDoesNotExist()
    }

    @Test
    fun `a refused rep's hint is shown, in a size a member reads from the floor`() {
        show(hint = "Go lower")
        compose.onNodeWithText("Go lower").assertIsDisplayed()

        val size = textSizeOf("Go lower")
        assertTrue(size >= 24.sp && size <= 30.sp, "the hint is about 26 sp, the title size scaled up, not twice it: $size")
    }

    private fun textSizeOf(text: String): androidx.compose.ui.unit.TextUnit {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        return result.first().layoutInput.style.fontSize
    }

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun inPoliteRegion(text: String) = compose.onNode(hasText(text) and (polite or hasAnyAncestor(polite)))

    // ---- soft coaching at the top (FR-021, 2026-10-13) --------------------------------------------------------

    @Test
    fun `a refused rep's hint sits directly under the header and above the count`() {
        show(hint = "Go lower")

        val header = compose.onNodeWithText("DAY 12 · 25 REPS").getBoundsInRoot()
        val hint = compose.onNodeWithText("Go lower").getBoundsInRoot()
        val count = compose.onNodeWithText("17").getBoundsInRoot()

        assertTrue(hint.top >= header.bottom, "under the header: $hint $header")
        assertTrue(hint.top - header.bottom <= 48.dp, "directly under it, not mid-screen: gap ${hint.top - header.bottom}")
        assertTrue(hint.bottom <= count.top, "above the count: $hint $count")
    }

    @Test
    fun `the banked pill sits directly under the header and above the count`() {
        show(done = 20, banked = 20, loose = 0, justBanked = true)

        val header = compose.onNodeWithText("DAY 12 · 25 REPS").getBoundsInRoot()
        val pill = compose.onNodeWithText("Block of 10 banked").getBoundsInRoot()
        val count = compose.onNodeWithText("20").getBoundsInRoot()

        assertTrue(pill.top >= header.bottom && pill.top - header.bottom <= 48.dp, "directly under the header: $pill $header")
        assertTrue(pill.bottom <= count.top, "above the count: $pill $count")
    }

    @Test
    fun `the Daily 25's hint sits at the top too`() {
        show(dayNumber = null, hint = "Keep your body straight")

        val header = compose.onNodeWithText("THE DAILY 25").getBoundsInRoot()
        val hint = compose.onNodeWithText("Keep your body straight").getBoundsInRoot()
        assertTrue(hint.top >= header.bottom && hint.top - header.bottom <= 48.dp, "directly under the header: $hint $header")
        inPoliteRegion("Keep your body straight").assertExists()
    }

    @Test
    fun `the hint is announced politely`() {
        show(hint = "Keep your body straight")
        inPoliteRegion("Keep your body straight").assertExists()
    }

    @Test
    fun `the banked pill is announced politely`() {
        show(done = 20, banked = 20, loose = 0, justBanked = true)
        inPoliteRegion("Block of 10 banked").assertExists()
    }

    @Test
    fun `the hint's text is in the primary colour - never the warning red`() {
        show(hint = "Go lower")

        val colors = compose.onNodeWithText("Go lower").textColors()
        assertEquals(setOf(HoldoutColors.TextPrimary), colors)
        assertTrue(HoldoutColors.Warning !in colors)
    }

    @Test
    fun `the banked pill's word is Success, smaller than a hint - never the warning red`() {
        show(done = 20, banked = 20, loose = 0, justBanked = true)

        val colors = compose.onNodeWithText("Block of 10 banked").textColors()
        assertEquals(setOf(HoldoutColors.Success), colors)
        assertTrue(textSizeOf("Block of 10 banked") < 20.sp, "reduced emphasis: smaller than the hint's text")
    }

    @Test
    fun `Go lower gets an arrow down and Keep your body straight a line`() {
        assertEquals(HintGlyph.ARROW_DOWN, hintGlyphFor("Go lower"))
        assertEquals(HintGlyph.LINE, hintGlyphFor("Keep your body straight"))
        assertEquals(HintGlyph.DOT, hintGlyphFor("Slow down"))
    }

    @Test
    fun `the pill draws the icon of its hint`() {
        show(hint = "Go lower")
        compose.onNodeWithTag("hint-icon-arrow_down", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a body line hint draws a line icon`() {
        show(hint = "Keep your body straight")
        compose.onNodeWithTag("hint-icon-line", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the moment a block of 10 banks is said on screen`() {
        show(done = 20, banked = 20, loose = 0, justBanked = true)
        compose.onNodeWithText("Block of 10 banked").assertIsDisplayed()
    }

    @Test
    fun `a refused rep's reason takes the place of the banked pill`() {
        show(done = 20, banked = 20, loose = 1, justBanked = true, hint = "Go lower")
        compose.onNodeWithText("Block of 10 banked").assertDoesNotExist()
        compose.onNodeWithText("Go lower").assertIsDisplayed()
    }

    @Test
    fun `no banked pill until a block banks`() {
        show()
        compose.onNodeWithText("Block of 10 banked").assertDoesNotExist()
    }

    @Test
    fun `no hint, no hint text`() {
        show(hint = null)
        compose.onNodeWithText("Go lower").assertDoesNotExist()
    }

    @Test
    fun `the close circle stops the sitting`() {
        show()
        compose.onNodeWithContentDescription("Stop this sitting").performClick()
        assertEquals(1, stops)
    }

    @Test
    fun `Holdout Lab's readouts are drawn in their slot`() {
        show(extra = { Text("elbow 92°") })
        compose.onNodeWithText("elbow 92°").assertIsDisplayed()
    }
}
