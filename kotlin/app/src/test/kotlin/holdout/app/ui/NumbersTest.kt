package holdout.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** CountdownText, StreakFlame and the bank blocks: the numbers the app comes back for. */
@RunWith(RobolectricTestRunner::class)
class NumbersTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a countdown shows the Format text and is named with left`() {
        compose.setContent { HoldoutTheme { CountdownText(4 * 3_600L + 6 * 60) } }

        compose.onNodeWithText("4h 06m").assertIsDisplayed()
        compose.onNodeWithContentDescription("4h 06m left").assertExists()
    }

    @Test
    fun `a countdown inside the last 10 minutes says so in words, not only in colour`() {
        compose.setContent { HoldoutTheme { CountdownText(9 * 60L + 30) } }

        compose.onNodeWithText("9m 30s").assertIsDisplayed()
        compose.onNodeWithContentDescription("Under 10 minutes left, 9m 30s").assertExists()
    }

    @Test
    fun `the warning switches on at exactly 10 minutes and not a second before`() {
        compose.setContent {
            HoldoutTheme {
                Column {
                    CountdownText(601)
                    CountdownText(600)
                }
            }
        }

        compose.onNodeWithContentDescription("10m 01s left").assertExists()
        compose.onNodeWithContentDescription("Under 10 minutes left, 10m 00s").assertExists()
    }

    @Test
    fun `an active streak flame names the days`() {
        compose.setContent { HoldoutTheme { StreakFlame(12, StreakState.Active) } }

        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithContentDescription("12-day streak").assertExists()
        compose.onNodeWithText("PB").assertDoesNotExist()
    }

    @Test
    fun `a streak extended today says so`() {
        compose.setContent { HoldoutTheme { StreakFlame(13, StreakState.ExtendedToday) } }

        compose.onNodeWithText("13").assertIsDisplayed()
        compose.onNodeWithContentDescription("13-day streak, extended today").assertExists()
    }

    @Test
    fun `a broken streak says it is broken and shows no PB`() {
        compose.setContent { HoldoutTheme { StreakFlame(0, StreakState.Broken) } }

        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Streak broken").assertExists()
        compose.onNodeWithText("PB").assertDoesNotExist()
    }

    @Test
    fun `a personal best carries the PB pill`() {
        compose.setContent { HoldoutTheme { StreakFlame(31, StreakState.PersonalBest) } }

        compose.onNodeWithText("31").assertIsDisplayed()
        compose.onNodeWithText("PB").assertIsDisplayed()
        compose.onNodeWithContentDescription("31-day streak, personal best").assertExists()
    }

    @Test
    fun `a day of 25 is cut 10, 10, 5 and reps short of a block are a short last block`() {
        assertEquals(listOf(10, 10, 5), bankBlocks(25))
        assertEquals(listOf(10), bankBlocks(10))
        assertEquals(listOf(7), bankBlocks(7))
        assertEquals(listOf(10, 10, 10), bankBlocks(30))
        assertEquals(emptyList(), bankBlocks(0))
    }
}
