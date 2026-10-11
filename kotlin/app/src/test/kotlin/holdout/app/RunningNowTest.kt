package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.core.CohortSnapshot
import holdout.core.MembershipSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's "Running now" list (FR-062a): running Holdouts the wallet is not in. */
class RunningNowTest {

    private val start = 1_791_176_400L
    private val wallet = SolanaPublicKey(ByteArray(32) { 0x11 })

    private fun holdout(n: Int, cohortStart: Long, member: Boolean = false) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { n.toByte() }),
        cohort = CohortSnapshot(
            leagueId = 0, index = n.toLong(), start = cohortStart, joinCutoff = cohortStart - 3_600, durationDays = 7, repsPerDay = 25,
            dayLength = 86_400, exercise = 0, chips = listOf(10uL, 25uL, 50uL, 100uL), memberCount = 12,
        ),
        league = null,
        membership = if (member) MembershipSnapshot(wallet, 0, 10uL, cohortStart - 86_400, 0, MembershipSnapshot.State.Joined) else null,
    )

    private fun view(vararg holdouts: Holdout) = ChainView(config = null, holdouts = holdouts.toList(), profile = null, skr = 0uL, lamports = 0L)

    @Test
    fun `lists running Holdouts the wallet is not in, and nothing else`() {
        val now = start + 2 * 86_400
        val running = holdout(1, start)
        val mine = holdout(2, start, member = true)
        val open = holdout(3, start + 7 * 86_400)
        val over = holdout(4, start - 30 * 86_400)

        assertEquals(listOf(running), view(running, mine, open, over).runningNotMine(now))
    }

    @Test
    fun `is empty before anything has started`() {
        assertEquals(emptyList(), view(holdout(1, start)).runningNotMine(start - 86_400))
    }
}
