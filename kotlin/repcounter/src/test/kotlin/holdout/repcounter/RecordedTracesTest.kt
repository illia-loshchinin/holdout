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
        // As on the phone: the counter follows the countdown, then counts.
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
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
    fun `a plank held square side-on is read as side-on and a staked sitting never starts`() {
        val recording = Trace.parse(traces().single { it.name == "side-right-hold-01.trace.csv" }.readLines())
        assertEquals(null, recording.countingView, "the phone never started a countdown that led to counting")
        val preflight = Preflight(stakes = recording.stakes)
        val xScale = recording.frames.first().image.let { Aspect.xScale(it.width, it.height) }
        val views = recording.frames.mapNotNull { preflight.classifyView(it.image.frame, xScale) }
        // Walking in and getting down read as anything; the hold is most of the frames.
        assertTrue(views.count { it == CameraView.SIDE } > views.size * 3 / 4, "side-on in ${views.count { it == CameraView.SIDE }} of ${views.size} frames")
    }

    @Test
    fun `from 40 to 55 degrees the placement reads as 45 degrees, and half push-ups at the depth line stay refused`() {
        for (name in listOf("angled40-right-mixed-08", "angled55-right-mixed-07")) {
            val recording = Trace.parse(traces().single { it.name == "$name.trace.csv" }.readLines())
            val preflight = Preflight(stakes = recording.stakes)
            val counting = recording.countingFrames
            val xScale = counting.first().image.let { Aspect.xScale(it.width, it.height) }
            val oblique = counting.count { preflight.classifyView(it.image.frame, xScale) == CameraView.OBLIQUE }
            assertTrue(oblique > counting.size * 9 / 10, "$name: 45° in $oblique of ${counting.size} counting frames")

            val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
            for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
            val events = counting.mapNotNull { counter.onFrame(it.world, it.image) }
            // Exactly, not within one: at 40° three of the five half push-ups stopped
            // with the shoulder at 0.34–0.35 — inside the doubtful band — and the
            // elbow at 69–71°, which is what refuses them.
            assertEquals(recording.expectedReps, counter.total, name)
            assertTrue(events.count { it == RepEvent.Discarded(RepHint.GO_LOWER) } >= 5, "$name: the five half push-ups")
            assertEquals(10, events.count { it == RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES) }, "$name: five from the knees, five cobra")
        }
    }

    @Test
    fun `knees out of the picture are not counted as knees off the floor, and the member is told why`() {
        val file = traces().single { it.name == "angled-knees-out-of-frame-01.trace.csv" }
        val recording = Trace.parse(file.readLines())
        val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        val events = recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
        assertEquals(0, counter.total, "every rep was done with the knees out of camera")
        assertEquals(List(5) { RepEvent.Discarded(RepHint.LEGS_IN_VIEW) }, events)
    }

    @Test
    fun `where the phone stood is written into the header and read back, and nothing else is accepted`() {
        val (world, image) = Traces.frontFrame(1_000, elbowDegrees = 120f)
        val text = Trace.header(Stakes.STAKED, image.width, image.height) +
            Trace.row(1_000, "COUNTING", CameraView.OBLIQUE, world, image.frame, null, null)
        val labelled = Trace.parse(Trace.label(text.lines(), 5, mapOf("honest" to 5), "", placement = "50", phoneSide = "right"))
        assertEquals("50", labelled.placement)
        assertEquals("right", labelled.phoneSide)
        val forged = Trace.parse(Trace.label(text.lines(), 5, emptyMap(), "", placement = "50 expectedReps=99", phoneSide = "up"))
        assertEquals(null, forged.placement)
        assertEquals(5, forged.expectedReps)
    }

    @Test
    fun `a person's account is written into the header, and a note cannot forge a fact`() {
        val (world, image) = Traces.frontFrame(1_000, elbowDegrees = 120f)
        val text = Trace.header(Stakes.STAKED, image.width, image.height) +
            Trace.row(1_000, "COUNTING", CameraView.OBLIQUE, world, image.frame, null, null)
        val labelled = Trace.label(
            text.lines(),
            expectedReps = 10,
            done = mapOf("honest" to 10, "knees" to 5),
            note = "phone on a book\nexpectedReps=99",
        )
        val recording = Trace.parse(labelled)
        assertEquals(10, recording.expectedReps)
        assertTrue(labelled.any { it == "# done=honest:10,half:0,knees:5,cobra:0,other:0" })
        assertTrue(labelled.any { it.startsWith("# note=phone on a book expectedReps:99") })
        assertEquals(1, recording.frames.size)
    }

    @Test
    fun `the replay primes with the countdown that led into counting, not an aborted one`() {
        val (world, image) = Traces.frontFrame(1_000, elbowDegrees = 170f)
        fun row(t: Long, step: String) = Trace.row(t, step, CameraView.FRONT, world, image.frame, null, null)
        val text = Trace.header(Stakes.DAILY, image.width, image.height) +
            row(1, "PREFLIGHT") + row(2, "COUNTDOWN") + row(3, "COUNTDOWN") + // aborted: back to the checks
            row(4, "PREFLIGHT") + row(5, "COUNTDOWN") + row(6, "COUNTDOWN") + row(7, "COUNTING")
        val recording = Trace.parse(text.lines())
        assertEquals(listOf(5L, 6L), recording.countdownFrames.map { it.timestampMillis })
        assertEquals(listOf(7L), recording.countingFrames.map { it.timestampMillis })
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
