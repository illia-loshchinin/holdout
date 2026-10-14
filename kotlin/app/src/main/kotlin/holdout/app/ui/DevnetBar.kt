package holdout.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import holdout.app.holdout.CheckAgainCopy
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing

/**
 * The one devnet status bar (001 FR-069h, research R25), directly under a screen's header wherever the
 * chain cannot be reached: a calm card, not a red box. The figures on the screen stay where they are.
 *
 * ```
 *  ┌ 1 dp soft-warm ring at 28 % ──────────────────────┐
 *  │ ●  Devnet isn't answering                  Retry  │   dot 8 dp, soft warm
 *  │    The Daily 25 still works.                      │   sub line, text-tertiary
 *  └───────────────────────────────────────────────────┘
 * ```
 *
 * [still]: a retry found the chain still silent ("Devnet still isn't answering"). While [checking] the
 * action reads "Checking…" and cannot be pressed. The words are a polite live region, so a screen reader
 * hears the change without it being thrown at them; the dot is `soft-warm`, never `warning`, and no text
 * is red. [sub] is one short line for the screen it sits on.
 */
@Composable
fun DevnetBar(
    network: String,
    still: Boolean,
    checking: Boolean,
    sub: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BAR_MIN_HEIGHT)
            .clip(shape)
            .background(HoldoutColors.Surface1, shape)
            .border(BorderStroke(Spacing.border, HoldoutColors.SoftWarm.copy(alpha = RING_ALPHA)), shape)
            .padding(start = Spacing.cardPadding - Spacing.base, end = Spacing.tight, top = Spacing.tight, bottom = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Dot()
        Column(
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            Text(
                text = if (still) CheckAgainCopy.stillUnreachable(network) else CheckAgainCopy.unreachable(network),
                style = BAR_TITLE,
                color = HoldoutColors.TextPrimary,
            )
            Text(text = sub, style = BAR_SUB, color = HoldoutColors.TextTertiary)
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = Spacing.touchTarget)
                .height(Spacing.touchTarget)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(enabled = !checking, role = Role.Button, onClick = onRetry)
                .padding(horizontal = BAR_ACTION_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checking) CheckAgainCopy.CHECKING else CheckAgainCopy.BUTTON,
                style = BAR_TITLE.copy(fontWeight = FontWeight.Medium),
                color = HoldoutColors.Accent,
            )
        }
    }
}

/** The bar's sizes on the boards: 56 dp high, the title 14 sp on an 18 sp line, the sub 12 sp, the action a transparent pill (48 dp to touch, the board draws 40). */
private val BAR_MIN_HEIGHT = 56.dp
private val BAR_ACTION_PADDING = 14.dp
private val BAR_TITLE = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp)
private val BAR_SUB = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

/** The sub line of the bar on a screen that shows what devnet last said (Holdouts, a Holdout's screen, the forfeit). */
object DevnetBarCopy {
    /** "Showing what it said 4m ago." — [ageSeconds] of the last read that answered; null when no read time is on record. */
    fun showing(ageSeconds: Long?): String = "Showing what it said ${ageSeconds?.let { "${Format.ago(it)} ago" } ?: "earlier"}."
}

@Composable
private fun Dot() {
    Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(HoldoutColors.SoftWarm))
}

/** The ring is the dot's colour at 28 %: a hint of warmth, not a border that shouts. */
private const val RING_ALPHA = 0.28f
private val DOT_SIZE = 8.dp

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DevnetBarPreview() {
    HoldoutTheme {
        Column(Modifier.padding(Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
            DevnetBar("Devnet", still = false, checking = false, sub = "The Daily 25 still works.", onRetry = {})
            DevnetBar("Devnet", still = true, checking = false, sub = "Showing what it said 4m ago.", onRetry = {})
            DevnetBar("Devnet", still = true, checking = true, sub = "Your reps still count on this phone.", onRetry = {})
            DevnetBar("The local validator", still = false, checking = false, sub = "The Daily 25 still works.", onRetry = {})
        }
    }
}
