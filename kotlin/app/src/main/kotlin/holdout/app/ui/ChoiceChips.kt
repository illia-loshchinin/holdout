package holdout.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutShapes
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * A small single choice among short labels (design book, Choice chips): pills
 * 36 dp tall on `surface-3` with a `hairline` border, label-style text (not
 * uppercased), wrapping to as many rows as needed. Selected: `accent-tint`
 * fill, `accent` border and text. A radio group, with a `size-touch` (48 dp)
 * target around every pill.
 *
 * [selected] is the chosen option's index, or null for none. Tapping the
 * chosen pill reports null when [allowEmpty] is set (the label dialog allows
 * it) and does nothing otherwise (a board picker does not). For up to about ten
 * options; the stake tiers have their own component ([TierChips]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    allowEmpty: Boolean = false,
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        options.forEachIndexed { i, option ->
            val picked = i == selected
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = Spacing.touchTarget)
                    .selectable(
                        selected = picked,
                        role = Role.RadioButton,
                        onClick = {
                            if (picked) {
                                if (allowEmpty) onSelect(null)
                            } else {
                                onSelect(i)
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(PILL_HEIGHT)
                        .background(if (picked) HoldoutColors.AccentTint else HoldoutColors.Surface3, HoldoutShapes.extraLarge)
                        .border(Spacing.border, if (picked) HoldoutColors.Accent else HoldoutColors.Hairline, HoldoutShapes.extraLarge)
                        .padding(horizontal = Spacing.betweenItems),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option, style = LabelStyle, color = if (picked) HoldoutColors.Accent else HoldoutColors.TextPrimary)
                }
            }
        }
    }
}

private val PILL_HEIGHT = 36.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChoiceChipsPreview() {
    HoldoutTheme {
        var selected by remember { mutableStateOf<Int?>(1) }
        ChoiceChips(
            options = listOf("Spark · 7-day", "Habit · 21-day", "Short test"),
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(Spacing.screenGutter),
        )
    }
}
