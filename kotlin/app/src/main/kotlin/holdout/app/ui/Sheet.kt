package holdout.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * A bottom sheet (DS Sheet) over the screen that opened it, which stays
 * composed behind a scrim: `surface-2`, `radius-sheet` on the top corners, a
 * hairline edge and a 32×4 drag handle. It enters over 300 ms and closes on a
 * drag down or a scrim tap ([onDismiss]). Used for the stake confirm and the
 * faucet only (rulings G8).
 *
 * Drawn in the same composition as the screen under it, not in a window of its
 * own, so the screen tests see it and the opener keeps its state.
 */
@Composable
fun BoxScope.HoldoutSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, dismissLabel: String = "Close", content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS)
    var dragged by remember { mutableFloatStateOf(0f) }
    val dismissAt = with(LocalDensity.current) { DISMISS_DRAG.toPx() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier
            .fillMaxSize()
            .background(HoldoutColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .semantics {
                contentDescription = dismissLabel
                onClick(label = dismissLabel) { onDismiss().let { true } }
            },
    )
    AnimatedVisibility(
        visibleState = shown,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(ENTER_MILLIS)) { it } + fadeIn(tween(ENTER_MILLIS)),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightUpTo(MAX_HEIGHT_FRACTION)
                .offset { IntOffset(0, dragged.roundToInt()) }
                .clip(shape)
                .background(HoldoutColors.Surface2, shape)
                .border(Spacing.border, HoldoutColors.Hairline, shape)
                // Taps inside never reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dragged = (dragged + it).coerceAtLeast(0f) },
                        onDragStopped = { if (dragged > dismissAt) onDismiss() else dragged = 0f },
                    )
                    .padding(vertical = Spacing.tight),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(HANDLE_WIDTH, HANDLE_HEIGHT).clip(MaterialTheme.shapes.extraLarge).background(HoldoutColors.DragHandle))
            }
            content()
        }
    }
}

private val SHEET_RADIUS = 28.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val DISMISS_DRAG = 120.dp
private const val ENTER_MILLIS = 300

/** The sheet leaves the top of the screen it covers in view. */
private const val MAX_HEIGHT_FRACTION = 0.92f

/**
 * Wraps the content, up to [fraction] of the height on offer: a short state
 * (the Seeker gate's) is a short sheet, a long form stops below the top of the screen.
 */
private fun Modifier.heightUpTo(fraction: Float): Modifier = layout { measurable, constraints ->
    val ceiling = if (constraints.hasBoundedHeight) (constraints.maxHeight * fraction).roundToInt() else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = ceiling))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 480)
@Composable
private fun HoldoutSheetPreview() {
    HoldoutTheme {
        Box(Modifier.fillMaxSize()) {
            Text("The screen that opened it", color = HoldoutColors.TextPrimary, modifier = Modifier.padding(Spacing.screenGutter))
            HoldoutSheet(onDismiss = {}) {
                Column(Modifier.padding(Spacing.screenGutter)) {
                    Text("Get test tokens", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                }
            }
        }
    }
}
