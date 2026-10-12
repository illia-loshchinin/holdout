package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.core.CohortSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 001 FR-024b, 2026-10-10: a day completed while the chain can't be reached says it is not sent yet, and how it gets sent. */
class OfflineNoteTest {
    private val start = 1_791_176_400L
    private val holdout = Holdout(
        address = SolanaPublicKey(ByteArray(32) { 7 }),
        cohort = CohortSnapshot(0, 0, start, start - 3_600, 7, 25, 86_400, 0, listOf(1uL, 2uL, 3uL, 4uL)),
        league = null,
        membership = null,
    )

    // Day 3 (index 2) ends at start + 3 * 86_400 - 1.
    private val day3Deadline = start + 3 * 86_400 - 1

    @Test
    fun `the note names the day, the reps saved on the phone, the time left to that day's deadline and the wallet`() {
        // 4h 06m before day 3's deadline.
        val (title, sentence) = offlineNote(holdout, day = 2, nowSeconds = day3Deadline - (4 * 3_600 + 6 * 60), network = "Devnet")
        assertEquals("Done — not sent yet", title)
        assertEquals(
            "Devnet can't be reached, so day 3 isn't on chain yet. Your 25 are saved on this phone. " +
                "Open Holdout when it can be, before the deadline (4h 06m left), and approve day 3 in your wallet.",
            sentence,
        )
    }

    @Test
    fun `past that day's deadline the day can no longer be sent, and the note does not count down to tomorrow's`() {
        // One minute into day 4: today's clock would read 23h 59m left.
        val (title, sentence) = offlineNote(holdout, day = 2, nowSeconds = day3Deadline + 61, network = "Devnet")
        assertEquals("Done — too late to send", title)
        assertEquals(
            "Devnet can't be reached, and day 3's deadline has passed, so it can no longer be sent. Your 25 are saved on this phone.",
            sentence,
        )
        assertFalse("left" in sentence)
    }

    @Test
    fun `the deadline's own last second still counts down`() {
        val (title, sentence) = offlineNote(holdout, day = 2, nowSeconds = day3Deadline - 30, network = "Devnet")
        assertEquals("Done — not sent yet", title)
        assertEquals(true, "(30s left)" in sentence, sentence)
    }

    @Test
    fun `without a chain time the note still says what to do`() {
        val (title, sentence) = offlineNote(holdout, day = 0, nowSeconds = null, network = "Devnet")
        assertEquals("Done — not sent yet", title)
        assertEquals(
            "Devnet can't be reached, so day 1 isn't on chain yet. Your 25 are saved on this phone. " +
                "Open Holdout when it can be, before the deadline, and approve day 1 in your wallet.",
            sentence,
        )
    }

    @Test
    fun `past that day's deadline the note says too late to send even when the chain answers, with no offer to submit`() {
        val (title, sentence) = sittingNote(holdout, day = 2, nowSeconds = day3Deadline + 61, offline = false, network = "Devnet")!!
        assertEquals("Done — too late to send", title)
        assertEquals("Day 3's deadline has passed, so it can no longer be sent. Your 25 are saved on this phone.", sentence)
    }

    @Test
    fun `before the deadline with the chain answering there is no note, so the day is submitted`() {
        assertNull(sittingNote(holdout, day = 2, nowSeconds = day3Deadline - 30, offline = false, network = "Devnet"))
        assertNull(sittingNote(holdout, day = 2, nowSeconds = day3Deadline, offline = false, network = "Devnet"), "the deadline second can still be sent")
        assertNull(sittingNote(holdout, day = 2, nowSeconds = null, offline = false, network = "Devnet"))
    }

    @Test
    fun `an earlier day's deadline counts, not the current day's`() {
        // Day 2 (index 1) is over once day 3 is running, whatever today's clock says.
        val day2Deadline = start + 2 * 86_400 - 1
        val (title, _) = sittingNote(holdout, day = 1, nowSeconds = day2Deadline + 5, offline = false, network = "Devnet")!!
        assertEquals("Done — too late to send", title)
    }
}
