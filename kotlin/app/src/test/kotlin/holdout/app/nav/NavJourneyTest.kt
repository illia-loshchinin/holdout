package holdout.app.nav

import holdout.app.stake.SeekerStatus
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.core.Leaderboard
import holdout.core.Stage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The navigational journeys (screen map §10, J1–J9) and use cases (§11,
 * UC-1…UC-17) as event sequences on the navigation model. Each asserts where
 * the member lands and whether the bar shows; what a screen says is the
 * screens' own tests.
 */
class NavJourneyTest {
    @Test
    fun `J1 UC-1 first reps - connect, tour, Today, Daily 25, back on Today`() {
        var s = NavState().then(NavEvent.Connected(tourSeen = false))
        assertEquals(Tour(firstRun = true, welcome = true), s.tour)
        s = s.then(NavEvent.TourDone)
        assertTrue(s.showsBar)
        s = s.then(NavEvent.StartSitting(Sitting.Daily25(DAY)))
        assertFalse(s.showsBar)
        s = s.then(NavEvent.SittingEnded)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.showsBar)
    }

    @Test
    fun `UC-2 no wallet app - nothing past the connect screen`() {
        val s = NavState().then(NavEvent.SelectTab(Tab.Me), NavEvent.Open(Screen.About), NavEvent.OpenStake(A))
        assertEquals(NavState(), s)
    }

    @Test
    fun `J2 UC-4 stake on yourself - Join, Seeker gate, short, faucet, back, stake, wallet, You're in, lobby, start`() {
        var s = home().then(NavEvent.ChainMoved(mapOf(A to Stage.NotJoined)), NavEvent.SelectTab(Tab.Holdouts), NavEvent.OpenStake(A))
        assertEquals(SeekerStatus.Checking, (s.sheet as Sheet.Stake).gate)
        s = s.then(NavEvent.SeekerChecked(SeekerStatus.Confirmed), NavEvent.OpenFaucet)
        assertTrue(s.sheet is Sheet.Faucet)
        s = s.then(NavEvent.CloseSheet)
        assertEquals(Sheet.Stake(A, 0, SeekerStatus.Confirmed), s.sheet)
        s = s.then(NavEvent.TxStarted(TxKind.Join(A, 0)), NavEvent.TxProgress(TxPhase.Confirming))
        assertEquals(TxPhase.Confirming, s.tx?.phase)
        s = s.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))), NavEvent.ChainMoved(mapOf(A to Stage.Lobby)))
        assertEquals(true, s.tx?.youreIn)
        s = s.then(NavEvent.SeeLobby)
        assertEquals(Screen.Lobby(A), s.top)
        assertEquals(Tab.Holdouts, s.tab)
        s = s.then(NavEvent.ChainMoved(mapOf(A to Stage.LobbyLocked)))
        assertEquals(Screen.Lobby(A), s.top)
        s = s.then(NavEvent.ChainMoved(mapOf(A to Stage.Active(0, false))))
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
    }

    @Test
    fun `J3 UC-6 a day inside a Holdout - reminder, T2, sitting, submit, on chain, optionally its screen and the members`() {
        var s = running().then(NavEvent.SelectTab(Tab.Me), NavEvent.AppOpened)
        assertEquals(Tab.Today, s.tab)
        s = s.then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 5)), NavEvent.SittingEnded, NavEvent.TxStarted(TxKind.Submit(A, 5)))
        s = s.then(NavEvent.TxProgress(TxPhase.Confirming), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))))
        assertNull(s.tx)
        assertEquals(Tab.Today, s.tab)
        s = s.then(NavEvent.Open(Screen.Detail(A)), NavEvent.Open(Screen.Roster(A)))
        assertEquals(listOf(Screen.Detail(A), Screen.Roster(A)), s.stack)
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `J3a a day started from a Holdout's screen is the same sitting as from Today, and ends on Today's root`() {
        val fromToday = running().then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 5)))
        // The screen pushed on Holdouts (a card) and the one pushed on Today (the crowd strip).
        val onHoldouts = running().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(A)))
        val onToday = running().then(NavEvent.Open(Screen.Detail(A)))
        for (detail in listOf(onHoldouts, onToday)) {
            assertFalse(detail.showsBar)
            val started = detail.then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 5)))
            assertEquals(fromToday.sitting, started.sitting)
            assertFalse(started.showsBar)
            val back = started.then(NavEvent.SittingEnded)
            assertNull(back.sitting)
            assertEquals(Tab.Today, back.tab)
            assertTrue(back.onRoot)
        }
    }

    @Test
    fun `J3b Today's crowd strip opens the Holdout's screen over Today, and Back brings Today back`() {
        var s = running()
        assertEquals(Tab.Today, s.tab)
        s = s.then(NavEvent.Open(Screen.Detail(A)))
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Detail(A), s.top)
        assertFalse(s.showsBar)
        s = s.then(NavEvent.Back)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.showsBar)
    }

    @Test
    fun `J4 UC-5 change your mind - H0, lobby, leave lands, Back to Holdouts, H0`() {
        val s = home().then(
            NavEvent.ChainMoved(mapOf(A to Stage.Lobby)),
            NavEvent.SelectTab(Tab.Holdouts),
            NavEvent.Open(Screen.Lobby(A)),
            NavEvent.ChainMoved(mapOf(A to Stage.NotJoined)),
            NavEvent.Back,
        )
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
    }

    @Test
    fun `J5 UC-9 UC-10 miss a day - forfeit, comeback stake sheet on the lost chip, or the Daily 25`() {
        val forfeit = running().then(NavEvent.ChainMoved(mapOf(A to Stage.Forfeited(6, recorded = false))))
        assertEquals(Screen.Forfeit(A), forfeit.top)
        val comeback = forfeit.then(NavEvent.OpenStake(B, tier = 2))
        assertEquals(Sheet.Stake(B, 2, SeekerStatus.Checking), comeback.sheet)
        val daily = forfeit.then(NavEvent.StartSitting(Sitting.Daily25(DAY)), NavEvent.SittingEnded)
        assertEquals(Tab.Today, daily.tab)
        assertTrue(daily.onRoot)
    }

    @Test
    fun `J6 UC-11 finish and get paid - settling over Today, settled opens claim, claimed, H0 finished`() {
        var s = running().then(NavEvent.ChainMoved(mapOf(A to Stage.Settling)))
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Detail(A), s.top)
        s = s.then(NavEvent.ChainMoved(mapOf(A to claimable())))
        assertEquals(Screen.Claim(A), s.top)
        s = s.then(NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))), NavEvent.TxDone)
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.showsBar)
    }

    @Test
    fun `UC-11 the finished notice opens Today, whose nudge opens the claim`() {
        val s = home().then(NavEvent.SelectTab(Tab.Me), NavEvent.AppOpened, NavEvent.Open(Screen.Claim(A)))
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Claim(A), s.top)
    }

    @Test
    fun `J7 UC-17 watch a Holdout thin without joining - Running now, detail, roster, back`() {
        val s = home().then(NavEvent.Open(Screen.Detail(A)), NavEvent.Open(Screen.Roster(A)), NavEvent.Back, NavEvent.Back)
        assertEquals(home(), s)
    }

    @Test
    fun `J7 UC-12 the whole cycle on a short test Holdout`() {
        var s = home().then(NavEvent.ChainMoved(mapOf(T to Stage.NotJoined)), NavEvent.OpenStake(T), NavEvent.SeekerChecked(SeekerStatus.Confirmed))
        s = s.then(NavEvent.TxStarted(TxKind.Join(T, 0)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("j"))), NavEvent.TxBack)
        assertNull(s.tx)
        s = s.then(NavEvent.ChainMoved(mapOf(T to Stage.Lobby)), NavEvent.ChainMoved(mapOf(T to Stage.Active(0, false))))
        for (day in 0..2) {
            s = s.then(NavEvent.StartSitting(Sitting.HoldoutDay(T, day)), NavEvent.SittingEnded, NavEvent.TxStarted(TxKind.Submit(T, day)))
            s = s.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("d$day"))), NavEvent.ChainMoved(mapOf(T to Stage.Active(day, true))))
            assertEquals(Tab.Today, s.tab)
        }
        s = s.then(NavEvent.ChainMoved(mapOf(T to Stage.Settling)))
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Detail(T), s.top)
        s = s.then(NavEvent.ChainMoved(mapOf(T to claimable())))
        assertEquals(Screen.Claim(T), s.top)
    }

    @Test
    fun `J8 UC-14 offline - the Daily 25 works, a day done offline waits and goes from Today`() {
        var s = home().then(NavEvent.StartSitting(Sitting.Daily25(DAY)), NavEvent.SittingEnded)
        assertEquals(Tab.Today, s.tab)
        s = running().then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 5)), NavEvent.SittingEnded)
        assertNull(s.tx)
        s = s.then(NavEvent.TxStarted(TxKind.Submit(A, 5)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Failed(mayStillLand = false, message = "Devnet did not answer."))), NavEvent.TxBack)
        assertEquals(Tab.Today, s.tab)
        assertNull(s.tx)
    }

    @Test
    fun `J9 UC-13 where do I stand - Me, Leaderboards, back to Me`() {
        val me = home().then(NavEvent.SelectTab(Tab.Me))
        val s = me.then(NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.EASY_7D)))
        assertFalse(s.showsBar)
        assertEquals(me, s.then(NavEvent.Back))
    }

    @Test
    fun `UC-3 test tokens when short - from the stake sheet, from Today, from a claim, back where it started`() {
        val stake = home().then(NavEvent.OpenStake(A, seekerKnown = true))
        assertEquals(stake, stake.then(NavEvent.OpenFaucet, NavEvent.Back))
        val today = running()
        assertEquals(today, today.then(NavEvent.OpenFaucet, NavEvent.CloseSheet))
        val claim = home().then(NavEvent.Open(Screen.Claim(A)))
        assertEquals(claim, claim.then(NavEvent.OpenFaucet, NavEvent.Back))
    }

    @Test
    fun `UC-7 reps in two or three sittings - each stop lands on Today, the next starts from it`() {
        var s = running()
        repeat(3) {
            s = s.then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 5)), NavEvent.SittingEnded)
            assertEquals(Tab.Today, s.tab)
            assertTrue(s.showsBar)
        }
    }

    @Test
    fun `UC-8 see who dropped - T2, the crowd strip, members, back to the Holdout's screen, back to Today`() {
        val detail = running().then(NavEvent.Open(Screen.Detail(A)))
        assertEquals(detail, detail.then(NavEvent.Open(Screen.Roster(A)), NavEvent.Back))
        assertEquals(running(), detail.then(NavEvent.Back))
    }

    @Test
    fun `J10 FR-069a the last day - submit lands on Today, its Holdout's screen opens over Today once, Back returns`() {
        var s = running().then(NavEvent.TxStarted(TxKind.Submit(A, 6)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("last"))))
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
        s = s.then(NavEvent.LastDayOnChain(A))
        assertEquals(Screen.Detail(A), s.top)
        s = s.then(NavEvent.Back)
        assertTrue(s.onRoot)
        // The same moment seen again (the next read): nothing.
        assertEquals(s, s.then(NavEvent.LastDayOnChain(A)))
        // Settling then finds Today at its root and the Holdout's screen already shown once.
        assertEquals(s.copy(stages = mapOf(A to Stage.Settling)), s.then(NavEvent.ChainMoved(mapOf(A to Stage.Settling))))
    }

    @Test
    fun `UC-15 UC-16 know what it runs on and switch wallets - Me, About, tour again, disconnect`() {
        var s = home().then(NavEvent.SelectTab(Tab.Me), NavEvent.Open(Screen.About), NavEvent.OpenTour())
        assertEquals(Tour(firstRun = false), s.tour)
        s = s.then(NavEvent.TourDone)
        assertEquals(Screen.About, s.top)
        s = s.then(NavEvent.Disconnected)
        assertEquals(NavState(), s)
        s = s.then(NavEvent.Connected(tourSeen = true))
        assertEquals(home(), s)
    }

    private companion object {
        const val A = "CohortA"
        const val B = "CohortB"
        const val T = "TestCohort"
        const val DAY = 20_400L

        fun home() = NavState().then(NavEvent.Connected(tourSeen = true))

        fun running() = home().then(NavEvent.ChainMoved(mapOf(A to Stage.Active(5, todayDone = false))))

        fun claimable() = Stage.ReadyToClaim(1_412_000_000uL, 1_250_000_000uL, 162_000_000uL)
    }
}
