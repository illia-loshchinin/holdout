package holdout.core

import com.solana.publickey.SolanaPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T067a — the reminder schedule (FR-068): from chain state and chain time, for
 * 24-hour days and the short test cohort's one-hour days alike.
 */
class RemindersTest {
    private val wallet = SolanaPublicKey(ByteArray(32) { 1 })
    private val address = SolanaPublicKey(ByteArray(32) { 7 })
    private val start = 1_791_176_400L

    private fun cohort(dayLength: Long = 86_400, days: Int = 7, reps: Int = 25, settled: Boolean = false) = CohortSnapshot(
        leagueId = 0, index = 0, start = start, joinCutoff = start - 3_600, durationDays = days, repsPerDay = reps, dayLength = dayLength, exercise = 0,
        chips = listOf(10uL, 25uL, 50uL, 100uL), settled = settled,
    )

    private fun member(days: Int = 0, state: MembershipSnapshot.State = MembershipSnapshot.State.Joined) = MembershipSnapshot(wallet, 0, 10uL, start - 86_400, days, state)

    private fun schedule(c: CohortSnapshot, m: MembershipSnapshot, now: Long, done: (SolanaPublicKey, Int) -> Boolean = { _, _ -> false }) = Reminders.schedule(listOf(c to m), { address }, now, done)

    @Test
    fun `3 hours before a 24-hour day's deadline, 15 minutes before a one-hour day's`() {
        assertEquals(3 * 3_600L, Reminders.leadSeconds(86_400))
        assertEquals(15 * 60L, Reminders.leadSeconds(3_600))
    }

    @Test
    fun `a reminder may come late, but always before its deadline`() {
        for (dayLength in listOf(3_600L, 86_400L)) {
            val window = Reminders.windowMillis(dayLength) / 1_000
            assertTrue(window >= 10 * 60, "Android's shortest window without the exact-alarm permission")
            assertTrue(window < Reminders.leadSeconds(dayLength), "day length $dayLength: the latest delivery is still before the deadline")
        }
        assertEquals(10 * 60L, Reminders.windowMillis(3_600) / 1_000)
        assertEquals(3_600L, Reminders.windowMillis(86_400) / 1_000)
    }

    @Test
    fun `in the lobby - a start reminder, then one before every day's deadline`() {
        val c = cohort()
        val r = schedule(c, member(), now = start - 86_400)
        assertEquals(Reminder.Kind.Start, r.first().kind)
        assertEquals(start, r.first().at)
        val deadlines = r.filter { it.kind == Reminder.Kind.Deadline }
        assertEquals((0 until 7).toList(), deadlines.map { it.dayIndex })
        deadlines.forEach { assertEquals(Derive.deadlineOf(c, it.dayIndex) - 3 * 3_600, it.at) }
    }

    @Test
    fun `a one-hour test day is reminded 15 minutes before its deadline`() {
        val c = cohort(dayLength = 3_600, reps = 5)
        val day0 = schedule(c, member(), now = start + 60).first()
        assertEquals(Reminder.Kind.Deadline, day0.kind)
        assertEquals(0, day0.dayIndex)
        assertEquals(start + 3_600 - 1 - 15 * 60, day0.at)
        assertEquals(5, day0.repsPerDay)
    }

    @Test
    fun `nothing for a day on chain, done on the phone, or already past its reminder`() {
        val c = cohort()
        val midDay2 = start + 2 * 86_400 + 60
        val r = schedule(c, member(days = 2), now = midDay2) { _, day -> day == 3 }
        assertEquals(listOf(2, 4, 5, 6), r.map { it.dayIndex }, "day 2 still owed today, day 3 done on the phone")
        val late = start + 3 * 86_400 - 1 - 60 // past day 2's reminder, before its deadline
        assertTrue(schedule(c, member(days = 2), now = late).none { it.dayIndex == 2 })
    }

    @Test
    fun `nothing once forfeited, finished, settled, ended or in breach`() {
        val c = cohort()
        assertTrue(schedule(c, member(state = MembershipSnapshot.State.Forfeited), start).isEmpty())
        assertTrue(schedule(c, member(days = 7, state = MembershipSnapshot.State.Finished), start).isEmpty())
        assertTrue(schedule(cohort(settled = true), member(), start).isEmpty())
        assertTrue(schedule(c, member(days = 7), start + 7 * 86_400).isEmpty())
        assertTrue(schedule(c, member(days = 0), start + 86_400).isEmpty(), "day 0's deadline passed: in breach")
    }

    @Test
    fun `ids are stable, so a recomputed schedule replaces rather than adds`() {
        val a = schedule(cohort(), member(), start - 60)
        val b = schedule(cohort(), member(), start - 30)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(a.size, a.map { it.id }.toSet().size)
    }

    @Test
    fun `a chain instant becomes a moment on the monotonic clock`() {
        assertEquals(5_000_000L + 900_000, Reminders.elapsedMillisAt(atChainSeconds = start + 900, chainNowSeconds = start, elapsedNowMillis = 5_000_000))
    }
}
