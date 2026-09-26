package holdout.repcounter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Counting with the phone on the floor in front of the member.
 *
 * The first test replays a real recording — two honest push-ups, filmed
 * head-on — which is what showed the world-landmark reading could not count
 * from the front at all (`ViewReading`). The rest are synthetic, and state the
 * rule as movements, as [RepCounterTest] does.
 */
class FrontViewTest {

    private fun run(frames: List<Pair<PoseFrame, ImagePose?>>, view: CameraView = CameraView.FRONT): Pair<Int, List<RepHint?>> {
        val counter = RepCounter(view = view)
        val hints = mutableListOf<RepHint?>()
        for ((world, image) in frames) {
            (counter.onFrame(world, image) as? RepEvent.Discarded)?.let { hints += it.hint }
        }
        return counter.total to hints
    }

    @Test
    fun `the recorded head-on set counts its two push-ups`() {
        val (count, _) = run(Recording.load("front-honest-01.csv"))
        assertEquals(2, count)
    }

    @Test
    fun `the same recording read the old way counts nothing - the bug this fixes`() {
        // Side-on rules read the 3D world elbow, which head-on never gets past
        // about 160° at the top or much below 90° at the bottom.
        val (count, _) = run(Recording.load("front-honest-01.csv").map { (world, _) -> world to null }, view = CameraView.SIDE)
        assertEquals(0, count)
    }

    @Test
    fun `a head-on set of 25 counts 25`() {
        val (count, hints) = run(Traces.frontSet(count = 25))
        assertEquals(25, count)
        assertTrue(hints.isEmpty(), "a clean set should produce no hints, got $hints")
    }

    @Test
    fun `head-on half reps are rejected, with Go lower`() {
        val (count, hints) = run(Traces.frontSet(count = 10, bottom = 115f))
        assertEquals(0, count)
        assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.GO_LOWER }, "expected only Go lower, got $hints")
    }

    @Test
    fun `bending the arms while upright in front of the phone counts nothing`() {
        // Kneeling up or standing, torso near vertical: the elbows can go
        // through a full range and it is still not a push-up.
        val (count, _) = run(Traces.frontSet(count = 10, torsoDegrees = 80f))
        assertEquals(0, count)
    }

    @Test
    fun `head-on reps that are too fast are still rejected, with Slow down`() {
        val (count, hints) = run(Traces.frontSet(count = 10, cycleMillis = 500))
        assertEquals(0, count)
        assertTrue(hints.contains(RepHint.SLOW_DOWN), "expected Slow down, got $hints")
    }

    @Test
    fun `a head-on frame without its picture is dropped, not scored`() {
        val (count, _) = run(Traces.frontSet(count = 5).map { (world, _) -> world to null })
        assertEquals(0, count)
    }

    @Test
    fun `the picture's elbow is measured in pixels, not in squashed normalised units`() {
        val (_, image) = Traces.frontFrame(0, elbowDegrees = 90f)
        val reading = Angles.elbowInImage(image)!!
        assertTrue(kotlin.math.abs(reading - 90f) < 0.5f, "expected 90°, read $reading")
    }

    @Test
    fun `the ready position is held head-on`() {
        val gate = ReadyGate(holdMillis = 1_000)
        val (world0, image0) = Traces.frontFrame(0, elbowDegrees = 176f)
        val (world1, image1) = Traces.frontFrame(1_000, elbowDegrees = 176f)
        gate.onFrame(world0, 0, pillsGreen = true, view = CameraView.FRONT, image = image0)
        assertTrue(gate.onFrame(world1, 1_000, pillsGreen = true, view = CameraView.FRONT, image = image1))
    }

    @Test
    fun `standing in front of the phone with straight arms is not the ready position`() {
        val gate = ReadyGate(holdMillis = 1_000)
        val (world0, image0) = Traces.frontFrame(0, elbowDegrees = 176f, torsoDegrees = 85f)
        val (world1, image1) = Traces.frontFrame(2_000, elbowDegrees = 176f, torsoDegrees = 85f)
        gate.onFrame(world0, 0, pillsGreen = true, view = CameraView.FRONT, image = image0)
        assertTrue(!gate.onFrame(world1, 2_000, pillsGreen = true, view = CameraView.FRONT, image = image1))
    }
}

/**
 * Reads a recorded trace from `fixtures/rep-traces`. The format is the CSV its
 * header describes: time, then world x y z presence for ten landmarks, then
 * image x y presence for the same ten; `# width=… height=…` gives the picture.
 */
private object Recording {
    private val INDICES = intArrayOf(
        PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER,
        PoseIndex.LEFT_ELBOW, PoseIndex.RIGHT_ELBOW,
        PoseIndex.LEFT_WRIST, PoseIndex.RIGHT_WRIST,
        PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP,
        PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE,
    )

    fun load(name: String): List<Pair<PoseFrame, ImagePose>> {
        val dir = System.getProperty("holdout.repTraces") ?: "../../fixtures/rep-traces"
        val lines = File(dir, name).readLines()
        val size = lines.first { it.startsWith("# width=") }
        val width = Regex("""width=(\d+)""").find(size)!!.groupValues[1].toInt()
        val height = Regex("""height=(\d+)""").find(size)!!.groupValues[1].toInt()

        return lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val v = line.split(',').map { it.toFloat() }
            val t = v[0].toLong()
            val world = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
            val image = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
            INDICES.forEachIndexed { k, index ->
                val w = 1 + k * 4
                world[index] = Landmark(v[w], v[w + 1], v[w + 2], v[w + 3])
                val i = 1 + INDICES.size * 4 + k * 3
                image[index] = Landmark(v[i], v[i + 1], 0f, v[i + 2])
            }
            PoseFrame(t, world) to ImagePose(PoseFrame(t, image), width, height)
        }
    }
}
