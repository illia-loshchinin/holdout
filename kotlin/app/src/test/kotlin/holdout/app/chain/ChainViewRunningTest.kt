package holdout.app.chain

import com.solana.publickey.SolanaPublicKey
import holdout.core.CohortSnapshot
import holdout.core.Derive
import holdout.core.LeagueParams
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A running round nobody is in and nobody can still join, even late, is listed nowhere (FR-062a):
 * not in Standing's "Running now" ([ChainView.runningNotMine]) and not in the connect screen's
 * live list ([ChainView.live]), so neither reads its roster. An empty round still inside its first
 * day stays, since a comeback member may join it late (FR-037e); a round with anyone in it stays.
 */
class ChainViewRunningTest {
    private val skr = 1_000_000uL

    /** A Hackathon round: three 600-second days, join lock 0, so it runs right after its lobby closes. */
    private val dayLength = 600L
    private val start = 1_791_176_400L

    private fun round(n: Int, roundStart: Long, members: Long) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { n.toByte() }),
        cohort = CohortSnapshot(
            102,
            n.toLong(),
            roundStart,
            roundStart,
            3,
            5,
            dayLength,
            0,
            listOf(10uL * skr, 25uL * skr, 50uL * skr, 100uL * skr),
            memberCount = members,
            form = LeagueParams.FORM_KNEES_ALLOWED,
        ),
        league = null,
        membership = null,
    )

    private fun view(vararg h: Holdout) = ChainView(null, h.toList(), null, 0uL, 0L)

    @Test
    fun `an empty round past its first day is hidden from both lists`() {
        val now = start + 2 * dayLength
        val empty = round(1, start, members = 0)
        val v = view(empty)
        assertEquals(emptyList(), v.runningNotMine(now))
        assertEquals(emptyList(), v.live(now))
    }

    @Test
    fun `an empty round still in its first day stays - a comeback member may join it late`() {
        val empty = round(1, start, members = 0)
        val v = view(empty)
        val lastLateSecond = Derive.deadlineOf(empty.cohort, 0)
        assertEquals(listOf(empty), v.runningNotMine(start))
        assertEquals(listOf(empty), v.runningNotMine(lastLateSecond))
        assertEquals(listOf(empty), v.live(lastLateSecond))
        assertEquals(emptyList(), v.runningNotMine(lastLateSecond + 1), "gone the second late joining closes")
    }

    @Test
    fun `a round with anyone in it stays for its whole run`() {
        val now = start + 3 * dayLength - 1
        val crowded = round(1, start, members = 10)
        val v = view(crowded)
        assertEquals(listOf(crowded), v.runningNotMine(now))
        assertEquals(listOf(crowded), v.live(now))
    }

    @Test
    fun `the census case - three empty rounds of a lobby leave only the joinable ones`() {
        // Rounds every 600 s: at `now` #20 is on day one, #19 and #18 are past it; #21 is the open lobby.
        val now = start + 2 * dayLength + 30
        val r18 = round(18, start, members = 0)
        val r19 = round(19, start + dayLength, members = 0)
        val r20 = round(20, start + 2 * dayLength, members = 0)
        val r21 = round(21, start + 3 * dayLength, members = 0)
        val v = view(r18, r19, r20, r21)
        assertEquals(listOf(r20), v.runningNotMine(now))
        assertEquals(listOf(r20, r21), v.live(now))
    }
}
