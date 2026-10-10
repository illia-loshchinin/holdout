package holdout.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors

/**
 * The Holdout wordmark (design book, Iconography → Logo): the name set in
 * type, never drawn — "Holdout" in the `counter` face at weight 300, title
 * case, `text-primary`, tracked −2%. The book names Roboto Flex; until it is
 * bundled (T035) this follows [CounterStyle], which the theme sets in the
 * system face at the same weight.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        text = "Holdout",
        style = CounterStyle.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em),
        color = HoldoutColors.TextPrimary,
        modifier = modifier,
    )
}
