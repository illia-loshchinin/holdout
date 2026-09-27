package holdout.app.chain

import com.solana.publickey.SolanaPublicKey
import holdout.app.BuildConfig
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.ConfigSnapshot
import holdout.core.Derive
import holdout.core.Journey
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
    val networkName: String = if (rpcUrl == holdout.core.Rpc.DEVNET) "Devnet" else "The local validator"

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
    /** Cohorts taking members now — what the ladder, home and the connect screen offer (FR-053). */
    fun openToJoin(now: Long): List<Holdout> = holdouts.filter { it.status(now) == CohortStatus.Open && it.membership == null }

    /** Cohorts the wallet is in and has not claimed out of — what home leads with. */
    fun mine(now: Long): List<Holdout> = holdouts.filter { h -> h.membership != null && h.stage(now) !is Stage.Claimed }

    /** Cohorts running or open, with or without the wallet — the connect screen's live list (FR-001a). */
    fun live(now: Long): List<Holdout> = holdouts.filter { it.status(now) == CohortStatus.Open || it.status(now) == CohortStatus.Running }

    /** The end of the cohort in the profile's running slot, for the overlap check (FR-006b). */
    fun runningEnd(now: Long): Long? = Journey.runningCohortFor(profile, now)?.let { r -> holdouts.firstOrNull { it.address == r }?.cohort?.let(Derive::end) }
}

class ChainRepository(private val rpc: Rpc = Rpc(Deployment.rpcUrl), private val programId: SolanaPublicKey = Deployment.programId) {
    /** Null when the cluster cannot be reached — unknown, never empty (R8). */
    suspend fun load(wallet: SolanaPublicKey?): ChainView? {
        val config =
            when (val read = rpc.account(Pdas.config(programId))) {
                is Rpc.Answer.Ok -> read.value?.let { runCatching { ConfigSnapshot.decode(it) }.getOrNull() }
                else -> return null
            }
        if (config == null) return ChainView(null, emptyList(), null, 0uL, 0L)
        val leagues = rpc.leagues(programId) ?: return null
        val cohorts = rpc.cohorts(programId) ?: return null
        val mine = wallet?.let { rpc.walletMemberships(programId, it) ?: return null }.orEmpty().associateBy({ it.cohort }, { it.member })
        val profile = wallet?.let { w -> (rpc.profile(programId, w) as? Rpc.Answer.Ok)?.value }
        val skr = wallet?.let { rpc.tokenAmount(Pdas.associatedToken(it, config.skrMint)) } ?: 0uL
        val lamports = wallet?.let { rpc.balance(it) } ?: 0L
        val holdouts =
            cohorts
                .map { (address, c) -> Holdout(address, c, leagues.firstOrNull { it.params.leagueId == c.leagueId }, mine[address]) }
                .sortedWith(compareBy({ it.cohort.leagueId }, { it.cohort.index }))
        return ChainView(config, holdouts, profile, skr, lamports)
    }

    /** The roster of one cohort (FR-062), for the roster screen. */
    suspend fun roster(cohort: SolanaPublicKey): List<MembershipSnapshot>? = rpc.memberships(programId, cohort)

    suspend fun chainTime(): Rpc.ChainTime? = rpc.chainTime()
}
