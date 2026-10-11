package holdout.app.stake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.HoldoutLoader
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * What a join shows instead of the stake confirm while the wallet is not
 * known to hold a Seeker Genesis Token (spec FR-003b–FR-003d, ES-0c, ES-0d,
 * contracts/android-app §5). Staked Holdouts are Seeker-exclusive; the
 * Daily 25 and watching every Holdout stay open to everyone.
 *
 * ```
 *  ← Back                                   TextButton, text-secondary
 *  SEEKER ONLY                              SectionLabel
 *
 *  Checking:      [loader]  Checking your Seeker…           HoldoutLoader · Body
 *
 *  NotSeeker:     Holdouts are for Seeker owners            headlineMedium
 *                 ┌ Staking in a Holdout needs a Seeker.  ┐ HoldoutCard · Body
 *                 └ You can still do the Daily 25 and … ┘
 *                 [ Get a Seeker ]                          PrimaryButton → the Seeker store
 *                 Not now                                   QuietAction (accent)
 *
 *  CouldNotCheck: ! We couldn't check your Seeker — try again.   Notice, warning
 *                 [ Try again ]                             PrimaryButton
 * ```
 *
 * Nothing on it is signed; the stake confirm opens only once the wallet is
 * confirmed.
 */
@Composable
fun SeekerGateSheet(
    status: SeekerStatus,
    onGetSeeker: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Back", color = HoldoutColors.TextSecondary) }
        SectionLabel("Seeker only")
        when (status) {
            SeekerStatus.Checking, SeekerStatus.Confirmed -> {
                HoldoutLoader(contentDescription = "Checking your Seeker")
                Body("Checking your Seeker…", center = true)
            }
            SeekerStatus.NotSeeker -> {
                Text("Holdouts are for Seeker owners", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
                HoldoutCard {
                    Body("Staking in a Holdout needs a Seeker. You can still do the Daily 25 and watch every Holdout.")
                }
                PrimaryButton("Get a Seeker", onGetSeeker)
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Spacing.touchTarget)) {
                    Text("Not now", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = HoldoutColors.Accent)
                }
            }
            SeekerStatus.CouldNotCheck -> {
                Notice("We couldn't check your Seeker — try again.", warning = true)
                PrimaryButton("Try again", onRetry)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CheckingPreview() {
    HoldoutTheme { SeekerGateSheet(SeekerStatus.Checking, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun NotSeekerPreview() {
    HoldoutTheme { SeekerGateSheet(SeekerStatus.NotSeeker, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CouldNotCheckPreview() {
    HoldoutTheme { SeekerGateSheet(SeekerStatus.CouldNotCheck, {}, {}, {}) }
}
