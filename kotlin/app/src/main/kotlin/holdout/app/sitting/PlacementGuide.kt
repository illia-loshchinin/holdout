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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            Text("Before you get down", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.TextPrimary)

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
        if (stakes.accepts(CameraView.FRONT)) {
            Placement(
                view = CameraView.FRONT,
                title = "In front",
                caption = "Head towards the phone, hands ~1.5 m from it.",
                highlighted = detected == CameraView.FRONT,
            )
        }
        Placement(
            view = CameraView.OBLIQUE,
            title = "At 45°",
            caption = "Ahead of your shoulder, ~1.5 m away, looking at you at 45°.",
            highlighted = detected == CameraView.OBLIQUE,
        )
    }
    if (stakes == Stakes.STAKED) {
        Text(
            "Staked: at 45° only — not in front, not straight beside you.",
            style = MaterialTheme.typography.bodyMedium,
            color = HoldoutColors.Accent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BodyPage(stakes: Stakes) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val box = Rect(size.width * 0.1f, size.height * 0.1f, size.width * 0.9f, size.height * 0.95f)
        drawPlankFigure(box, CameraView.SIDE, HoldoutColors.Accent)
    }
    Bullets(
        buildList {
            add("Get into the top of a push-up: arms straight, hands under your shoulders.")
            add("Your whole body in the picture — head to feet. Move back if it is not.")
            add("Line your body up with the outline on the screen.")
            if (stakes == Stakes.STAKED) {
                add("On your toes. Push-ups from the knees do not count.")
            } else {
                add("Body straight from shoulders to heels.")
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
    if (stakes == Stakes.STAKED) {
        Text(Stakes.STAKED_RULE, style = MaterialTheme.typography.bodyMedium, color = HoldoutColors.Accent, textAlign = TextAlign.Center)
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
private fun Placement(view: CameraView, title: String, caption: String, highlighted: Boolean) {
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
            when (view) {
                CameraView.FRONT -> planFront(accent)
                CameraView.OBLIQUE -> planAngled(accent)
                CameraView.SIDE -> planSide(accent)
            }
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

/** Head-on, from above: the phone at the top, the member lying away from it. */
private fun DrawScope.planFront(accent: Color) {
    val w = size.width
    val h = size.height
    drawPhone(Offset(w / 2f, h * 0.08f), accent, lensDown = true)
    drawLine(
        accent.copy(alpha = 0.6f),
        Offset(w / 2f, h * 0.17f),
        Offset(w / 2f, h * 0.33f),
        strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
    )
    // Head nearest the phone, body stretching away down the frame.
    val stroke = 6f
    drawCircle(BODY, radius = w * 0.06f, center = Offset(w / 2f, h * 0.41f))
    val shoulderY = h * 0.5f
    drawLine(BODY, Offset(w * 0.34f, shoulderY), Offset(w * 0.66f, shoulderY), stroke, StrokeCap.Round)
    // Hands on the floor just outside the shoulders.
    drawCircle(BODY, radius = 5f, center = Offset(w * 0.26f, h * 0.5f))
    drawCircle(BODY, radius = 5f, center = Offset(w * 0.74f, h * 0.5f))
    drawLine(BODY, Offset(w / 2f, shoulderY), Offset(w / 2f, h * 0.74f), stroke, StrokeCap.Round)
    drawLine(BODY, Offset(w / 2f, h * 0.74f), Offset(w * 0.45f, h * 0.97f), stroke, StrokeCap.Round)
    drawLine(BODY, Offset(w / 2f, h * 0.74f), Offset(w * 0.55f, h * 0.97f), stroke, StrokeCap.Round)
}

/** Side-on, from above: the member lying across, the phone off to one side at chest level. */
private fun DrawScope.planSide(accent: Color) {
    val w = size.width
    val h = size.height
    val bodyY = h * 0.28f
    val stroke = 6f
    drawCircle(BODY, radius = w * 0.05f, center = Offset(w * 0.1f, bodyY))
    drawLine(BODY, Offset(w * 0.17f, bodyY), Offset(w * 0.96f, bodyY), stroke, StrokeCap.Round)
    // Shoulders, and the hands either side of them.
    drawLine(BODY, Offset(w * 0.22f, bodyY - h * 0.1f), Offset(w * 0.22f, bodyY + h * 0.1f), stroke, StrokeCap.Round)
    drawCircle(BODY, radius = 5f, center = Offset(w * 0.24f, bodyY - h * 0.15f))
    drawCircle(BODY, radius = 5f, center = Offset(w * 0.24f, bodyY + h * 0.15f))

    val phone = Offset(w * 0.3f, h * 0.9f)
    drawLine(
        accent.copy(alpha = 0.6f),
        Offset(phone.x, phone.y - h * 0.1f),
        Offset(phone.x, bodyY + h * 0.2f),
        strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
    )
    drawPhone(phone, accent, lensDown = false)
}

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
    drawPhone(phone, accent, lensDown = false)
}

/** A phone standing on its edge, seen from above, with its lens facing [lensDown] or up. */
private fun DrawScope.drawPhone(centre: Offset, accent: Color, lensDown: Boolean) {
    val pw = size.width * 0.22f
    val ph = size.height * 0.07f
    drawRoundRect(
        color = accent,
        topLeft = Offset(centre.x - pw / 2f, centre.y - ph / 2f),
        size = Size(pw, ph),
        cornerRadius = CornerRadius(6f, 6f),
    )
    val lensY = if (lensDown) centre.y + ph / 2f else centre.y - ph / 2f
    drawCircle(HoldoutColors.Canvas, radius = ph * 0.28f, center = Offset(centre.x, lensY))
}

/**
 * A body in the top-of-a-push-up position, drawn inside the outline over the
 * camera, so the outline says what goes in it: lying across the frame from the
 * side, foreshortened and facing the lens head-on.
 */
fun DrawScope.drawPlankFigure(box: Rect, view: CameraView, color: Color) {
    val w = box.width
    val h = box.height
    fun p(x: Float, y: Float) = Offset(box.left + x * w, box.top + y * h)
    val stroke = (minOf(w, h) * 0.045f).coerceIn(4f, 12f)
    val line = Stroke(width = stroke, cap = StrokeCap.Round)

    when (view) {
        CameraView.FRONT -> {
            drawCircle(color, radius = w * 0.1f, center = p(0.5f, 0.2f), style = line)
            drawLine(color, p(0.22f, 0.38f), p(0.78f, 0.38f), stroke, StrokeCap.Round)
            // Straight arms down to hands on the floor, a little wider than the shoulders.
            drawLine(color, p(0.22f, 0.38f), p(0.12f, 0.96f), stroke, StrokeCap.Round)
            drawLine(color, p(0.78f, 0.38f), p(0.88f, 0.96f), stroke, StrokeCap.Round)
            // The torso and legs run away from the lens, so they are short.
            drawLine(color, p(0.5f, 0.38f), p(0.5f, 0.62f), stroke, StrokeCap.Round)
            drawLine(color, p(0.5f, 0.62f), p(0.44f, 0.76f), stroke, StrokeCap.Round)
            drawLine(color, p(0.5f, 0.62f), p(0.56f, 0.76f), stroke, StrokeCap.Round)
        }

        else -> {
            drawCircle(color, radius = h * 0.13f, center = p(0.08f, 0.3f), style = line)
            // Shoulder to heel in one straight line, on the toes.
            drawLine(color, p(0.16f, 0.36f), p(0.95f, 0.88f), stroke, StrokeCap.Round)
            drawLine(color, p(0.95f, 0.88f), p(0.98f, 0.98f), stroke, StrokeCap.Round)
            // A straight arm down to the floor.
            drawLine(color, p(0.18f, 0.37f), p(0.2f, 0.98f), stroke, StrokeCap.Round)
        }
    }
}
