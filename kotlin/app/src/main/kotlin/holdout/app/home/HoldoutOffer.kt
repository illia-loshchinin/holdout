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
 * joining closes, and the prize boost the league's reserve can add when it
 * settles (FR-037d), as "up to". Never a promised amount. The ladder screen that also listed
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
        boostLine(h.league?.boostReserve, Format.level(c))?.let { Text(it, style = AmountStyle, color = HoldoutColors.Success) }
    }
}

/**
 * What the league's boost reserve can add to this Holdout's prize pool when it
 * settles (FR-053, FR-037d), or nothing while the reserve is empty or unread.
 * "Up to": the draw is capped at 10% of the pool and paid only if someone
 * forfeits, so it is never a promised amount. The reserve is named by its
 * Holdouts' [level] ("Easy · 7-day"), never "league" (FR-041).
 */
internal fun boostLine(boostReserve: ULong?, level: String): String? = boostReserve?.takeIf { it > 0uL }?.let { "Prize boost: up to ${Format.skr(it)} from the reserve of $level Holdouts when it settles" }
