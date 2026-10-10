package holdout.app.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.Derive
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * Home, with a Holdout running (screen map T2, FR-038, T073, T067): **the survivor
 * counter and the prize pool are the screen**, and both come from `Derive`
 * over chain accounts and chain time, so they move at the deadline itself,
 * not when a crank gets round to recording it (FR-038a). The counter rolls
 * rather than cuts.
 *
 * Under them: the member's own record, today's reps still owed and the time
 * left — with the clear warning inside the last 10 minutes, and no refusal to
 * start (FR-047) — then Start, or a completed day waiting to reach the chain:
 * "Done — waiting for network" while it cannot, sent by itself once it can, and
 * "Done — tap to submit" only if that send did not land (FR-024b, T098).
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
    onDetail: () -> Unit,
    onAbout: () -> Unit,
    onRoster: () -> Unit,
    onDaily25: () -> Unit,
    /** The faucet: a member whose SOL has run out cannot pay to submit a day. */
    onGetTokens: () -> Unit,
    modifier: Modifier = Modifier,
    /** The member's other Holdouts — a lobby joined for later, a claim waiting — as cards. */
    otherHoldouts: @Composable () -> Unit = {},
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now, boostReserve = h.league?.boostReserve) }
    val standing by animateIntAsState(figures?.survivors?.standing ?: c.memberCount.toInt(), animationSpec = tween(900), label = "survivors")
    val timeLeft = Derive.timeLeftToday(c, now)
    val warning = Derive.inWarningWindow(timeLeft)
    // The member's own days still to do: a day already on chain is not one to go.
    val daysLeft = c.durationDays - (h.membership?.daysCompleted ?: 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (h.isTest) TestCohortBadge(c.dayLength)
        SectionLabel("${Format.holdoutTitle(c)} · day ${stage.dayIndex + 1} of ${c.durationDays}")
        Text("$standing", style = CounterStyle, color = HoldoutColors.TextPrimary)
        Text("still standing of ${c.memberCount}", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        Text(figures?.let { Format.skr(it.projectedPool) } ?: Format.skr(c.pool), style = DisplayStyle, color = HoldoutColors.Accent)
        Text("prize pool, running", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        // The league's boost, as its reserve stands today (FR-037d): an estimate,
        // drawn only at settlement and only if someone finishes.
        figures?.projectedBoost?.takeIf { it > 0uL }?.let {
            Text(
                "+ about ${Format.skr(it)} boost from the reserve of ${Format.level(c)} Holdouts if anyone finishes",
                style = MaterialTheme.typography.bodyMedium,
                color = HoldoutColors.TextSecondary,
            )
        }

        HoldoutCard {
            h.membership?.let { m ->
                Text("Your record: ${m.daysCompleted} of ${c.durationDays} days · $daysLeft to go", style = AmountStyle, color = HoldoutColors.TextPrimary)
                figures?.let { Body("If today's standing held to the end, your share: ${Format.skr(it.projectedShare(m))} on top of your ${Format.skr(m.stake)} stake.") }
            }
        }

        // Offline, a sitting can still be done, but not submitted: said before it starts, not after the set.
        if (offline && !stage.todayDone) {
            Notice("${holdout.app.chain.Deployment.networkName} can't be reached right now. You can still do today's reps, but the day only counts once it is submitted before the deadline, so check the connection first.", warning = true)
        }
        when {
            stage.todayDone -> {
                Text("Today is done and on chain.", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
                timeLeft?.let { Body("Next day opens in ${Format.countdown(it)}.") }
            }
            pendingDay != null && pendingDay == stage.dayIndex -> {
                val tap = !offline && !busy && autoSendTried
                Text(
                    when {
                        offline -> "Done — waiting for network"
                        tap -> "Done — tap to submit"
                        else -> "Done — sending to the chain"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = HoldoutColors.Success,
                )
                Body(
                    "Your ${c.repsPerDay} are counted on this phone. It only counts once it reaches the chain before the deadline" +
                        if (offline) {
                            ", and it is sent by itself as soon as the connection is back."
                        } else if (tap) {
                            "."
                        } else {
                            ": approve it in your wallet."
                        },
                )
                timeLeft?.let { Body("${Format.countdown(it)} left.") }
                if (tap) {
                    PrimaryButton("Submit day ${stage.dayIndex + 1}", { onSubmit(stage.dayIndex) })
                } else if (!offline) {
                    PrimaryButton("Waiting for the wallet…", {}, busy = true)
                }
            }
            else -> {
                val owed = (c.repsPerDay - banked).coerceAtLeast(0)
                Text(
                    "$owed push-ups still needed today" + (timeLeft?.let { " · ${Format.countdown(it)} left" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (warning) HoldoutColors.Warning else HoldoutColors.TextPrimary,
                )
                if (warning) Body("Under 10 minutes left. You can still start — the deadline is the chain's, not this phone's.")
                if (banked > 0) Body("$banked banked on this phone.")
                PrimaryButton(if (banked > 0) "Keep going" else "Start today's ${c.repsPerDay}", onStart)
            }
        }
        Notice(message, messageIsWarning)
        otherHoldouts()
        SecondaryButton("Who's still standing", onRoster)
        TextButton(onClick = onDetail) { Text("Holdout details", color = HoldoutColors.TextSecondary) }
        TextButton(onClick = onGetTokens) { Text("Get test tokens", color = HoldoutColors.TextTertiary) }
        TextButton(onClick = onAbout) { Text("About Holdout", color = HoldoutColors.TextTertiary) }
        TextButton(onClick = onDaily25) { Text("The Daily 25", color = HoldoutColors.TextTertiary) }
    }
}
