package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.core.CohortSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 001 FR-024b, 2026-10-10: a day completed offline says it is not sent yet, and how it gets sent. */
class OfflineNoteTest {
    private val start = 1_791_176_400L
    private val holdout = Holdout(
        address = SolanaPublicKey(ByteArray(32) { 7 }),
        cohort = CohortSnapshot(0, 0, start, start - 3_600, 7, 25, 86_400, 0, listOf(1uL, 2uL, 3uL, 4uL)),
        league = null,
        membership = null,
    )

    @Test
    fun `the note names the day, the reps saved on the phone, the time left and the wallet`() {
        val (title, sentence) = offlineNote(holdout, day = 2, nowSeconds = start + 2 * 86_400 + 3_600)
        assertEquals("Done — not sent yet", title)
        assertTrue("day 3 isn't on chain yet" in sentence)
        assertTrue("Your 25 are saved on this phone" in sentence)
        assertTrue("left)" in sentence, "the time left before the deadline")
        assertTrue("approve day 3 in your wallet" in sentence)
    }

    @Test
    fun `without a chain time the note still says what to do`() {
        val (_, sentence) = offlineNote(holdout, day = 0, nowSeconds = null)
        assertTrue("before the deadline and approve day 1" in sentence)
    }
}
