package holdout.app.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.BottomNav
import holdout.app.ui.BottomNavItem
import holdout.app.ui.NavIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ShellShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun bar() = compose.shoot("shell", "bar") {
        BottomNav(
            items = listOf(
                BottomNavItem("Today", NavIcons.House, dot = true),
                BottomNavItem("Standing", NavIcons.Pulse),
                BottomNavItem("Holdouts", NavIcons.List),
                BottomNavItem("Me", NavIcons.Person),
            ),
            selected = 0,
            onSelect = {},
        )
    }
}
