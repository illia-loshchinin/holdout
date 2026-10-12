package holdout.app.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.holdout.ProgramErrors
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.ScreenHeader
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Derive
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot

/** The words of the lobby (board 8; FR-009, ES-12, ruling G3, G9), kept apart from the screen so they are tested alone. */
object LobbyText {
    const val LATE_LEAVE = "Leaving closed before your transaction landed. You're still in."
    const val CONTRACT_HOLDS = "held by the contract, not by us"
    const val NO_POOL = "No prize pool until someone misses a day."
    const val APPROVE = "Approve in your wallet."
    const val UNTIL_CLOSES = "Until joining closes."
    const val LOCKED_NOTE = "Joining closed. This is the final roster."
    const val FOOTER = "Dollar values are approximate."

    fun name(c: CohortSnapshot, test: Boolean): String = if (test) Format.testName(c) else Format.level(c)

    /** `SPARK · 7-DAY · STARTS IN 2D 4H`; locked, `SPARK · 7-DAY · LOCKED · STARTS IN 41M 10S`. No calendar dates (ruling G3). */
    fun headerLabel(c: CohortSnapshot, test: Boolean, secondsToStart: Long, locked: Boolean): String {
        val lead = "${name(c, test)}${if (locked) " · locked" else ""} · starts in ${Format.countdown(secondsToStart)}"
        return lead.uppercase()
    }

    /** `25 push-ups a day for 7 days. At 45° only. …` — the level's own rule sentence (G2), never the other level's. */
    fun rules(c: CohortSnapshot): String = "${c.repsPerDay} push-ups a day for ${c.durationDays} days. ${Format.levelRule(c).orEmpty()}".trim()

    /** Between the cutoff and the start there is nothing to do but keep the streak; reminders are promised only when they are on (G9). */
    fun lockedFooter(notificationsOn: Boolean): String = "Until it starts, just keep your streak." + if (notificationsOn) " We'll remind you." else ""

    fun left(stake: String): String = "You left. All $stake is back in your wallet."

    /** Whether [message] is the program's "joining has closed" answer to a leave that landed after the cutoff (ES-12). */
    fun isLateLeave(message: String?): Boolean = message != null && message == ProgramErrors.explain(JOINING_CLOSED)

    private const val JOINING_CLOSED = 6021
}

/**
 * The lobby (board 8; US-2, ES-12a, T060): the countdown to the start, how
 * many have joined and what is at stake, the member's own held stake, the
 * level's rule, and **unconditional leave** until joining closes (FR-009).
 * Once it closes: the locked variant — the final roster, no leave, until the
 * start.
 *
 * ```
 *  ‹   SPARK · 7-DAY · STARTS IN 2D 4H                 ScreenHeader
 *  [DEVNET COHORT badge]
 *            Spark · 7-day
 *            STARTS IN   2d 4h  (DisplayStyle, accent)
 *            Everyone starts together.
 *  ┌ 14 joined, you too                12,500 SKR at stake ┐
 *  │ No prize pool until someone…        See who's in →    │
 *  └─────────────────────────────────────────────────────┘
 *  ┌ Your stake 1,250 SKR · held by the contract, not by us │
 *  │ Joining closes in 2d 3h   |  Joining closed. …         │
 *  └────────────────────────────────────────────────────────┘
 *  FROM THE START · 25 push-ups a day for 7 days. <the level's rule>
 *  Leave — full refund   Until joining closes.            QuietAction (warning)
 *  Dollar values are approximate.
 * ```
 *
 * Leave signs inline (G7): the button reads "Waiting for your wallet…" with
 * "Approve in your wallet." while it is out; [leftStake] is the left state
 * (the stake back in the wallet, "Back to Holdouts").
 */
@Composable
fun LobbyScreen(
    h: Holdout,
    now: Long,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onLeave: () -> Unit,
    onRoster: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The stake that came back, once the leave landed — the screen says so instead of offering the leave again. */
    leftStake: String? = null,
    /** Notifications are on: only then may the screen say "We'll remind you" (ruling G9). */
    notificationsOn: Boolean = false,
) {
    val c = h.cohort
    val test = h.isTest
    // Anything past Open is locked here: a lobby still shown at the start must not offer a leave the program refuses.
    val locked = h.status(now) != CohortStatus.Open
    val toStart = c.start - now
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections),
    ) {
        ScreenHeader(LobbyText.headerLabel(c, test, toStart, locked), onBack = onBack, backDescription = "Back")

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            if (test) TestCohortBadge(c.dayLength)
            Text(LobbyText.name(c, test), style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
            SectionLabel("starts in")
            Text(Format.countdown(toStart), style = DisplayStyle, color = HoldoutColors.Accent)
            Text("Everyone starts together.", style = LabelStyle, color = HoldoutColors.TextTertiary)
        }

        HoldoutCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${c.memberCount}", style = DisplayStyle, color = HoldoutColors.TextPrimary)
                    Text(if (h.membership != null) "joined, you too" else "joined", style = LabelStyle, color = HoldoutColors.TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(Derive.atStake(c)), style = AmountStyle, color = HoldoutColors.TextPrimary)
                    Text("at stake", style = LabelStyle, color = HoldoutColors.TextSecondary)
                }
            }
            HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(LobbyText.NO_POOL, style = LabelStyle, color = HoldoutColors.TextTertiary, modifier = Modifier.weight(1f))
                QuietAction("See who's in →", onRoster)
            }
        }

        HoldoutCard {
            h.membership?.let { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Your stake", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.skr(m.stake), style = AmountStyle, color = HoldoutColors.TextPrimary)
                        Text(LobbyText.CONTRACT_HOLDS, style = LabelStyle, color = HoldoutColors.TextTertiary)
                    }
                }
                HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
            }
            if (locked) {
                Body(LobbyText.LOCKED_NOTE)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Joining closes in", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                    Text(Format.countdown(Derive.timeUntilJoiningCloses(c, now)), style = AmountStyle, color = HoldoutColors.Accent)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HoldoutColors.Surface3, MaterialTheme.shapes.small)
                .padding(Spacing.betweenItems),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            SectionLabel("From the start")
            Text(LobbyText.rules(c), style = LabelStyle, color = HoldoutColors.TextPrimary)
        }

        val late = LobbyText.isLateLeave(message)
        when {
            leftStake != null -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
                Text(LobbyText.left(leftStake), style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                PrimaryButton("Back to Holdouts", onBack)
            }
            else -> {
                if (late) Notice(LobbyText.LATE_LEAVE, warning = true) else Notice(message, messageIsWarning)
                if (busy) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                        PrimaryButton(Format.WAITING_FOR_WALLET, {}, busy = true)
                        Text(LobbyText.APPROVE, style = LabelStyle, color = HoldoutColors.TextTertiary)
                    }
                } else if (!locked) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QuietAction("Leave — full refund", onLeave, warning = true)
                        Text(LobbyText.UNTIL_CLOSES, style = LabelStyle, color = HoldoutColors.TextTertiary)
                    }
                } else {
                    Text(LobbyText.lockedFooter(notificationsOn), style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Text(LobbyText.FOOTER, style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private val PREVIEW_CHIPS = listOf(500uL, 1_250uL, 2_500uL, 5_000uL).map { it * 1_000_000uL }
private const val PREVIEW_START = 1_791_000_000L

private fun previewHolder(joinCutoff: Long) = Holdout(
    address = SolanaPublicKey(ByteArray(32) { 1 }),
    cohort = CohortSnapshot(0, 1, PREVIEW_START, joinCutoff, 7, 25, 86_400, 0, PREVIEW_CHIPS, memberCount = 14, heldStake = 12_500uL * 1_000_000uL, form = LeagueParams.FORM_KNEES_ALLOWED),
    league = null,
    membership = MembershipSnapshot(SolanaPublicKey(ByteArray(32) { 2 }), 1, PREVIEW_CHIPS[1], PREVIEW_START - 100_000, 0, MembershipSnapshot.State.Joined),
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun LobbyOpenPreview() {
    HoldoutTheme { LobbyScreen(previewHolder(PREVIEW_START - 3_600), PREVIEW_START - 190_000, busy = false, message = null, messageIsWarning = false, onLeave = {}, onRoster = {}, onBack = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun LobbyLockedPreview() {
    HoldoutTheme { LobbyScreen(previewHolder(PREVIEW_START - 3_600), PREVIEW_START - 2_470, busy = false, message = null, messageIsWarning = false, onLeave = {}, onRoster = {}, onBack = {}, notificationsOn = true) }
}
