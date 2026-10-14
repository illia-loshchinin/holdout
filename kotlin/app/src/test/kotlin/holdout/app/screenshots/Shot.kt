package holdout.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import holdout.app.nav.NavState
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.shell.AppShell
import holdout.app.ui.HoldoutSheet
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import org.robolectric.RuntimeEnvironment

/** A phone's width and height in dp (xxhdpi), for `@Config(qualifiers = …)`. */
const val PHONE_QUALIFIERS = "w400dp-h829dp-xxhdpi"

/** The height of a phone-wide screen tall enough to show the whole of a scrolling one. */
const val TALL = 2200

/**
 * Renders [content] inside the app theme and, when recording is on
 * (`-Proborazzi.test.record=true`), writes it to `build/screens/<area>/<name>.png`.
 * With recording off it only renders, so the normal test run writes nothing.
 * [heightDp] stretches the screen so a scrolling one is drawn whole; [after] acts on the screen (a tap on Next) before it is captured.
 */
internal fun ComposeContentTestRule.shoot(area: String, name: String, heightDp: Int? = null, after: ComposeContentTestRule.() -> Unit = {}, content: @Composable () -> Unit) {
    if (heightDp != null) RuntimeEnvironment.setQualifiers("+h${heightDp}dp")
    setContent { HoldoutTheme { Box(Modifier.fillMaxSize().background(HoldoutColors.Canvas)) { content() } } }
    waitForIdle()
    after()
    waitForIdle()
    onRoot().captureRoboImage("build/screens/$area/$name.png")
}

/** [content] as the shell draws a tab root: filling the screen with the bottom bar under it (rulings G1). */
@Composable
internal fun Tabbed(tab: Tab, badges: TabBadges = TabBadges(), content: @Composable () -> Unit) {
    AppShell(
        nav = NavState(connected = true, tab = tab),
        badges = badges,
        onEvent = {},
        connect = {},
        tour = {},
        sitting = {},
        signing = {},
        root = { content() },
        screen = {},
        sheet = {},
    )
}

/** The screen that opened a sheet, a scrim over it, and the sheet — as the shell draws them (rulings G8). */
@Composable
internal fun Sheeted(behind: @Composable () -> Unit, sheet: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        behind()
        HoldoutSheet(onDismiss = {}, content = sheet)
    }
}
