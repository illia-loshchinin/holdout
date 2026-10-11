package holdout.app.stake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.HoldoutLoader
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.QuietAction
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * What a join shows in the stake sheet instead of the stake confirm while the
 * wallet is not known to hold a Seeker Genesis Token (spec FR-003b–FR-003d,
 * ES-0c, ES-0d, contracts/android-app §5; board 4 · Seeker check). Staked
 * Holdouts are Seeker-exclusive; the Daily 25 and watching every Holdout stay
 * open to everyone.
 *
 * ```
 *  SEEKER ONLY                                         SectionLabel
 *
 *  Checking:      (ring)  Checking your Seeker…        HoldoutLoader · Body
 *
 *  NotSeeker:     Holdouts are for Seeker owners       headlineMedium
 *                 ┌ The Daily 25 stays free, and you can follow every Holdout. ┐
 *                 [ Get a Seeker ]                     PrimaryButton → the Seeker store
 *                 Not now                              QuietAction → closes the sheet
 *
 *  CouldNotCheck: We couldn't check your Seeker — try again.   Notice, warning (FR-003d)
 *                 [ Try again ]                        PrimaryButton
 * ```
 *
 * Content of the sheet, which closes by drag or scrim: no back button. Nothing
 * on it is signed; the stake confirm opens only once the wallet is confirmed.
 */
@Composable
fun SeekerGateSheet(
    status: SeekerStatus,
    onGetSeeker: () -> Unit,
    onRetry: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.betweenSections),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        SectionLabel("Seeker only")
        when (status) {
            SeekerStatus.Checking, SeekerStatus.Confirmed -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.betweenSections),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
                ) {
                    HoldoutLoader(size = LOADER, contentDescription = "Checking your Seeker")
                    Body("Checking your Seeker…", center = true)
                }
            }
            SeekerStatus.NotSeeker -> {
                Text("Holdouts are for Seeker owners", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
                HoldoutCard { Body("The Daily 25 stays free, and you can follow every Holdout.") }
                PrimaryButton("Get a Seeker", onGetSeeker)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QuietAction("Not now", onNotNow) }
            }
            SeekerStatus.CouldNotCheck -> {
                Notice("We couldn't check your Seeker — try again.", warning = true)
                PrimaryButton("Try again", onRetry)
            }
        }
    }
}

private val LOADER = 56.dp

@Preview(showBackground = true, backgroundColor = 0xFF121B1D)
@Composable
private fun CheckingPreview() {
    HoldoutTheme { SeekerGateSheet(SeekerStatus.Checking, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF121B1D)
@Composable
private fun NotSeekerPreview() {
    HoldoutTheme { SeekerGateSheet(SeekerStatus.NotSeeker, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF121B1D)
@Composable
private fun CouldNotCheckPreview() {
    HoldoutTheme { SeekerGateSheet(SeekerStatus.CouldNotCheck, {}, {}, {}) }
}
