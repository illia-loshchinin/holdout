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
    fun `every reminder carries its cohort's form, so a Hackathon round can be named by it`() {
        for (form in listOf(LeagueParams.FORM_KNEES_ALLOWED, LeagueParams.FORM_TOES)) {
            val c = cohort(dayLength = 600, days = 3, reps = 5).copy(leagueId = LeagueParams.TEST_LEAGUE_MIN, form = form)
            val running = schedule(c, member(), now = start - 60)
            assertTrue(running.isNotEmpty() && running.all { it.form == form }, "form $form")
            val done = finished(c, member(days = 3, state = MembershipSnapshot.State.Finished), start)
            assertTrue(done.isNotEmpty() && done.all { it.form == form }, "finished notice, form $form")
        }
        // A reminder that does not say has no form.
        assertEquals(Reminders.FORM_UNKNOWN, Reminder(Reminder.Kind.Start, address, 100, 0, start, start + 600, 5, 3, 600).form)
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
    fun `a 24-hour day is reminded at its midpoint with sound, and before its deadline silently`() {
        val c = cohort()
        val r = schedule(c, member(), now = start - 86_400)
        val middays = r.filter { it.kind == Reminder.Kind.Midday }
        assertEquals((0 until 7).toList(), middays.map { it.dayIndex })
        // 05:00 UTC deadlines: the midpoint is 17:00 UTC — an evening in Europe, a working day in the Americas.
        middays.forEach { assertEquals(Derive.deadlineOf(c, it.dayIndex) - 12 * 3_600, it.at) }
        assertTrue(middays.none { it.silent }, "the midday reminder sounds")
        assertTrue(r.filter { it.kind == Reminder.Kind.Deadline }.all { it.silent }, "the one 3 hours before the deadline may fall at night: silent")
        assertEquals(r.size, r.map { it.id }.toSet().size, "a midday and a deadline reminder of one day are two reminders")
    }

    @Test
    fun `short days keep one reminder, with sound`() {
        for (dayLength in listOf(600L, 3_600L)) {
            val r = schedule(cohort(dayLength = dayLength, reps = 5), member(), now = start - 60)
            assertTrue(r.none { it.kind == Reminder.Kind.Midday }, "day length $dayLength")
            assertTrue(r.none { it.silent }, "day length $dayLength")
        }
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
        assertEquals(listOf(2, 3, 4, 5, 6), r.filter { it.kind == Reminder.Kind.Deadline }.map { it.dayIndex }, "day 2 still owed today, whether or not the phone has it done")
        assertEquals(listOf(2, 3, 4, 5, 6), r.filter { it.kind == Reminder.Kind.Midday }.map { it.dayIndex }, "no halfway reminder for a day already on chain either")
        assertTrue(r.all { it.daysOnChain == 2 }, "the alarm carries what the chain had: day 2's needs no record on this phone of day 1")
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
    fun `nothing once forfeited, settled, ended or in breach - and once finished, only the finished notice`() {
        val c = cohort()
        assertTrue(schedule(c, member(state = MembershipSnapshot.State.Forfeited), start).isEmpty())
        assertEquals(listOf(Reminder.Kind.Finished), schedule(c, member(days = 7, state = MembershipSnapshot.State.Finished), start).map { it.kind })
        assertTrue(schedule(cohort(settled = true), member(), start).isEmpty())
        assertEquals(listOf(Reminder.Kind.Finished), schedule(c, member(days = 7), start + 7 * 86_400).map { it.kind })
        assertTrue(schedule(c, member(days = 7), start + 7 * 86_400 + Derive.SETTLE_USUAL_SECONDS).isEmpty())
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
    fun `the 12-hour boundary - a day of exactly 12 hours has two reminders, one second less has one`() {
        assertEquals(12 * 3_600L, Reminders.MIDDAY_MIN_DAY_SECONDS)
        val twelve = schedule(cohort(dayLength = 43_200, reps = 5), member(), now = start - 60)
        assertEquals((0 until 7).toList(), twelve.filter { it.kind == Reminder.Kind.Midday }.map { it.dayIndex })
        assertTrue(twelve.filter { it.kind == Reminder.Kind.Deadline }.all { it.silent }, "a 12-hour day's deadline reminder is silent")
        val under = schedule(cohort(dayLength = 43_199, reps = 5), member(), now = start - 60)
        assertTrue(under.none { it.kind == Reminder.Kind.Midday }, "11 h 59 min 59 s is a short day")
        assertTrue(under.none { it.silent }, "and keeps its one reminder, with sound")
    }

    @Test
    fun `a day already past its midpoint keeps the halfway reminder for its window, then only the deadline reminder`() {
        val c = cohort()
        val midpoint2 = Derive.deadlineOf(c, 2) - 12 * 3_600
        val inWindow = schedule(c, member(days = 2), now = midpoint2 + 1_800)
        assertTrue(inWindow.any { it.kind == Reminder.Kind.Midday && it.dayIndex == 2 }, "30 minutes late: Android may still deliver it")
        val past = schedule(c, member(days = 2), now = Derive.deadlineOf(c, 2) - 10 * 3_600)
        assertTrue(past.none { it.kind == Reminder.Kind.Midday && it.dayIndex == 2 }, "its window has closed")
        assertTrue(past.any { it.kind == Reminder.Kind.Deadline && it.dayIndex == 2 }, "the deadline reminder is still to come")
        assertTrue(past.any { it.kind == Reminder.Kind.Midday && it.dayIndex == 3 }, "and tomorrow's halfway one")
    }

    @Test
    fun `ids are distinct for every reminder of a cohort of any length`() {
        // 40 days: with the old formula Midday of day d and Deadline of day d + 31 shared an id.
        val c = cohort(days = 40)
        val now = start + 5 * 86_400 + 3_600
        val plain = schedule(c, member(days = 5), now = now)
        val withNotSent = notSent(c, member(days = 5), now)
        for (r in listOf(plain, withNotSent)) {
            assertEquals(r.size, r.map { it.id }.toSet().size, "no two of ${r.size} reminders share an alarm")
        }
        assertTrue(plain.any { it.kind == Reminder.Kind.Midday && it.dayIndex == 6 } && plain.any { it.kind == Reminder.Kind.Deadline && it.dayIndex == 37 })
    }

    @Test
    fun `a day's halfway, deadline and not-sent notices share one notification, and the start has its own`() {
        val cohort = address.base58()
        val day = Reminders.notificationId(cohort, Reminder.Kind.Midday, 2)
        assertEquals(day, Reminders.notificationId(cohort, Reminder.Kind.Deadline, 2), "a later notice replaces the halfway one rather than stacking beside it")
        assertEquals(day, Reminders.notificationId(cohort, Reminder.Kind.NotSent, 2))
        assertTrue(Reminders.notificationId(cohort, Reminder.Kind.Midday, 3) != day, "another day is another notification")
        assertTrue(Reminders.notificationId(cohort, Reminder.Kind.Start, 0) != Reminders.notificationId(cohort, Reminder.Kind.Midday, 0), "the start is its own")
    }

    @Test
    fun `one rule for silence - the deadline reminder of a long day and every not-sent notice`() {
        assertTrue(Reminders.isSilent(Reminder.Kind.Deadline, 86_400))
        assertTrue(!Reminders.isSilent(Reminder.Kind.Deadline, 3_600))
        assertTrue(!Reminders.isSilent(Reminder.Kind.Midday, 86_400))
        assertTrue(!Reminders.isSilent(Reminder.Kind.Start, 86_400))
        assertTrue(Reminders.isSilent(Reminder.Kind.NotSent, 3_600))
        assertTrue(Reminders.isSilent(Reminder.Kind.NotSent, 86_400))
    }

    private fun notSent(c: CohortSnapshot, m: MembershipSnapshot, now: Long, day: Int = m.daysCompleted) = Reminders.schedule(listOf(c to m), { address }, now) { _, d -> d == day }

    @Test
    fun `a day done here and not on chain - a standing notice at once, then a silent one every hour to the deadline`() {
        val c = cohort()
        val deadline = Derive.deadlineOf(c, 0)
        val now = start + 10 * 3_600 + 1_800 // done 10 and a half hours into the day
        val r = notSent(c, member(), now)
        val standing = r.filter { it.standing }
        assertEquals(1, standing.size)
        assertEquals(now, standing.single().at, "posted at once")
        val ticks = r.filter { it.kind == Reminder.Kind.NotSent && !it.standing }
        // 13 and a half hours to the deadline: a re-post 1 hour before it, 2 hours before it ... 13.
        assertEquals((1..13).toList(), ticks.map { it.tick }.sorted())
        ticks.forEach {
            assertEquals(deadline - it.tick * 3_600L, it.at)
            assertTrue(it.at > now && it.at < deadline)
            assertTrue(it.silent, "every one silent")
        }
        // The halfway and the long day's deadline reminders give way to them.
        assertTrue(r.none { it.dayIndex == 0 && (it.kind == Reminder.Kind.Midday || it.kind == Reminder.Kind.Deadline) })
        assertTrue(r.any { it.kind == Reminder.Kind.Midday && it.dayIndex == 1 } && r.any { it.kind == Reminder.Kind.Deadline && it.dayIndex == 1 }, "other days are reminded as usual")
        assertEquals(r.size, r.map { it.id }.toSet().size)
        assertTrue(ticks.size <= Reminders.NOT_SENT_MAX_TICKS, "at most 24 hourly re-posts, so 48 alarms, a day")
    }

    @Test
    fun `a day done right at its start gets every hour, at most 24`() {
        val c = cohort()
        val ticks = notSent(c, member(), now = start).filter { it.kind == Reminder.Kind.NotSent && !it.standing }
        assertEquals((1..23).toList(), ticks.map { it.tick }.sorted(), "a 24-hour day has 23 hours between its opening and the last one")
        val long = cohort(dayLength = 40 * 3_600L)
        val longTicks = notSent(long, member(), now = start).filter { it.kind == Reminder.Kind.NotSent && !it.standing }
        assertEquals(Reminders.NOT_SENT_MAX_TICKS, longTicks.size)
    }

    @Test
    fun `the not-sent hours stop with the day - sent, past the deadline, or not done here`() {
        val c = cohort()
        val now = start + 10 * 3_600
        // On chain: the next day is not done here, so nothing is left to keep in view.
        assertTrue(notSent(c, member(days = 1), now = start + 86_400 + 60, day = 0).none { it.kind == Reminder.Kind.NotSent })
        // Not done here, or done here for another day.
        assertTrue(schedule(c, member(), now).none { it.kind == Reminder.Kind.NotSent })
        assertTrue(notSent(c, member(), now, day = 3).none { it.kind == Reminder.Kind.NotSent })
        // The deadline passed: in breach, nothing at all.
        assertTrue(notSent(c, member(), Derive.deadlineOf(c, 0) + 1).isEmpty())
        // The last hour: the standing notice only, no re-post left.
        val last = notSent(c, member(), Derive.deadlineOf(c, 0) - 1_800).filter { it.kind == Reminder.Kind.NotSent }
        assertEquals(listOf(0), last.map { it.tick })
    }

    @Test
    fun `a short day keeps its deadline reminder when done here, and has the standing notice`() {
        val c = cohort(dayLength = 3_600, reps = 5)
        val r = notSent(c, member(), now = start + 60)
        assertEquals(listOf(0), r.filter { it.kind == Reminder.Kind.NotSent }.map { it.tick }, "a one-hour day has no hour to re-post in")
        assertTrue(r.any { it.kind == Reminder.Kind.Deadline && it.dayIndex == 0 }, "its reminder to submit still comes")
        assertTrue(r.none { it.kind == Reminder.Kind.Deadline && it.silent }, "with sound")
    }

    @Test
    fun `a not-sent hour is delivered within ten minutes of its time`() {
        val c = cohort()
        val tick3 = Derive.deadlineOf(c, 0) - 3 * 3_600
        assertTrue(notSent(c, member(), tick3 + 300).any { it.tick == 3 }, "5 minutes late: still deliverable")
        assertTrue(notSent(c, member(), tick3 + 600).none { it.tick == 3 }, "its window has closed")
        assertEquals(10 * 60_000L, Reminders.windowMillis(notSent(c, member(), start + 3_600).first { it.tick == 1 }))
        assertEquals(Reminders.windowMillis(86_400), Reminders.windowMillis(schedule(c, member(), start - 60).first { it.kind == Reminder.Kind.Midday }))
    }

    @Test
    fun `a chain instant becomes a moment on the monotonic clock`() {
        assertEquals(5_000_000L + 900_000, Reminders.elapsedMillisAt(atChainSeconds = start + 900, chainNowSeconds = start, elapsedNowMillis = 5_000_000))
    }

    // ---- the finished notice (FR-068b, 2026-10-11) -------------------------------

    private fun finished(c: CohortSnapshot, m: MembershipSnapshot, now: Long, comeback: Boolean = false) = Reminders.schedule(listOf(c to m), { address }, now, comebackFor = { _, _ -> comeback }).filter { it.kind == Reminder.Kind.Finished }

    @Test
    fun `a member with every day on chain gets one finished notice 15 minutes after the end`() {
        val c = cohort()
        val end = Derive.end(c)
        for (state in listOf(MembershipSnapshot.State.Joined, MembershipSnapshot.State.Finished)) {
            for (now in listOf(end - 3_600, end, end + 899)) {
                val r = finished(c, member(days = 7, state = state), now)
                assertEquals(1, r.size, "$state at ${now - end}")
                assertEquals(end + Derive.SETTLE_USUAL_SECONDS, r.single().at)
                assertEquals(r.single().at + 6 * 3_600, r.single().deadline)
                assertEquals(7, r.single().dayIndex)
                assertEquals(false, r.single().comeback)
            }
        }
        assertEquals(true, finished(c, member(days = 7), end, comeback = true).single().comeback)
    }

    @Test
    fun `no finished notice once its time has come, the cohort settled, the payout claimed, a forfeit or a day missing`() {
        val c = cohort()
        val end = Derive.end(c)
        assertTrue(finished(c, member(days = 7), end + 900).isEmpty(), "a read after its time finds it still unsettled: dropped")
        assertTrue(finished(cohort(settled = true), member(days = 7, state = MembershipSnapshot.State.Finished), end).isEmpty())
        assertTrue(finished(cohort(settled = true), member(days = 7, state = MembershipSnapshot.State.Claimed), end).isEmpty())
        assertTrue(finished(c, member(days = 7, state = MembershipSnapshot.State.Claimed), end).isEmpty())
        assertTrue(finished(c, member(days = 5, state = MembershipSnapshot.State.Forfeited), end).isEmpty())
        assertTrue(finished(c, member(days = 6), end).isEmpty())
        assertTrue(finished(c, member(days = 6), end - 3_600).isEmpty(), "still running, a day to go")
    }

    @Test
    fun `the finished notice is silent for long days and sounds for the test lobby, with ids of its own`() {
        assertTrue(Reminders.isSilent(Reminder.Kind.Finished, 86_400))
        assertTrue(Reminders.isSilent(Reminder.Kind.Finished, 43_200))
        assertEquals(false, Reminders.isSilent(Reminder.Kind.Finished, 600))
        val c = cohort(days = 40)
        val all = Reminders.schedule(listOf(c to member()), { address }, start - 60, doneOnPhone = { _, _ -> false }) +
            finished(c, member(days = 40), Derive.end(c)) +
            Reminders.schedule(listOf(c to member(days = 3)), { address }, Derive.deadlineOf(c, 3) - 10 * 3_600, doneOnPhone = { _, d -> d == 3 })
        val distinct = all.distinctBy { Triple(it.kind, it.dayIndex, it.tick) }
        assertEquals(distinct.size, distinct.map { it.id }.toSet().size, "every alarm id distinct")
        val notSent = all.filter { it.kind == Reminder.Kind.NotSent }
        assertTrue(notSent.isNotEmpty())
        val ids = (0 until 40).flatMap { d -> listOf(Reminder.Kind.Start, Reminder.Kind.Deadline).map { Reminders.notificationId(address.base58(), it, d) } }.toSet()
        assertTrue(Reminders.notificationId(address.base58(), Reminder.Kind.Finished, 40) !in ids, "its own notification")
    }

    @Test
    fun `the comeback line - owed on the membership, or open in the progress and not used up by an earlier finish of the league`() {
        val c = cohort()
        val open = ProfileSnapshot.LeagueProgress(0, 1, 0, 0, 0, comebackStake = 100uL, comebackLeague = 0, comebackRound = 0, comebackLastRound = 1)
        val none = ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0)
        fun profile(p: ProfileSnapshot.LeagueProgress) = ProfileSnapshot(wallet, listOf(p, none, none), null, null)
        val m = member(days = 7)
        assertTrue(Reminders.comebackExpected(c, m, profile(open), listOf(c to m)))
        assertEquals(false, Reminders.comebackExpected(c, m, profile(none), listOf(c to m)))
        assertEquals(false, Reminders.comebackExpected(c, m, null, listOf(c to m)))
        assertTrue(Reminders.comebackExpected(c, m.copy(comebackEntitled = 5uL), profile(none), listOf(c to m)), "already counted with one")
        // Two finished, unsettled Holdouts of one league: only the earlier uses the record.
        val later = cohort().copy(index = 1, start = start + 7 * 86_400)
        val both = listOf(c to m, later to m)
        assertTrue(Reminders.comebackExpected(c, m, profile(open), both))
        assertEquals(false, Reminders.comebackExpected(later, m, profile(open), both))
        // Another league's cohort, or one past the comeback's last round, earns none.
        assertEquals(false, Reminders.comebackExpected(c.copy(leagueId = 1), m, profile(open), listOf(c to m)))
        assertEquals(false, Reminders.comebackExpected(c.copy(index = 2), m, profile(open), listOf(c to m)))
    }

    @Test
    fun `a finished notice is withdrawn once the app sees the cohort settled or the payout claimed`() {
        val id = Reminders.notificationId(address.base58(), Reminder.Kind.Finished, 7)
        assertEquals(setOf(id), Reminders.finishedToWithdraw(listOf(cohort(settled = true) to member(days = 7, state = MembershipSnapshot.State.Finished)), { address }))
        assertEquals(setOf(id), Reminders.finishedToWithdraw(listOf(cohort() to member(days = 7, state = MembershipSnapshot.State.Claimed)), { address }))
        assertTrue(Reminders.finishedToWithdraw(listOf(cohort() to member(days = 7)), { address }).isEmpty(), "still settling: the notice stays")
    }
}
