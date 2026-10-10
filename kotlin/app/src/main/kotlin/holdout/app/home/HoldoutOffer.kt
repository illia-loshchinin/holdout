package holdout.app.home

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.core.Derive

/**
 * A Holdout open to join, as Home and the connect screen show it (FR-053):
 * **at stake** — the stakes already held — joined count and the time until
 * joining closes. Never a promised amount. The ladder screen that also listed
 * these was removed on 6 October (screen map §8): the offers already sit on Home.
 */
@Composable
fun HoldoutOffer(h: Holdout, now: Long, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = h.cohort
    HoldoutCard(if (onClick != null) modifier.clickable(onClick = onClick) else modifier) {
        if (h.isTest) TestCohortBadge(c.dayLength)
        Text(
            "${Format.holdoutTitle(c)} · ${c.repsPerDay} push-ups a day",
            style = MaterialTheme.typography.titleMedium,
            color = HoldoutColors.TextPrimary,
        )
        Text("${Format.skr(Derive.atStake(c))} at stake · ${c.memberCount} joined", style = AmountStyle, color = HoldoutColors.Accent)
        Body("Joining closes in ${Format.countdown(Derive.timeUntilJoiningCloses(c, now))} · starts in ${Format.countdown(c.start - now)}")
    }
}
