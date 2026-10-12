package holdout.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import holdout.app.R

/**
 * Type, transcribed from the design book: the `type` tokens in
 * `docs/design/tokens.json`, described under Visual foundations → Type in
 * `docs/design/README.md`.
 *
 * **Roboto** is the Android system family, so the app matches the Seeker's own
 * UI with nothing bundled. The two faces the design book adds are bundled in
 * `res/font/` under the SIL Open Font License (texts in `assets/licenses/`):
 * **JetBrains Mono** for amounts, countdowns and addresses, and **Roboto Flex**
 * at weight 300 for the counter and display numbers, its optical size set to
 * the style's own size as a browser's `font-optical-sizing: auto` does.
 *
 * Tabular figures everywhere a number changes, so counters and timers never
 * jitter as digits swap.
 */
@OptIn(ExperimentalTextApi::class)
object HoldoutFonts {
    /** SKR amounts, countdowns, wallet addresses: JetBrains Mono 400 and 500. */
    val Mono: FontFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    )

    /** Roboto Flex at weight 300, its optical size [opsz] in sp. */
    private fun flex(opsz: Float): FontFamily = FontFamily(
        Font(
            R.font.roboto_flex,
            FontWeight.Light,
            variationSettings = FontVariation.Settings(FontVariation.weight(300), FontVariation.Setting("opsz", opsz)),
        ),
    )

    /** The survivor counter and the rep count (56 sp). */
    val FlexCounter: FontFamily = flex(56f)

    /** Claim amount, streak, the lobby's countdown (40 sp). */
    val FlexDisplay: FontFamily = flex(40f)
}

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The survivor counter and the rep count — the largest thing on any screen. */
val CounterStyle = TextStyle(
    fontFamily = HoldoutFonts.FlexCounter,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    letterSpacing = (-0.01).em,
    fontFeatureSettings = Tabular.fontFeatureSettings,
    textAlign = TextAlign.Center,
)

/** Claim amount, streak. */
val DisplayStyle = CounterStyle.copy(fontFamily = HoldoutFonts.FlexDisplay, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.em)

/** SKR amounts, countdowns, wallet addresses. */
val AmountStyle = TextStyle(
    fontFamily = HoldoutFonts.Mono,
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
