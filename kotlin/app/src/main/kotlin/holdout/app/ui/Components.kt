package holdout.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import java.math.BigDecimal

/** Amounts, times and the shared pieces every Holdout screen is built from. */
object Format {
    /** The mock SKR mint's decimals — `SKR_DECIMALS` in `initialize` (decision H31). */
    const val SKR_DECIMALS = 6

    /** "Easy · 7-day Holdout", "Hard · 21-day Holdout": a Holdout's level is its form (H35). */
    fun holdoutTitle(c: holdout.core.CohortSnapshot): String = "${level(c)} Holdout"

    /**
     * "Easy · 7-day", "Hard · 21-day": the level and the length. Member copy
     * names a league's reserves by these, never by the word "league" (FR-041).
     */
    fun level(c: holdout.core.CohortSnapshot): String = "${holdout.core.LeagueParams.formName(c.form)} · ${c.durationDays}-day"

    /**
     * Where a lost stake goes, in the sentence the stake sheet and the forfeit
     * screen share (FR-027, FR-037, FR-041; decided 2026-10-10). [pool] names
     * the prize pool as that screen sees it. A no-break space keeps every "%"
     * on its number's line.
     */
    fun lostStakeSplit(c: holdout.core.CohortSnapshot, pool: String): String = "80 % of it stays with members: 70 % to $pool and 10 % to the boost and comeback reserves of ${level(c)} Holdouts. " +
        "20 % goes to the project, and so does the prize pool if nobody finishes."

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

    /**
     * A member as the app names them (spec 003): their Seeker ID cut to
     * [budget] characters, or their shortened address when they have none.
     */
    fun identity(wallet: String, name: String?, budget: Int): String = name?.let { holdout.core.SeekerNames.fit(it, budget) } ?: short(wallet)

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

/** A hairline card — the only separation on black; there are no shadows (design book, Depth). */
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

/** [busy]: the action is waiting on the wallet or devnet. The button is disabled and leads with the [HoldoutLoader]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().height(Spacing.primaryButtonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = HoldoutColors.TextPrimary, contentColor = HoldoutColors.OnLight),
    ) {
        if (busy) BusyMark()
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().height(Spacing.primaryButtonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, HoldoutColors.Hairline),
    ) {
        if (busy) BusyMark()
        Text(text, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
    }
}

@Composable
private fun BusyMark() {
    HoldoutLoader(contentDescription = null)
    Spacer(Modifier.width(Spacing.tight))
}

/** A read from devnet with nothing to show yet: the [HoldoutLoader] beside what is being read. */
@Composable
fun Reading(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BusyMark()
        content()
    }
}

/**
 * The short test cohort's label (FR-044a), wherever it appears: the offers
 * on home, the stake sheet, the lobby, the roster and cohort detail.
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

/**
 * Every kept Seeker ID, wallet base58 → name (spec 003), provided once by the
 * app from `SeekerNameRepository.names`. Empty in previews and tests, where
 * every member shows their shortened address.
 */
val LocalSeekerNames = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * One member, the way every list and line names them (spec 003 FR-001–FR-004,
 * research R8): their Seeker ID when they have one, else their shortened
 * address, in the same [style] and [color] either way, so the two look like
 * the same kind of row — no badge, no "verified" mark (FR-003). "· you"
 * follows when it is the connected wallet, decided by the wallet and never
 * by the name (FR-008).
 *
 * The address shows at once and the name crossfades in over 200 ms when it
 * arrives (FR-006). The line's width is fixed before any name is known —
 * [budget] characters, or the space it is given — and whatever it shows is
 * drawn inside that width, so a name arriving never moves what is beside it
 * (US1-3). A name wider than that (wide glyphs, such as CJK) ends in "…". In
 * a row, give it the space beside the other text with `Modifier.weight(1f)`.
 */
@Composable
fun MemberIdentity(
    wallet: String,
    isYou: Boolean,
    budget: Int,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountStyle,
) {
    val you = if (isYou) " · you" else ""
    val shown = Format.identity(wallet, LocalSeekerNames.current[wallet], budget) + you
    Layout(
        content = {
            // Measured for its width only, never drawn.
            Text("0".repeat(budget) + you, style = style, maxLines = 1, softWrap = false)
            Crossfade(targetState = shown, animationSpec = tween(durationMillis = 200), label = "member identity") {
                Text(it, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = shown },
    ) { (reserve, identity), constraints ->
        val width = reserve.measure(constraints).width
        val line = identity.measure(constraints.copy(minWidth = 0, maxWidth = width))
        layout(width, line.height) { line.place(0, 0) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MemberIdentityNamedPreview() {
    HoldoutTheme {
        CompositionLocalProvider(LocalSeekerNames provides mapOf(PREVIEW_WALLET to "alice.skr")) {
            MemberIdentity(PREVIEW_WALLET, isYou = true, budget = 20, color = HoldoutColors.TextPrimary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MemberIdentityAddressPreview() {
    HoldoutTheme {
        MemberIdentity(PREVIEW_WALLET, isYou = false, budget = 20, color = HoldoutColors.TextPrimary)
    }
}

private const val PREVIEW_WALLET = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
