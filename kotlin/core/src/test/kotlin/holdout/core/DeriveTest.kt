package holdout.core

import com.solana.publickey.SolanaPublicKey
import holdout.core.MembershipSnapshot.State.Claimed
import holdout.core.MembershipSnapshot.State.Finished
import holdout.core.MembershipSnapshot.State.Forfeited
import holdout.core.MembershipSnapshot.State.Joined
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T032 — the figures every cohort screen shows, derived from chain accounts and
 * chain time only (data-model §5, FR-038a).
 *
 * The schedule and breach tests restate the program's own (`schedule.rs`,
 * `cohort.rs`, `membership.rs`) with the same instants, so a rule that moves on
 * one side fails here too. The settlement vectors are read from
 * `fixtures/settlement-vectors.json`, the file `settlement.rs` replays.
 */
class DeriveTest {

    /** Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start. */
    private val firstStart = 1_790_571_600L
    private val day = 86_400L

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    private fun cohort(
        start: Long = firstStart,
        durationDays: Int = 7,
        chips: List<ULong> = listOf(10uL, 25uL, 50uL, 100uL),
        memberCount: Long = 0,
        heldStake: ULong = 0uL,
        pool: ULong = 0uL,
        carryOverReceived: ULong = 0uL,
    ) = CohortSnapshot(
        leagueId = 0,
        index = 0,
        start = start,
        joinCutoff = start - 3_600,
        durationDays = durationDays,
        repsPerDay = 25,
        dayLength = day,
        exercise = 0,
        chips = chips,
        memberCount = memberCount,
        heldStake = heldStake,
        pool = pool,
        carryOverReceived = carryOverReceived,
    )

    private fun member(
        n: Int,
        daysCompleted: Int,
        state: MembershipSnapshot.State = Joined,
        stake: ULong = 100uL,
        tier: Int = 3,
    ) = MembershipSnapshot(
        wallet = key(n),
        tier = tier,
        stake = stake,
        joinedAt = firstStart - 7_200,
        daysCompleted = daysCompleted,
        state = state,
    )

    // ---- the schedule, as `schedule.rs` has it ----------------------------

    @Test
    fun `deadlines land one second before the next boundary`() {
        assertEquals(firstStart + day - 1, Schedule.deadlineOf(firstStart, day, 0))
        assertEquals(firstStart + 7 * day - 1, Schedule.deadlineOf(firstStart, day, 6))
        // A test league keeps the same shape with a shorter day.
        assertEquals(1_599, Schedule.deadlineOf(1_000, 600, 0))
        assertEquals(5_199, Schedule.deadlineOf(1_000, 600, 6))
    }

    @Test
    fun `elapsed days are the current day index and stop at the duration`() {
        assertEquals(0, Schedule.daysElapsed(firstStart, day, 7, firstStart))
        assertEquals(0, Schedule.daysElapsed(firstStart, day, 7, firstStart + day - 1))
        assertEquals(1, Schedule.daysElapsed(firstStart, day, 7, firstStart + day))
        assertEquals(7, Schedule.daysElapsed(firstStart, day, 7, firstStart + 99 * day))
        assertEquals(0, Schedule.daysElapsed(firstStart, day, 7, firstStart - 1), "before the start")
    }

    @Test
    fun `the first day can be completed on the first day, and never past the duration`() {
        assertEquals(1, Schedule.maxDaysCompleted(firstStart, day, 7, firstStart))
        assertEquals(1, Schedule.maxDaysCompleted(firstStart, day, 7, firstStart + day - 1))
        assertEquals(2, Schedule.maxDaysCompleted(firstStart, day, 7, firstStart + day))
        assertEquals(7, Schedule.maxDaysCompleted(firstStart, day, 7, firstStart + 7 * day))
        assertEquals(0, Schedule.maxDaysCompleted(firstStart, day, 7, firstStart - 1))
    }

    // ---- status, as `cohort.rs` has it ------------------------------------

    @Test
    fun `every status boundary is one second wide`() {
        val c = cohort()
        val end = Derive.end(c)
        assertEquals(CohortStatus.Open, Derive.status(c, c.joinCutoff - 1))
        assertEquals(CohortStatus.Locked, Derive.status(c, c.joinCutoff), "the cutoff itself closes it")
        assertEquals(CohortStatus.Locked, Derive.status(c, c.start - 1))
        assertEquals(CohortStatus.Running, Derive.status(c, c.start))
        assertEquals(CohortStatus.Running, Derive.status(c, end - 1), "the last second still counts")
        assertEquals(CohortStatus.Settling, Derive.status(c, end))
        assertEquals(Derive.deadlineOf(c, 6) + 1, end)
    }

    @Test
    fun `settled outranks the clock`() {
        val c = cohort().copy(settled = true)
        assertEquals(CohortStatus.Settled, Derive.status(c, c.start - 1))
        assertEquals(CohortStatus.Settled, Derive.status(c, Derive.end(c) + 99))
    }

    // ---- breach and standing, as `membership.rs` has them -----------------

    @Test
    fun `breach begins one second after the deadline`() {
        val c = cohort()
        val m = member(1, daysCompleted = 0)
        val deadline = Derive.deadlineOf(c, 0)
        assertFalse(Derive.inBreach(m, c, deadline), "the deadline second is still theirs")
        assertTrue(Derive.inBreach(m, c, deadline + 1), "and the next one is not")
        assertFalse(Derive.inBreach(m, c, c.start - 1), "nothing is owed before the start")
    }

    @Test
    fun `a member who completed every day is never in breach`() {
        val c = cohort()
        assertFalse(Derive.inBreach(member(1, daysCompleted = 7), c, Derive.end(c) + 99))
    }

    @Test
    fun `standing treats a breach as out before any forfeit is recorded`() {
        val c = cohort()
        val at = Derive.deadlineOf(c, 0) + 1
        assertFalse(Derive.standing(member(1, 0), c, at), "the counter drops at the deadline (FR-038a)")
        assertTrue(Derive.standing(member(2, 7, Finished), c, at))
        assertTrue(Derive.standing(member(3, 7, Claimed), c, at))
        assertFalse(Derive.standing(member(4, 2, Forfeited), c, at))
    }

    // ---- settlement arithmetic, as `settlement.rs` has it -----------------

    @Test
    fun `the fee is twenty percent rounded down, in 128-bit arithmetic`() {
        assertEquals(20uL, Settlement.feeOf(100uL))
        assertEquals(0uL, Settlement.feeOf(1uL))
        assertEquals(1uL, Settlement.feeOf(9uL))
        assertEquals(250uL, Settlement.feeOf(1_250uL))
        // `stake * 20` overflows 64 bits here; the program widens to u128 first.
        assertEquals(3_689_348_814_741_910_323uL, Settlement.feeOf(ULong.MAX_VALUE))
        assertEquals(ULong.MAX_VALUE - 3_689_348_814_741_910_323uL, Settlement.poolContribution(ULong.MAX_VALUE))
    }

    @Test
    fun `a share is floored, widened, and zero with nobody to pay`() {
        assertEquals(333uL, Settlement.shareForTier(1_000uL, 100uL, 300uL))
        assertEquals(0uL, Settlement.shareForTier(0uL, 100uL, 500uL))
        assertEquals(0uL, Settlement.shareForTier(1_000uL, 100uL, 0uL), "no division by zero")
        assertEquals(ULong.MAX_VALUE, Settlement.shareForTier(ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE))
    }

    // ---- the settlement vectors, as projections ---------------------------

    /**
     * Every PRD §19 vector, replayed as a cohort at its end before anyone has
     * run settlement: the forfeits are breaches nobody has recorded yet, and the
     * finishers have every day done. The projected pool and each finisher's
     * projected share must already be exactly what settlement will pay — and
     * recording the forfeits must change none of it (FR-038a).
     */
    @Test
    fun `every settlement vector is already visible before any forfeit is recorded`() {
        val vectors =
            Json
                .parseToJsonElement(RepoFiles.read("fixtures/settlement-vectors.json"))
                .jsonObject
                .getValue("math_vectors")
                .jsonArray
        for (element in vectors) {
            val v = element.jsonObject
            val ctx = v.getValue("id").jsonPrimitive.content
            val expected = v.getValue("expected").jsonObject
            val forfeited = counts(v, "forfeited_stakes")
            val finishers = counts(v, "finisher_stakes")
            val carryIn = v["carry_over_in"]?.jsonPrimitive?.long?.toULong() ?: 0uL
            val poolOverride = v["pool_override"]?.jsonPrimitive?.longOrNull?.toULong()
            val expectedPool = expected.getValue("pool").jsonPrimitive.long.toULong()

            var n = 0
            val breached = forfeited.flatMap { (chip, count) -> List(count) { member(++n, 2, stake = chip) } }
            val finished = finishers.flatMap { (chip, count) -> List(count) { member(++n, 7, stake = chip) } }
            val members = breached + finished
            val base = cohort(memberCount = members.size.toLong(), pool = poolOverride ?: carryIn)
            val now = Derive.end(base)

            val live = Derive.figures(base, members, now)
            assertEquals(expectedPool, live.projectedPool, "projected pool — $ctx")
            assertEquals(Survivors(finished.size, members.size.toLong()), live.survivors, "survivors — $ctx")

            // The same cohort once the crank has recorded every forfeit.
            val recorded =
                Derive.figures(
                    base.copy(pool = expectedPool),
                    breached.map { it.copy(state = Forfeited) } + finished,
                    now,
                )
            assertEquals(live.projectedPool, recorded.projectedPool, "recording moves no figure — $ctx")
            assertEquals(live.survivors, recorded.survivors, "recording moves no figure — $ctx")

            val shares = expected["shares"]?.jsonObject.orEmpty()
            var paid = 0uL
            for (m in finished) {
                val share = live.projectedShare(m)
                assertEquals(shares.getValue(m.stake.toString()).jsonPrimitive.long.toULong(), share, "share for ${m.stake} — $ctx")
                assertEquals(share, recorded.projectedShare(m), "recording moves no share — $ctx")
                paid += share
            }
            for (m in breached) assertEquals(0uL, live.projectedShare(m), "a breached member has no share — $ctx")

            if (finished.isNotEmpty()) {
                val remainder = expected["remainder"]?.jsonPrimitive?.long?.toULong() ?: 0uL
                assertEquals(expectedPool, paid + remainder, "shares plus the remainder are the pool — $ctx")
            }
        }
    }

    private fun counts(v: JsonObject, field: String): List<Pair<ULong, Int>> = v[field]?.jsonObject.orEmpty().map { (chip, count) -> chip.toULong() to count.jsonPrimitive.long.toInt() }

    @Test
    fun `nobody standing means no shares and no division by zero`() {
        val c = cohort(memberCount = 2)
        val members = listOf(member(1, 0), member(2, 1))
        val f = Derive.figures(c, members, Derive.end(c))
        assertEquals(Survivors(0, 2), f.survivors)
        assertEquals(160uL, f.projectedPool)
        members.forEach { assertEquals(0uL, f.projectedShare(it)) }
    }

    @Test
    fun `a settled cohort shows what settlement wrote, whatever has been claimed since`() {
        // V17's shape, settled, after one finisher has claimed: `pool` has
        // fallen by the claimed share and the remainder, but a finisher's figure
        // is the stored share, identical for the whole tier (R10).
        val c =
            cohort(memberCount = 8, pool = 1_000uL - 1uL - 333uL).copy(
                chips = listOf(100uL, 250uL, 500uL, 1_000uL),
                settled = true,
                finishersPerTier = listOf(3, 0, 0, 0),
                sharePerTier = listOf(333uL, 0uL, 0uL, 0uL),
                totalFinisherStake = 300uL,
            )
        val claimed = member(1, 7, Claimed, stake = 100uL, tier = 0)
        val finished = member(2, 7, Finished, stake = 100uL, tier = 0)
        val out = member(3, 2, Forfeited, stake = 250uL, tier = 1)
        val f = Derive.figures(c, listOf(claimed, finished, out), Derive.end(c) + 5 * day)
        assertEquals(333uL, f.projectedShare(claimed))
        assertEquals(333uL, f.projectedShare(finished))
        assertEquals(0uL, f.projectedShare(out))
        assertEquals(999uL, f.projectedPool, "the prize settlement shared out")
    }

    // ---- counters and roster ----------------------------------------------

    @Test
    fun `the survivor counter drops at the deadline for everyone at once`() {
        val c = cohort(memberCount = 3)
        val members = listOf(member(1, 1), member(2, 1), member(3, 0))
        val deadline = Derive.deadlineOf(c, 0)
        assertEquals(Survivors(3, 3), Derive.survivors(c, members, deadline))
        assertEquals(Survivors(2, 3), Derive.survivors(c, members, deadline + 1))
        // Recording the forfeit later changes nothing on screen.
        val recorded = members.take(2) + members[2].copy(state = Forfeited)
        assertEquals(Survivors(2, 3), Derive.survivors(c, recorded, deadline + 1))
    }

    @Test
    fun `the projected pool holds 80 percent of every breach nobody has recorded`() {
        val c = cohort(memberCount = 3, pool = 600uL, carryOverReceived = 600uL)
        val members = listOf(member(1, 1), member(2, 1), member(3, 0, stake = 250uL))
        val at = Derive.deadlineOf(c, 0) + 1
        assertEquals(600uL + 200uL, Derive.projectedPool(c, members, at))
        assertEquals(600uL, Derive.projectedPool(c, members, at - 1), "not a second early")
        // Standing stake 200: each 100-stake member projects 100 × 800 ÷ 200.
        assertEquals(400uL, Derive.projectedShare(members[0], c, members, at))
    }

    @Test
    fun `the roster puts the standing first, then the fallen by days lasted`() {
        val c = cohort(memberCount = 6)
        val now = c.start + 3 * day + 10 // day 3 of 7
        val standingA = member(5, 3)
        val standingB = member(1, 4)
        val breachedTwo = member(3, 2) // missed day 2, not yet recorded
        val forfeitedTwo = member(2, 2, Forfeited)
        val forfeitedOne = member(4, 1, Forfeited)
        val forfeitedZero = member(6, 0, Forfeited)
        val roster =
            Derive.roster(
                c,
                listOf(forfeitedOne, breachedTwo, forfeitedZero, standingA, forfeitedTwo, standingB),
                now,
                scriptedWallets = setOf(key(3), key(6)),
            )
        assertEquals(
            listOf(standingB, standingA, forfeitedTwo, breachedTwo, forfeitedOne, forfeitedZero),
            roster.map { it.member },
            "standing first; the out by days lasted, most first; ties by wallet so every device agrees",
        )
        assertEquals(listOf(true, true, false, false, false, false), roster.map { it.standing })
        assertEquals(listOf(4, 3, 2, 2, 1, 0), roster.map { it.daysLasted })
        assertEquals(listOf(false, false, false, true, false, true), roster.map { it.scripted }, "FR-064")
    }

    // ---- time --------------------------------------------------------------

    @Test
    fun `time left counts down to the deadline second, then a whole day again`() {
        val c = cohort()
        assertEquals(day - 1, Derive.timeLeftToday(c, c.start))
        assertEquals(0, Derive.timeLeftToday(c, Derive.deadlineOf(c, 0)), "the deadline second itself")
        assertEquals(day - 1, Derive.timeLeftToday(c, Derive.deadlineOf(c, 0) + 1))
        assertEquals(0, Derive.timeLeftToday(c, Derive.end(c) - 1))
        assertNull(Derive.timeLeftToday(c, c.start - 1), "nothing owed before the start")
        assertNull(Derive.timeLeftToday(c, Derive.end(c)), "nor after the end")
    }

    @Test
    fun `the warning window is the last ten minutes`() {
        val c = cohort()
        val deadline = Derive.deadlineOf(c, 0)
        assertTrue(Derive.inWarningWindow(Derive.timeLeftToday(c, deadline - 600)))
        assertFalse(Derive.inWarningWindow(Derive.timeLeftToday(c, deadline - 601)))
        assertTrue(Derive.inWarningWindow(Derive.timeLeftToday(c, deadline)))
        assertFalse(Derive.inWarningWindow(null))
    }

    // ---- an open Holdout's card (FR-053) -----------------------------------

    @Test
    fun `an open Holdout shows what is at stake and how long joining stays open`() {
        val c = cohort(memberCount = 4, heldStake = 185uL)
        assertEquals(185uL, Derive.atStake(c))
        assertEquals(3_600, Derive.timeUntilJoiningCloses(c, c.joinCutoff - 3_600))
        assertEquals(1, Derive.timeUntilJoiningCloses(c, c.joinCutoff - 1))
        assertEquals(0, Derive.timeUntilJoiningCloses(c, c.joinCutoff), "closed at the cutoff itself")
        assertEquals(0, Derive.timeUntilJoiningCloses(c, c.start))
    }

    @Test
    fun `carry-over received is its own line only when there is some`() {
        assertNull(Derive.carryOverReceived(cohort()))
        assertEquals(600uL, Derive.carryOverReceived(cohort(carryOverReceived = 600uL)))
    }

    @Test
    fun `figures computed together agree with each on its own`() {
        val c = cohort(memberCount = 3, heldStake = 300uL, carryOverReceived = 50uL, pool = 50uL)
        val members = listOf(member(1, 1), member(2, 1), member(3, 0))
        val now = Derive.deadlineOf(c, 0) + 1
        val f = Derive.figures(c, members, now, scriptedWallets = setOf(key(2)))
        assertEquals(Derive.status(c, now), f.status)
        assertEquals(Derive.survivors(c, members, now), f.survivors)
        assertEquals(Derive.projectedPool(c, members, now), f.projectedPool)
        assertEquals(Derive.roster(c, members, now, setOf(key(2))), f.roster)
        assertEquals(Derive.timeLeftToday(c, now), f.timeLeftToday)
        assertEquals(Derive.inWarningWindow(f.timeLeftToday), f.inWarningWindow)
        assertEquals(Derive.atStake(c), f.atStake)
        assertEquals(Derive.carryOverReceived(c), f.carryOverReceived)
        assertEquals(Derive.timeUntilJoiningCloses(c, now), f.timeUntilJoiningCloses)
        members.forEach { assertEquals(Derive.projectedShare(it, c, members, now), f.projectedShare(it)) }
    }
}
