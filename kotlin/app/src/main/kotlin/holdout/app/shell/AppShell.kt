package holdout.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.nav.NavEvent
import holdout.app.nav.NavState
import holdout.app.nav.Screen
import holdout.app.nav.Sheet
import holdout.app.nav.Sitting
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.nav.TxFlow
import holdout.app.ui.BottomNav
import holdout.app.ui.BottomNavItem
import holdout.app.ui.HoldoutSheet
import holdout.app.ui.NavIcons
import holdout.app.ui.theme.HoldoutColors

/**
 * The shell (rulings G1, G7, G8): which layer of [nav] fills the screen, the
 * bottom bar on tab roots only, a sheet over the screen that opened it, and the
 * system Back. Stateless — plain state in, [NavEvent]s out — so its tests need
 * no chain, wallet, storage or camera; each area's content comes from its host
 * (`TodayHost`, `HoldoutsHost`, `MeHost`, `DetailHost`, `SittingHost`,
 * `TxFlowHost`) through the slots. Three tabs, Today · Holdouts · Me (FR-069).
 *
 * ```
 *  no wallet      → connect                         full screen
 *  a sitting      → sitting(…)                      full screen, no bar
 *  the tour       → tour                            full screen, no bar
 *  signing        → signing(…)                      full screen, no bar
 *  else           → notice, then screen(top) or root(tab)   + BottomNav on a root
 *                   + sheet(…) over both, with a scrim
 * ```
 */
@Composable
fun AppShell(
    nav: NavState,
    badges: TabBadges,
    onEvent: (NavEvent) -> Unit,
    connect: @Composable () -> Unit,
    tour: @Composable () -> Unit,
    sitting: @Composable (Sitting) -> Unit,
    signing: @Composable (TxFlow) -> Unit,
    root: @Composable (Tab) -> Unit,
    screen: @Composable (Screen) -> Unit,
    sheet: @Composable ColumnScope.(Sheet) -> Unit,
    modifier: Modifier = Modifier,
    /** Above whatever tab or screen shows: the outcome of a signature the member left before it came back (G7). */
    notice: @Composable () -> Unit = {},
) {
    BackHandler(enabled = nav.canGoBack) { onEvent(NavEvent.Back) }
    Box(modifier.fillMaxSize().background(HoldoutColors.Canvas)) {
        val s = nav.sitting
        val tx = nav.tx
        when {
            !nav.connected -> connect()
            s != null -> sitting(s)
            nav.tour != null -> tour()
            tx != null -> signing(tx)
            else -> {
                Column(Modifier.fillMaxSize()) {
                    notice()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        nav.top?.let { screen(it) } ?: root(nav.tab)
                    }
                    if (nav.showsBar) {
                        BottomNav(
                            items = Tab.entries.map { BottomNavItem(it.label, it.icon(), badges.dot(it), badges.accessibleName(it)) },
                            selected = nav.tab.ordinal,
                            onSelect = { onEvent(NavEvent.SelectTab(Tab.entries[it])) },
                        )
                    }
                }
                nav.sheet?.let { open -> HoldoutSheet(onDismiss = { onEvent(NavEvent.CloseSheet) }) { sheet(open) } }
            }
        }
    }
}

private fun Tab.icon() = when (this) {
    Tab.Today -> NavIcons.Today
    Tab.Holdouts -> NavIcons.Holdouts
    Tab.Me -> NavIcons.Me
}
