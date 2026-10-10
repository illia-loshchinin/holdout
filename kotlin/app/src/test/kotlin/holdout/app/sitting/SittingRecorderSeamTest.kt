package holdout.app.sitting

import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.vision.VisionAnalyzer
import holdout.repcounter.CameraView
import holdout.repcounter.Landmark
import holdout.repcounter.PoseFrame
import holdout.repcounter.PoseIndex
import holdout.repcounter.Readout
import holdout.repcounter.RepEvent
import holdout.repcounter.Stakes
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The seam between a sitting and whatever records it (spec 004, data-model §3).
 * Holdout Lab hands the sitting a [SittingRecorder]; the member app hands it
 * none, and then nothing about the sitting is written anywhere.
 */
class SittingRecorderSeamTest {
    private val frame = PoseFrame(0, List(PoseIndex.COUNT) { Landmark(0.5f, 0.5f, 0f, 1f) })
    private val observation = VisionAnalyzer.Observation(frame, frame, brightness = 0.5f, backgroundMotion = 0f, frameWidth = 720, frameHeight = 1280)

    private class FakeRecorder(override var failed: Boolean = false, val labelLands: Boolean = true) : SittingRecorder {
        var frames = 0
        var labels = 0
        var closes = 0

        /** The expected count of the last label; labelling runs on its own thread. */
        val expected = java.util.concurrent.LinkedBlockingQueue<Int>()
        override val fileName: String? get() = if (frames > 0) "fake.trace.csv" else null
        override fun record(timestampMillis: Long, step: String, view: CameraView?, world: PoseFrame, image: PoseFrame, width: Int, height: Int, readout: Readout?, event: RepEvent?) {
            frames += 1
        }
        override fun label(expectedReps: Int, done: Map<String, Int>, note: String, placement: String?, phoneSide: String?): Boolean {
            labels += 1
            expected.put(expectedReps)
            return labelLands
        }
        override fun close() {
            closes += 1
        }
    }

    private fun sitting(recorder: SittingRecorder?, stakes: Stakes = Stakes.DAILY) = InMemoryStore().let { store ->
        SittingViewModel(
            store = store,
            streaks = StreakStore(store),
            wallet = "lab",
            scope = "lab:${stakes.name}:1",
            dayIndex = Store.UNKNOWN_DAY,
            target = 25,
            chainSecondsNow = { null },
            rollsOver = false,
            stakes = stakes,
            recorder = recorder,
        )
    }

    private fun expectedFor(stakes: Stakes): Int {
        val recorder = FakeRecorder()
        val vm = sitting(recorder, stakes)
        vm.onPoseFrame(observation, backgroundMotion = 0f)
        vm.endSitting()
        vm.labelTrace(mapOf("honest" to 5, "knees" to 3, "half" to 2), "note")
        return recorder.expected.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!
    }

    @Test
    fun `given a Lab set, when it ends, then it is labelled if its trace was written and reported not saved otherwise`() {
        val written = sitting(FakeRecorder())
        written.onPoseFrame(observation, backgroundMotion = 0f)
        written.endSitting()
        assertEquals(SittingViewModel.LabEnding.LABEL, written.labEnding)

        // Stopped before the first frame: the camera never started, or the tester stopped at once.
        val empty = sitting(FakeRecorder())
        empty.endSitting()
        assertEquals(SittingViewModel.LabEnding.NOT_SAVED, empty.labEnding)

        val failing = FakeRecorder()
        val failed = sitting(failing)
        failed.onPoseFrame(observation, backgroundMotion = 0f)
        failing.failed = true
        failed.endSitting()
        assertEquals(SittingViewModel.LabEnding.NOT_SAVED, failed.labEnding)

        // A member's sitting has no recorder and no Lab ending.
        val member = sitting(recorder = null)
        member.endSitting()
        assertEquals(null, member.labEnding)
    }

    @Test
    fun `given a labelled set, then push-ups from the knees are expected wherever knees are not judged`() {
        assertEquals(8, expectedFor(Stakes.DAILY))
        assertEquals(8, expectedFor(Stakes.EASY))
        assertEquals(5, expectedFor(Stakes.STAKED))
    }

    @Test
    fun `given a recorder, when frames arrive and the set ends, then it records, can be labelled and closes`() {
        val recorder = FakeRecorder()
        val vm = sitting(recorder)
        repeat(3) { vm.onPoseFrame(observation, backgroundMotion = 0f) }
        assertEquals(3, recorder.frames)
        assertTrue(vm.canLabelTrace)
        vm.endSitting()
        assertTrue(recorder.closes >= 1)
    }

    @Test
    fun `given a recorder that failed to write, when the set ends, then the sitting says it was not saved and asks for no label`() {
        val recorder = FakeRecorder()
        val vm = sitting(recorder)
        vm.onPoseFrame(observation, backgroundMotion = 0f)
        assertFalse(vm.traceFailed)
        recorder.failed = true
        vm.onPoseFrame(observation, backgroundMotion = 0f)
        vm.endSitting()
        assertTrue(vm.traceFailed)
        assertTrue(vm.uiState.value.traceFailed)
        assertFalse(vm.canLabelTrace)
    }

    @Test
    fun `given a label the recorder could not write, when the tester saves it, then the loss is reported`() {
        val recorder = FakeRecorder(labelLands = false)
        val vm = sitting(recorder)
        vm.onPoseFrame(observation, backgroundMotion = 0f)
        vm.endSitting()
        val lost = java.util.concurrent.LinkedBlockingQueue<String>()
        vm.labelTrace(mapOf("honest" to 3), "note", onLost = { lost.put(it) })
        val what = lost.poll(5, java.util.concurrent.TimeUnit.SECONDS)
        vm.release()
        assertTrue(what != null && "fake.trace.csv" in what, "the loss names the trace: $what")
    }

    @Test
    fun `given no recorder, when frames arrive and the set ends, then nothing is written and nothing can be labelled`() {
        val dir = Files.createTempDirectory("member").toFile()
        val vm = sitting(recorder = null)
        repeat(3) { vm.onPoseFrame(observation, backgroundMotion = 0f) }
        vm.labelTrace(mapOf("honest" to 3), "note")
        vm.endSitting()
        vm.release()
        assertFalse(vm.canLabelTrace)
        assertEquals(0, dir.walkTopDown().count { it.isFile })
    }
}
