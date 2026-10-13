package holdout.app.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solana.publickey.SolanaPublicKey
import holdout.app.ui.Body
import holdout.app.ui.ChoiceChips
import holdout.app.ui.HoldoutCard
import holdout.app.ui.MemberIdentity
import holdout.app.ui.Reading
import holdout.app.ui.ReportShownWallets
import holdout.app.ui.ScreenHeader
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Leaderboard
import holdout.core.Leaderboard.Board
import holdout.core.Leaderboard.Standing
import holdout.core.Leaderboard.View

/**
 * The leaderboards (L1, board 29; US-10, FR-060…FR-065): all-time, one per
 * level, plus one combined board for the Hackathon rounds, which wears its
 * `DEVNET COHORT` badge (FR-044a). Ranked by [Leaderboard] from on-chain progress
 * only; each member by Seeker ID or shortened address (spec 003), scripted
 * members labelled, and **no amount anywhere** (FR-063, constitution XIII).
 * Names never reorder the board (FR-008).
 *
 * ```
 *  ‹            ME                              ScreenHeader · Back goes to where it was opened
 *  Leaderboards                                 headline
 *  ( Spark · 7-day ) ( Habit · 21-day ) ( Hackathon )    ChoiceChips
 *  [DEVNET COHORT · 10-MINUTE DAYS]             the test board only
 *  All-time, from the chain. Ranked by …        the board's copy
 *  YOU'RE RANKED #4                             accent label
 *  RANKED · 12                                  1.  alice.skr          100% survived
 *  NOT RANKED YET · 31                          ·   oleh.skr            …
 * ```
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
    /** The newest test league's day length, for the test board's badge (FR-044a). */
    testDayLengthSeconds: Long = TEST_DAY_SECONDS,
) {
    val list = rememberLazyListState()
    ReportShownWallets(list, onShowing, ::leaderboardRowWallet)
    LazyColumn(
        state = list,
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(horizontal = Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        item { ScreenHeader("me", onBack = onBack, modifier = Modifier.padding(top = Spacing.screenGutter)) }
        item { Text("Leaderboards", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary) }
        item {
            ChoiceChips(
                options = boards.map(LeaderboardText::tab),
                selected = boards.indexOf(board).takeIf { it >= 0 },
                onSelect = { i -> i?.let { onBoard(boards[it]) } },
            )
        }
        if (board == Board.TEST) item { TestCohortBadge(testDayLengthSeconds) }
        item { Body(LeaderboardText.about(board)) }
        when (view) {
            null -> item { Reading { Body("Reading devnet…") } }
            View.Updating -> item { HoldoutCard { Body(LeaderboardText.UPDATING) } }
            is View.Ready -> {
                if (me != null) item { Text(LeaderboardText.standing(view.standingOf(me)).uppercase(), style = LabelStyle, color = HoldoutColors.Accent) }
                item { SectionLabel("Ranked · ${view.ranked.size}") }
                if (view.ranked.isEmpty()) item { Body(LeaderboardText.emptyBoard()) }
                itemsIndexed(view.ranked, key = { _, r -> "r" + r.wallet.base58() }) { i, r -> Line("${i + 1}.", r, me, ranked = true) }
                if (view.notRanked.isNotEmpty()) {
                    item { SectionLabel("Not ranked yet · ${view.notRanked.size}", Modifier.padding(top = Spacing.betweenItems)) }
                    itemsIndexed(view.notRanked, key = { _, r -> "n" + r.wallet.base58() }) { _, r -> Line("·", r, me, ranked = false) }
                }
            }
        }
        item { Box(Modifier.height(Spacing.screenGutter)) }
    }
}

/** One member: place, name and figures, the survival rate at the end; a hairline under it. */
@Composable
private fun Line(place: String, r: Leaderboard.Row, me: SolanaPublicKey?, ranked: Boolean) {
    val address = r.wallet.base58()
    val tone = if (ranked) HoldoutColors.TextPrimary else HoldoutColors.TextSecondary
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = LINE_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            Text(place, style = AmountStyle, color = HoldoutColors.TextTertiary, modifier = Modifier.width(PLACE_WIDTH))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                MemberIdentity(address, isYou = r.wallet == me, budget = 20, color = tone, modifier = Modifier.fillMaxWidth())
                Text(LeaderboardText.figures(r), style = LabelStyle, color = HoldoutColors.TextTertiary)
            }
            Text(LeaderboardText.rate(r), style = AmountStyle.copy(fontSize = LabelStyle.fontSize, lineHeight = LabelStyle.lineHeight), color = if (ranked) HoldoutColors.Success else HoldoutColors.TextSecondary)
        }
        HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
    }
}

private val LINE_HEIGHT = 52.dp
private val PLACE_WIDTH = 32.dp

/** A short test day, 10 minutes, when no test league has been read yet. */
private const val TEST_DAY_SECONDS = 600L

/**
 * The wallet a leaderboard row names, from its `LazyColumn` key: rows are
 * keyed "r" (ranked) or "n" (not ranked yet) followed by the wallet's base58;
 * header rows have no key of their own.
 */
internal fun leaderboardRowWallet(key: Any): String? = (key as? String)?.takeIf { it.length > 1 && it[0] in "rn" }?.drop(1)

/** Every word the leaderboard shows, kept pure so it can be tested — never an amount, never the word cohort. */
object LeaderboardText {
    const val UPDATING = "Updating while a Holdout settles."

    fun tab(b: Board): String = when (b) {
        Board.EASY_7D -> "Spark · 7-day"
        Board.HARD_21D -> "Habit · 21-day"
        Board.TEST -> "Hackathon"
    }

    fun about(b: Board): String = when (b) {
        Board.TEST -> "Every Hackathon round on devnet, together. Ranked by share survived, after ${Leaderboard.RANKED_AFTER} played."
        else -> "All-time, from the chain. Ranked by share survived, after ${Leaderboard.RANKED_AFTER} played."
    }

    fun emptyBoard(): String = "Nobody has played ${Leaderboard.RANKED_AFTER} of these yet."

    fun rate(r: Leaderboard.Row): String = "${r.survivalPercent}% survived"

    fun figures(r: Leaderboard.Row): String = buildString {
        if (r.scripted) append("SCRIPTED · ")
        append("${r.cleared} of ${r.played} cleared · streak ${r.currentStreak} · best ${r.longestStreak}")
        if (r.cohortsNeeded > 0) append(" · ${r.cohortsNeeded} more to be ranked")
    }

    fun standing(s: Standing): String = when (s) {
        is Standing.Ranked -> "You're ranked #${s.position}"
        is Standing.NotRanked -> "You're ${s.cohortsNeeded} Holdout${if (s.cohortsNeeded == 1) "" else "s"} away from being ranked"
        is Standing.Absent -> "Play ${s.cohortsNeeded} of these Holdouts to be ranked"
    }
}
