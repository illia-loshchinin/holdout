package holdout.app.home

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.core.Derive
import holdout.core.MembershipSnapshot

/**
 * A running Holdout, watched from outside: its length and day, how many are
 * still standing of how many started, and the prize pool. Read-only — it offers no
 * join. On the connect screen's "Live now" list and Home's "Running now" list
 * (FR-062a), where [onClick] opens its detail and roster.
 *
 * The figures come from the roster and chain time, as every other screen: the
 * raw account lags a missed deadline until the automation records it
 * (FR-038a). Until the roster is read, the account's own count shows.
 */
@Composable
fun RunningHoldoutCard(
    h: Holdout,
    now: Long,
    roster: List<MembershipSnapshot>?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val figures = roster?.let { Derive.figures(h.cohort, it, now) }
    val day = (Derive.dayIndex(h.cohort, now) + 1).coerceIn(1, h.cohort.durationDays)
    HoldoutCard(if (onClick != null) modifier.clickable(onClickLabel = "Watch this Holdout", onClick = onClick) else modifier) {
        if (h.isTest) TestCohortBadge(h.cohort.dayLength)
        Text("${Format.holdoutTitle(h.cohort)} · day $day of ${h.cohort.durationDays}", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        Text(
            figures?.let { "${it.survivors.standing} still standing of ${it.survivors.starters} · prize pool ${Format.skr(it.projectedPool)}" }
                ?: "${h.cohort.memberCount} started · prize pool ${Format.skr(h.cohort.pool)}",
            style = AmountStyle,
            color = HoldoutColors.Accent,
        )
    }
}
