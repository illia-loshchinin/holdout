package holdout.app.tour

import kotlin.test.Test
import kotlin.test.assertEquals

/** The worked example on tour card 2: every figure follows from 25 members, 7 finishers and a 1,250 SKR stake. */
class TourExampleTest {
    @Test
    fun `the arithmetic on the card is the arithmetic of the split`() {
        val e = TourExample

        assertEquals(25, e.MEMBERS)
        assertEquals(7, e.FINISHERS)
        assertEquals(18, e.lost)
        assertEquals(22_500L, e.lostStakes)
        assertEquals(15_750L, e.prizePool)
        assertEquals(2_250L, e.sharePerFinisher)
        assertEquals(3_500L, e.finisherTakes)
    }

    @Test
    fun `the seven finishers stand as an H on a five by five grid`() {
        val standing = TourExample.standing

        assertEquals(7, standing.size)
        assertEquals(setOf(1 to 1, 1 to 3, 2 to 1, 2 to 2, 2 to 3, 3 to 1, 3 to 3), standing)
    }

    @Test
    fun `amounts read as the rest of the app writes SKR`() {
        assertEquals("1,250 SKR", TourExample.skr(1_250))
        assertEquals("22,500 SKR", TourExample.skr(TourExample.lostStakes))
    }
}
