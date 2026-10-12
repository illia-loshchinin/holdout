package holdout.app.sitting

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the pre-flight tip sheet is offered: 30 s short of all green in total, or the third green-to-red flip (Gap 1, screen map S3). */
class PreflightTipsOfferTest {
    private val green = listOf(true, true, true, true, true)

    private fun red(vararg at: Int) = green.mapIndexed { i, ok -> ok && i !in at }

    /** Frames every 100 ms from [from] for [seconds], all with the same [pills]; returns the time after the last one. */
    private fun PreflightTipsOffer.run(from: Long, seconds: Int, pills: List<Boolean>, reading: Boolean = false): Long {
        var t = from
        repeat(seconds * 10) {
            onReport(t, pills, reading)
            t += 100
        }
        return t
    }

    @Test
    fun `not offered before anything has been seen`() {
        assertFalse(PreflightTipsOffer().offered)
    }

    @Test
    fun `29 seconds short of green is not enough`() {
        val offer = PreflightTipsOffer()
        offer.run(0, 29, red(0))
        assertFalse(offer.offered)
    }

    @Test
    fun `30 seconds short of green offers the tips`() {
        val offer = PreflightTipsOffer()
        offer.run(0, 31, red(0))
        assertTrue(offer.offered)
    }

    @Test
    fun `the 30 seconds are in total, not in a row`() {
        val offer = PreflightTipsOffer()
        var t = offer.run(0, 20, red(0))
        t = offer.run(t, 40, green)
        assertFalse(offer.offered)
        offer.run(t, 11, red(1))
        assertTrue(offer.offered)
    }

    @Test
    fun `time with every check green does not count`() {
        val offer = PreflightTipsOffer()
        offer.run(0, 120, green)
        assertFalse(offer.offered)
    }

    @Test
    fun `a single red pill, the advisory one too, counts as short of green`() {
        val offer = PreflightTipsOffer()
        offer.run(0, 31, red(4))
        assertTrue(offer.offered)
    }

    @Test
    fun `time spent reading the set-up guide does not count`() {
        val offer = PreflightTipsOffer()
        offer.run(0, 120, red(0), reading = true)
        assertFalse(offer.offered)
    }

    @Test
    fun `a pause between frames is not counted as time short of green`() {
        val offer = PreflightTipsOffer()
        offer.onReport(0, red(0), false)
        offer.onReport(120_000, red(0), false)
        assertFalse(offer.offered)
    }

    @Test
    fun `two flips from green to red are not enough`() {
        val offer = PreflightTipsOffer()
        offer.onReport(0, green, false)
        offer.onReport(100, red(0), false)
        offer.onReport(200, green, false)
        offer.onReport(300, red(2), false)
        assertFalse(offer.offered)
    }

    @Test
    fun `the third flip from green to red offers the tips at once`() {
        val offer = PreflightTipsOffer()
        offer.onReport(0, green, false)
        offer.onReport(100, red(0), false)
        offer.onReport(200, green, false)
        offer.onReport(300, red(2), false)
        offer.onReport(400, green, false)
        assertFalse(offer.offered)
        offer.onReport(500, red(3), false)
        assertTrue(offer.offered)
    }

    @Test
    fun `a check that stays red is one flip, not one per frame`() {
        val offer = PreflightTipsOffer()
        offer.onReport(0, green, false)
        repeat(20) { offer.onReport(100L * (it + 1), red(0), false) }
        assertFalse(offer.offered)
    }

    @Test
    fun `another check turning red while one is already red is a flip`() {
        val offer = PreflightTipsOffer()
        offer.onReport(0, green, false)
        offer.onReport(100, red(0), false)
        offer.onReport(200, red(0, 1), false)
        offer.onReport(300, red(0, 1, 2), false)
        assertTrue(offer.offered)
    }

    @Test
    fun `a check red from the first frame has not turned red`() {
        val offer = PreflightTipsOffer()
        offer.onReport(0, red(0), false)
        offer.onReport(100, red(0), false)
        assertFalse(offer.offered)
    }

    @Test
    fun `once offered it stays offered, whatever the checks do after`() {
        val offer = PreflightTipsOffer()
        val t = offer.run(0, 31, red(0))
        assertTrue(offer.offered)
        offer.run(t, 60, green)
        assertTrue(offer.offered)
    }

    @Test
    fun `a new sitting starts with nothing offered`() {
        PreflightTipsOffer().run(0, 31, red(0))
        assertFalse(PreflightTipsOffer().offered)
    }
}
