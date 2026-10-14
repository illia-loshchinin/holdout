package holdout.app.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.SEEKER
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Today's hero is one frame, 312 dp high whatever it holds (001 FR-069a: "a state changes its content, never its size"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = SEEKER)
class DayHeroTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private fun show(label: String, sub: String, button: String, footer: String) {
        compose.setContent {
            HoldoutTheme {
                DayHero(
                    label = label,
                    countdown = { Text("14h 02m left") },
                    number = "25",
                    unit = "push-ups",
                    bars = { BankBars(target = 25, banked = 10) },
                    sub = { Text(sub) },
                    button = { Text(button) },
                    footer = { Box { Text(footer) } },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `short content - the frame is 312 dp`() {
        show("THE DAILY 25", "Reps count on this phone.", "Start", "Streak 12")
        compose.onNodeWithTag("day-hero").assertIsDisplayed().assertHeightIsEqualTo(312.dp)
        compose.onNodeWithText("25").assertIsDisplayed()
    }

    @Test
    fun `long content - the frame is still 312 dp`() {
        show(
            "DEVNET COHORT · SPARK · 7-DAY · DAY 6 OF 7",
            "Counts as your Daily 25 too. Saved on this phone, it sends when devnet answers and you approve it.",
            "Done — waiting for network",
            "Spark · 7-day · 12 of 14 still standing · 12,500 SKR in the pool",
        )
        compose.onNodeWithTag("day-hero").assertHeightIsEqualTo(312.dp)
    }
}
