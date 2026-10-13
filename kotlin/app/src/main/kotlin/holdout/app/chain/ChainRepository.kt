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
import holdout.core.MembershipAccount
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

    /** The counters a pulse watches: a change in any means the roster, and the memberships in it, moved (FR-071). */
    val counters: List<Any> get() = countersOf(cohort)

    companion object {
        /** See [counters]: member count, forfeits recorded, members counted, settled. A submit moves none of them. */
        fun countersOf(c: CohortSnapshot): List<Any> = listOf(c.memberCount, c.forfeitsRecorded, c.counted, c.settled)
    }
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

    /**
     * [load], reading again what an action of the wallet's or a Check again may have changed and a
     * pulse keeps between changes — the wallet's memberships and its own settled cohorts (FR-071).
     */
    suspend fun loadFresh(wallet: SolanaPublicKey?): ChainView? = load(wallet)

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
    /** `Config` as last read, and when (R8): it changes rarely, so it is read on the first pass and then every [CONFIG_MAX_AGE_MILLIS]. */
    @Volatile private var keptConfig: Pair<Long, ConfigSnapshot>? = null

    /** The leagues as last read, when, and how far each cohort's settlement had gone then (see [load]). */
    private class KeptLeagues(val readAt: Long, val leagues: List<LeagueSnapshot>, val settlement: Map<SolanaPublicKey, List<Any>>)

    @Volatile private var keptLeagues: KeptLeagues? = null

    /**
     * The settled cohorts, kept: a pulse reads only the cohorts not settled yet (FR-071). [readAt] is the
     * last read of every cohort; [open] the cohorts the last pulse found not settled, so one that has
     * left the list since is fetched once and kept.
     */
    private class KeptCohorts(val readAt: Long, val settled: Map<SolanaPublicKey, CohortSnapshot>, val open: Set<SolanaPublicKey>)

    @Volatile private var keptCohorts: KeptCohorts? = null

    /** The wallet's memberships as last read, when, and the counters of the cohorts they are in then (see [load]). */
    private class KeptMemberships(val wallet: SolanaPublicKey, val readAt: Long, val memberships: List<MembershipAccount>, val counters: Map<SolanaPublicKey, List<Any>>)

    @Volatile private var keptMemberships: KeptMemberships? = null

    /** The last cohorts read that answered, and when: the leaderboard's first read reuses it while it is fresh. */
    @Volatile private var lastCohorts: Pair<Long, List<Pair<SolanaPublicKey, CohortSnapshot>>>? = null

    override suspend fun load(wallet: SolanaPublicKey?): ChainView? = read(wallet, fresh = false)

    override suspend fun loadFresh(wallet: SolanaPublicKey?): ChainView? = read(wallet, fresh = true)

    /**
     * One snapshot (FR-071): on a pulse with nothing changed, the cohorts not settled yet and one
     * `getMultipleAccounts` for the wallet — everything else is kept until the counters that move it do,
     * or until it is old. [fresh] reads the wallet's memberships and its own settled cohorts as well.
     */
    private suspend fun read(wallet: SolanaPublicKey?, fresh: Boolean): ChainView? {
        val now = clock()
        val config =
            keptConfig?.takeIf { now - it.first < CONFIG_MAX_AGE_MILLIS }?.second ?: when (val read = twice({ it is Rpc.Answer.Ok }) { rpc.account(Pdas.config(programId)) }) {
                // No account at all: Holdout is not live here. An account that
                // does not decode is a failed read, not an absent one — the last
                // snapshot stays rather than an empty view saying "not live".
                is Rpc.Answer.Ok -> (read.value ?: return ChainView(null, emptyList(), null, 0uL, 0L)).let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() ?: return failed("config does not decode") }
                else -> return failed("config: $read")
            }.also { keptConfig = now to it }
        val ownBefore = keptMemberships?.takeIf { it.wallet == wallet }?.memberships.orEmpty().map { it.cohort }.toSet()
        val cohorts = cohorts(now, refetch = if (fresh) ownBefore else emptySet()) ?: return failed("cohorts")
        lastCohorts = clock() to cohorts
        // The leagues are read again once they are old, when a cohort names one not read yet, or when any
        // cohort's settlement has moved since — a page counted, the cohort finalized, a boost or a comeback
        // refund drawn or paid: settlement is what moves a league's boost and comeback reserves, page by page.
        val settlement = cohorts.associate { (address, c) -> address to settlementOf(c) }
        val leagues =
            keptLeagues?.takeIf { k -> now - k.readAt < SETTINGS_MAX_AGE_MILLIS && k.settlement == settlement && cohorts.all { (_, c) -> k.leagues.any { it.params.leagueId == c.leagueId } } }?.leagues
                ?: (twice { rpc.leagues(programId) } ?: return failed("leagues")).also { keptLeagues = KeptLeagues(now, it, settlement) }
        val byAddress = cohorts.toMap()
        val memberships =
            wallet?.let { w ->
                val kept = keptMemberships?.takeIf { it.wallet == w }
                // Kept until a cohort the wallet is in moves (a forfeit recorded, a member counted, the cohort settled),
                // until it is old, or until the wallet acts: only those change a membership the wallet did not sign.
                val keep = !fresh && kept != null && now - kept.readAt < MEMBERSHIPS_MAX_AGE_MILLIS && kept.counters == countersOf(kept.memberships, byAddress)
                if (keep) {
                    kept!!.memberships
                } else {
                    (twice { rpc.walletMemberships(programId, w) } ?: return failed("the wallet's memberships")).also { keptMemberships = KeptMemberships(w, now, it, countersOf(it, byAddress)) }
                }
            }.orEmpty()
        val mine = memberships.associateBy({ it.cohort }, { it.member })
        // A read that failed is unknown, not absent: no profile or a zero balance would
        // be shown as real (faucet offered to a funded wallet, a join without its
        // running cohort), so the whole view waits for the next refresh instead.
        var profile: ProfileSnapshot? = null
        var skr = 0uL
        var lamports = 0L
        if (wallet != null) {
            val accounts = twice { rpc.multipleAccounts(listOf(Pdas.profile(programId, wallet), Pdas.associatedToken(wallet, config.skrMint), wallet)) } ?: return failed("the wallet's accounts")
            val (profileAccount, tokenAccount, walletAccount) = accounts
            profile = profileAccount?.let { runCatching { ProfileSnapshot.decode(it.data) }.getOrNull() ?: return failed("the wallet's profile does not decode") }
            skr = tokenAccount?.let { Rpc.tokenAmountOf(it.data) ?: return failed("the wallet's SKR does not decode") } ?: 0uL
            lamports = walletAccount?.lamports ?: 0L
        }
        val holdouts =
            cohorts
                .map { (address, c) -> Holdout(address, c, leagues.firstOrNull { it.params.leagueId == c.leagueId }, mine[address]) }
                .sortedWith(compareBy({ it.cohort.leagueId }, { it.cohort.index }))
        return ChainView(config, holdouts, profile, skr, lamports)
    }

    /**
     * Every cohort: the ones not settled yet read now, the settled ones kept — all of them read whole
     * when none are kept or the kept ones are [FULL_READ_MILLIS] old. A cohort that has left the
     * not-settled list since the last pulse, and each of [refetch] that is settled, is fetched once.
     */
    private suspend fun cohorts(now: Long, refetch: Set<SolanaPublicKey>): List<Pair<SolanaPublicKey, CohortSnapshot>>? {
        val kept = keptCohorts?.takeIf { now - it.readAt < FULL_READ_MILLIS }
        if (kept == null) {
            val all = twice { rpc.cohorts(programId) } ?: return null
            keptCohorts = KeptCohorts(now, all.filter { it.second.settled }.toMap(), all.filterNot { it.second.settled }.map { it.first }.toSet())
            return all
        }
        val open = twice { rpc.unsettledCohorts(programId) } ?: return null
        val openNow = open.map { it.first }.toSet()
        val fetch = ((kept.open - openNow) + refetch.filter { it in kept.settled }).toList()
        val settled = kept.settled.toMutableMap()
        if (fetch.isNotEmpty()) {
            val accounts = twice { rpc.multipleAccounts(fetch) } ?: return null
            for ((address, account) in fetch.zip(accounts)) {
                // A cohort whose account is gone is dropped; one that does not decode is a failed read.
                if (account == null) settled.remove(address) else settled[address] = runCatching { CohortSnapshot.decode(account.data) }.getOrNull() ?: return null
            }
        }
        keptCohorts = KeptCohorts(kept.readAt, settled.filterValues { it.settled }, openNow)
        return settled.filterValues { it.settled }.toList() + open
    }

    /** The counters of the cohorts [memberships] are in that move a membership the wallet did not sign. */
    private fun countersOf(memberships: List<MembershipAccount>, cohorts: Map<SolanaPublicKey, CohortSnapshot>): Map<SolanaPublicKey, List<Any>> = memberships.associate { m -> m.cohort to (cohorts[m.cohort]?.let(Holdout::countersOf) ?: emptyList()) }

    /** The fields of [c] that move when its settlement moves a league's reserves. */
    private fun settlementOf(c: CohortSnapshot): List<Any> = listOf(c.counted, c.settled, c.noFinisherBoost, c.boostReceived, c.comebackOwed, c.comebackFunded, c.comebackClaimed)

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
        /** How long the leagues are kept, at the longest, before they are read again (R8). */
        const val SETTINGS_MAX_AGE_MILLIS = 600_000L

        /** How long `Config` is kept before it is read again: it changes only by the admin. */
        const val CONFIG_MAX_AGE_MILLIS = 3_600_000L

        /** How long the settled cohorts are kept before every cohort is read whole again. */
        const val FULL_READ_MILLIS = 600_000L

        /** How long the wallet's memberships are kept, at the longest, when nothing moved them. */
        const val MEMBERSHIPS_MAX_AGE_MILLIS = 300_000L

        /** The wait before a failed call of the snapshot is asked once more. */
        const val RETRY_MILLIS = 1_500L

        /** How old a cohorts read the leaderboard takes as its first read instead of making its own. */
        const val COHORTS_REUSE_MILLIS = 30_000L
    }
}
