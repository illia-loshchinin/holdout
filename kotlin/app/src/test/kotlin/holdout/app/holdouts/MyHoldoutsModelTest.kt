package holdout.app.holdouts

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.app.ui.ChipTone
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.ProfileSnapshot
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** H0 My Holdouts (screen map H0, board 10): what each section lists and where each card goes. */
class MyHoldoutsModelTest {
    private val start = 1_791_176_400L
    private val day = 86_400L
    private val me = SolanaPublicKey(ByteArray(32) { 0x11 })

    @Test
    fun `yours - one card per Holdout by stage, each going where the screen map says`() {
        val lobby = holdout(1, start + 3 * day, member(0))
        val running = holdout(2, start - 3 * day, member(3), form = LeagueParams.FORM_TOES, days = 21)
        val now = start
        val s = MyHoldoutsModel.build(view(lobby, running), now, bankedToday = { _, _ -> 10 })

        val (r, l) = s.yours.sortedBy { it.status }.let { it[1] to it[0] }
        assertEquals("lobby", l.status)
        assertEquals("Spark · 7-day", l.name)
        assertEquals("starts in 3d 0h", l.detail)
        assertEquals(NavEvent.Open(Screen.Lobby(lobby.address.base58())), l.go)

        assertEquals("running", r.status)
        assertEquals("Habit · 21-day", r.name)
        assertEquals("Day 4 of 21 · 12 started", r.line)
        assertEquals("15 push-ups to go today", r.detail)
        assertEquals(NavEvent.Open(Screen.Detail(running.address.base58())), r.go)
    }

    @Test
    fun `yours - forfeit, settling and a claim waiting`() {
        val out = holdout(1, start - 5 * day, member(1))
        val settling = holdout(2, start - 8 * day, member(7, MembershipSnapshot.State.Finished))
        val claim = holdout(3, start - 9 * day, member(7, MembershipSnapshot.State.Finished), settled = true)
        val s = MyHoldoutsModel.build(view(out, settling, claim), start)

        val byId = s.yours.associateBy { it.cohort }
        assertEquals("Out after 1 day", byId.getValue(out.id).line)
        assertEquals(NavEvent.Open(Screen.Forfeit(out.id)), byId.getValue(out.id).go)
        assertEquals("settling", byId.getValue(settling.id).status)
        assertEquals(NavEvent.Open(Screen.Detail(settling.id)), byId.getValue(settling.id).go)
        assertEquals("claim", byId.getValue(claim.id).status)
        assertEquals("Held out 7 of 7", byId.getValue(claim.id).line)
        assertEquals("15 SKR waiting for you", byId.getValue(claim.id).detail)
        assertEquals(NavEvent.Open(Screen.Claim(claim.id)), byId.getValue(claim.id).go)
    }

    @Test
    fun `yours alone is the same list H0 shows, each card carrying its typed kind and the chip word of it`() {
        val out = holdout(1, start - 5 * day, member(1))
        val settling = holdout(2, start - 8 * day, member(7, MembershipSnapshot.State.Finished))
        val claim = holdout(3, start - 9 * day, member(7, MembershipSnapshot.State.Finished), settled = true)
        val lobby = holdout(4, start + 3 * day, member(0))
        val v = view(out, settling, claim, lobby)

        val yours = MyHoldoutsModel.yours(v, start)
        assertEquals(MyHoldoutsModel.build(v, start).yours, yours)
        val byId = yours.associateBy { it.cohort }
        assertEquals(YourKind.Out, byId.getValue(out.id).kind)
        assertEquals(YourKind.Settling, byId.getValue(settling.id).kind)
        assertEquals(YourKind.Claim, byId.getValue(claim.id).kind)
        assertEquals(YourKind.Lobby, byId.getValue(lobby.id).kind)
        yours.forEach { assertEquals(it.kind.word, it.status) }
    }

    @Test
    fun `finished - claimed Holdouts, and a forfeit once its Holdout has ended with no way back`() {
        val claimed = holdout(1, start - 20 * day, member(7, MembershipSnapshot.State.Claimed), settled = true)
        val oldOut = holdout(2, start - 30 * day, member(2, MembershipSnapshot.State.Forfeited))
        val s = MyHoldoutsModel.build(view(claimed, oldOut), start)
        assertTrue(s.yours.isEmpty())
        assertEquals(listOf("Spark · 7-day · finished · claimed 15 SKR", "Spark · 7-day · finished · out after 2 days"), s.finished.map { it.line })
        assertEquals(NavEvent.Open(Screen.Detail(claimed.id)), s.finished[0].go)
        assertEquals(NavEvent.Open(Screen.Forfeit(oldOut.id)), s.finished[1].go)
    }

    @Test
    fun `open to join - the offers with what is at stake, the boost and the countdown, never one already joined`() {
        val open = holdout(1, start + 2 * day, null, joined = 9, held = 9_000_000_000uL)
        val claimed = holdout(2, start - 20 * day, member(7, MembershipSnapshot.State.Claimed), settled = true)
        val s = MyHoldoutsModel.build(view(open, claimed), start)
        assertEquals(listOf(open.id), s.open.map { it.cohort })
        assertEquals("9,000 SKR", s.open[0].amount)
        assertEquals("at stake · 9 joined", s.open[0].note)
        assertEquals("Joining closes in 1d 23h", s.open[0].closesIn)
        assertFalse(s.holdsLobbyPlace)
    }

    @Test
    fun `open to join - only what the program would take, so a Join is always live`() {
        val running = holdout(1, start - 2 * day, member(2))
        val overlapping = holdout(2, start + 3 * day, null)
        val backToBack = holdout(3, start + 5 * day, null)
        val p = profile(running = ProfileSnapshot.Running(running.address, start + 5 * day))
        val s = MyHoldoutsModel.build(view(running, overlapping, backToBack, profile = p), start)
        assertEquals(listOf(backToBack.id), s.open.map { it.cohort })
    }

    @Test
    fun `open to join - none while the member holds a lobby place, the section is not drawn`() {
        val lobby = holdout(1, start + 3 * day, member(0))
        val other = holdout(2, start + 2 * day, null)
        val s = MyHoldoutsModel.build(view(lobby, other), start)
        assertTrue(s.holdsLobbyPlace)
        assertTrue(s.open.isEmpty())
        assertEquals(listOf(lobby.id), s.yours.map { it.cohort })
    }

    @Test
    fun `an Out row offers the win-back until the member holds a place in the comeback round, then says Comeback joined`() {
        val out = withLeague(holdout(1, start - 5 * day, member(1)))
        val before = MyHoldoutsModel.build(view(out, withLeague(holdout(2, start + 2 * day, null))), start).yours.single()
        assertTrue(before.detail.orEmpty().startsWith("Win back up to "), before.detail)

        val round = withLeague(holdout(2, start + 2 * day, member(0)))
        val after = MyHoldoutsModel.build(view(out, round), start).yours.single { it.kind == YourKind.Out }
        assertEquals("Comeback joined", after.detail)
        assertEquals(ChipTone.Success, after.detailTone)
    }

    @Test
    fun `running now - running Holdouts the wallet is not in, read-only, each opening H4`() {
        val watched = holdout(1, start - 2 * day, null)
        val mine = holdout(2, start - 2 * day, member(2))
        val s = MyHoldoutsModel.build(view(watched, mine), start)
        assertEquals(listOf(watched.id), s.running.map { it.cohort })
        assertEquals("Spark · 7-day · day 3 of 7", s.running[0].name)
        assertEquals("12 started · prize pool 0 SKR", s.running[0].line)
        assertEquals(NavEvent.Open(Screen.Detail(watched.id)), s.running[0].go)
    }

    @Test
    fun `empty - no Holdouts yet, the offers still listed`() {
        val s = MyHoldoutsModel.build(view(holdout(1, start + 2 * day, null)), start)
        assertTrue(s.empty)
        assertEquals(1, s.open.size)
    }

    private val Holdout.id get() = address.base58()

    /** The comeback offer is worked out against the league record. */
    private fun withLeague(h: Holdout) = h.copy(league = LeagueSnapshot(LeagueParams.judged(h.cohort.leagueId, start), nextIndex = 3))

    private fun member(daysCompleted: Int, state: MembershipSnapshot.State = MembershipSnapshot.State.Joined) = MembershipSnapshot(me, 0, 10_000_000uL, start - 10 * day, daysCompleted, state)

    private fun holdout(
        n: Int,
        cohortStart: Long,
        membership: MembershipSnapshot?,
        form: Int = LeagueParams.FORM_KNEES_ALLOWED,
        days: Int = 7,
        settled: Boolean = false,
        joined: Long = 12,
        held: ULong = 0uL,
    ) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { n.toByte() }),
        cohort = CohortSnapshot(
            leagueId = if (days == 7) LeagueParams.LEAGUE_7D else LeagueParams.LEAGUE_21D,
            index = n.toLong(),
            start = cohortStart,
            joinCutoff = cohortStart - 3_600,
            durationDays = days,
            repsPerDay = 25,
            dayLength = day,
            exercise = 0,
            chips = listOf(10_000_000uL, 25_000_000uL, 50_000_000uL, 100_000_000uL),
            memberCount = joined,
            heldStake = held,
            settled = settled,
            sharePerTier = listOf(5_000_000uL, 0uL, 0uL, 0uL),
            form = form,
        ),
        league = null,
        membership = membership,
    )

    private fun view(vararg holdouts: Holdout, profile: ProfileSnapshot? = null) = ChainView(config = null, holdouts = holdouts.toList(), profile = profile, skr = 0uL, lamports = 0L)

    private fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(me, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending)
}
