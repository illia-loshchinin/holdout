package holdout.app.sitting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.PrimaryButton
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * The camera permission, before the OS asks (board 15; screen map S1, N4).
 *
 * ```
 *  (✕)        DAY 12 · 25 REPS
 *
 *               ( [camera] )         ← 88 dp Surface3 circle, accent line icon
 *           The camera counts
 *              your reps             ← title, light
 *     Nothing is recorded or uploaded.
 *
 *  [            Allow camera           ]  ← PrimaryButton; "Open settings" when denied
 * ```
 *
 * Nothing about the frames leaves the phone (constitution X); the sentence says
 * so. Shown only while the camera is not allowed — a member who already
 * allowed it never sees it.
 */
@Composable
internal fun CameraPermissionScreen(
    label: String,
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.betweenSections),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        SittingTopBar(label, onStop)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems, Alignment.CenterVertically),
        ) {
            Column(
                modifier = Modifier
                    .size(IconCircle)
                    .background(HoldoutColors.Surface3, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CameraIcon()
            }
            if (denied) {
                Copy("Camera access is off", "Turn it on in Settings › Apps › Holdout › Permissions.")
            } else {
                Copy("The camera counts\nyour reps", "Nothing is recorded or uploaded.")
            }
        }
        if (denied) {
            PrimaryButton("Open settings", onOpenSettings)
        } else {
            PrimaryButton("Allow camera", onAllow)
        }
    }
}

@Composable
private fun Copy(title: String, sentence: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** A camera body with a lens, drawn as a line icon in the accent. */
@Composable
private fun CameraIcon() {
    Canvas(Modifier.size(IconSize)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = HoldoutColors.Accent,
            topLeft = Offset(w * 0.14f, h * 0.26f),
            size = Size(w * 0.72f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke,
        )
        drawCircle(HoldoutColors.Accent, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.55f), style = stroke)
    }
}

private val IconCircle = 88.dp
private val IconSize = 36.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CameraAskPreview() {
    HoldoutTheme { CameraPermissionScreen(SittingCopy.countingLabel(12, 25), denied = false, onAllow = {}, onOpenSettings = {}, onStop = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun CameraDeniedPreview() {
    HoldoutTheme { CameraPermissionScreen(SittingCopy.countingLabel(null, 25), denied = true, onAllow = {}, onOpenSettings = {}, onStop = {}) }
}
