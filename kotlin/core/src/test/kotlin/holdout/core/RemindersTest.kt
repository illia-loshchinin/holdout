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

    private fun schedule(c: CohortSnapshot, m: MembershipSnapshot, now: Long) = Reminders.schedule(listOf(c to m), { address }, now)

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
    fun `a 10-minute test day is reminded as it opens, and its first day by the start reminder alone`() {
        assertEquals(599L, Reminders.leadSeconds(600))
        val c = cohort(dayLength = 600, reps = 5)
        val r = schedule(c, member(), now = start - 60)
        assertEquals(Reminder.Kind.Start, r.first().kind)
        assertTrue(r.none { it.kind == Reminder.Kind.Deadline && it.dayIndex == 0 }, "day 1 opens with the start: one notification, not two")
        val deadlines = r.filter { it.kind == Reminder.Kind.Deadline }
        assertEquals((1 until 7).toList(), deadlines.map { it.dayIndex })
        deadlines.forEach { assertEquals(start + it.dayIndex * 600L, it.at, "day ${it.dayIndex} reminded as it opens") }
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
        val day0 = schedule(c, member(), now = start + 60).first { it.kind == Reminder.Kind.Deadline }
        assertEquals(Reminder.Kind.Deadline, day0.kind)
        assertEquals(0, day0.dayIndex)
        assertEquals(start + 3_600 - 1 - 15 * 60, day0.at)
        assertEquals(5, day0.repsPerDay)
    }

    @Test
    fun `nothing for a day on chain, or already past its reminder - but a day only on the phone is still owed`() {
        val c = cohort()
        val midDay2 = start + 2 * 86_400 + 60
        val r = schedule(c, member(days = 2), now = midDay2)
        assertEquals(listOf(2, 3, 4, 5, 6), r.map { it.dayIndex }, "day 2 still owed today, whether or not the phone has it done")
        val late = start + 3 * 86_400 - 1 - 60 // past day 2's reminder window, before its deadline
        assertTrue(schedule(c, member(days = 2), now = late).none { it.dayIndex == 2 })
    }

    @Test
    fun `a reminder just past its time stays scheduled until its window closes, so a refresh does not cancel it undelivered`() {
        val c = cohort(dayLength = 3_600, reps = 5)
        val day0At = start + 3_600 - 1 - 15 * 60
        val window = Reminders.windowMillis(3_600) / 1_000
        assertTrue(schedule(c, member(), now = day0At + 30).any { it.kind == Reminder.Kind.Deadline && it.dayIndex == 0 }, "30 s after its time: Android may still deliver it")
        assertTrue(schedule(c, member(), now = day0At + window).none { it.kind == Reminder.Kind.Deadline && it.dayIndex == 0 }, "its window has closed")
        assertTrue(schedule(c, member(), now = start + 30).any { it.kind == Reminder.Kind.Start }, "the start reminder too")
        assertTrue(schedule(c, member(days = 1), now = start + 30).none { it.kind == Reminder.Kind.Start }, "but not once day 1 is on chain: it would say day 1 is still owed")
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
