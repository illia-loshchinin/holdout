package holdout.app.cohort

import org.junit.Test
import kotlin.test.assertEquals

/** The settling card as the board draws it (H4, FR-035a): the usual-time sentence, then the time since the end and what is left. */
class SettlingCardWordsTest {
    @Test
    fun `inside the usual wait - the sentence, then the time since the end and the minutes left of the 15`() {
        assertEquals("Automation settles it on its next pass, usually within 15 minutes of the end.", SettlingWords.cardLead(usuallyIn = 660))
        assertEquals("Ended 4m ago · 11m left of those 15", SettlingWords.cardDetail(sinceEnd = 240, usuallyIn = 660))
    }

    @Test
    fun `under a minute since the end`() {
        assertEquals("Ended under a minute ago · 15m left of those 15", SettlingWords.cardDetail(sinceEnd = 30, usuallyIn = 870))
    }

    @Test
    fun `the last seconds count as a minute left, never zero`() {
        assertEquals("Ended 14m ago · 1m left of those 15", SettlingWords.cardDetail(sinceEnd = 880, usuallyIn = 20))
    }

    @Test
    fun `past the usual wait - taking longer, and nothing owed can change`() {
        assertEquals("Taking longer than usual. It settles on the next automation pass; nothing you're owed can change.", SettlingWords.cardLead(usuallyIn = 0))
        assertEquals("Ended 19m ago", SettlingWords.cardDetail(sinceEnd = 1_140, usuallyIn = 0))
    }

    @Test
    fun `the lead and the detail together say what the one-sentence body says`() {
        val joined = SettlingWords.cardLead(660) + " " + SettlingWords.cardDetail(240, 660).substringBefore(" ·") + "."
        assertEquals(SettlingWords.cardBody(240, 660), joined)
    }
}
