package holdout.app.testutil

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.core.CohortSnapshot
import holdout.core.ConfigSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.ProfileSnapshot

/** Chain data for the shell's JVM tests: a Spark/Habit Holdout at a start time, the member's place in it, a view of several. */
object ChainFixtures {
    const val SKR = 1_000_000uL
    const val DAY = 86_400L
    const val START = 1_791_176_400L
    val me = SolanaPublicKey(ByteArray(32) { 0x11 })
    private val key = SolanaPublicKey(ByteArray(32) { 5 })
    val config = ConfigSnapshot(key, key, key, key, key, key, false, emptyList(), 0, 0, 0uL, 0uL, 0uL, 0uL, 0, 0uL)

    fun holdout(
        n: Int,
        cohortStart: Long,
        member: MembershipSnapshot? = null,
        form: Int = LeagueParams.FORM_KNEES_ALLOWED,
        days: Int = 7,
        leagueId: Int = LeagueParams.LEAGUE_7D,
        reps: Int = 25,
        dayLength: Long = DAY,
        cutoff: Long = cohortStart - 3_600,
    ) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { n.toByte() }),
        cohort = CohortSnapshot(leagueId, n.toLong(), cohortStart, cutoff, days, reps, dayLength, 0, listOf(500uL * SKR, 1_250uL * SKR, 2_500uL * SKR, 5_000uL * SKR), memberCount = 14, heldStake = 12_500uL * SKR, form = form),
        league = if (leagueId < LeagueParams.TEST_LEAGUE_MIN) LeagueSnapshot(LeagueParams.judged(leagueId, cohortStart), nextIndex = 3) else null,
        membership = member,
    )

    fun member(days: Int, state: MembershipSnapshot.State = MembershipSnapshot.State.Joined, joinedAt: Long = START - 10 * DAY) = MembershipSnapshot(me, 1, 1_250uL * SKR, joinedAt, days, state)

    fun view(vararg h: Holdout, cfg: ConfigSnapshot? = config, lamports: Long = 0L, profile: ProfileSnapshot? = null) = ChainView(cfg, h.toList(), profile, 0uL, lamports)
}
