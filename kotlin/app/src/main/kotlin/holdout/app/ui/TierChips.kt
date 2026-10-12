package holdout.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * One stake tier: the approximate dollar label (`≈ $25`, [Format.tierUsd]) over
 * the exact amount in SKR (`1,250 SKR`, [Format.skr]). The dollar label is never
 * what the member receives.
 */
data class TierChip(val usd: String, val skr: String)

/**
 * The stake picker (design book, Tier chips): chips across, one per tier, a
 * single choice. Each is `surface-3` with a `hairline` border and
 * `radius-chip`, the dollar label in 16 sp / 500 over the exact SKR in the
 * `amount` face, `text-secondary`. Selected: `accent-tint` fill and `accent`
 * border and text. A radio group: each chip reads "≈ $25, 1,250 SKR" and says
 * whether it is selected.
 *
 * The caller says "Dollar values are approximate." nearby.
 */
@Composable
fun TierChips(tiers: List<TierChip>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        tiers.forEachIndexed { i, tier ->
            val picked = i == selected
            val shape = MaterialTheme.shapes.small
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = CHIP_MIN_HEIGHT)
                    .clip(shape)
                    .background(if (picked) HoldoutColors.AccentTint else HoldoutColors.Surface3, shape)
                    .border(Spacing.border, if (picked) HoldoutColors.Accent else HoldoutColors.Hairline, shape)
                    .selectable(selected = picked, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = Spacing.base, vertical = Spacing.betweenItems)
                    .semantics { contentDescription = "${tier.usd}, ${tier.skr}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.base, Alignment.CenterVertically),
            ) {
                Text(
                    text = tier.usd,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (picked) HoldoutColors.Accent else HoldoutColors.TextPrimary,
                )
                Text(
                    text = tier.skr,
                    style = LabelStyle,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    color = if (picked) HoldoutColors.Accent else HoldoutColors.TextSecondary,
                    // Four chips share a phone's width: the amount stays on one line ("1,250 SKR" does not wrap).
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private val CHIP_MIN_HEIGHT = 72.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TierChipsPreview() {
    HoldoutTheme {
        var selected by remember { mutableIntStateOf(1) }
        TierChips(
            tiers = listOf(
                TierChip("≈ \$10", "500 SKR"),
                TierChip("≈ \$25", "1,250 SKR"),
                TierChip("≈ \$50", "2,500 SKR"),
                TierChip("≈ \$100", "5,000 SKR"),
            ),
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(Spacing.screenGutter),
        )
    }
}
