package holdout.app.stake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import holdout.app.chain.Holdout
import holdout.app.faucet.FaucetClient
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.TierChip
import holdout.app.ui.TierChips
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Comeback
import holdout.core.Derive
import holdout.core.Journey

/**
 * The stake confirm, the content of the bottom sheet (board 4 · 4c; ruling G8;
 * US-6, FR-041, FR-059): what the member joins, the four chips with their exact
 * SKR and approximate dollar labels, what is at stake now, the rule of counting
 * and where a lost stake goes — all read **before anything is staked**
 * (FR-021a). There is no "← Back": the sheet closes by drag or scrim.
 *
 * ```
 *  [DEVNET COHORT badge]
 *  Join the Spark · 7-day Holdout                      headlineMedium
 *  Light it up: 25 push-ups a day for 7 days.          Body
 *  ( Starts in 2d 4h )                                 accent tint pill
 *  [≈ $10] [≈ $25] [≈ $50] [≈ $100]                    TierChips
 *  Finish it and 125 SKR of the stake you lost comes back.   success (comeback only)
 *  ┌ Your stake        1,250 SKR  ≈ $25 ┐
 *  │ At stake now     12,500 SKR  14 joined │
 *  │ Joining closes in             2d 3h │
 *  └──────────────────────────────────────┘
 *  If nobody drops out, you get exactly your stake back.
 *  Miss a day and you lose your stake: 70 % …            fee sentence
 *  Full refund until joining closes.
 *  Allow notifications and … (notifications off only)
 *  ┌ HOW REPS ARE COUNTED HERE · the level's rule ┐
 *  Joining closes in 8m 30s. A transaction signed now may land too late.   warning, last minutes
 *  [ Stake 1,250 SKR ]                                  PrimaryButton, or why not + Get test tokens
 *  Test network, test token. Dollar values are approximate.
 * ```
 *
 * **A comeback round** (FR-037g, T185): opened from the forfeit screen or
 * Today's comeback card, the sheet starts on [initialTier], the tier of the
 * lost stake, and says what finishing it brings back for the chip chosen — "up
 * to", capped at the new stake (FR-037e).
 */
@Composable
fun StakeSheet(
    h: Holdout,
    skrBalance: ULong,
    /** The wallet's SOL, in lamports: the member pays the join's fee and rent. */
    lamports: Long,
    now: Long,
    joinBlocker: Journey.JoinBlocker?,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onStake: (tier: Int) -> Unit,
    onGetTokens: () -> Unit,
    modifier: Modifier = Modifier,
    /** The chip the sheet starts on: the lost stake's, when opened from the comeback offer (0..3). */
    initialTier: Int = 0,
    /** Told of every chip the member picks, so the pick outlives the sheet (the faucet round trip, ES-0a). */
    onTierChange: (Int) -> Unit = {},
    /** The member's comeback offer, if any; its exact amount shows only when [h] is one of its rounds. */
    comeback: Comeback.Offer? = null,
    /** Where "See your lobby" goes, when the blocker is a place already held in a lobby. */
    onSeeLobby: (() -> Unit)? = null,
    /** The notification reason (FR-068a) shows only while notifications are off (ruling G9). */
    showNotificationReason: Boolean = true,
) {
    val c = h.cohort
    val test = h.isTest
    var tier by remember(h.address) { mutableIntStateOf(initialTier.coerceIn(0, c.chips.lastIndex)) }
    val chip = c.chips[tier]
    // The member's comeback round, started or locked, stays open to them to the end of its first day (FR-037e).
    val lateRound = comeback?.rounds?.firstOrNull { it.lateJoin && it.entry?.address == h.address }
    val closesIn = lateRound?.let { (it.closesAt - now).coerceAtLeast(0) } ?: Derive.timeUntilJoiningCloses(c, now)
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.betweenSections),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            if (test) TestCohortBadge(c.dayLength)
            Text(StakeText.title(c, test), style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
            Body(StakeText.levelLine(c, test))
            Text(
                if (c.start <= now) "Started today" else "Starts in ${Format.countdown(c.start - now)}",
                style = LabelStyle,
                color = HoldoutColors.Accent,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(HoldoutColors.AccentTint)
                    .border(Spacing.border, HoldoutColors.Accent, MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.betweenItems, vertical = Spacing.tight),
            )
        }

        TierChips(
            tiers = c.chips.mapIndexed { i, amount -> TierChip(Format.tierUsd(i), Format.skr(amount)) },
            selected = tier,
            onSelect = {
                tier = it
                onTierChange(it)
            },
        )
        if (comeback != null && comeback.rounds.any { it.entry?.address == h.address }) {
            Text(StakeText.comeback(comeback.amount(chip)), style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success)
        }

        HoldoutCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your stake", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(chip), style = AmountStyle, color = HoldoutColors.TextPrimary)
                    Text(Format.tierUsd(tier), style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
            HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("At stake now", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(Derive.atStake(c)), style = AmountStyle, color = HoldoutColors.TextPrimary)
                    Text("${c.memberCount} joined", style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Joining closes in", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                Text(Format.countdown(closesIn), style = AmountStyle, color = HoldoutColors.Accent)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(StakeText.NO_DROPOUT, style = LabelStyle, color = HoldoutColors.TextPrimary)
            Text(StakeText.fee(c, test), style = LabelStyle, color = HoldoutColors.TextSecondary)
            Text(StakeText.FULL_REFUND, style = LabelStyle, color = HoldoutColors.TextSecondary)
            if (showNotificationReason) Text(StakeText.NOTIFICATION_REASON, style = LabelStyle, color = HoldoutColors.TextTertiary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(HoldoutColors.Surface3)
                .padding(horizontal = Spacing.betweenItems, vertical = Spacing.betweenItems),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            SectionLabel("How reps are counted here")
            Text(Format.levelRule(c) ?: Format.levelRule(holdout.core.LeagueParams.FORM_TOES).orEmpty(), style = LabelStyle, color = HoldoutColors.TextPrimary)
        }

        StakeBottom(
            closingWarning = StakeText.closingWarning(closesIn),
            joinBlocker = joinBlocker,
            short = skrBalance < chip,
            held = skrBalance,
            chip = chip,
            noFees = !FaucetClient.canPayFees(lamports),
            busy = busy,
            message = message,
            messageIsWarning = messageIsWarning,
            onStake = { onStake(tier) },
            onGetTokens = onGetTokens,
            onSeeLobby = onSeeLobby,
        )

        Text(StakeText.FOOTER, style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The ES-12 warning, why the join cannot go ahead (if it cannot), and the stake button. */
@Composable
private fun StakeBottom(
    closingWarning: String?,
    joinBlocker: Journey.JoinBlocker?,
    short: Boolean,
    held: ULong,
    chip: ULong,
    noFees: Boolean,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onStake: () -> Unit,
    onGetTokens: () -> Unit,
    onSeeLobby: (() -> Unit)?,
) {
    closingWarning?.let { Notice(it, warning = true) }
    when {
        joinBlocker != null -> {
            Notice(StakeText.blocker(joinBlocker), warning = true)
            if (joinBlocker == Journey.JoinBlocker.LobbyPlaceTaken && onSeeLobby != null) {
                QuietAction("See your lobby", onSeeLobby)
            }
        }
        short -> {
            Notice(StakeText.shortOfSkr(held, chip), warning = true)
            SecondaryButton("Get test tokens", onGetTokens)
        }
        noFees -> {
            Notice(StakeText.SHORT_OF_SOL, warning = true)
            SecondaryButton("Get test tokens", onGetTokens)
        }
    }
    Notice(message, messageIsWarning)
    PrimaryButton(
        text = if (busy) Format.WAITING_FOR_WALLET else "Stake ${Format.skr(chip)}",
        onClick = onStake,
        enabled = joinBlocker == null && !short && !noFees,
        busy = busy,
    )
}
