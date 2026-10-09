package holdout.core

import com.solana.publickey.SolanaPublicKey
import java.math.BigInteger

/**
 * The cohort schedule — `programs/holdout/src/math/schedule.rs`, line for line.
 *
 * One clock (constitution VI): every instant here is chain time and the
 * cohort's own parameters. Nothing reads a timezone, and nothing reads the
 * device clock — the `now` passed in comes from [ChainClock] (research R7).
 */
object Schedule {
    /** The last second of day [dayIndex]: `start + (k + 1) × day_length − 1`. */
    fun deadlineOf(start: Long, dayLength: Long, dayIndex: Int): Long = start + (dayIndex + 1L) * dayLength - 1

    /**
     * Whole cohort days elapsed at [now], `0` before the start and capped at
     * the duration — the program's `days_elapsed`. While the cohort is
     * `Running` this is its current zero-based day. `now ≥ start` here, so the
     * program's truncating `/` and a floor agree.
     */
    fun daysElapsed(start: Long, dayLength: Long, durationDays: Int, now: Long): Int {
        if (now < start) return 0
        return minOf((now - start) / dayLength, durationDays.toLong()).toInt()
    }

    /**
     * The INV-3 ceiling: `0` before the start, else `min(days_elapsed + 1,
     * duration_days)` — the day in progress may itself be completed.
     */
    fun maxDaysCompleted(start: Long, dayLength: Long, durationDays: Int, now: Long): Int {
        if (now < start) return 0
        return minOf(daysElapsed(start, dayLength, durationDays, now) + 1, durationDays)
    }
}

/**
 * Settlement arithmetic — `programs/holdout/src/math/settlement.rs`, with the
 * same widths: amounts are `u64`, products are taken in 128 bits and floored,
 * and the result narrows back. `BigInteger` stands in for `u128`; nothing here
 * can exceed it, because the program's own results are `u64`.
 */
object Settlement {
    /** 30 % of one forfeited stake, rounded down (FR-027). */
    fun feeOf(forfeitedStake: ULong): ULong = (forfeitedStake.big() * FEE_PERCENT / HUNDRED).toULongExact()

    /** What one forfeited stake adds to the pool: the stake minus its fee. */
    fun poolContribution(forfeitedStake: ULong): ULong = forfeitedStake - feeOf(forfeitedStake)

    /**
     * `floor(pool × chip ÷ total_finisher_stake)` (FR-032, R10), and `0` with
     * nobody to pay rather than a division by zero — as the program has it.
     */
    fun shareForTier(pool: ULong, chip: ULong, totalFinisherStake: ULong): ULong {
        if (totalFinisherStake == 0uL) return 0uL
        return (pool.big() * chip.big() / totalFinisherStake.big()).toULongExact()
    }

    /** Percents of a forfeited stake for each destination of the fee (FR-027, H35; 70 / 5 / 5 / 10 / 10 since 2026-10-09); operations takes what the floors leave. */
    const val BOOST_PERCENT = 5
    const val COMEBACK_PERCENT = 5
    const val DEVELOPER_PERCENT = 10

    /** The most a boost may add to a cohort's own pool, in percent (FR-037d). */
    const val BOOST_CAP_PERCENT = 20

    /** The share of a counted forfeited stake a comeback refunds, in percent (FR-037e). */
    const val COMEBACK_REFUND_PERCENT = 10

    /** How many of a cohort's days after the missed deadline a comeback cohort may start (FR-037e). */
    const val COMEBACK_WINDOW_DAYS = 14

    /** Where one forfeited stake goes (FR-027, H35) — the program's `ForfeitSplit`. */
    data class ForfeitSplit(
        /** To this cohort's prize pool. */
        val pool: ULong,
        /** Held in the cohort's vault until it finalizes, then the league's boost reserve. */
        val boost: ULong,
        /** Held likewise, then the league's comeback reserve. */
        val comeback: ULong,
        /** To the developer destination at once. */
        val developer: ULong,
        /** To the operations destination at once; takes what the floors leave. */
        val operations: ULong,
    ) {
        /** Everything that did not reach the pool: always [feeOf] the stake. */
        val fee: ULong get() = boost + comeback + developer + operations

        /** The parts that leave the cohort's vault when the forfeit is recorded. */
        val paidOutNow: ULong get() = developer + operations
    }

    /** Split one forfeited stake 70 / 5 / 5 / 10 / 10, each part floored (FR-027, H35). */
    fun forfeitSplit(forfeitedStake: ULong): ForfeitSplit {
        val fee = feeOf(forfeitedStake)
        val boost = percentOf(forfeitedStake, BOOST_PERCENT)
        val comeback = percentOf(forfeitedStake, COMEBACK_PERCENT)
        val developer = percentOf(forfeitedStake, DEVELOPER_PERCENT)
        // 5 + 5 + 10 of the stake never exceed 30 of it, so this cannot underflow.
        return ForfeitSplit(forfeitedStake - fee, boost, comeback, developer, fee - boost - comeback - developer)
    }

    /**
     * The boost a finishing cohort draws: 20% of its own pool, never more than
     * the league's boost reserve holds (FR-037d). A cohort with no forfeits
     * gets none.
     */
    fun boostFor(ownPool: ULong, boostReserve: ULong): ULong = minOf(percentOf(ownPool, BOOST_CAP_PERCENT), boostReserve)

    /** What a comeback owes a finisher: 10% of the forfeited stake, never more than the stake they finished on (FR-037e). */
    fun comebackOwed(forfeitedStake: ULong, newStake: ULong): ULong = minOf(percentOf(forfeitedStake, COMEBACK_REFUND_PERCENT), newStake)

    /**
     * One finisher's refund: `floor(entitled × funded ÷ total_owed)` — their
     * entitlement, scaled down pro rata when the comeback reserve could not
     * fund every finisher's in full (FR-037e). `0` when nothing is owed.
     */
    fun comebackRefund(entitled: ULong, funded: ULong, totalOwed: ULong): ULong {
        if (totalOwed == 0uL) return 0uL
        return (entitled.big() * funded.big() / totalOwed.big()).toULongExact()
    }

    /**
     * A cohort nobody finished: its whole pool goes out, half (floored) to the
     * developer and the rest to operations (FR-037, H34, H35; half each since
     * 2026-10-09, was a third). Returned as
     * `(developer, operations)`.
     */
    fun noFinisherSplit(pool: ULong): Pair<ULong, ULong> {
        val developer = pool / 2uL
        return developer to pool - developer
    }

    private fun percentOf(amount: ULong, percent: Int): ULong = (amount.big() * BigInteger.valueOf(percent.toLong()) / HUNDRED).toULongExact()

    private val FEE_PERCENT = BigInteger.valueOf(30)
    private val HUNDRED = BigInteger.valueOf(100)
}

internal fun ULong.big(): BigInteger = BigInteger(toString())

internal fun BigInteger.toULongExact(): ULong {
    require(signum() >= 0 && bitLength() <= 64) { "$this does not fit u64" }
    return toString().toULong()
}

/** Where a cohort is in its life — `CohortStatus` in `state/cohort.rs`. Derived, never stored. */
enum class CohortStatus { Open, Locked, Running, Settling, Settled }

/** `N / M still standing` (FR-038a). */
data class Survivors(
    /** Members standing now: finished, or joined and not in breach. */
    val standing: Int,
    /**
     * `member_count` — the starters once the cohort has started, since the
     * roster is final from the join cutoff (FR-010). Before the start it is
     * still the live joined count.
     */
    val starters: Long,
)

/** One line of a cohort roster (FR-062, FR-064). */
data class RosterEntry(
    val member: MembershipSnapshot,
    val standing: Boolean,
    /** `days_completed` — what orders the fallen. */
    val daysLasted: Int,
    /** In the bundled scripted-wallet list, and labelled wherever it appears (FR-043a, FR-064). */
    val scripted: Boolean,
)

/**
 * Every figure one cohort screen shows, from one read and one chain instant.
 * Built by [Derive.figures], so a 1,000-member roster is walked once rather
 * than once per figure.
 */
class CohortFigures internal constructor(
    val cohort: CohortSnapshot,
    val now: Long,
    val status: CohortStatus,
    val survivors: Survivors,
    val projectedPool: ULong,
    /** Σ stake of standing members — the projected share's denominator. */
    val standingStake: ULong,
    val roster: List<RosterEntry>,
    val timeLeftToday: Long?,
    val inWarningWindow: Boolean,
    val atStake: ULong,
    /**
     * [Derive.projectedBoost] against the league's boost reserve, when the
     * caller passed one to [Derive.figures]; `null` otherwise. Before
     * settlement it is an "up to" figure, never a promise.
     */
    val projectedBoost: ULong?,
    val timeUntilJoiningCloses: Long,
) {
    /** [Derive.projectedShare], with the pool and denominator already summed. */
    fun projectedShare(member: MembershipSnapshot): ULong = Derive.shareOf(member, cohort, now, projectedPool, standingStake)
}

/**
 * The figures every cohort screen shows (data-model §5, T032) — derived from
 * chain accounts and chain time only, in one place, shared by the app and the
 * crank (research R7, R12). No screen computes any of these itself
 * (contracts/android-app.md).
 *
 * **A breach counts from the second it happens** (FR-038a, constitution II).
 * The survivor counter, projected pool, projected shares and roster all treat a
 * member as out once `now > deadline_of(days_completed)`, whether or not
 * anybody has recorded the forfeit, and recording it later moves none of them.
 * A crank that misses a run delays money, never a figure.
 *
 * Status, breach and standing mirror `Cohort::status`, `Membership::in_breach`
 * and `Membership::standing` in the program; the amounts mirror
 * `settlement.rs`. Replayed against `fixtures/settlement-vectors.json`, and
 * against `fixtures/derived-figures.json` once T029 generates it (R2).
 */
object Derive {
    /** The last 10 minutes before a deadline, when the app warns (FR-047). */
    const val WARNING_WINDOW_SECONDS = 600L

    /** The instant after the final deadline: `start + duration_days × day_length`. */
    fun end(c: CohortSnapshot): Long = c.start + c.durationDays * c.dayLength

    fun deadlineOf(c: CohortSnapshot, dayIndex: Int): Long = Schedule.deadlineOf(c.start, c.dayLength, dayIndex)

    /** The current zero-based day while `Running`, clamped outside it. */
    fun dayIndex(c: CohortSnapshot, now: Long): Int = Schedule.daysElapsed(c.start, c.dayLength, c.durationDays, now)

    fun maxDaysCompleted(c: CohortSnapshot, now: Long): Int = Schedule.maxDaysCompleted(c.start, c.dayLength, c.durationDays, now)

    /** `Open` before the cutoff, `Locked` before the start, `Running` before the end, then `Settling` — and `Settled` outranks the clock. */
    fun status(c: CohortSnapshot, now: Long): CohortStatus = when {
        c.settled -> CohortStatus.Settled
        now < c.joinCutoff -> CohortStatus.Open
        now < c.start -> CohortStatus.Locked
        now < end(c) -> CohortStatus.Running
        else -> CohortStatus.Settling
    }

    /** `state == Joined ∧ now ≥ start ∧ days_completed < duration ∧ now > deadline_of(days_completed)` (data-model §1.4). */
    fun inBreach(m: MembershipSnapshot, c: CohortSnapshot, now: Long): Boolean = m.state == MembershipSnapshot.State.Joined &&
        now >= c.start &&
        m.daysCompleted < c.durationDays &&
        now > deadlineOf(c, m.daysCompleted)

    /** Finished or claimed, or joined and not in breach. */
    fun standing(m: MembershipSnapshot, c: CohortSnapshot, now: Long): Boolean = when (m.state) {
        MembershipSnapshot.State.Finished, MembershipSnapshot.State.Claimed -> true
        MembershipSnapshot.State.Joined -> !inBreach(m, c, now)
        MembershipSnapshot.State.Forfeited -> false
    }

    /** `count(standing) / member_count`. */
    fun survivors(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): Survivors = Survivors(members.count { standing(it, c, now) }, c.memberCount)

    /**
     * `pool + Σ (stake − floor(stake × 30 ÷ 100))` over members in breach whose
     * forfeit is not yet recorded — exactly what `record_forfeits` will add (the
     * 70% pool part of the split), so recording it changes nothing on screen
     * (FR-038a, V16). The boost is not in it: it is drawn only at finalization,
     * and shown on its own as [projectedBoost].
     *
     * Once settled, `pool` no longer means the prize: settlement took the
     * rounding remainder out, and every claim takes a share out after it. The
     * figure is then what settlement shared out, `Σ finishers_per_tier[t] ×
     * share_per_tier[t]` — the boosted pool less its remainder — which no claim
     * can move.
     */
    fun projectedPool(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): ULong {
        if (c.settled) {
            return (0 until 4)
                .fold(BigInteger.ZERO) { sum, t -> sum + BigInteger.valueOf(c.finishersPerTier[t]) * c.sharePerTier[t].big() }
                .toULongExact()
        }
        return members
            .filter { inBreach(it, c, now) }
            .fold(c.pool) { pool, m -> pool + Settlement.forfeitSplit(m.stake).pool }
    }

    /**
     * `floor(projected_pool × stake ÷ Σ stake of standing members)` — what this
     * member's share of the prize would be if everyone standing now finished.
     * `0` for a member who is out, and `0` with nobody standing. Once settled,
     * the stored `share_per_tier` of the member's tier, the figure a claim pays
     * on top of the stake (R10).
     */
    fun projectedShare(m: MembershipSnapshot, c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): ULong = shareOf(m, c, now, projectedPool(c, members, now), standingStake(c, members, now))

    internal fun shareOf(m: MembershipSnapshot, c: CohortSnapshot, now: Long, projectedPool: ULong, standingStake: ULong): ULong {
        if (!standing(m, c, now)) return 0uL
        if (c.settled) return c.sharePerTier.getOrElse(m.tier) { 0uL }
        return Settlement.shareForTier(projectedPool, m.stake, standingStake)
    }

    private fun standingStake(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long): ULong = members.filter { standing(it, c, now) }.fold(0uL) { sum, m -> sum + m.stake }

    /**
     * Standing members first; then everyone out — recorded forfeits and
     * unrecorded breaches alike — by days lasted, most first (FR-062). Ties go
     * by wallet bytes, so every device lists the same roster in the same order.
     * Nothing about money orders it (constitution XIII).
     */
    fun roster(
        c: CohortSnapshot,
        members: List<MembershipSnapshot>,
        now: Long,
        scriptedWallets: Set<SolanaPublicKey> = emptySet(),
    ): List<RosterEntry> = members
        .map { RosterEntry(it, standing(it, c, now), it.daysCompleted, it.wallet in scriptedWallets) }
        .sortedWith(
            compareByDescending<RosterEntry> { it.standing }
                .thenByDescending { if (it.standing) 0 else it.daysLasted }
                .thenComparator { a, b -> compareKeys(a.member.wallet, b.member.wallet) },
        )

    private fun compareKeys(a: SolanaPublicKey, b: SolanaPublicKey): Int = java.util.Arrays.compareUnsigned(a.bytes, b.bytes)

    /**
     * `deadline_of(day_index) − now` while the cohort is running (FR-046): `0`
     * in the deadline second itself, then a whole day again. `null` when no day
     * is owed — before the start and from the end.
     */
    fun timeLeftToday(c: CohortSnapshot, now: Long): Long? {
        if (status(c, now) != CohortStatus.Running) return null
        return deadlineOf(c, dayIndex(c, now)) - now
    }

    /** Inside the last [WARNING_WINDOW_SECONDS] of the day (FR-047). Never a refusal to start. */
    fun inWarningWindow(timeLeft: Long?): Boolean = timeLeft != null && timeLeft <= WARNING_WINDOW_SECONDS

    /**
     * What an open Holdout's card calls **at stake**: `held_stake`, the stakes
     * taken so far (FR-053). Never labelled a prize — an open cohort has no
     * forfeits yet.
     */
    fun atStake(c: CohortSnapshot): ULong = c.heldStake

    /**
     * The prize boost this cohort can expect from its league's boost reserve
     * (FR-037d), shown **"up to"**: `min(floor(20% × projected pool),
     * boostReserve)`, where [boostReserve] is the league's reserve as read now.
     * The real draw happens at finalization, against the reserve as it stands
     * then (earlier cohorts of the league finalize first and draw on it, and
     * credit their own dues to it), so this is an estimate, never a promise.
     *
     * `0` with nobody standing — a cohort with no finishers draws no boost —
     * and `0` with no forfeits, as the program has it. Once settled, the boost
     * the cohort actually received.
     */
    fun projectedBoost(c: CohortSnapshot, members: List<MembershipSnapshot>, now: Long, boostReserve: ULong): ULong {
        if (c.settled) return c.boostReceived
        if (members.none { standing(it, c, now) }) return 0uL
        return Settlement.boostFor(projectedPool(c, members, now), boostReserve)
    }

    /**
     * A finisher's comeback refund in a settled cohort (FR-037e):
     * `floor(comeback_entitled × comeback_funded ÷ comeback_owed)`, the amount
     * its claim pays on top of the stake and share. `null` before settlement,
     * when what the reserve will fund is not yet known; `0` for a member owed
     * nothing.
     */
    fun comebackRefund(m: MembershipSnapshot, c: CohortSnapshot): ULong? {
        if (!c.settled) return null
        return Settlement.comebackRefund(m.comebackEntitled, c.comebackFunded, c.comebackOwed)
    }

    /** `join_cutoff − now`, never negative: `0` from the cutoff itself, which closes joining (FR-008). */
    fun timeUntilJoiningCloses(c: CohortSnapshot, now: Long): Long = (c.joinCutoff - now).coerceAtLeast(0)

    /** Every figure at once, for one cohort screen. */
    fun figures(
        c: CohortSnapshot,
        members: List<MembershipSnapshot>,
        now: Long,
        scriptedWallets: Set<SolanaPublicKey> = emptySet(),
        /** The league's `boost_reserve`, to show the projected boost; `null` leaves it out. */
        boostReserve: ULong? = null,
    ): CohortFigures {
        val timeLeft = timeLeftToday(c, now)
        return CohortFigures(
            cohort = c,
            now = now,
            status = status(c, now),
            survivors = survivors(c, members, now),
            projectedPool = projectedPool(c, members, now),
            standingStake = standingStake(c, members, now),
            roster = roster(c, members, now, scriptedWallets),
            timeLeftToday = timeLeft,
            inWarningWindow = inWarningWindow(timeLeft),
            atStake = atStake(c),
            projectedBoost = boostReserve?.let { projectedBoost(c, members, now, it) },
            timeUntilJoiningCloses = timeUntilJoiningCloses(c, now),
        )
    }
}
