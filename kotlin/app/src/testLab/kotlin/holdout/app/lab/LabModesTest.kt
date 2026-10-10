package holdout.app.lab

import holdout.repcounter.Stakes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** Holdout Lab's mode choice and settings (spec 004, data-model §2). */
class LabModesTest {
    @Test
    fun `each mode counts as the member app counts that sitting`() {
        assertEquals(Stakes.DAILY, LabMode.DAILY_25.stakes)
        assertEquals(Stakes.EASY, LabMode.EASY.stakes)
        assertEquals(Stakes.STAKED, LabMode.HARD.stakes)
    }

    @Test
    fun `every set banks under its own scope, so a new set never inherits reps`() {
        assertEquals("lab:EASY:1700000000000", labScope(LabMode.EASY, 1_700_000_000_000))
        assertNotEquals(labScope(LabMode.DAILY_25, 1), labScope(LabMode.DAILY_25, 2))
    }

    @Test
    fun `a fresh install records no video, draws the overlay and counts to 25`() {
        val settings = LabSettings(InMemoryLabPrefs())
        assertFalse(settings.recordsVideo)
        assertFalse(settings.cleanScreen)
        assertEquals(25, settings.target)
    }

    @Test
    fun `the target stays between 1 and 100, and settings are remembered`() {
        val prefs = InMemoryLabPrefs()
        val settings = LabSettings(prefs)
        settings.target = 0
        assertEquals(1, settings.target)
        settings.target = 500
        assertEquals(100, settings.target)
        settings.recordsVideo = true
        assertEquals(true, LabSettings(prefs).recordsVideo)
    }
}
