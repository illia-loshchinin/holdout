package holdout.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Sitting
import holdout.app.nav.TxFlow
import holdout.app.nav.TxKind
import holdout.app.storage.Store
import holdout.app.tx.SigningScreen
import holdout.app.tx.SigningState
import holdout.app.tx.YoureInScreen
import holdout.app.tx.YoureInState
import holdout.app.ui.Format
import holdout.core.Derive
import holdout.core.LeagueParams
import holdout.core.Stage

/**
 * The signing flow (rulings G7, FR-070): waiting for your wallet, the
 * confirming checklist, the result — and You're in after a join. Its phase
 * comes from `HoldoutViewModel.UiState.signing` through the navigation model.
 */
@Composable
internal fun Shared.TxFlowHost(tx: TxFlow, modifier: Modifier = Modifier) {
    val h = holdout(tx.kind.cohort)
    val n = now
    if (h == null || n == null) return Loading(modifier)
    if (tx.youreIn && n >= h.cohort.start) {
        // Joined late into a Holdout that has started: "You're in" speaks of a lobby, so go to Today's day instead.
        androidx.compose.runtime.LaunchedEffect(tx) { dispatch(NavEvent.JoinedUnderWay) }
        return Loading(modifier)
    }
    if (tx.youreIn) {
        YoureInScreen(youreIn(h, n, (tx.kind as? TxKind.Join)?.tier ?: 0), onDaily25 = {
            if (dayIndex == Store.UNKNOWN_DAY) offlineDays.sittingStarted(wallet, boot, android.os.SystemClock.elapsedRealtime())
            dispatch(NavEvent.StartSitting(Sitting.Daily25(dayIndex)))
        }, onEvent = dispatch, modifier = modifier)
        return
    }
    SigningScreen(signingState(tx, h, n), onEvent = dispatch, modifier = modifier)
}

/** "Try again": the same action, a new signature asked of the wallet. */
internal fun Shared.resend(kind: TxKind) {
    val h = holdout(kind.cohort) ?: return
    when (kind) {
        is TxKind.Join -> now?.let { n -> holdouts.join(sender, h, kind.tier, n) }
        is TxKind.Submit -> submitDayOf(h, kind.day)
        is TxKind.Claim -> holdouts.claim(sender, h)
    }
}

internal fun Shared.signingState(tx: TxFlow, h: Holdout, n: Long): SigningState {
    val c = h.cohort
    val level = Format.holdoutName(c)
    return when (val k = tx.kind) {
        is TxKind.Join -> SigningState(k, tx.phase, "$level · joining", amount = c.chips.getOrNull(k.tier)?.let(Format::skr))
        is TxKind.Submit -> SigningState(k, tx.phase, "$level · day ${k.day + 1}", timeLeft = Derive.timeLeftToday(c, n)?.let(Format::countdown))
        is TxKind.Claim -> {
            val (amount, stake, share, comeback) = when (val s = h.stage(n)) {
                is Stage.ReadyToClaim -> listOf(s.amount, s.stake, s.share, s.comeback)
                is Stage.Claimed -> listOf(s.amount, s.stake, s.share, s.comeback)
                else -> listOf(null, null, null, null)
            }
            val breakdown = if (stake != null && share != null && stake > 0uL) {
                "${Format.skr(stake)} stake back + ${Format.skr(share)} from the pool" + (comeback?.takeIf { it > 0uL }?.let { " + ${Format.skr(it)} comeback" } ?: "") + "."
            } else {
                null
            }
            SigningState(k, tx.phase, "$level · claiming", amount = amount?.let(Format::skr), breakdown = breakdown, upsell = upsell(h, n))
        }
    }
}

/**
 * After a claim, the next step up that the member can take now (G7): from a
 * Spark, "Make it a Habit · 21-day"; from a Habit, the next Habit. Only a
 * Holdout open to join that nothing blocks; never the short test league as the
 * target. A short test Holdout's claim offers it like any other: a test Spark
 * the Habit, a test Holdout of the toes form the next one. The length in the
 * words is the open Habit's own, never typed here.
 */
private fun Shared.upsell(h: Holdout, n: Long): Pair<String, NavEvent>? = view?.let { claimUpsell(h, it, n, seekerKnown) }

internal fun claimUpsell(h: Holdout, v: ChainView, n: Long, seekerKnown: Boolean): Pair<String, NavEvent>? {
    val habit = v.joinable(n)
        .filter { !it.isTest && it.cohort.form == LeagueParams.FORM_TOES }
        .minByOrNull { it.cohort.start } ?: return null
    val text = if (h.cohort.form == LeagueParams.FORM_TOES) "Join the next ${Format.holdoutName(habit.cohort)}" else "Make it a ${Format.holdoutName(habit.cohort)}"
    return text to NavEvent.OpenStake(habit.address.base58(), seekerKnown = seekerKnown)
}

private fun Shared.youreIn(h: Holdout, n: Long, tier: Int): YoureInState {
    val c = h.cohort
    return YoureInState(
        name = Format.holdoutName(c),
        startsIn = Format.countdown(c.start - n),
        members = c.memberCount,
        atStake = Format.skr(Derive.atStake(c)),
        stake = Format.skr(h.membership?.stake ?: c.chips.getOrElse(tier) { 0uL }),
        joiningClosesIn = Format.countdown(Derive.timeUntilJoiningCloses(c, n)),
        daily25Done = store.isDayComplete(wallet, Store.CHALLENGE, dayIndex),
        test = h.isTest,
        testDayLength = c.dayLength,
        startsToday = YoureInState.startsToday(c.start, dayIndex),
    )
}
