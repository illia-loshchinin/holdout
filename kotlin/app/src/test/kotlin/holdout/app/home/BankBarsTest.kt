package holdout.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** The bank on Today's hero as bars of 10 / 10 / 5 (001 FR-069a, research R25): a block is filled only when its whole amount is banked. */
class BankBarsTest {
    @Test
    fun `the Daily 25 is three blocks - 10, 10 and 5`() {
        assertEquals(listOf(10, 10, 5), BankBars.blocks(25))
    }

    @Test
    fun `a Holdout day of 5 or 10 is one block`() {
        assertEquals(listOf(5), BankBars.blocks(5))
        assertEquals(listOf(10), BankBars.blocks(10))
    }

    @Test
    fun `no target, no blocks`() {
        assertEquals(emptyList(), BankBars.blocks(0))
    }

    @Test
    fun `a block is filled only when its whole amount is banked`() {
        assertEquals(listOf(false, false, false), BankBars.filled(25, 0))
        assertEquals(listOf(false, false, false), BankBars.filled(25, 9))
        assertEquals(listOf(true, false, false), BankBars.filled(25, 10))
        assertEquals(listOf(true, false, false), BankBars.filled(25, 19))
        assertEquals(listOf(true, true, false), BankBars.filled(25, 20))
        assertEquals(listOf(true, true, false), BankBars.filled(25, 24))
        assertEquals(listOf(true, true, true), BankBars.filled(25, 25))
    }

    @Test
    fun `the short last block fills at its own amount`() {
        assertEquals(listOf(false), BankBars.filled(5, 4))
        assertEquals(listOf(true), BankBars.filled(5, 5))
        assertEquals(listOf(true), BankBars.filled(10, 10))
        assertEquals(listOf(false), BankBars.filled(10, 9))
    }

    @Test
    fun `more banked than the target fills every block, no more`() {
        assertEquals(listOf(true, true, true), BankBars.filled(25, 40))
    }
}
