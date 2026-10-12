package holdout.app

import holdout.app.cohort.SettlingWords
import holdout.app.reminders.ReminderNotices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The words of the not-sent notice (FR-068, 2026-10-11): it says to submit, never an amount, and counts down to the chain's deadline. */
class ReminderNoticesTest {
    @Test
    fun `given a day done here, when its notice is worded, then it says the day is not on chain and to submit it`() {
        assertEquals("Day 3 is done, but not on chain yet", ReminderNotices.notSentTitle(2))
        assertEquals(
            "Open Holdout and submit it: about 4h 30m left in your 7-day Holdout. Only the chain counts it.",
            ReminderNotices.notSentText(4 * 3_600L + 1_800, ReminderNotices.holdoutLabel(7, test = false, dayLength = 86_400)),
        )
    }

    @Test
    fun `given the short test cohort, when it is labelled, then its day length is named`() {
        assertEquals("7-day Hackathon Holdout (1-hour days)", ReminderNotices.holdoutLabel(7, test = true, dayLength = 3_600))
        assertFalse(ReminderNotices.holdoutLabel(21, test = false, dayLength = 86_400).contains("test"))
    }

    @Test
    fun `given a finished Holdout, when its notice is worded, then it says the claim is probably ready and never an amount`() {
        assertEquals("You finished your 7-day Holdout", ReminderNotices.finishedTitle(7, test = false, dayLength = 86_400))
        assertEquals("You finished your 7-day Hackathon Holdout (10-minute days)", ReminderNotices.finishedTitle(7, test = true, dayLength = 600))
        val plain = ReminderNotices.finishedText(comeback = false)
        assertEquals("Settlement usually comes within 15 minutes of the end, so your claim is probably ready: open Holdout to claim it.", plain)
        val comeback = ReminderNotices.finishedText(comeback = true)
        assertEquals("$plain It should include a comeback refund too.", comeback)
        for (text in listOf(plain, comeback)) {
            assertFalse(text.contains("SKR"))
            assertEquals("15", text.filter { it.isDigit() })
        }
    }

    @Test
    fun `given a Holdout settling, when its countdown is worded, then it says usually and never a clock time`() {
        assertEquals("You finished · settling, usually within 11m", SettlingWords.homeLine(usuallyIn = 660))
        assertEquals("You finished · settling, usually within 1m", SettlingWords.homeLine(usuallyIn = 20))
        assertEquals("You finished · settling any moment", SettlingWords.homeLine(usuallyIn = 0))
        assertEquals("Automation settles it on its next pass, usually within 15 minutes of the end. Ended 4m ago.", SettlingWords.cardBody(sinceEnd = 240, usuallyIn = 660))
        assertEquals("Automation settles it on its next pass, usually within 15 minutes of the end. Ended under a minute ago.", SettlingWords.cardBody(sinceEnd = 30, usuallyIn = 870))
        assertEquals("Taking longer than usual. It settles on the next automation pass; nothing you're owed can change.", SettlingWords.cardBody(sinceEnd = 1_200, usuallyIn = 0))
        assertEquals("You finished · settling", SettlingWords.CARD_TITLE)
    }
}
