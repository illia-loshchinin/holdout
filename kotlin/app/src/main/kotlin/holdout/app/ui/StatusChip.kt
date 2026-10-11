package holdout.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/** The colour a [StatusChip]'s word takes. Never the only signal: the chip is always a word. */
enum class ChipTone {
    /** `text-secondary`: the network, a plain state. */
    Neutral,

    /** `accent`: open, free, LAB. */
    Accent,

    /** `success`: running, settled, standing. */
    Success,

    /** `warning`: TEST COHORT, out, forfeited. */
    Warning,
    ;

    internal val color: Color
        get() = when (this) {
            Neutral -> HoldoutColors.TextSecondary
            Accent -> HoldoutColors.Accent
            Success -> HoldoutColors.Success
            Warning -> HoldoutColors.Warning
        }
}

/**
 * A small uppercase word that names a state beside a number or a title
 * (design book, Status chip): a 24 dp pill on `surface-3`, `radius-chip`, the
 * word in [tone]. The word is the meaning, so the accessible name says it
 * ("Status: running") instead of leaving it to a colour. "Scripted" always
 * carries the diamond: `◇ SCRIPTED`.
 *
 * One chip per card at most; never a count in a chip.
 */
@Composable
fun StatusChip(word: String, modifier: Modifier = Modifier, tone: ChipTone = ChipTone.Neutral) {
    val scripted = word.equals(SCRIPTED, ignoreCase = true)
    val shown = if (scripted) "$DIAMOND ${word.uppercase()}" else word.uppercase()
    val name = if (scripted) "Scripted member" else "Status: ${word.lowercase()}"
    Row(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .background(HoldoutColors.Surface3, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.tight)
            .semantics(mergeDescendants = true) { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shown, style = LabelStyle, color = tone.color, maxLines = 1)
    }
}

/** The label on every scripted member, on every roster: `◇ SCRIPTED`. */
@Composable
fun ScriptedChip(modifier: Modifier = Modifier) = StatusChip(SCRIPTED, modifier)

private val CHIP_HEIGHT = 24.dp
private const val DIAMOND = "◇"
private const val SCRIPTED = "scripted"

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun StatusChipPreview() {
    HoldoutTheme {
        Box(Modifier.padding(Spacing.screenGutter)) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.tight)) {
                StatusChip("devnet")
                StatusChip("open", tone = ChipTone.Accent)
                StatusChip("running", tone = ChipTone.Success)
                StatusChip("forfeited", tone = ChipTone.Warning)
                ScriptedChip()
            }
        }
    }
}
