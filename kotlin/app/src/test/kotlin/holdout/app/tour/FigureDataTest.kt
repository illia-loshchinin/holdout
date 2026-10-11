package holdout.app.tour

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The generated data file the tour figure is drawn from (scripts/gen-tour-figure.py): complete, and read the way the generator writes it. */
class FigureDataTest {
    private val sample = """
        # a comment
        V 1
        D 0 M0 0 L10 10
        K 0 800 0
        K 800 12000 1
        N scene
        P 0 s=@accent sw=1.5 lc=round
        N outline
        R 141.1 123.7 198.1 82.3 14
        N knee
        T 282 226 0 11 400 0 mb sans f=#B2FFFFFF | knees in view
        N frame0
        G 12 12 0
        C 34 36 4.2 f=@accent
        Z
        N frame1
        E 1 2 3 4 5 f=#FF568AA4 fo=0.5
    """.trimIndent() + (2 until 36).joinToString("") { "\nN frame$it" }

    @Test
    fun `it reads sections, the path dictionary and the schedule`() {
        val data = FigureData.parse(sample.lineSequence())

        assertEquals(36, data.frames.size)
        assertEquals(1, data.scene.size)
        assertEquals("M0 0 L10 10", (data.scene.single() as FigureOp.PathOp).data)
        assertEquals(141.1f, data.outline.x)
        assertEquals("knees in view", (data.knee.single() as FigureOp.Text).text)
        assertEquals(3, data.frames[0].size)
        assertEquals(0, data.schedule.frameAt(799))
        assertEquals(1, data.schedule.frameAt(800))
    }

    @Test
    fun `the generated file holds the scene, all 36 poses and a schedule over the whole loop`() {
        val file = File("src/main/assets/$ASSET")
        val data = file.inputStream().use { FigureData.parse(it) }

        assertEquals(36, data.frames.size)
        assertTrue(data.frames.all { it.isNotEmpty() })
        assertTrue(data.scene.isNotEmpty())
        // Every moment of the loop has a pose, and the top pose is the one that holds the longest.
        val frames = (0 until FigureTimeline.LOOP_MILLIS step 50).map { data.schedule.frameAt(it.toLong()) }
        assertTrue(frames.all { it in 0 until 36 })
        assertEquals(FigureTimeline.STILL_FRAME, data.schedule.frameAt(5_000))
        assertEquals(FigureTimeline.STILL_FRAME, data.schedule.frameAt(11_900))
        assertEquals(0, data.schedule.frameAt(0))
    }
}
