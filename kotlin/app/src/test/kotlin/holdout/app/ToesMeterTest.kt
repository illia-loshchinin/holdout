package holdout.app

import holdout.app.storage.InMemoryStore
import holdout.app.streak.ToesMeter
import holdout.repcounter.RepStance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Daily 25's toes meter (H35): how many of today's reps Hard would have counted. */
class ToesMeterTest {

    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"
    private val meter = ToesMeter(InMemoryStore(), target = 25)

    @Test
    fun `given a day of reps at 45 degrees, then the meter counts the ones from the toes out of those it could see`() {
        repeat(18) { meter.record(wallet, 100, RepStance.TOES) }
        repeat(5) { meter.record(wallet, 100, RepStance.KNEES) }
        repeat(2) { meter.record(wallet, 100, RepStance.NOT_VISIBLE) }
        val today = meter.read(wallet, 100)
        assertEquals(18, today.toes)
        assertEquals(25, today.measured, "a rep the camera could not judge still happened at 45 degrees")
        assertFalse(today.hardDay)
    }

    @Test
    fun `given head-on reps, then nothing is measured - the meter does not guess`() {
        repeat(25) { meter.record(wallet, 100, null) }
        assertEquals(0, meter.read(wallet, 100).measured)
    }

    @Test
    fun `given all 25 from the toes, then it is a Hard day`() {
        repeat(25) { meter.record(wallet, 100, RepStance.TOES) }
        assertTrue(meter.read(wallet, 100).hardDay)
    }

    @Test
    fun `given more reps than the target, then the meter stops at the target`() {
        repeat(30) { meter.record(wallet, 100, RepStance.TOES) }
        assertEquals(25, meter.read(wallet, 100).toes)
        assertEquals(25, meter.read(wallet, 100).measured)
    }

    @Test
    fun `given a week, then the bars are the last seven days, oldest first, and the best is remembered`() {
        repeat(10) { meter.record(wallet, 95, RepStance.TOES) }
        repeat(20) { meter.record(wallet, 99, RepStance.TOES) }
        repeat(12) { meter.record(wallet, 101, RepStance.TOES) }
        val today = meter.read(wallet, 101)
        assertEquals(listOf(10, 0, 0, 0, 20, 0, 12), today.week)
        assertEquals(20, today.best)
    }
}
