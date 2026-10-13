package holdout.app.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.holdout.CheckAgainCopy
import holdout.app.ui.BankRing
import holdout.app.ui.Body
import holdout.app.ui.ChainCheckBox
import holdout.app.ui.ChainUnreachable
import holdout.app.ui.CheckAgain
import holdout.app.ui.CountdownText
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.ProgressRing
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.bankBlocks
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * Today, with a Holdout running (screen map T2, board 5 · 5b; FR-038, FR-046,
 * FR-047, FR-069a, T073, T067): **the day is the screen** — what to do now and
 * how long there is to do it. The crowd shrinks to one strip under it that opens
 * the Standing tab, where the counter, the thinning, the prize pool, the share
 * and the boost live (FR-069b). The strip's figures come from `Derive` over chain
 * accounts and chain time, so they move at the deadline itself, not when a crank
 * gets round to recording it (FR-038a).
 *
 * ```
 *  [DEVNET COHORT badge]
 *  HABIT · 21-DAY · DAY 12 OF 21                          🔥 12      header label, no dates · streak chip
 *  (ring)  25 push-ups                                              BankRing · the day's title
 *          all at once or 10 at a time
 *          4h 06m left                                              CountdownText
 *  [ Start today's 25 ]                                             the day button
 *  ┌ 189 of 312 still standing · your share 255 SKR   › ┐          the crowd strip → Standing
 *  │ ▲ 3 dropped since yesterday                         │
 *  └─────────────────────────────────────────────────────┘
 * ```
 *
 * The day itself — its title, words, button and deadline — is [DayState], the
 * same model Standing's day strip draws. One Holdout day only: no Daily 25 link
 * and no offers here, since a Holdout day counts for the streak (B-D1, FR-050).
 * The day button is "Start today's 25", "Keep going" or "Submit day N"; a
 * completed day waiting to reach the chain reads "Done — waiting for network"
 * while it cannot, is sent by itself once it can, and shows "Done — tap to
 * submit" only if that send did not land (FR-024b, T098). Once the day is on
 * the chain the deadline countdown goes: the next day's opening (or, on the
 * last day, that the end settles) takes its place. Inside the last 10 minutes
 * the ring and the line turn warning and say unfinished reps won't count —
 * never a refusal to start (FR-047). While the chain can't be reached, "Check
 * again" sits under what says so, and a pull down checks too (spec 005).
 */
@Composable
fun CohortHome(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    stage: Stage.Active,
    now: Long,
    /** Reps banked on this phone toward today's day. */
    banked: Int,
    /** The last chain read failed: what is shown is the previous one, and nothing can be submitted until it answers. */
    offline: Boolean,
    /** A completed day waiting to be submitted, or null. */
    pendingDay: Int?,
    /** The pending day was already sent by itself since the chain last answered: the tap is the way left. */
    autoSendTried: Boolean,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onStart: () -> Unit,
    onSubmit: (day: Int) -> Unit,
    /** The counter is a link to who is still standing (the Standing tab). */
    onDetail: () -> Unit,
    /** The faucet: a member whose SOL has run out cannot pay to submit a day. */
    onGetTokens: () -> Unit,
    modifier: Modifier = Modifier,
    /** The new-phone signal (`NewPhone.shows`): this phone first saw the wallet today and has nothing banked, so say so before the day starts (ES-3a). */
    newPhone: Boolean = false,
    /** The daily streak, for the footer. */
    streak: Int = 0,
    /** The wallet holds enough SOL for the submit's fee (ES-0a). */
    canPayFees: Boolean = true,
    /** A send of today's day may still land: Submit stays shut, and the day says so (FR-070a). */
    mayStillLand: Boolean = false,
    /** A member's "Check again" is out (spec 005). */
    checking: Boolean = false,
    /** The last "Check again" found the chain still silent. */
    stillUnreachable: Boolean = false,
    /** Reads the chain now; offered only while [offline]. */
    onCheck: () -> Unit = {},
    /** The member's other Holdouts — a lobby joined for later, a claim waiting — one line each. */
    otherHoldouts: @Composable () -> Unit = {},
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now, boostReserve = h.league?.boostReserve) }
    val since = if (figures != null && members != null) Derive.sinceYesterday(figures, members) else null
    val d = DayState.of(c, stage, now, banked, pendingDay, autoSendTried, offline, busy, canPayFees, mayStillLand)
    val me = h.membership
    val crowd = CrowdStrip(
        standing = figures?.survivors?.standing ?: c.memberCount.toInt(),
        total = c.memberCount,
        share = if (figures != null && me != null) figures.projectedShare(me) else null,
        dropped = since?.dropped ?: 0,
    )

    ChainCheckBox(offline, checking, onCheck, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HoldoutColors.Canvas)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections),
        ) {
            if (h.isTest) TestCohortBadge(c.dayLength, Modifier.align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
                Text(Format.headerLabel(c, d.day), style = LabelStyle, color = HoldoutColors.TextTertiary, modifier = Modifier.weight(1f))
                StreakFlame(streak, if (d.phase == DayPhase.OnChain) StreakState.ExtendedToday else StreakState.Active, chip = true)
            }

            // Offline, a sitting can still be done, but not submitted: said before it starts, not after the set.
            // A waiting day has its own notice, under its line.
            if (offline && d.phase != DayPhase.OnChain && d.phase != DayPhase.Unsent) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    Notice(
                        if (stillUnreachable) CheckAgainCopy.stillUnreachable(Deployment.networkName) else CheckAgainCopy.unreachable(Deployment.networkName),
                        warning = true,
                    )
                    Body("You can still do today's reps. The day only counts once it reaches the chain before the deadline.")
                    CheckAgain(checking, stillUnreachable = false, onCheck = onCheck)
                }
            }
            // Nothing else here says so: the crowd strip is an earlier read (spec 005 US2).
            if (offline && d.phase == DayPhase.OnChain) ChainUnreachable(checking, stillUnreachable, onCheck)
            if (newPhone && d.phase == DayPhase.ToDo) Banner(NewPhone.line(d.target), warning = false)

            DayRow(
                ring = {
                    when (d.phase) {
                        DayPhase.OnChain -> ProgressRing("Today is on chain", done = true, size = RING)
                        DayPhase.ToDo -> BankRing(d.target, d.banked / DayState.BLOCK, d.banked, "banked", size = RING, bankedColor = if (d.warning) HoldoutColors.Warning else HoldoutColors.Accent)
                        else -> BankRing(d.target, bankBlocks(d.target).size, d.target, "done", size = RING)
                    }
                },
                title = d.title,
                titleColor = d.tone.color(),
                text = d.text,
                timeLeft = d.timeLeft,
            )
            if (offline && d.phase == DayPhase.Unsent) {
                ChainUnreachable(checking, stillUnreachable, onCheck)
            }
            DayActions(d, onStart, onSubmit, onGetTokens)
            // "Day N is on chain." repeats the done card's title: only a warning gets a line of its own.
            Notice(message.takeIf { messageIsWarning }, messageIsWarning)

            Crowd(crowd, onDetail)
            otherHoldouts()
        }
    }
}

/**
 * What the crowd strip says: [standing] of [total], the member's [share] if they stand in it and there is
 * one to show (nothing has dropped yet, so nothing to share: no "your share 0 SKR"), and who [dropped] since yesterday.
 */
private data class CrowdStrip(val standing: Int, val total: Long, val share: ULong?, val dropped: Int) {
    val line: String get() = "$standing of $total still standing" + (share?.takeIf { it > 0uL }?.let { " · your share ${Format.skr(it)}" } ?: "")
}

/**
 * The crowd, shrunk to one tappable strip under the day (Decision 1): the line, a chevron,
 * and `▲ n dropped since yesterday` under it when anyone did. It opens the Standing tab,
 * where the counter, the thinning, the pool and the roster live.
 */
@Composable
private fun Crowd(crowd: CrowdStrip, onDetail: () -> Unit) {
    HoldoutCard(Modifier.clickable(onClickLabel = "Who is still standing", role = Role.Button, onClick = onDetail)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Text(crowd.line, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextTertiary)
        }
        if (crowd.dropped > 0) Dropped(crowd.dropped)
    }
}

/** The ring and its lines: what today is, what to do about it, and how long is left to do it. */
@Composable
private fun DayRow(ring: @Composable () -> Unit, title: String, titleColor: Color, text: String?, timeLeft: Long?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenSections)) {
        ring()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            text?.let { Body(it) }
            timeLeft?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.base), verticalAlignment = Alignment.Bottom) {
                    CountdownText(it)
                    Text("left", style = LabelStyle, color = HoldoutColors.TextSecondary)
                }
            }
        }
    }
}

/** `▲ 3 dropped since yesterday`: the day's loss to the crowd, in the colour of a good thing for the share. */
@Composable
private fun Dropped(count: Int) {
    val words = "$count dropped since yesterday"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Canvas(Modifier.size(TRIANGLE)) {
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height * TRIANGLE_DROP)
                lineTo(0f, size.height * TRIANGLE_DROP)
                close()
            }
            drawPath(path, HoldoutColors.Success)
        }
        Text(words, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success)
    }
}

private const val TRIANGLE_DROP = 0.8f
private val RING: Dp = 96.dp
private val TRIANGLE = 10.dp
