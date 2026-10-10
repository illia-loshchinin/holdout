package holdout.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens, transcribed from the design book's `color` tokens in
 * `docs/design/tokens.json` (Visual foundations → Colour in
 * `docs/design/README.md`) — not reinterpreted.
 *
 * Every value is sampled from the Solana brand kit, so nothing looks borrowed.
 * **Dark only**, and Material You dynamic colour is disabled in [HoldoutTheme]:
 * brand colours are fixed.
 */
object HoldoutColors {

    // Canvas and surfaces. Pure black like the Seeker's system UI, with
    // surfaces tinted very slightly teal so cards separate without a shadow.
    val Canvas = Color(0xFF000000)
    val Surface1 = Color(0xFF0B1214)
    val Surface2 = Color(0xFF121B1D)
    val Surface3 = Color(0xFF1A2426)

    /** 1dp borders — the only separation on black. There are no shadows anywhere. */
    val Hairline = Color(0x1AFFFFFF)

    /** Over the camera preview, so overlaid text stays legible. */
    val Scrim = Color(0x99000000)

    /** The sheet's drag handle: white at 24%. */
    val DragHandle = Color(0x3DFFFFFF)

    // Text.
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x7AFFFFFF)

    /** Tide Light. The interactive colour: selection, progress, the skeleton, countdowns. */
    val Accent = Color(0xFF80ECFF)

    /** Tide. Rare emphasis on an accent surface. */
    val AccentStrong = Color(0xFF00DAFF)

    /** [Accent] at 12%: the fill of a selected tier chip. */
    val AccentTint = Color(0x1F80ECFF)

    /** Solana Green. Still standing, day complete, claim, confirmed transactions. */
    val Success = Color(0xFF14F195)

    /** Coral. Forfeit, the last-10-minutes warning, faucet exhausted. Never a harsh red. */
    val Warning = Color(0xFFFF623A)

    /** Solana Purple. Decoration only, never text: purple that has to be read is [CelebrationText]. */
    val Celebration = Color(0xFF9945FF)

    /** Solana Purple lightened, same hue: the rare purple that has to be read, such as a PB label. */
    val CelebrationText = Color(0xFFB980FF)

    /** The label on the white pill button. */
    val OnLight = Color(0xFF000000)

    // The "on" colours the M3 mapping names for accent and state surfaces.
    val OnAccent = Color(0xFF002731)
    val OnSuccess = Color(0xFF00110A)
    val OnWarning = Color(0xFF1A0400)

    /** Reserved for two moments only: day complete, and claim success. */
    val GradientStart = Celebration
    val GradientEnd = Success
}
