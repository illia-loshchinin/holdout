package holdout.core

import com.solana.publickey.SolanaPublicKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Where each field sits in the program's accounts, and read-only views over the
 * three the cohort screens and `crank status` need (T032, T034, T036).
 *
 * **Scoped on purpose.** These are decode-only views of `Cohort`, `Membership`
 * and `Config`, written ahead of the full codecs so derived figures and account
 * reads have something to run on. The codecs proper — every account, encode as
 * well as decode, PDAs and instruction builders — are T030 (`Accounts.kt`,
 * `Pdas.kt`, `Instructions.kt`), pinned by the T029 fixtures. When they land they
 * should absorb these views or replace them; the offsets stay the program's.
 *
 * Every offset counts from the start of the account, Anchor's 8-byte
 * discriminator first, then the fields in declaration order with no padding
 * (Borsh). They are checked against `tests/helpers/layout-vectors.json`, the
 * bytes the program's own `state/layout_tests.rs` serializes (T016), so a field
 * the Rust structs gain, lose or reorder fails the Kotlin tests as well.
 *
 * Kotlin types hold each field's whole Rust range: `u8`/`u16` → `Int`,
 * `u32`/`i64` → `Long`, **`u64` → `ULong`** — amounts are unsigned 64-bit on
 * chain and the arithmetic in [Settlement] keeps them that way.
 */
object AccountLayout {
    const val DISCRIMINATOR_SIZE = 8

    /** `programs/holdout/src/state/cohort.rs` — 236 bytes. */
    object Cohort {
        const val SIZE = 236

        /** `sha256("account:Cohort")[..8]`. */
        val DISCRIMINATOR: ByteArray get() = bytes(0x89, 0x11, 0xd5, 0x3d, 0x69, 0x40, 0x90, 0xa9)

        const val LEAGUE_ID = 8
        const val INDEX = 9
        const val START = 13
        const val JOIN_CUTOFF = 21
        const val DURATION_DAYS = 29
        const val REPS_PER_DAY = 30
        const val DAY_LENGTH = 32
        const val EXERCISE = 40
        const val CHIPS = 41
        const val MEMBER_COUNT = 73
        const val HELD_STAKE = 77
        const val POOL = 85
        const val CARRY_OVER_RECEIVED = 93
        const val FORFEITS_RECORDED = 101
        const val COUNTED = 105
        const val FINISHER_COUNT = 109
        const val FINISHERS_PER_TIER = 113
        const val TOTAL_FINISHER_STAKE = 129
        const val SETTLED = 137
        const val SHARE_PER_TIER = 138
        const val ROUNDING_REMAINDER = 170
        const val NO_FINISHER_FEE = 178
        const val CARRY_OVER_SENT = 186
        const val CARRY_OVER_HELD = 194
        const val CARRY_OVER_TARGET = 202
        const val VAULT_BUMP = 234
        const val BUMP = 235
    }

    /** `programs/holdout/src/state/membership.rs` — 93 bytes. */
    object Membership {
        const val SIZE = 93

        /** `sha256("account:Membership")[..8]`. */
        val DISCRIMINATOR: ByteArray get() = bytes(0xe7, 0x8d, 0xb4, 0x62, 0x6d, 0xa8, 0xaf, 0xa6)

        /** The cohort key — what the roster query's `memcmp` matches (R8). */
        const val COHORT = 8
        const val WALLET = 40
        const val TIER = 72
        const val STAKE = 73
        const val JOINED_AT = 81
        const val DAYS_COMPLETED = 89
        const val STATE = 90
        const val COUNTED = 91
        const val BUMP = 92

        /**
         * The `dataSlice` a roster read asks for: `wallet` through `state`, the
         * fields the survivor counter, projected share and roster order need.
         * The cohort key is left out — the query already matched on it — and so
         * are `counted` and the bump, which only settlement uses. 51 bytes a
         * member instead of 93, which is most of the ~100 KB a 1,000-member
         * cohort would otherwise cost every 30 seconds (R8).
         */
        const val ROSTER_SLICE_OFFSET = WALLET
        const val ROSTER_SLICE_LENGTH = STATE + 1 - WALLET
    }

    /** `programs/holdout/src/state/config.rs` — 263 bytes. */
    object Config {
        const val SIZE = 263

        /** `sha256("account:Config")[..8]`. */
        val DISCRIMINATOR: ByteArray get() = bytes(0x9b, 0x0c, 0xaa, 0xe0, 0x1e, 0xfa, 0xcc, 0x82)

        const val ADMIN = 8
        const val OPERATOR = 40
        const val SPONSOR = 72
        const val SKR_MINT = 104
        const val FEE_DESTINATION = 136
        const val LADDER_GATE_ENABLED = 168
        const val GENESIS_GATE_ENABLED = 169
        const val CHIP_AMOUNTS = 170
        const val CHIP_VERSION = 202
        const val CHIP_PUBLISHED_AT = 206
        const val MAX_TOP_TIER = 214
        const val SOL_TOP_UP = 222
        const val SPONSOR_FLOAT = 230
        const val DAILY_LAMPORT_CAP = 238
        const val LAMPORT_DAY = 246
        const val LAMPORTS_TODAY = 254
        const val BUMP = 262
    }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /** Refuses bytes that are not the account they claim to be, rather than misreading them. */
    internal fun requireAccount(data: ByteArray, size: Int, discriminator: ByteArray, name: String) {
        require(data.size == size) { "$name is $size bytes, got ${data.size}" }
        require(data.copyOf(DISCRIMINATOR_SIZE).contentEquals(discriminator)) { "not a $name account" }
    }
}

/** Little-endian Borsh reads at fixed offsets. */
internal class LayoutReader(data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun u8(at: Int): Int = buf.get(at).toInt() and 0xFF

    fun u16(at: Int): Int = buf.getShort(at).toInt() and 0xFFFF

    fun u32(at: Int): Long = buf.getInt(at).toLong() and 0xFFFF_FFFFL

    fun i64(at: Int): Long = buf.getLong(at)

    fun u64(at: Int): ULong = buf.getLong(at).toULong()

    fun bool(at: Int): Boolean = when (u8(at)) {
        0 -> false
        1 -> true
        else -> throw IllegalArgumentException("invalid bool at $at")
    }

    fun pubkey(at: Int): SolanaPublicKey = SolanaPublicKey(ByteArray(32).also { buf.get(at, it) })

    fun u64s(at: Int, n: Int): List<ULong> = List(n) { u64(at + 8 * it) }

    fun u32s(at: Int, n: Int): List<Long> = List(n) { u32(at + 4 * it) }
}

/**
 * A `Cohort` as read from chain (data-model §1.3). Status, `end` and every
 * deadline are derived from these by [Derive], never stored.
 *
 * The running totals and settlement fields default to a fresh cohort's zeros,
 * so a test can describe a cohort by its schedule alone.
 */
data class CohortSnapshot(
    val leagueId: Int,
    val index: Long,
    val start: Long,
    val joinCutoff: Long,
    val durationDays: Int,
    val repsPerDay: Int,
    val dayLength: Long,
    val exercise: Int,
    /** The four chip amounts this cohort locked at creation (FR-055). */
    val chips: List<ULong>,
    val memberCount: Long = 0,
    val heldStake: ULong = 0uL,
    val pool: ULong = 0uL,
    val carryOverReceived: ULong = 0uL,
    val forfeitsRecorded: Long = 0,
    val counted: Long = 0,
    val finisherCount: Long = 0,
    val finishersPerTier: List<Long> = listOf(0, 0, 0, 0),
    val totalFinisherStake: ULong = 0uL,
    val settled: Boolean = false,
    val sharePerTier: List<ULong> = listOf(0uL, 0uL, 0uL, 0uL),
    val roundingRemainder: ULong = 0uL,
    val noFinisherFee: ULong = 0uL,
    val carryOverSent: ULong = 0uL,
    val carryOverHeld: ULong = 0uL,
    val carryOverTarget: SolanaPublicKey = SolanaPublicKey(ByteArray(32)),
) {
    companion object {
        fun decode(data: ByteArray): CohortSnapshot {
            val l = AccountLayout.Cohort
            AccountLayout.requireAccount(data, l.SIZE, l.DISCRIMINATOR, "Cohort")
            val r = LayoutReader(data)
            return CohortSnapshot(
                leagueId = r.u8(l.LEAGUE_ID),
                index = r.u32(l.INDEX),
                start = r.i64(l.START),
                joinCutoff = r.i64(l.JOIN_CUTOFF),
                durationDays = r.u8(l.DURATION_DAYS),
                repsPerDay = r.u16(l.REPS_PER_DAY),
                dayLength = r.i64(l.DAY_LENGTH),
                exercise = r.u8(l.EXERCISE),
                chips = r.u64s(l.CHIPS, 4),
                memberCount = r.u32(l.MEMBER_COUNT),
                heldStake = r.u64(l.HELD_STAKE),
                pool = r.u64(l.POOL),
                carryOverReceived = r.u64(l.CARRY_OVER_RECEIVED),
                forfeitsRecorded = r.u32(l.FORFEITS_RECORDED),
                counted = r.u32(l.COUNTED),
                finisherCount = r.u32(l.FINISHER_COUNT),
                finishersPerTier = r.u32s(l.FINISHERS_PER_TIER, 4),
                totalFinisherStake = r.u64(l.TOTAL_FINISHER_STAKE),
                settled = r.bool(l.SETTLED),
                sharePerTier = r.u64s(l.SHARE_PER_TIER, 4),
                roundingRemainder = r.u64(l.ROUNDING_REMAINDER),
                noFinisherFee = r.u64(l.NO_FINISHER_FEE),
                carryOverSent = r.u64(l.CARRY_OVER_SENT),
                carryOverHeld = r.u64(l.CARRY_OVER_HELD),
                carryOverTarget = r.pubkey(l.CARRY_OVER_TARGET),
            )
        }
    }
}

/**
 * One member's place in a cohort, as the roster reads it: exactly the fields in
 * the roster `dataSlice` (data-model §1.4, [AccountLayout.Membership]).
 * `Pending` and `Active` are not states here — they follow from the cohort's
 * status while the state is `Joined` (spec §8.2).
 */
data class MembershipSnapshot(
    val wallet: SolanaPublicKey,
    /** 0..=3. */
    val tier: Int,
    /** `cohort.chips[tier]` at join, never changed (FR-058). */
    val stake: ULong,
    val joinedAt: Long,
    val daysCompleted: Int,
    val state: State,
) {
    /** The program's `MembershipState`, in its Borsh variant order. */
    enum class State { Joined, Forfeited, Finished, Claimed }

    companion object {
        /** A whole `Membership` account. */
        fun decode(data: ByteArray): MembershipSnapshot {
            val l = AccountLayout.Membership
            AccountLayout.requireAccount(data, l.SIZE, l.DISCRIMINATOR, "Membership")
            return decodeRosterSlice(data.copyOfRange(l.ROSTER_SLICE_OFFSET, l.ROSTER_SLICE_OFFSET + l.ROSTER_SLICE_LENGTH))
        }

        /**
         * The roster `dataSlice` — no discriminator in it to check, which is why
         * the query that fetched it filters on the discriminator and the size.
         */
        fun decodeRosterSlice(slice: ByteArray): MembershipSnapshot {
            val l = AccountLayout.Membership
            require(slice.size == l.ROSTER_SLICE_LENGTH) { "roster slice is ${l.ROSTER_SLICE_LENGTH} bytes, got ${slice.size}" }
            val r = LayoutReader(slice)
            val base = l.ROSTER_SLICE_OFFSET
            val state = r.u8(l.STATE - base)
            require(state < State.entries.size) { "unknown membership state $state" }
            return MembershipSnapshot(
                wallet = r.pubkey(l.WALLET - base),
                tier = r.u8(l.TIER - base),
                stake = r.u64(l.STAKE - base),
                joinedAt = r.i64(l.JOINED_AT - base),
                daysCompleted = r.u8(l.DAYS_COMPLETED - base),
                state = State.entries[state],
            )
        }
    }
}

/** The `Config` singleton (data-model §1.1): keys, the chip set and the faucet's books. */
data class ConfigSnapshot(
    val admin: SolanaPublicKey,
    val operator: SolanaPublicKey,
    val sponsor: SolanaPublicKey,
    val skrMint: SolanaPublicKey,
    val feeDestination: SolanaPublicKey,
    val ladderGateEnabled: Boolean,
    val genesisGateEnabled: Boolean,
    val chipAmounts: List<ULong>,
    /** `0` until the first publish (FR-055b). */
    val chipVersion: Long,
    val chipPublishedAt: Long,
    val maxTopTier: ULong,
    val solTopUp: ULong,
    val sponsorFloat: ULong,
    val dailyLamportCap: ULong,
    val lamportDay: Long,
    val lamportsToday: ULong,
) {
    /**
     * Lamports out of the faucet vault on global day [today]. The stored
     * counter belongs to `lamportDay`; on any other day nothing has been spent
     * yet, exactly as the program's `Faucet::remaining_today` reads it (R13).
     */
    fun lamportsSpentOn(today: Long): ULong = if (lamportDay == today) lamportsToday else 0uL

    companion object {
        fun decode(data: ByteArray): ConfigSnapshot {
            val l = AccountLayout.Config
            AccountLayout.requireAccount(data, l.SIZE, l.DISCRIMINATOR, "Config")
            val r = LayoutReader(data)
            return ConfigSnapshot(
                admin = r.pubkey(l.ADMIN),
                operator = r.pubkey(l.OPERATOR),
                sponsor = r.pubkey(l.SPONSOR),
                skrMint = r.pubkey(l.SKR_MINT),
                feeDestination = r.pubkey(l.FEE_DESTINATION),
                ladderGateEnabled = r.bool(l.LADDER_GATE_ENABLED),
                genesisGateEnabled = r.bool(l.GENESIS_GATE_ENABLED),
                chipAmounts = r.u64s(l.CHIP_AMOUNTS, 4),
                chipVersion = r.u32(l.CHIP_VERSION),
                chipPublishedAt = r.i64(l.CHIP_PUBLISHED_AT),
                maxTopTier = r.u64(l.MAX_TOP_TIER),
                solTopUp = r.u64(l.SOL_TOP_UP),
                sponsorFloat = r.u64(l.SPONSOR_FLOAT),
                dailyLamportCap = r.u64(l.DAILY_LAMPORT_CAP),
                lamportDay = r.i64(l.LAMPORT_DAY),
                lamportsToday = r.u64(l.LAMPORTS_TODAY),
            )
        }
    }
}
