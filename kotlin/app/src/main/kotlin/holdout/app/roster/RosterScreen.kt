package holdout.app.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.Reading
import holdout.app.ui.ReportShownWallets
import holdout.app.ui.RosterRow
import holdout.app.ui.ScreenHeader
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.ScriptedWallets

/**
 * The roster (H5, board 14; FR-062, FR-064): still standing first, then the
 * fallen by days lasted, each under its own header. **No amounts anywhere** — a
 * roster ranks nobody by money (FR-063, constitution XIII). Every scripted
 * member carries `◇ SCRIPTED`, from the bundled list, with no server
 * (FR-043a), and the page says plainly that their days are not
 * camera-verified.
 *
 * ```
 *  ‹            ROSTER                          ScreenHeader · Back goes to where it was opened
 *  Habit · 21-day · day 12 of 21                title
 *  No amounts here … not camera-verified …      note
 *  STILL STANDING · 189                         SectionLabel
 *  ○ nova.skr · you            189 days ✓       RosterRow ×N
 *  OUT · 123
 *  ○ kira.skr             lasted 9 days
 * ```
 *
 * In a lobby everyone is "in" and nobody is out (`IN THE LOBBY · 14`); while
 * the roster is being read the page says so and draws placeholders.
 *
 * Members show by Seeker ID where they have one (spec 003 FR-002), looked up
 * only for the rows on screen ([onShowing], FR-012). Names never change the
 * order or a label (FR-008).
 */
@Composable
fun RosterScreen(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    me: String?,
    now: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowing: (Collection<String>) -> Unit = {},
) {
    val c = h.cohort
    val lobby = RosterText.inLobby(c, now)
    val roster = members?.let { Derive.roster(c, it, now, ScriptedWallets.keys) }
    val list = rememberLazyListState()
    ReportShownWallets(list, onShowing, ::rosterRowWallet)
    LazyColumn(
        state = list,
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(horizontal = Spacing.screenGutter),
    ) {
        item { ScreenHeader("roster", onBack = onBack, modifier = Modifier.padding(top = Spacing.screenGutter)) }
        if (h.isTest) item { TestCohortBadge(c.dayLength, Modifier.padding(top = Spacing.tight)) }
        item { Text(RosterText.title(c, now), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary, modifier = Modifier.padding(top = Spacing.betweenItems)) }
        item { Text(RosterText.NOTE, style = LabelStyle, color = HoldoutColors.TextTertiary, modifier = Modifier.padding(top = Spacing.tight, bottom = Spacing.betweenItems)) }
        if (roster == null) {
            item { Reading { SectionLabel("Reading the roster…") } }
            items(PLACEHOLDER_ROWS) { PlaceholderRow() }
            return@LazyColumn
        }
        if (lobby) {
            item { SectionLabel(RosterText.lobbyHeader(roster.size)) }
            items(roster, key = { it.member.wallet.base58() }) { entry ->
                val address = entry.member.wallet.base58()
                LobbyRosterRow(address, isYou = address == me, scripted = entry.scripted)
            }
            item { Body(RosterText.LOBBY_NOTE, modifier = Modifier.padding(vertical = Spacing.betweenItems)) }
            return@LazyColumn
        }
        val (standing, out) = roster.partition { it.standing }
        item { SectionLabel(RosterText.standingHeader(standing.size)) }
        items(standing, key = { it.member.wallet.base58() }) { entry ->
            val address = entry.member.wallet.base58()
            RosterRow(address, isYou = address == me, standing = true, days = entry.daysLasted, scripted = entry.scripted)
        }
        if (out.isNotEmpty()) {
            item { SectionLabel(RosterText.outHeader(out.size), Modifier.padding(top = Spacing.betweenSections)) }
            items(out, key = { it.member.wallet.base58() }) { entry ->
                val address = entry.member.wallet.base58()
                RosterRow(address, isYou = address == me, standing = false, days = entry.daysLasted, scripted = entry.scripted)
            }
        }
        item { Box(Modifier.height(Spacing.screenGutter)) }
    }
}

/** A row while the roster is being read: an avatar and a name's worth of bar, in `surface-3`. */
@Composable
private fun PlaceholderRow() {
    Row(
        Modifier.fillMaxWidth().height(Spacing.primaryButtonHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Box(Modifier.size(28.dp).background(HoldoutColors.Surface3, CircleShape))
        Box(Modifier.width(120.dp).height(12.dp).background(HoldoutColors.Surface3, MaterialTheme.shapes.small))
    }
    HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
}

private const val PLACEHOLDER_ROWS = 4

/**
 * The wallet a roster row names, from its `LazyColumn` key: member rows are
 * keyed by the wallet's base58, and header rows have no key of their own.
 */
internal fun rosterRowWallet(key: Any): String? = key as? String

/** Every word of the roster page that is not a name, pure so it can be tested (no dates, no amounts). */
internal object RosterText {
    const val NOTE = "No amounts here: nobody is ranked by money. ◇ SCRIPTED members follow the same rules, but their days are not camera-verified. Names are Seeker IDs (.skr); a wallet without one shows its address."
    const val LOBBY_NOTE = "Everyone is standing until the first deadline."

    /** Before the start nobody has had a deadline: the roster is the lobby. */
    fun inLobby(c: CohortSnapshot, now: Long): Boolean = Derive.status(c, now).let { it == CohortStatus.Open || it == CohortStatus.Locked }

    /** `Habit · 21-day · day 12 of 21` while it runs; the bare level in the lobby; `… · finished` after. */
    fun title(c: CohortSnapshot, now: Long): String = when (Derive.status(c, now)) {
        CohortStatus.Open, CohortStatus.Locked -> Format.holdoutName(c)
        CohortStatus.Running -> "${Format.holdoutName(c)} · day ${(Derive.dayIndex(c, now) + 1).coerceIn(1, c.durationDays)} of ${c.durationDays}"
        CohortStatus.Settling, CohortStatus.Settled -> "${Format.holdoutName(c)} · finished"
    }

    fun standingHeader(n: Int): String = "Still standing · $n"

    fun outHeader(n: Int): String = "Out · $n"

    fun lobbyHeader(n: Int): String = "In the lobby · $n"
}
