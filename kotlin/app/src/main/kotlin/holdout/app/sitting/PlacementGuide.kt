package holdout.app.sitting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import holdout.app.ui.theme.HoldoutColors
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
 * plank who could neither read it nor dismiss it. So it is a dialog now, read
 * at the start and closed with a tap: three short pages — the phone, the body,
 * how the count starts — and a "don't show this again" that is remembered per
 * wallet and per stakes, because a staked sitting has different rules to read.
 *
 * The placement the camera currently sees is ticked on the first page, so the
 * member sees the app agree with where they just stood the phone.
 */
@Composable
fun SetupDialog(
    stakes: Stakes,
    detected: CameraView?,
    onDone: (dontShowAgain: Boolean) -> Unit,
) {
    var page by remember { mutableStateOf(0) }
    var dontShowAgain by remember { mutableStateOf(false) }
    val pages = listOf("Phone", "Body", "Start")

    // One scroll offset for the whole card would carry a page's scroll to the next page, and the title and
    // headline (FR-015, first on every page) would be off the top of it: every page opens at the top.
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }

    Dialog(
        onDismissRequest = { onDone(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenGutter)
                .background(HoldoutColors.Surface1, RoundedCornerShape(24.dp))
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            // A small phone may not fit every line of a page: this part scrolls rather than clips, and the
            // checkbox and the buttons below it stay where they are, as the design book's Dialog has them.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
            ) {
                Text("Before you get down", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.TextPrimary)
                // What decides whether a rep counts, first and on every page (002 FR-015, 2026-10-10).
                Text(GUIDE_HEADLINE, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Accent, textAlign = TextAlign.Center)

                // The tabs: tap any, or walk through with Next.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    pages.forEachIndexed { i, title ->
                        val selected = i == page
                        TextButton(onClick = { page = i }) {
                            Text(
                                "${i + 1}. $title",
                                style = LabelStyle,
                                color = if (selected) HoldoutColors.Accent else HoldoutColors.TextTertiary,
                                modifier = Modifier
                                    .border(
                                        width = if (selected) 1.dp else 0.dp,
                                        color = if (selected) HoldoutColors.Accent else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
                ) {
                    when (page) {
                        0 -> PhonePage(stakes, detected)
                        1 -> BodyPage(stakes)
                        else -> StartPage(stakes)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dontShowAgain = !dontShowAgain },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                    colors = CheckboxDefaults.colors(checkedColor = HoldoutColors.Accent),
                )
                Text("Don't show this again", style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.TextSecondary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (page > 0) page -= 1 else onDone(dontShowAgain) }) {
                    Text(if (page > 0) "Back" else "Skip", color = HoldoutColors.TextSecondary)
                }
                Button(
                    onClick = { if (page < pages.lastIndex) page += 1 else onDone(dontShowAgain) },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoldoutColors.TextPrimary,
                        contentColor = HoldoutColors.OnLight,
                    ),
                ) {
                    Text(if (page < pages.lastIndex) "Next" else "Got it", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PhonePage(stakes: Stakes, detected: CameraView?) {
    Text(
        "Stand the phone up on the floor, screen facing where you will be. Lean it on something if it will not stand.",
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextPrimary,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Placement(
            title = "At 45°",
            caption = "Ahead of your shoulder, ~1.5 m away, looking at you at 45°.",
            highlighted = detected == CameraView.OBLIQUE,
        )
    }
    Text(
        "From there it sees all of you, and the knee nearer to it.",
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
    Text(
        when (stakes) {
            Stakes.STAKED -> "Habit: at 45° only — not in front, not straight beside you."
            Stakes.EASY -> "Spark: at 45° only — turned well to the side of you, not in front, not straight beside you."
            // The Daily 25 practises the Easy set-up (002 FR-001).
            Stakes.DAILY -> "At 45° only, as in a Holdout — turned well to the side of you, not in front, not straight beside you."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = HoldoutColors.Accent,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BodyPage(stakes: Stakes) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val box = Rect(size.width * 0.1f, size.height * 0.1f, size.width * 0.9f, size.height * 0.95f)
        drawPlankFigure(box, HoldoutColors.Accent)
    }
    Bullets(
        buildList {
            // The two things that decide whether a rep counts come first (002 FR-015, 2026-10-10).
            add("Your whole body in the picture — head to feet. Move back if it is not.")
            add("The phone must see your knees: turn your body a little away from it, so the leg nearer the phone shows clearly.")
            add("Get into the top of a push-up: arms straight, hands under your shoulders.")
            add("Line your body up with the outline on the screen.")
            when (stakes) {
                Stakes.STAKED -> add("On your toes, and stop just above the floor. Push-ups from the knees, or resting on the floor at the bottom, do not count.")
                Stakes.EASY -> add("From your toes or from your knees — both count in a Spark. Keep your body straight from shoulders to knees.")
                Stakes.DAILY -> add("Body straight from shoulders to heels — or from the knees if you are building up. Both count for the Daily 25.")
            }
        },
    )
}

@Composable
private fun StartPage(stakes: Stakes) {
    Bullets(
        listOf(
            "Hold the top of a push-up for a second. A ring fills, then you hear “three, two, one, go”.",
            "Nothing to tap: each counted rep beeps, and every 10 are banked for good.",
            "Go all the way down and all the way up. A rep that is too short is skipped with a hint.",
            "Stop at any time with the button in the corner.",
        ),
    )
    if (stakes.staked) {
        Text(if (stakes == Stakes.EASY) Stakes.EASY_RULE else Stakes.STAKED_RULE, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.Accent, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Bullets(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        lines.forEach {
            Text("•  $it", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
        }
    }
}

@Composable
private fun Placement(title: String, caption: String, highlighted: Boolean) {
    val accent = if (highlighted) HoldoutColors.Success else HoldoutColors.Accent
    Column(
        modifier = Modifier
            .width(150.dp)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = accent.copy(alpha = if (highlighted) 1f else 0.4f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(Spacing.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (highlighted) "$title ✓" else title,
            style = LabelStyle,
            color = accent,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            planAngled(accent)
        }
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private val BODY = Color(0xFFE8E8E8)

/** At 45°, from above: the member lying across, the phone ahead of and beside the shoulders. */
private fun DrawScope.planAngled(accent: Color) {
    val w = size.width
    val h = size.height
    val bodyY = h * 0.3f
    val stroke = 6f
    drawCircle(BODY, radius = w * 0.05f, center = Offset(w * 0.34f, bodyY))
    drawLine(BODY, Offset(w * 0.41f, bodyY), Offset(w * 0.98f, bodyY), stroke, StrokeCap.Round)
    drawLine(BODY, Offset(w * 0.45f, bodyY - h * 0.1f), Offset(w * 0.45f, bodyY + h * 0.1f), stroke, StrokeCap.Round)
    drawCircle(BODY, radius = 5f, center = Offset(w * 0.47f, bodyY - h * 0.15f))
    drawCircle(BODY, radius = 5f, center = Offset(w * 0.47f, bodyY + h * 0.15f))

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

/** The guide's first line on every page: what decides whether a rep counts (002 FR-015). */
internal const val GUIDE_HEADLINE =
    "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone."
