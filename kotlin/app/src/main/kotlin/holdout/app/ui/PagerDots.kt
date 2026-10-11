package holdout.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * The dots under a pager (the tour): the current one is a 22 dp white pill, the
 * others 7 dp dots in `drag-handle` white. [current] is zero-based. Decorative
 * to the eye, but not to TalkBack: the accessible name is "Card 2 of 4" (the
 * [noun] is the caller's word for a page).
 */
@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier, noun: String = "Card") {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$noun ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            val width = animateDpAsState(if (active) ACTIVE_WIDTH else DOT, tween(durationMillis = 200), label = "dot width")
            val colour = animateColorAsState(if (active) HoldoutColors.TextPrimary else HoldoutColors.DragHandle, tween(durationMillis = 200), label = "dot colour")
            Box(
                Modifier
                    .width(width.value)
                    .height(DOT)
                    .background(colour.value, CircleShape),
            )
        }
    }
}

private val DOT = 7.dp
private val ACTIVE_WIDTH = 22.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PagerDotsPreview() {
    HoldoutTheme { PagerDots(count = 4, current = 1) }
}
