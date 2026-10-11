package holdout.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TierChips and ChoiceChips: single-choice radio groups that report the pick and say what is selected. */
@RunWith(RobolectricTestRunner::class)
class ChipsTest {
    @get:Rule
    val compose = createComposeRule()

    private val tiers = listOf(
        TierChip("≈ \$10", "500 SKR"),
        TierChip("≈ \$25", "1,250 SKR"),
        TierChip("≈ \$50", "2,500 SKR"),
        TierChip("≈ \$100", "5,000 SKR"),
    )

    @Test
    fun `tier chips show the dollar label over the exact SKR for every tier`() {
        compose.setContent { HoldoutTheme { TierChips(tiers, selected = 0, onSelect = {}) } }

        tiers.forEach {
            compose.onNodeWithText(it.usd).assertIsDisplayed()
            compose.onNodeWithText(it.skr).assertIsDisplayed()
        }
    }

    @Test
    fun `the picked tier reads as selected and the others do not`() {
        compose.setContent { HoldoutTheme { TierChips(tiers, selected = 1, onSelect = {}) } }

        compose.onNodeWithContentDescription("≈ \$25, 1,250 SKR").assertIsSelected()
        compose.onNodeWithContentDescription("≈ \$10, 500 SKR").assertIsNotSelected()
        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertIsNotSelected()
        compose.onNodeWithContentDescription("≈ \$100, 5,000 SKR").assertIsNotSelected()
    }

    @Test
    fun `tapping a tier reports its index and moves the selection`() {
        val picks = mutableListOf<Int>()
        compose.setContent {
            HoldoutTheme {
                var selected by remember { mutableIntStateOf(0) }
                TierChips(tiers, selected, onSelect = {
                    picks += it
                    selected = it
                })
            }
        }

        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").performClick()

        assertEquals(listOf(2), picks)
        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertIsSelected()
        compose.onNodeWithContentDescription("≈ \$10, 500 SKR").assertIsNotSelected()
    }

    private val options = listOf("Spark · 7-day", "Habit · 21-day", "Short test")

    @Test
    fun `choice chips show every option as given, not uppercased`() {
        compose.setContent { HoldoutTheme { ChoiceChips(options, selected = null, onSelect = {}) } }

        options.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `choice chips mark the chosen option selected`() {
        compose.setContent { HoldoutTheme { ChoiceChips(options, selected = 0, onSelect = {}) } }

        compose.onNodeWithText("Spark · 7-day").assertIsSelected()
        compose.onNodeWithText("Habit · 21-day").assertIsNotSelected()
    }

    @Test
    fun `tapping another option reports its index`() {
        var picked: Int? = 0
        compose.setContent { HoldoutTheme { ChoiceChips(options, selected = picked, onSelect = { picked = it }) } }

        compose.onNodeWithText("Habit · 21-day").performClick()

        assertEquals(1, picked)
    }

    @Test
    fun `tapping the chosen option clears it only when an empty choice is allowed`() {
        var picked: Int? = 1
        compose.setContent {
            HoldoutTheme {
                var current by remember { mutableStateOf<Int?>(1) }
                ChoiceChips(options, selected = current, onSelect = {
                    current = it
                    picked = it
                }, allowEmpty = true)
            }
        }

        compose.onNodeWithText("Habit · 21-day").performClick()

        assertNull(picked)
    }

    @Test
    fun `a board picker never lets the choice go empty`() {
        var calls = 0
        compose.setContent { HoldoutTheme { ChoiceChips(options, selected = 1, onSelect = { calls++ }, allowEmpty = false) } }

        compose.onNodeWithText("Habit · 21-day").performClick()

        assertEquals(0, calls)
    }
}
