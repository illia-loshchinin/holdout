package holdout.app.nav

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.chain.Holdout
import holdout.app.holdouts.MyHoldoutsModel
import holdout.app.stake.SeekerStatus
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import holdout.core.Stage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole member journey through the navigation model, driven by a fake chain
 * timeline: the stages come from real cohort and membership snapshots read at
 * chain times, as the shell derives them (`ChainView.stages`), so a stage the
 * app would compute differently fails here.
 *
 * connect → tour → Today → the Daily 25 → Join a Spark → Seeker gate → stake
 * sheet → wallet → confirming → You're in → lobby → the Holdout starts → Today
 * in the Holdout → sitting → submit → on chain → a missed day → forfeit →
 * comeback → stake sheet; and the finishing branch → settling → claim →
 * claimed → Holdouts "Finished".
 */
class MemberJourneyEndToEndTest {
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val me = SolanaPublicKey(ByteArray(32) { 0x11 })
    private val spark = SolanaPublicKey(ByteArray(32) { 1 })
    private val nextSpark = SolanaPublicKey(ByteArray(32) { 2 })

    /** The chain as the app reads it: one Spark the member joins, and the next one the comeback offers. */
    private inner class FakeChain {
        var membership: MembershipSnapshot? = null
        var settled = false

        fun view(): ChainView = ChainView(
            config = null,
            holdouts = listOf(
                Holdout(spark, cohort(start, settled), null, membership),
                Holdout(nextSpark, cohort(start + 7 * day, false), null, null),
            ),
            profile = null,
            skr = 100_000_000uL,
            lamports = 50_000_000L,
        )

        fun join() {
            membership = MembershipSnapshot(me, 0, 10_000_000uL, start - day, 0, MembershipSnapshot.State.Joined)
        }

        fun submit() {
            membership = membership!!.copy(daysCompleted = membership!!.daysCompleted + 1)
        }

        fun finishAll() {
            membership = membership!!.copy(daysCompleted = 7, state = MembershipSnapshot.State.Finished)
        }

        fun claim() {
            membership = membership!!.copy(state = MembershipSnapshot.State.Claimed)
        }

        private fun cohort(at: Long, settled: Boolean) = CohortSnapshot(
            leagueId = LeagueParams.LEAGUE_7D, index = at, start = at, joinCutoff = at - 3_600, durationDays = 7, repsPerDay = 25,
            dayLength = day, exercise = 0, chips = listOf(10_000_000uL, 25_000_000uL, 50_000_000uL, 100_000_000uL), memberCount = 14,
            settled = settled, sharePerTier = listOf(1_620_000uL, 0uL, 0uL, 0uL), form = LeagueParams.FORM_KNEES_ALLOWED,
        )
    }

    private val chain = FakeChain()
    private val id = spark.base58()

    private fun NavState.read(now: Long) = then(NavEvent.ChainMoved(chain.view().stages(now)))

    @Test
    fun `the whole member journey, and the finishing branch`() {
        // Connect, first time: the tour, full screen, then Today.
        var now = start - 2 * day
        var s = NavState().then(NavEvent.Connected(tourSeen = false)).read(now)
        assertEquals(Tour(firstRun = true), s.tour)
        assertFalse(s.showsBar)
        s = s.then(NavEvent.TourDone)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.showsBar)
        assertTrue(Badges.today(running = null, holdoutDayDoneOnPhone = false, daily25Done = false))

        // The Daily 25, done.
        s = s.then(NavEvent.StartSitting(Sitting.Daily25(20_000L)))
        assertFalse(s.showsBar)
        s = s.then(NavEvent.SittingEnded)
        assertTrue(s.showsBar)
        assertFalse(Badges.today(running = null, holdoutDayDoneOnPhone = false, daily25Done = true))

        // Join the Spark from Today: the Seeker gate, then the stake confirm.
        s = s.then(NavEvent.OpenStake(id))
        assertEquals(SeekerStatus.Checking, (s.sheet as Sheet.Stake).gate)
        s = s.then(NavEvent.SeekerChecked(SeekerStatus.Confirmed))
        assertEquals(Sheet.Stake(id, 0, SeekerStatus.Confirmed), s.sheet)

        // Stake: the wallet, confirming, landed — You're in.
        s = s.then(NavEvent.TxStarted(TxKind.Join(id, 0)))
        assertEquals(TxPhase.WaitingForWallet, s.tx?.phase)
        s = s.then(NavEvent.TxProgress(TxPhase.Confirming))
        chain.join()
        s = s.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("join")))).read(now)
        assertEquals(true, s.tx?.youreIn)
        assertNull(s.sheet)

        // See the lobby; the Holdout starts and the lobby gives way to Today in the Holdout.
        s = s.then(NavEvent.SeeLobby)
        assertEquals(Screen.Lobby(id), s.top)
        assertEquals(Stage.Lobby, s.stages[id])
        now = start + 60
        s = s.read(now)
        assertEquals(Stage.Active(0, todayDone = false), s.stages[id])
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
        assertTrue(Badges.today(s.stages[id] as Stage.Active, holdoutDayDoneOnPhone = false, daily25Done = true))

        // The day's sitting, then submit: the wallet flow, on chain, back on Today.
        s = s.then(NavEvent.StartSitting(Sitting.HoldoutDay(id, 0)), NavEvent.SittingEnded, NavEvent.TxStarted(TxKind.Submit(id, 0)))
        s = s.then(NavEvent.TxProgress(TxPhase.Confirming))
        chain.submit()
        s = s.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("day0")))).read(now)
        assertNull(s.tx)
        assertEquals(Stage.Active(0, todayDone = true), s.stages[id])
        assertFalse(Badges.today(s.stages[id] as Stage.Active, holdoutDayDoneOnPhone = false, daily25Done = false))

        // The finishing branch starts from here.
        val runningOnDayOne = s

        // A later day is missed: the forfeit opens over Today.
        now = start + 2 * day + 60
        s = s.read(now)
        assertEquals(Stage.Forfeited(1, recorded = false), s.stages[id])
        assertEquals(Screen.Forfeit(id), s.top)
        assertFalse(s.showsBar)

        // The comeback offer: the next Spark's stake sheet on the lost stake's chip, over the forfeit.
        s = s.then(NavEvent.OpenStake(nextSpark.base58(), tier = 0, seekerKnown = true))
        assertEquals(Sheet.Stake(nextSpark.base58(), 0, SeekerStatus.Confirmed), s.sheet)
        assertEquals(Screen.Forfeit(id), s.top)

        // ---- finishing instead -------------------------------------------------------------------
        chain.finishAll()
        now = start + 7 * day + 60
        s = runningOnDayOne.read(now)
        assertEquals(Stage.Settling, s.stages[id])
        assertEquals(Tab.Standing, s.tab)
        assertFalse(Badges.holdouts(s.stages.values))

        chain.settled = true
        s = s.read(now + 600)
        assertTrue(s.stages[id] is Stage.ReadyToClaim)
        assertEquals(Screen.Claim(id), s.top)
        assertTrue(Badges.holdouts(s.stages.values))

        s = s.then(NavEvent.TxStarted(TxKind.Claim(id)), NavEvent.TxProgress(TxPhase.Confirming))
        chain.claim()
        s = s.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("claim")))).read(now + 660)
        assertEquals(TxPhase.Done(TxOutcome.Landed("claim")), s.tx?.phase)
        s = s.then(NavEvent.TxDone)
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.showsBar)
        assertFalse(Badges.holdouts(s.stages.values))

        // H0 lists it under Past, won.
        val h0 = MyHoldoutsModel.build(chain.view(), now + 660)
        assertTrue(h0.yours.isEmpty())
        assertEquals(listOf("Spark · 7-day" to "11.62 SKR"), h0.won.map { it.name to it.amount })
    }
}
