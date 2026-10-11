package holdout.app.cohort

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.Derive
import holdout.core.Journey
import holdout.core.Stage

/**
 * The forfeit (US-4, FR-052, T070): what happened, plainly; that **the streak
 * is still alive** — a forfeit ends a Holdout, never the Daily 25 streak — and
 * the way back.
 *
 * With a comeback [offer] (FR-037g, T181) the way back is its card, from the
 * forfeit itself and before automation has recorded it:
 *
 * ```
 * THE WAY BACK
 * up to 5 SKR back                              (AmountStyle, Accent)
 * Finish the Easy · 7-day Holdout starting in 1d 2h and up to 5 SKR of the stake you lost comes back.
 * Joining stays open for 4h 10m.
 * The one after it starts in 8d 2h and opens for joining in 1d 2h.
 * [ Join the Easy · 7-day Holdout ]             (the one primary button)
 * [ Today's Daily 25 ]
 * ```
 *
 * with a place already held in a round, "finish it and up to N SKR … comes back"
 * and no Join; with a lobby place outside the window, leave it first and no Join
 * (ES-18). With no offer (null) the ways back are as before: today's Daily 25
 * and the next Holdout of the same level and length, the one a comeback refund
 * pays on (FR-037e).
 */
@Composable
fun ForfeitScreen(
    h: Holdout,
    stage: Stage.Forfeited,
    streak: Int,
    onDaily25: () -> Unit,
    /** The same league's next Holdout's stake sheet; null when none is open to join. Used only with no [offer]. */
    onNextHoldout: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** What finishing a later Holdout of this level and length brings back of the lost stake, if anything (FR-037g); null shows no offer. */
    offer: Comeback.Offer? = null,
    /** Chain time, for the countdowns on the offer. */
    now: Long = 0,
    /** What stops the member joining the offer's round now; null lets the Join show (ES-17: a breach of the running Holdout is not one). */
    joinBlocker: Journey.JoinBlocker? = null,
    /** Opens the stake sheet on the lost stake's tier, for the offer's next open round. */
    onJoin: () -> Unit = {},
) {
    val c = h.cohort
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Home", color = HoldoutColors.TextSecondary) }
        if (h.isTest) TestCohortBadge(c.dayLength)
        SectionLabel(Format.holdoutTitle(c))
        Text("You missed day ${stage.daysLasted + 1}.", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.Warning)
        HoldoutCard {
            Body("You lasted ${stage.daysLasted} of ${c.durationDays} days. The deadline passed before day ${stage.daysLasted + 1} reached the chain, so your stake of ${h.membership?.let { Format.skr(it.stake) } ?: "—"} is lost. ${Format.lostStakeSplit(c, "this Holdout's prize pool")}")
            if (!stage.recorded) Body("It is final from the deadline; the record catches up on the next automation pass.")
        }
        HoldoutCard {
            Text(if (streak > 0) "Your $streak-day streak is still alive." else "Your streak is untouched.", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Success)
            Body("A forfeit ends a Holdout, never your streak. The Daily 25 keeps it going.")
        }
        if (offer != null) {
            ComebackCard(offer, now, joinBlocker, onJoin, onDaily25)
        } else {
            SectionLabel("Two ways back")
            PrimaryButton("Today's Daily 25", onDaily25)
            onNextHoldout?.let { SecondaryButton(if (h.isTest) "The next short test cohort" else "The next ${c.durationDays}-day Holdout", it) }
        }
    }
}

/**
 * The offer card (FR-037g): every amount "up to" — the refund is capped at the
 * new stake and cut pro rata if the comeback reserve falls short (FR-037e) —
 * and the rounds named by level and length, never by league (FR-041).
 */
@Composable
private fun ColumnScope.ComebackCard(offer: Comeback.Offer, now: Long, joinBlocker: Journey.JoinBlocker?, onJoin: () -> Unit, onDaily25: () -> Unit) {
    val held = offer.held
    val place = held?.entry
    val next = offer.next
    val nextCohort = next?.entry?.cohort
    SectionLabel("The way back")
    HoldoutCard {
        when {
            place != null -> {
                val back = offer.amount(place.member?.stake)
                Text("up to ${Format.skr(back)} back", style = AmountStyle, color = HoldoutColors.Accent)
                val timing = if (Derive.status(place.cohort, now) == CohortStatus.Running) "running now" else "starting in ${Format.countdown(place.cohort.start - now)}"
                Body("You hold a place in the ${Format.holdoutTitle(place.cohort)}, $timing.")
                Body("Finish it and up to ${Format.skr(back)} of the stake you lost comes back.")
            }
            next == null && offer.upcoming != null -> {
                // The round before it is locked or running and the next is not opened yet: nothing to join, not closed (FR-037g).
                val upcoming = offer.upcoming!!
                val back = offer.amount()
                Text("up to ${Format.skr(back)} back", style = AmountStyle, color = HoldoutColors.Accent)
                Body("The next ${Format.holdoutTitle(offer.forfeited.cohort)} ${Format.opensForJoining(upcoming.opensAt, now)} and starts in ${Format.countdown(upcoming.start - now)}. Finish it and up to ${Format.skr(back)} of the stake you lost comes back.")
            }
            next != null && nextCohort != null -> {
                val back = offer.amount()
                Text("up to ${Format.skr(back)} back", style = AmountStyle, color = HoldoutColors.Accent)
                Body("Finish the ${Format.holdoutTitle(nextCohort)} starting in ${Format.countdown(next.start - now)} and up to ${Format.skr(back)} of the stake you lost comes back.")
                Body("Joining stays open for ${Format.countdown(Derive.timeUntilJoiningCloses(nextCohort, now))}.")
                offer.after(next)?.let { after ->
                    val open = after.entry != null && after.status == CohortStatus.Open
                    when {
                        open -> Body("Or the one after it, starting in ${Format.countdown(after.start - now)}.")
                        after.entry == null -> Body("The one after it starts in ${Format.countdown(after.start - now)} and ${Format.opensForJoining(after.opensAt, now)}.")
                    }
                }
                when (joinBlocker) {
                    Journey.JoinBlocker.LobbyPlaceTaken -> Notice("You hold a place in another Holdout that hasn't started. Leave it first, then you can join this one.", warning = true)
                    Journey.JoinBlocker.Overlaps -> Notice("This Holdout starts before your current one ends.", warning = true)
                    Journey.JoinBlocker.JoiningClosed -> Notice("Joining has closed for this Holdout.", warning = true)
                    null -> Unit
                }
            }
        }
    }
    val canJoin = held == null && next != null && joinBlocker == null
    if (canJoin) {
        PrimaryButton("Join the ${Format.holdoutTitle(nextCohort!!)}", onJoin)
        SecondaryButton("Today's Daily 25", onDaily25)
    } else {
        PrimaryButton("Today's Daily 25", onDaily25)
    }
}
