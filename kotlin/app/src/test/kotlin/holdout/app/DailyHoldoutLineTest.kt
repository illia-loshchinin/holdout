package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.home.DailyHoldoutLine
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.ProfileSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Daily 25's way into a Holdout (H35): the member's own money, never a promise. */
class DailyHoldoutLineTest {
    private val now = 1_000_000L
    private val skr = 1_000_000uL

    private fun open(start: Long, form: Int = LeagueParams.FORM_KNEES_ALLOWED, leagueId: Int = 0, joined: Long = 14) = CohortSnapshot(leagueId, 1, start, start - 3_600, 7, 25, 86_400, 0, listOf(10uL * skr, 25uL * skr, 50uL * skr, 100uL * skr), memberCount = joined, form = form)

    private fun profile(comebackStake: ULong = 0uL, from: Long = 0, until: Long = 0) = ProfileSnapshot(
        SolanaPublicKey(ByteArray(32)),
        listOf(ProfileSnapshot.LeagueProgress(0, 1, 0, 0, from, comebackStake, from, until)) + List(2) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) },
        null,
        null,
        ProfileSnapshot.NEVER_CLAIMED,
    )

    @Test
    fun `given a lost stake whose comeback window covers the next Holdout, then the line names what comes back`() {
        val line = DailyHoldoutLine.of(listOf(open(now + 90_000)), profile(comebackStake = 25uL * skr, from = now - 86_400, until = now + 1_000_000), now)
        assertEquals("Finish the Easy · 7-day Holdout starting in 1d 1h and up to 5 SKR of the stake you lost comes back.", line)
    }

    @Test
    fun `given no comeback, then the line is the next Holdout to join`() {
        val line = DailyHoldoutLine.of(listOf(open(now + 200_000, form = LeagueParams.FORM_TOES), open(now + 90_000)), profile(), now)
        assertEquals("Next: Easy · 7-day Holdout, starting in 1d 1h · 14 joined.", line)
    }

    @Test
    fun `given a comeback whose window closed before the next Holdout starts, then it is not offered`() {
        val line = DailyHoldoutLine.of(listOf(open(now + 90_000)), profile(comebackStake = 25uL * skr, from = now - 86_400, until = now + 1_000), now)
        assertEquals("Next: Easy · 7-day Holdout, starting in 1d 1h · 14 joined.", line)
    }

    @Test
    fun `given nothing open to join, then there is no line`() {
        assertNull(DailyHoldoutLine.of(emptyList(), profile(), now))
    }
}
