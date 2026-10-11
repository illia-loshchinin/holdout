package holdout.app.standing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.holdouts.RunningHoldout
import holdout.app.holdouts.RunningNowCard
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.nav.Tab
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
import holdout.core.Leaderboard

/**
 * The Standing tab with no Holdout of the member's to show (FR-069b, rulings G1,
 * D-D9, G14; board "Standing — nothing of yours yet"): the running Holdouts they
 * could watch, then either the line that opens their lobby place — or, with
 * none, "Join one to have a crowd", which opens the Holdouts tab. The
 * leaderboards live on Standing, so they are reachable from here too.
 *
 * ```
 *  Standing                                   headline
 *  RUNNING NOW                                SectionLabel
 *  ┌ Habit · 21-day · day 12 of 21        › ┐ RunningNowCard → H4 (watching), then its roster
 *  └ 189 still standing of 312 · prize pool … ┘
 *  ( Yours starts in 2d 4h            › )     accent pill → H3, only with a lobby place
 *  ┌ Join one to have a crowd.              ┐ only without one
 *  │ Everyone who starts together thins out together.
 *  └ [ See Holdouts ]                       ┘ PrimaryButton → Holdouts tab
 *  Leaderboards →                             QuietAction → L1
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
        Text(StandingEmptyText.TITLE, style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        header()
        if (running.isNotEmpty()) {
            SectionLabel("Running now")
            running.forEach { r -> RunningNowCard(r, onOpen = { onEvent(r.go) }) }
        }
        if (lobby != null) {
            val (line, cohort) = lobby
            LobbyLine(line) { onEvent(NavEvent.Open(Screen.Lobby(cohort))) }
        } else {
            HoldoutCard {
                Text(StandingEmptyText.JOIN_TITLE, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                Body(StandingEmptyText.JOIN_BODY)
                PrimaryButton(StandingEmptyText.JOIN_BUTTON, { onEvent(NavEvent.SelectTab(Tab.Holdouts)) })
            }
        }
        QuietAction("Leaderboards →", { onEvent(NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.EASY_7D))) })
    }
}

/** "Yours starts in 2d 4h ›": the member's lobby place, as an accent pill that opens it. */
@Composable
private fun LobbyLine(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .background(HoldoutColors.AccentTint, MaterialTheme.shapes.extraLarge)
            .border(Spacing.border, HoldoutColors.Accent.copy(alpha = BORDER_ALPHA), MaterialTheme.shapes.extraLarge)
            .clickable(onClickLabel = "Open your lobby", onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AmountStyle, color = HoldoutColors.Accent, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Accent)
    }
}

/** The empty state's words (FR-069b). */
object StandingEmptyText {
    const val TITLE = "Standing"
    const val JOIN_TITLE = "Join one to have a crowd."
    const val JOIN_BODY = "Everyone who starts together thins out together."
    const val JOIN_BUTTON = "See Holdouts"
}

private const val BORDER_ALPHA = 0.32f

private val PREVIEW_RUNNING = listOf(
    RunningHoldout("w", "Habit · 21-day · day 12 of 21", "189 still standing of 312 · prize pool 48,200 SKR", false),
    RunningHoldout("v", "Spark · 7-day · day 7 of 7", "13 still standing of 20 · prize pool 6,125 SKR", false),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun StandingEmptyPreview() {
    HoldoutTheme { StandingEmptyScreen(running = PREVIEW_RUNNING, lobby = null, onEvent = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun StandingLobbyPreview() {
    HoldoutTheme { StandingEmptyScreen(running = PREVIEW_RUNNING, lobby = "Yours starts in 2d 4h" to "l", onEvent = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 500)
@Composable
private fun StandingNothingRunningPreview() {
    HoldoutTheme { StandingEmptyScreen(running = emptyList(), lobby = null, onEvent = {}) }
}
