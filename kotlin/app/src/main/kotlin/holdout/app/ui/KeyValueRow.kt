package holdout.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * A row inside a card: the [label] on the left in `text-secondary`, the
 * [value] on the right in the `amount` face (mono, tabular). [success] sets the
 * value in `success` for the one row that carries good news (**Share of the
 * prize pool**, **Stake back**). The accessible name reads label then value.
 */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, success: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = AmountStyle,
            color = if (success) HoldoutColors.Success else HoldoutColors.TextPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun KeyValueRowPreview() {
    HoldoutTheme {
        HoldoutCard {
            KeyValueRow("Stake back", "1,250 SKR")
            KeyValueRow("Share of the prize pool", "310 SKR", success = true)
        }
    }
}
