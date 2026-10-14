package holdout.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.theme.HoldoutColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bottom bar on each tab root, with all four dots on in the last one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ShellShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun bar(tab: Tab, badges: TabBadges = TabBadges()) = compose.shoot("shell", "bar-${tab.label.lowercase()}") {
        Tabbed(tab, badges) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("${tab.label} root", color = HoldoutColors.TextSecondary) }
        }
    }

    @Test
    fun today() = bar(Tab.Today, TabBadges(today = true))

    @Test
    fun holdouts() = bar(Tab.Holdouts, TabBadges(holdouts = true))

    @Test
    fun me() = bar(Tab.Me, TabBadges(today = true, holdouts = true, me = true))
}
