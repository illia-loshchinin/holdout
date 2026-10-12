package holdout.app.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PagerDots
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The first-run tour (FR-067, T042; boards TourDaily, Tour, TourCounting,
 * Disclosure): four cards, "how it works · N of 4", re-openable from Me and About.
 *
 * ```
 *  HOW IT WORKS · 1 OF 4                     Skip        header · Skip on cards 1–3
 *  1 The Daily 25   2 A Holdout   3 How reps are counted   4 Scripted members
 *  (the card, centred, scrolls when it must)
 *  ▬ ● ● ●                                               PagerDots
 *  [ Back ] [ Next ]  /  [ Start ] on the last         Back from card 2 on; PrimaryButton
 *  TEST NETWORK · TEST TOKEN
 * ```
 *
 * **Skip on cards 1–3 goes to card 4**, not out of the tour: the scripted-member
 * note is required on first run (spec 004 FR-013). **Back** — the button from
 * card 2 on, or the system Back — steps back a card; on the first card there is
 * no button and the system Back is left to the shell. **Start** on the last card finishes
 * ([onDone]). Nothing here is for judges, and no card names Holdout Lab.
 */
@Composable
fun TourScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var card by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = card > 0) { card-- }
    val last = card == LAST
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(horizontal = Spacing.screenGutter)
            .padding(top = Spacing.tight, bottom = Spacing.betweenSections),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "How it works · ${card + 1} of $CARDS".uppercase(),
                style = LabelStyle,
                color = HoldoutColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            if (!last) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HoldoutColors.TextTertiary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button) { card = LAST }
                        .padding(horizontal = Spacing.tight, vertical = Spacing.betweenItems),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            key(card) {
                Column(
                    modifier = Modifier.heightIn(min = maxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections, Alignment.CenterVertically),
                ) {
                    when (card) {
                        0 -> DailyCard()
                        1 -> HoldoutCardPage()
                        2 -> CountingCard()
                        else -> ScriptedCard()
                    }
                }
            }
        }
        PagerDots(CARDS, card, Modifier.padding(vertical = Spacing.betweenItems))
        // Back beside Next from card 2 on, so a card can be read again (owner, 12 Oct); the system Back does the same.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            if (card > 0) SecondaryButton("Back", { card-- }, Modifier.weight(1f))
            PrimaryButton(if (last) "Start" else "Next", { if (last) onDone() else card++ }, Modifier.weight(2f))
        }
        Text(
            "Test network · test token".uppercase(),
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.betweenItems),
        )
    }
}

private const val CARDS = 4
private const val LAST = CARDS - 1

// ---- card 1: the Daily 25 ---------------------------------------------------------------------------

@Composable
private fun Title(text: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
        Text(text, style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        sub?.let { Body(it) }
    }
}

@Composable
private fun DailyCard() {
    Title("The Daily 25", "25 push-ups a day. Free. Your way:")
    HoldoutCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            DotGrid(74.dp, 74.dp, 6f, dailyDots(listOf(7f, 22f, 37f, 52f, 67f), listOf(1f, 1f, 1f, 1f, 1f)))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text("All at once", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
                Body("One sitting, all 25.")
            }
        }
    }
    HoldoutCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            DotGrid(74.dp, 90.dp, 6f, dailyDots(listOf(7f, 22f, 45f, 60f, 83f), listOf(1f, 1f, BAND_2, BAND_2, BAND_3)))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text("At your own pace", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
                Legend(1f, "10", "morning")
                Legend(BAND_2, "10", "lunch")
                Legend(BAND_3, "5", "evening")
            }
        }
    }
    SuccessNote(Mark.Lock, "Every 10 are banked for good", "Close the app or stop mid-set — banked reps stay.")
    Text("Each day done grows your streak.", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextTertiary)
}

/** A 5-wide grid of accent dots whose rows ([ys]) are faded by [alphas]: the Daily 25 in blocks of 10. */
private fun dailyDots(ys: List<Float>, alphas: List<Float>): List<DotSpec> = ys.indices.flatMap { r -> (0 until 5).map { c -> DotSpec(7f + 15f * c, ys[r], HoldoutColors.Accent.copy(alpha = alphas[r])) } }

@Composable
private fun Legend(alpha: Float, number: String, word: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Box(Modifier.size(8.dp).background(HoldoutColors.Accent.copy(alpha = alpha), CircleShape))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = HoldoutColors.TextPrimary)) { append(number) }
                append(" $word")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
        )
    }
}

private const val BAND_2 = 0.55f
private const val BAND_3 = 0.3f

private enum class Mark { Lock, Shield }

/** A note in `success` with a drawn mark: the lock on "banked for good", the shield on "private by design". */
@Composable
private fun SuccessNote(mark: Mark, title: String, text: String) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Success.copy(alpha = NOTE_FILL), shape)
            .border(Spacing.border, HoldoutColors.Success.copy(alpha = NOTE_BORDER), shape)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.betweenItems),
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(Modifier.padding(top = Spacing.base).size(20.dp)) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            scale(size.width / 20f, pivot = Offset.Zero) {
                when (mark) {
                    Mark.Lock -> {
                        drawRoundRect(HoldoutColors.Success, Offset(4f, 9f), Size(12f, 8.5f), CornerRadius(2f), style = stroke)
                        drawPath(PathParser().parsePathString("M6.5 9V6.5a3.5 3.5 0 0 1 7 0V9").toPath(), HoldoutColors.Success, style = stroke)
                    }
                    Mark.Shield -> {
                        drawPath(PathParser().parsePathString("M10 2 3 5v6c0 4.4 3 7.8 7 9 4-1.2 7-4.6 7-9V5l-7-3Z").toPath(), HoldoutColors.Success, style = stroke)
                        drawPath(PathParser().parsePathString("M6.8 11.2l2.2 2.2 4.2-4.6").toPath(), HoldoutColors.Success, style = stroke)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Success)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        }
    }
}

private const val NOTE_FILL = 0.08f
private const val NOTE_BORDER = 0.32f

// ---- card 2: a Holdout ------------------------------------------------------------------------------

@Composable
private fun HoldoutCardPage() {
    Title("A Holdout", "Stake SKR with everyone who starts on Monday. Finish, and split what the others lost.")
    ExampleCard()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Body("Spark · 7-day lights it up — knees allowed.\nHabit · 21-day makes it stick — toes only.")
        Text(
            "Changed your mind? Full refund until joining closes.\nHoldouts need a Seeker. The Daily 25 is free for everyone.",
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextTertiary,
        )
    }
}

// ---- card 3: how reps are counted -------------------------------------------------------------------

@Composable
private fun CountingCard() {
    val clock = rememberFigureClock()
    HoldoutCard {
        TourFigure(clock)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            STEPS.forEachIndexed { i, step ->
                // Each step is lit while the figure is on it: read in the layer, so a frame recomposes nothing.
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = HoldoutColors.Accent)) { append("${i + 1}") }
                        append("  $step")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = HoldoutColors.TextPrimary,
                    modifier = Modifier.graphicsLayer { alpha = clock.overlay().stepAlpha[i] },
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Text("How reps are counted", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        Text(
            buildAnnotatedString {
                append("The Daily 25 and Spark: aim for your toes; knees are allowed.\n")
                withStyle(SpanStyle(color = HoldoutColors.TextPrimary)) { append("Habit: full push-ups from your toes.") }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
        )
        SuccessNote(Mark.Shield, "Private by design", "No microphone, ever. Reps are counted on this phone — no video leaves the app.")
    }
}

private val STEPS = listOf(
    "Phone on the floor, at 45°, ahead of your shoulder.",
    "Get into a push-up: head to feet, knees included, inside the outline.",
    "The checks tick green. Hold the top for a second, then 3 · 2 · 1 and every rep counts.",
)

// ---- card 4: scripted members -----------------------------------------------------------------------

/** Spark's minimum before scripted members stop joining (spec 001 FR-043), and Habit's. */
private const val SPARK_MINIMUM = 20
private const val HABIT_MINIMUM = 12

@Composable
private fun ScriptedCard() {
    Title("Scripted members")
    ScriptedRow(real = SPARK_MINIMUM - SCRIPTED_SHOWN, scripted = SCRIPTED_SHOWN)
    Body("Some members in these Holdouts are scripted.\nReal wallets, same rules, labelled SCRIPTED.")
    Text(
        "They only fill a Holdout to its minimum:\n$SPARK_MINIMUM per Spark and $HABIT_MINIMUM per Habit.\nTheir days aren't camera-checked.",
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextTertiary,
    )
}

private const val SCRIPTED_SHOWN = 5

/** A Spark at its minimum: solid dots for real members, dashed rings for the scripted ones that top it up. */
@Composable
private fun ScriptedRow(real: Int, scripted: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Text("A Spark · 7-day Holdout · ${real + scripted} members".uppercase(), style = LabelStyle, color = HoldoutColors.TextTertiary)
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val pitch = size.width / (real + scripted)
            val r = pitch * 0.34f
            repeat(real + scripted) { i ->
                val centre = Offset(pitch * (i + 0.5f), size.height / 2)
                if (i < real) {
                    drawCircle(HoldoutColors.TextPrimary.copy(alpha = 0.7f), r, centre)
                } else {
                    drawCircle(HoldoutColors.Warning, r, centre, style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("real members", style = LabelStyle.copy(fontFamily = FontFamily.Monospace), color = HoldoutColors.TextPrimary)
            Text("scripted, up to the minimum", style = LabelStyle.copy(fontFamily = FontFamily.Monospace), color = HoldoutColors.Warning)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 844, widthDp = 390)
@Composable
private fun TourPreview() {
    HoldoutTheme { TourScreen(onDone = {}) }
}
