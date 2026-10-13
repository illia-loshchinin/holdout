package holdout.app.chain

import com.solana.publickey.SolanaPublicKey
import holdout.app.BuildConfig
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.Journey
import holdout.core.Leaderboard
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Pdas
import holdout.core.ProfileSnapshot
import holdout.core.Rpc
import holdout.core.Stage

/** The deployment this build talks to (`app/build.gradle.kts`). Devnet, always. */
object Deployment {
    val programId: SolanaPublicKey = SolanaPublicKey.from(BuildConfig.PROGRAM_ID)
    val rpcUrl: String = BuildConfig.RPC_URL

    /** What the member is told failed to answer: devnet, or in a local test build the local validator. */
    val networkName: String = networkNameOf(rpcUrl)

    /**
     * Devnet for any endpoint reached by name — the public one or a provider's (`holdout.rpcUrl`) —
     * and the local validator only for one on this machine or the local network, by address.
     */
    fun networkNameOf(url: String): String {
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBeforeLast(':').removeSurrounding("[", "]")
        val local = host == "localhost" || host.all { it.isDigit() || it == '.' } || ':' in host
        return if (local) "The local validator" else "Devnet"
    }

    /** A test league (FR-044a): the short test cohort, labelled wherever it appears. */
    fun isTestLeague(leagueId: Int): Boolean = leagueId >= holdout.core.LeagueParams.TEST_LEAGUE_MIN
}

/** One cohort with the connected wallet's place in it. */
data class Holdout(
    val address: SolanaPublicKey,
    val cohort: CohortSnapshot,
    val league: LeagueSnapshot?,
    val membership: MembershipSnapshot?,
) {
    val isTest: Boolean get() = Deployment.isTestLeague(cohort.leagueId)

    fun status(now: Long): CohortStatus = Derive.status(cohort, now)

    fun stage(now: Long): Stage = Journey.stage(cohort, membership, now)
}

/**
 * Everything a Holdout screen reads, in one snapshot (research R8): the
 * program's `Config`, every league and cohort, the wallet's memberships and
 * profile, and its balances. Null fields mean the program is not deployed or
 * not initialised yet, which the screens say rather than hide.
 */
data class ChainView(
    val config: ConfigSnapshot?,
    val holdouts: List<Holdout>,
    val profile: ProfileSnapshot?,
    /** SKR in base units. */
    val skr: ULong,
    val lamports: Long,
) {
    /** Cohorts taking members now — what home and the connect screen offer (FR-053). */
    fun openToJoin(now: Long): List<Holdout> = holdouts.filter { it.status(now) == CohortStatus.Open && it.membership == null }

    /**
     * What the app offers to join: [openToJoin] minus every Holdout the program would refuse the wallet now
     * (a lobby place already held, a running Holdout that ends after it starts, FR-006a, FR-006b), so a Join
     * shown is a Join that goes through. The program re-checks all of it.
     */
    fun joinable(now: Long): List<Holdout> = openToJoin(now).filter { joinBlocker(it, now) == null }

    /**
     * Why the program would refuse the wallet a place in [h] at [now], or null when it would take it (FR-006a, FR-006b).
     * The wallet's own comeback round is taken after its joining closed, until the end of its first day (FR-037e).
     */
    fun joinBlocker(h: Holdout, now: Long): Journey.JoinBlocker? = Journey.joinBlocker(h.cohort, profile, runningEnd(now), now, runningInBreach = runningInBreach(now), lateJoin = comebackRound(now, h)?.lateJoin == true)

    /** The round of the wallet's comeback offer that is [h], if it is one (FR-037e). */
    fun comebackRound(now: Long, h: Holdout): Comeback.Round? = comebackOffer(now, h.cohort.leagueId)?.rounds?.firstOrNull { it.entry?.address == h.address }

    /** Whether the wallet waits in a lobby, open or locked: its one lobby place is taken, so no other join is offered (FR-006a). */
    fun holdsLobbyPlace(now: Long): Boolean = holdouts.any { it.membership != null && it.stage(now).let { s -> s == Stage.Lobby || s == Stage.LobbyLocked } }

    /**
     * Running Holdouts the wallet is not in — Home's read-only "Running now"
     * list, so any connected wallet, with or without a Seeker, can watch a
     * Holdout thin (FR-062a, FR-003c, constitution XII).
     */
    fun runningNotMine(now: Long): List<Holdout> = holdouts.filter { it.status(now) == CohortStatus.Running && it.membership == null }

    /** Cohorts the wallet is in and has not claimed out of — what home leads with. */
    fun mine(now: Long): List<Holdout> = holdouts.filter { h -> h.membership != null && h.stage(now) !is Stage.Claimed }

    /** Holdouts the wallet finished and claimed out of — kept reachable, for their detail and history. */
    fun claimed(now: Long): List<Holdout> = holdouts.filter { h -> h.membership != null && h.stage(now) is Stage.Claimed }

    /** Cohorts running or open, with or without the wallet — the connect screen's live list (FR-001a). */
    fun live(now: Long): List<Holdout> = holdouts.filter { it.status(now) == CohortStatus.Open || it.status(now) == CohortStatus.Running }

    /** The end of the cohort in the profile's running slot, for the overlap check (FR-006b). */
    fun runningEnd(now: Long): Long? = Journey.runningCohortFor(profile, now)?.let { r -> holdouts.firstOrNull { it.address == r }?.cohort?.let(Derive::end) }

    /**
     * What a join of [target] at [now] sends for the profile's running slot: the record of an unrecorded breach first,
     * or the running cohort to name (FR-037g, ES-17). When [target] is the wallet's first comeback round it also names
     * the forfeited cohort, which is what lets the program take the join after the round's cutoff (FR-037e).
     */
    fun joinPlan(now: Long, target: Holdout? = null): Journey.JoinPlan {
        val running = Journey.runningCohortFor(profile, now)
        val h = running?.let { r -> holdouts.firstOrNull { it.address == r } }
        val offer = target?.let { comebackOffer(now, it.cohort.leagueId) }
        val comeback = offer?.takeIf { o -> o.rounds.firstOrNull()?.entry?.address == target.address }?.forfeited?.address
        return Journey.joinPlan(profile, h?.let { Comeback.Entry(it.address, it.cohort, it.membership) }, now, comeback)
    }

    /**
     * Whether the cohort in the profile's running slot is one the wallet is in breach of, its forfeit
     * not recorded yet, which a join frees by sending the record with it (FR-037g, ES-17).
     */
    fun runningInBreach(now: Long): Boolean = joinPlan(now).runningInBreach

    private fun comebackEntries(): List<Comeback.Entry> = holdouts.map { Comeback.Entry(it.address, it.cohort, it.membership) }

    private fun comebackLeagues(): List<LeagueSnapshot> = holdouts.mapNotNull { it.league }.distinctBy { it.params.leagueId }

    /** What the wallet can still win back of stakes it lost, per level and length (FR-037g); empty when nothing is offered. */
    fun comebackOffers(now: Long): List<Comeback.Offer> = Comeback.offers(comebackEntries(), comebackLeagues(), now, profile)

    /** The comeback offer of the slot [leagueId] belongs to, if any. */
    fun comebackOffer(now: Long, leagueId: Int): Comeback.Offer? = Comeback.offer(comebackEntries(), comebackLeagues(), now, leagueId, profile)
}

/** The chain reads the staked side of the app makes: [ChainRepository] on a phone, a fake in the view model's tests (005 T019). */
interface ChainReads {
    /** Null when the cluster cannot be reached — unknown, never empty (R8). */
    suspend fun load(wallet: SolanaPublicKey?): ChainView?

    suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>?

    suspend fun leaderboard(): Leaderboard.Snapshot?
}

class ChainRepository(private val rpc: Rpc = Rpc(Deployment.rpcUrl), private val programId: SolanaPublicKey = Deployment.programId) : ChainReads {
    override suspend fun load(wallet: SolanaPublicKey?): ChainView? {
        val config =
            when (val read = rpc.account(Pdas.config(programId))) {
                // No account at all: Holdout is not live here. An account that
                // does not decode is a failed read, not an absent one — the last
                // snapshot stays rather than an empty view saying "not live".
                is Rpc.Answer.Ok -> (read.value ?: return ChainView(null, emptyList(), null, 0uL, 0L)).let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: return null }
                else -> return null
            }
        val leagues = rpc.leagues(programId) ?: return null
        val cohorts = rpc.cohorts(programId) ?: return null
        val mine = wallet?.let { rpc.walletMemberships(programId, it) ?: return null }.orEmpty().associateBy({ it.cohort }, { it.member })
        // A read that failed is unknown, not absent: no profile or a zero balance would
        // be shown as real (faucet offered to a funded wallet, a join without its
        // running cohort), so the whole view waits for the next refresh instead.
        val profile = wallet?.let { w -> (rpc.profile(programId, w) as? Rpc.Answer.Ok ?: return null).value }
        val skr = wallet?.let { rpc.tokenAmount(Pdas.associatedToken(it, config.skrMint)) ?: return null } ?: 0uL
        val lamports = wallet?.let { rpc.balance(it) ?: return null } ?: 0L
        val holdouts =
            cohorts
                .map { (address, c) -> Holdout(address, c, leagues.firstOrNull { it.params.leagueId == c.leagueId }, mine[address]) }
                .sortedWith(compareBy({ it.cohort.leagueId }, { it.cohort.index }))
        return ChainView(config, holdouts, profile, skr, lamports)
    }

    /** The roster of one cohort (FR-062), for the roster screen. */
    override suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = rpc.memberships(programId, cohort)

    /**
     * What the league leaderboards read (FR-060, R8): every cohort, every
     * profile, every forfeit not yet counted, then every cohort again, so a
     * count or forfeit landing between the calls is seen rather than mixed
     * into the board. Null when any read failed.
     */
    override suspend fun leaderboard(): Leaderboard.Snapshot? {
        val before = rpc.cohorts(programId) ?: return null
        val profiles = rpc.profiles(programId) ?: return null
        val forfeits = rpc.uncountedForfeits(programId) ?: return null
        val after = rpc.cohorts(programId)?.map { it.second } ?: return null
        // A forfeit in a cohort the first read did not hold changes the cohort list, which holds the board anyway.
        val leagueOf = before.associate { (address, cohort) -> address to cohort.leagueId }
        val uncounted = forfeits
            .mapNotNull { f -> leagueOf[f.cohort]?.let { it to f.member.wallet } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
        return Leaderboard.Snapshot(before.map { it.second }, profiles, after, uncounted)
    }

    suspend fun chainTime(): Rpc.ChainTime? = rpc.chainTime()
}
