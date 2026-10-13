package holdout.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing

/**
 * "Skip" in the top corner of the welcome and of tour cards 1–5: a quiet `body` word in `text-tertiary`
 * (it must not compete with Next), with a padded target of at least `size-touch` (FR-067, FR-067a).
 */
@Composable
fun SkipAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        "Skip",
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextTertiary,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.tight, vertical = Spacing.betweenItems),
    )
}
