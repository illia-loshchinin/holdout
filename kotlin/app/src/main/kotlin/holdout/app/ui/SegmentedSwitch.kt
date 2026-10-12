package holdout.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutShapes
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/** One side of a [SegmentedSwitch]: its [label] and the [count] of what it holds, set after it in the mono face. */
data class Segment(val label: String, val count: Int)

/**
 * A switch between two or three views of one list (My Holdouts: *Now · 3* and *Past · 4*): one pill row on
 * `surface-2` with a `hairline` border and 4 dp padding, equal segments 40 dp tall inside a `size-touch`
 * (48 dp) target. Selected: filled `text-primary` (white) with `on-light` text; the others transparent with
 * `text-secondary`. Each segment reads "label · count", the count in the mono face. A tab group for the
 * screen reader: every segment is a selectable tab. Unlike [ChoiceChips] it sets a view, not a value, and
 * always has one chosen.
 */
@Composable
fun SegmentedSwitch(segments: List<Segment>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface2, HoldoutShapes.extraLarge)
            .border(Spacing.border, HoldoutColors.Hairline, HoldoutShapes.extraLarge)
            .padding(Spacing.base)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        segments.forEachIndexed { i, segment ->
            val picked = i == selected
            val color = if (picked) HoldoutColors.OnLight else HoldoutColors.TextSecondary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SEGMENT_HEIGHT)
                    .clip(HoldoutShapes.extraLarge)
                    .background(if (picked) HoldoutColors.TextPrimary else HoldoutColors.Surface2, HoldoutShapes.extraLarge)
                    .selectable(selected = picked, role = Role.Tab, onClick = { if (!picked) onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                val text = buildAnnotatedString {
                    append("${segment.label} · ")
                    withStyle(SpanStyle(fontFamily = AmountStyle.fontFamily, fontFeatureSettings = AmountStyle.fontFeatureSettings)) { append(segment.count.toString()) }
                }
                Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = color)
            }
        }
    }
}

private val SEGMENT_HEIGHT = 40.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SegmentedSwitchPreview() {
    HoldoutTheme {
        var selected by remember { mutableIntStateOf(0) }
        SegmentedSwitch(
            segments = listOf(Segment("Now", 3), Segment("Past", 4)),
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(Spacing.screenGutter),
        )
    }
}
