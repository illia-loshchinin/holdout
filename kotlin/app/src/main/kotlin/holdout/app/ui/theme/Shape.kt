package holdout.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape and spacing, from PRD §11.3.
 *
 * **Depth: none.** No shadows anywhere on black — a surface level plus a
 * hairline does all the separating.
 */
val HoldoutShapes = Shapes(
    // Chips.
    small = RoundedCornerShape(16.dp),
    // Cards and list rows.
    medium = RoundedCornerShape(24.dp),
    // Sheets, on the top corners.
    large = RoundedCornerShape(28.dp),
    // Buttons and the nav pill.
    extraLarge = RoundedCornerShape(percent = 50),
)

/** The 4dp base grid, named so screens do not invent their own numbers. */
object Spacing {
    val screenGutter = 20.dp
    val cardPadding = 20.dp
    val betweenSections = 24.dp
    val betweenItems = 12.dp
    val tight = 8.dp

    /** Minimum touch target; the primary button is taller still. */
    val touchTarget = 48.dp
    val primaryButtonHeight = 56.dp
}
