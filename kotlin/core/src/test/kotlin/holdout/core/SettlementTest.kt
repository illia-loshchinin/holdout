package holdout.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The H35 settlement arithmetic in [Settlement] — the forfeit split, the boost,
 * the comeback and the no-finisher split — replayed against
 * `fixtures/settlement-vectors.json` → `reserve_vectors`, the same golden
 * numbers `programs/holdout/src/math/settlement.rs` replays. Amounts reach
 * `u64::MAX`, past a JSON double and a signed Long, so every number is read
 * from the primitive's text straight into a `ULong`.
 */
class SettlementTest {
    private val vectors = Json.parseToJsonElement(RepoFiles.read("fixtures/settlement-vectors.json")).jsonObject

    private fun reserve(name: String): List<JsonObject> = vectors.getValue("reserve_vectors").jsonObject.getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.u(name: String): ULong = getValue(name).jsonPrimitive.content.toULong()

    @Test
    fun `given every golden stake, when it is forfeited, then it splits exactly as the program splits it`() {
        val cases = reserve("forfeit_split")
        assertTrue(cases.isNotEmpty())
        cases.forEach { v ->
            val stake = v.u("stake")
            val split = Settlement.forfeitSplit(stake)
            assertEquals(Settlement.ForfeitSplit(v.u("pool"), v.u("boost"), v.u("comeback"), v.u("developer"), v.u("operations")), split, "split of $stake")
            assertEquals(Settlement.feeOf(stake), split.fee, "the parts of the fee add up — $stake")
            assertEquals(split.pool, Settlement.poolContribution(stake), "the pool part is what the projected pool counts — $stake")
            assertEquals(
                BigInteger(stake.toString()),
                BigInteger(split.pool.toString()) + BigInteger(split.fee.toString()),
                "the stake is fully allocated — $stake",
            )
            assertEquals(split.developer + split.operations, split.paidOutNow)
        }
    }

    @Test
    fun `given every golden pool and reserve, when a cohort finalizes, then its boost is the program's`() {
        reserve("boost").forEach { v ->
            assertEquals(v.u("boost"), Settlement.boostFor(v.u("own_pool"), v.u("reserve")), "boost ${v.u("own_pool")} / ${v.u("reserve")}")
        }
    }

    @Test
    fun `given every golden forfeit and new stake, when the finisher is counted, then what they are owed is the program's`() {
        reserve("comeback_owed").forEach { v ->
            assertEquals(v.u("owed"), Settlement.comebackOwed(v.u("forfeited_stake"), v.u("new_stake")), "owed ${v.u("forfeited_stake")} / ${v.u("new_stake")}")
        }
    }

    @Test
    fun `given every golden entitlement, when the finisher claims, then the pro-rata refund is the program's`() {
        reserve("comeback_refund").forEach { v ->
            assertEquals(v.u("refund"), Settlement.comebackRefund(v.u("entitled"), v.u("funded"), v.u("owed")), "refund ${v.u("entitled")} ${v.u("funded")} ${v.u("owed")}")
        }
    }

    @Test
    fun `given every golden pool with no finishers, when it settles, then a third goes to the developer and the rest to operations`() {
        reserve("no_finisher_split").forEach { v ->
            val pool = v.u("pool")
            val (developer, operations) = Settlement.noFinisherSplit(pool)
            assertEquals(v.u("developer") to v.u("operations"), developer to operations, "no-finisher split of $pool")
            assertEquals(pool, developer + operations, "nothing left behind — $pool")
        }
    }

    @Test
    fun `given the no-finisher math vectors, when their pool settles, then the developer and operations figures are the vector's`() {
        val noFinishers = vectors.getValue("math_vectors").jsonArray.map { it.jsonObject }.filter { "developer" in it.getValue("expected").jsonObject }
        assertTrue(noFinishers.isNotEmpty())
        noFinishers.forEach { v ->
            val e = v.getValue("expected").jsonObject
            assertEquals(e.u("developer") to e.u("operations"), Settlement.noFinisherSplit(e.u("pool")), v.getValue("id").jsonPrimitive.content)
        }
    }

    @Test
    fun `given a hundred base units, when split, then it is 70 10 5 5 10`() {
        assertEquals(Settlement.ForfeitSplit(70uL, 10uL, 5uL, 5uL, 10uL), Settlement.forfeitSplit(100uL))
        // Operations takes what the floors leave: 9 → pool 7, fee 2, every percent part floors to 0.
        assertEquals(Settlement.ForfeitSplit(7uL, 0uL, 0uL, 0uL, 2uL), Settlement.forfeitSplit(9uL))
    }

    @Test
    fun `given nothing owed, when computing a refund, then it is zero rather than a division by zero`() {
        assertEquals(0uL, Settlement.comebackRefund(10uL, 10uL, 0uL))
    }
}
