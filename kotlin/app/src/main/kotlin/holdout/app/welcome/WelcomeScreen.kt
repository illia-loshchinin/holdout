package holdout.app.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.tour.DotGrid
import holdout.app.tour.DotSpec
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SkipAction
import holdout.app.ui.Wordmark
import holdout.app.ui.theme.DisplayStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutFonts
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/**
 * The welcome (board Welcome; FR-067a, R25): what a member sees between connecting a wallet for the first
 * time and the tour. First run only — never when the tour is opened from About or the stake sheet.
 *
 * ```
 *  HOLDOUT                                       Skip       Wordmark · SkipAction → the disclosure card
 *
 *  ⠿ the Holdout H                                           72 dp dot grid, the H in Accent
 *  Welcome,                                                  titleMedium, text-secondary
 *  nova.skr                                                  display, mono, Accent (one line, ellipsis)
 *  ● 7xQp…3fA9 · connected                                   Success dot, address mono
 *  First, a quick brief.                                     titleMedium
 *  The Daily 25, the two kinds of Holdout …                  Body
 *  Six cards, about a minute.
 *  [ Start the brief ]                                       PrimaryButton → card 1
 *  TEST NETWORK · TEST TOKEN
 * ```
 *
 * With no Seeker ID ([seekerId] null) the greeting is "Welcome to Holdout." over "You're in.". The middle
 * is centred in the space between the header and the button and scrolls only if the phone is too small for
 * it; on a Seeker (400 × 829 dp) it does not (`TourFitTest`). Stateless: the host passes the wallet's name
 * from the identity already read for Me.
 */
@Composable
fun WelcomeScreen(
    wallet: String,
    seekerId: String?,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(horizontal = Spacing.screenGutter)
            .padding(top = Spacing.tight, bottom = Spacing.betweenSections),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Wordmark()
            SkipAction(onSkip)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.heightIn(min = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections + Spacing.tight, Alignment.CenterVertically),
            ) {
                HoldoutMark()
                Greeting(wallet, seekerId)
                Brief()
            }
        }
        PrimaryButton("Start the brief", onStart)
        Text(
            "Test network · test token".uppercase(),
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.betweenItems),
        )
    }
}

/** "Welcome," over the Seeker ID — or "Welcome to Holdout." over "You're in." — and the address with "connected". */
@Composable
private fun Greeting(wallet: String, seekerId: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems)) {
        Text(
            if (seekerId != null) "Welcome," else "Welcome to Holdout.",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = HoldoutColors.TextSecondary,
        )
        if (seekerId != null) {
            // The `.skr` name in the mono face and the accent colour: the one coloured thing here besides the mark.
            Text(
                seekerId,
                style = DisplayStyle.copy(fontFamily = HoldoutFonts.Mono, fontWeight = FontWeight.Normal, textAlign = TextAlign.Start),
                color = HoldoutColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("You're in.", style = DisplayStyle.copy(textAlign = TextAlign.Start), color = HoldoutColors.TextPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Box(Modifier.size(CONNECTED_DOT).background(HoldoutColors.Success, CircleShape))
            Text(Format.short(wallet), style = LabelStyle.copy(fontFamily = HoldoutFonts.Mono, fontWeight = FontWeight.Normal), color = HoldoutColors.TextTertiary)
            Text("· connected", style = LabelStyle.copy(fontWeight = FontWeight.Normal), color = HoldoutColors.TextTertiary)
        }
    }
}

@Composable
private fun Brief() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Text("First, a quick brief.", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
        Body("The Daily 25, the two kinds of Holdout and their rules, where a lost stake goes, and how reps are counted.\nSix cards, about a minute.")
    }
}

/** The Holdout H on the five-by-five grid (board Welcome): the other dots at 24 %, the H in Accent. Decorative, 72 dp. */
@Composable
private fun HoldoutMark() {
    val unit = MARK_SIZE.value / MARK_UNITS
    val dots = (0 until GRID).flatMap { r ->
        (0 until GRID).map { c ->
            val colour = if (r to c in H) HoldoutColors.Accent else HoldoutColors.TextPrimary.copy(alpha = MARK_FADE)
            DotSpec((MARK_INSET + MARK_PITCH * c) * unit, (MARK_INSET + MARK_PITCH * r) * unit, colour)
        }
    }
    Box(Modifier.semantics { contentDescription = "Holdout" }) { DotGrid(MARK_SIZE, MARK_SIZE, MARK_RADIUS * unit, dots) }
}

/** The Holdout H, (row, column), on a 5 × 5 grid: the same seven dots as the worked example's finishers. */
private val H = setOf(1 to 1, 2 to 1, 3 to 1, 2 to 2, 1 to 3, 2 to 3, 3 to 3)

private val MARK_SIZE = 72.dp
private val CONNECTED_DOT = 6.dp
private const val MARK_UNITS = 52f
private const val MARK_INSET = 4f
private const val MARK_PITCH = 11f
private const val MARK_RADIUS = 3.4f
private const val MARK_FADE = 0.24f
private const val GRID = 5

private const val PREVIEW_WALLET = "7xQpAbCdEfGhJkLmNpQrStUvWxYz1234567893fA9"

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829, widthDp = 400)
@Composable
private fun WelcomeNamedPreview() {
    HoldoutTheme { WelcomeScreen(wallet = PREVIEW_WALLET, seekerId = "nova.skr", onStart = {}, onSkip = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 829, widthDp = 400)
@Composable
private fun WelcomeUnnamedPreview() {
    HoldoutTheme { WelcomeScreen(wallet = PREVIEW_WALLET, seekerId = null, onStart = {}, onSkip = {}) }
}
