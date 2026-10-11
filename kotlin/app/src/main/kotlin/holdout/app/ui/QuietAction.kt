package holdout.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * A text-only button for the tertiary paths that should not compete with the
 * numbers (design book, Quiet action): `body` at weight 500 in `accent`, or in
 * `warning` for a leave or a stop; no fill, no underline, at least
 * `size-touch` (48 dp) tall and wide. The text starts at the content edge, so a
 * link in a list lines up with the cards above it. Never the screen's main action, and never a menu
 * in a card's corner.
 */
@Composable
fun QuietAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, warning: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (warning) HoldoutColors.Warning else HoldoutColors.Accent,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QuietActionPreview() {
    HoldoutTheme {
        androidx.compose.foundation.layout.Column {
            QuietAction("No wallet yet?", onClick = {})
            QuietAction("Leave — full refund until joining closes", onClick = {}, warning = true)
        }
    }
}
