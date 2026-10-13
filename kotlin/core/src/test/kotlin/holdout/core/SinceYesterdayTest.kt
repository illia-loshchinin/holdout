package holdout.core

import com.solana.publickey.SolanaPublicKey
import holdout.core.MembershipSnapshot.State.Forfeited
import holdout.core.MembershipSnapshot.State.Joined
import kotlin.test.Test
import kotlin.test.assertEquals

/** "N dropped since yesterday" on a running Holdout, from the roster and chain time alone (board 5, N5). */
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

    private fun since(c: CohortSnapshot, members: List<MembershipSnapshot>, at: Long) = Derive.sinceYesterday(Derive.figures(c, members, at), members)

    /** Day 3 (index 2) has run for an hour; days 0 and 1 are complete for those who did them. */
    private val now = start + 2 * day + 3_600

    @Test
    fun `a member whose deadline fell in the last day dropped since yesterday`() {
        val c = cohort()
        // Missed day 1's deadline (start + 2 days - 1) an hour ago: out, and it fell inside the last day.
        val dropped = member(1, days = 1)
        val standing = member(2, days = 2)
        val s = since(c, listOf(dropped, standing), now)
        assertEquals(1, s.dropped)
    }

    @Test
    fun `a member who dropped more than a day ago is not counted`() {
        val c = cohort()
        // Missed day 0's deadline (start + 1 day - 1), a day and an hour before now: older than a day.
        val old = member(1, days = 0)
        val s = since(c, listOf(old, member(2, days = 2)), now)
        assertEquals(0, s.dropped)
    }

    @Test
    fun `a recorded forfeit counts like an unrecorded breach`() {
        val c = cohort()
        val recorded = member(1, days = 1, state = Forfeited)
        val unrecorded = member(2, days = 1)
        val s = since(c, listOf(recorded, unrecorded, member(3, days = 2)), now)
        assertEquals(2, s.dropped)
    }

    @Test
    fun `nobody is out on the first day`() {
        val c = cohort()
        val s = since(c, listOf(member(1, days = 0), member(2, days = 0)), start + 3_600)
        assertEquals(0, s.dropped)
    }

    @Test
    fun `a roster that all kept up has no one dropped`() {
        val c = cohort()
        val me = member(2, days = 2)
        val s = since(c, listOf(me, member(3, days = 2)), now)
        assertEquals(0, s.dropped)
    }
}
