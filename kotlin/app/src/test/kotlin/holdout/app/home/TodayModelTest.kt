package holdout.app.home

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Screen
import holdout.core.CohortSnapshot
import holdout.core.ConfigSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.ProfileSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Today's chain-derived content (board 3): the offer cards, the block they sit in, the nudge rows, the comeback card and yesterday's note. */
class TodayModelTest {
    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val me = SolanaPublicKey(ByteArray(32) { 0x11 })
    private val key = SolanaPublicKey(ByteArray(32) { 5 })
    private val config = ConfigSnapshot(key, key, key, key, key, key, false, emptyList(), 0, 0, 0uL, 0uL, 0uL, 0uL, 0, 0uL)

    private fun holdout(
        n: Int,
        cohortStart: Long,
        member: MembershipSnapshot? = null,
        form: Int = LeagueParams.FORM_KNEES_ALLOWED,
        days: Int = 7,
        leagueId: Int = LeagueParams.LEAGUE_7D,
        joined: Long = 14,
        held: ULong = 12_500uL * skr,
        boost: ULong = 0uL,
        reps: Int = 25,
        dayLength: Long = day,
        cutoff: Long = cohortStart - 3_600,
    ) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { n.toByte() }),
        cohort = CohortSnapshot(leagueId, n.toLong(), cohortStart, cutoff, days, reps, dayLength, 0, listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr), memberCount = joined, heldStake = held, form = form),
        league = if (leagueId < LeagueParams.TEST_LEAGUE_MIN) LeagueSnapshot(LeagueParams.judged(leagueId, cohortStart), nextIndex = 3).copy(boostReserve = boost) else null,
        membership = member,
    )

    private fun member(days: Int, state: MembershipSnapshot.State = MembershipSnapshot.State.Joined) = MembershipSnapshot(me, 1, 1_250uL * skr, start - 10 * day, days, state)

    private fun view(vararg h: Holdout, cfg: ConfigSnapshot? = config, profile: ProfileSnapshot? = null) = ChainView(cfg, h.toList(), profile, 0uL, 0L)

    private fun profile(running: ProfileSnapshot.Running? = null, pending: ProfileSnapshot.Pending? = null) = ProfileSnapshot(me, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, running, pending)

    @Test
    fun `an offer card names the level and its tagline, what is at stake, how long joining stays open and the boost`() {
        val now = start - 2 * day - 3 * 3_600
        val card = OfferCardState.of(holdout(1, start, boost = 480uL * skr), now)
        assertEquals("Spark · 7-day", card.title)
        assertEquals("Light it up · knees allowed", card.tagline)
        assertEquals("12,500 SKR", card.atStake)
        assertEquals("at stake · 14 joined", card.joined)
        assertEquals("Joining closes in 2d 2h", card.timing)
        assertEquals("Prize boost: up to 480 SKR", card.boost)
        assertFalse(card.test)
    }

    @Test
    fun `a 21-day offer reads Habit with its own tagline, and no boost line while the reserve is empty`() {
        val card = OfferCardState.of(holdout(1, start, form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D, boost = 0uL), start - day)
        assertEquals("Habit · 21-day", card.title)
        assertEquals("Make it stick · toes only", card.tagline)
        assertNull(card.boost)
    }

    @Test
    fun `an offer never carries a calendar date, a clock time or a dollar figure`() {
        val card = OfferCardState.of(holdout(1, start, boost = 480uL * skr), start - day)
        val words = listOf(card.title, card.tagline, card.atStake, card.joined, card.timing, card.boost.orEmpty()).joinToString(" ")
        assertFalse(Regex("\\d{1,2}:\\d{2}|Oct|Sep|UTC|[≈$]").containsMatchIn(words), words)
    }

    @Test
    fun `the block is Reading before the chain answers, Unreachable when it can't be reached, NotLive without a program`() {
        assertEquals(HoldoutsBlock.Reading, TodayModel.holdouts(null, null, offline = false))
        assertEquals(HoldoutsBlock.Unreachable, TodayModel.holdouts(null, null, offline = true))
        assertEquals(HoldoutsBlock.NotLive, TodayModel.holdouts(view(cfg = null), start, offline = false))
    }

    @Test
    fun `the block lists the open Holdouts soonest first, none the member is in already`() {
        val later = holdout(1, start + 7 * day)
        val sooner = holdout(2, start + 2 * day)
        val finished = holdout(3, start - 9 * day, member = member(7, MembershipSnapshot.State.Claimed))
        val block = assertIs<HoldoutsBlock.Open>(TodayModel.holdouts(view(later, sooner, finished), start, offline = false))
        assertEquals(listOf(sooner.address.base58(), later.address.base58()), block.cards.map { it.cohort })
        assertFalse(block.stale)
    }

    @Test
    fun `no Holdout open to join is an empty block - the next one opens soon (ES-15)`() {
        val block = assertIs<HoldoutsBlock.Open>(TodayModel.holdouts(view(holdout(1, start - 3 * day)), start, offline = false))
        assertTrue(block.cards.isEmpty())
        assertEquals("The next Holdout opens soon.", TodayModel.OPENS_SOON)
    }

    @Test
    fun `while a lobby place is held the block is hidden - no cards, no Seeker line, no opens-soon card`() {
        val lobby = holdout(1, start + 2 * day, member = member(0))
        val other = holdout(2, start + 3 * day)
        assertEquals(HoldoutsBlock.Hidden, TodayModel.holdouts(view(lobby, other), start, offline = false))
        assertEquals(HoldoutsBlock.Hidden, TodayModel.holdouts(view(lobby), start, offline = false))
        // A locked lobby is still a place held.
        val locked = holdout(3, start + 1_800, member = member(0))
        assertEquals(HoldoutsBlock.Hidden, TodayModel.holdouts(view(locked), start, offline = false))
    }

    @Test
    fun `a place in a running Holdout still lists the Holdouts that start after it ends, and only those`() {
        val running = holdout(1, start - 2 * day, member = member(2))
        val overlapping = holdout(2, start + 3 * day)
        val backToBack = holdout(3, start + 5 * day)
        val v = view(running, overlapping, backToBack, profile = profile(running = ProfileSnapshot.Running(running.address, start + 5 * day)))
        val block = assertIs<HoldoutsBlock.Open>(TodayModel.holdouts(v, start, offline = false))
        assertEquals(listOf(backToBack.address.base58()), block.cards.map { it.cohort })
    }

    @Test
    fun `nothing joinable behind a running Holdout is the opens-soon card, not a Join that would be refused`() {
        val running = holdout(1, start - 2 * day, member = member(2))
        val overlapping = holdout(2, start + 3 * day)
        val v = view(running, overlapping, profile = profile(running = ProfileSnapshot.Running(running.address, start + 5 * day)))
        val block = assertIs<HoldoutsBlock.Open>(TodayModel.holdouts(v, start, offline = false))
        assertTrue(block.cards.isEmpty())
    }

    @Test
    fun `an earlier read shown while the chain can't be reached is marked stale`() {
        val block = assertIs<HoldoutsBlock.Open>(TodayModel.holdouts(view(holdout(1, start + 2 * day)), start, offline = true))
        assertTrue(block.stale)
        assertEquals(1, block.cards.size)
    }

    @Test
    fun `a Hackathon Holdout keeps its own name and counts down to its start`() {
        val test = holdout(1, start + 750, leagueId = LeagueParams.TEST_LEAGUE_MIN, days = 3, reps = 5, dayLength = 600, joined = 3, held = 250uL * skr, cutoff = start + 750)
        val card = OfferCardState.of(test, start)
        assertTrue(card.test)
        assertEquals("Hackathon Holdout", card.title)
        assertEquals("3 days · 5 push-ups a day · joinable right up to its start", card.tagline)
        assertEquals("Starts in 12m 30s", card.timing)
        assertEquals(600, card.testDayLength)
    }

    @Test
    fun `a short test Holdout with a join lock does not claim to be joinable up to its start`() {
        val test = holdout(1, start + 750, leagueId = LeagueParams.TEST_LEAGUE_MIN, days = 3, reps = 5, dayLength = 600, joined = 3, held = 250uL * skr, cutoff = start + 690)
        assertEquals("3 days · 5 push-ups a day", OfferCardState.of(test, start).tagline)
    }

    @Test
    fun `nudges - one line per Holdout needing a tap, never the running one, each going to its screen`() {
        val lobby = holdout(1, start + 2 * day, member = member(0))
        val running = holdout(2, start - 2 * day, member = member(2))
        val claim = holdout(3, start - 9 * day, member = member(7, MembershipSnapshot.State.Finished)).let { it.copy(cohort = it.cohort.copy(settled = true, sharePerTier = listOf(0uL, 162uL * skr, 0uL, 0uL))) }
        val nudges = TodayModel.nudges(view(lobby, running, claim), start)

        assertEquals(2, nudges.size)
        val claimRow = nudges.single { it.tone == NudgeTone.Success }
        assertTrue(claimRow.text.startsWith("You finished · claim "), claimRow.text)
        assertEquals(NavEvent.Open(Screen.Claim(claim.address.base58())), claimRow.go)
        val lobbyRow = nudges.single { it.tone == NudgeTone.Neutral }
        assertEquals("Spark · 7-day lobby · starts in 2d 0h", lobbyRow.text)
        assertEquals(NavEvent.Open(Screen.Lobby(lobby.address.base58())), lobbyRow.go)
    }

    @Test
    fun `a Holdout the member is out of keeps its line until they hold a place in its comeback round`() {
        val out = holdout(1, start - 5 * day, member(1))
        val outRow = TodayModel.nudges(view(out), start).single()
        assertEquals(NudgeTone.Warning, outRow.tone)

        // Joined the comeback round: only its lobby waits, no loss line and no comeback card (owner, 12 Oct walk).
        val comeback = holdout(2, start + 2 * day, member(0))
        val v = view(out, comeback)
        val rows = TodayModel.nudges(v, start)
        assertEquals(1, rows.size)
        assertEquals(NavEvent.Open(Screen.Lobby(comeback.address.base58())), rows.single().go)
        assertNull(TodayModel.comeback(v, start, seekerKnown = true))
    }

    @Test
    fun `no comeback card when its round to join would be refused - a lobby place held in another Holdout`() {
        val out = holdout(1, start - 5 * day, member(1))
        val round = holdout(2, start + 2 * day)
        assertNotNull(TodayModel.comeback(view(out, round), start, seekerKnown = true), "the round is joinable on its own")

        val lobby = holdout(3, start + 3 * day, member(0), form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D)
        val pending = ProfileSnapshot.Pending(lobby.address, start + 3 * day, start + 24 * day)
        assertNull(TodayModel.comeback(view(out, round, lobby, profile = profile(pending = pending)), start, seekerKnown = true))
    }

    @Test
    fun `nudges leave out the Holdout on show`() {
        val lobby = holdout(1, start + 2 * day, member = member(0))
        assertTrue(TodayModel.nudges(view(lobby), start, exceptCohort = lobby.address.base58()).isEmpty())
    }

    @Test
    fun `yesterday's note appears only when yesterday banked something and was not completed`() {
        assertEquals("Yesterday ended at 20 of 25. Today starts fresh.", TodayModel.yesterdayNote(20, completed = false, streakAlive = false, target = 25, todayBanked = 0, todayDone = false))
        assertNull(TodayModel.yesterdayNote(0, completed = false, streakAlive = false, target = 25, todayBanked = 0, todayDone = false))
        assertNull(TodayModel.yesterdayNote(25, completed = true, streakAlive = true, target = 25, todayBanked = 0, todayDone = false))
        assertNull(TodayModel.yesterdayNote(20, completed = false, streakAlive = false, target = 25, todayBanked = 10, todayDone = false))
        assertNull(TodayModel.yesterdayNote(20, completed = false, streakAlive = false, target = 25, todayBanked = 25, todayDone = true))
    }

    @Test
    fun `when a freeze covered the missed day, the note says the streak was kept`() {
        assertEquals("Yesterday ended at 20 of 25. A freeze kept your streak.", TodayModel.yesterdayNote(20, completed = false, streakAlive = true, target = 25, todayBanked = 0, todayDone = false))
    }

    @Test
    fun `no config means no comeback card and no nudges`() {
        assertNull(TodayModel.comeback(view(cfg = null), start, seekerKnown = false))
        assertTrue(TodayModel.nudges(view(cfg = null), start).isEmpty())
    }
}
