package holdout.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type, transcribed from the design book: the `type` tokens in
 * `docs/design/tokens.json`, described under Visual foundations → Type in
 * `docs/design/README.md`.
 *
 * **Roboto** is the Android system family, so the app matches the Seeker's own
 * UI with nothing bundled and no licence to carry. The design book also names
 * **JetBrains Mono** for amounts and countdowns; until that file is added to
 * `res/font/`, those styles fall back to [FontFamily.Monospace], which the
 * system provides. That is a visual difference on the amount row only, and no
 * layout depends on it.
 *
 * Tabular figures everywhere a number changes, so counters and timers never
 * jitter as digits swap.
 */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The survivor counter and the rep count — the largest thing on any screen. */
val CounterStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    letterSpacing = (-0.01).em,
    fontFeatureSettings = Tabular.fontFeatureSettings,
    textAlign = TextAlign.Center,
)

/** Claim amount, streak. */
val DisplayStyle = CounterStyle.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.em)

/** SKR amounts, countdowns, wallet addresses. */
val AmountStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = Tabular.fontFeatureSettings,
)

/** Captions under numbers, nav labels, chips. Uppercase is applied at the call site. */
val LabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.02.em,
)

val HoldoutTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelMedium = LabelStyle,
)
