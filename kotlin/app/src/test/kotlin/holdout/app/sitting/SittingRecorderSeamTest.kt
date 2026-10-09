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

    private class FakeRecorder : SittingRecorder {
        var frames = 0
        var labels = 0
        var closes = 0
        override val fileName: String? get() = if (frames > 0) "fake.trace.csv" else null
        override fun record(timestampMillis: Long, step: String, view: CameraView?, world: PoseFrame, image: PoseFrame, width: Int, height: Int, readout: Readout?, event: RepEvent?) {
            frames += 1
        }
        override fun label(expectedReps: Int, done: Map<String, Int>, note: String, placement: String?, phoneSide: String?) {
            labels += 1
        }
        override fun close() {
            closes += 1
        }
    }

    private fun sitting(recorder: SittingRecorder?) = InMemoryStore().let { store ->
        SittingViewModel(
            store = store,
            streaks = StreakStore(store),
            wallet = "lab",
            scope = "lab:DAILY:1",
            dayIndex = Store.UNKNOWN_DAY,
            target = 25,
            chainSecondsNow = { null },
            rollsOver = false,
            recorder = recorder,
        )
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
