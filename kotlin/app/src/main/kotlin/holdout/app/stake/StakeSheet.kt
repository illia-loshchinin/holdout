package holdout.app.stake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 *  Join the Spark · 7-day Holdout                      titleMedium, one line
 *  Light it up: 25 push-ups a day for 7 days.          Body
 *  ( Starts in 2d 4h )                                 accent tint pill
 *  [≈ $10] [≈ $25] [≈ $50] [≈ $100]                    TierChips: the stake itself
 *  Finish it and 125 SKR of the stake you lost comes back.   success (comeback only)
 *  ┌ At stake now     12,500 SKR  14 joined │
 *  └ Joining closes in             2d 3h ┘              warning colour in the last minutes
 *  If nobody drops out, you get exactly your stake back.
 *  Miss a day and you lose your stake: 70 % …            fee sentence
 *  Full refund until joining closes.
 *  At 45° only. Aim for full push-ups from your toes; …  the level's rule
 *  Allow notifications and … (notifications off only)
 *  A transaction signed now may land too late.          warning, last minutes
 *  [ Stake 1,250 SKR ]                                  PrimaryButton; a join that can't go ahead
 *                                                        says why, its one way on in the button's place
 *  Test network, test token. Dollar values are approximate.
 * ```
 *
 * **It fits a Seeker screen unscrolled** in every state (owner, 12 Oct;
 * StakeSheetFitTest): no row repeats what the chips or the button say, the
 * rule is a line rather than a box, and a blocked join shows one button.
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
    /** False while a join of this Holdout may still land: Stake stays shut, [message] says why (FR-070a). */
    canStake: Boolean = true,
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
    val closingWarning = StakeText.closingWarning(closesIn)
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            if (test) TestCohortBadge(c.dayLength)
            Text(StakeText.title(c, test), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
            Body(StakeText.levelLine(c, test))
            Text(
                if (c.start <= now) "Started today" else "Starts in ${Format.countdown(c.start - now)}",
                style = LabelStyle,
                color = HoldoutColors.Accent,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(HoldoutColors.AccentTint)
                    .border(Spacing.border, HoldoutColors.Accent, MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.betweenItems, vertical = Spacing.base),
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

        // The stake itself is the chip picked and the button's own words, so no row repeats it.
        HoldoutCard(padding = PaddingValues(horizontal = Spacing.cardPadding, vertical = Spacing.betweenItems)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("At stake now", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(Derive.atStake(c)), style = AmountStyle, color = HoldoutColors.TextPrimary)
                    Text("${c.memberCount} joined", style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Joining closes in", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                Text(Format.countdown(closesIn), style = AmountStyle, color = if (closingWarning != null) HoldoutColors.Warning else HoldoutColors.Accent)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(StakeText.NO_DROPOUT, style = LabelStyle, color = HoldoutColors.TextPrimary)
            Text(StakeText.fee(c, test), style = LabelStyle, color = HoldoutColors.TextSecondary)
            Text(StakeText.FULL_REFUND, style = LabelStyle, color = HoldoutColors.TextSecondary)
            // How reps are counted here: the level's rule, a line of its own.
            Text(Format.levelRule(c) ?: Format.levelRule(holdout.core.LeagueParams.FORM_TOES).orEmpty(), style = LabelStyle, color = HoldoutColors.TextPrimary)
            if (showNotificationReason) Text(StakeText.NOTIFICATION_REASON, style = LabelStyle, color = HoldoutColors.TextTertiary)
        }

        StakeBottom(
            closingWarning = closingWarning,
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
            canStake = canStake,
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
    canStake: Boolean,
) {
    closingWarning?.let { Notice(it, warning = true) }
    // A join that can't go ahead says why, and its one way on takes the stake button's place.
    when {
        joinBlocker != null -> {
            Notice(StakeText.blocker(joinBlocker), warning = true)
            if (joinBlocker == Journey.JoinBlocker.LobbyPlaceTaken && onSeeLobby != null) {
                PrimaryButton("See your lobby", onSeeLobby)
            } else {
                PrimaryButton("Stake ${Format.skr(chip)}", onStake, enabled = false)
            }
        }
        short -> {
            Notice(StakeText.shortOfSkr(held, chip), warning = true)
            PrimaryButton("Get test tokens", onGetTokens)
        }
        noFees -> {
            Notice(StakeText.SHORT_OF_SOL, warning = true)
            PrimaryButton("Get test tokens", onGetTokens)
        }
        else -> {
            Notice(message, messageIsWarning)
            PrimaryButton(
                text = if (busy) Format.WAITING_FOR_WALLET else "Stake ${Format.skr(chip)}",
                onClick = onStake,
                enabled = canStake,
                busy = busy,
            )
        }
    }
}
