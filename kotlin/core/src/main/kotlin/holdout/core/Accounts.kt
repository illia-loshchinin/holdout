package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * The account codecs T030 adds beside the views in [AccountLayout]: `Profile`,
 * and the whole `Membership` the crank pages settlement over. Decode only —
 * nothing off-chain ever writes a program account, so an encoder would be code
 * with no caller. `FixtureReplayTest` pins every account type to
 * `fixtures/accounts.json` field by field instead.
 */

/** `programs/holdout/src/state/profile.rs` — 236 bytes. */
object ProfileLayout {
    const val SIZE = 236

    /** `sha256("account:Profile")[..8]`. */
    val DISCRIMINATOR: ByteArray get() = byteArrayOf(0xb8.toByte(), 0x65, 0xa5.toByte(), 0xbc.toByte(), 0x5f, 0x3f, 0x7f, 0xbc.toByte())

    const val WALLET = 8

    /**
     * Three `LeagueProgress` slots of 33 bytes: 7-day, 21-day, test
     * (`TEST_LEAGUE_SLOT`). Within a slot: four u16 counters, `last_changed`,
     * then the open comeback (FR-037e) — `comeback_stake` (u64),
     * `comeback_league` (u8), `comeback_round` and `comeback_last_round` (u32).
     */
    const val LEAGUES = 40
    const val LEAGUE_SLOTS = 3
    const val LEAGUE_PROGRESS_SIZE = 33
    const val TEST_LEAGUE_SLOT = 2
    const val RUNNING = 139
    const val PENDING = 179

    /** Still stamped by the program, informational only: the faucet has no daily limit per wallet, so nothing reads it. */
    const val LAST_FAUCET_DAY = 227
    const val BUMP = 235

    /** The wallet key, for a `memcmp` that finds one member's profile among all of them. */
    const val WALLET_OFFSET = WALLET
}

/**
 * A wallet's `Profile` (data-model §1.5): its per-league record,
 * and the cohort it is running in and the one it waits in the lobby of.
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
) {
    data class LeagueProgress(
        val cleared: Int,
        val forfeited: Int,
        val currentStreak: Int,
        val longestStreak: Int,
        val lastChanged: Long,
        /**
         * The stake of the latest **counted** forfeit in this league while its
         * comeback is open; `0` once made good or released (FR-037e). Its pledge,
         * 10 % of it ([Settlement.comebackPledge]), sits in the league's comeback
         * reserve meanwhile.
         */
        val comebackStake: ULong = 0uL,
        /** The league that holds the pledge: every test league shares one slot, so the slot alone does not say. */
        val comebackLeague: Int = 0,
        /** The first and last cohort index of that league whose finishing makes the comeback good ([Settlement.comebackRounds]). */
        val comebackRound: Long = 0,
        val comebackLastRound: Long = 0,
    ) {
        val played: Int get() = cleared + forfeited

        /** Whether the pledge is still open in [leagueId] (the program's `open_pledge_in`). */
        fun comebackOpenIn(leagueId: Int): Boolean = comebackStake > 0uL && comebackLeague == leagueId

        /**
         * Whether finishing cohort [index] of [leagueId] would make the comeback good —
         * the program's `comeback_on_finish`, without using the record up.
         */
        fun comebackOpenFor(leagueId: Int, index: Long): Boolean = comebackOpenIn(leagueId) && index in comebackRound..comebackLastRound
    }

    data class Running(val cohort: SolanaPublicKey, val end: Long)

    data class Pending(val cohort: SolanaPublicKey, val start: Long, val end: Long)

    /** This wallet's record in [leagueId]'s slot: 0, 1, or the shared test slot for ids ≥ 100. */
    fun progress(leagueId: Int): LeagueProgress = leagues[slotOf(leagueId)]

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
                    LeagueProgress(r.u16(at), r.u16(at + 2), r.u16(at + 4), r.u16(at + 6), r.i64(at + 8), r.u64(at + 16), r.u8(at + 24), r.u32(at + 25), r.u32(at + 29))
                },
                running = if (runningCohort == none) null else Running(runningCohort, r.i64(l.RUNNING + 32)),
                pending = if (pendingCohort == none) null else Pending(pendingCohort, r.i64(l.PENDING + 32), r.i64(l.PENDING + 40)),
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
