package holdout.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.chain.Deployment
import holdout.app.holdout.CheckAgainCopy
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.repcounter.Stakes
import java.math.BigDecimal
import java.math.RoundingMode

/** Amounts, times and the shared pieces every Holdout screen is built from. */
object Format {
    /** The mock SKR mint's decimals — `SKR_DECIMALS` in `initialize` (decision H31). */
    const val SKR_DECIMALS = 6

    /** "Waiting for your wallet…": every button that is out with the wallet says so, in these words (ruling G7). */
    const val WAITING_FOR_WALLET = "Waiting for your wallet…"

    /** The level a form is read as: **Spark** (knees allowed) or **Habit** (toes only). The code's Easy/Hard stay internal (H35). */
    fun levelName(form: Int): String = when (form) {
        holdout.core.LeagueParams.FORM_KNEES_ALLOWED -> "Spark"
        holdout.core.LeagueParams.FORM_TOES -> "Habit"
        else -> holdout.core.LeagueParams.formName(form)
    }

    /**
     * "Spark · 7-day Holdout", "Habit · 21-day Holdout": a Holdout's level is
     * its form, and this is the one mention of it on a screen (design book, Naming).
     */
    fun holdoutTitle(c: holdout.core.CohortSnapshot): String = "${level(c)} Holdout"

    /**
     * "Spark · 7-day", "Habit · 21-day": the level and the length. Member copy
     * names a league's reserves by these, never by the word "league" (FR-041).
     */
    fun level(c: holdout.core.CohortSnapshot): String = "${levelName(c.form)} · ${c.durationDays}-day"

    /** "Light it up · knees allowed" / "Make it stick · toes only": the level's tagline, or null for a form this build does not know. */
    fun levelTagline(form: Int): String? = when (form) {
        holdout.core.LeagueParams.FORM_KNEES_ALLOWED -> "Light it up · knees allowed"
        holdout.core.LeagueParams.FORM_TOES -> "Make it stick · toes only"
        else -> null
    }

    fun levelTagline(c: holdout.core.CohortSnapshot): String? = levelTagline(c.form)

    /**
     * The level's rule sentence, read the same everywhere it appears (design
     * book, Levels; ruling G2). A Spark screen never shows the Habit sentence:
     * the Holdout's own form picks it. Null for a form this build does not know.
     * The words live once, in [Stakes].
     */
    fun levelRule(form: Int): String? = when (Stakes.ofForm(form)) {
        Stakes.EASY -> Stakes.EASY_RULE
        Stakes.STAKED -> Stakes.STAKED_RULE
        else -> null
    }

    fun levelRule(c: holdout.core.CohortSnapshot): String? = levelRule(c.form)

    /** `SPARK · 7-DAY · DAY 3 OF 7`: a running Holdout's header label (no calendar dates, constitution VI; ruling G3). */
    fun headerLabel(c: holdout.core.CohortSnapshot, day: Int): String = "${level(c)} · day $day of ${c.durationDays}".uppercase()

    /** `SPARK · 7-DAY · STARTS IN 2D 4H`: a Holdout in its lobby. */
    fun lobbyHeaderLabel(c: holdout.core.CohortSnapshot, secondsToStart: Long): String = "${level(c)} · starts in ${countdown(secondsToStart)}".uppercase()

    /** `SPARK · 7-DAY · FINISHED`: a Holdout that has run its length. */
    fun finishedHeaderLabel(c: holdout.core.CohortSnapshot): String = "${level(c)} · finished".uppercase()

    /**
     * Where a lost stake goes, in the sentence the stake sheet shows (FR-027,
     * FR-037, FR-041; decided 2026-10-10, shortened 2026-10-11; ruling G5).
     * The level and length are the Holdout's own, and a no-break space keeps
     * every "%" on its number's line.
     */
    fun lostStakeSplit(c: holdout.core.CohortSnapshot): String = "Miss a day and you lose your stake: 70\u00a0% to the prize pool, 10\u00a0% to ${level(c)} boosts and comebacks, " +
        "20\u00a0% to the project. If nobody finishes, the pool goes to the project too."

    /** The same split on the forfeit screen, under its **Stake lost** row (ruling G5). */
    fun lostStakeSplitForfeit(c: holdout.core.CohortSnapshot): String = "It goes 70\u00a0% to this Holdout's prize pool, 10\u00a0% to ${level(c)} boosts and comebacks, " +
        "20\u00a0% to the project. If nobody finishes, the pool goes to the project too."

    /** "Prize boost: up to 480 SKR", on an offer: "up to", never a promise (ruling G6). The card already names the level. */
    fun boostLine(amount: ULong): String = "Prize boost: up to ${skr(amount)}"

    /** "+ about 1,200 SKR boost if anyone finishes", on a running Holdout (design book, Real copy by moment). */
    fun runningBoostLine(amount: ULong): String = "+ about ${skr(amount)} boost if anyone finishes"

    /** On Today while a lost stake's 14-day window is open: "Get back up to 250 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h." */
    fun comebackToday(amount: ULong, c: holdout.core.CohortSnapshot, startsInSeconds: Long): String = "Get back up to ${skr(amount)} of your lost stake: finish the ${level(c)} starting in ${countdown(startsInSeconds)}."

    /** On the forfeit screen: "Finish a Habit · 21-day starting within 14 days: up to 250 SKR comes back." */
    fun comebackForfeit(amount: ULong, c: holdout.core.CohortSnapshot): String = "Finish a ${level(c)} starting within 14 days: up to ${skr(amount)} comes back."

    /** The claim's **Comeback** row note, under the amount. */
    const val COMEBACK_CLAIM_NOTE = "part of your last lost stake, back"

    /** Most decimals a member ever reads in an amount; the chain keeps all [SKR_DECIMALS]. */
    private const val SHOWN_DECIMALS = 2

    /**
     * `46 SKR`, `12.5 SKR`, `1,560 SKR`: every amount a member stakes or receives, in SKR (FR-059).
     * Thousands grouped, at most two decimals, rounded DOWN (never overstate what a member gets),
     * trailing zeros dropped. Display only: the exact base units go to the chain unchanged.
     */
    fun skr(baseUnits: ULong): String {
        val plain = BigDecimal(baseUnits.toString()).movePointLeft(SKR_DECIMALS)
            .setScale(SHOWN_DECIMALS, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return (if (fraction.isEmpty()) grouped else "$grouped.$fraction") + " SKR"
    }

    /** The fiat label a chip carries — always approximate, never on-chain (FR-059, constitution XIII). */
    val TIER_USD = listOf(10, 25, 50, 100)

    fun tierUsd(tier: Int): String = "≈ $${TIER_USD.getOrElse(tier) { 0 }}"

    /**
     * "opens for joining in 1d 2h", or "opens for joining shortly" once [opensAt]
     * has passed and automation has not opened the Holdout yet — never a
     * countdown stuck at zero (FR-037g).
     */
    fun opensForJoining(opensAt: Long, now: Long): String = if (opensAt > now) "opens for joining in ${countdown(opensAt - now)}" else "opens for joining shortly"

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
    val shape = MaterialTheme.shapes.medium
    Column(
        // Clipped outside the caller's modifier, so a card made clickable ripples inside its corners.
        modifier = Modifier
            .clip(shape)
            .then(modifier)
            .fillMaxWidth()
            .background(HoldoutColors.Surface1, shape)
            .border(BorderStroke(1.dp, HoldoutColors.Hairline), shape)
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
 * "Check again" while the chain can't be reached (spec 005,
 * contracts/check-again-ui.md): a secondary button, busy while the check is
 * out, and "Devnet still can't be reached." under it, in the warning colour of
 * the line it answers, once a check found it so. Shown only while the last
 * chain read failed; the caller decides that.
 */
@Composable
fun CheckAgain(checking: Boolean, stillUnreachable: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier, network: String = Deployment.networkName) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.tight), horizontalAlignment = Alignment.CenterHorizontally) {
        SecondaryButton(if (checking) CheckAgainCopy.checking(network) else CheckAgainCopy.BUTTON, onCheck, busy = checking)
        if (stillUnreachable && !checking) Notice(CheckAgainCopy.stillUnreachable(network), warning = true)
    }
}

/**
 * A screen that says nothing else about the chain — the home showing an
 * earlier read, a Holdout's details, the forfeit — says it can't be reached in
 * one line above [CheckAgain] (spec 005 FR-002). The line itself answers a
 * failed check ("…still can't be reached."), so nothing moves on the screen.
 */
@Composable
fun ChainUnreachable(checking: Boolean, stillUnreachable: Boolean, onCheck: () -> Unit, network: String = Deployment.networkName) {
    Notice(if (stillUnreachable) CheckAgainCopy.stillUnreachable(network) else CheckAgainCopy.unreachable(network), warning = true)
    CheckAgain(checking, stillUnreachable = false, onCheck = onCheck, network = network)
}

/**
 * Pull down to check the chain again (spec 005 FR-001): only while [offline]
 * — the last chain read failed. While the chain answers the pull does nothing
 * and shows nothing; the app's own 30-second reads keep the screen fresh.
 * [content] scrolls as before; its overscroll drives the pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainCheckBox(offline: Boolean, checking: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    // The pull is a touch gesture only: TalkBack and switch users get the same check as an action.
    val actions = if (offline) listOf(CustomAccessibilityAction(CheckAgainCopy.BUTTON) { onCheck().let { true } }) else emptyList()
    Box(modifier.pullToRefresh(isRefreshing = checking, state = state, enabled = offline, onRefresh = onCheck).semantics { customActions = actions }) {
        content()
        if (offline) {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = checking,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = HoldoutColors.Surface2,
                color = HoldoutColors.Accent,
            )
        }
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
