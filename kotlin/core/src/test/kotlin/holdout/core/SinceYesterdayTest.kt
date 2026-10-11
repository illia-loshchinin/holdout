package holdout.core

import com.solana.publickey.SolanaPublicKey
import holdout.core.MembershipSnapshot.State.Forfeited
import holdout.core.MembershipSnapshot.State.Joined
import kotlin.test.Test
import kotlin.test.assertEquals

/** "N dropped since yesterday" and "+N SKR since yesterday" on a running Holdout, from the roster and chain time alone (board 5, N5). */
class SinceYesterdayTest {
    private val start = 1_790_571_600L
    private val day = 86_400L
    private val skr = 1_000_000uL

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    private fun cohort(pool: ULong = 0uL, durationDays: Int = 7) = CohortSnapshot(
        leagueId = 0,
        index = 0,
        start = start,
        joinCutoff = start - 3_600,
        durationDays = durationDays,
        repsPerDay = 25,
        dayLength = day,
        exercise = 0,
        chips = listOf(10uL * skr, 25uL * skr, 50uL * skr, 100uL * skr),
        memberCount = 10,
        pool = pool,
    )

    private fun member(n: Int, days: Int, state: MembershipSnapshot.State = Joined, stake: ULong = 100uL * skr) = MembershipSnapshot(key(n), 3, stake, start - 7_200, days, state)

    /** Day 3 (index 2) has run for an hour; days 0 and 1 are complete for those who did them. */
    private val now = start + 2 * day + 3_600

    @Test
    fun `a member whose deadline fell in the last day dropped since yesterday`() {
        val c = cohort()
        // Missed day 1's deadline (start + 2 days - 1) an hour ago: out, and it fell inside the last day.
        val dropped = member(1, days = 1)
        val standing = member(2, days = 2)
        val s = Derive.sinceYesterday(c, listOf(dropped, standing), now)
        assertEquals(1, s.dropped)
    }

    @Test
    fun `a member who dropped more than a day ago is not counted`() {
        val c = cohort()
        // Missed day 0's deadline (start + 1 day - 1), a day and an hour before now: older than a day.
        val old = member(1, days = 0)
        val s = Derive.sinceYesterday(c, listOf(old, member(2, days = 2)), now)
        assertEquals(0, s.dropped)
    }

    @Test
    fun `a recorded forfeit counts like an unrecorded breach`() {
        val c = cohort()
        val recorded = member(1, days = 1, state = Forfeited)
        val unrecorded = member(2, days = 1)
        val s = Derive.sinceYesterday(c, listOf(recorded, unrecorded, member(3, days = 2)), now)
        assertEquals(2, s.dropped)
    }

    @Test
    fun `nobody is out on the first day`() {
        val c = cohort()
        val s = Derive.sinceYesterday(c, listOf(member(1, days = 0), member(2, days = 0)), start + 3_600)
        assertEquals(0, s.dropped)
        assertEquals(0uL, s.shareGain(member(1, days = 0)))
    }

    @Test
    fun `a standing member's share grew by what the day's forfeits added`() {
        val c = cohort()
        val me = member(2, days = 2)
        val other = member(3, days = 2)
        val gone = member(1, days = 1)
        val s = Derive.sinceYesterday(c, listOf(gone, me, other), now)
        // Pool now = 70 % of 100 SKR = 70 SKR, shared by two equal standing stakes: 35 each.
        // A day ago the pool was empty: nothing to share.
        assertEquals(35uL * skr, s.shareGain(me))
    }

    @Test
    fun `the gain is the difference of two shares, not the share`() {
        // A recorded earlier forfeit already filled 70 SKR of the pool before yesterday.
        val earlier = member(9, days = 0, state = Forfeited)
        val c = cohort(pool = 70uL * skr)
        val me = member(2, days = 2)
        val other = member(3, days = 2)
        val gone = member(1, days = 1)
        val s = Derive.sinceYesterday(c, listOf(earlier, gone, me, other), now)
        // Now: (70 + 70) / 2 = 70 each. A day ago: 70 SKR over three standing stakes of 100, a third for me.
        assertEquals(70uL * skr - 70uL * skr * 100uL / 300uL, s.shareGain(me))
    }

    @Test
    fun `a member who is out gains nothing`() {
        val c = cohort()
        val gone = member(1, days = 1)
        val s = Derive.sinceYesterday(c, listOf(gone, member(2, days = 2)), now)
        assertEquals(0uL, s.shareGain(gone))
    }

    @Test
    fun `nobody dropping means no gain`() {
        val c = cohort()
        val me = member(2, days = 2)
        val s = Derive.sinceYesterday(c, listOf(me, member(3, days = 2)), now)
        assertEquals(0, s.dropped)
        assertEquals(0uL, s.shareGain(me))
    }
}
