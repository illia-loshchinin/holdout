package holdout.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme

/**
 * A card that reports something the member needs to know, with a quiet way out
 * (design book, Notice card): a [title] in `text-primary`, or in `warning` when
 * something was lost or refused ([warning]); a [text] sentence that says what
 * happened and what to do next; and one [QuietAction] when the card can be
 * dismissed ([actionText] with [onAction]). No icon, no primary button inside.
 *
 * Not the one-line `Notice`, which has no title and no action. A warning card
 * never blames the person.
 */
@Composable
fun NoticeCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HoldoutCard(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (warning) HoldoutColors.Warning else HoldoutColors.TextPrimary,
        )
        Body(text)
        if (actionText != null && onAction != null) {
            QuietAction(actionText, onAction, warning = warning)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun NoticeCardPreview() {
    HoldoutTheme {
        NoticeCard(
            title = "Joining closed",
            text = "Your transaction landed too late. Nothing was taken.",
            warning = true,
            actionText = "OK",
            onAction = {},
        )
    }
}
