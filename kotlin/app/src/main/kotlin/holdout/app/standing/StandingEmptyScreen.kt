package holdout.app.standing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.holdouts.RunningHoldout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.QuietAction
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
import holdout.core.Leaderboard

/**
 * The Standing tab with no Holdout of the member's to show (rulings G1, D-D9):
 * the running Holdouts they could watch, their lobby place if they hold one,
 * then "Join one to have a crowd" → the Holdouts tab. The leaderboards live
 * on Standing, so they are reachable from here too.
 *
 * ```
 *  Standing                                   headlineMedium
 *  RUNNING NOW                                SectionLabel
 *  ┌ Spark · 7-day · day 3 of 7 ┐             HoldoutCard → H4 (watching)
 *  └ 9 still standing of 12 · prize pool … ┘
 *  Yours starts in 2d 4h →                    QuietAction → H3
 *  Join one to have a crowd →                 QuietAction → Holdouts tab
 *  [ Leaderboards ]                           SecondaryButton → L1
 * ```
 */
@Composable
fun StandingEmptyScreen(
    running: List<RunningHoldout>,
    /** "Yours starts in 2d 4h" and its lobby, when the member holds a place in one that hasn't started. */
    lobby: Pair<String, String>?,
    onEvent: (NavEvent) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Standing", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        header()
        if (running.isNotEmpty()) {
            SectionLabel("Running now")
            running.forEach { r ->
                HoldoutCard(Modifier.clickable(onClickLabel = "Watch this Holdout") { onEvent(r.go) }) {
                    if (r.test) TestCohortBadge(r.testDayLength)
                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                    Text(r.line, style = AmountStyle, color = HoldoutColors.Accent)
                }
            }
        } else {
            Body("No Holdout is running right now.")
        }
        lobby?.let { (line, cohort) -> QuietAction("$line →", { onEvent(NavEvent.Open(Screen.Lobby(cohort))) }) }
        QuietAction("Join one to have a crowd →", { onEvent(NavEvent.SelectTab(Tab.Holdouts)) })
        SecondaryButton("Leaderboards", { onEvent(NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.EASY_7D))) })
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun StandingEmptyPreview() {
    HoldoutTheme {
        StandingEmptyScreen(
            running = listOf(RunningHoldout("w", "Spark · 7-day · day 3 of 7", "9 still standing of 12 · prize pool 2,400 SKR", false)),
            lobby = "Yours starts in 2d 4h" to "l",
            onEvent = {},
        )
    }
}
