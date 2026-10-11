package holdout.core

import com.solana.publickey.SolanaPublicKey
import holdout.core.MembershipSnapshot.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The comeback offer against the program's own rule (FR-037e, FR-037g, R22):
 * `LeagueProgress::take_comeback` counts a Holdout when `start > missed` and
 * `start <= missed + 14 × day_length`, `missed = deadline_of(days_completed)`.
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
    fun `given a 21-day forfeit on day 2, then exactly two weekly rounds and the one open is joinable`() {
        val r1 = entry(cohort(1, s0 + week))
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), r1), listOf(leagueOf(1)), now, leagueId = 1))
        assertEquals(missed21, offer.missed)
        assertEquals(missed21 + 14 * day, offer.until)
        assertEquals(listOf(s0 + week, s0 + 2 * week), offer.rounds.map { it.start })
        assertEquals(r1, offer.rounds[0].entry)
        assertEquals(CohortStatus.Open, offer.rounds[0].status)
        assertTrue(offer.rounds[0].joinable)
        assertEquals(offer.rounds[0], offer.next)
        assertEquals(offer.rounds[1], offer.after(offer.rounds[0]))
        assertNull(offer.held)
    }

    @Test
    fun `given a 7-day forfeit on day 7, then exactly two rounds`() {
        // days_completed 6 of 7: the missed deadline is the last second before the next cohort starts, which is already
        // running (closed) when the breach shows; the one after it is open.
        val offer = assertNotNull(
            Comeback.offer(listOf(entry(seven, State.Forfeited, days = 6), entry(cohort(0, s0 + week)), entry(cohort(0, s0 + 2 * week))), listOf(leagueOf(0)), s0 + 7 * day + 500, leagueId = 0),
        )
        assertEquals(s0 + week - 1, offer.missed)
        assertEquals(listOf(s0 + week, s0 + 2 * week), offer.rounds.map { it.start })
    }

    @Test
    fun `a round that is not opened yet is named and opens at the previous round's start`() {
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), entry(cohort(1, s0 + week))), listOf(leagueOf(1)), now, 1))
        val second = offer.rounds[1]
        assertNull(second.entry)
        assertNull(second.status)
        assertEquals(s0 + week, second.opensAt)
        assertEquals(false, second.joinable)
    }

    @Test
    fun `locked and running rounds are closed - no Join - and with none open there is no offer`() {
        val league = listOf(leagueOf(1))
        val locked = entry(cohort(1, s0 + week))
        // Past round 1's join cutoff, before its start: Locked.
        val lockedNow = s0 + week - 1_000
        assertEquals(CohortStatus.Locked, Derive.status(locked.cohort, lockedNow))
        // Round 1 locked and round 2 not opened yet: nothing to join, but nothing closed either (FR-037g).
        val waiting = assertNotNull(Comeback.offer(listOf(forfeit21(), locked), league, lockedNow, 1))
        assertNull(waiting.next)
        assertEquals(waiting.rounds[1], waiting.upcoming)
        assertEquals(s0 + 2 * week, waiting.upcoming?.start)
        assertEquals(s0 + week, waiting.upcoming?.opensAt)
        assertNull(waiting.held)
        // Once round 1 has started, round 2 is unopened only if the program has not opened it; a started, unopened round is not upcoming.
        assertNull(Comeback.offer(listOf(forfeit21(), locked), league, s0 + 2 * week + 10, 1), "every round started or closed, none joined")
        val round2 = entry(cohort(1, s0 + 2 * week))
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), locked, round2), league, lockedNow, 1), "round 2 opened and open")
        assertEquals(listOf(CohortStatus.Locked, CohortStatus.Open), offer.rounds.map { it.status })
        assertEquals(offer.rounds[1], offer.next)
        // Round 1 running, round 2 open.
        val runningNow = s0 + week + 1_000
        assertEquals(CohortStatus.Running, Derive.status(locked.cohort, runningNow))
        assertEquals(CohortStatus.Running, Comeback.offer(listOf(forfeit21(), locked, round2), league, runningNow, 1)!!.rounds[0].status)
        // Both closed: no offer.
        assertNull(Comeback.offer(listOf(forfeit21(), locked, round2), league, s0 + 2 * week + 1_000, 1))
    }

    @Test
    fun `the window's boundary seconds follow take_comeback`() {
        val c = twentyOne
        val missed = Derive.deadlineOf(c, 2)
        fun leagueStarting(first: Long, cadence: Long) = LeagueSnapshot(LeagueParams.judged(1, first).copy(cadence = cadence), nextIndex = 5)
        val member = entry(c, State.Forfeited, days = 2)
        fun open(start: Long) = entry(cohort(1, start, index = 1))
        // A start equal to `missed` is not a round; the next at +7 days is.
        val atMissed = Comeback.offer(listOf(member, open(missed + week)), listOf(leagueStarting(missed, week)), now, 1)!!
        assertEquals(listOf(missed + week, missed + 2 * week), atMissed.rounds.map { it.start })
        // A start equal to `missed + 14 × dayLength` is a round.
        val atUntil = Comeback.offer(listOf(member, open(missed + 14 * day)), listOf(leagueStarting(missed, 14 * day)), now, 1)!!
        assertEquals(listOf(missed + 14 * day), atUntil.rounds.map { it.start })
        // One second past is not (and nothing else is left).
        assertNull(Comeback.offer(listOf(member, open(missed + 14 * day + 1)), listOf(leagueStarting(missed, 14 * day + 1)), now, 1))
        // A cohort the app holds starting at the last second counts as a place; one second later it does not.
        val heldAt = Comeback.offer(listOf(member, entry(cohort(1, missed + 14 * day, index = 1), State.Joined)), listOf(leagueStarting(missed + 5 * 14 * day, week)), now, 1)
        assertNotNull(heldAt?.held)
        assertNull(Comeback.offer(listOf(member, entry(cohort(1, missed + 14 * day + 1, index = 1), State.Joined)), listOf(leagueStarting(missed + 5 * 14 * day, week)), now, 1))
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
    }

    @Test
    fun `a forfeit in the comeback Holdout replaces the first and opens its own window (ES-19)`() {
        val league = listOf(leagueOf(1))
        val r1 = cohort(1, s0 + week)
        val joinedAndLost = entry(r1, State.Forfeited, days = 1)
        val r2 = entry(cohort(1, s0 + 2 * week))
        val r3 = entry(cohort(1, s0 + 3 * week))
        val t = s0 + week + 2 * day + 100
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), joinedAndLost, r2, r3), league, t, 1))
        assertEquals(Derive.deadlineOf(r1, 1), offer.missed)
        assertEquals(listOf(s0 + 2 * week, s0 + 3 * week), offer.rounds.map { it.start })
    }

    @Test
    fun `the test leagues share one slot`() {
        val a = LeagueParams.TEST_LEAGUE_MIN
        val testLeague = { id: Int -> LeagueSnapshot(LeagueParams.judged(0, s0).copy(leagueId = id, durationDays = 2, dayLength = 600, cadence = 3_600), nextIndex = 5) }
        fun testCohort(id: Int, start: Long) = CohortSnapshot(id, (start - s0) / 3_600, start, start - 60, 2, 25, 600, 0, chips)
        val forfeit = entry(testCohort(a, s0), State.Forfeited, days = 0)
        val missed = Derive.deadlineOf(forfeit.cohort, 0)
        val nextRound = entry(testCohort(a + 1, s0 + 3_600))
        val offer = assertNotNull(Comeback.offer(listOf(forfeit, nextRound), listOf(testLeague(a), testLeague(a + 1)), missed + 10, a + 1))
        assertEquals(Comeback.slotOf(a), offer.slot)
        assertEquals(nextRound, offer.next?.entry, "another test league's Holdout is the same slot")
        assertEquals(Comeback.slotOf(a + 9), Comeback.slotOf(a))
        // A judged league's forfeit is a slot of its own.
        assertNull(Comeback.offer(listOf(forfeit, nextRound), listOf(testLeague(a), testLeague(a + 1)), missed + 10, leagueId = 0))
    }

    @Test
    fun `a place held in a round is the comeback - no Join, finishing it brings the amount back`() {
        val r1 = entry(cohort(1, s0 + week), State.Joined, tier = 2)
        val offer = assertNotNull(Comeback.offer(listOf(forfeit21(), r1, entry(cohort(1, s0 + 2 * week))), listOf(leagueOf(1)), now, 1))
        assertEquals(r1, offer.held?.entry)
        assertEquals(5uL, offer.amount(r1.member!!.stake), "a fifth of the 25 lost, under the 50 staked")
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
    fun `the amount is min of a fifth of the lost stake and the chip, or a fifth with no chip`() {
        val lostEntry = entry(twentyOne, State.Forfeited, days = 2, tier = 3) // lost 100
        val offer = assertNotNull(Comeback.offer(listOf(lostEntry, entry(cohort(1, s0 + week))), listOf(leagueOf(1)), now, 1))
        assertEquals(20uL, offer.amount())
        assertEquals(Settlement.comebackOwed(100uL, 10uL), offer.amount(10uL))
        assertEquals(10uL, offer.amount(10uL), "capped at the new stake")
        assertEquals(20uL, offer.amount(25uL))
        val odd = offer.copy(lost = 99uL)
        assertEquals(19uL, odd.amount(), "floor(99 × 20 / 100)")
    }

    @Test
    fun `no forfeit, an unknown league, or someone else's cohort gives no offer`() {
        val r1 = entry(cohort(1, s0 + week))
        assertNull(Comeback.offer(listOf(r1), listOf(leagueOf(1)), now, 1))
        assertNull(Comeback.offer(listOf(forfeit21(), r1), emptyList(), now, 1))
        assertTrue(Comeback.offers(emptyList(), emptyList(), now).isEmpty())
    }
}
