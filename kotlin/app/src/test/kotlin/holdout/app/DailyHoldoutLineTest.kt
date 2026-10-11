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

    private fun offers(vararg entries: Comeback.Entry, at: Long = now) = Comeback.offers(entries.toList(), listOf(league), at)

    @Test
    fun `given a forfeited membership not yet settled, then the line names the round, its start and the time left to join`() {
        val line = DailyHoldoutLine.of(listOf(round1), offers(unrecorded, entry(round1, null)), now)
        assertEquals("Get back up to 5 SKR of your lost stake: finish the Spark · 7-day starting in 1d 0h. Joining closes in 23h 00m.", line)
    }

    @Test
    fun `given a place held in the round, then the line says finishing it brings up to N back`() {
        val joined = entry(round1, MembershipSnapshot.State.Joined, tier = 2)
        val line = DailyHoldoutLine.of(emptyList(), offers(unrecorded, joined), now)
        assertEquals("You're in the Spark · 7-day Holdout. Finish it and up to 5 SKR of your lost stake comes back.", line)
    }

    @Test
    fun `given every round closed without a place, then there is no comeback line`() {
        // Round 1 is running and round 2 locked: nothing is left to join.
        val at = round2.start - 1_000
        val line = DailyHoldoutLine.of(listOf(open(at + 90_000)), offers(unrecorded, entry(round1, null), entry(round2, null), at = at), at)
        assertEquals("Next: Spark · 7-day Holdout, starting in 1d 1h · 14 joined.", line)
    }

    @Test
    fun `given round 1 locked and the next not opened yet, then the line says when it opens and starts`() {
        val at = round1.start - 1_000
        val line = DailyHoldoutLine.of(emptyList(), offers(unrecorded, entry(round1, null), at = at), at)
        assertEquals("The next Spark · 7-day Holdout opens for joining in 16m 40s and starts in 7d 0h. Finish it and up to 5 SKR of your lost stake comes back.", line)
    }

    @Test
    fun `given the next round due but not opened by automation yet, then it opens shortly, never in 0m 00s`() {
        val at = round1.start + 60
        val line = DailyHoldoutLine.of(emptyList(), offers(unrecorded, entry(round1, null), at = at), at)
        assertEquals("The next Spark · 7-day Holdout opens for joining shortly and starts in 6d 23h. Finish it and up to 5 SKR of your lost stake comes back.", line)
        assertEquals(line, DailyHoldoutLine.comebackLine(offers(unrecorded, entry(round1, null), at = at), at))
    }

    /** A second slot: a 21-day Hard forfeit whose missed deadline is also `now − 1`, with its own round open to join at `now + 2 days`. */
    private val hardLeague = LeagueSnapshot(LeagueParams.judged(1, now + 2 * day), nextIndex = 3)
    private val hardForfeited = CohortSnapshot(1, 0, now - 21 * day, now - 21 * day - 3_600, 21, 25, day, 0, chips, form = LeagueParams.FORM_TOES)
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
        // Round 1 locked and the next not opened (upcoming) in slot 0; slot 1 still joinable: the joinable one.
        val at = round1.start - 1_000
        val mixed = twoSlots(unrecorded, entry(round1, null), hardUnrecorded, hardRound, at = at)
        assertTrue(assertNotNull(DailyHoldoutLine.comebackLine(mixed, at)).contains("finish the Habit"))
    }

    @Test
    fun `the line comes with a flag saying it is the comeback`() {
        val comeback = assertNotNull(DailyHoldoutLine.line(listOf(round1), offers(unrecorded, entry(round1, null)), now))
        assertTrue(comeback.comeback)
        val plain = assertNotNull(DailyHoldoutLine.line(listOf(round1), emptyList(), now))
        assertFalse(plain.comeback)
        assertEquals(DailyHoldoutLine.of(listOf(round1), emptyList(), now), plain.text)
        assertNull(DailyHoldoutLine.line(emptyList(), emptyList(), now))
    }

    @Test
    fun `given no forfeit, then the line is the next Holdout to join`() {
        val line = DailyHoldoutLine.of(listOf(open(now + 200_000, form = LeagueParams.FORM_TOES), open(now + 90_000)), emptyList(), now)
        assertEquals("Next: Spark · 7-day Holdout, starting in 1d 1h · 14 joined.", line)
    }

    @Test
    fun `given nothing open to join, then there is no line`() {
        assertNull(DailyHoldoutLine.of(emptyList(), emptyList(), now))
    }

    @Test
    fun `no member-facing line contains the word league (FR-041)`() {
        val lines = listOf(
            DailyHoldoutLine.of(listOf(round1), offers(unrecorded, entry(round1, null)), now),
            DailyHoldoutLine.of(emptyList(), offers(unrecorded, entry(round1, MembershipSnapshot.State.Joined)), now),
            DailyHoldoutLine.of(listOf(round1), emptyList(), now),
        )
        assertTrue(lines.all { it != null && !it.contains("league", ignoreCase = true) })
        assertFalse(lines.any { it.isNullOrEmpty() })
    }
}
