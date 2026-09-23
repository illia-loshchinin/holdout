package holdout.crank

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * T062 — pricing the four chips (research R15): $10 / $25 / $50 / $100 at the
 * Operator's rate, each rounded **down** to a multiple of 10 whole SKR, never
 * below 10 SKR. The rate lives only here; the program sees SKR amounts and
 * nothing else (constitution III).
 */
class ChipsTest {

    private fun whole(rate: String) = Chips.price(BigDecimal(rate)).map { it.wholeSkr.toLong() }

    @Test
    fun `each tier is converted and rounded down to a multiple of 10 SKR`() {
        // $10 ÷ 0.0213 = 469.48…, $25 → 1,173.7…, $50 → 2,347.4…, $100 → 4,694.8…
        assertEquals(listOf(460L, 1_170L, 2_340L, 4_690L), whole("0.0213"))
    }

    @Test
    fun `an exact multiple stays exactly where it is`() {
        assertEquals(listOf(40L, 100L, 200L, 400L), whole("0.25"))
        // Decimal arithmetic, not floating point: $10 ÷ 0.1 is 100, not 99.999….
        assertEquals(listOf(100L, 250L, 500L, 1_000L), whole("0.1"))
    }

    @Test
    fun `rounding is always down, never to the nearest`() {
        // $10 ÷ 0.0999 = 100.1 → 100, and $100 ÷ 0.0999 = 1,001.0 → 1,000.
        assertEquals(listOf(100L, 250L, 500L, 1_000L), whole("0.0999"))
        // $10 ÷ 0.1001 = 99.9 → 90: a hair under a multiple drops to the one below.
        assertEquals(90L, whole("0.1001")[0])
    }

    @Test
    fun `no tier is ever priced below 10 SKR`() {
        // $10 ÷ 1.2 = 8.3 → 0, lifted to the 10 SKR floor; the rest clear it.
        assertEquals(listOf(10L, 20L, 40L, 80L), whole("1.2"))
    }

    @Test
    fun `amounts are whole SKR in base units`() {
        val tiers = Chips.price(BigDecimal("0.0213"), decimals = 6)
        assertEquals(listOf(10, 25, 50, 100), tiers.map { it.usd })
        assertEquals(listOf(460_000_000uL, 1_170_000_000uL, 2_340_000_000uL, 4_690_000_000uL), tiers.map { it.baseUnits })
        assertEquals(listOf(460uL, 1_170uL, 2_340uL, 4_690uL), Chips.price(BigDecimal("0.0213"), decimals = 0).map { it.baseUnits })
        assertEquals(BigInteger.valueOf(460), tiers[0].wholeSkr)
    }

    @Test
    fun `a rate that collapses two tiers onto one amount is refused`() {
        // $10 ÷ 2 = 5 → 10 and $25 ÷ 2 = 12.5 → 10: the program would reject the
        // set as not strictly increasing (InvalidChipSet, 6011). Refuse here, with
        // the reason, rather than send a transaction built to fail.
        val e = assertFailsWith<ChipPricingException> { Chips.price(BigDecimal("2")) }
        assertEquals(true, e.message!!.contains("strictly increasing"), e.message)
    }

    @Test
    fun `a rate that is not a positive number is refused`() {
        for (rate in listOf("0", "-0.02", "0.0")) {
            assertFailsWith<ChipPricingException>(rate) { Chips.price(BigDecimal(rate)) }
        }
        assertFailsWith<ChipPricingException> { Chips.parseRate("twenty cents") }
        assertFailsWith<ChipPricingException> { Chips.parseRate("NaN") }
        assertEquals(BigDecimal("0.0213"), Chips.parseRate("0.0213"))
        assertEquals(0, BigDecimal("0.0213").compareTo(Chips.parseRate("2.13e-2")))
    }

    @Test
    fun `an amount too large for a u64 is refused, not wrapped`() {
        // $100 at 1e-15 USD per SKR is 1e17 SKR — 1e23 base units at 6 decimals.
        assertFailsWith<ChipPricingException> { Chips.price(BigDecimal("1e-15")) }
    }
}
