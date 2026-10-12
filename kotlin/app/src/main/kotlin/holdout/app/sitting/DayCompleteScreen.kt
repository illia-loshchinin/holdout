package holdout.app.sitting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.streak.StreakMilestones
import holdout.app.ui.Body
import holdout.app.ui.CountdownText
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PrimaryButton
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The card under a finished Daily 25 that points on: "Stake on yourself next" with
 * the next Holdout's level, or a comeback the member is owed, which takes the
 * upsell's place (ruling C-D8). The host decides whether there is one — no
 * Holdout running or in the lobby, never a wallet known not to be a Seeker — and
 * passes null when there is not.
 */
data class DayCompleteOffer(val title: String, val subtitle: String)

/** The Holdout day's card: what is on the phone, what is on chain, and how long is left to send it. */
internal data class HoldoutDayCard(
    val dayNumber: Int,
    /** Seconds to this day's deadline; null while chain time is unknown. */
    val secondsLeft: Long?,
    /** The day is already on chain (the signing flow's success): nothing is left to submit. */
    val onChain: Boolean = false,
    /** "Done — not sent yet" / "Done — too late to send" and its sentence (FR-024b), when the day cannot be sent now. */
    val note: Pair<String, String>? = null,
)

/**
 * The finished page (board 19). A Holdout day carries [holdout]; the Daily 25
 * carries [streak] and, maybe, an [offer]. Holdout Lab's has none of them: just
 * the count, "done" and its button.
 */
internal data class DayCompleteUi(
    val header: String?,
    val reps: Int,
    val holdout: HoldoutDayCard?,
    val streak: StreakAfter?,
    val offer: DayCompleteOffer?,
    val button: String,
) {
    companion object {
        /** "Submit day 12" while it can be sent; "Back to today" when it cannot or is already on chain; "Done" for the Daily 25. */
        fun buttonFor(holdoutDay: Int?, note: Pair<String, String>?, onChain: Boolean): String = when {
            holdoutDay == null -> "Done"
            note != null || onChain -> "Back to today"
            else -> "Submit day $holdoutDay"
        }
    }
}

/**
 * Day complete (board 19; screen map S6; 001 FR-024b, FR-050). The gradient
 * plays once behind the count and fades (design book: day complete and claim
 * success only).
 *
 * ```
 *   HABIT · 21-DAY · DAY 12 OF 21          THE DAILY 25
 *               25                             25
 *              DONE                           DONE
 *   ┌ Counted on this phone.   ┐        🔥 13  day streak · +1
 *   │ Not on chain yet.        │        ┌ Stake on yourself next      › ┐
 *   │ Left to submit     4h 06m│        │ Spark · 7-day · joining … 1d 3h│
 *   [      Submit day 12      ]         [             Done             ]
 * ```
 * A Daily 25 that takes the streak to a milestone (7, 14, 21 … `StreakMilestones`) swaps
 * the streak row for the flame's milestone state, `7-day streak` and the milestone's line.
 */
@Composable
internal fun DayCompleteContent(ui: DayCompleteUi, onFinish: () -> Unit, onOffer: () -> Unit, modifier: Modifier = Modifier) {
    val wash = remember { Animatable(WASH_FROM) }
    LaunchedEffect(Unit) { wash.animateTo(0f, tween(durationMillis = WASH_MILLIS)) }
    Box(
        modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(HoldoutColors.GradientStart.copy(alpha = wash.value), HoldoutColors.GradientEnd.copy(alpha = wash.value)))),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.betweenSections),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            ui.header?.let {
                Text(it.uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.CenterVertically),
            ) {
                Text("${ui.reps}", style = BigCount, color = HoldoutColors.Success)
                Text("done".uppercase(), style = LabelStyle, color = HoldoutColors.TextSecondary)
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
            ) {
                ui.holdout?.let { HoldoutCardFor(it) }
                ui.streak?.let { if (it.milestone) MilestoneRow(it) else StreakRow(it) }
                ui.offer?.let { Offer(it, onOffer) }
                PrimaryButton(ui.button, onFinish)
            }
        }
    }
}

@Composable
private fun HoldoutCardFor(day: HoldoutDayCard) {
    HoldoutCard {
        val note = day.note
        when {
            note != null -> {
                Text(note.first, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                Body(note.second)
            }
            else -> {
                Text("Counted on this phone.", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
                if (day.onChain) {
                    Text("On chain.", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success)
                } else {
                    Text("Not on chain yet.", style = LabelStyle, color = HoldoutColors.TextSecondary)
                    day.secondsLeft?.let { left ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Left to submit", style = LabelStyle, color = HoldoutColors.TextSecondary)
                            CountdownText(left)
                        }
                    }
                }
            }
        }
    }
}

/** The flame, the days, and "+1" only when this day made it grow. */
@Composable
private fun StreakRow(streak: StreakAfter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakFlame(streak.days, StreakState.Active)
        Text(
            text = if (streak.grew) "day streak · +1" else "day streak",
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
        )
    }
}

/**
 * The streak at a milestone (S6, Gap 2): the flame in its milestone state, the headline
 * and the line, in place of [StreakRow]. Part of the page; nothing to dismiss.
 */
@Composable
private fun MilestoneRow(streak: StreakAfter) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        StreakFlame(streak.days, StreakState.Milestone)
        Text(StreakMilestones.headline(streak.days), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        StreakMilestones.line(streak.days)?.let { Body(it, center = true) }
    }
}

@Composable
private fun Offer(offer: DayCompleteOffer, onClick: () -> Unit) {
    HoldoutCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text(offer.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = HoldoutColors.TextPrimary)
                Text(offer.subtitle, style = LabelStyle, color = HoldoutColors.TextSecondary)
            }
            Canvas(Modifier.size(ChevronSize)) {
                val stroke = 1.5.dp.toPx()
                drawLine(HoldoutColors.TextTertiary, Offset(size.width * 0.36f, size.height * 0.21f), Offset(size.width * 0.64f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(HoldoutColors.TextTertiary, Offset(size.width * 0.64f, size.height * 0.5f), Offset(size.width * 0.36f, size.height * 0.79f), stroke, StrokeCap.Round)
            }
        }
    }
}

private val BigCount = TextStyle(
    fontFamily = CounterStyle.fontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 132.sp,
    lineHeight = 132.sp,
    letterSpacing = CounterStyle.letterSpacing,
    fontFeatureSettings = CounterStyle.fontFeatureSettings,
)
private val ChevronSize = 14.dp
private const val WASH_FROM = 0.55f
private const val WASH_MILLIS = 900

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun DayCompleteHoldoutPreview() {
    HoldoutTheme {
        DayCompleteContent(
            DayCompleteUi("Habit · 21-day · day 12 of 21", 25, HoldoutDayCard(12, 4 * 3_600L + 6 * 60), null, null, "Submit day 12"),
            {},
            {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun DayCompleteOfflinePreview() {
    HoldoutTheme {
        DayCompleteContent(
            DayCompleteUi(
                "Habit · 21-day · day 12 of 21",
                25,
                HoldoutDayCard(12, null, note = "Done — not sent yet" to "Devnet can't be reached, so day 12 isn't on chain yet. Your 25 are saved on this phone. Open Holdout when it can be, before the deadline (4h 06m left), and approve day 12 in your wallet."),
                null,
                null,
                "Back to today",
            ),
            {},
            {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun DayCompleteDailyPreview() {
    HoldoutTheme {
        DayCompleteContent(
            DayCompleteUi("The daily 25", 25, null, StreakAfter(13, grew = true), DayCompleteOffer("Stake on yourself next", "Spark · 7-day · joining closes in 1d 3h"), "Done"),
            {},
            {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun DayCompleteDailyPlainPreview() {
    HoldoutTheme { DayCompleteContent(DayCompleteUi("The daily 25", 25, null, StreakAfter(13, grew = false), null, "Done"), {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun DayCompleteMilestonePreview() {
    HoldoutTheme {
        DayCompleteContent(
            DayCompleteUi("The daily 25", 25, null, StreakAfter(7, grew = true), DayCompleteOffer("Stake on yourself next", "Spark · 7-day · joining closes in 1d 3h"), "Done"),
            {},
            {},
        )
    }
}
