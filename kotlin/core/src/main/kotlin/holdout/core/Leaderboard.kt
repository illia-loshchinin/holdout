package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * League leaderboards (US-10, FR-060…FR-060c, T112): all-time, one per
 * league, ranked on discipline from each wallet's on-chain `Profile` and
 * nothing else — no server, no device-reported figure, never an amount
 * (FR-063, constitution XIII). The ranking lives only here; the app draws it.
 */
object Leaderboard {

    /** Cohorts a member must have finished in a league before they are ranked (FR-060b). */
    const val RANKED_AFTER = 3

    /**
     * The three boards, one per `Profile` progress slot: the Easy 7-day, the
     * Hard 21-day, and one combined board for every short test league, which
     * all write the same slot (FR-060, FR-044a).
     */
    enum class Board(val slot: Int) {
        EASY_7D(LeagueParams.LEAGUE_7D),
        HARD_21D(LeagueParams.LEAGUE_21D),
        TEST(ProfileLayout.TEST_LEAGUE_SLOT),
        ;

        fun covers(leagueId: Int): Boolean = of(leagueId) == this

        companion object {
            fun of(leagueId: Int): Board = entries.first { it.slot == ProfileSnapshot.slotOf(leagueId) }
        }
    }

    /** One member's line: the figures FR-060 shows, straight from their progress. */
    data class Row(
        val wallet: SolanaPublicKey,
        val cleared: Int,
        val forfeited: Int,
        val currentStreak: Int,
        val longestStreak: Int,
        /** The instant of their latest outcome — a cohort's end or a missed deadline, never a landing time. */
        val lastChanged: Long,
        /** A disclosed scripted member, labelled wherever it appears (FR-064). */
        val scripted: Boolean,
    ) {
        /** Cohorts finished in the league — cleared or forfeited (FR-060a). */
        val played: Int get() = cleared + forfeited

        /** Whole percent, rounded down so nothing short of a perfect record reads 100 (FR-060c). */
        val survivalPercent: Int get() = if (played == 0) 0 else (cleared.toLong() * 100 / played).toInt()

        val cohortsNeeded: Int get() = (RANKED_AFTER - played).coerceAtLeast(0)
    }

    /** Where one wallet stands on a board — the member sees theirs (US-10). */
    sealed interface Standing {
        data class Ranked(val position: Int) : Standing

        data class NotRanked(val cohortsNeeded: Int) : Standing

        /** Never finished a cohort of this league. */
        data class Absent(val cohortsNeeded: Int = RANKED_AFTER) : Standing
    }

    sealed interface View {
        /**
         * One of the board's cohorts is part-way through counting: some of its
         * members' figures are written and others not, and a counted member's
         * previous streak is not on chain to subtract, so the board waits rather
         * than show figures the chain does not support (FR-060a, FR-065).
         */
        data object Updating : View

        data class Ready(val ranked: List<Row>, val notRanked: List<Row>) : View {
            fun standingOf(wallet: SolanaPublicKey): Standing {
                val at = ranked.indexOfFirst { it.wallet == wallet }
                if (at >= 0) return Standing.Ranked(at + 1)
                notRanked.firstOrNull { it.wallet == wallet }?.let { return Standing.NotRanked(it.cohortsNeeded) }
                return Standing.Absent()
            }
        }
    }

    /**
     * The board as of its league's last settled cohort. [cohorts] are every
     * cohort the program holds; only this board's matter, and only one
     * `Settling` with some members already counted holds it (FR-061a keeps
     * that to one per league).
     */
    fun view(board: Board, profiles: List<ProfileSnapshot>, cohorts: List<CohortSnapshot>, now: Long, isScripted: (SolanaPublicKey) -> Boolean): View {
        if (cohorts.any { board.covers(it.leagueId) && midCount(it, now) }) return View.Updating
        return rank(rows(board, profiles, isScripted))
    }

    /**
     * One read of what a board needs: every cohort, then every profile, then
     * every cohort again. Profiles and cohorts come from separate calls, so a
     * count can land between them; reading cohorts on both sides is what lets
     * the board tell (FR-060a, FR-065).
     */
    data class Snapshot(val cohortsBefore: List<CohortSnapshot>, val profiles: List<ProfileSnapshot>, val cohortsAfter: List<CohortSnapshot>)

    /**
     * [view] over a [Snapshot]: also [View.Updating] when any of the board's
     * cohorts moved in its count or settlement between the two cohort reads —
     * the profiles in between may hold some of that count and not the rest.
     */
    fun view(board: Board, snapshot: Snapshot, now: Long, isScripted: (SolanaPublicKey) -> Boolean): View {
        fun progress(cohorts: List<CohortSnapshot>) = cohorts
            .filter { board.covers(it.leagueId) }
            .associate { (it.leagueId to it.index) to (it.counted to it.settled) }
        if (progress(snapshot.cohortsBefore) != progress(snapshot.cohortsAfter)) return View.Updating
        if (snapshot.cohortsBefore.any { board.covers(it.leagueId) && midCount(it, now) }) return View.Updating
        return view(board, snapshot.profiles, snapshot.cohortsAfter, now, isScripted)
    }

    /** Counted in part: settlement has begun writing its members' progress and not finished. */
    fun midCount(c: CohortSnapshot, now: Long): Boolean = Derive.status(c, now) == CohortStatus.Settling && c.counted > 0

    /** Every wallet that has finished at least one cohort of the board's league. */
    fun rows(board: Board, profiles: List<ProfileSnapshot>, isScripted: (SolanaPublicKey) -> Boolean): List<Row> = profiles
        .map { p ->
            val l = p.leagues[board.slot]
            Row(p.wallet, l.cleared, l.forfeited, l.currentStreak, l.longestStreak, l.lastChanged, isScripted(p.wallet))
        }
        .filter { it.played > 0 }

    fun rank(rows: List<Row>): View.Ready {
        val (ranked, notRanked) = rows.partition { it.played >= RANKED_AFTER }
        return View.Ready(ranked.sortedWith(RANKED), notRanked.sortedWith(NOT_RANKED))
    }

    /** Survival rate, highest first, compared exactly: `a.cleared / a.played` vs `b.cleared / b.played` by cross-multiplying. */
    private val bySurvival = Comparator<Row> { a, b -> (b.cleared.toLong() * a.played).compareTo(a.cleared.toLong() * b.played) }

    /**
     * After the leading keys: the longer best streak, then whoever reached the
     * figures first by the outcome's own instant, then wallet address
     * ascending — the same order on every device (FR-060b).
     */
    private val tail: Comparator<Row> = compareByDescending<Row> { it.longestStreak }
        .thenBy { it.lastChanged }
        .thenBy { it.wallet.base58() }

    private val RANKED: Comparator<Row> = bySurvival.thenByDescending { it.played }.then(tail)

    private val NOT_RANKED: Comparator<Row> = compareByDescending<Row> { it.played }.then(bySurvival).then(tail)
}
