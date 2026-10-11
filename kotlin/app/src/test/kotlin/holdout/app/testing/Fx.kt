package holdout.app.testing

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot

/**
 * Small Holdouts for the Standing and Holdouts screens' tests: a cohort with a
 * start and a length, members with a state, and the clock set where the test
 * wants it. Chain time only (constitution VI); no screen reads a wall clock.
 */
internal object Fx {
    const val START = 1_791_176_400L
    const val DAY = 86_400L

    val me = SolanaPublicKey(ByteArray(32) { 0x11 })

    fun key(n: Int) = SolanaPublicKey(ByteArray(32) { (n * 7 + it).toByte() })

    fun member(
        wallet: SolanaPublicKey = me,
        days: Int = 0,
        state: MembershipSnapshot.State = MembershipSnapshot.State.Joined,
        stake: ULong = 1_250_000_000uL,
        comeback: ULong = 0uL,
    ) = MembershipSnapshot(wallet, 0, stake, START - 10 * DAY, days, state, comebackEntitled = comeback)

    fun cohort(
        start: Long = START,
        days: Int = 7,
        joined: Long = 12,
        leagueId: Int = if (days == 7) LeagueParams.LEAGUE_7D else LeagueParams.LEAGUE_21D,
        form: Int = if (days == 7) LeagueParams.FORM_KNEES_ALLOWED else LeagueParams.FORM_TOES,
        dayLength: Long = DAY,
        pool: ULong = 0uL,
        held: ULong = 0uL,
        settled: Boolean = false,
        finishers: Long = 0,
        share: ULong = 0uL,
    ) = CohortSnapshot(
        leagueId = leagueId,
        index = 1,
        start = start,
        joinCutoff = start - 3_600,
        durationDays = days,
        repsPerDay = 25,
        dayLength = dayLength,
        exercise = 0,
        chips = listOf(10_000_000uL, 25_000_000uL, 50_000_000uL, 100_000_000uL),
        memberCount = joined,
        heldStake = held,
        pool = pool,
        settled = settled,
        finisherCount = finishers,
        finishersPerTier = listOf(finishers, 0, 0, 0),
        sharePerTier = listOf(share, 0uL, 0uL, 0uL),
        form = form,
    )

    fun holdout(
        cohort: CohortSnapshot,
        membership: MembershipSnapshot? = null,
        boostReserve: ULong? = null,
        n: Int = cohort.index.toInt() + cohort.leagueId * 10,
    ) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { (n + 1).toByte() }),
        cohort = cohort,
        league = boostReserve?.let {
            LeagueSnapshot(LeagueParams(cohort.leagueId, cohort.durationDays, 25, 0, cohort.form, cohort.start, cohort.dayLength, cohort.dayLength, 0), 2, boostReserve = it)
        },
        membership = membership,
    )

    /** [standing] members still going ([going] days in), then [out] who forfeited after [lasted] days. [me] is the first of the standing. */
    fun roster(standing: Int, out: Int, lasted: Int = 3, finished: Boolean = false, going: Int = 4): List<MembershipSnapshot> {
        val state = if (finished) MembershipSnapshot.State.Finished else MembershipSnapshot.State.Joined
        val inPlay = (0 until standing).map { member(if (it == 0) me else key(it), days = if (finished) 7 else going, state = state) }
        val dropped = (0 until out).map { member(key(100 + it), days = lasted, state = MembershipSnapshot.State.Forfeited) }
        return inPlay + dropped
    }
}
