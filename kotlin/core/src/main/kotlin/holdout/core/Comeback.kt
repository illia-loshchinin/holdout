package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * The comeback offer, worked out on the phone from the member's own forfeited
 * membership the moment a deadline passes unmet (FR-037g, research R22,
 * data-model §5 "Comeback offer"). The program decides the same thing once the
 * forfeited cohort settles (`settle_count` opens the comeback), by which time
 * the app has been showing it for a while, so the rule lives in both places and
 * is the same one: `schedule::comeback_rounds` (FR-037e).
 *
 * The rule: with `missed = deadline_of(days_completed)` of the latest forfeit of
 * a progress slot, the rounds that make it good are the forfeit's own league's
 * cohorts `first..last`. `first` is the league's first cohort to **start** after
 * the miss; `last` is `first`, or `first + 1` when the miss was so late that
 * `first` had already closed for joining (a miss on a round's last day lands the
 * second before the next round starts). Finishing any of them brings back
 * exactly `min(10 % of the lost stake, the new stake)` ([Settlement.comebackOwed]):
 * guaranteed, since the pledge was set aside at the forfeit.
 *
 * The member whose round `first` it is may join it **late** — even once it is
 * Locked or Running — until the end of its first day (`join_cohort` takes the
 * forfeited membership and its cohort as optional accounts). Only the latest
 * forfeit of a slot counts, a finished round uses the offer up, and a forfeit
 * recorded by the crank and one still unrecorded ([Derive.inBreach]) count alike
 * (FR-038a). Another league never counts, test leagues included, though they
 * share a progress slot (`Profile::slot_of`). Pure: chain accounts and chain
 * time only (constitution II, VI).
 */
object Comeback {
    /** One cohort with the connected wallet's membership in it, if any — all the offer reads. */
    data class Entry(val address: SolanaPublicKey, val cohort: CohortSnapshot, val member: MembershipSnapshot?)

    /**
     * One cohort of the league that makes the comeback good, scheduled at
     * `firstStart + index × cadence`. [entry] is its cohort once the program has
     * opened it, [status] that cohort's status; before then a round is only
     * named, and it opens at [opensAt], the previous round's start.
     *
     * [closesAt] is the first second the member can no longer join it: the
     * join cutoff, or — for the round a Locked or Running cohort the member may
     * still enter late ([lateJoin]) — the second after its first day's deadline.
     */
    data class Round(
        val start: Long,
        val opensAt: Long,
        val entry: Entry?,
        val status: CohortStatus?,
        /** The cohort index in its league; `0` where a test builds a round by hand. */
        val index: Long = 0,
        val closesAt: Long = 0,
        /** The member's comeback round, started or locked, still open to them until the end of its first day. */
        val lateJoin: Boolean = false,
    ) {
        /** Taking a place here is possible now: opened, joining open (or open late for this member), no place held in it. */
        val joinable: Boolean get() = entry != null && entry.member == null && (status == CohortStatus.Open || lateJoin)
    }

    /**
     * What a member who lost a stake can still win back in one progress slot.
     * [rounds] are the league's cohorts [firstRound]..[lastRound], in order;
     * [held] is the round the member already holds a place in (lobby or
     * running), if any.
     */
    data class Offer(
        val slot: Int,
        /** The latest forfeited stake. */
        val lost: ULong,
        /** The tier the lost stake was staked on. */
        val tier: Int,
        /** The missed deadline: `deadline_of(days_completed)` of the forfeited cohort. */
        val missed: Long,
        /** The league's first cohort to start after [missed]: `schedule::comeback_round`. */
        val firstRound: Long = 0,
        /** [firstRound], or the one after it when [firstRound] had closed for joining by the second after the miss. */
        val lastRound: Long = firstRound,
        /** The forfeited cohort. */
        val forfeited: Entry,
        val rounds: List<Round>,
        val held: Round?,
        /**
         * The first round the program has not opened yet, that has not started and is not inside its
         * own join lock (`entry == null`, `start > now`, `start - joinLock > now`): still to come and
         * joinable once opened, so not closed, and it keeps the offer alive while the round before it
         * is locked or running (FR-037g).
         */
        val upcoming: Round? = null,
    ) {
        /** The earliest round still open to join, if any. */
        val next: Round? get() = rounds.firstOrNull { it.joinable }

        /** The round scheduled right after [round] among the offer's, if any. */
        fun after(round: Round): Round? = rounds.getOrNull(rounds.indexOf(round) + 1)

        /**
         * What finishing a Holdout staked on [chip] brings back, exactly:
         * `min(floor(lost × 10 ÷ 100), chip)`, and with no chip chosen yet
         * `floor(lost × 10 ÷ 100)` (FR-037e).
         */
        fun amount(chip: ULong? = null): ULong = Settlement.comebackOwed(lost, chip ?: ULong.MAX_VALUE)
    }

    /** The offer for the forfeit screen of the cohort at [address]: only the latest forfeit of a slot carries it, an older one's screen shows none. */
    fun Offer?.forScreenOf(address: SolanaPublicKey): Offer? = this?.takeIf { it.forfeited.address == address }

    /** The offer in the slot of [leagueId], or null (see [offers]). */
    fun offer(holdouts: List<Entry>, leagues: List<LeagueSnapshot>, now: Long, leagueId: Int): Offer? = offers(holdouts, leagues, now).firstOrNull { it.slot == ProfileSnapshot.slotOf(leagueId) }

    /**
     * Per progress slot, the offer from the member's latest forfeit there. A slot
     * has none when the comeback is used up (a finished round), or when nothing is
     * left to join or finish: every round closed for joining without the member in
     * one. A round the program has not opened yet has not closed, so an
     * [Offer.upcoming] one keeps the offer (FR-037g), unless it is already inside
     * its join lock. The league of the forfeit must be among [leagues]: its first
     * start, cadence and join lock give the rounds.
     */
    fun offers(holdouts: List<Entry>, leagues: List<LeagueSnapshot>, now: Long): List<Offer> {
        val mine = holdouts.filter { it.member != null }
        val forfeits = mine.filter { forfeited(it, now) }.groupBy { ProfileSnapshot.slotOf(it.cohort.leagueId) }
        return forfeits.mapNotNull { (slot, lost) ->
            // Only the latest counts, as on-chain; a tie goes to the later cohort.
            val latest = lost.maxWith(compareBy({ missedOf(it) }, { it.cohort.start }))
            offerFrom(slot, latest, holdouts, leagues, now)
        }.sortedBy { it.slot }
    }

    private fun forfeited(e: Entry, now: Long): Boolean {
        val m = e.member ?: return false
        return m.state == MembershipSnapshot.State.Forfeited || Derive.inBreach(m, e.cohort, now)
    }

    private fun missedOf(e: Entry): Long = Derive.deadlineOf(e.cohort, e.member!!.daysCompleted)

    private fun finished(m: MembershipSnapshot, c: CohortSnapshot): Boolean = m.state == MembershipSnapshot.State.Finished ||
        m.state == MembershipSnapshot.State.Claimed ||
        (m.state == MembershipSnapshot.State.Joined && m.daysCompleted >= c.durationDays)

    private fun offerFrom(slot: Int, lost: Entry, holdouts: List<Entry>, leagues: List<LeagueSnapshot>, now: Long): Offer? {
        val member = lost.member!!
        val league = leagues.firstOrNull { it.params.leagueId == lost.cohort.leagueId }?.params ?: return null
        if (league.cadence <= 0) return null
        val missed = missedOf(lost)
        val indices = Schedule.comebackRounds(league.firstStart, league.cadence, league.joinLock, missed)
        val inLeague = holdouts.filter { it.cohort.leagueId == league.leagueId && it.cohort.index in indices }

        // Used up: the member finished one of the rounds (settle_count makes the comeback good then).
        if (inLeague.any { e -> e.member?.let { finished(it, e.cohort) } == true }) return null

        val rounds = indices.map { k ->
            val start = Schedule.cohortStart(league.firstStart, league.cadence, k)
            val entry = inLeague.firstOrNull { it.cohort.index == k }
            val status = entry?.let { Derive.status(it.cohort, now) }
            // The member's own comeback round, `first`, stays open to them after its cutoff, to the end of its first day.
            val firstDayEnds = entry?.let { Schedule.deadlineOf(it.cohort.start, it.cohort.dayLength, 0) + 1 }
            val late = k == indices.first &&
                entry != null &&
                entry.member == null &&
                !entry.cohort.settled &&
                (status == CohortStatus.Locked || status == CohortStatus.Running) &&
                now < firstDayEnds!!
            val closesAt = if (late) firstDayEnds!! else entry?.cohort?.joinCutoff ?: Schedule.joinCutoff(start, league.joinLock)
            Round(start, start - league.cadence, entry, status, k, closesAt, late)
        }
        val held = inLeague.firstOrNull { e -> e.member != null && !forfeited(e, now) }?.let { e -> rounds.first { it.entry == e } }
        // Joining must still be possible when it opens: inside its join lock a cohort opened then would already be Locked.
        val upcoming = rounds.firstOrNull { it.entry == null && it.start > now && it.start - league.joinLock > now }
        val offer = Offer(slot, member.stake, member.tier, missed, indices.first, indices.last, lost, rounds, held, upcoming)
        return offer.takeIf { it.held != null || it.next != null || it.upcoming != null }
    }
}
