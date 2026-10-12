package holdout.app.sitting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.HoldoutSheet
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.repcounter.CameraView
import holdout.repcounter.Stakes

/**
 * Where the phone goes and where the body goes — shown **while the member is
 * still holding the phone**, before anybody gets down on the floor.
 *
 * An earlier version drew this over the camera until the body was detected on
 * the floor, which put a card of instructions in front of somebody already in a
 * plank who could neither read it nor dismiss it. So it is read at the start and
 * closed with a tap: three short pages — the phone, the body, how the count
 * starts — and a "don't show this again" that is remembered per wallet and per
 * stakes, because a Holdout has a different rule to read.
 *
 * It is the shared bottom sheet (board 16, `HoldoutSheet`) over the dimmed
 * camera, with no intro paragraph under its title. Positioning says where the
 * phone and the body go; Rules says what counts in this sitting, by its own
 * form (a Habit asks for the toes, a Spark and the Daily 25 allow the knees;
 * 002 FR-014); Start says how the count begins.
 */
@Composable
fun BoxScope.SetupSheet(
    stakes: Stakes,
    detected: CameraView?,
    onDone: (dontShowAgain: Boolean) -> Unit,
    levelLabel: String = SittingCopy.levelLabel(stakes),
) {
    // Hoisted here so every way out carries the tick: the buttons, a scrim tap, a drag down and system Back.
    var dontShowAgain by remember { mutableStateOf(false) }
    BackHandler { onDone(dontShowAgain) }
    HoldoutSheet(onDismiss = { onDone(dontShowAgain) }, dismissLabel = "Close the set-up guide") {
        SetupGuide(
            stakes = stakes,
            detected = detected,
            levelLabel = levelLabel,
            dontShowAgain = dontShowAgain,
            onDontShowAgainChange = { dontShowAgain = it },
            onDone = onDone,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A page that runs past the bottom of its scroll area fades out there, so the cut line is read
 * as "more below" and not as clipped text; at the end of the scroll the fade is gone.
 */
private fun Modifier.fadeBottomWhileMore(scroll: ScrollState): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (scroll.canScrollForward) {
            val fade = FadeHeight.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(HoldoutColors.TextPrimary, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private val FadeHeight = 48.dp

/** The sheet's content: title with the level, the three tabs (Positioning, Rules, Start), the page, "Don't show this again" and the buttons. */
@Composable
internal fun SetupGuide(
    stakes: Stakes,
    detected: CameraView?,
    levelLabel: String,
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onDone: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf("1. Positioning", "2. Rules", "3. Start")

    // One scroll offset for the whole sheet would carry a page's scroll to the next page: every page opens at the top.
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.tight, bottom = Spacing.betweenSections),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Before you get down", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
            Text(levelLabel, style = AmountStyle.copy(fontSize = LabelStyle.fontSize), color = HoldoutColors.TextTertiary)
        }

        Tabs(pages, page) { page = it }

        Column(
            modifier = Modifier
                .weight(1f)
                .fadeBottomWhileMore(scroll)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            when (page) {
                0 -> PositioningPage(detected)
                1 -> RulesPage(stakes)
                else -> StartPage()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDontShowAgainChange(!dontShowAgain) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = dontShowAgain,
                onCheckedChange = onDontShowAgainChange,
                colors = CheckboxDefaults.colors(checkedColor = HoldoutColors.Accent),
            )
            Text("Don't show this again", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            SecondaryButton(if (page > 0) "Back" else "Skip", { if (page > 0) page -= 1 else onDone(dontShowAgain) }, Modifier.weight(1f))
            PrimaryButton(if (page < pages.lastIndex) "Next" else "Got it", { if (page < pages.lastIndex) page += 1 else onDone(dontShowAgain) }, Modifier.weight(1f))
        }
    }
}

/** The three tabs as a segmented pill: the open one white with black text. Tap any, or walk through with Next. */
@Composable
private fun Tabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface1, CircleShape)
            .padding(Spacing.base),
        horizontalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        titles.forEachIndexed { i, title ->
            val open = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(TabHeight)
                    .background(if (open) HoldoutColors.TextPrimary else Color.Transparent, CircleShape)
                    .clickable { onSelect(i) }
                    .semantics {
                        role = Role.Tab
                        this.selected = open
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (open) HoldoutColors.OnLight else HoldoutColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PositioningPage(detected: CameraView?) {
    GuideLine("Phone on the floor, leaning on something, facing where you'll be.")
    Placement(
        title = "At 45°",
        caption = "Ahead of your shoulder, about 1.5 m away.",
        highlighted = detected == CameraView.OBLIQUE,
    )
    GuideLine("Top of a push-up, hands under your shoulders, turned a little away: it sees you head to feet and your near knee.")
    GuideLine("Line up with the outline on screen.", strong = true)
}

/** What counts in this sitting, by its own form (a Hackathon Holdout follows its form like any other), then the line common to all. */
@Composable
private fun RulesPage(stakes: Stakes) {
    when (stakes) {
        Stakes.STAKED -> {
            GuideLine("Full push-ups from your toes, down to just above the floor.", strong = true)
            GuideLine("Knee push-ups, or hips resting on the floor, don't count.", strong = true)
        }
        Stakes.EASY -> {
            GuideLine("Aim for full push-ups from your toes.", strong = true)
            GuideLine("From the knees still counts, body straight from the knees.", strong = true)
        }
        Stakes.DAILY -> GuideLine("From your toes or your knees — both count.", strong = true)
    }
    GuideLine("All the way down and up — short reps are skipped.")
}

@Composable
private fun StartPage() {
    GuideLine("Hold the top for a second. A ring fills, then “three, two, one, go”.")
    GuideLine("Nothing to tap. Each rep beeps; every 10 bank for good.")
    GuideLine("Stop any time with ✕ in the corner.")
}

/** One line of the guide, led by an accent tick: the board's check-circle. */
@Composable
private fun GuideLine(text: String, strong: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Canvas(
            Modifier
                .padding(top = Spacing.base)
                .size(TickSize),
        ) {
            val stroke = 1.3.dp.toPx()
            drawCircle(HoldoutColors.Accent, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
            drawLine(HoldoutColors.Accent, Offset(size.width * 0.31f, size.height * 0.51f), Offset(size.width * 0.44f, size.height * 0.64f), stroke, StrokeCap.Round)
            drawLine(HoldoutColors.Accent, Offset(size.width * 0.44f, size.height * 0.64f), Offset(size.width * 0.7f, size.height * 0.36f), stroke, StrokeCap.Round)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (strong) HoldoutColors.TextPrimary else HoldoutColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The placement, drawn from above and ticked once the camera sees the phone there. */
@Composable
private fun Placement(title: String, caption: String, highlighted: Boolean) {
    val accent = if (highlighted) HoldoutColors.Success else HoldoutColors.Accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface1, MaterialTheme.shapes.medium)
            .border(Spacing.border, HoldoutColors.Hairline, MaterialTheme.shapes.medium)
            .padding(Spacing.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Text(
            text = if (highlighted) "$title ✓" else title,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) HoldoutColors.Success else HoldoutColors.TextPrimary,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlacementHeight),
        ) {
            planAngled(accent)
        }
        Text(
            caption,
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** At 45°, from above: the member lying across, the phone ahead of and beside the shoulders. */
private fun DrawScope.planAngled(accent: Color) {
    val w = size.width
    val h = size.height
    val bodyY = h * 0.3f
    val stroke = 6f
    val body = HoldoutColors.TextPrimary
    drawCircle(body, radius = h * 0.09f, center = Offset(w * 0.34f, bodyY))
    drawLine(body, Offset(w * 0.41f, bodyY), Offset(w * 0.98f, bodyY), stroke, StrokeCap.Round)
    drawLine(body, Offset(w * 0.45f, bodyY - h * 0.1f), Offset(w * 0.45f, bodyY + h * 0.1f), stroke, StrokeCap.Round)
    drawCircle(body, radius = 5f, center = Offset(w * 0.47f, bodyY - h * 0.15f))
    drawCircle(body, radius = 5f, center = Offset(w * 0.47f, bodyY + h * 0.15f))

    // Ahead of the head and off to the side, looking back along the body at 45°.
    val phone = Offset(w * 0.14f, h * 0.84f)
    drawLine(
        accent.copy(alpha = 0.6f),
        Offset(phone.x + w * 0.05f, phone.y - h * 0.06f),
        Offset(w * 0.45f, bodyY + h * 0.16f),
        strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
    )
    drawPhone(phone, accent)
}

/** A phone standing on its edge, seen from above, its lens facing up the drawing. */
private fun DrawScope.drawPhone(centre: Offset, accent: Color) {
    val pw = size.width * 0.22f
    val ph = size.height * 0.07f
    drawRoundRect(
        color = accent,
        topLeft = Offset(centre.x - pw / 2f, centre.y - ph / 2f),
        size = Size(pw, ph),
        cornerRadius = CornerRadius(6f, 6f),
    )
    val lensY = centre.y - ph / 2f
    drawCircle(HoldoutColors.Canvas, radius = ph * 0.28f, center = Offset(centre.x, lensY))
}

/**
 * A body in the top-of-a-push-up position, drawn inside the outline over the
 * camera, so the outline says what goes in it: lying across the frame, as the
 * phone at 45° sees the body.
 */
fun DrawScope.drawPlankFigure(box: Rect, color: Color) {
    val w = box.width
    val h = box.height
    fun p(x: Float, y: Float) = Offset(box.left + x * w, box.top + y * h)
    val stroke = (minOf(w, h) * 0.045f).coerceIn(4f, 12f)
    val line = Stroke(width = stroke, cap = StrokeCap.Round)

    drawCircle(color, radius = h * 0.13f, center = p(0.08f, 0.3f), style = line)
    // Shoulder to heel in one straight line, on the toes.
    drawLine(color, p(0.16f, 0.36f), p(0.95f, 0.88f), stroke, StrokeCap.Round)
    drawLine(color, p(0.95f, 0.88f), p(0.98f, 0.98f), stroke, StrokeCap.Round)
    // A straight arm down to the floor.
    drawLine(color, p(0.18f, 0.37f), p(0.2f, 0.98f), stroke, StrokeCap.Round)
}

private val TabHeight = 40.dp
private val TickSize = 16.dp
private val PlacementHeight = 112.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun SetupPhonePreview() {
    HoldoutTheme { Box(Modifier.fillMaxSize()) { SetupSheet(Stakes.STAKED, CameraView.OBLIQUE, {}) } }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun SetupDailyPreview() {
    HoldoutTheme { Box(Modifier.fillMaxSize()) { SetupSheet(Stakes.DAILY, null, {}) } }
}
