package holdout.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * One theme, dark, fixed (PRD §11, T035).
 *
 * **No light variant and no dynamic colour.** Material You would repaint the
 * app from the member's wallpaper, and the palette here is the product's
 * identity — the survivor counter reading Solana Green is a decision, not a
 * default. There is deliberately no `dynamicColor` parameter to pass `true` to.
 *
 * The mapping below is PRD §11.1's table, slot for slot, so the theme is a
 * transcription rather than a reinterpretation.
 */
private val HoldoutColorScheme = darkColorScheme(
    background = HoldoutColors.Canvas,
    onBackground = HoldoutColors.TextPrimary,

    surface = HoldoutColors.Surface1,
    onSurface = HoldoutColors.TextPrimary,
    surfaceContainer = HoldoutColors.Surface2,
    surfaceContainerHigh = HoldoutColors.Surface3,
    onSurfaceVariant = HoldoutColors.TextSecondary,

    primary = HoldoutColors.Accent,
    onPrimary = HoldoutColors.OnAccent,
    secondary = HoldoutColors.Success,
    onSecondary = HoldoutColors.OnSuccess,
    tertiary = HoldoutColors.Celebration,

    error = HoldoutColors.Warning,
    onError = HoldoutColors.OnWarning,

    outline = HoldoutColors.Hairline,

    // The primary button is a white pill with a black label.
    inverseSurface = HoldoutColors.TextPrimary,
    inverseOnSurface = HoldoutColors.OnLight,
)

@Composable
fun HoldoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HoldoutColorScheme,
        typography = HoldoutTypography,
        shapes = HoldoutShapes,
        content = content,
    )
}
