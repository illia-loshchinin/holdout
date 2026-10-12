package holdout.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import holdout.app.ui.theme.HoldoutColors

/**
 * The Holdout wordmark (design book, Iconography → Logo): the name set in
 * type, never drawn — *HOLDOUT* in the system Roboto at weight 500, all
 * capitals, `text-primary`, tracked +22%, as the first-open board sets it
 * (owner, 12 Oct). The book's outlined logo files carry the same name.
 * Screen readers hear "Holdout", not seven letters.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        text = "HOLDOUT",
        style = WordmarkStyle,
        color = HoldoutColors.TextPrimary,
        modifier = modifier.semantics { contentDescription = "Holdout" },
    )
}

private val WordmarkStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.22.em,
)
