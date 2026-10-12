package holdout.core

import com.solana.publickey.SolanaPublicKey
import holdout.core.Comeback.forScreenOf
import holdout.core.MembershipSnapshot.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The comeback offer against the program's own rule (FR-037e, FR-037g, R22):
 * `schedule::comeback_rounds` names the forfeit's own league's cohorts `first..last`,
 * `first` the first to start after `missed = deadline_of(days_completed)`, and the
 * comeback is exactly `min(10 % of the lost stake, the new stake)`.
 */
class ComebackTest {
    private val day = 86_400L
    private val week = 7 * day
    private val s0 = LeagueParams.FIRST_JUDGED_START
    private val wallet = SolanaPublicKey(ByteArray(32) { 1 })
    private val chips = listOf(10uL, 25uL, 50uL, 100uL)

    private fun leagueOf(id: Int, days: Int = if (id == 1) 21 else 7) = LeagueSnapshot(LeagueParams.judged(id, s0).copy(durationDays = days), nextIndex = 5)

    private fun cohort(leagueId: Int, start: Long, days: Int = if (leagueId == 1) 21 else 7, index: Long = (start - s0) / week) = CohortSnapshot(
        leagueId = leagueId, index = index, start = start, joinCutoff = start - 3_600, durationDays = days, repsPerDay = 25, dayLength = day, exercise = 0, chips = chips,
    )

    private fun entry(c: CohortSnapshot, state: State? = null, days: Int = 0, tier: Int = 1, seed: Int = c.index.toInt() + 10 * c.leagueId) = Comeback.Entry(
        SolanaPublicKey(ByteArray(32) { seed.toByte() }),
        c,
        state?.let { MembershipSnapshot(wallet, tier, chips[tier], c.start - day, days, it) },
    )

    private val twentyOne = cohort(1, s0)
    private val seven = cohort(0, s0)

    /** A 21-day forfeit on day 2: missed deadline `s0 + 3 days − 1`. */
    private val missed21 = s0 + 3 * day - 1
    private val now = s0 + 3 * day + 1_000

    private fun forfeit21(state: State = State.Forfeited) = entry(twentyOne, state, days = 2)

    @Test
    fun `given a mid-round miss, then exactly one round, the next weekly one, and it is joinable`() {
        val r1 = entry(cohort(1, s0 + week))
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), r1), listOf(leagueOf(1)), now, leagueId = 1))
        assertEquals(missed21, offer.missed)
        assertEquals(1L..1L, offer.firstRound..offer.lastRound)
        assertEquals(listOf(s0 + week), offer.rounds.map { it.start })
        assertEquals(r1, offer.rounds[0].entry)
        assertEquals(CohortStatus.Open, offer.rounds[0].status)
        assertEquals(r1.cohort.joinCutoff, offer.rounds[0].closesAt, "open: joining closes at the cutoff")
        assertEquals(false, offer.rounds[0].lateJoin)
        assertTrue(offer.rounds[0].joinable)
        assertEquals(offer.rounds[0], offer.next)
        assertNull(offer.after(offer.rounds[0]), "no second round after a mid-round miss")
        assertNull(offer.held)
    }

    @Test
    fun `given a last-day miss, then two rounds, and the first is joinable late until the end of its first day`() {
        val league = listOf(leagueOf(0))
        val lost = entry(seven, State.Forfeited, days = 6)
        val r1 = entry(cohort(0, s0 + week))
        val r2 = entry(cohort(0, s0 + 2 * week))
        // days_completed 6 of 7: the missed deadline is the second before round 1 starts, an hour after its joining closed.
        val justStarted = s0 + week + 500
        val offer = assertNotNull(Comeback.offer(listOf(lost, r1, r2), league, justStarted, leagueId = 0))
        assertEquals(s0 + week - 1, offer.missed)
        assertEquals(1L..2L, offer.firstRound..offer.lastRound)
        assertEquals(listOf(s0 + week, s0 + 2 * week), offer.rounds.map { it.start })
        assertEquals(CohortStatus.Running, offer.rounds[0].status)
        assertTrue(offer.rounds[0].lateJoin && offer.rounds[0].joinable)
        assertEquals(s0 + week + day, offer.rounds[0].closesAt, "day 0 ends at the first day's deadline")
        assertEquals(offer.rounds[0], offer.next)
        assertEquals(offer.rounds[1], offer.after(offer.rounds[0]))
        assertTrue(offer.rounds[1].joinable && !offer.rounds[1].lateJoin)

        // The last second of day 0 still takes the member; the next does not, and the second round is the way back.
        val lastSecond = assertNotNull(Comeback.offer(listOf(lost, r1, r2), league, s0 + week + day - 1, 0))
        assertEquals(lastSecond.rounds[0], lastSecond.next)
        val closed = assertNotNull(Comeback.offer(listOf(lost, r1, r2), league, s0 + week + day, 0))
        assertEquals(false, closed.rounds[0].joinable)
        assertEquals(closed.rounds[1], closed.next)

        // Locked (inside the join lock) is late too; it is the same round.
        val locked = assertNotNull(Comeback.offer(listOf(lost, r1), league, s0 + week - 1_000, 0))
        assertEquals(CohortStatus.Locked, locked.rounds[0].status)
        assertTrue(locked.rounds[0].lateJoin && locked.rounds[0].joinable)
        assertNull(locked.rounds[1].entry)

        // Both rounds closed without the member: no offer.
        assertNull(Comeback.offer(listOf(lost, r1, r2), league, s0 + 2 * week + 1_000, 0))
    }

    @Test
    fun `only the member's first comeback round is joinable late`() {
        val r1 = entry(cohort(1, s0 + week))
        // A mid-round miss names round 1 alone; once it is Locked it is still the member's, until its first day ends.
        val locked = assertNotNull(Comeback.offer(listOf(forfeit21(), r1), listOf(leagueOf(1)), s0 + week - 1_000, 1))
        assertEquals(listOf(s0 + week), locked.rounds.map { it.start }, "the miss named round 1 alone")
        assertTrue(locked.rounds[0].lateJoin)
        // A member already holding a place in the round has nothing to join late.
        val placed = entry(cohort(1, s0 + week), State.Joined)
        assertEquals(false, assertNotNull(Comeback.offer(listOf(forfeit21(), placed), listOf(leagueOf(1)), s0 + week + 1_000, 1)).rounds[0].joinable)
    }

    @Test
    fun `a round that is not opened yet is named and opens at the previous round's start`() {
        val lost = entry(seven, State.Forfeited, days = 6)
        val offer = assertNotNull(Comeback.offer(listOf(lost, entry(cohort(0, s0 + week))), listOf(leagueOf(0)), s0 + week + 500, 0))
        val second = offer.rounds[1]
        assertNull(second.entry)
        assertNull(second.status)
        assertEquals(s0 + week, second.opensAt)
        assertEquals(s0 + 2 * week - 3_600, second.closesAt, "an unopened round will close at its cutoff")
        assertEquals(false, second.joinable)
        assertEquals(second, offer.upcoming)
    }

    @Test
    fun `an unopened round inside its join lock is not upcoming - a cohort opened then would already be Locked`() {
        val league = listOf(leagueOf(1))
        val roundStart = s0 + week
        // Round 1 not opened; one second before its join lock begins it is still to come.
        val before = assertNotNull(Comeback.offer(listOf(forfeit21()), league, roundStart - 3_601, 1))
        assertEquals(roundStart, before.upcoming?.start)
        assertEquals(s0, before.upcoming?.opensAt)
        // At the lock it cannot be joined once opened: not upcoming, and nothing else is left.
        assertNull(Comeback.offer(listOf(forfeit21()), league, roundStart - 3_600, 1))
        assertNull(Comeback.offer(listOf(forfeit21()), league, roundStart - 100, 1))
    }

    @Test
    fun `a recorded forfeit and an unrecorded breach both count`() {
        val r1 = entry(cohort(1, s0 + week))
        val recorded = Comeback.offer(listOf(forfeit21(State.Forfeited), r1), listOf(leagueOf(1)), now, 1)
        val unrecorded = Comeback.offer(listOf(forfeit21(State.Joined), r1), listOf(leagueOf(1)), now, 1)
        assertNotNull(recorded)
        assertNotNull(unrecorded)
        assertEquals(recorded.copy(forfeited = unrecorded.forfeited), unrecorded)
        // Not yet in breach: still on day 3's clock, no offer.
        assertNull(Comeback.offer(listOf(entry(twentyOne, State.Joined, days = 2), r1), listOf(leagueOf(1)), s0 + 2 * day + 10, 1))
    }

    @Test
    fun `of two forfeits the later missed deadline wins`() {
        val league = listOf(leagueOf(1))
        val early = entry(twentyOne, State.Forfeited, days = 2)
        val laterCohort = cohort(1, s0 + 3 * week)
        val later = entry(laterCohort, State.Forfeited, days = 5, tier = 3)
        val r = entry(cohort(1, s0 + 4 * week))
        val offer = assertNotNull(Comeback.offer(listOf(early, later, r), league, s0 + 4 * week - 7_200, 1))
        assertEquals(Derive.deadlineOf(laterCohort, 5), offer.missed)
        assertEquals(chips[3], offer.lost)
        assertEquals(3, offer.tier)
        assertEquals(later, offer.forfeited)
        // Only the screen of the latest forfeit shows the offer; an older forfeit's screen shows none.
        assertEquals(offer, offer.forScreenOf(later.address))
        assertNull(offer.forScreenOf(early.address))
        assertNull(null.forScreenOf(later.address))
    }

    @Test
    fun `a forfeit in the comeback Holdout replaces the first and opens its own rounds (ES-19)`() {
        val league = listOf(leagueOf(1))
        val r1 = cohort(1, s0 + week)
        val joinedAndLost = entry(r1, State.Forfeited, days = 1)
        val r2 = entry(cohort(1, s0 + 2 * week))
        val r3 = entry(cohort(1, s0 + 3 * week))
        val t = s0 + week + 2 * day + 100
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), joinedAndLost, r2, r3), league, t, 1))
        assertEquals(Derive.deadlineOf(r1, 1), offer.missed)
        assertEquals(listOf(s0 + 2 * week), offer.rounds.map { it.start })
    }

    @Test
    fun `another league does not count, even in the slot the test leagues share`() {
        val a = LeagueParams.TEST_LEAGUE_MIN
        fun testLeague(id: Int) = LeagueSnapshot(LeagueParams.judged(0, s0).copy(leagueId = id, durationDays = 2, dayLength = 600, cadence = 3_600, joinLock = 60), nextIndex = 5)
        fun testCohort(id: Int, start: Long) = CohortSnapshot(id, (start - s0) / 3_600, start, start - 60, 2, 25, 600, 0, chips)
        val leagues = listOf(testLeague(a), testLeague(a + 1))
        val forfeit = entry(testCohort(a, s0), State.Forfeited, days = 0)
        val missed = Derive.deadlineOf(forfeit.cohort, 0)
        val ownRound = entry(testCohort(a, s0 + 3_600))
        val otherRound = entry(testCohort(a + 1, s0 + 3_600))
        val offer = assertNotNull(Comeback.offer(listOf(forfeit, ownRound, otherRound), leagues, missed + 10, a))
        assertEquals(ProfileSnapshot.slotOf(a), offer.slot)
        assertEquals(ownRound, offer.next?.entry, "the forfeit's own league's round")
        assertEquals(listOf(s0 + 3_600), offer.rounds.map { it.start })
        assertEquals(offer, Comeback.offer(listOf(forfeit, ownRound, otherRound), leagues, missed + 10, a + 1), "the slot is shared, so either id finds the offer")
        // The other league's cohort is no way back: with only that one open, the own round is merely upcoming.
        val onlyOther = assertNotNull(Comeback.offer(listOf(forfeit, otherRound), leagues, missed + 10, a))
        assertNull(onlyOther.next)
        assertNull(onlyOther.rounds[0].entry)
        assertEquals(onlyOther.rounds[0], onlyOther.upcoming)
        // Nor does finishing it use the offer up.
        val finishedOther = entry(testCohort(a + 1, s0 + 3_600), State.Finished, days = 2)
        assertNotNull(Comeback.offer(listOf(forfeit, ownRound, finishedOther), leagues, missed + 10, a))
        // A judged league's forfeit is a slot of its own.
        assertNull(Comeback.offer(listOf(forfeit, ownRound), leagues, missed + 10, leagueId = 0))
    }

    @Test
    fun `a place held in a round is the comeback - no Join, finishing it brings the amount back`() {
        val r1 = entry(cohort(1, s0 + week), State.Joined, tier = 2)
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), r1, entry(cohort(1, s0 + 2 * week))), listOf(leagueOf(1)), now, 1))
        assertEquals(r1, offer.held?.entry)
        assertEquals(2uL, offer.amount(r1.member!!.stake), "a tenth of the 25 lost, under the 50 staked")
        // Locked or running does not matter: the place is held either way.
        val running = Comeback.offer(listOf(forfeit21(), r1), listOf(leagueOf(1)), s0 + week + 3_000, 1)
        assertEquals(r1, running?.held?.entry)
        assertNull(running?.next)
    }

    @Test
    fun `a finished membership in a round uses the offer up`() {
        val finished = entry(cohort(1, s0 + week), State.Finished, days = 21)
        assertNull(Comeback.offer(listOf(forfeit21(), finished, entry(cohort(1, s0 + 2 * week))), listOf(leagueOf(1)), s0 + 40 * day, 1))
        assertNull(Comeback.offer(listOf(forfeit21(), entry(cohort(1, s0 + week), State.Claimed, days = 21), entry(cohort(1, s0 + 2 * week))), listOf(leagueOf(1)), now, 1))
    }

    @Test
    fun `the amount is exactly min of a tenth of the lost stake and the chip, or a tenth with no chip`() {
        val lostEntry = entry(twentyOne, State.Forfeited, days = 2, tier = 3) // lost 100
        val offer = assertNotNull(Comeback.offer(listOf(lostEntry, entry(cohort(1, s0 + week))), listOf(leagueOf(1)), now, 1))
        assertEquals(10uL, offer.amount())
        assertEquals(Settlement.comebackOwed(100uL, 5uL), offer.amount(5uL))
        assertEquals(5uL, offer.amount(5uL), "capped at the new stake")
        assertEquals(10uL, offer.amount(25uL))
        val odd = offer.copy(lost = 99uL)
        assertEquals(9uL, odd.amount(), "floor(99 × 10 / 100)")
    }

    @Test
    fun `no forfeit, an unknown league, or someone else's cohort gives no offer`() {
        val r1 = entry(cohort(1, s0 + week))
        assertNull(Comeback.offer(listOf(r1), listOf(leagueOf(1)), now, 1))
        assertNull(Comeback.offer(listOf(forfeit21(), r1), emptyList(), now, 1))
        assertTrue(Comeback.offers(emptyList(), emptyList(), now).isEmpty())
    }
}
