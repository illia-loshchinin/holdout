package holdout.app.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.HorizontalDivider
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
import holdout.app.ui.PrimaryButton
import holdout.app.ui.ProgressRing
import holdout.app.ui.SecondaryButton
import holdout.app.ui.StatBlock
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.bankBlocks
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * Today, with a Holdout running (screen map T2, board 5 · 5b; FR-038, FR-046,
 * FR-047, T073, T067): **the still-standing counter is the screen**, and it and
 * the prize pool come from `Derive` over chain accounts and chain time, so they
 * move at the deadline itself, not when a crank gets round to recording it
 * (FR-038a). The counter rolls rather than cuts.
 *
 * ```
 *  [TEST COHORT badge]
 *  HABIT · 21-DAY · DAY 12 OF 21                     header label, no dates
 *  189 / 312    STILL STANDING   ▲ 3 dropped since yesterday      StatBlock → Standing
 *  ┌ Prize pool                      48,200 SKR ┐
 *  │ ────────────────────────────────────────── │
 *  │ Your share now                    255 SKR  │   if today's standing holds
 *  │ +17 SKR since yesterday                    │   success
 *  │ + about 1,200 SKR boost if anyone finishes │   success
 *  └────────────────────────────────────────────┘
 *  (ring)  Nothing banked yet today · all at once or 10 at a time       BankRing
 *  [ Start today's 25 ]                                                  the day button
 *  🔥 12 day streak                               4h 06m left            footer
 * ```
 *
 * One Holdout day only: no Daily 25 link and no offers here, since a Holdout
 * day counts for the streak (B-D1, FR-050). The day button is "Start today's
 * 25", "Keep going" or "Submit day N"; a completed day waiting to reach the
 * chain reads "Done — waiting for network" while it cannot, is sent by itself
 * once it can, and shows "Done — tap to submit" only if that send did not land
 * (FR-024b, T098). Inside the last 10 minutes the ring and the line turn
 * warning and say unfinished reps won't count — never a refusal to start
 * (FR-047). While the chain can't be reached, "Check again" sits under what
 * says so, and a pull down checks too (spec 005).
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
    /** The daily streak, for the footer. */
    streak: Int = 0,
    /** The wallet holds enough SOL for the submit's fee (ES-0a). */
    canPayFees: Boolean = true,
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
    val standing by animateIntAsState(figures?.survivors?.standing ?: c.memberCount.toInt(), animationSpec = tween(COUNT_MILLIS), label = "survivors")
    val timeLeft = Derive.timeLeftToday(c, now)
    val warning = Derive.inWarningWindow(timeLeft)
    val day = stage.dayIndex + 1
    val waiting = pendingDay != null && pendingDay == stage.dayIndex
    val short = waiting && !offline && !canPayFees

    ChainCheckBox(offline, checking, onCheck, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HoldoutColors.Canvas)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (h.isTest) TestCohortBadge(c.dayLength)
            Text(Format.headerLabel(c, day), style = LabelStyle, color = HoldoutColors.TextTertiary)

            Column(
                modifier = Modifier
                    .clickable(onClickLabel = "Who is still standing", role = Role.Button, onClick = onDetail)
                    .padding(vertical = Spacing.tight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                StatBlock(value = "$standing", of = "${c.memberCount}", caption = "still standing")
                since?.dropped?.takeIf { it > 0 }?.let { Dropped(it) }
            }

            HoldoutCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Prize pool", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                    Text(figures?.let { Format.skr(it.projectedPool) } ?: Format.skr(c.pool), style = AmountStyle, color = HoldoutColors.TextPrimary)
                }
                val me = h.membership
                if (figures != null && me != null) {
                    HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Your share now", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Format.skr(figures.projectedShare(me)), style = AmountStyle, color = HoldoutColors.TextPrimary)
                            Text("if today's standing holds", style = LabelStyle, color = HoldoutColors.TextTertiary)
                        }
                    }
                    since?.shareGain(me)?.takeIf { it > 0uL }?.let { Text("+${Format.skr(it)} since yesterday", style = LabelStyle, color = HoldoutColors.Success) }
                }
                // The league's boost, as its reserve stands today (FR-037d): an estimate, drawn only at settlement and only if someone finishes.
                figures?.projectedBoost?.takeIf { it > 0uL }?.let { Text(Format.runningBoostLine(it), style = LabelStyle, color = HoldoutColors.Success) }
            }

            // Offline, a sitting can still be done, but not submitted: said before it starts, not after the set.
            if (offline && !stage.todayDone) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    Notice(
                        if (stillUnreachable) CheckAgainCopy.stillUnreachable(Deployment.networkName) else CheckAgainCopy.unreachable(Deployment.networkName),
                        warning = true,
                    )
                    if (!waiting) Body("You can still do today's reps. The day only counts once it reaches the chain before the deadline.")
                    // A waiting day has its own, under its line.
                    if (!waiting) CheckAgain(checking, stillUnreachable = false, onCheck = onCheck)
                }
            }

            val reps = c.repsPerDay
            val owed = (reps - banked).coerceAtLeast(0)
            val blocks = bankBlocks(reps).size
            when {
                stage.todayDone -> {
                    // Nothing else here says so: the counter and pool are an earlier read (spec 005 US2).
                    if (offline) ChainUnreachable(checking, stillUnreachable, onCheck)
                    DayRow(
                        ring = { ProgressRing("Today is on chain", done = true, size = RING) },
                        title = "Today is done and on chain.",
                        titleColor = HoldoutColors.Success,
                        text = timeLeft?.let { "Next day opens in ${Format.countdown(it)}." },
                    )
                }
                short -> {
                    DayRow(
                        ring = { BankRing(reps, blocks, reps, "done", size = RING) },
                        title = "Today's $reps are done",
                        titleColor = HoldoutColors.Accent,
                        text = "Not on chain yet.",
                    )
                    Notice("Not enough devnet SOL for the fee. Test tokens include some.", warning = true)
                    PrimaryButton("Submit day $day", {}, enabled = false)
                    SecondaryButton("Get test tokens", onGetTokens)
                }
                waiting -> {
                    val tap = !offline && !busy && autoSendTried
                    DayRow(
                        ring = { BankRing(reps, blocks, reps, "done", size = RING) },
                        title = when {
                            offline -> "Done — waiting for network"
                            tap -> "Done — tap to submit"
                            else -> "Done — sending to the chain"
                        },
                        titleColor = HoldoutColors.Success,
                        text = if (offline) {
                            // Not "by itself": nothing is sent while the app is closed (FR-024b, 2026-10-10).
                            "Your $reps are saved on this phone. It only counts once it reaches the chain before the deadline: it's sent when Holdout is open and back online, and you approve it in your wallet."
                        } else {
                            "Your $reps are counted on this phone. It only counts once it reaches the chain before the deadline" + if (tap) "." else ": approve it in your wallet."
                        },
                    )
                    if (offline) {
                        Notice(if (stillUnreachable) CheckAgainCopy.stillUnreachable(Deployment.networkName) else CheckAgainCopy.unreachable(Deployment.networkName), warning = true)
                        CheckAgain(checking, stillUnreachable = false, onCheck = onCheck)
                    }
                    if (tap) {
                        PrimaryButton("Submit day $day", { onSubmit(stage.dayIndex) })
                    } else if (!offline) {
                        PrimaryButton(Format.WAITING_FOR_WALLET, {}, busy = true)
                    }
                }
                else -> {
                    val label = if (warning) {
                        "$owed push-ups still needed"
                    } else if (banked > 0) {
                        "$banked banked today"
                    } else {
                        "Nothing banked yet today"
                    }
                    DayRow(
                        ring = { BankRing(reps, banked / BLOCK, banked, "banked", size = RING, bankedColor = if (warning) HoldoutColors.Warning else HoldoutColors.Accent) },
                        title = label,
                        titleColor = if (warning) HoldoutColors.Warning else HoldoutColors.TextPrimary,
                        text = when {
                            warning -> "Under 10 minutes left. Unfinished reps won't count."
                            banked > 0 -> "$owed to go"
                            else -> "all at once or $BLOCK at a time"
                        },
                    )
                    PrimaryButton(if (banked > 0) "Keep going" else "Start today's $reps", onStart)
                }
            }

            Notice(message, messageIsWarning)
            otherHoldouts()

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                StreakFlame(streak, if (stage.todayDone) StreakState.ExtendedToday else StreakState.Active, chip = true)
                timeLeft?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.base), verticalAlignment = Alignment.Bottom) {
                        CountdownText(it)
                        Text("left", style = LabelStyle, color = HoldoutColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** The ring and its two lines: what today is, and what to do about it. */
@Composable
private fun DayRow(ring: @Composable () -> Unit, title: String, titleColor: Color, text: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenSections)) {
        ring()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            text?.let { Body(it) }
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

private const val BLOCK = 10
private const val COUNT_MILLIS = 900
private const val TRIANGLE_DROP = 0.8f
private val RING: Dp = 96.dp
private val TRIANGLE = 10.dp
