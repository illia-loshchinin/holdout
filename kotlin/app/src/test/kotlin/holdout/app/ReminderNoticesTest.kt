package holdout.app

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
        assertEquals("7-day Holdout · short test cohort, 1-hour days", ReminderNotices.holdoutLabel(7, test = true, dayLength = 3_600))
        assertFalse(ReminderNotices.holdoutLabel(21, test = false, dayLength = 86_400).contains("test"))
    }
}
