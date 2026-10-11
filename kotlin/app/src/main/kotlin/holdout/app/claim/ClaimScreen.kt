package holdout.app.claim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.ChipTone
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.KeyValueRow
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.ScreenHeader
import holdout.app.ui.StatBlock
import holdout.app.ui.StatusChip
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Stage

/**
 * The claim (H7, board 7; US-8): what you held out, the amount, what it is made
 * of — stake back, share of the prize pool, a comeback — and one deliberate tap.
 * Claimable indefinitely (FR-035), so the footer says it waits. No date and no
 * settled time (constitution VI, ruling D-D1); the signature itself happens in
 * Holdout's own signing flow (FR-070), which the button opens.
 *
 * ```
 *  ‹  SPARK · 7-DAY · FINISHED              [SETTLED]    ScreenHeader · StatusChip
 *  Spark lit: you held out 7 days.                       opener, by level
 *  1,412                                                 StatBlock counter
 *  SKR · YOURS TO CLAIM
 *  ┌ Stake back              1,250 SKR ┐                 KeyValueRows
 *  │ Share of the prize pool   162 SKR │
 *  │ Comeback (part of your last lost stake, back) 250 SKR │
 *  │ ───────────────────────────────── │
 *  └ 9 of 20 dropped out. The 11 who finished split the pool and its boost. ┘
 *  [ Claim 1,412 SKR ]
 *  One signature. It waits here until you claim.
 * ```
 *
 * Short of devnet SOL for the fee, the button waits behind **Get test tokens**
 * (ES-0a). A Holdout already claimed shows the same summary with its explorer
 * link and the one gradient sweep (design book, principle 4).
 */
@Composable
fun ClaimScreen(
    h: Holdout,
    stage: Stage,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    lastClaimSignature: String?,
    onClaim: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The wallet has too little SOL to pay the claim's fee: the faucet comes first (ES-0a). */
    shortOfSol: Boolean = false,
    onGetTokens: () -> Unit = {},
) {
    val c = h.cohort
    val claimed = stage as? Stage.Claimed
    val ready = stage as? Stage.ReadyToClaim
    val amount = claimed?.amount ?: ready?.amount ?: 0uL
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(claimed != null) { if (claimed != null) sweep.animateTo(1f, tween(GRADIENT_MILLIS)) }
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        ScreenHeader(Format.finishedHeaderLabel(c), onBack = onBack, trailing = { StatusChip("settled", tone = ChipTone.Success) })
        if (h.isTest) TestCohortBadge(c.dayLength, Modifier.align(Alignment.CenterHorizontally))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Body(ClaimText.opener(c), center = true)
            StatBlock(
                value = ClaimText.amountNumber(amount),
                caption = if (claimed != null) ClaimText.CLAIMED_CAPTION else ClaimText.AMOUNT_CAPTION,
                valueColor = if (claimed != null) HoldoutColors.Success else HoldoutColors.TextPrimary,
            )
            if (claimed != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.base)
                        .drawBehind {
                            drawRect(
                                brush = Brush.horizontalGradient(listOf(HoldoutColors.GradientStart, HoldoutColors.GradientEnd)),
                                size = Size(size.width * sweep.value, size.height),
                            )
                        },
                )
            }
        }

        // What the amount is made of, before the claim and after it alike (UC-10).
        val breakdown = ready?.let { Triple(it.stake, it.share, it.comeback) } ?: claimed?.let { Triple(it.stake, it.share, it.comeback) }
        breakdown?.takeIf { (stake) -> stake > 0uL }?.let { (stake, share, comeback) ->
            HoldoutCard {
                PayoutRows(stake, share, comeback)
                ClaimText.droppedLine(c)?.let { line ->
                    HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
                    Text(line, style = LabelStyle, color = HoldoutColors.TextSecondary)
                }
            }
        }

        if (claimed != null) {
            lastClaimSignature?.let { sig ->
                // Tapped, it opens in the browser; a phone with none leaves the address to read.
                QuietAction("${Format.short(sig)} · view on explorer →", { runCatching { uriHandler.openUri(Format.explorer(sig)) } })
            }
        }
        Notice(message, messageIsWarning)

        if (ready != null) {
            if (shortOfSol) {
                HoldoutCard {
                    Body(ClaimText.NO_SOL)
                    QuietAction(ClaimText.GET_TOKENS, onGetTokens)
                }
                PrimaryButton(ClaimText.button(ready.amount), onClaim, enabled = false)
            } else {
                PrimaryButton(if (busy) Format.WAITING_FOR_WALLET else ClaimText.button(ready.amount), onClaim, busy = busy)
            }
            Text(ClaimText.FOOTER, style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * What a payout is made of (H7): the stake back, the share of the prize pool
 * and, when owed, the comeback refund — part of the stake lost in the last
 * Holdout of this level and length, earned back by finishing this one
 * (FR-037e, H35). Shown before the claim and after it (UC-10), on the claim
 * and on the detail of a claimed Holdout.
 */
@Composable
fun PayoutRows(stake: ULong, share: ULong, comeback: ULong) {
    KeyValueRow("Stake back", Format.skr(stake))
    if (share > 0uL) KeyValueRow("Share of the prize pool", Format.skr(share), success = true)
    if (comeback > 0uL) {
        KeyValueRow("Comeback", Format.skr(comeback), success = true)
        Text(Format.COMEBACK_CLAIM_NOTE, style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

private const val GRADIENT_MILLIS = 1_200
