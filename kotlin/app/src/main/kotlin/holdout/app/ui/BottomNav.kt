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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import holdout.app.ui.theme.Spacing

/**
 * One tab of the [BottomNav]. The bar draws no words (001 FR-069, 2026-10-13): [accessibleName] is what a
 * screen reader hears — "Today, today's reps are not done" when the dot is on — so the dot's meaning is
 * said, not coloured. [label] is the tab's plain name, kept for previews and tests; it is never drawn.
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    /** A dot on the pill's corner — never a count. */
    val dot: Boolean = false,
    /** "Holdouts, a claim is waiting"; the label alone when there is no dot. */
    val accessibleName: String = label,
)

/**
 * The bottom bar (DS BottomNav, v2): icon-only tabs on `surface-2` with a hairline top edge, `size-nav`
 * tall. Each tab is a 64×40 pill holding a 24 dp icon, in a target at least 48 dp tall; the active pill is
 * white with the icon in `on-light`, the others have no fill and a `text-secondary` icon. A badge is an
 * 8 dp accent dot on the pill's corner, its meaning in the tab's accessible name. Shown on tab roots only
 * — the shell decides.
 *
 * ```
 *  ─────────────────────────────────────────── hairline
 *    ┌────────┐
 *    │   ▣ •  │       ⠿ •            ◯         64×40 pills, 24 dp icons, dot
 *    └────────┘
 *               (no text: the accessible name says it)
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
    Box(
        modifier = Modifier
            .sizeIn(minWidth = PILL_WIDTH, minHeight = Spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraLarge)
            .selectable(selected = active, onClick = onClick, role = Role.Tab)
            .clearAndSetSemantics {
                contentDescription = item.accessibleName
                role = Role.Tab
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = PILL_WIDTH, height = PILL_HEIGHT)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(if (active) HoldoutColors.TextPrimary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (active) HoldoutColors.OnLight else HoldoutColors.TextSecondary,
                modifier = Modifier.size(ICON_SIZE),
            )
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
    }
}

/**
 * The bar's icons, drawn in code (24×24; research R25): [Today] a calendar with today's square filled,
 * [Holdouts] a 3 × 3 dot grid forming an H with the top and bottom middle dots at 35 %, [Me] a person.
 * The colour here is a placeholder; the bar tints each one.
 */
object NavIcons {
    val Today: ImageVector = ImageVector.Builder("today", 24.dp, 24.dp, 24f, 24f)
        .stroked("M5 6h14a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z M4 10.5h16 M8 3.5v3 M16 3.5v3")
        .filled("M13 13.5h4v4h-4z")
        .build()

    val Holdouts: ImageVector = ImageVector.Builder("holdouts", 24.dp, 24.dp, 24f, 24f)
        .filled(dots(6f to 6f, 6f to 12f, 6f to 18f, 18f to 6f, 18f to 12f, 18f to 18f, 12f to 12f))
        .filled(dots(12f to 6f, 12f to 18f), alpha = QUIET_DOT_ALPHA)
        .build()

    // A circle (cx 12, cy 8, r 4) as two arcs, then the shoulders.
    val Me: ImageVector = ImageVector.Builder("me", 24.dp, 24.dp, 24f, 24f)
        .stroked("M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z M4.5 20a7.5 7.5 0 0 1 15 0")
        .build()

    /** One path of circles (two arcs each) of radius [DOT_RADIUS] around the given centres. */
    private fun dots(vararg centres: Pair<Float, Float>): String = centres.joinToString(" ") { (x, y) ->
        "M${x - DOT_RADIUS} ${y}a$DOT_RADIUS $DOT_RADIUS 0 1 1 ${2 * DOT_RADIUS} 0a$DOT_RADIUS $DOT_RADIUS 0 1 1 -${2 * DOT_RADIUS} 0z"
    }

    private fun ImageVector.Builder.stroked(path: String): ImageVector.Builder = addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(HoldoutColors.TextPrimary),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

    private fun ImageVector.Builder.filled(path: String, alpha: Float = 1f): ImageVector.Builder = addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(HoldoutColors.TextPrimary),
        fillAlpha = alpha,
    )

    private const val DOT_RADIUS = 2f
    private const val QUIET_DOT_ALPHA = 0.35f
}

private val PILL_WIDTH = 64.dp
private val PILL_HEIGHT = 40.dp
private val ICON_SIZE = 24.dp
private val DOT_SIZE = 8.dp
private val DOT_INSET_TOP = 5.dp
private val DOT_INSET_END = 14.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BottomNavPreview() {
    HoldoutTheme {
        BottomNav(
            items = listOf(
                BottomNavItem("Today", NavIcons.Today, dot = true, accessibleName = "Today, today's reps are not done"),
                BottomNavItem("Holdouts", NavIcons.Holdouts),
                BottomNavItem("Me", NavIcons.Me),
            ),
            selected = 0,
            onSelect = {},
        )
    }
}
