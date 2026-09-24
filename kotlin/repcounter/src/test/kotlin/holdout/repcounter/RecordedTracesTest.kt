package holdout.repcounter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every sitting recorded on a phone and committed under `fixtures/rep-traces/`
 * is replayed here, and must count within one rep of what a person counted —
 * spike S2's bar (fixtures/rep-traces/README.md). A trace whose header still
 * says `expectedReps=?` has not been counted by anyone, so it is skipped
 * rather than guessed at.
 *
 * This is how a counting bug found on the floor becomes a test: record it,
 * write the true count into the header, commit it.
 */
class RecordedTracesTest {

    private fun traces(): List<File> {
        val dir = File(System.getProperty("holdout.repTraces") ?: "../../fixtures/rep-traces")
        return dir.listFiles { f -> f.name.endsWith(".trace.csv") }?.sortedBy { it.name }.orEmpty()
    }

    private fun replay(recording: Trace.Recording): Int {
        val view = recording.countingView ?: return 0
        val counter = RepCounter(view = view, stakes = recording.stakes)
        for (frame in recording.countingFrames) counter.onFrame(frame.world, frame.image)
        return counter.total
    }

    @Test
    fun `every counted recording replays within one rep of the human count`() {
        val misses = traces().mapNotNull { file ->
            val recording = Trace.parse(file.readLines())
            val expected = recording.expectedReps ?: return@mapNotNull null
            val counted = replay(recording)
            if (kotlin.math.abs(counted - expected) <= 1) null else "${file.name}: counted $counted, a person counted $expected"
        }
        assertTrue(misses.isEmpty(), misses.joinToString("\n"))
    }

    @Test
    fun `a trace written by the recorder reads back frame for frame`() {
        val (world, image) = Traces.frontFrame(1_000, elbowDegrees = 120f)
        val text = Trace.header(Stakes.DAILY, image.width, image.height) +
            Trace.row(1_000, "COUNTING", CameraView.FRONT, world, image.frame, Readout(CameraView.FRONT, 120f, 30f, null), RepEvent.Counted(1, 1_500))
        val recording = Trace.parse(text.lines())
        assertEquals(Stakes.DAILY, recording.stakes)
        assertEquals(null, recording.expectedReps)
        assertEquals(CameraView.FRONT, recording.countingView)
        val frame = recording.frames.single()
        assertEquals("counted", frame.event)
        val reread = Angles.elbowInImage(frame.image)!!
        assertTrue(kotlin.math.abs(reread - 120f) < 0.5f, "the picture survives the round trip: $reread")
    }
}
