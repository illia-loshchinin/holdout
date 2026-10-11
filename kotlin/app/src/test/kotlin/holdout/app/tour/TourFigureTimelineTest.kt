package holdout.app.tour

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The 12 s loop of the tour figure (DS TourFigure): stand with the phone, kneel,
 * get into the top, four checks tick, hold and count 3 · 2 · 1, the checks clear,
 * three reps, rest. The timeline is the canvas's own keyframes, in milliseconds.
 */
class TourFigureTimelineTest {
    @Test
    fun `the loop is twelve seconds and wraps`() {
        assertEquals(12_000, FigureTimeline.LOOP_MILLIS)
        assertEquals(FigureTimeline.at(500), FigureTimeline.at(12_500))
        assertEquals(FigureTimeline.at(0), FigureTimeline.at(24_000))
    }

    @Test
    fun `it starts standing with the phone, nothing checked`() {
        val s = FigureTimeline.at(0)

        assertFalse(s.inFrame)
        assertFalse(s.panel)
        assertFalse(s.kneesTag)
        assertNull(s.countdown)
        assertNull(s.reps)
        assertEquals(1f, s.stepAlpha[0])
        assertEquals(0.35f, s.stepAlpha[1])
        assertEquals(0.35f, s.stepAlpha[2])
    }

    @Test
    fun `the outline turns green with the whole body in frame, and the checks tick one after another`() {
        assertEquals(0, FigureTimeline.at(3_600).checksDone)
        assertTrue(FigureTimeline.at(3_600).panel)
        assertEquals(1, FigureTimeline.at(4_000).checksDone)
        assertFalse(FigureTimeline.at(4_000).inFrame)
        assertEquals(2, FigureTimeline.at(4_400).checksDone)
        assertTrue(FigureTimeline.at(4_400).inFrame)
        assertTrue(FigureTimeline.at(4_400).kneesTag)
        assertEquals(4, FigureTimeline.at(5_200).checksDone)
        assertFalse(FigureTimeline.at(5_400).kneesTag)
    }

    @Test
    fun `the hold fills a ring, then 3 2 1`() {
        assertNull(FigureTimeline.at(5_000).countdown)
        assertEquals(0f, FigureTimeline.at(5_000).holdProgress)
        assertEquals(3, FigureTimeline.at(5_300).countdown)
        assertEquals(2, FigureTimeline.at(5_800).countdown)
        assertEquals(1, FigureTimeline.at(6_300).countdown)
        assertNull(FigureTimeline.at(6_700).countdown)
        assertEquals(1f, FigureTimeline.at(6_700).holdProgress)
        assertTrue(FigureTimeline.at(5_850).holdProgress in 0.4f..0.7f)
        assertTrue(FigureTimeline.at(6_700).holdDone)
    }

    @Test
    fun `the checks clear and three reps count`() {
        assertFalse(FigureTimeline.at(7_100).panel)
        assertEquals(0, FigureTimeline.at(7_100).reps)
        assertEquals(0, FigureTimeline.at(8_300).reps)
        assertEquals(1, FigureTimeline.at(8_500).reps)
        assertEquals(2, FigureTimeline.at(10_000).reps)
        assertEquals(3, FigureTimeline.at(11_500).reps)
        assertEquals(3, FigureTimeline.at(11_999).reps)
        assertNull(FigureTimeline.at(1_000).reps)
    }

    @Test
    fun `the numbered steps light up as the figure gets to them`() {
        assertEquals(1f, FigureTimeline.at(400).stepAlpha[0])
        assertEquals(0.35f, FigureTimeline.at(2_000).stepAlpha[0])
        assertEquals(1f, FigureTimeline.at(2_000).stepAlpha[1])
        assertEquals(0.35f, FigureTimeline.at(4_500).stepAlpha[1])
        assertEquals(1f, FigureTimeline.at(4_500).stepAlpha[2])
    }

    @Test
    fun `reduced motion shows the top pose with the checks finished and nothing moving`() {
        val s = FigureTimeline.still

        assertTrue(s.inFrame)
        assertTrue(s.panel)
        assertEquals(4, s.checksDone)
        assertTrue(s.holdDone)
        assertNull(s.countdown)
        assertNull(s.reps)
        assertEquals(listOf(1f, 1f, 1f), s.stepAlpha)
    }

    @Test
    fun `the frame schedule picks the frame whose span holds the time and wraps with the loop`() {
        val schedule = FrameSchedule(
            listOf(FrameSpan(0, 800, 0), FrameSpan(800, 900, 1), FrameSpan(900, 12_000, 2)),
        )

        assertEquals(0, schedule.frameAt(0))
        assertEquals(0, schedule.frameAt(799))
        assertEquals(1, schedule.frameAt(850))
        assertEquals(2, schedule.frameAt(900))
        assertEquals(2, schedule.frameAt(11_999))
        assertEquals(0, schedule.frameAt(12_000))
        assertEquals(1, schedule.frameAt(12_850))
    }
}
