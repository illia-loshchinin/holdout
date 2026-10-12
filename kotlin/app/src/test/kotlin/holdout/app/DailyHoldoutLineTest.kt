package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.home.DailyHoldoutLine
import holdout.core.CohortSnapshot
import holdout.core.Comeback
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Daily 25's way into a Holdout (H35): the member's own money, never a promise. */
class DailyHoldoutLineTest {
    private val now = 1_000_000L
    private val day = 86_400L
    private val skr = 1_000_000uL
    private val chips = listOf(10uL * skr, 25uL * skr, 50uL * skr, 100uL * skr)
    private val wallet = SolanaPublicKey(ByteArray(32) { 1 })

    private fun open(start: Long, form: Int = LeagueParams.FORM_KNEES_ALLOWED, leagueId: Int = 0, joined: Long = 14) = CohortSnapshot(leagueId, 1, start, start - 3_600, 7, 25, day, 0, chips, memberCount = joined, form = form)

    /** A 7-day Easy Holdout that began six days before [now], so day 6 was missed a second ago: `missed = now − 1`. */
    private val forfeitedCohort = CohortSnapshot(0, 0, now - 6 * day, now - 6 * day - 3_600, 7, 25, day, 0, chips, form = LeagueParams.FORM_KNEES_ALLOWED)
    private val league = LeagueSnapshot(LeagueParams.judged(0, forfeitedCohort.start), nextIndex = 3)

    private fun entry(c: CohortSnapshot, state: MembershipSnapshot.State?, days: Int = 0, tier: Int = 1) = Comeback.Entry(
        SolanaPublicKey(ByteArray(32) { (c.start % 251).toByte() }),
        c,
        state?.let { MembershipSnapshot(wallet, tier, chips[tier], c.start - day, days, it) },
    )

    /** Day 6 of 7 not done at its deadline `now − 1`: a breach nobody has recorded or settled (progress.comebackStake is still 0). */
    private val unrecorded = entry(forfeitedCohort, MembershipSnapshot.State.Joined, days = 5)
    private val round1 = open(now + day)
    private val round2 = open(now + day + 7 * day)
    private val round1Address = entry(round1, null).address

    private fun offers(vararg entries: Comeback.Entry, at: Long = now) = Comeback.offers(entries.toList(), listOf(league), at)

    @Test
    fun `given a forfeited membership not yet settled, then the line names the round, its start and the time left to join`() {
        val line = DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null)), now)
        assertEquals("Get back 2.5 SKR of your lost stake: finish the Spark · 7-day starting in 1d 0h. Joining closes in 23h 00m.", line)
    }

    @Test
    fun `given a place held in the round, then the line says finishing it brings exactly N back`() {
        val joined = entry(round1, MembershipSnapshot.State.Joined, tier = 2)
        val line = DailyHoldoutLine.comebackLine(offers(unrecorded, joined), now)
        assertEquals("You're in the Spark · 7-day. Finish it and 2.5 SKR of your lost stake comes back.", line)
    }

    @Test
    fun `given the round closed without a place, then there is no comeback line`() {
        // Round 1 started more than a day ago: its first day is over, so it takes nobody late. Nothing is left to join.
        val at = round1.start + day
        val line = DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null), at = at), at)
        assertNull(line)
    }

    @Test
    fun `given round 1 locked, then it is still the member's to join, until the end of its first day`() {
        val at = round1.start - 1_000
        val line = DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null), at = at), at)
        assertEquals("Get back 2.5 SKR of your lost stake: finish the Spark · 7-day starting in 16m 40s. Joining closes in 1d 0h.", line)
    }

    @Test
    fun `given round 1 running on its first day, then the line says it started today and when joining closes`() {
        val at = round1.start + 60
        val line = DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null), at = at), at)
        assertEquals("Get back 2.5 SKR of your lost stake: finish the Spark · 7-day that started today. Joining closes in 23h 59m.", line)
        assertEquals(round1Address, DailyHoldoutLine.nudge(offers(unrecorded, entry(round1, null), at = at), at)?.joinAddress)
    }

    @Test
    fun `given the next round not opened by automation yet, then it opens shortly, never in 0m 00s`() {
        val line = DailyHoldoutLine.comebackLine(offers(unrecorded), now)
        assertEquals("The next Spark · 7-day opens for joining shortly and starts in 1d 0h. Finish it and 2.5 SKR of your lost stake comes back.", line)
    }

    /** A second slot: a 21-day Hard forfeit whose missed deadline is also `now − 1`, with its own round open to join at `now + 2 days`. */
    private val hardForfeited = CohortSnapshot(1, 0, now - 21 * day, now - 21 * day - 3_600, 21, 25, day, 0, chips, form = LeagueParams.FORM_TOES)
    private val hardLeague = LeagueSnapshot(LeagueParams.judged(1, hardForfeited.start).copy(cadence = 23 * day), nextIndex = 3)
    private val hardUnrecorded = entry(hardForfeited, MembershipSnapshot.State.Joined, days = 20)
    private val hardRound = entry(CohortSnapshot(1, 1, now + 2 * day, now + 2 * day - 3_600, 21, 25, day, 0, chips, form = LeagueParams.FORM_TOES), null)

    private fun twoSlots(vararg entries: Comeback.Entry, at: Long = now) = Comeback.offers(entries.toList(), listOf(league, hardLeague), at)

    @Test
    fun `given two offers, then a round to join comes before a place held, even when the place starts first`() {
        val held = entry(round1, MembershipSnapshot.State.Joined, tier = 2)
        val both = twoSlots(unrecorded, held, hardUnrecorded, hardRound)
        assertEquals(2, both.size)
        val line = assertNotNull(DailyHoldoutLine.comebackLine(both, now))
        assertTrue("finish the Habit" in line, line)
    }

    @Test
    fun `given two offers to join, then the soonest start is shown, and a round to join beats one not opened yet`() {
        // Slot 0 has round 1 open at now + 1 day, slot 1 a round at now + 2 days: the soonest.
        val bothOpen = twoSlots(unrecorded, entry(round1, null), hardUnrecorded, hardRound)
        assertTrue(assertNotNull(DailyHoldoutLine.comebackLine(bothOpen, now)).contains("finish the Spark"))
        // Slot 0's round not opened yet (upcoming) while slot 1 is joinable: the joinable one.
        val mixed = twoSlots(unrecorded, hardUnrecorded, hardRound)
        assertTrue(assertNotNull(DailyHoldoutLine.comebackLine(mixed, now)).contains("finish the Habit"))
        // Round 1 locked is still the member's to join late, and it starts first.
        val at = round1.start - 1_000
        val late = twoSlots(unrecorded, entry(round1, null), hardUnrecorded, hardRound, at = at)
        assertTrue(assertNotNull(DailyHoldoutLine.comebackLine(late, at)).contains("finish the Spark"))
    }

    @Test
    fun `a round to join opens the stake sheet on the tier of the lost stake, anything else the Holdouts tab`() {
        val toJoin = assertNotNull(DailyHoldoutLine.nudge(offers(unrecorded, entry(round1, null)), now))
        assertEquals(round1Address, toJoin.joinAddress)
        assertEquals(1, toJoin.tier)
        val held = assertNotNull(DailyHoldoutLine.nudge(offers(unrecorded, entry(round1, MembershipSnapshot.State.Joined)), now))
        assertNull(held.joinAddress)
        assertNull(DailyHoldoutLine.nudge(emptyList(), now))
    }

    @Test
    fun `no member-facing line contains the word league (FR-041)`() {
        val lines = listOf(
            DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null)), now),
            DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, MembershipSnapshot.State.Joined)), now),
            DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null), at = round1.start - 1_000), round1.start - 1_000),
        )
        assertTrue(lines.all { it != null && !it.contains("league", ignoreCase = true) })
        assertFalse(lines.any { it.isNullOrEmpty() })
    }
}
