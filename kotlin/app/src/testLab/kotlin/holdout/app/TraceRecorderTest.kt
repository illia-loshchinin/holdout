package holdout.app

import holdout.app.sitting.TraceRecorder
import holdout.repcounter.Landmark
import holdout.repcounter.PoseFrame
import holdout.repcounter.PoseIndex
import holdout.repcounter.Stakes
import holdout.repcounter.Trace
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Holdout Lab traces: every set keeps its own file. */
class TraceRecorderTest {
    private val frame = PoseFrame(0, List(PoseIndex.COUNT) { Landmark(0.5f, 0.5f, 0f, 1f) })

    @Test
    fun `two sittings started in the same second write two files`() {
        val dir = Files.createTempDirectory("traces").toFile()
        val first = TraceRecorder(dir, Stakes.DAILY)
        val second = TraceRecorder(dir, Stakes.DAILY)
        first.record(1, "COUNTING", null, frame, frame, 720, 1280)
        second.record(1, "COUNTING", null, frame, frame, 720, 1280)
        first.close()
        second.close()

        assertNotEquals(first.file, second.file)
        assertEquals(2, dir.listFiles()!!.size)
        dir.listFiles()!!.forEach { assertEquals(1, Trace.parse(it.readLines()).frames.size, it.name) }
    }

    @Test
    fun `a frame arriving after close never reopens the trace`() {
        val dir = Files.createTempDirectory("traces").toFile()
        val recorder = TraceRecorder(dir, Stakes.DAILY)
        recorder.record(1, "COUNTING", null, frame, frame, 720, 1280)
        recorder.close()
        recorder.record(2, "COUNTING", null, frame, frame, 720, 1280)
        assertEquals(1, Trace.parse(recorder.file!!.readLines()).frames.size)
    }

    @Test
    fun `a trace that cannot be written says so`() {
        // A file where the trace folder should be: nothing can be written under it.
        val blocked = Files.createTempFile("traces", "").toFile()
        val recorder = TraceRecorder(File(blocked, "traces"), Stakes.DAILY)
        assertFalse(recorder.failed)
        recorder.record(1, "COUNTING", null, frame, frame, 720, 1280)
        assertTrue(recorder.failed)
    }
}
