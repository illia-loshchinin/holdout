package holdout.app.testutil

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/**
 * Every colour the text of this node is drawn in: its base colour and each coloured span. How a test says
 * "the pool is in Success" or "no text in Warning" without a screenshot.
 */
fun SemanticsNodeInteraction.textColors(): Set<Color> {
    val results = mutableListOf<TextLayoutResult>()
    val read = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
    checkNotNull(read) { "this node has no text layout" }.invoke(results)
    val layout = results.single().layoutInput
    return buildSet {
        add(layout.style.color)
        layout.text.spanStyles.forEach { span -> span.item.color.takeIf { it != Color.Unspecified }?.let { add(it) } }
    }
}
