package holdout.app.sitting

import holdout.repcounter.Coach
import holdout.repcounter.RepHint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every line a sitting can say has a recorded clip (scripts/gen-sitting-voice.py). */
class VoiceClipsTest {
    private val clips = File("src/main/assets/${VoiceClips.DIR}").list().orEmpty().toSet()

    private val lines = listOf("three", "two", "one", "go", "Done") +
        Coach.entries.map { it.words } +
        RepHint.entries.map { it.message } +
        (10..300 step 10).map { "$it banked" }

    @Test
    fun `every line has a clip`() {
        val missing = lines.filter { "${VoiceClips.slug(it)}.mp3" !in clips }
        assertTrue(missing.isEmpty(), "no clip for $missing — add them to scripts/gen-sitting-voice.py and re-run it")
    }

    @Test
    fun `no two lines share a clip`() {
        assertEquals(lines.size, lines.map(VoiceClips::slug).toSet().size)
    }

    @Test
    fun `the name of a clip`() {
        assertEquals("phone-to-45deg", VoiceClips.slug("Phone to 45°"))
        assertEquals("can-t-see-knees", VoiceClips.slug("Can't see knees"))
        assertEquals("20-banked", VoiceClips.slug("20 banked"))
    }
}
