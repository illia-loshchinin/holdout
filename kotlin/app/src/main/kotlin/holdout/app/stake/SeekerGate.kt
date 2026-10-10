package holdout.app.stake

import holdout.app.storage.SeekerConfirmation
import holdout.app.storage.Store
import holdout.core.GenesisCheck

/** Where a join stands with the Seeker Genesis Token check (spec FR-003b–FR-003d). */
enum class SeekerStatus {
    /** Asking mainnet. */
    Checking,

    /** The wallet holds a Seeker Genesis Token: the stake confirm opens. */
    Confirmed,

    /** Mainnet answered and the wallet holds none: the Seeker-exclusive sheet (ES-0c). */
    NotSeeker,

    /** Mainnet did not answer: try again, never the Seeker-exclusive sheet (ES-0d). */
    CouldNotCheck,
}

/**
 * The gate every join passes before the stake confirm opens (spec FR-003b,
 * research R17). Staked Holdouts are Seeker-exclusive; on devnet, where
 * Seeker Genesis Tokens do not exist, the app checks the wallet's token on
 * mainnet. A confirmation is remembered per wallet and never asked again, so
 * an outage never blocks a confirmed owner; an absence is never remembered,
 * so a wallet that gains a token is let in on its next try (FR-003d).
 */
class SeekerGate(
    private val store: Store,
    private val check: suspend (wallet: String) -> GenesisCheck,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * False only in a debug build made with `-Pholdout.seekerGate=off`
     * (`BuildConfig.SEEKER_GATE`, research R17): every wallet then goes
     * straight to the stake confirm and mainnet is never asked. A release
     * build always checks.
     */
    private val enabled: Boolean = true,
) {
    /** Whether [wallet] goes straight on: confirmed on this phone, or the gate is off. */
    fun remembered(wallet: String): Boolean = !enabled || store.seekerConfirmation(wallet) != null

    /** [wallet]'s standing: remembered, or asked of mainnet now. */
    suspend fun status(wallet: String): SeekerStatus {
        if (remembered(wallet)) return SeekerStatus.Confirmed
        return when (val answer = check(wallet)) {
            is GenesisCheck.Holds -> {
                store.setSeekerConfirmation(wallet, SeekerConfirmation(answer.mint.base58(), clock(), answer.tokenAccount.base58()))
                SeekerStatus.Confirmed
            }
            GenesisCheck.None -> SeekerStatus.NotSeeker
            is GenesisCheck.Unknown -> SeekerStatus.CouldNotCheck
        }
    }

    companion object {
        /** Where the Seeker-exclusive sheet sends a member who wants one (FR-003c). */
        const val SEEKER_STORE_URL = "https://store.solanamobile.com/"
    }
}
