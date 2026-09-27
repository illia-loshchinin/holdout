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
 * Home, with a Holdout running (PRD §10.1a, FR-038, T073, T067): **the survivor
 * counter and the prize pool are the screen**, and both come from `Derive`
 * over chain accounts and chain time, so they move at the deadline itself,
 * not when a crank gets round to recording it (FR-038a). The counter rolls
 * rather than cuts.
 *
 * Under them: the member's own record, today's reps still owed and the time
 * left — with the clear warning inside the last 10 minutes, and no refusal to
 * start (FR-047) — then Start, or "Done — tap to submit" when a completed day
 * is waiting to reach the chain (FR-024b).
 */
@Composable
fun CohortHome(
    h: Holdout,
    members: List<MembershipSnapshot>?,
    stage: Stage.Active,
    now: Long,
    /** Reps banked on this phone toward today's day. */
    banked: Int,
    /** A completed day waiting to be submitted, or null. */
    pendingDay: Int?,
    busy: Boolean,
    message: String?,
    messageIsWarning: Boolean,
    onStart: () -> Unit,
    onSubmit: (day: Int) -> Unit,
    onDetail: () -> Unit,
    onRoster: () -> Unit,
    onDaily25: () -> Unit,
    modifier: Modifier = Modifier,
    /** Test builds only: record sittings as video (constitution X, v1.5.0). Null hides the switch. */
    recordVideo: Boolean = false,
    onRecordVideoChange: ((Boolean) -> Unit)? = null,
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now) }
    val standing by animateIntAsState(figures?.survivors?.standing ?: c.memberCount.toInt(), animationSpec = tween(900), label = "survivors")
    val timeLeft = Derive.timeLeftToday(c, now)
    val warning = Derive.inWarningWindow(timeLeft)
    val daysLeft = c.durationDays - stage.dayIndex

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
        SectionLabel("${c.durationDays}-day Holdout · day ${stage.dayIndex + 1} of ${c.durationDays}")
        Text("$standing", style = CounterStyle, color = HoldoutColors.TextPrimary)
        Text("still standing of ${c.memberCount}", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        Text(figures?.let { Format.skr(it.projectedPool) } ?: Format.skr(c.pool), style = DisplayStyle, color = HoldoutColors.Accent)
        Text("prize pool, running", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)

        HoldoutCard {
            h.membership?.let { m ->
                Text("Your record: ${m.daysCompleted} of ${c.durationDays} days · $daysLeft to go", style = AmountStyle, color = HoldoutColors.TextPrimary)
                figures?.let { Body("If today's standing held to the end, your share: ${Format.skr(it.projectedShare(m))} on top of your ${Format.skr(m.stake)} stake.") }
            }
        }

        when {
            stage.todayDone -> {
                Text("Today is done and on chain.", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
                timeLeft?.let { Body("Next day opens in ${Format.countdown(it)}.") }
            }
            pendingDay != null && pendingDay == stage.dayIndex -> {
                Text("Done — tap to submit", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
                Body("Your ${c.repsPerDay} are counted on this phone. It only counts once it reaches the chain before the deadline.")
                timeLeft?.let { Body("${Format.countdown(it)} left.") }
                PrimaryButton(if (busy) "Waiting for the wallet…" else "Submit day ${stage.dayIndex + 1}", { onSubmit(stage.dayIndex) }, enabled = !busy)
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
        SecondaryButton("Who's still standing", onRoster)
        TextButton(onClick = onDetail) { Text("Holdout details", color = HoldoutColors.TextSecondary) }
        TextButton(onClick = onDaily25) { Text("The Daily 25", color = HoldoutColors.TextTertiary) }
        onRecordVideoChange?.let { holdout.app.ui.RecordVideoSwitch(recordVideo, it) }
    }
}
