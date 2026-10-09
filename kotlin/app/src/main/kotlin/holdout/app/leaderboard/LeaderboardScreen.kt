package holdout.app.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.solana.publickey.SolanaPublicKey
import holdout.app.ui.Body
import holdout.app.ui.MemberIdentity
import holdout.app.ui.Reading
import holdout.app.ui.ReportShownWallets
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Board
import holdout.core.Leaderboard.Standing
import holdout.core.Leaderboard.View

/**
 * The league leaderboards (US-10, FR-060…FR-065, T097): all-time, one per
 * league, plus one combined board for the short test cohorts. Ranked by
 * [Leaderboard] from on-chain progress only; each member by Seeker ID or
 * shortened address (spec 003), scripted members labelled, and **no amount
 * anywhere** (FR-063, constitution XIII). Names never reorder the board (FR-008).
 */
@Composable
fun LeaderboardScreen(
    board: Board,
    boards: List<Board>,
    view: View?,
    me: SolanaPublicKey?,
    onBoard: (Board) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowing: (Collection<String>) -> Unit = {},
) {
    val list = rememberLazyListState()
    // Rows are keyed "r" or "n" (ranked, not yet) + the wallet; drop the prefix for the wallet.
    ReportShownWallets(list, onShowing) { (it as? String)?.takeIf { key -> key.length > 1 }?.drop(1) }
    LazyColumn(
        state = list,
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        item { TextButton(onClick = onBack) { Text("← Home", color = HoldoutColors.TextSecondary) } }
        item { Text("Leaderboards", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                boards.forEach { b ->
                    TextButton(onClick = { onBoard(b) }) {
                        Text(LeaderboardText.tab(b), color = if (b == board) HoldoutColors.Accent else HoldoutColors.TextTertiary)
                    }
                }
            }
        }
        item { Body(LeaderboardText.about(board)) }
        when (view) {
            null -> item { Reading { Body("Reading devnet…") } }
            View.Updating -> item { Body(LeaderboardText.UPDATING) }
            is View.Ready -> {
                if (me != null) item { Text(LeaderboardText.standing(view.standingOf(me)), style = LabelStyle, color = HoldoutColors.Accent) }
                item { SectionLabel("Ranked · ${view.ranked.size}") }
                if (view.ranked.isEmpty()) item { Body("Nobody has finished ${Leaderboard.RANKED_AFTER} of these Holdouts yet.") }
                itemsIndexed(view.ranked, key = { _, r -> "r" + r.wallet.base58() }) { i, r -> Line("${i + 1}.", r, me) }
                if (view.notRanked.isNotEmpty()) {
                    item { SectionLabel("Not ranked yet · ${view.notRanked.size}") }
                    itemsIndexed(view.notRanked, key = { _, r -> "n" + r.wallet.base58() }) { _, r -> Line("·", r, me) }
                }
            }
        }
    }
}

@Composable
private fun Line(place: String, r: Leaderboard.Row, me: SolanaPublicKey?) {
    val address = r.wallet.base58()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Text("$place ", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
                MemberIdentity(address, isYou = r.wallet == me, budget = 20, color = HoldoutColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            }
            Text(LeaderboardText.rate(r), style = LabelStyle, color = HoldoutColors.Success)
        }
        Text(LeaderboardText.figures(r), style = LabelStyle, color = HoldoutColors.TextSecondary)
    }
}

/** Every word the leaderboard shows, kept pure so it can be tested — never an amount. */
object LeaderboardText {
    const val UPDATING = "Updating — a Holdout's results are being counted. The board is back once it settles."

    fun tab(b: Board): String = when (b) {
        Board.EASY_7D -> "Easy · 7-day"
        Board.HARD_21D -> "Hard · 21-day"
        Board.TEST -> "Short test"
    }

    fun about(b: Board): String = when (b) {
        Board.TEST -> "Every short test cohort on devnet, counted together. Ranked by the share of Holdouts survived, after ${Leaderboard.RANKED_AFTER} finished."
        else -> "All-time, from the chain. Ranked by the share of Holdouts survived, after ${Leaderboard.RANKED_AFTER} finished."
    }

    fun rate(r: Leaderboard.Row): String = "${r.survivalPercent}% survived"

    fun figures(r: Leaderboard.Row): String = buildString {
        if (r.scripted) append("SCRIPTED · ")
        append("${r.cleared} of ${r.played} cleared · streak ${r.currentStreak} · best ${r.longestStreak}")
        if (r.cohortsNeeded > 0) append(" · ${r.cohortsNeeded} more to be ranked")
    }

    fun standing(s: Standing): String = when (s) {
        is Standing.Ranked -> "You're ranked #${s.position}"
        is Standing.NotRanked -> "You're ${s.cohortsNeeded} Holdout${if (s.cohortsNeeded == 1) "" else "s"} away from being ranked"
        is Standing.Absent -> "Finish ${s.cohortsNeeded} of these Holdouts to be ranked"
    }
}
