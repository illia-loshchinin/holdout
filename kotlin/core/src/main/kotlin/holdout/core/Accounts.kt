package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * The account codecs T030 adds beside the views in [AccountLayout]: `Profile`,
 * and the whole `Membership` the crank pages settlement over. Decode only —
 * nothing off-chain ever writes a program account, so an encoder would be code
 * with no caller. `FixtureReplayTest` pins every account type to
 * `fixtures/accounts.json` field by field instead.
 */

/** `programs/holdout/src/state/profile.rs` — 257 bytes. */
object ProfileLayout {
    const val SIZE = 257

    /** `sha256("account:Profile")[..8]`. */
    val DISCRIMINATOR: ByteArray get() = byteArrayOf(0xb8.toByte(), 0x65, 0xa5.toByte(), 0xbc.toByte(), 0x5f, 0x3f, 0x7f, 0xbc.toByte())

    const val WALLET = 8

    /**
     * Three `LeagueProgress` slots of 40 bytes: 7-day, 21-day, test
     * (`TEST_LEAGUE_SLOT`). Within a slot: four u16 counters, `last_changed`,
     * then the open comeback (H35) — `comeback_stake`, `comeback_from`,
     * `comeback_until`.
     */
    const val LEAGUES = 40
    const val LEAGUE_SLOTS = 3
    const val LEAGUE_PROGRESS_SIZE = 40
    const val TEST_LEAGUE_SLOT = 2
    const val RUNNING = 160
    const val PENDING = 200
    const val LAST_FAUCET_DAY = 248
    const val BUMP = 256

    /** The wallet key, for a `memcmp` that finds one member's profile among all of them. */
    const val WALLET_OFFSET = WALLET
}

/**
 * A wallet's `Profile` (data-model §1.5): its per-league record,
 * the cohort it is running in and the one it waits in the lobby of, and the
 * last faucet day.
 *
 * `running` and `pending` are stored as the program last left them; the
 * program normalises them lazily (a pending cohort that has started moves to
 * running on the next write), so a reader must do the same with [normalised].
 */
data class ProfileSnapshot(
    val wallet: SolanaPublicKey,
    val leagues: List<LeagueProgress>,
    val running: Running?,
    val pending: Pending?,
    /** The global day of the last claim, or `-1` for never (data-model §1.5). */
    val lastFaucetDay: Long,
) {
    data class LeagueProgress(
        val cleared: Int,
        val forfeited: Int,
        val currentStreak: Int,
        val longestStreak: Int,
        val lastChanged: Long,
        /**
         * The stake of the latest **counted** forfeit in this league while it
         * can still earn a comeback; `0` once used or never (FR-037e, H35).
         */
        val comebackStake: ULong = 0uL,
        /** That forfeit's missed deadline: a comeback cohort starts strictly after it. */
        val comebackFrom: Long = 0,
        /** The last instant a cohort may start and still be its comeback. */
        val comebackUntil: Long = 0,
    ) {
        val played: Int get() = cleared + forfeited

        /**
         * Whether finishing a cohort of this league that starts at [cohortStart]
         * would earn a comeback refund — the program's `take_comeback` test,
         * without using the record up.
         */
        fun comebackOpenFor(cohortStart: Long): Boolean = comebackStake > 0uL && cohortStart > comebackFrom && cohortStart <= comebackUntil
    }

    data class Running(val cohort: SolanaPublicKey, val end: Long)

    data class Pending(val cohort: SolanaPublicKey, val start: Long, val end: Long)

    /** This wallet's record in [leagueId]'s slot: 0, 1, or the shared test slot for ids ≥ 100. */
    fun progress(leagueId: Int): LeagueProgress = leagues[slotOf(leagueId)]

    /** Whether the faucet has paid this wallet on global day [today]. */
    fun claimedFaucetOn(today: Long): Boolean = lastFaucetDay == today

    /**
     * The slots as the program would read them at [now]: a pending cohort that
     * has started is the running one (the program's `normalise_slots`).
     */
    fun normalised(now: Long): ProfileSnapshot = if (pending != null && pending.start <= now) {
        copy(running = Running(pending.cohort, pending.end), pending = null)
    } else {
        this
    }

    companion object {
        const val NEVER_CLAIMED = -1L

        /** The program's `Profile::slot_of`: real leagues their own slot, every other id the shared test slot. */
        fun slotOf(leagueId: Int): Int = if (leagueId < ProfileLayout.TEST_LEAGUE_SLOT) leagueId else ProfileLayout.TEST_LEAGUE_SLOT

        fun decode(data: ByteArray): ProfileSnapshot {
            val l = ProfileLayout
            AccountLayout.requireAccount(data, l.SIZE, l.DISCRIMINATOR, "Profile")
            val r = LayoutReader(data)
            val none = SolanaPublicKey(ByteArray(32))
            val runningCohort = r.pubkey(l.RUNNING)
            val pendingCohort = r.pubkey(l.PENDING)
            return ProfileSnapshot(
                wallet = r.pubkey(l.WALLET),
                leagues = List(l.LEAGUE_SLOTS) {
                    val at = l.LEAGUES + it * l.LEAGUE_PROGRESS_SIZE
                    LeagueProgress(r.u16(at), r.u16(at + 2), r.u16(at + 4), r.u16(at + 6), r.i64(at + 8), r.u64(at + 16), r.i64(at + 24), r.i64(at + 32))
                },
                running = if (runningCohort == none) null else Running(runningCohort, r.i64(l.RUNNING + 32)),
                pending = if (pendingCohort == none) null else Pending(pendingCohort, r.i64(l.PENDING + 32), r.i64(l.PENDING + 40)),
                lastFaucetDay = r.i64(l.LAST_FAUCET_DAY),
            )
        }
    }
}

/**
 * A whole `Membership` account: the roster view (which carries
 * `comeback_entitled`) plus the cohort it belongs to and the settlement flag
 * the crank pages `settle_count` by.
 */
data class MembershipAccount(val cohort: SolanaPublicKey, val member: MembershipSnapshot, val counted: Boolean) {
    companion object {
        fun decode(data: ByteArray): MembershipAccount {
            val l = AccountLayout.Membership
            val member = MembershipSnapshot.decode(data)
            val r = LayoutReader(data)
            return MembershipAccount(r.pubkey(l.COHORT), member, r.bool(l.COUNTED))
        }
    }
}
