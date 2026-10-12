package holdout.app.tx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import holdout.app.nav.NavEvent
import holdout.app.nav.TxKind
import holdout.app.storage.Store
import holdout.app.ui.Body
import holdout.app.ui.ChecklistRow
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.KeyValueRow
import holdout.app.ui.PrimaryButton
import holdout.app.ui.ProgressRing
import holdout.app.ui.QuietAction
import holdout.app.ui.ScreenHeader
import holdout.app.ui.SecondaryButton
import holdout.app.ui.StepState
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.core.HoldoutDay

/** What the signing flow shows for one signature (rulings G7, C-D14; boards 4b, 6b, 7b). */
data class SigningState(
    val kind: TxKind,
    val phase: TxPhase,
    /** "SPARK · 7-DAY · JOINING", "HABIT · 21-DAY · DAY 12", "SPARK · 7-DAY · CLAIMING". */
    val label: String,
    /** The SKR that moves: a join's stake, a claim's amount. */
    val amount: String? = null,
    /** A submit's time left to the day's deadline. */
    val timeLeft: String? = null,
    /** A claim's breakdown: "1,250 SKR stake back + 162 SKR from the pool." */
    val breakdown: String? = null,
    /** After a claim: the next level up, open to join — "Make it a Habit · 21-day" and the event that opens its stake sheet. */
    val upsell: Pair<String, NavEvent>? = null,
)

/**
 * Holdout's own signing flow, full screen with no bar (G7):
 *
 * ```
 *  SPARK · 7-DAY · JOINING                        ScreenHeader label
 *  Waiting:     (ring) Waiting for your wallet    ProgressRing · headline, no Cancel (MWA cannot be aborted)
 *               Approve or decline in your wallet. Nothing moves until you approve.
 *  Confirming:  (ring) Taking your place           ProgressRing · headline
 *               ✓ Approved in your wallet · ✓ Sent to devnet · ◌ Confirming · ○ In the lobby   ChecklistRow ×4
 *               Takes a few seconds. You can leave this screen.     Leave this screen (QuietAction)
 *  Result:      (mark) title · what is still safe · [primary] [secondary]
 *  TEST NETWORK · TEST TOKEN
 * ```
 *
 * Every button is a [NavEvent]; the shell sends the signature again on
 * [NavEvent.TxRetry].
 */
@Composable
fun SigningScreen(state: SigningState, onEvent: (NavEvent) -> Unit, modifier: Modifier = Modifier) {
    SigningColumn(state.label, modifier) {
        when (val phase = state.phase) {
            TxPhase.WaitingForWallet -> {
                Centre {
                    ProgressRing("Waiting for your wallet")
                    Headline(WAITING)
                    Body(
                        "Approve or decline in your wallet. Nothing moves until you approve." + (state.timeLeft?.let { " $it to the deadline." } ?: ""),
                        center = true,
                    )
                }
            }
            TxPhase.Confirming -> {
                Centre {
                    ProgressRing("Confirming")
                    Headline(confirmingTitle(state))
                }
                HoldoutCard {
                    ChecklistRow("Approved in your wallet", StepState.Done)
                    ChecklistRow("Sent to devnet", StepState.Done)
                    ChecklistRow(confirmingStep(state.kind), StepState.Active)
                    ChecklistRow(resultStep(state.kind), StepState.Pending)
                }
                Body("Takes a few seconds. You can leave this screen.", center = true, modifier = Modifier.fillMaxWidth())
                QuietAction("Leave this screen", { onEvent(NavEvent.TxBack) }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            is TxPhase.Done -> Result(state, phase.outcome, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.Result(state: SigningState, outcome: TxOutcome, onEvent: (NavEvent) -> Unit) {
    val safe = stillSafe(state)
    val back = { onEvent(NavEvent.TxBack) }
    val retry = { onEvent(NavEvent.TxRetry) }
    when (outcome) {
        is TxOutcome.Landed -> if (state.kind is TxKind.Claim) {
            Claimed(state, outcome.signature, onEvent)
        } else {
            Outcome(Mark.Done, landedTitle(state), null)
            PrimaryButton(if (state.kind is TxKind.Submit) "Back to today" else "Continue", back)
        }
        TxOutcome.Declined -> {
            Outcome(Mark.Declined, "You declined in your wallet", "Nothing was sent. $safe")
            PrimaryButton("Try again", retry)
            SecondaryButton("Back", back)
        }
        is TxOutcome.Failed -> if (outcome.mayStillLand) {
            // Never "your SKR is still yours" when it may land yet (G7).
            Outcome(Mark.Failed, "Not confirmed yet", "It was sent, but the network hasn't confirmed it. It may still land — check again in a minute.")
            PrimaryButton("Back", back)
        } else {
            Outcome(Mark.Failed, "It didn't go through", "The network didn't confirm it. $safe")
            PrimaryButton("Try again", retry)
            SecondaryButton("Back", back)
        }
        TxOutcome.JoiningClosed -> {
            Outcome(Mark.Clock, "Joining closed first", "Your transaction landed too late. Nothing was taken.")
            PrimaryButton("See open Holdouts", { onEvent(NavEvent.SeeOpenHoldouts) })
        }
        TxOutcome.DeadlinePassed -> {
            Outcome(Mark.Clock, "The deadline passed", "Your run in this Holdout has ended.")
            PrimaryButton("What happens now", { onEvent(NavEvent.WhatHappensNow) })
        }
        TxOutcome.NotEnoughSol -> {
            Outcome(Mark.Failed, "Not enough SOL", "Your wallet has too little devnet SOL to pay for this. The faucet sends some with the test tokens.")
            PrimaryButton("Get test tokens", { onEvent(NavEvent.OpenFaucet) })
            SecondaryButton("Back", back)
        }
        TxOutcome.NoWallet -> {
            Outcome(Mark.Failed, "No wallet on this phone", "Install a Solana wallet, then try again. Nothing was sent.")
            PrimaryButton("Back", back)
        }
        is TxOutcome.Refused -> {
            Outcome(Mark.Failed, "It didn't go through", outcome.message)
            PrimaryButton("Back", back)
        }
    }
}

/** A claim that landed: the amount in green, the gradient sweeping once, and where next (G7, board 7b). */
@Composable
private fun ColumnScope.Claimed(state: SigningState, signature: String?, onEvent: (NavEvent) -> Unit) {
    val uri = LocalUriHandler.current
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(GRADIENT_MILLIS)) }
    Centre {
        Text("CLAIMED", style = LabelStyle, color = HoldoutColors.Success)
        Text(state.amount ?: "", style = CounterStyle, color = HoldoutColors.Success, modifier = Modifier.semantics { heading() })
        Box(
            Modifier
                .fillMaxWidth(sweep.value)
                .height(Spacing.base)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.horizontalGradient(listOf(HoldoutColors.GradientStart, HoldoutColors.GradientEnd))),
        )
        state.breakdown?.let { Body(it, center = true) }
        signature?.let { QuietAction("${Format.short(it)} · view on explorer →", { runCatching { uri.openUri(Format.explorer(it)) } }) }
    }
    state.upsell?.let { (text, event) ->
        PrimaryButton(text, { onEvent(event) })
        SecondaryButton("Done", { onEvent(NavEvent.TxDone) })
    } ?: PrimaryButton("Done", { onEvent(NavEvent.TxDone) })
}

/** You're in (board 12), after a join landed (G7, B-D19). */
data class YoureInState(
    /** "Spark · 7-day". */
    val name: String,
    val startsIn: String,
    val members: Long,
    val atStake: String,
    val stake: String,
    val joiningClosesIn: String,
    /** Today's Daily 25 is done: its button gives way to the lobby's. */
    val daily25Done: Boolean,
    val test: Boolean = false,
    val testDayLength: Long = 0,
    /**
     * The Holdout's first day starts within today's Daily 25 day: the member does today's
     * push-ups in the Holdout itself (a Holdout day counts for the streak, FR-050), so the
     * Daily 25 is not offered here.
     */
    val startsToday: Boolean = false,
) {
    /** The Daily 25 button shows only when today's is not done and the Holdout starts on a later day. */
    val showDaily25: Boolean get() = !daily25Done && !startsToday

    companion object {
        /** True when [start] (chain seconds) falls in the Daily 25 day [dayIndex]; an unknown day is never "today". */
        fun startsToday(start: Long, dayIndex: Long): Boolean = dayIndex != Store.UNKNOWN_DAY && HoldoutDay.indexOf(start) == dayIndex
    }
}

/**
 * ```
 *  (✓)  You're in.                               success ring · headline
 *  Spark · 7-day. Everyone starts together in 2d 4h.
 *  ┌ Members now 15 · At stake now 13,750 SKR · Your stake 1,250 SKR ┐   KeyValueRow
 *  │ held by the contract, not by us · ── · Joining closes in 2d 3h  │
 *  └ Until then, leaving refunds it all. ┘
 *  [ Do today's Daily 25 ]  [ See the lobby ]
 * ```
 * The Daily 25 button shows only when the Holdout starts on a later day than today's Daily 25;
 * otherwise the lobby is the one primary button.
 * No dollar figures here: "≈ $" lives beside a tier chip only (G4).
 */
@Composable
fun YoureInScreen(state: YoureInState, onDaily25: () -> Unit, onEvent: (NavEvent) -> Unit, modifier: Modifier = Modifier) {
    SigningColumn("${state.name} · joined", modifier) {
        if (state.test) TestCohortBadge(state.testDayLength)
        Centre {
            ResultMark(Mark.Done)
            Headline("You're in.")
            Body("${state.name}. Everyone starts together in ${state.startsIn}.", center = true)
        }
        HoldoutCard {
            KeyValueRow("Members now", state.members.toString())
            KeyValueRow("At stake now", state.atStake)
            Caption("total staked")
            KeyValueRow("Your stake", state.stake)
            Caption("held by the contract, not by us")
            HorizontalDivider(thickness = Spacing.border, color = HoldoutColors.Hairline)
            KeyValueRow("Joining closes in", state.joiningClosesIn)
            Body("Until then, leaving refunds it all.")
        }
        if (state.showDaily25) {
            PrimaryButton("Do today's Daily 25", onDaily25)
            SecondaryButton("See the lobby", { onEvent(NavEvent.SeeLobby) })
        } else {
            PrimaryButton("See the lobby", { onEvent(NavEvent.SeeLobby) })
        }
    }
}

/** The small line under a row's value, on the value's side (board 12). */
@Composable
private fun ColumnScope.Caption(text: String) {
    Text(text, style = LabelStyle, color = HoldoutColors.TextTertiary, modifier = Modifier.align(Alignment.End))
}

@Composable
private fun SigningColumn(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections),
    ) {
        ScreenHeader(label)
        // The step sits in the middle of what is left of the screen (as the boards draw it), and scrolls when it is taller.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenSections, Alignment.CenterVertically),
            ) {
                content()
                Text("TEST NETWORK · TEST TOKEN", style = LabelStyle, color = HoldoutColors.TextTertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun Centre(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems), content = content)
}

@Composable
private fun Headline(text: String, color: Color = HoldoutColors.TextPrimary) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = color, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Outcome(mark: Mark, title: String, text: String?) {
    Centre {
        ResultMark(mark)
        Headline(title, if (mark == Mark.Done) HoldoutColors.Success else HoldoutColors.TextPrimary)
        text?.let { Body(it, center = true) }
    }
}

private enum class Mark { Done, Declined, Failed, Clock }

/** The result's mark in a 72 dp `surface-3` circle; its meaning is in the title beside it. */
@Composable
private fun ResultMark(mark: Mark) {
    val color = when (mark) {
        Mark.Done -> HoldoutColors.Success
        Mark.Declined -> HoldoutColors.TextSecondary
        Mark.Failed, Mark.Clock -> HoldoutColors.Warning
    }
    Box(Modifier.size(MARK_SIZE).clip(CircleShape).background(HoldoutColors.Surface3), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(GLYPH_SIZE)) {
            val w = size.width
            val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            when (mark) {
                Mark.Done -> drawPath(
                    Path().apply {
                        moveTo(w * 0.2f, w * 0.52f)
                        lineTo(w * 0.42f, w * 0.72f)
                        lineTo(w * 0.8f, w * 0.3f)
                    },
                    color,
                    style = stroke,
                )
                Mark.Declined -> {
                    drawLine(color, Offset(w * 0.25f, w * 0.25f), Offset(w * 0.75f, w * 0.75f), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.75f, w * 0.25f), Offset(w * 0.25f, w * 0.75f), stroke.width, StrokeCap.Round)
                }
                Mark.Failed -> {
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.5f, w * 0.12f)
                            lineTo(w * 0.9f, w * 0.85f)
                            lineTo(w * 0.1f, w * 0.85f)
                            close()
                        },
                        color,
                        style = stroke,
                    )
                    drawLine(color, Offset(w * 0.5f, w * 0.4f), Offset(w * 0.5f, w * 0.6f), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.5f, w * 0.71f), Offset(w * 0.5f, w * 0.72f), stroke.width, StrokeCap.Round)
                }
                Mark.Clock -> {
                    drawCircle(color, radius = w * 0.38f, style = stroke)
                    drawLine(color, Offset(w * 0.5f, w * 0.3f), Offset(w * 0.5f, w * 0.52f), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.5f, w * 0.52f), Offset(w * 0.65f, w * 0.62f), stroke.width, StrokeCap.Round)
                }
            }
        }
    }
}

private fun confirmingTitle(s: SigningState): String = when (val k = s.kind) {
    is TxKind.Join -> "Taking your place"
    is TxKind.Submit -> "Submitting day ${k.day + 1}"
    is TxKind.Claim -> s.amount?.let { "Claiming $it" } ?: "Claiming"
}

private fun confirmingStep(k: TxKind): String = when (k) {
    is TxKind.Join -> "Confirming your place"
    is TxKind.Submit -> "Confirming before the deadline"
    is TxKind.Claim -> "Confirming"
}

private fun resultStep(k: TxKind): String = when (k) {
    is TxKind.Join -> "In the lobby"
    is TxKind.Submit -> "Day ${k.day + 1} on chain"
    is TxKind.Claim -> "In your wallet"
}

private fun landedTitle(s: SigningState): String = when (val k = s.kind) {
    is TxKind.Join -> "Place secured"
    is TxKind.Submit -> "Day ${k.day + 1} is on chain"
    is TxKind.Claim -> "Claimed"
}

/** What a declined or failed send leaves untouched — said only when nothing can have moved (G7). */
private fun stillSafe(s: SigningState): String = when (s.kind) {
    is TxKind.Join -> "Your SKR is still yours."
    is TxKind.Submit -> "Your day is saved here" + (s.timeLeft?.let { " — submit within $it." } ?: ".")
    is TxKind.Claim -> s.amount?.let { "Your $it waits here." } ?: "Your claim waits here."
}

private const val WAITING = "Waiting for your wallet"
private const val GRADIENT_MILLIS = 900
private val MARK_SIZE = 72.dp
private val GLYPH_SIZE = 30.dp

// ---- previews -------------------------------------------------------------------------------------

private val JOIN = SigningState(TxKind.Join("c", 1), TxPhase.WaitingForWallet, "Spark · 7-day · joining", amount = "1,250 SKR")
private val CLAIM = SigningState(TxKind.Claim("c"), TxPhase.Confirming, "Spark · 7-day · claiming", amount = "1,412 SKR", breakdown = "1,250 SKR stake back + 162 SKR from the pool.")

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun WaitingPreview() = HoldoutTheme { SigningScreen(JOIN, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun ConfirmingPreview() = HoldoutTheme { SigningScreen(CLAIM, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun ClaimedPreview() = HoldoutTheme {
    SigningScreen(CLAIM.copy(phase = TxPhase.Done(TxOutcome.Landed("4Bx9pQ7Qk2abcdef")), upsell = "Make it a Habit · 21-day" to NavEvent.OpenStake("h")), {})
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun DeclinedPreview() = HoldoutTheme { SigningScreen(JOIN.copy(phase = TxPhase.Done(TxOutcome.Declined)), {}) }

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun DeadlinePreview() = HoldoutTheme {
    SigningScreen(SigningState(TxKind.Submit("c", 11), TxPhase.Done(TxOutcome.DeadlinePassed), "Habit · 21-day · day 12"), {})
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun YoureInPreview() = HoldoutTheme {
    YoureInScreen(YoureInState("Spark · 7-day", "2d 4h", 15, "13,750 SKR", "1,250 SKR", "2d 3h", daily25Done = false), {}, {})
}
