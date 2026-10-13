package holdout.app.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.chain.Holdout
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * A Holdout open to join, as Today and the connect screen show it (FR-053,
 * board 3): **at stake** and joined, how long joining stays open, and the prize
 * boost the level's reserve can add when it settles (FR-037d), as "up to" —
 * never a promised amount. With [onClick] the card carries a **Join** button;
 * without it (the connect screen) the card only shows.
 */
@Composable
fun HoldoutOffer(h: Holdout, now: Long, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    OfferCard(OfferCardState.of(h, now), onClick, modifier)
}

/**
 * ```
 *  ┌ [DEVNET COHORT badge]                                 ┐
 *  │ Spark · 7-day                               ( Join )  │  titleMedium · outlined pill
 *  │ Light it up · knees allowed                           │  LabelStyle, text-secondary
 *  │ 12,500 SKR  at stake · 14 joined                      │  AmountStyle · LabelStyle
 *  │ Joining closes in 2d 3h                               │  LabelStyle, accent
 *  │ Prize boost: up to 480 SKR                            │  LabelStyle, success
 *  └───────────────────────────────────────────────────────┘
 * ```
 */
@Composable
fun OfferCard(offer: OfferCardState, onJoin: (() -> Unit)?, modifier: Modifier = Modifier) {
    // The whole card opens the stake sheet, as Join does (owner, 12 Oct): a tap anywhere on the offer means "this one".
    HoldoutCard(if (onJoin != null) modifier.clickable(onClickLabel = "Join ${offer.title}", onClick = onJoin) else modifier) {
        if (offer.test) TestCohortBadge(offer.testDayLength)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text(offer.title, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                Text(offer.tagline, style = LabelStyle, color = HoldoutColors.TextSecondary)
            }
            if (onJoin != null) JoinPill(offer.title, onJoin)
        }
        // Under the title row, not beside the button: the stake line gets the card's whole width and stays on one line.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(offer.atStake, style = AmountStyle, color = HoldoutColors.TextPrimary)
            Text(offer.joined, style = LabelStyle, color = HoldoutColors.TextSecondary)
        }
        Text(offer.timing, style = LabelStyle, color = HoldoutColors.Accent)
        offer.boost?.let { Text(it, style = LabelStyle, color = HoldoutColors.Success) }
    }
}

/** The outlined **Join** pill both offer cards carry; its accessible name says which Holdout. */
@Composable
private fun JoinPill(title: String, onJoin: () -> Unit) {
    OutlinedButton(
        onClick = onJoin,
        modifier = Modifier
            .defaultMinSize(minWidth = Spacing.touchTarget)
            .height(JOIN_HEIGHT)
            .semantics { contentDescription = "Join $title" },
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Spacing.border, HoldoutColors.Hairline),
    ) {
        Text("Join", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
    }
}

/**
 * The one offer on Today with no Holdout running (FR-053, 2026-10-13): the first in Holdouts' order, compact,
 * with no tagline and no boost line (those are on Holdouts), so Today fits one Seeker screen.
 *
 * ```
 *  ┌ [DEVNET COHORT badge]                         ┐
 *  │ Spark · 7-day                       ( Join )  │  titleMedium · outlined pill
 *  │ 12,500 SKR at stake                           │  AmountStyle · LabelStyle
 *  │ 14 joined · closes in 2d 3h                   │  LabelStyle, text-tertiary
 *  └───────────────────────────────────────────────┘
 * ```
 * A tap anywhere on the card does what Join does; with [onJoin] null (a wallet known not to hold a Seeker)
 * the card only shows.
 */
@Composable
fun CompactOfferCard(offer: OfferCardState, onJoin: (() -> Unit)?, modifier: Modifier = Modifier) {
    HoldoutCard(
        if (onJoin != null) modifier.clickable(onClickLabel = "Join ${offer.title}", onClick = onJoin) else modifier,
        padding = PaddingValues(start = Spacing.cardPadding, top = Spacing.betweenItems + Spacing.base, end = Spacing.betweenItems + Spacing.base, bottom = Spacing.betweenItems + Spacing.base),
    ) {
        if (offer.test) TestCohortBadge(offer.testDayLength)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text(offer.title, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    Text(offer.atStake, style = AmountStyle.copy(fontSize = 14.sp, lineHeight = 18.sp), color = HoldoutColors.TextPrimary)
                    Text("at stake", style = LabelStyle, color = HoldoutColors.TextSecondary)
                }
                Text(offer.compactMeta, style = LabelStyle, color = HoldoutColors.TextTertiary)
            }
            if (onJoin != null) JoinPill(offer.title, onJoin)
        }
    }
}

/**
 * A quiet row on Today (FR-053, FR-069a): one line of text in the secondary colour, with its "›" in the text,
 * no accent and no money colour, [Spacing.touchTarget] high; "1 more open ›", "2 open to join · from 9,000 SKR ›".
 */
@Composable
fun QuietRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Spacing.cardPadding, end = Spacing.betweenItems + Spacing.base),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary)
    }
}

private val JOIN_HEIGHT = 44.dp

/**
 * What the league's boost reserve can add to this Holdout's prize pool when it
 * settles (FR-053, FR-037d), or nothing while the reserve is empty or unread.
 * "Up to": the draw is capped at 10% of the pool and paid only if someone
 * forfeits, so it is never a promised amount. The card already names the
 * level, so the line does not (ruling G6; `Format.boostLine`).
 */
internal fun boostLine(boostReserve: ULong?): String? = boostReserve?.takeIf { it > 0uL }?.let { Format.boostLine(it) }

private val PREVIEW = OfferCardState(
    cohort = "o",
    title = "Spark · 7-day",
    tagline = "Light it up · knees allowed",
    atStake = "12,500 SKR",
    joined = "at stake · 14 joined",
    timing = "Joining closes in 2d 3h",
    boost = "Prize boost: up to 480 SKR",
    test = false,
    testDayLength = 0,
)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OfferCardPreview() {
    HoldoutTheme { OfferCard(PREVIEW, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CompactOfferCardPreview() {
    HoldoutTheme { CompactOfferCard(PREVIEW, onJoin = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OfferCardTestPreview() {
    HoldoutTheme {
        OfferCard(
            PREVIEW.copy(
                title = "Hackathon · 3-day",
                tagline = "A quick taste · knees allowed",
                atStake = "250 SKR",
                joined = "at stake · 3 joined",
                timing = "Starts in 12m 30s",
                boost = null,
                test = true,
                testDayLength = 600,
            ),
            onJoin = {},
        )
    }
}
