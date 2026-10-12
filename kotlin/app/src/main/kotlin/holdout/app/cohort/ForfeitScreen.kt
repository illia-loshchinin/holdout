package holdout.app.cohort

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import holdout.app.chain.Holdout
import holdout.app.ui.Body
import holdout.app.ui.ChainCheckBox
import holdout.app.ui.ChainUnreachable
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.KeyValueRow
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.ScreenHeader
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.Derive
import holdout.core.Journey
import holdout.core.MembershipSnapshot
import holdout.core.Stage

/**
 * The forfeit (H6, board 9; US-4, FR-052): what happened, plainly; the standing
 * of the Holdout and where the member fell out of it; the **Stake lost** and
 * where it went; that **the streak is still alive** — a forfeit ends a Holdout,
 * never the Daily 25 streak — and the way back.
 *
 * ```
 *  ‹  HABIT · 21-DAY · DAY 12 OF 21                      ScreenHeader
 *  ┌ 189 / 312 still standing        48,200 SKR ┐        summary card · DayStrip
 *  └ ▮▮▮▮▮▮▮▮▮▮▮▮▯▯▯▯▯▯▯▯▯  Day 1 · You: out on day 12 · Day 21 ┘
 *  You missed day 12.                                    headline
 *  It didn't reach the chain in time. Your run has ended.      (a day never finished: "You didn't finish day 12 before its deadline. …")
 *  ┌ Stake lost                       1,250 SKR ┐        KeyValueRow + the fee sentence
 *  ┌ COMEBACK  Win back 125 SKR … ┐                      the offer card, by state
 *  [ Join the next Habit · 21-day ]                       the one primary button
 *  Your 12-day streak is still alive.                     one quiet line, no card
 *  [ Do today's Daily 25 ]                                SecondaryButton, only while today's is not done
 * ```
 *
 * With a comeback [offer] (FR-037g) the way back is its card, from the forfeit
 * itself and before automation has recorded it: a round to join (after a
 * last-day miss the round that just started, joinable until the end of its
 * first day, with the one after it as the other way), a place
 * already held, a round not opened yet or opening shortly, or a lobby place
 * elsewhere to leave first (ES-17…ES-19). With no offer (null) the ways back are
 * today's Daily 25 and the next Holdout of the same level and length, the one a
 * comeback refund pays on (FR-037e).
 */
@Composable
fun ForfeitScreen(
    h: Holdout,
    stage: Stage.Forfeited,
    streak: Int,
    onDaily25: () -> Unit,
    /** The same league's next Holdout's stake sheet; null when none is open to join. */
    onNextHoldout: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** What finishing the next Holdout of this level and length brings back of the lost stake, exactly, if anything (FR-037e, FR-037g); null shows no offer. */
    offer: Comeback.Offer? = null,
    /** Chain time, for the countdowns on the offer. */
    now: Long = 0,
    /** What stops the member joining the offer's round now; null lets the Join show (ES-17: a breach of the running Holdout is not one). */
    joinBlocker: Journey.JoinBlocker? = null,
    /** Opens the stake sheet on the lost stake's tier, for the offer's next open round. */
    onJoin: () -> Unit = {},
    /** The last chain read failed (spec 005): the line, Check again and the pull. */
    offline: Boolean = false,
    checking: Boolean = false,
    stillUnreachable: Boolean = false,
    onCheck: () -> Unit = {},
    /** The Holdout's roster, once read: the summary card's counts. Null shows the strip alone. */
    members: List<MembershipSnapshot>? = null,
    /** Opens the place the member holds in a round of the offer; null shows no such button. */
    onSeePlace: (() -> Unit)? = null,
    /** The missed day was finished on this phone and never reached the chain: the cause sentence says so (FR-024b, ES-3b). */
    doneOnPhone: Boolean = false,
    /** Today's Daily 25 is done on this phone: the Daily 25 button and its sentences give way. */
    daily25Done: Boolean = false,
) {
    val c = h.cohort
    val figures = members?.let { Derive.figures(c, it, now) }
    ChainCheckBox(offline, checking, onCheck, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HoldoutColors.Canvas)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
        ) {
            ScreenHeader(DetailText.label(c, now), onBack = onBack, backDescription = "Back to Holdouts")
            if (h.isTest) TestCohortBadge(c.dayLength, Modifier.align(Alignment.CenterHorizontally))
            if (offline) ChainUnreachable(checking, stillUnreachable, onCheck)

            Summary(c, stage.daysLasted, figures)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                Text(ForfeitText.headline(stage.daysLasted), style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
                Body(ForfeitText.body(stage.daysLasted, doneOnPhone))
            }

            HoldoutCard {
                h.membership?.let { KeyValueRow("Stake lost", Format.skr(it.stake)) }
                Text(Format.lostStakeSplitForfeit(c, name = Format.boostName(c, h.isTest)), style = LabelStyle, color = HoldoutColors.TextTertiary)
                if (!stage.recorded) Text(ForfeitText.PENDING, style = LabelStyle, color = HoldoutColors.TextTertiary)
            }

            val copy = offer?.let { ForfeitText.offer(it, c, now, joinBlocker, dailyOpen = !daily25Done) }
            if (copy != null) {
                OfferCard(copy)
            } else {
                ForfeitText.noOffer(c, onNextHoldout != null, dailyOpen = !daily25Done)?.let { words ->
                    if (!daily25Done) SectionLabel("Two ways back")
                    Body(words)
                }
            }

            Buttons(h, offer, joinBlocker, onNextHoldout, onJoin, onSeePlace)

            // A run ending never ends the streak: one quiet line, not a card.
            Text(ForfeitText.streakTitle(streak), style = LabelStyle, color = HoldoutColors.TextSecondary)
            if (!daily25Done) SecondaryButton("Do today's Daily 25", onDaily25)
        }
    }
}

/** `189 / 312 still standing` · the pool, and the days as a strip with the missed one marked. */
@Composable
private fun Summary(c: CohortSnapshot, daysLasted: Int, figures: holdout.core.CohortFigures?) {
    val spoken = ForfeitText.summaryDescription(c, daysLasted, figures?.survivors?.standing, figures?.projectedPool)
    HoldoutCard(Modifier.semantics { contentDescription = spoken }) {
        if (figures != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("${figures.survivors.standing} / ${figures.survivors.starters}", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
                    Text("still standing", style = LabelStyle, color = HoldoutColors.TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.skr(figures.projectedPool), style = AmountStyle, color = HoldoutColors.TextPrimary)
                    Text("prize pool", style = LabelStyle, color = HoldoutColors.TextTertiary)
                }
            }
        }
        DayStrip(c.durationDays, daysLasted)
    }
}

/** The offer card (FR-037g): a label, "Win back …", one sentence by state, and the round's timing. */
@Composable
private fun OfferCard(copy: ForfeitText.OfferCopy) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HoldoutColors.Success.copy(alpha = TINT_ALPHA), MaterialTheme.shapes.medium)
            .border(Spacing.border, HoldoutColors.Success.copy(alpha = BORDER_ALPHA), MaterialTheme.shapes.medium)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        Text(copy.label.uppercase(), style = LabelStyle, color = HoldoutColors.Success)
        copy.headline?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary) }
        Body(copy.body)
        copy.timing?.let { Text(timingLine(it, copy.countdowns), style = LabelStyle, color = HoldoutColors.Accent) }
        copy.after?.let { Text(it, style = LabelStyle, color = HoldoutColors.TextTertiary) }
        copy.notice?.let { Notice(it, warning = true) }
    }
}

/** Mono is for countdowns (design book): the words stay in the text face, only each of the [countdowns] in [text] is mono. */
private fun timingLine(text: String, countdowns: List<String>): AnnotatedString = buildAnnotatedString {
    val mono = AmountStyle.toSpanStyle().copy(fontSize = LabelStyle.fontSize)
    var at = 0
    for (countdown in countdowns) {
        val i = text.indexOf(countdown, at)
        if (i < 0) break
        append(text.substring(at, i))
        withStyle(mono) { append(countdown) }
        at = i + countdown.length
    }
    append(text.substring(at))
}

/** The one primary button, the way back into a Holdout; the Daily 25 is its own secondary button below the streak note (DS: one primary per screen). */
@Composable
private fun ColumnScope.Buttons(
    h: Holdout,
    offer: Comeback.Offer?,
    joinBlocker: Journey.JoinBlocker?,
    onNextHoldout: (() -> Unit)?,
    onJoin: () -> Unit,
    onSeePlace: (() -> Unit)?,
) {
    val c = h.cohort
    val next = offer?.next
    val nextCohort = next?.entry?.cohort
    when {
        offer == null -> if (onNextHoldout != null) PrimaryButton(ForfeitText.nextHoldout(c, h.isTest), onNextHoldout)
        offer.held != null && onSeePlace != null -> PrimaryButton("See your place", onSeePlace)
        // The round that has started or is locked is not "the next": the member joins it late — today's once it runs.
        offer.held == null && nextCohort != null && joinBlocker == null -> PrimaryButton(
            when {
                !next.lateJoin -> "Join the next ${Format.level(nextCohort)}"
                next.status == CohortStatus.Running -> "Join today's ${Format.level(nextCohort)}"
                else -> "Join the ${Format.level(nextCohort)}"
            },
            onJoin,
        )
    }
}

/** Every word the forfeit says, pure so it can be tested (no dates, no dollar figures; rulings G3–G5, D-D3, D-D4). */
internal object ForfeitText {
    private const val ENDED = "Your run has ended."

    /** The cause, by what this phone knows: a day finished but not sent reached the chain too late; a day never finished was not finished by its deadline. */
    fun body(daysLasted: Int, doneOnPhone: Boolean): String = if (doneOnPhone) {
        "It didn't reach the chain in time. $ENDED"
    } else {
        "You didn't finish day ${daysLasted + 1} before its deadline. $ENDED"
    }
    const val PENDING = "Final as of the deadline. The chain shows it shortly."

    fun headline(daysLasted: Int): String = "You missed day ${daysLasted + 1}."

    fun streakTitle(streak: Int): String = if (streak > 0) "Your $streak-day streak is still alive." else "Your streak is untouched."

    fun nextHoldout(c: CohortSnapshot, test: Boolean): String = if (test) "The next Hackathon Holdout" else "The next ${Format.level(c)}"

    /** No comeback offer: two ways back. */
    fun noOffer(c: CohortSnapshot, nextOpen: Boolean, dailyOpen: Boolean = true): String? = when {
        dailyOpen && nextOpen -> "Today's Daily 25 keeps your streak going. The next ${Format.level(c)} starts a new run."
        dailyOpen -> "Today's Daily 25 keeps your streak going."
        nextOpen -> "The next ${Format.level(c)} starts a new run."
        else -> null
    }

    fun summaryDescription(c: CohortSnapshot, daysLasted: Int, standing: Int?, pool: ULong?): String = buildString {
        if (standing != null) append("$standing of ${c.memberCount} still standing; ")
        if (pool != null) append("prize pool ${Format.skr(pool)}; ")
        append("you were out on day ${daysLasted + 1} of ${c.durationDays}.")
    }

    /** The offer card's pieces for one state of [offer]. */
    data class OfferCopy(
        val label: String,
        val headline: String?,
        val body: String,
        /** The round's start and when joining closes. */
        val timing: String? = null,
        /** The countdowns inside [timing], in order: the only part set in mono. */
        val countdowns: List<String> = emptyList(),
        val after: String? = null,
        val notice: String? = null,
    )

    /**
     * The offer card for [offer], or null when no round is left to join, hold or wait for and today's
     * Daily 25 is done ([dailyOpen] false): the "keeps your streak going" sentence would ask for a day already done.
     */
    fun offer(offer: Comeback.Offer, forfeited: CohortSnapshot, now: Long, joinBlocker: Journey.JoinBlocker?, dailyOpen: Boolean = true): OfferCopy? {
        val held = offer.held
        val place = held?.entry
        val next = offer.next
        val nextCohort = next?.entry?.cohort
        val level = Format.level(forfeited)
        return when {
            place != null -> {
                val back = Format.skr(offer.amount(place.member?.stake))
                val timing = if (Derive.status(place.cohort, now) == CohortStatus.Running) "running now" else "starting in ${Format.countdown(place.cohort.start - now)}"
                val sep = if (timing == "running now") ", " else " "
                OfferCopy(COMEBACK, Format.winBack(offer.amount(place.member?.stake)), "You're in the ${Format.level(place.cohort)}$sep$timing. Finish it and $back of the stake you lost comes back.")
            }
            next == null && offer.upcoming != null -> {
                // The round before it is locked or running and the next is not opened yet: nothing to join, not closed (FR-037g).
                val upcoming = offer.upcoming!!
                val back = Format.skr(offer.amount())
                OfferCopy(COMEBACK, Format.winBack(offer.amount()), "The next $level ${Format.opensForJoining(upcoming.opensAt, now)} and starts in ${Format.countdown(upcoming.start - now)}. Finish it and $back of the stake you lost comes back.")
            }
            next != null && nextCohort != null -> {
                val back = Format.skr(offer.amount())
                when (joinBlocker) {
                    Journey.JoinBlocker.LobbyPlaceTaken -> OfferCopy(COMEBACK, null, "You hold a place in a Holdout that starts later. Leave it first to join one that can bring your stake back.")
                    else -> {
                        val notice = when (joinBlocker) {
                            Journey.JoinBlocker.Overlaps -> "This Holdout starts before your current one ends."
                            Journey.JoinBlocker.JoiningClosed -> "Joining has closed for this Holdout."
                            else -> null
                        }
                        if (next.lateJoin) {
                            val closesIn = Format.countdown((next.closesAt - now).coerceAtLeast(0))
                            // The round the miss lands on has started (or is locked): joinable until the end of its first day, with the one after it as the other way.
                            val name = Format.level(nextCohort)
                            val lead = if (Derive.status(nextCohort, now) == CohortStatus.Running) "Join the $name that started today." else "Join the $name starting in ${Format.countdown(next.start - now)}."
                            val tail = offer.after(next)?.let { "Finish it \u2014 or the one after it, starting in ${Format.countdown(it.start - now)} \u2014 and $back comes back." } ?: "Finish it and $back comes back."
                            OfferCopy(COMEBACK, Format.winBack(offer.amount()), "$lead $tail", timing = "Joining closes in $closesIn", countdowns = listOf(closesIn), notice = notice)
                        } else {
                            val startsIn = Format.countdown(next.start - now)
                            val closesIn = Format.countdown(Derive.timeUntilJoiningCloses(nextCohort, now))
                            OfferCopy(
                                COMEBACK,
                                Format.winBack(offer.amount()),
                                Format.comebackForfeit(offer.amount(), nextCohort),
                                timing = "Starts in $startsIn · joining closes in $closesIn",
                                countdowns = listOf(startsIn, closesIn),
                                notice = notice,
                            )
                        }
                    }
                }
            }
            dailyOpen -> OfferCopy(COMEBACK, null, "Today's Daily 25 keeps your streak going.")
            else -> null
        }
    }

    private const val COMEBACK = "comeback"
}

private const val TINT_ALPHA = 0.08f
private const val BORDER_ALPHA = 0.32f
