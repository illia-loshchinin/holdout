package holdout.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import holdout.app.testutil.SEEKER
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The icon-only bottom bar (001 FR-069, 2026-10-13): three items with no text; each item's name is the
 * accessible name the caller passes (`TabBadges.accessibleName`), the selected one reports `selected`,
 * and every target is at least 48 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = SEEKER)
class BottomNavTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = listOf("Today, today's reps are not done", "Holdouts, a claim is waiting", "Me, SKR or SOL is too low")
    private var selectedTab = -1

    private fun show(selected: Int = 0, dots: Boolean = true) {
        compose.setContent {
            HoldoutTheme {
                BottomNav(
                    items = listOf(
                        BottomNavItem("Today", NavIcons.Today, dot = dots, accessibleName = names[0]),
                        BottomNavItem("Holdouts", NavIcons.Holdouts, dot = dots, accessibleName = names[1]),
                        BottomNavItem("Me", NavIcons.Me, dot = dots, accessibleName = names[2]),
                    ),
                    selected = selected,
                    onSelect = { selectedTab = it },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the bar draws no text at all`() {
        show()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `each item's content description is the accessible name it was given`() {
        show()
        names.forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun `the selected item reports selected and the others do not`() {
        show(selected = 1)
        compose.onNodeWithContentDescription(names[0]).assertIsNotSelected()
        compose.onNodeWithContentDescription(names[1]).assertIsSelected()
        compose.onNodeWithContentDescription(names[2]).assertIsNotSelected()
    }

    @Test
    fun `a tap selects that item`() {
        show()
        compose.onNodeWithContentDescription(names[2]).performClick()
        assertEquals(2, selectedTab)
    }

    @Test
    fun `every target is at least 48 dp each way`() {
        show()
        names.forEach {
            val bounds = compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot()
            assertTrue(bounds.height.value >= 48f, "$it is ${bounds.height} high")
            assertTrue(bounds.width.value >= 48f, "$it is ${bounds.width} wide")
        }
    }
}
