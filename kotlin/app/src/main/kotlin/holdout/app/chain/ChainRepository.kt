package holdout.app.chain

import android.util.Log
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
import kotlinx.coroutines.delay

private const val LOG_TAG = "Chain"

/** The deployment this build talks to (`app/build.gradle.kts`). Devnet, always. */
object Deployment {
    val programId: SolanaPublicKey = SolanaPublicKey.from(BuildConfig.PROGRAM_ID)
    val rpcUrl: String = BuildConfig.RPC_URL

    /**
     * Devnet through any node — the public one or a hosted one with its own key
     * (`holdout.rpcUrl`), whose host names the cluster — rather than a local validator.
     */
    val isDevnet: Boolean = rpcUrl == holdout.core.Rpc.DEVNET || runCatching { java.net.URI(rpcUrl).host }.getOrNull().orEmpty().contains("devnet")

    /** What the member is told failed to answer: devnet, or in a local test build the local validator. */
    val networkName: String = if (isDevnet) "Devnet" else "The local validator"

    /** The app's client for [rpcUrl]: every call the cluster refuses or does not answer is logged with its reason. */
    fun rpc(): Rpc = Rpc(rpcUrl, onFailure = { method, reason -> Log.w(LOG_TAG, "$method failed: $reason") })

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

class ChainRepository(
    private val rpc: Rpc = Deployment.rpc(),
    private val programId: SolanaPublicKey = Deployment.programId,
    /** A monotonic clock in milliseconds: how old the settings read and the last cohorts read are. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    /** The wait before a failed call is asked once more: `delay` except under test. */
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val log: (String) -> Unit = { Log.w(LOG_TAG, it) },
) : ChainReads {
    /** `Config` as last read, and when (R8): it changes rarely, so it is read on the first pass and then every [SETTINGS_MAX_AGE_MILLIS]. */
    @Volatile private var keptConfig: Pair<Long, ConfigSnapshot>? = null

    /** The leagues as last read, when, and which cohorts had settled then (see [load]). */
    private class KeptLeagues(val readAt: Long, val leagues: List<LeagueSnapshot>, val settled: Set<SolanaPublicKey>)

    @Volatile private var keptLeagues: KeptLeagues? = null

    /** The last cohorts read that answered, and when: the leaderboard's first read reuses it while it is fresh. */
    @Volatile private var lastCohorts: Pair<Long, List<Pair<SolanaPublicKey, CohortSnapshot>>>? = null

    override suspend fun load(wallet: SolanaPublicKey?): ChainView? {
        val now = clock()
        val config =
            keptConfig?.takeIf { now - it.first < SETTINGS_MAX_AGE_MILLIS }?.second ?: when (val read = twice({ it is Rpc.Answer.Ok }) { rpc.account(Pdas.config(programId)) }) {
                // No account at all: Holdout is not live here. An account that
                // does not decode is a failed read, not an absent one — the last
                // snapshot stays rather than an empty view saying "not live".
                is Rpc.Answer.Ok -> (read.value ?: return ChainView(null, emptyList(), null, 0uL, 0L)).let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: return failed("config does not decode") }
                else -> return failed("config: $read")
            }.also { keptConfig = now to it }
        val cohorts = twice { rpc.cohorts(programId) } ?: return failed("cohorts")
        lastCohorts = clock() to cohorts
        // The leagues are read again once they are old, when a cohort names one not read yet, or when a
        // cohort has settled since: a settlement is what moves a league's boost and comeback reserves.
        val settled = cohorts.filter { it.second.settled }.map { it.first }.toSet()
        val leagues =
            keptLeagues?.takeIf { k -> now - k.readAt < SETTINGS_MAX_AGE_MILLIS && k.settled == settled && cohorts.all { (_, c) -> k.leagues.any { it.params.leagueId == c.leagueId } } }?.leagues
                ?: (twice { rpc.leagues(programId) } ?: return failed("leagues")).also { keptLeagues = KeptLeagues(now, it, settled) }
        val mine = wallet?.let { twice { rpc.walletMemberships(programId, it) } ?: return failed("the wallet's memberships") }.orEmpty().associateBy({ it.cohort }, { it.member })
        // A read that failed is unknown, not absent: no profile or a zero balance would
        // be shown as real (faucet offered to a funded wallet, a join without its
        // running cohort), so the whole view waits for the next refresh instead.
        val profile =
            wallet?.let { w ->
                when (val read = twice({ it is Rpc.Answer.Ok }) { rpc.profile(programId, w) }) {
                    is Rpc.Answer.Ok -> read.value
                    else -> return failed("the wallet's profile: $read")
                }
            }
        val skr = wallet?.let { twice { rpc.tokenAmount(Pdas.associatedToken(it, config.skrMint)) } ?: return failed("the wallet's SKR") } ?: 0uL
        val lamports = wallet?.let { twice { rpc.balance(it) } ?: return failed("the wallet's SOL") } ?: 0L
        val holdouts =
            cohorts
                .map { (address, c) -> Holdout(address, c, leagues.firstOrNull { it.params.leagueId == c.leagueId }, mine[address]) }
                .sortedWith(compareBy({ it.cohort.leagueId }, { it.cohort.index }))
        return ChainView(config, holdouts, profile, skr, lamports)
    }

    /** [read], asked once more after [RETRY_MILLIS] when it did not answer: one dropped call does not cost the whole pass. */
    private suspend fun <T> twice(answered: (T) -> Boolean = { it != null }, read: suspend () -> T): T {
        val first = read()
        if (answered(first)) return first
        pause(RETRY_MILLIS)
        return read()
    }

    /** Logs which read left the view unknown: the member is told only that the chain can't be reached. */
    private fun failed(read: String): ChainView? {
        log("chain view not loaded: $read failed")
        return null
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
        // The snapshot's own cohorts read, when it is recent, is as good a "before" as a new one: it was read before the profiles.
        val before = lastCohorts?.takeIf { clock() - it.first <= COHORTS_REUSE_MILLIS }?.second ?: rpc.cohorts(programId) ?: return null
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

    companion object {
        /** How long `Config` and the leagues are kept before they are read again (R8). */
        const val SETTINGS_MAX_AGE_MILLIS = 600_000L

        /** The wait before a failed call of the snapshot is asked once more. */
        const val RETRY_MILLIS = 1_500L

        /** How old a cohorts read the leaderboard takes as its first read instead of making its own. */
        const val COHORTS_REUSE_MILLIS = 30_000L
    }
}
