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
        return dir.listFiles { f -> TraceFiles.isTrace(f) }?.sortedBy { it.name }.orEmpty()
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
            // A labelled recording is held rep by rep by the ratchet (RepRatchetTest), not by its total.
            if (TraceFiles.labels(file).exists()) return@mapNotNull null
            val recording = Trace.parse(TraceFiles.lines(file))
            val expected = recording.expectedReps ?: return@mapNotNull null
            // A sitting today's app would not start (head-on, or in front of its band) is evidence, not a test.
            if (!recording.startsToday()) return@mapNotNull null
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
    fun `full reps that stop just over the depth line count when the elbow closed`() {
        // Twelve honest reps from a spot near 40°; three stopped with the shoulder
        // 0.33–0.34 arm-lengths over the hand, elbow at 62–64°, and had been refused.
        val recording = Trace.parse(traces().single { it.name == "angled-right-honest-10.trace.csv" }.readLines())
        assertEquals(12, replay(recording))
    }

    @Test
    fun `a rep let down from the knees and pushed up on the toes is refused`() {
        val recording = Trace.parse(traces().single { it.name == "angled60-right-knees-down-up-09.trace.csv" }.readLines())
        val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        val events = recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
        // Five honest, then knees all the way: the sixth count was the one whose
        // push alone was on the toes.
        assertEquals(5, counter.total)
        assertEquals(List(5) { true }, events.take(5).map { it is RepEvent.Counted })
        assertEquals(12, events.count { it == RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES) })
    }

    @Test
    fun `kneeling with the feet raised is refused though the legs read high`() {
        // One honest rep, half reps, then push-ups from the knees with the feet up:
        // knee 90–112° and legs 0.43–0.45, between the two knee lines. Five had counted.
        val recording = Trace.parse(traces().single { it.name == "angled30-right-knees-feetup-13.trace.csv" }.readLines())
        val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        val events = recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
        assertEquals(10, events.count { it == RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES) })
        assertTrue(events.takeLast(10).none { it is RepEvent.Counted }, "nothing counts once the knees are down")
    }

    @Test
    fun `a half push-up on the toes is told to go lower, not to get the knees off the floor`() {
        // 45°: ten full, five from the knees, four pushed up from the floor, one with the body
        // bent, then half push-ups on the toes. Live, four halves were refused as knees: the leg
        // a little bent (knee 127–132°) and the legs at 0.41–0.48, on or between the knee lines.
        val recording = Trace.parse(traces().single { it.name == "angled45-right-mixed-18.trace.csv" }.readLines())
        val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        val events = recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
        assertEquals(List(10) { true }, events.take(10).map { it is RepEvent.Counted })
        assertEquals(List(9) { RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES) }, events.subList(10, 19), "the knee reps and those pushed from the floor")
        val halves = events.drop(20)
        // 1:59, and 2:07 at the depth line (shoulder 0.317, line 0.32): full, the member says.
        // 2:07 read knee 130°, legs 0.47 on the push and was refused as knees; it came down at 135°.
        assertEquals(2, halves.count { it is RepEvent.Counted }, "the two full ones among them")
        assertEquals(8, halves.count { it == RepEvent.Discarded(RepHint.GO_LOWER) })
        assertEquals(0, halves.count { it == RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES) })
        assertEquals(12, counter.total)
    }

    @Test
    fun `a second person's knee push-ups are refused against his own plank`() {
        // 4 Oct, a second person at about 40°: his knee reps read the legs at 0.42–0.44 over
        // the hands — over the 0.41 line, set on one body — and two had counted. His own
        // plank held 0.70; those reps read 0.60–0.63 of it, honest reps everywhere 0.70 or more.
        val recording = Trace.parse(traces().single { it.name == "angled40-right-mixed-19.trace.csv" }.readLines())
        val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        val events = recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
        assertEquals(List(10) { true }, events.subList(2, 12).map { it is RepEvent.Counted }, "the ten full reps")
        assertEquals(List(5) { true }, events.subList(12, 17).map { it is RepEvent.Discarded }, "the five from the knees, 3:58–4:25")
        assertEquals(12, counter.total, "two of the three cobras still count: a named weakness")
    }

    @Test
    fun `the same knee push-ups count in a Daily 25, and the toes meter does not claim them for the toes`() {
        // The Daily 25 counts knee reps by design, but tags each with whether Hard would
        // have: against this person's own plank on the toes, these are knees (PR #14 review).
        val recording = Trace.parse(traces().single { it.name == "angled40-right-mixed-19.trace.csv" }.readLines())
        fun timed(stakes: Stakes): Map<Long, RepEvent> {
            val counter = RepCounter(view = recording.countingView!!, stakes = stakes)
            for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
            return recording.countingFrames.mapNotNull { f -> counter.onFrame(f.world, f.image)?.let { f.timestampMillis to it } }.toMap()
        }
        val hard = timed(Stakes.STAKED)
        val daily = timed(Stakes.DAILY)
        // Every rep Hard refuses as knees down that the Daily 25 counts is tagged as knees.
        val kneesDown = hard.filterValues { it == RepEvent.Discarded(RepHint.UP_ON_YOUR_TOES) }.keys
        val countedDaily = kneesDown.mapNotNull { daily[it] as? RepEvent.Counted }
        assertTrue(countedDaily.size >= 4, "the knee reps count in a Daily 25: ${kneesDown.map { daily[it] }}")
        assertEquals(List(countedDaily.size) { RepStance.KNEES }, countedDaily.map { it.stance })
        // And every rep Hard counts is on the toes.
        val countedHard = hard.filterValues { it is RepEvent.Counted }.keys
        assertTrue(countedHard.all { (daily[it] as? RepEvent.Counted)?.stance == RepStance.TOES }, "Hard's reps are toes reps")
    }

    @Test
    fun `a rep pushed up from lying on the floor is refused - the body must come up as one piece`() {
        // Phone on the left at 45°: eleven full reps, five from the knees, two cobras, then
        // three let down onto the floor and pushed up from there. One of those had counted.
        val recording = Trace.parse(traces().single { it.name == "angled45-left-floor-start-15.trace.csv" }.readLines())
        val counter = RepCounter(view = recording.countingView!!, stakes = recording.stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        val events = recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
        assertEquals(9, counter.total, "nine of the eleven full reps: the foot at the picture's edge cost the other two")
        assertEquals(2, events.count { it == RepEvent.Discarded(RepHint.LEGS_IN_VIEW) })
        assertTrue(events.drop(11).none { it is RepEvent.Counted }, "nothing after the full reps counts")
        assertEquals(1, events.count { it == RepEvent.Discarded(RepHint.KEEP_BODY_STRAIGHT) }, "the one whose legs read honest: the chest came up first")
    }

    /** [recording] replayed as a sitting at [stakes] rather than the stakes it was recorded at. */
    private fun eventsAs(recording: Trace.Recording, stakes: Stakes): List<RepEvent> {
        val counter = RepCounter(view = recording.countingView!!, stakes = stakes)
        for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
        return recording.countingFrames.mapNotNull { counter.onFrame(it.world, it.image) }
    }

    @Test
    fun `push-ups from the knees with the feet raised count where knees count - straight from shoulder to knee is enough`() {
        // Ten push-ups from the knees, feet in the air. Read shoulder–hip–ankle the raised
        // feet put three under the floor (138–142°), refused "Keep your body straight"
        // where Easy and the Daily 25 allow them (Codex P1, PR #18).
        val recording = Trace.parse(traces().single { it.name == "angled-right-knees-feetup-01.trace.csv" }.readLines())
        for (stakes in listOf(Stakes.EASY, Stakes.DAILY)) {
            assertEquals(List(10) { true }, eventsAs(recording, stakes).take(10).map { it is RepEvent.Counted }, "$stakes")
        }
        assertTrue(eventsAs(recording, Stakes.STAKED).none { it is RepEvent.Counted }, "Hard still refuses them")
    }

    @Test
    fun `where knees count, cobras from a placement Easy accepts are still refused`() {
        // Held ratios 0.44 and 0.56, inside Easy's 0.90: the knee line reads a cobra
        // straighter than the ankle line does, and the hip rise still refuses all of them.
        for ((name, cobras) in listOf("angled45-left-mixed-11" to 7, "angled55-right-mixed-07" to 5)) {
            val recording = Trace.parse(traces().single { it.name == "$name.trace.csv" }.readLines())
            val events = eventsAs(recording, Stakes.EASY)
            assertEquals(cobras, events.count { it == RepEvent.Discarded(RepHint.KEEP_BODY_STRAIGHT) }, name)
        }
    }

    @Test
    fun `cobras with the body nearly straight are refused, on a second person`() {
        val recording = Trace.parse(traces().single { it.name == "angled45-right-loose-trousers-cobra-16.trace.csv" }.readLines())
        assertEquals(0, replay(recording), "three had counted: their body line read 145–150°, over the cobra line")
    }

    @Test
    fun `a knee the model puts on the ankle is not read, and the hold says so`() {
        // A proper plank in loose white trousers: the knee angle read 10–120° and the hold never said why.
        val recording = Trace.parse(traces().single { it.name == "angled45-right-loose-trousers-hold-17.trace.csv" }.readLines())
        assertEquals(null, recording.countingView)
        val gate = ReadyGate(stakes = Stakes.STAKED)
        val blockers = recording.frames.filter { it.view == CameraView.OBLIQUE }.mapNotNull { frame ->
            gate.onFrame(frame.world, frame.world.timestampMillis, pillsGreen = true, view = CameraView.OBLIQUE, image = frame.image)
            gate.blocker
        }
        val unreadable = blockers.count { it == HoldBlocker.KNEES_UNREADABLE }
        assertTrue(unreadable > blockers.size / 2, "the knees unreadable in $unreadable of ${blockers.size} blocked frames")
    }

    @Test
    fun `a staked sitting does not start from in front of about 35 degrees, and does from 40`() {
        fun held(name: String): Pair<Int, Int> {
            val recording = Trace.parse(traces().single { it.name == "$name.trace.csv" }.readLines())
            val preflight = Preflight(stakes = Stakes.STAKED)
            val frames = recording.countdownFrames
            val xScale = frames.first().image.let { Aspect.xScale(it.width, it.height) }
            return frames.count { preflight.classifyView(it.image.frame, xScale) == CameraView.OBLIQUE } to frames.size
        }
        // Recorded at about 30°, close to the body, before the line: the plank held through its countdown.
        val (accepted30, frames30) = held("angled30-right-honest-12")
        assertEquals(0, accepted30, "30°: accepted in $accepted30 of $frames30 countdown frames")
        // The most frontal spot the band still takes: a little in front of 40°.
        val (accepted40, frames40) = held("angled-right-honest-10")
        assertEquals(frames40, accepted40, "near 40°: accepted in $accepted40 of $frames40 countdown frames")
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

    /** A frame at 45° — world landmarks and the picture of the same instant. */
    private fun atFortyFive(t: Long, elbowDegrees: Float) = Traces.frame(t, elbowDegrees, 180f) to Traces.angledImage(t, elbowDegrees)

    @Test
    fun `where the phone stood is written into the header and read back, and nothing else is accepted`() {
        val (world, image) = atFortyFive(1_000, elbowDegrees = 120f)
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
        val (world, image) = atFortyFive(1_000, elbowDegrees = 120f)
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
        val (world, image) = atFortyFive(1_000, elbowDegrees = 170f)
        fun row(t: Long, step: String) = Trace.row(t, step, CameraView.OBLIQUE, world, image.frame, null, null)
        val text = Trace.header(Stakes.DAILY, image.width, image.height) +
            row(1, "PREFLIGHT") + row(2, "COUNTDOWN") + row(3, "COUNTDOWN") + // aborted: back to the checks
            row(4, "PREFLIGHT") + row(5, "COUNTDOWN") + row(6, "COUNTDOWN") + row(7, "COUNTING")
        val recording = Trace.parse(text.lines())
        assertEquals(listOf(5L, 6L), recording.countdownFrames.map { it.timestampMillis })
        assertEquals(listOf(7L), recording.countingFrames.map { it.timestampMillis })
    }

    @Test
    fun `a trace written by the recorder reads back frame for frame`() {
        val (world, image) = atFortyFive(1_000, elbowDegrees = 120f)
        val text = Trace.header(Stakes.DAILY, image.width, image.height) +
            Trace.row(1_000, "COUNTING", CameraView.OBLIQUE, world, image.frame, Readout(CameraView.OBLIQUE, 120f, 175f, 178f), RepEvent.Counted(1, 1_500))
        val recording = Trace.parse(text.lines())
        assertEquals(Stakes.DAILY, recording.stakes)
        assertEquals(null, recording.expectedReps)
        assertEquals(CameraView.OBLIQUE, recording.countingView)
        val frame = recording.frames.single()
        assertEquals("counted", frame.event)
        val reread = Angles.elbowInImage(frame.image, Side.LEFT)!!
        assertTrue(kotlin.math.abs(reread - 120f) < 0.5f, "the picture survives the round trip: $reread")
    }
}
