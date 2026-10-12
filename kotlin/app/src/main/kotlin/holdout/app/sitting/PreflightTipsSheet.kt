package holdout.app.sitting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.HoldoutSheet
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
import holdout.repcounter.PreflightCheck
import holdout.repcounter.Stakes

/**
 * The pre-flight tip sheet (screen map S3, Gap 1): "Getting the checks green", opened from the
 * "Still not green? Tips" button once the sitting has been short of green long enough or often enough.
 *
 * ```
 *  ───                                     ← the shared sheet (HoldoutSheet, board 16)
 *  Getting the checks green                ← title
 *  (✓ Lighting)                            ← PreflightChip, as the pill is
 *  Add light                               ← that check's own fix sentence
 *  (! Camera view)                         ← red now: warning colour, "Not green yet" to a screen reader
 *  Move the phone so it can see all of you
 *  …
 *  [ Open the set-up guide ]               ← SecondaryButton: the placement guide, as the S3 link does
 *  [ Back to the checks    ]               ← PrimaryButton
 * ```
 * Closes by its button, system Back (which wins over the sitting's own Back while the sheet is open),
 * a tap outside or a drag; the checks keep running underneath and closing starts nothing.
 */
@Composable
internal fun BoxScope.PreflightTipsSheet(tips: List<PreflightTip>, onSetup: () -> Unit, onClose: () -> Unit) {
    // Composed after the sitting's own handler, so while the sheet is open Back closes it and does not stop the sitting.
    BackHandler(onBack = onClose)
    HoldoutSheet(onDismiss = onClose, dismissLabel = "Close the tips") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.tight, bottom = Spacing.betweenSections),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            Text("Getting the checks green", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
            // The rows scroll when the phone is short; the buttons stay in reach under them.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
            ) {
                tips.forEach { TipRow(it) }
            }
            SecondaryButton("Open the set-up guide", onSetup)
            PrimaryButton("Back to the checks", onClose)
        }
    }
}

/** A check's pill, then the sentence that fixes it; the whole row is one thing to a screen reader, with the mark when red. */
@Composable
private fun TipRow(tip: PreflightTip) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (tip.red) "${tip.label}, Not green yet. ${tip.fix}" else "${tip.label}, ok. ${tip.fix}"
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        PreflightChip(PreflightPill(tip.label, passing = !tip.red))
        Text(
            text = tip.fix,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tip.red) HoldoutColors.Warning else HoldoutColors.TextSecondary,
        )
    }
}

private fun previewTips(vararg red: PreflightCheck) = PreflightCheck.entries.map { PreflightTip(it.label, it.fix, red = it in red) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun PreflightTipsPreview() {
    HoldoutTheme {
        Box(Modifier.fillMaxSize()) {
            PreflightContent(
                PreflightUi(
                    header = SittingHeader(SittingCopy.positionLabel(12), 4 * 3_600L + 6 * 60),
                    pills = PreflightCheck.entries.map { PreflightPill(it.label, it != PreflightCheck.CAMERA_ANGLE) },
                    coach = "Phone to 45°",
                    sub = PreflightCheck.CAMERA_ANGLE.fix,
                    rule = SittingCopy.ruleCaption(Stakes.STAKED),
                    tipsOffered = true,
                ),
                onStop = {},
                onSetup = {},
            )
            PreflightTipsSheet(previewTips(PreflightCheck.CAMERA_ANGLE, PreflightCheck.LIGHTING), onSetup = {}, onClose = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun PreflightTipsAllRedPreview() {
    HoldoutTheme { Box(Modifier.fillMaxSize()) { PreflightTipsSheet(previewTips(*PreflightCheck.entries.toTypedArray()), onSetup = {}, onClose = {}) } }
}
