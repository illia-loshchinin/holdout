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
        assertEquals("Finish the Easy · 7-day Holdout starting in 1d 0h and up to 5 SKR of the stake you lost comes back. Joining closes in 23h 00m.", line)
    }

    @Test
    fun `given a place held in the round, then the line says finishing it brings up to N back`() {
        val joined = entry(round1, MembershipSnapshot.State.Joined, tier = 2)
        val line = DailyHoldoutLine.of(emptyList(), offers(unrecorded, joined), now)
        assertEquals("You're in the Easy · 7-day Holdout. Finish it and up to 5 SKR of the stake you lost comes back.", line)
    }

    @Test
    fun `given every round closed without a place, then there is no comeback line`() {
        // Round 1 is running and round 2 locked: nothing is left to join.
        val at = round2.start - 1_000
        val line = DailyHoldoutLine.of(listOf(open(at + 90_000)), offers(unrecorded, entry(round1, null), entry(round2, null), at = at), at)
        assertEquals("Next: Easy · 7-day Holdout, starting in 1d 1h · 14 joined.", line)
    }

    @Test
    fun `given round 1 locked and the next not opened yet, then the line says when it opens and starts`() {
        val at = round1.start - 1_000
        val line = DailyHoldoutLine.of(emptyList(), offers(unrecorded, entry(round1, null), at = at), at)
        assertEquals("The next Easy · 7-day Holdout opens for joining in 16m 40s and starts in 7d 0h. Finish it and up to 5 SKR of the stake you lost comes back.", line)
    }

    @Test
    fun `given no forfeit, then the line is the next Holdout to join`() {
        val line = DailyHoldoutLine.of(listOf(open(now + 200_000, form = LeagueParams.FORM_TOES), open(now + 90_000)), emptyList(), now)
        assertEquals("Next: Easy · 7-day Holdout, starting in 1d 1h · 14 joined.", line)
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
