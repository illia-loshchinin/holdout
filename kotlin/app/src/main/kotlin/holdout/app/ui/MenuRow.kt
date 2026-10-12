package holdout.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * An outlined pill that opens something (Me: *About Holdout*, *How Holdout
 * works*; the board's row): the [title] in `text-primary`, a quiet [caption] in
 * `text-tertiary` and a chevron, at least `size-touch` (48 dp) tall, a `hairline`
 * border and no fill. One control for the screen reader: the title then the
 * caption. Not a button: it never competes with the screen's primary action.
 */
@Composable
fun MenuRow(title: String, caption: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .clip(shape)
            .border(Spacing.border, HoldoutColors.Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary, modifier = Modifier.weight(1f))
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Chevron()
    }
}

/** The right-pointing chevron of the board's row, drawn (a Past row on Holdouts has it too): 14 dp, `text-tertiary`, 1.5 stroke. */
@Composable
internal fun Chevron() {
    Canvas(Modifier.size(CHEVRON)) {
        val u = size.minDimension / CHEVRON_UNITS
        val path = Path().apply {
            moveTo(5f * u, 3f * u)
            lineTo(9f * u, 7f * u)
            lineTo(5f * u, 11f * u)
        }
        drawPath(path, HoldoutColors.TextTertiary, style = Stroke(width = CHEVRON_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val CHEVRON = 14.dp
private const val CHEVRON_UNITS = 14f
private const val CHEVRON_STROKE = 1.5f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MenuRowPreview() {
    HoldoutTheme {
        Column(Modifier.padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            MenuRow("About Holdout", "devnet · test token", onClick = {})
            MenuRow("How Holdout works", "5 cards", onClick = {})
        }
    }
}
