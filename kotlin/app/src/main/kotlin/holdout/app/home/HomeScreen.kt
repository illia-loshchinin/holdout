package holdout.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * Home, with no cohort running: **the Daily 25 is the hero** (PRD §10.1a,
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
)

@Composable
fun HomeScreen(
    state: HomeState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Test builds only: run the next sitting under staked rules — side-on, from
     * the toes — so recognition can be tuned both ways on a real body. Null
     * hides the switch, as it is in every release build.
     */
    stakedTest: Boolean = false,
    onStakedTestChange: ((Boolean) -> Unit)? = null,
    /** Test builds only: start today over from zero. Null hides the button. */
    onResetToday: (() -> Unit)? = null,
    /** Test builds only: nothing drawn over the camera, for training recordings. */
    cleanScreen: Boolean = false,
    onCleanScreenChange: ((Boolean) -> Unit)? = null,
    /** Test builds only: record sittings as video (constitution X, v1.5.0). Null hides the switch. */
    recordVideo: Boolean = false,
    onRecordVideoChange: ((Boolean) -> Unit)? = null,
    /**
     * Test builds only: spike S1's on-device path — a devnet transaction
     * signed by the connected wallet — kept reachable until T040 turns it into
     * `WalletRepository` (T012a). Here, not on the connect screen, because it
     * needs the connected wallet. Null hides it.
     */
    onSpikeTransaction: (() -> Unit)? = null,
    /** What the last spike transaction said: its signature, or why it failed. */
    spikeMessage: String? = null,
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
            text = if (state.completedToday) "TODAY" else "TODAY'S CHALLENGE",
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

        if (onCleanScreenChange != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                Switch(
                    checked = cleanScreen,
                    onCheckedChange = onCleanScreenChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = HoldoutColors.Accent),
                )
                Text(
                    text = if (cleanScreen) "TEST · clean screen: no dots or text, for training recordings" else "TEST · clean screen off",
                    style = LabelStyle,
                    color = if (cleanScreen) HoldoutColors.Accent else HoldoutColors.TextTertiary,
                )
            }
        }

        onRecordVideoChange?.let { holdout.app.ui.RecordVideoSwitch(recordVideo, it) }

        if (onResetToday != null) {
            TextButton(onClick = onResetToday) {
                Text("TEST · reset today to 0", style = LabelStyle, color = HoldoutColors.Warning)
            }
        }

        if (onSpikeTransaction != null) {
            TextButton(onClick = onSpikeTransaction) {
                Text("TEST · spike S1: send devnet transaction", style = LabelStyle, color = HoldoutColors.TextTertiary)
            }
            spikeMessage?.let { Text(it, style = LabelStyle, color = HoldoutColors.TextTertiary) }
        }

        if (onStakedTestChange != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                Switch(
                    checked = stakedTest,
                    onCheckedChange = onStakedTestChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = HoldoutColors.Accent),
                )
                Text(
                    text = if (stakedTest) {
                        "TEST · staked rules: 45° only, from the toes. Not your Daily 25."
                    } else {
                        "TEST · staked rules off"
                    },
                    style = LabelStyle,
                    color = if (stakedTest) HoldoutColors.Accent else HoldoutColors.TextTertiary,
                )
            }
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
        if (state.streak > 0) {
            Text(
                text = when (state.freezes) {
                    0 -> "Seven days in a row earn a freeze for a missed day"
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

/** `4h 06m`, or `12m 30s` in the last hour — the shape PRD §10.1a shows. */
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
