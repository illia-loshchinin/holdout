package holdout.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import java.math.BigDecimal

/** Amounts, times and the shared pieces every Holdout screen is built from. */
object Format {
    /** The mock SKR mint's decimals — `SKR_DECIMALS` in `initialize` (decision H31). */
    const val SKR_DECIMALS = 6

    /** "Easy · 7-day Holdout", "Hard · 21-day Holdout": a Holdout's level is its form (H35). */
    fun holdoutTitle(c: holdout.core.CohortSnapshot): String = "${holdout.core.LeagueParams.formName(c.form)} · ${c.durationDays}-day Holdout"

    /** `46 SKR`, `12.5 SKR`: every amount a member stakes or receives, in SKR (FR-059). */
    fun skr(baseUnits: ULong): String = BigDecimal(baseUnits.toString()).movePointLeft(SKR_DECIMALS).stripTrailingZeros().toPlainString() + " SKR"

    /** The fiat label a chip carries — always approximate, never on-chain (FR-059, constitution XIII). */
    val TIER_USD = listOf(10, 25, 50, 100)

    fun tierUsd(tier: Int): String = "≈ $${TIER_USD.getOrElse(tier) { 0 }}"

    /** `3d 4h`, `4h 06m`, `12m 30s`: a countdown, never a date (constitution VI). */
    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val days = s / 86_400
        val hours = (s % 86_400) / 3_600
        val minutes = (s % 3_600) / 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "%dh %02dm".format(hours, minutes)
            else -> "%dm %02ds".format(minutes, s % 60)
        }
    }

    /** `ABcd…wxYZ`: a wallet as the roster shows it (FR-062). */
    fun short(address: String): String = if (address.length <= 10) address else address.take(4) + "…" + address.takeLast(4)

    /** A day length, for the short test cohort's label (FR-044a). */
    fun dayLength(seconds: Long): String = when {
        seconds % 86_400 == 0L -> "${seconds / 86_400}-day"
        seconds % 3_600 == 0L -> "${seconds / 3_600}-hour"
        seconds % 60 == 0L -> "${seconds / 60}-minute"
        else -> "$seconds-second"
    }

    /** A devnet transaction, for the member to check. */
    fun explorer(signature: String): String = "https://explorer.solana.com/tx/$signature?cluster=devnet"
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = LabelStyle, color = HoldoutColors.TextTertiary, modifier = modifier)
}

/** A hairline card — the only separation on black; there are no shadows (PRD §11). */
@Composable
fun HoldoutCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HoldoutColors.Surface1, MaterialTheme.shapes.large)
            .border(BorderStroke(1.dp, HoldoutColors.Hairline), MaterialTheme.shapes.large)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Spacing.primaryButtonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = HoldoutColors.TextPrimary, contentColor = HoldoutColors.OnLight),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Spacing.primaryButtonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, HoldoutColors.Hairline),
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary) }
}

/**
 * The short test cohort's label (FR-044a), wherever it appears: the ladder,
 * the stake sheet, the lobby, home, the roster and cohort detail.
 */
@Composable
fun TestCohortBadge(dayLengthSeconds: Long, modifier: Modifier = Modifier) {
    Text(
        text = "SHORT TEST COHORT · ${Format.dayLength(dayLengthSeconds).uppercase()} DAYS · DEVNET",
        style = LabelStyle,
        color = HoldoutColors.Warning,
        modifier = modifier,
    )
}

/** A line of body text in the secondary colour. */
@Composable
fun Body(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = HoldoutColors.TextSecondary,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

/** What the last action said — a signature, or why it did not land — with room to breathe. */
@Composable
fun Notice(text: String?, warning: Boolean = false, modifier: Modifier = Modifier) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (warning) HoldoutColors.Warning else HoldoutColors.TextSecondary,
        modifier = modifier.padding(vertical = Spacing.tight),
    )
}
