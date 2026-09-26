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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.Stage

/**
 * The claim (PRD §10.1 screen 10, §11.5, T081): the amount, the breakdown —
 * stake plus share — and one deliberate tap. On success, **the single gradient
 * sweep** (one of the two moments the gradient is reserved for) and the
 * settled transaction's link. Claimable indefinitely (FR-035).
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
) {
    val claimed = stage as? Stage.Claimed
    val ready = stage as? Stage.ReadyToClaim
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(claimed != null) { if (claimed != null) sweep.animateTo(1f, tween(1_200)) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Home", color = HoldoutColors.TextSecondary) }
        if (h.isTest) TestCohortBadge(h.cohort.dayLength)
        SectionLabel(if (claimed != null) "Claimed" else "You finished")
        Text(Format.skr(claimed?.amount ?: ready?.amount ?: 0uL), style = DisplayStyle, color = if (claimed != null) HoldoutColors.Success else HoldoutColors.TextPrimary)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(HoldoutColors.GradientStart, HoldoutColors.GradientEnd)),
                        size = Size(size.width * sweep.value, size.height),
                    )
                },
        )
        ready?.let {
            HoldoutCard {
                Body("Your stake back: ${Format.skr(it.stake)}")
                Body("Your share of the prize pool: ${Format.skr(it.share)}")
            }
        }
        (lastClaimSignature ?: null)?.takeIf { claimed != null }?.let { Body("Transaction: ${Format.explorer(it)}") }
        Notice(message, messageIsWarning)
        if (ready != null) PrimaryButton(if (busy) "Waiting for the wallet…" else "Claim ${Format.skr(ready.amount)}", onClaim, enabled = !busy)
    }
}
