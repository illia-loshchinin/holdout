package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * The comeback offer, worked out on the phone from the member's own forfeited
 * membership the moment a deadline passes unmet (FR-037g, research R22,
 * data-model §5 "Comeback offer"). The program is unchanged: it writes the
 * comeback record only when the forfeited cohort settles, by which time joining
 * has closed for most Holdouts inside the window, so the app applies the same
 * rule itself — once here, mirroring `LeagueProgress::take_comeback` and
 * `settle_count`, so the offer and the payout follow one rule (FR-037e).
 *
 * The rule: with `missed = deadline_of(days_completed)` of the latest forfeit of
 * a progress slot, a Holdout of that slot counts when
 * `start > missed && start <= missed + 14 × day_length`. A slot is a league, and
 * every test league shares one (`Profile::slot_of`). Only the latest forfeit
 * counts, a finished Holdout inside the window uses the offer up, and a forfeit
 * recorded by the crank and one still unrecorded ([Derive.inBreach]) count alike
 * (FR-038a). Pure: chain accounts and chain time only (constitution II, VI).
 */
object Comeback {
    /** One cohort with the connected wallet's membership in it, if any — all the offer reads. */
    data class Entry(val address: SolanaPublicKey, val cohort: CohortSnapshot, val member: MembershipSnapshot?)

    /**
     * One scheduled start inside the window (`firstStart + k × cadence`).
     * [entry] is its cohort once the program has opened it, [status] that
     * cohort's status; before then a round is only named, and it opens at
     * [opensAt], the previous round's start.
     */
    data class Round(val start: Long, val opensAt: Long, val entry: Entry?, val status: CohortStatus?) {
        /** Taking a place here is possible now: opened, joining open, no place held in it. */
        val joinable: Boolean get() = entry != null && status == CohortStatus.Open && entry.member == null
    }

    /**
     * What a member who lost a stake can still win back in one progress slot.
     * [rounds] are the scheduled starts in the window, in order; [held] is the
     * round the member already holds a place in (lobby or running), if any.
     */
    data class Offer(
        val slot: Int,
        /** The latest forfeited stake. */
        val lost: ULong,
        /** The tier the lost stake was staked on. */
        val tier: Int,
        /** The missed deadline: `deadline_of(days_completed)` of the forfeited cohort. */
        val missed: Long,
        /** The last instant a cohort may start and still be the comeback: `missed + 14 × day_length`. */
        val until: Long,
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

        /** The round scheduled right after [round] inside the window, if any. */
        fun after(round: Round): Round? = rounds.getOrNull(rounds.indexOf(round) + 1)

        /**
         * What finishing a Holdout staked on [chip] brings back, "up to" because the
         * comeback reserve may fall short: `min(floor(lost × 20 ÷ 100), chip)`, and
         * with no chip chosen yet `floor(lost × 20 ÷ 100)` (FR-037e).
         */
        fun amount(chip: ULong? = null): ULong = Settlement.comebackOwed(lost, chip ?: ULong.MAX_VALUE)
    }

    /** The offer for the forfeit screen of the cohort at [address]: only the latest forfeit of a slot carries it, an older one's screen shows none. */
    fun Offer?.forScreenOf(address: SolanaPublicKey): Offer? = this?.takeIf { it.forfeited.address == address }

    /** The offer in the slot of [leagueId], or null (see [offers]). */
    fun offer(holdouts: List<Entry>, leagues: List<LeagueSnapshot>, now: Long, leagueId: Int): Offer? = offers(holdouts, leagues, now).firstOrNull { it.slot == ProfileSnapshot.slotOf(leagueId) }

    /**
     * Per progress slot, the offer from the member's latest forfeit there. A slot
     * has none when the comeback is used up (a finished Holdout inside the
     * window), or when nothing is left to join or finish: every round of the window
     * closed for joining without the member in one. A round the program has not
     * opened yet has not closed, so an [Offer.upcoming] one keeps the offer
     * (FR-037g), unless it is already inside its join lock. The league of the forfeit must be
     * among [leagues]: its first start and cadence give the rounds, with those of every other
     * league of the same slot.
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
        if (leagues.none { it.params.leagueId == lost.cohort.leagueId }) return null
        val missed = missedOf(lost)
        val until = missed + Settlement.COMEBACK_WINDOW_DAYS * lost.cohort.dayLength
        val inWindow = holdouts.filter { ProfileSnapshot.slotOf(it.cohort.leagueId) == slot && it.cohort.start > missed && it.cohort.start <= until }

        // Used up: the member finished a Holdout that counts (settle_count takes the record then).
        if (inWindow.any { e -> e.member?.let { finished(it, e.cohort) } == true }) return null

        // The league that schedules the rounds: the forfeit's own, or for the test slot, which every
        // test league shares, the newest test league — the only one still opening cohorts (FR-044a);
        // an older one winds down, and its schedule would name rounds that never open (FR-037g).
        val slotLeagues = leagues.map { it.params }.filter { ProfileSnapshot.slotOf(it.leagueId) == slot }.maxByOrNull { it.leagueId }.let(::listOfNotNull)
        val scheduled = slotLeagues.flatMap { l -> scheduledStarts(l, missed, until).map { start -> start to l } }.distinctBy { it.first }.sortedBy { it.first }
        val rounds = scheduled.map { (start, l) ->
            val entry = inWindow.filter { it.cohort.start == start }.let { at -> at.firstOrNull { it.cohort.leagueId == lost.cohort.leagueId } ?: at.firstOrNull() }
            Round(start, start - l.cadence, entry, entry?.let { Derive.status(it.cohort, now) })
        }
        val joinLockOf = scheduled.associate { (start, l) -> start to l.joinLock }
        val placeEntry = inWindow.firstOrNull { e -> e.member?.let { !forfeited(e, now) } == true }
        val held = placeEntry?.let { e -> rounds.firstOrNull { it.entry == e } ?: Round(e.cohort.start, e.cohort.start - (leagues.firstOrNull { it.params.leagueId == e.cohort.leagueId }?.params?.cadence ?: 0L), e, Derive.status(e.cohort, now)) }
        // Joining must still be possible when it opens: inside its join lock a cohort opened then would already be Locked.
        val upcoming = rounds.firstOrNull { it.entry == null && it.start > now && it.start - (joinLockOf[it.start] ?: 0L) > now }
        val offer = Offer(slot, member.stake, member.tier, missed, until, lost, rounds, held, upcoming)
        return offer.takeIf { it.held != null || it.next != null || it.upcoming != null }
    }

    /** `firstStart + k × cadence` inside `(missed, until]` — the starts the program may open; never before the league's first. */
    private fun scheduledStarts(league: LeagueParams, missed: Long, until: Long): List<Long> {
        if (league.cadence <= 0) return emptyList()
        val k0 = if (missed < league.firstStart) 0L else (missed - league.firstStart) / league.cadence + 1
        return generateSequence(league.firstStart + k0 * league.cadence) { it + league.cadence }.takeWhile { it <= until }.toList()
    }
}
