package holdout.app.cohort

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.claim.PayoutRows
import holdout.app.home.DayState
import holdout.app.ui.Body
import holdout.app.ui.ChainCheckBox
import holdout.app.ui.DevnetBar
import holdout.app.ui.DevnetBarCopy
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.KeyValueRow
import holdout.app.ui.NoticeCard
import holdout.app.ui.QuietAction
import holdout.app.ui.Reading
import holdout.app.ui.ScreenHeader
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.StatBlock
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.CohortStatus
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * The Holdout detail (H4, board 13; US-7, FR-038, FR-062a): a Holdout's screen, pushed over what opened it (FR-069b),
 * also the page a watcher reaches from "Running now". The member's own day, the share they would get
 * if the crowd held here, and how the crowd thinned day by day. It also carries
 * the quiet end states: **You finished · settling** until finalisation, with
 * how long that usually takes counted from the end (FR-035a), and **claimed**.
 *
 * ```
 *  ‹  HABIT · 21-DAY · DAY 12 OF 21           ScreenHeader
 *  (devnet bar while devnet is silent)         DevnetBar, directly under the header
 *  [You're watching this one]                  a watcher's note
 *  DAY 12 · 25 push-ups · 4h 06m left  [Start]  the member's day while it is open (YourDayStrip, FR-069b)
 *  ┌ You finished · settling … ┐               settling card, success border
 *  189 / 312                                   StatBlock counter, rolls
 *  STILL STANDING
 *  ┌ HOW THE CROWD THINNED  ~chart~ ┐          ThinningChart
 *  ┌ Prize pool  48,200 SKR         ┐          KeyValueRows
 *  │ + about 1,200 SKR boost if anyone finishes
 *  │ Your share now 255 SKR                    (the header already says the day)
 *  [ Members ]                                 SecondaryButton
 * ```
 */
@Composable
fun CohortDetailScreen(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    stage: Stage,
    now: Long,
    lastClaimSignature: String?,
    onRoster: () -> Unit,
    /** Back to the screen that opened this one (FR-069b); null draws no back arrow. */
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** The last chain read failed (spec 005, FR-069h): the devnet bar under the header, and the pull. */
    offline: Boolean = false,
    checking: Boolean = false,
    stillUnreachable: Boolean = false,
    onCheck: () -> Unit = {},
    /** How old the read the screen shows is, in seconds, for the bar's "Showing what it said 4m ago."; null when unknown. */
    readAgoSeconds: Long? = null,
    /** A member who is out reaches their forfeit screen from here; null shows no card. */
    onForfeit: (() -> Unit)? = null,
    /** The member's day, for the strip at the top (FR-069b): null on a Holdout they only watch, or past the last day. */
    day: DayState? = null,
    onStartDay: () -> Unit = {},
    onSubmitDay: (dayIndex: Int) -> Unit = {},
    /** The faucet: a member short of the SOL a submit costs. */
    onGetTokens: () -> Unit = {},
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now, boostReserve = h.league?.boostReserve) }
    val member = h.membership
    val running = Derive.status(c, now) == CohortStatus.Running
    ChainCheckBox(offline, checking, onCheck, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HoldoutColors.Canvas)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            ScreenHeader(DetailText.label(c, now), onBack = onBack)
            if (h.isTest) TestCohortBadge(c.dayLength, Modifier.align(Alignment.CenterHorizontally))
            if (member == null) Watching()
            if (offline) DevnetBar(Deployment.networkName, stillUnreachable, checking, DevnetBarCopy.showing(readAgoSeconds), onCheck)
            // Only while the member stands in the running Holdout with a day open or just done.
            if (day != null && member != null && stage is Stage.Active) YourDayStrip(day, onStartDay, onSubmitDay, onGetTokens)

            when (stage) {
                Stage.Settling -> Settling(c, now)
                is Stage.Claimed -> ClaimedCard(stage, lastClaimSignature)
                is Stage.Forfeited -> if (onForfeit != null) {
                    NoticeCard(DetailText.outTitle(stage.daysLasted), "Your run has ended. Your streak hasn't.", warning = true, actionText = "Your way back", onAction = onForfeit)
                }
                else -> Unit
            }

            Counter(figures?.survivors?.standing, c.memberCount)

            val points = members?.let { DetailText.thinning(c, it, now) }
            if (points != null && points.size >= 2) {
                HoldoutCard {
                    SectionLabel("How the crowd thinned")
                    ThinningChart(points, c.durationDays)
                }
            }

            HoldoutCard {
                KeyValueRow("Prize pool", Format.skr(figures?.projectedPool ?: c.pool))
                figures?.projectedBoost?.takeIf { running && it > 0uL }?.let {
                    Text(Format.runningBoostLine(it), style = LabelStyle, color = HoldoutColors.Success)
                }
                if (member != null) {
                    if (figures != null && Derive.standing(member, c, now) && !c.settled) {
                        KeyValueRow("Your share now", Format.skr(figures.projectedShare(member)), success = true)
                        Text(DetailText.SHARE_NOTE, style = LabelStyle, color = HoldoutColors.TextTertiary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text(DetailText.WATCHER_NOTE, style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
            if (members == null) Reading { Body("Reading devnet…") }

            SecondaryButton(DetailText.MEMBERS, onRoster, Modifier.fillMaxWidth())
        }
    }
}

/** `189 / 312 · still standing`, the survivor count rolling, never cutting (design book, Stat block). */
@Composable
private fun Counter(standing: Int?, started: Long) {
    if (standing == null) {
        StatBlock(value = started.toString(), caption = "started", modifier = Modifier.fillMaxWidth())
    } else {
        val shown = animateIntAsState(standing, label = "still standing").value
        StatBlock(value = shown.toString(), of = started.toString(), caption = "still standing", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Watching() {
    Text(
        DetailText.WATCHING,
        style = LabelStyle,
        color = HoldoutColors.TextSecondary,
        modifier = Modifier
            .background(HoldoutColors.Surface3, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = Spacing.betweenItems, vertical = Spacing.base),
    )
}

/** FR-035a: automation settles it on its next pass, usually within 15 minutes of the end — the time since the end and what is left of those 15. */
@Composable
private fun Settling(c: holdout.core.CohortSnapshot, now: Long) {
    val usually = Derive.settleUsuallyIn(c, now)
    SuccessCard {
        Text(SettlingWords.CARD_TITLE, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
        Body(SettlingWords.cardLead(usually))
        Text(SettlingWords.cardDetail(Derive.sinceEnd(c, now), usually), style = LabelStyle, color = HoldoutColors.Accent)
    }
}

@Composable
private fun ClaimedCard(stage: Stage.Claimed, signature: String?) {
    val uriHandler = LocalUriHandler.current
    SuccessCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Claimed", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
            Text(Format.skr(stage.amount), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
        }
        if (DetailText.hasBreakdown(stage.stake)) {
            HorizontalDivider(color = HoldoutColors.Hairline, thickness = Spacing.border)
            PayoutRows(stage.stake, stage.share, stage.comeback)
        }
        // The link exists in the session that claimed; tapped, it opens in the browser.
        signature?.let { sig ->
            QuietAction("${Format.short(sig)} · view on explorer →", { runCatching { uriHandler.openUri(Format.explorer(sig)) } })
        }
    }
}

/** A hairline card with the `success` border: good news (settling, claimed, the streak still alive). */
@Composable
internal fun SuccessCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface1, shape)
            .border(Spacing.border, HoldoutColors.Success.copy(alpha = BORDER_ALPHA), shape)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        content = content,
    )
}

private const val BORDER_ALPHA = 0.32f
