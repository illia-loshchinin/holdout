package holdout.app.sitting

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.streak.StreakMilestones
import holdout.app.ui.Body
import holdout.app.ui.CountdownText
import holdout.app.ui.HoldoutCard
import holdout.app.ui.MilestoneFlame
import holdout.app.ui.PrimaryButton
import holdout.app.ui.StreakFlame
import holdout.app.ui.StreakState
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
 * the celebration and its button.
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
 * Day complete (board 19; screen map S6; 001 FR-024b, FR-050, FR-069g). The page
 * opens with the [Celebration] in a 168 dp ring; the page's own content sits
 * below it and waits on nothing.
 *
 * ```
 *   HABIT · 21-DAY · DAY 12 OF 21          THE DAILY 25
 *            ( ring + tick )                ( ring + tick )
 *             Nailed it.                     Nailed it.
 *          25 push-ups done               25 push-ups done
 *   ┌ Counted on this phone.   ┐        🔥 13  day streak · +1
 *   │ Not on chain yet.        │        ┌ Stake on yourself next      › ┐
 *   │ Left to submit     4h 06m│        │ Spark · 7-day · joining … 1d 3h│
 *   [      Submit day 12      ]         [             Done             ]
 * ```
 * A Daily 25 that takes the streak to a milestone (7, 14, 21 … `StreakMilestones`) swaps
 * the streak row for one line, a small accent flame beside `7-day streak`, and the milestone's
 * line under it. The board has no gradient wash behind the page, so there is none.
 * [celebration] is the animation's state, so a test can read where it stands.
 */
@Composable
internal fun DayCompleteContent(
    ui: DayCompleteUi,
    onFinish: () -> Unit,
    onOffer: () -> Unit,
    modifier: Modifier = Modifier,
    celebration: CelebrationProgress = rememberCelebrationProgress(),
) {
    LaunchedEffect(celebration) { celebration.play() }
    Box(
        modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas),
    ) {
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
                verticalArrangement = Arrangement.Center,
            ) {
                Celebration(ui.reps, celebration)
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

/**
 * The celebration (FR-069g): a 168 dp ring that draws itself, a tick that draws in, ten small dots that burst
 * once, and the headline rising under it — "Nailed it." over "{reps} push-ups done". The ring box is
 * `testTag("celebration")`. Nothing waits on it: the page's buttons are live from the first frame.
 */
@Composable
internal fun Celebration(reps: Int, progress: CelebrationProgress, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CelebrationGap)) {
        Box(Modifier.size(CelebrationSize).testTag("celebration")) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = progress.pop.value
                        scaleY = progress.pop.value
                    },
            ) { drawRingAndTick(progress.ring.value, progress.tick.value) }
            Canvas(Modifier.fillMaxSize()) { drawDots(celebrationDots(progress.burst.value)) }
        }
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = progress.rise.value
                translationY = (1f - progress.rise.value) * RISE_FROM_DP.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text("Nailed it.", style = HeadlineStyle, color = HoldoutColors.TextPrimary, textAlign = TextAlign.Center)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.Success)) { append("$reps") }
                    append(" push-ups done")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Where the celebration stands. Every part starts at its first frame and [play] runs them on the board's
 * timeline: the ring over 700 ms from 150 ms, the whole mark popping from 760 ms, the tick over 380 ms
 * from 820 ms, the dots from 820 ms (18 ms apart), the headline rising over 500 ms from 1,050 ms. With the
 * phone's animations off (`ValueAnimator.areAnimatorsEnabled()`) everything is at its last frame at once.
 */
internal class CelebrationProgress(val animate: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    private val first = if (animate) 0f else 1f
    val ring = Animatable(first)
    val tick = Animatable(first)

    /** 0 to 1 over the whole burst, from the first dot leaving to the last one gone. */
    val burst = Animatable(first)
    val pop = Animatable(if (animate) POP_FROM else 1f)
    val rise = Animatable(first)

    suspend fun play() {
        if (!animate) return
        coroutineScope {
            launch {
                delay(RING_DELAY)
                ring.animateTo(1f, tween(RING_MILLIS, easing = RingEasing))
            }
            launch {
                delay(POP_DELAY)
                pop.animateTo(
                    1f,
                    keyframes {
                        durationMillis = POP_MILLIS
                        POP_FROM at 0
                        POP_PEAK at POP_PEAK_AT
                        1f at POP_MILLIS
                    },
                )
            }
            launch {
                delay(TICK_DELAY)
                tick.animateTo(1f, tween(TICK_MILLIS, easing = TickEasing))
            }
            launch {
                delay(BURST_DELAY)
                burst.animateTo(1f, tween(BURST_SPAN_MILLIS, easing = LinearEasing))
            }
            launch {
                delay(RISE_DELAY)
                rise.animateTo(1f, tween(RISE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }
}

@Composable
internal fun rememberCelebrationProgress(): CelebrationProgress = remember { CelebrationProgress() }

/** One dot of the burst: its offset from the ring's centre in the ring box's 220-unit space, and how it looks there. */
internal data class CelebrationDot(val dx: Float, val dy: Float, val alpha: Float, val scale: Float, val color: Color)

/**
 * The ten dots at [burst] (0 to 1 over the whole burst): evenly round the ring, alternating Success and Accent,
 * each leaving 18 ms after the one before, flying out and fading; none visible before the burst or after it.
 */
internal fun celebrationDots(burst: Float): List<CelebrationDot> = List(DOT_COUNT) { i ->
    val p = ((burst * BURST_SPAN_MILLIS - i * DOT_STAGGER_MILLIS) / DOT_MILLIS).coerceIn(0f, 1f)
    val angle = Math.toRadians(i * DOT_STEP_DEGREES)
    val reach = DotEasing.transform(p) * DOT_REACH
    CelebrationDot(
        dx = (sin(angle) * reach).toFloat(),
        dy = (-cos(angle) * reach).toFloat(),
        alpha = when {
            p <= 0f || p >= 1f -> 0f
            p < DOT_FADE_IN -> p / DOT_FADE_IN
            else -> (1f - p) / (1f - DOT_FADE_IN)
        },
        scale = DOT_SCALE_FROM + (1f - DOT_SCALE_FROM) * p,
        color = if (i % 2 == 0) HoldoutColors.Success else HoldoutColors.Accent,
    )
}

/** The ring and the tick, in the ring box's 220-unit space scaled to the canvas. */
private fun DrawScope.drawRingAndTick(ring: Float, tick: Float) {
    val unit = size.minDimension / BOX_UNITS
    val topLeft = Offset((CENTER - RING_RADIUS) * unit, (CENTER - RING_RADIUS) * unit)
    val diameter = Size(2 * RING_RADIUS * unit, 2 * RING_RADIUS * unit)
    drawArc(HoldoutColors.Success.copy(alpha = TRACK_ALPHA), 0f, 360f, false, topLeft, diameter, style = Stroke(RING_STROKE * unit))
    if (ring > 0f) {
        drawArc(HoldoutColors.Success, -90f, 360f * ring, false, topLeft, diameter, style = Stroke(RING_STROKE * unit, cap = StrokeCap.Round))
    }
    if (tick > 0f) {
        val a = Offset(TICK_A.first * unit, TICK_A.second * unit)
        val b = Offset(TICK_B.first * unit, TICK_B.second * unit)
        val c = Offset(TICK_C.first * unit, TICK_C.second * unit)
        val first = hypot(b.x - a.x, b.y - a.y)
        val second = hypot(c.x - b.x, c.y - b.y)
        val drawn = tick * (first + second)
        val stroke = TICK_STROKE * unit
        val toB = (drawn / first).coerceAtMost(1f)
        drawLine(HoldoutColors.Success, a, a + (b - a) * toB, stroke, StrokeCap.Round)
        if (drawn > first) {
            drawLine(HoldoutColors.Success, b, b + (c - b) * ((drawn - first) / second).coerceAtMost(1f), stroke, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawDots(dots: List<CelebrationDot>) {
    val unit = size.minDimension / BOX_UNITS
    dots.forEach { dot ->
        if (dot.alpha > 0f) {
            drawCircle(dot.color.copy(alpha = dot.alpha), DOT_RADIUS * dot.scale * unit, Offset((CENTER + dot.dx) * unit, (CENTER + dot.dy) * unit))
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
 * The streak at a milestone (S6, FR-049a), in place of [StreakRow]: one row — a small accent flame
 * (one pulse; none with animations off) beside the headline `{N}-day streak` — and the milestone's
 * line under it. No ring, no badge: the page keeps one big shape, the celebration's. Part of the
 * page; nothing to dismiss.
 */
@Composable
private fun MilestoneRow(streak: StreakAfter) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${streak.days}-day streak, milestone" },
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilestoneFlame(chip = false)
            Text(StreakMilestones.headline(streak.days), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        }
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

/** 32 / 38, medium, tight tracking: the board's headline. */
private val HeadlineStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.32).sp)

private val CelebrationSize = 168.dp
private val CelebrationGap = 20.dp
private val ChevronSize = 14.dp

// The board draws the ring box at 220 and scales it to 168: every figure below is in that 220-unit space.
private const val BOX_UNITS = 220f
private const val CENTER = 110f
private const val RING_RADIUS = 88f
private const val RING_STROKE = 8f
private const val TICK_STROKE = 10f
private const val TRACK_ALPHA = 0.14f
private val TICK_A = 74f to 113f
private val TICK_B = 99f to 138f
private val TICK_C = 148f to 86f

private const val DOT_COUNT = 10
private const val DOT_STEP_DEGREES = 36.0
private const val DOT_REACH = 118f
private const val DOT_RADIUS = 4f
private const val DOT_SCALE_FROM = 0.4f
private const val DOT_FADE_IN = 0.25f
private const val DOT_MILLIS = 900
private const val DOT_STAGGER_MILLIS = 18
private const val BURST_SPAN_MILLIS = DOT_MILLIS + (DOT_COUNT - 1) * DOT_STAGGER_MILLIS

private const val RING_DELAY = 150L
private const val RING_MILLIS = 700
private const val POP_DELAY = 760L
private const val POP_MILLIS = 520
private const val POP_PEAK_AT = 286
private const val POP_FROM = 0.92f
private const val POP_PEAK = 1.06f
private const val TICK_DELAY = 820L
private const val TICK_MILLIS = 380
private const val BURST_DELAY = 820L
private const val RISE_DELAY = 1_050L
private const val RISE_MILLIS = 500
private const val RISE_FROM_DP = 8

private val RingEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val TickEasing = CubicBezierEasing(0.3f, 0.9f, 0.3f, 1f)
private val DotEasing = CubicBezierEasing(0.1f, 0.8f, 0.3f, 1f)

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
