package holdout.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/** One tab of the [BottomNav]: its label, icon, and the accessible name that says what its dot means. */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    /** A dot on the pill's corner — never a count. */
    val dot: Boolean = false,
    /** "Standing, 3 dropped since you last looked"; the label alone when there is no dot. */
    val accessibleName: String = label,
)

/**
 * The bottom bar (DS BottomNav): four labelled tabs on `surface-2` with a
 * hairline top edge, `size-nav` tall. Each tab is a 56×32 pill holding a 24 dp
 * icon with a 12 sp/500 label under it; the active pill is white with a black
 * icon. A badge is an 8 dp accent dot on the pill's corner, its meaning in the
 * tab's accessible name. Shown on tab roots only — the shell decides.
 *
 * ```
 *  ─────────────────────────────────────────── hairline
 *   (⌂)        ∿•         ☰          ◯         56×32 pill, 24 dp icon, dot
 *  Today    Standing   Holdouts      Me        12 sp / 500
 * ```
 */
@Composable
fun BottomNav(items: List<BottomNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(HoldoutColors.Surface2)) {
        HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
        Row(
            modifier = Modifier.fillMaxWidth().height(Spacing.navHeight - Spacing.border).padding(horizontal = Spacing.tight),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item -> NavTab(item, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun NavTab(item: BottomNavItem, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = NAV_ITEM_MIN_WIDTH)
            .clip(androidx.compose.material3.MaterialTheme.shapes.small)
            .selectable(selected = active, onClick = onClick, role = Role.Tab)
            .clearAndSetSemantics {
                contentDescription = item.accessibleName
                role = Role.Tab
                selected = active
            }
            .padding(vertical = Spacing.base),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        Box(
            modifier = Modifier
                .size(width = PILL_WIDTH, height = PILL_HEIGHT)
                .clip(androidx.compose.material3.MaterialTheme.shapes.extraLarge)
                .background(if (active) HoldoutColors.TextPrimary else HoldoutColors.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = if (active) HoldoutColors.OnLight else HoldoutColors.TextTertiary, modifier = Modifier.size(ICON_SIZE))
            if (item.dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -DOT_INSET_END, y = DOT_INSET_TOP)
                        .size(DOT_SIZE)
                        .clip(CircleShape)
                        .background(HoldoutColors.Accent),
                )
            }
        }
        Text(item.label, style = LabelStyle, color = if (active) HoldoutColors.TextPrimary else HoldoutColors.TextTertiary)
    }
}

/** The bar's four icons — house · pulse · list · person — drawn from the design book's BottomNav preview (24×24, 2 px round strokes). */
object NavIcons {
    val House: ImageVector = stroked("house", "M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5h-5v5H5a1 1 0 0 1-1-1z")
    val Pulse: ImageVector = stroked("pulse", "M3 12h4l2.5-6 5 12 2.5-6h4")
    val List: ImageVector = stroked("list", "M5 6h14M5 12h14M5 18h14")

    // The preview's circle (cx 12, cy 8, r 4) as two arcs, then the shoulders.
    val Person: ImageVector = stroked("person", "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z M4.5 20a7.5 7.5 0 0 1 15 0")

    private fun stroked(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(HoldoutColors.TextPrimary),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

private val PILL_WIDTH = 56.dp
private val PILL_HEIGHT = 32.dp
private val ICON_SIZE = 24.dp
private val DOT_SIZE = 8.dp
private val DOT_INSET_TOP = 3.dp
private val DOT_INSET_END = 13.dp
private val NAV_ITEM_MIN_WIDTH = 64.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BottomNavPreview() {
    HoldoutTheme {
        BottomNav(
            items = listOf(
                BottomNavItem("Today", NavIcons.House),
                BottomNavItem("Standing", NavIcons.Pulse, dot = true, accessibleName = "Standing, 3 dropped since you last looked"),
                BottomNavItem("Holdouts", NavIcons.List),
                BottomNavItem("Me", NavIcons.Person),
            ),
            selected = 0,
            onSelect = {},
        )
    }
}
