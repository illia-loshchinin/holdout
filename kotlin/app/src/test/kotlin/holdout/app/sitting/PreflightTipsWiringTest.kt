package holdout.app.sitting

import holdout.app.storage.InMemoryStore
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.vision.VisionAnalyzer
import holdout.repcounter.PoseFrame
import holdout.repcounter.Stakes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sitting offers the tip sheet from its pre-flight frames, opens and closes it, and never opens it by itself (Gap 1, S3). */
class PreflightTipsWiringTest {
    private val wallet = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU"

    private fun sitting(guideHidden: Boolean = true): SittingViewModel {
        val store = InMemoryStore()
        if (guideHidden) store.hideSetupGuide(wallet, Stakes.DAILY.name)
        return SittingViewModel(store, StreakStore(store), wallet, Store.CHALLENGE, 21_000L, 25, chainSecondsNow = { null })
    }

    /** A frame with nobody in it: only the lighting can be green, and it is when [bright]. */
    private fun frame(at: Long, bright: Boolean = false): VisionAnalyzer.Observation {
        val pose = PoseFrame(at, emptyList())
        return VisionAnalyzer.Observation(pose, pose, brightness = if (bright) 0.5f else 0f, backgroundMotion = 0f, frameWidth = 720, frameHeight = 1280)
    }

    @Test
    fun `a fresh sitting offers nothing and shows nothing`() {
        val state = sitting().uiState.value
        assertFalse(state.tipsOffered)
        assertFalse(state.showTips)
    }

    @Test
    fun `half a minute of frames short of green offers the tips, and does not open the sheet`() {
        val vm = sitting()
        for (i in 0..250) vm.onPoseFrame(frame(i * 100L), 0f)
        assertFalse(vm.uiState.value.tipsOffered)
        for (i in 251..320) vm.onPoseFrame(frame(i * 100L), 0f)
        assertTrue(vm.uiState.value.tipsOffered)
        assertFalse(vm.uiState.value.showTips)
    }

    @Test
    fun `the third time the lighting turns red offers the tips`() {
        val vm = sitting()
        var t = 0L
        repeat(2) {
            vm.onPoseFrame(frame(t, bright = true), 0f)
            vm.onPoseFrame(frame(t + 100), 0f)
            t += 200
        }
        assertFalse(vm.uiState.value.tipsOffered)
        vm.onPoseFrame(frame(t, bright = true), 0f)
        vm.onPoseFrame(frame(t + 100), 0f)
        assertTrue(vm.uiState.value.tipsOffered)
    }

    @Test
    fun `the time the set-up guide is open is not short-of-green time`() {
        val vm = sitting(guideHidden = false)
        assertTrue(vm.uiState.value.showSetup)
        for (i in 0..400) vm.onPoseFrame(frame(i * 100L), 0f)
        assertFalse(vm.uiState.value.tipsOffered)
    }

    @Test
    fun `openTips and closeTips toggle the sheet and leave the sitting in its checks`() {
        val vm = sitting()
        vm.openTips()
        assertTrue(vm.uiState.value.showTips)
        vm.closeTips()
        assertFalse(vm.uiState.value.showTips)
        assertEquals(SittingViewModel.Step.PREFLIGHT, vm.uiState.value.step)
    }

    @Test
    fun `opening the set-up guide from the tips closes the tips`() {
        val vm = sitting()
        vm.openTips()
        vm.openSetup()
        assertTrue(vm.uiState.value.showSetup)
        assertFalse(vm.uiState.value.showTips)
    }

    @Test
    fun `closing the tips starts nothing and offers nothing new`() {
        val vm = sitting()
        vm.openTips()
        val before = vm.uiState.value
        vm.closeTips()
        assertEquals(before.copy(showTips = false), vm.uiState.value)
    }
}
