package holdout.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.streak.ToesMeter
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * Home, with no cohort running: **the Daily 25 is the hero** (screen map T1,
 * FR-053, T053).
 *
 * ```
 *         TODAY'S CHALLENGE
 *               25
 *            push-ups
 *           [  start  ]
 *   do them all now, or 10 at a time today
 *      🔥 12-day streak · 4h 06m left today
 * ```
 *
 * Never empty and never "come back later": when today is already done, the
 * screen says so and the streak carries it, rather than going blank until
 * tomorrow.
 *
 * The staked Holdouts belong under this (FR-053) and arrive with T054 onward;
 * the Daily 25 is free, needs no cohort and no connection, and stands alone.
 */
data class HomeState(
    val remainingToday: Int,
    val bankedToday: Int,
    val target: Int,
    val completedToday: Boolean,
    val streak: Int,
    /** Null while chain time is unknown — the screen says so rather than guessing. */
    val secondsLeftToday: Long?,
    /** Streak freezes held, 0–2 (H35): each keeps the streak through one missed day. */
    val freezes: Int = 0,
    /** Today's toes meter (H35), or null before any Daily 25 rep is counted. */
    val toes: ToesMeter.Meter? = null,
    /** The way into a Holdout ([DailyHoldoutLine]), or null: shown once today is done, or at once when it is a comeback (FR-037g). */
    val holdoutLine: String? = null,
    /** [holdoutLine] is a comeback offer: shown before the Daily 25 too, since joining may close before it is done (FR-037g). */
    val holdoutLineIsComeback: Boolean = false,
)

@Composable
fun HomeScreen(
    state: HomeState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Under the hero: the Holdouts open to join, with what is at stake in each
     * (FR-053, T053), drawn by the shell from chain state.
     */
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems, Alignment.CenterVertically),
    ) {
        Text(
            text = if (state.completedToday) "TODAY" else "THE DAILY 25",
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
        )

        Text(
            text = if (state.completedToday) "${state.target}" else "${state.remainingToday}",
            style = CounterStyle,
            color = if (state.completedToday) HoldoutColors.Success else HoldoutColors.TextPrimary,
        )

        Text(
            text = when {
                state.completedToday -> "done"
                state.bankedToday > 0 -> "to go · ${state.bankedToday} banked"
                else -> "push-ups"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
        )

        if (!state.completedToday) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.primaryButtonHeight),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HoldoutColors.TextPrimary,
                    contentColor = HoldoutColors.OnLight,
                ),
            ) {
                Text(
                    text = if (state.bankedToday > 0) "Keep going" else "Start",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = "do them all now, or 10 at a time today",
                style = MaterialTheme.typography.bodyLarge,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = buildString {
                append(if (state.streak > 0) "${state.streak}-day streak" else "no streak yet")
                append(" · ")
                append(state.secondsLeftToday?.let { "${formatCountdown(it)} left today" } ?: "—")
            },
            style = AmountStyle,
            color = HoldoutColors.Accent,
            modifier = Modifier.padding(top = 8.dp),
        )
        state.toes?.takeIf { state.completedToday }?.let { ToesMeterCard(it, state.target) }
        state.holdoutLine?.takeIf { state.completedToday || state.holdoutLineIsComeback }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextPrimary, textAlign = TextAlign.Center)
        }

        if (state.streak > 0) {
            Text(
                text = when (state.freezes) {
                    0 -> "Seven days in a row give you a freeze for a missed day"
                    1 -> "1 freeze held — a missed day won't break the streak"
                    else -> "${state.freezes} freezes held — two missed days won't break the streak"
                },
                style = MaterialTheme.typography.bodySmall,
                color = HoldoutColors.TextSecondary,
            )
        }

        below()
    }
}

/** `4h 06m`, the shape screen map T1 and T2 show, or `12m 30s` in the last hour. */
internal fun formatCountdown(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%dh %02dm".format(hours, minutes)
    } else {
        "%dm %02ds".format(minutes, secs)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeFreshPreview() {
    HoldoutTheme {
        HomeScreen(
            state = HomeState(25, 0, 25, completedToday = false, streak = 12, secondsLeftToday = 14_760),
            onStart = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomePartwayPreview() {
    HoldoutTheme {
        HomeScreen(
            state = HomeState(15, 10, 25, completedToday = false, streak = 3, secondsLeftToday = 2_400),
            onStart = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HomeDonePreview() {
    HoldoutTheme {
        HomeScreen(
            state = HomeState(0, 25, 25, completedToday = true, streak = 13, secondsLeftToday = 14_760),
            onStart = {},
        )
    }
}

/**
 * "18 of 25 from your toes", seven bars for the last week and the best day
 * (H35): how far from a Hard Holdout today's Daily 25 was. Measured at 45° only;
 * head-on it says how to see it instead of guessing.
 */
@Composable
private fun ToesMeterCard(meter: ToesMeter.Meter, target: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        if (meter.measured == 0) {
            Text(
                "Do it with the phone at 45° to see how many come from your toes",
                style = MaterialTheme.typography.bodySmall,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        Text(
            if (meter.measured < target) "${meter.toes} of ${meter.measured} seen at 45° from your toes" else "${meter.toes} of ${meter.measured} from your toes",
            style = AmountStyle,
            color = if (meter.hardDay(target)) HoldoutColors.Success else HoldoutColors.TextPrimary,
        )
        if (meter.hardDay(target)) {
            Text("That's a Habit day.", style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.Success)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(32.dp).padding(top = 4.dp),
        ) {
            meter.week.forEach { toes ->
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height((4 + 28 * toes.coerceAtMost(target) / target.coerceAtLeast(1)).dp)
                        .background(if (toes >= target) HoldoutColors.Success else HoldoutColors.Accent),
                )
            }
        }
        Text("Best: ${meter.best} from your toes", style = MaterialTheme.typography.bodySmall, color = HoldoutColors.TextTertiary)
    }
}
