package holdout.app.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.nav.Tour
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.PagerDots
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SkipAction
import holdout.app.ui.StatusChip
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The first-run tour (FR-067, T042; boards TourDaily, Tour, TourPlay, TourMoney,
 * TourCounting, Disclosure): six cards, "how it works · N of 6", re-openable
 * from About, and from the stake sheet's *How it works* at card 2.
 *
 * ```
 *  HOW IT WORKS · 1 OF 6                     Skip        header · Skip on cards 1–5
 *  1 The Daily 25   2 Two kinds of Holdout   3 How a Holdout plays out
 *  4 Where a lost stake goes   5 How reps are counted   6 Scripted members
 *  (the card, centred; each fits a Seeker, 400 × 829 dp, unscrolled)
 *  ▬ ● ● ● ● ●                                           PagerDots
 *  [ Back ] [ Next ]  /  [ Start ] on the last         Back from card 2 on; PrimaryButton
 *  TEST NETWORK · TEST TOKEN
 * ```
 *
 * **Skip on cards 1–5 goes to card 6**, not out of the tour: the scripted-member
 * note is required on first run (spec 004 FR-013). **Back** — the button from
 * card 2 on, or the system Back — steps back a card; on the first card there is
 * no button and the system Back is left to the shell. **Start** on the last card finishes
 * ([onDone]). Nothing here is for judges, and no card names Holdout Lab.
 *
 * [startCard] (1-based) is the card it opens on: 1 from the welcome's *Start the brief* and About,
 * 6 from the welcome's *Skip*, 2 from the stake sheet's *How it works*.
 */
@Composable
fun TourScreen(onDone: () -> Unit, modifier: Modifier = Modifier, startCard: Int = 1) {
    var card by rememberSaveable { mutableIntStateOf(startCard.coerceIn(1, CARDS) - 1) }
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
            if (!last) SkipAction({ card = LAST })
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            key(card) {
                Column(
                    modifier = Modifier.heightIn(min = maxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections, Alignment.CenterVertically),
                ) {
                    when (card) {
                        0 -> DailyCard()
                        1 -> TwoKindsCard()
                        2 -> PlaysOutCard()
                        3 -> MoneyCard()
                        4 -> CountingCard()
                        else -> ScriptedCard()
                    }
                }
            }
        }
        // Tight around the dots, so every card fits a Seeker unscrolled (TourFitTest).
        PagerDots(CARDS, card, Modifier.padding(vertical = Spacing.tight))
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

private const val CARDS = Tour.CARDS
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
    // No "How reps are counted" here: the card fits a Seeker without scrolling, and card 2 says knees or toes (owner, 12 Oct; board 2a).
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

/** A note in `success` with a drawn mark: the lock on "banked for good", the shield on "private by design" and "private and fair by design". */
@Composable
private fun SuccessNote(mark: Mark, title: String, text: String) = SuccessNote(mark, title, listOf(AnnotatedString(text)))

/** The same note with its text in [lines], one node each, so a line can lead with words in white (card 5). */
@Composable
private fun SuccessNote(mark: Mark, title: String, lines: List<AnnotatedString>) {
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
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary) }
        }
    }
}

private const val NOTE_FILL = 0.08f
private const val NOTE_BORDER = 0.32f

// ---- card 2: two kinds of Holdout -------------------------------------------------------------------

/**
 * Spark and Habit side by side (FR-067, board Tour): a card each, titled by level and length with a tag
 * pill for who it suits, three facts and the level's one-line rule, then the four lines every Holdout shares.
 */
@Composable
private fun TwoKindsCard() {
    Title("Two kinds of Holdout", "Stake SKR on yourself, then do the day's push-ups every day until the end.")
    KindCard("Spark · 7-day", "New to sport", days = "7", form = "knees allowed", counting = "as the Daily 25", rule = "Spark · 7-day lights it up — knees allowed.")
    KindCard("Habit · 21-day", "Already active", days = "21", form = "toes only", counting = "stricter", rule = "Habit · 21-day makes it stick — toes only.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        LineWithIcon(LineIcon.Calendar, "A new one of each starts every Monday.")
        LineWithIcon(LineIcon.Check, "Both ask for 25 push-ups a day.")
        LineWithIcon(LineIcon.Clock, "Full refund until joining closes.")
        Text("Holdouts need a Seeker.", style = LabelStyle, color = HoldoutColors.TextTertiary)
    }
}

@Composable
private fun KindCard(title: String, tag: String, days: String, form: String, counting: String, rule: String) {
    HoldoutCard(padding = PaddingValues(horizontal = Spacing.cardPadding, vertical = Spacing.betweenItems + Spacing.base)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
            StatusChip(tag)
        }
        val length = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono)) { append(days) }
            append(" days")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            Fact("Length", length)
            Fact("Form", AnnotatedString(form))
            Fact("Counting", AnnotatedString(counting))
        }
        Text(rule, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary)
    }
}

@Composable
private fun RowScope.Fact(label: String, value: AnnotatedString) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
        Text(label, style = LabelStyle.copy(fontWeight = FontWeight.Normal), color = HoldoutColors.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextPrimary)
    }
}

private enum class LineIcon { Calendar, Check, Clock }

/** One of the four lines under the two cards: a drawn mark in the accent, then the sentence. */
@Composable
private fun LineWithIcon(icon: LineIcon, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            scale(size.width / 20f, pivot = Offset.Zero) {
                when (icon) {
                    LineIcon.Calendar -> {
                        drawRoundRect(HoldoutColors.Accent, Offset(3f, 4.5f), Size(14f, 12f), CornerRadius(2.5f), style = stroke)
                        drawPath(PathParser().parsePathString("M3 8.5h14M7 2.5v4M13 2.5v4").toPath(), HoldoutColors.Accent, style = stroke)
                    }
                    LineIcon.Check -> drawPath(PathParser().parsePathString("M4 10.5l4 4 8-9").toPath(), HoldoutColors.Accent, style = stroke)
                    LineIcon.Clock -> drawPath(PathParser().parsePathString("M10 3a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM10 6.5V10l2.5 1.5").toPath(), HoldoutColors.Accent, style = stroke)
                }
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextPrimary)
    }
}

// ---- card 3: how a Holdout plays out ----------------------------------------------------------------

/** The worked Habit example that was on card 2, then what each day brings: *Finish* and *Miss a day* (FR-067, board TourPlay). */
@Composable
private fun PlaysOutCard() {
    Title("How a Holdout plays out", "Everyone who starts on Monday stakes together. Do every day to stay in; miss one and you're out.")
    ExampleCard()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        OutcomeTile("Finish", "Your stake back, plus a share of the pool.", HoldoutColors.Success, Modifier.weight(1f))
        OutcomeTile("Miss a day", "You're out. Your stake feeds the prize pool.", HoldoutColors.Warning, Modifier.weight(1f))
    }
}

/** A tile with a 1 dp ring in its colour and the word that names it in the same colour, never the colour alone. */
@Composable
private fun OutcomeTile(title: String, text: String, colour: Color, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(Spacing.border, colour.copy(alpha = NOTE_BORDER), shape)
            .padding(horizontal = Spacing.cardPadding - Spacing.base, vertical = Spacing.betweenItems),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = colour)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary)
    }
}

// ---- card 4: where a lost stake goes ----------------------------------------------------------------

/**
 * The split of a lost stake (spec 001 FR-027, FR-037, FR-037e; board TourMoney):
 * 70 % to the prize pool, 10 % set aside as the member's own comeback, 20 % to
 * the project — the bar, then a row for each part.
 */
@Composable
private fun MoneyCard() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
        Text("Where a lost stake goes", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = HoldoutFonts.Mono, color = HoldoutColors.Accent)) { append("80 %") }
                append(" comes back to members. The rest runs Holdout.")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
        )
    }
    HoldoutCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            SplitBar()
            SplitRow("70 %", HoldoutColors.Accent, "This Holdout's prize pool", "Split by everyone who finishes.")
            SplitRow("10 %", HoldoutColors.Success, "Your comeback", "Finish your next Holdout of the same kind and it comes back. If not, it joins the boost pool.")
            SplitRow("20 %", HoldoutColors.TextSecondary, "The project", "Keeps Holdout running.")
        }
    }
    Text(
        "Lost on the last day? Join the next one on its first day, or the one after. Nothing is taken from a stake that finishes.",
        style = MaterialTheme.typography.bodyMedium,
        color = HoldoutColors.TextTertiary,
    )
}

/** 70 · 10 · 20, in the three parts' colours, with a hairline gap between them. */
@Composable
private fun SplitBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Of every lost stake: 70 percent to the prize pool, 10 percent to your comeback, 20 percent to the project." },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.weight(70f).fillMaxHeight().background(HoldoutColors.Accent))
        Box(Modifier.weight(10f).fillMaxHeight().background(HoldoutColors.Success))
        Box(Modifier.weight(20f).fillMaxHeight().background(HoldoutColors.TextPrimary.copy(alpha = 0.24f)))
    }
}

@Composable
private fun SplitRow(percent: String, colour: Color, title: String, vararg lines: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Text(
            percent,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = HoldoutFonts.Mono),
            color = colour,
            modifier = Modifier.width(52.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = HoldoutColors.TextPrimary)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary) }
        }
    }
}

// ---- card 5: how reps are counted -------------------------------------------------------------------

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
    // Private and fair in one note, three lines with the lead words in white (owner, 12 and 13 Oct; FR-067).
    SuccessNote(Mark.Shield, "Private and fair by design", TRUST)
}

/** The note's three lines, each leading with the words that matter in `TextPrimary` (FR-067, 2026-10-14). */
private val TRUST = listOf(
    trustLine("On-device AI", " counts your push-ups."),
    trustLine("No mic, no video, no body data", " leaves your phone."),
    trustLine("A Solana smart contract", " holds the stakes and pays out by open, verifiable rules."),
)

private fun trustLine(lead: String, rest: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = HoldoutColors.TextPrimary, fontWeight = FontWeight.Medium)) { append(lead) }
    append(rest)
}

private val STEPS = listOf(
    "Phone on the floor, at 45°, ahead of your shoulder.",
    "Get into a push-up: head to feet, knees included, inside the outline.",
    "The checks tick green. Hold the top for a second, then 3 · 2 · 1 and every rep counts.",
)

// ---- card 6: scripted members -----------------------------------------------------------------------

/** Spark's minimum before scripted members stop joining (spec 001 FR-043), and a Hackathon round's once someone real joins. */
private const val SPARK_MINIMUM = 20
private const val HACKATHON_MINIMUM = 4

@Composable
private fun ScriptedCard() {
    // On devnet scripted members play Spark and the Hackathon rounds, not Habit (owner, 13 Oct).
    Title("Scripted members\nin Spark and Hackathon")
    ScriptedRow(real = SPARK_MINIMUM - SCRIPTED_SHOWN, scripted = SCRIPTED_SHOWN)
    Body("Some members in these Holdouts are scripted.\nReal wallets, same rules, labelled SCRIPTED.")
    Text(
        "They only top a Holdout up: a Spark to $SPARK_MINIMUM,\na Hackathon to $HACKATHON_MINIMUM once someone real joins.\nTheir days aren't camera-checked.",
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
