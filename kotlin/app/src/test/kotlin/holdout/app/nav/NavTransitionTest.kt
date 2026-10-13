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
 * Every arrow of the screen map (docs/holdout_screen_map.md §4), re-read for the
 * 3-tab bar (§3, rulings G1, G7, G8; FR-069, FR-069b), as one event on the pure navigation model.
 * Test names carry the screen IDs; the N-xx IDs are the scenario-coverage list's.
 */
class NavTransitionTest {
    // ---- before a wallet and first run -------------------------------------------------------

    @Test
    fun `A1 opens with no wallet - full screen, no bar`() {
        val s = NavState()
        assertFalse(s.connected)
        assertFalse(s.showsBar)
    }

    @Test
    fun `N-03 A1 connect, first time - B1 tour, full screen`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = false))
        assertEquals(Tour(firstRun = true, welcome = true), s.tour)
        assertFalse(s.showsBar)
    }

    @Test
    fun `N-04 A1 connect, tour seen - T1 Today root with the bar`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = true))
        assertNull(s.tour)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.showsBar)
    }

    @Test
    fun `N-08 B2 tour finished on first run - Today root`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = false), NavEvent.TourDone)
        assertNull(s.tour)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.showsBar)
    }

    @Test
    fun `first-run tour does not take Back - the card handles it, the app never skips the tour`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = false))
        assertFalse(s.canGoBack)
        assertEquals(s, s.then(NavEvent.Back))
    }

    @Test
    fun `N-10 T1 How Holdout works - B1, finishing returns to Today`() {
        val today = home()
        val s = today.then(NavEvent.OpenTour())
        assertEquals(Tour(firstRun = false), s.tour)
        assertFalse(s.showsBar)
        assertEquals(today, s.then(NavEvent.TourDone))
    }

    @Test
    fun `N-11 X1 Tour again - B1, finishing or Back returns to About on the Me tab`() {
        val about = home().then(NavEvent.SelectTab(Tab.Me), NavEvent.Open(Screen.About))
        val s = about.then(NavEvent.OpenTour())
        assertEquals(about, s.then(NavEvent.TourDone))
        assertEquals(about, s.then(NavEvent.Back))
    }

    @Test
    fun `N-12 W1 Disconnect - A1, everything cleared`() {
        val s = home().then(NavEvent.SelectTab(Tab.Me), NavEvent.Open(Screen.About), NavEvent.Disconnected)
        assertEquals(NavState(), s)
    }

    // ---- the bar ------------------------------------------------------------------------------

    @Test
    fun `the bar has exactly three tabs - Today, Holdouts, Me, in that order`() {
        assertEquals(listOf(Tab.Today, Tab.Holdouts, Tab.Me), Tab.entries.toList())
        assertEquals(listOf("Today", "Holdouts", "Me"), Tab.entries.map { it.label })
    }

    @Test
    fun `N-20 every tab root is one tap from every other`() {
        for (from in Tab.entries) {
            for (to in Tab.entries) {
                val s = home().then(NavEvent.SelectTab(from), NavEvent.SelectTab(to))
                assertEquals(to, s.tab, "$from -> $to")
                assertTrue(s.onRoot, "$from -> $to")
                assertTrue(s.showsBar, "$from -> $to")
            }
        }
    }

    @Test
    fun `N-21 tapping the tab you are on pops its pushed screens to the root`() {
        val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(A)), NavEvent.Open(Screen.Roster(A)))
        assertFalse(s.showsBar)
        val popped = s.then(NavEvent.SelectTab(Tab.Holdouts))
        assertTrue(popped.onRoot)
        assertEquals(Tab.Holdouts, popped.tab)
    }

    @Test
    fun `each tab keeps its own stack while another tab is shown`() {
        val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Lobby(A)), NavEvent.SelectTab(Tab.Me), NavEvent.SelectTab(Tab.Holdouts))
        assertEquals(Screen.Lobby(A), s.top)
    }

    @Test
    fun `the bar shows on tab roots only`() {
        assertTrue(home().showsBar)
        assertFalse(home().then(NavEvent.Open(Screen.Detail(A))).showsBar)
        assertFalse(home().then(NavEvent.StartSitting(Sitting.Daily25(DAY))).showsBar)
        assertFalse(home().then(NavEvent.TxStarted(TxKind.Claim(A))).showsBar)
        assertFalse(home().then(NavEvent.OpenTour()).showsBar)
    }

    @Test
    fun `the bar is hidden on a Holdout's screen, Members, Leaderboards and every other pushed screen`() {
        for (screen in listOf(Screen.Detail(A), Screen.Roster(A), Screen.Leaderboards(Leaderboard.Board.EASY_7D), Screen.Lobby(A), Screen.Forfeit(A), Screen.Claim(A), Screen.About)) {
            for (tab in Tab.entries) {
                val s = home().then(NavEvent.SelectTab(tab), NavEvent.Open(screen))
                assertFalse(s.showsBar, "$screen on $tab")
                assertTrue(s.then(NavEvent.Back).showsBar, "back from $screen on $tab")
            }
        }
    }

    @Test
    fun `N-23 Back on the Today root leaves the app, on another root it goes to Today`() {
        assertFalse(home().canGoBack)
        for (tab in listOf(Tab.Holdouts, Tab.Me)) {
            val root = home().then(NavEvent.SelectTab(tab))
            assertTrue(root.canGoBack, "$tab")
            assertEquals(Tab.Today, root.then(NavEvent.Back).tab, "$tab")
            assertTrue(root.then(NavEvent.Back).onRoot, "$tab")
        }
    }

    @Test
    fun `N-24 Back from a pushed screen returns to the screen it was opened from, in its tab`() {
        val detail = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(A)))
        val roster = detail.then(NavEvent.Open(Screen.Roster(A)))
        assertEquals(detail, roster.then(NavEvent.Back))
        assertTrue(detail.then(NavEvent.Back).onRoot)
        assertEquals(Tab.Holdouts, detail.then(NavEvent.Back).tab)
    }

    @Test
    fun `a Holdout's screen opened from a Holdouts card or a Running now row is pushed on Holdouts, and Back returns there`() {
        val holdouts = home().then(NavEvent.SelectTab(Tab.Holdouts))
        val opened = holdouts.then(NavEvent.Open(Screen.Detail(A)))
        assertEquals(Tab.Holdouts, opened.tab)
        assertEquals(listOf<Screen>(Screen.Detail(A)), opened.stack)
        assertTrue(opened.stacks[Tab.Today].orEmpty().isEmpty())
        assertEquals(holdouts, opened.then(NavEvent.Back))
        // The members, one level further: Back pops one screen at a time.
        val members = opened.then(NavEvent.Open(Screen.Roster(A)))
        assertEquals(opened, members.then(NavEvent.Back))
    }

    @Test
    fun `a Holdout's screen opened from Today's crowd strip or a row is pushed on Today, and Back returns to Today`() {
        val today = running()
        val opened = today.then(NavEvent.Open(Screen.Detail(A)))
        assertEquals(Tab.Today, opened.tab)
        assertEquals(listOf<Screen>(Screen.Detail(A)), opened.stack)
        assertFalse(opened.showsBar)
        assertEquals(today, opened.then(NavEvent.Back))
    }

    @Test
    fun `a Holdout's screen is on the tab that opened it, each tab keeping its own`() {
        val s = home().then(NavEvent.Open(Screen.Detail(A)), NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(B)))
        assertEquals(listOf<Screen>(Screen.Detail(B)), s.stack)
        assertEquals(listOf<Screen>(Screen.Detail(A)), s.then(NavEvent.SelectTab(Tab.Today)).stack)
    }

    @Test
    fun `opening the screen already on top does not push it twice`() {
        val s = home().then(NavEvent.Open(Screen.Detail(A)), NavEvent.Open(Screen.Detail(A)))
        assertEquals(1, s.stack.size)
    }

    @Test
    fun `N-25 the bar takes no taps while a sitting, the signing flow or the first-run tour is up`() {
        for (s in listOf(
            home().then(NavEvent.StartSitting(Sitting.Daily25(DAY))),
            home().then(NavEvent.TxStarted(TxKind.Submit(A, 3))),
            NavState().then(NavEvent.Connected(tourSeen = false)),
        )) {
            assertEquals(s, s.then(NavEvent.SelectTab(Tab.Me)))
            assertEquals(s, s.then(NavEvent.Open(Screen.About)))
        }
    }

    @Test
    fun `N-26 a reminder tap lands on the Today root, never a stale pushed screen or a sheet`() {
        val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(A)), NavEvent.OpenFaucet, NavEvent.AppOpened)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
        assertNull(s.sheet)
        assertTrue(s.showsBar)
    }

    @Test
    fun `a reminder tap never interrupts a sitting or a signature`() {
        val sitting = home().then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 2)), NavEvent.AppOpened)
        assertEquals(Sitting.HoldoutDay(A, 2), sitting.sitting)
        val signing = home().then(NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.AppOpened)
        assertEquals(TxKind.Claim(A), signing.tx?.kind)
    }

    // ---- Today (T1, T2) -----------------------------------------------------------------------

    @Test
    fun `T1 Start - S1 the Daily 25 sitting, full screen`() {
        val s = home().then(NavEvent.StartSitting(Sitting.Daily25(DAY)))
        assertEquals(Sitting.Daily25(DAY), s.sitting)
        assertFalse(s.canGoBack)
    }

    @Test
    fun `T1 Join - H2 the stake sheet over Today, Seeker gate checking first`() {
        val s = home().then(NavEvent.OpenStake(A))
        assertEquals(Sheet.Stake(A, 0, SeekerStatus.Checking), s.sheet)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
    }

    @Test
    fun `T1 nudge - the matching Holdout screen, pushed on Today`() {
        val s = home().then(NavEvent.Open(Screen.Claim(A)))
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Claim(A), s.top)
        assertTrue(s.then(NavEvent.Back).onRoot)
    }

    @Test
    fun `T1 Running now - H4 as a watcher, then the roster`() {
        val s = home().then(NavEvent.Open(Screen.Detail(A)), NavEvent.Open(Screen.Roster(A)))
        assertEquals(listOf(Screen.Detail(A), Screen.Roster(A)), s.stack)
    }

    @Test
    fun `T2 Do more - S1 the Holdout day sitting`() {
        val s = home().then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 11)))
        assertEquals(Sitting.HoldoutDay(A, 11), s.sitting)
    }

    @Test
    fun `T2 Submit day - the wallet flow, then back on Today with the day on chain`() {
        val s = running().then(NavEvent.TxStarted(TxKind.Submit(A, 11)))
        assertEquals(TxPhase.WaitingForWallet, s.tx?.phase)
        val landed = s.then(NavEvent.TxProgress(TxPhase.Confirming), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))))
        assertNull(landed.tx)
        assertEquals(Tab.Today, landed.tab)
        assertTrue(landed.onRoot)
    }

    @Test
    fun `N2 a reminder tap - T2 on the Today tab`() {
        val s = running().then(NavEvent.SelectTab(Tab.Me), NavEvent.AppOpened)
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `T2 missed a deadline - H6 forfeit opens over Today`() {
        val s = running().then(NavEvent.ChainMoved(mapOf(A to Stage.Forfeited(6, recorded = false))))
        assertEquals(Screen.Forfeit(A), s.top)
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `T2 missed a deadline while elsewhere - nothing moves, H0 carries it`() {
        val s = running().then(NavEvent.SelectTab(Tab.Me), NavEvent.ChainMoved(mapOf(A to Stage.Forfeited(6, recorded = false))))
        assertEquals(Tab.Me, s.tab)
        assertTrue(s.onRoot)
    }

    @Test
    fun `T2 last day passed - H4 settling opens over Today, Back returns to Today`() {
        val s = running().then(NavEvent.ChainMoved(mapOf(A to Stage.Settling)))
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Detail(A), s.top)
        assertFalse(s.showsBar)
        assertTrue(s.then(NavEvent.Back).onRoot)
        assertEquals(Tab.Today, s.then(NavEvent.Back).tab)
    }

    // ---- the last day on chain (FR-069a) --------------------------------------------------------

    @Test
    fun `the last day on chain opens that Holdout's screen over Today`() {
        val s = running().then(NavEvent.LastDayOnChain(A))
        assertEquals(Tab.Today, s.tab)
        assertEquals(listOf<Screen>(Screen.Detail(A)), s.stack)
        assertFalse(s.showsBar)
        val back = s.then(NavEvent.Back)
        assertEquals(Tab.Today, back.tab, "Back returns to Today")
        assertTrue(back.onRoot)
    }

    @Test
    fun `the last day on chain opens once per process - a second event changes nothing, also after Back`() {
        val opened = running().then(NavEvent.LastDayOnChain(A))
        assertEquals(opened, opened.then(NavEvent.LastDayOnChain(A)))
        val closed = opened.then(NavEvent.Back)
        assertEquals(closed, closed.then(NavEvent.LastDayOnChain(A)))
        assertTrue(closed.onRoot)
        // Another Holdout is another moment.
        assertEquals(Screen.Detail(B), closed.then(NavEvent.LastDayOnChain(B)).top)
    }

    @Test
    fun `the last day on chain leaves the member where they are when something else holds the screen`() {
        val covered = listOf(
            running().then(NavEvent.StartSitting(Sitting.Daily25(DAY))),
            running().then(NavEvent.TxStarted(TxKind.Submit(A, 3))),
            running().then(NavEvent.OpenStake(B)),
            running().then(NavEvent.SelectTab(Tab.Me)),
        )
        for (s in covered) assertEquals(s, s.then(NavEvent.LastDayOnChain(A)))
        // Not used up by that: once the member is back on Today's root it opens.
        val sitting = running().then(NavEvent.StartSitting(Sitting.Daily25(DAY)), NavEvent.LastDayOnChain(A), NavEvent.SittingEnded)
        assertEquals(Screen.Detail(A), sitting.then(NavEvent.LastDayOnChain(A)).top)
    }

    @Test
    fun `the last day on chain does not stack a second copy of a Holdout's screen already open`() {
        val s = running().then(NavEvent.Open(Screen.Detail(A)), NavEvent.Open(Screen.Roster(A)), NavEvent.LastDayOnChain(A))
        assertEquals(listOf(Screen.Detail(A), Screen.Roster(A)), s.stack)
        assertEquals(s, s.then(NavEvent.LastDayOnChain(A)))
    }

    @Test
    fun `a Holdout that was opened by the last day is not opened again by its settling`() {
        val s = running().then(NavEvent.LastDayOnChain(A), NavEvent.Back, NavEvent.ChainMoved(mapOf(A to Stage.Settling)))
        assertTrue(s.onRoot)
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `the first chain read moves nothing - only a change seen while the app is open does`() {
        val s = home().then(NavEvent.ChainMoved(mapOf(A to Stage.Forfeited(6, recorded = true))))
        assertTrue(s.onRoot)
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `nothing moves under a sitting - the stage is still remembered`() {
        val s = running().then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 3)), NavEvent.ChainMoved(mapOf(A to Stage.Forfeited(3, false))))
        assertEquals(Sitting.HoldoutDay(A, 3), s.sitting)
        assertTrue(s.onRoot)
        assertEquals(Stage.Forfeited(3, false), s.stages[A])
    }

    // ---- sitting ------------------------------------------------------------------------------

    @Test
    fun `S6 Daily 25 Done or S7 stopped - T1, the Today root`() {
        val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Forfeit(A)), NavEvent.StartSitting(Sitting.Daily25(DAY)), NavEvent.SittingEnded)
        assertNull(s.sitting)
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
    }

    @Test
    fun `S6 Holdout Submit - the sitting ends and the wallet flow opens`() {
        val s = running().then(NavEvent.StartSitting(Sitting.HoldoutDay(A, 11)), NavEvent.SittingEnded, NavEvent.TxStarted(TxKind.Submit(A, 11)))
        assertNull(s.sitting)
        assertEquals(TxKind.Submit(A, 11), s.tx?.kind)
    }

    @Test
    fun `a send started under a sitting stays inline - the sitting is never covered`() {
        val s = home().then(NavEvent.StartSitting(Sitting.Daily25(DAY)), NavEvent.TxStarted(TxKind.Submit(A, 2)))
        assertNull(s.tx)
        assertEquals(Sitting.Daily25(DAY), s.sitting)
    }

    // ---- A Holdout's screen (H4) -----------------------------------------------------------------

    @Test
    fun `H4 Members - H5, back to H4`() {
        val detail = home().then(NavEvent.Open(Screen.Detail(A)))
        val s = detail.then(NavEvent.Open(Screen.Roster(A)))
        assertEquals(Screen.Roster(A), s.top)
        assertEquals(detail, s.then(NavEvent.Back))
    }

    @Test
    fun `W1 Leaderboards - L1 pushed on Me, a chip swaps the board in place, back to Me`() {
        val me = home().then(NavEvent.SelectTab(Tab.Me))
        val s = me.then(NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.EASY_7D)))
        assertFalse(s.showsBar)
        val other = s.then(NavEvent.Swap(Screen.Leaderboards(Leaderboard.Board.HARD_21D)))
        assertEquals(listOf<Screen>(Screen.Leaderboards(Leaderboard.Board.HARD_21D)), other.stack)
        assertEquals(me, other.then(NavEvent.Back))
    }

    @Test
    fun `H4 settled - H7 claim opens over the Holdout's detail`() {
        val s = home().then(
            NavEvent.ChainMoved(mapOf(A to Stage.Settling)),
            NavEvent.SelectTab(Tab.Holdouts),
            NavEvent.Open(Screen.Detail(A)),
            NavEvent.ChainMoved(mapOf(A to claimable())),
        )
        assertEquals(listOf(Screen.Detail(A), Screen.Claim(A)), s.stack)
    }

    @Test
    fun `H4 over Today settled - H7 claim opens over the Holdout's screen on Today`() {
        val s = running().then(NavEvent.ChainMoved(mapOf(A to Stage.Settling)), NavEvent.ChainMoved(mapOf(A to claimable())))
        assertEquals(Tab.Today, s.tab)
        assertEquals(listOf(Screen.Detail(A), Screen.Claim(A)), s.stack)
        assertEquals(Screen.Detail(A), s.then(NavEvent.Back).top)
    }

    @Test
    fun `the claim that is done takes the Holdout's screen off Today with it`() {
        val s = running().then(
            NavEvent.ChainMoved(mapOf(A to Stage.Settling)),
            NavEvent.ChainMoved(mapOf(A to claimable())),
            NavEvent.TxStarted(TxKind.Claim(A)),
            NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))),
            NavEvent.TxDone,
        )
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.stacks.isEmpty(), "no stale screen of a claimed Holdout is left on Today")
    }

    // ---- Holdouts ----------------------------------------------------------------------------

    @Test
    fun `H0 cards - lobby H3, forfeit H6, settling H4, claim H7, finished H4, pushed on Holdouts`() {
        for (screen in listOf(Screen.Lobby(A), Screen.Forfeit(A), Screen.Detail(A), Screen.Claim(A))) {
            val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(screen))
            assertEquals(Tab.Holdouts, s.tab)
            assertEquals(screen, s.top)
            assertFalse(s.showsBar)
        }
    }

    @Test
    fun `H0 running card - T2, the Today tab`() {
        val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.SelectTab(Tab.Today))
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `H0 offer Join - H2 over H0, drag down returns to H0`() {
        val h0 = home().then(NavEvent.SelectTab(Tab.Holdouts))
        val s = h0.then(NavEvent.OpenStake(B, seekerKnown = true))
        assertEquals(Sheet.Stake(B, 0, SeekerStatus.Confirmed), s.sheet)
        assertEquals(h0, s.then(NavEvent.CloseSheet))
        assertEquals(h0, s.then(NavEvent.Back))
    }

    @Test
    fun `N-70 H2 Seeker gate - checking then confirmed shows the stake confirm`() {
        val s = home().then(NavEvent.OpenStake(A), NavEvent.SeekerChecked(SeekerStatus.Confirmed))
        assertEquals(SeekerStatus.Confirmed, (s.sheet as Sheet.Stake).gate)
    }

    @Test
    fun `N-71 H2 Seeker gate - not a Seeker, Not now returns to the opener`() {
        val opener = home().then(NavEvent.SelectTab(Tab.Holdouts))
        val s = opener.then(NavEvent.OpenStake(A), NavEvent.SeekerChecked(SeekerStatus.NotSeeker))
        assertEquals(SeekerStatus.NotSeeker, (s.sheet as Sheet.Stake).gate)
        assertEquals(opener, s.then(NavEvent.CloseSheet))
    }

    @Test
    fun `N-72 H2 Seeker gate - could not check, Try again checks again`() {
        val s = home().then(NavEvent.OpenStake(A), NavEvent.SeekerChecked(SeekerStatus.CouldNotCheck), NavEvent.SeekerRetry)
        assertEquals(SeekerStatus.Checking, (s.sheet as Sheet.Stake).gate)
    }

    @Test
    fun `a check answering after the gate was passed changes nothing`() {
        val s = home().then(NavEvent.OpenStake(A, seekerKnown = true), NavEvent.SeekerChecked(SeekerStatus.CouldNotCheck))
        assertEquals(SeekerStatus.Confirmed, (s.sheet as Sheet.Stake).gate)
    }

    @Test
    fun `N-74 H2 short on SKR or SOL - the faucet sheet, and back to the stake sheet`() {
        val stake = home().then(NavEvent.OpenStake(A, tier = 2, seekerKnown = true))
        val faucet = stake.then(NavEvent.OpenFaucet)
        assertEquals(Sheet.Faucet(returnTo = stake.sheet as Sheet.Stake), faucet.sheet)
        assertEquals(stake, faucet.then(NavEvent.Back))
        assertEquals(stake, faucet.then(NavEvent.CloseSheet))
    }

    @Test
    fun `W1 Get test tokens - the faucet sheet over Me, closing returns to Me`() {
        val me = home().then(NavEvent.SelectTab(Tab.Me))
        val s = me.then(NavEvent.OpenFaucet)
        assertEquals(Sheet.Faucet(null), s.sheet)
        assertEquals(me, s.then(NavEvent.Back))
    }

    @Test
    fun `H2 Stake - the wallet flow opens over the sheet, waiting for your wallet`() {
        val s = stakeSheet().then(NavEvent.TxStarted(TxKind.Join(A, 1)))
        assertEquals(TxFlow(TxKind.Join(A, 1), TxPhase.WaitingForWallet), s.tx)
        assertFalse(s.showsBar)
    }

    @Test
    fun `waiting for your wallet has no cancel - Back does nothing`() {
        val s = stakeSheet().then(NavEvent.TxStarted(TxKind.Join(A, 1)))
        assertEquals(s, s.then(NavEvent.Back))
        assertEquals(s, s.then(NavEvent.TxBack))
    }

    @Test
    fun `H2 Stake landed - straight to You're in, the sheet closed`() {
        val s = joining().then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))))
        assertEquals(true, s.tx?.youreIn)
        assertNull(s.sheet)
    }

    @Test
    fun `You're in - See the lobby pushes H3 on the tab the join came from`() {
        val s = joining().then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))), NavEvent.SeeLobby)
        assertNull(s.tx)
        assertEquals(Tab.Today, s.tab)
        assertEquals(Screen.Lobby(A), s.top)
    }

    @Test
    fun `You're in - Do today's Daily 25 starts the sitting`() {
        val s = joining().then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))), NavEvent.StartSitting(Sitting.Daily25(DAY)))
        assertNull(s.tx)
        assertEquals(Sitting.Daily25(DAY), s.sitting)
    }

    @Test
    fun `H2 declined or failed - Try again waits for the wallet, Back returns to the stake sheet`() {
        for (outcome in listOf(TxOutcome.Declined, TxOutcome.Failed(mayStillLand = false, message = "x"))) {
            val result = joining().then(NavEvent.TxProgress(TxPhase.Done(outcome)))
            assertEquals(TxPhase.WaitingForWallet, result.then(NavEvent.TxRetry).tx?.phase)
            val back = result.then(NavEvent.TxBack)
            assertNull(back.tx)
            assertEquals(Sheet.Stake(A, 1, SeekerStatus.Confirmed), back.sheet)
        }
    }

    @Test
    fun `H2 joining closed - Holdouts open to join, on the Holdouts tab`() {
        val s = joining().then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.JoiningClosed)), NavEvent.SeeOpenHoldouts)
        assertNull(s.tx)
        assertNull(s.sheet)
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
    }

    @Test
    fun `H2 not enough SOL - the faucet sheet, which returns to the stake sheet`() {
        val s = joining().then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.NotEnoughSol)), NavEvent.OpenFaucet)
        assertNull(s.tx)
        val faucet = s.sheet as Sheet.Faucet
        assertEquals(Sheet.Stake(A, 1, SeekerStatus.Confirmed), faucet.returnTo)
        assertEquals(Sheet.Stake(A, 1, SeekerStatus.Confirmed), s.then(NavEvent.Back).sheet)
    }

    @Test
    fun `leaving the confirming screen - back where the join started, the sheet closed`() {
        val s = joining().then(NavEvent.TxProgress(TxPhase.Confirming), NavEvent.TxBack)
        assertNull(s.tx)
        assertNull(s.sheet)
        assertTrue(s.showsBar)
    }

    @Test
    fun `a join that lands with no flow showing - the sheet gives way to H3`() {
        val s = home().then(NavEvent.ChainMoved(mapOf(A to Stage.NotJoined)), NavEvent.OpenStake(A, seekerKnown = true), NavEvent.ChainMoved(mapOf(A to Stage.Lobby)))
        assertNull(s.sheet)
        assertEquals(Screen.Lobby(A), s.top)
    }

    @Test
    fun `N-77 H3 See who's in - H5, back to H3`() {
        val lobby = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Lobby(A)))
        assertEquals(lobby, lobby.then(NavEvent.Open(Screen.Roster(A)), NavEvent.Back))
    }

    @Test
    fun `N-78 H3 Leave landed - the lobby stays to say You left, Back to Holdouts goes to H0`() {
        val left = home().then(
            NavEvent.ChainMoved(mapOf(A to Stage.Lobby)),
            NavEvent.SelectTab(Tab.Holdouts),
            NavEvent.Open(Screen.Lobby(A)),
            NavEvent.ChainMoved(mapOf(A to Stage.NotJoined)),
        )
        // Still the screen shown, so it can say the stake is back in the wallet.
        assertEquals(Screen.Lobby(A), left.top)
        val s = left.then(NavEvent.Back)
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.stacks.values.none { Screen.Lobby(A) in it })
    }

    @Test
    fun `N-78 H3 Leave landed from a lobby opened on Today - Back to Holdouts still lands on H0`() {
        val left = home().then(
            NavEvent.ChainMoved(mapOf(A to Stage.Lobby)),
            NavEvent.Open(Screen.Lobby(A)),
            NavEvent.ChainMoved(mapOf(A to Stage.NotJoined)),
        )
        assertEquals(Screen.Lobby(A), left.top)
        val s = left.then(NavEvent.Back)
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.stacks.values.none { Screen.Lobby(A) in it })
    }

    @Test
    fun `a lobby that is not shown when the leave lands is taken off the stacks`() {
        val s = home().then(
            NavEvent.ChainMoved(mapOf(A to Stage.Lobby)),
            NavEvent.SelectTab(Tab.Holdouts),
            NavEvent.Open(Screen.Lobby(A)),
            NavEvent.SelectTab(Tab.Today),
            NavEvent.ChainMoved(mapOf(A to Stage.NotJoined)),
        )
        assertTrue(s.stacks.values.none { Screen.Lobby(A) in it })
    }

    @Test
    fun `N-79 H3 start time - T2, the Today root`() {
        val s = home().then(
            NavEvent.ChainMoved(mapOf(A to Stage.LobbyLocked)),
            NavEvent.SelectTab(Tab.Holdouts),
            NavEvent.Open(Screen.Lobby(A)),
            NavEvent.ChainMoved(mapOf(A to Stage.Active(0, false))),
        )
        assertEquals(Tab.Today, s.tab)
        assertTrue(s.onRoot)
        assertTrue(s.stacks.values.none { Screen.Lobby(A) in it })
    }

    @Test
    fun `H6 Today's Daily 25 - S1 the Daily 25 sitting`() {
        val s = home().then(NavEvent.Open(Screen.Forfeit(A)), NavEvent.StartSitting(Sitting.Daily25(DAY)))
        assertEquals(Sitting.Daily25(DAY), s.sitting)
    }

    @Test
    fun `H6 the way back - H2 on the lost stake's chip, over the forfeit`() {
        val s = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Forfeit(A)), NavEvent.OpenStake(B, tier = 3))
        assertEquals(Sheet.Stake(B, 3, SeekerStatus.Checking), s.sheet)
        assertEquals(Screen.Forfeit(A), s.top)
    }

    @Test
    fun `H7 Claim - the wallet flow, claimed, Done goes to H0`() {
        val claim = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Claim(A)))
        val claimed = claim.then(NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.TxProgress(TxPhase.Confirming), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))))
        assertEquals(TxPhase.Done(TxOutcome.Landed("sig")), claimed.tx?.phase)
        val done = claimed.then(NavEvent.TxDone)
        assertNull(done.tx)
        assertEquals(Tab.Holdouts, done.tab)
        assertTrue(done.onRoot)
        assertTrue(done.stacks.values.none { Screen.Claim(A) in it })
    }

    @Test
    fun `H7 claimed from Today - Done still lands on H0`() {
        val s = home().then(NavEvent.Open(Screen.Claim(A)), NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("s"))), NavEvent.TxDone)
        assertEquals(Tab.Holdouts, s.tab)
        assertTrue(s.stacks[Tab.Today].orEmpty().isEmpty())
    }

    @Test
    fun `H7 claimed after a Spark - Make it a Habit opens the stake sheet`() {
        val s = home().then(NavEvent.Open(Screen.Claim(A)), NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("s"))), NavEvent.OpenStake(B, seekerKnown = true))
        assertNull(s.tx)
        assertEquals(Sheet.Stake(B, 0, SeekerStatus.Confirmed), s.sheet)
    }

    @Test
    fun `H7 declined - Back returns to the claim screen`() {
        val claim = home().then(NavEvent.Open(Screen.Claim(A)))
        val s = claim.then(NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Declined)), NavEvent.TxBack)
        assertEquals(claim, s)
    }

    @Test
    fun `H7 short of SOL - the faucet sheet over the claim screen, back to it`() {
        val claim = home().then(NavEvent.Open(Screen.Claim(A)))
        val s = claim.then(NavEvent.TxStarted(TxKind.Claim(A)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.NotEnoughSol)), NavEvent.OpenFaucet)
        assertEquals(Sheet.Faucet(null), s.sheet)
        assertEquals(claim, s.then(NavEvent.Back))
    }

    @Test
    fun `C-D14 submit deadline passed - What happens now opens H6 on Today`() {
        val s = running().then(NavEvent.TxStarted(TxKind.Submit(A, 6)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.DeadlinePassed)), NavEvent.WhatHappensNow)
        assertNull(s.tx)
        assertEquals(Tab.Today, s.tab)
        assertEquals(listOf<Screen>(Screen.Forfeit(A)), s.stack)
    }

    @Test
    fun `C-D14 submit declined - Back returns to Today with the day saved`() {
        val s = running().then(NavEvent.TxStarted(TxKind.Submit(A, 6)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Declined)), NavEvent.TxBack)
        assertNull(s.tx)
        assertEquals(Tab.Today, s.tab)
    }

    @Test
    fun `C-D14 submit short of SOL - the faucet sheet over Today`() {
        val s = running().then(NavEvent.TxStarted(TxKind.Submit(A, 6)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.NotEnoughSol)), NavEvent.OpenFaucet)
        assertNull(s.tx)
        assertEquals(Sheet.Faucet(null), s.sheet)
        assertEquals(Tab.Today, s.tab)
    }

    // ---- Me ------------------------------------------------------------------------------------

    @Test
    fun `N-91 W1 About - X1, back to W1`() {
        val me = home().then(NavEvent.SelectTab(Tab.Me))
        assertEquals(me, me.then(NavEvent.Open(Screen.About), NavEvent.Back))
    }

    @Test
    fun `W1 Disconnect from any screen - A1`() {
        val s = home().then(NavEvent.SelectTab(Tab.Me), NavEvent.Open(Screen.About), NavEvent.OpenFaucet, NavEvent.Disconnected)
        assertFalse(s.connected)
        assertFalse(s.showsBar)
    }

    @Test
    fun `a Holdout seen running then claimable, settling skipped, still leads to the claim`() {
        // On Today: its screen, then the claim above it.
        val fromToday = running().then(NavEvent.ChainMoved(mapOf(A to claimable())))
        assertEquals(Tab.Today, fromToday.tab)
        assertEquals(listOf(Screen.Detail(A), Screen.Claim(A)), fromToday.stack)
        // On its detail: the claim on top.
        val onDetail = running().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(A)), NavEvent.ChainMoved(mapOf(A to claimable())))
        assertEquals(Screen.Claim(A), onDetail.top)
    }

    @Test
    fun `a join that lands in a Holdout already under way goes to Today, not You're in`() {
        val inLate = joining().then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("sig"))))
        assertTrue(inLate.tx?.youreIn == true)
        val today = inLate.then(NavEvent.JoinedUnderWay)
        assertNull(today.tx)
        assertEquals(Tab.Today, today.tab)
        assertNull(today.top)
        assertEquals(home(), home().then(NavEvent.JoinedUnderWay), "nothing to leave without You're in")
    }

    @Test
    fun `a link to a tab root opens the root, a bar tap the tab as it was left`() {
        val roster = running().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Roster(A)), NavEvent.SelectTab(Tab.Today))
        assertEquals(Screen.Roster(A), roster.then(NavEvent.SelectTab(Tab.Holdouts)).top, "the bar keeps the stack")
        val linked = roster.then(NavEvent.GoToRoot(Tab.Holdouts))
        assertEquals(Tab.Holdouts, linked.tab)
        assertNull(linked.top, "a link to Holdouts promises its root")
    }

    // ---- the welcome, the six cards and the stake sheet's How it works (FR-067, FR-067a, FR-041, R25) ----

    @Test
    fun `a first connect opens the welcome, full screen with no bar, and Back does not leave it`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = false))
        assertEquals(Tour(firstRun = true, welcome = true), s.tour)
        assertTrue(s.tour!!.welcome)
        assertFalse(s.showsBar)
        assertFalse(s.canGoBack)
    }

    @Test
    fun `Start the brief leaves the welcome for card 1 of the first-run tour`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = false), NavEvent.StartBrief)
        assertEquals(Tour(firstRun = true, welcome = false, startCard = 1), s.tour)
        assertFalse(s.showsBar)
    }

    @Test
    fun `Skip on the welcome opens the disclosure card, the sixth`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = false), NavEvent.SkipBrief)
        assertEquals(Tour(firstRun = true, welcome = false, startCard = 6), s.tour)
        assertEquals(Tour.CARDS, s.tour!!.startCard)
    }

    @Test
    fun `finishing the tour after the welcome lands on Today with the bar`() {
        for (leave in listOf(NavEvent.StartBrief, NavEvent.SkipBrief)) {
            val s = NavState().then(NavEvent.Connected(tourSeen = false), leave, NavEvent.TourDone)
            assertNull(s.tour)
            assertEquals(Tab.Today, s.tab)
            assertTrue(s.showsBar)
        }
    }

    @Test
    fun `a wallet whose tour is seen goes straight to Today, with no welcome and no version raise (R25)`() {
        val s = NavState().then(NavEvent.Connected(tourSeen = true))
        assertNull(s.tour)
        assertTrue(s.showsBar)
        // The welcome's events change nothing when there is no welcome.
        assertEquals(s, s.then(NavEvent.StartBrief))
        assertEquals(s, s.then(NavEvent.SkipBrief))
    }

    @Test
    fun `the welcome belongs to first run - opening the tour from About or Me never shows it`() {
        val s = home().then(NavEvent.OpenTour())
        assertEquals(Tour(firstRun = false), s.tour)
        assertFalse(s.tour!!.welcome)
        assertEquals(s, s.then(NavEvent.StartBrief))
    }

    @Test
    fun `How it works on the stake sheet opens the tour at card 2 and finishing it reopens the sheet on the same tier`() {
        val stake = home().then(NavEvent.OpenStake(A, seekerKnown = true), NavEvent.StakeTier(2))
        val sheet = stake.sheet as Sheet.Stake
        val tour = stake.then(NavEvent.OpenTour(startCard = 2, returnTo = sheet))
        assertEquals(Tour(firstRun = false, startCard = 2, returnTo = sheet), tour.tour)
        assertNull(tour.sheet)
        assertFalse(tour.showsBar)
        val back = tour.then(NavEvent.TourDone)
        assertNull(back.tour)
        assertEquals(Sheet.Stake(A, 2, SeekerStatus.Confirmed), back.sheet)
        assertEquals(stake, back)
    }

    @Test
    fun `Back out of the tour opened from the stake sheet reopens the sheet on the same tier`() {
        val stake = home().then(NavEvent.OpenStake(A, seekerKnown = true), NavEvent.StakeTier(3))
        val tour = stake.then(NavEvent.OpenTour(startCard = 2, returnTo = stake.sheet as Sheet.Stake))
        assertTrue(tour.canGoBack)
        assertEquals(stake, tour.then(NavEvent.Back))
    }

    @Test
    fun `the tour opened from the stake sheet is not a first run, and a tab or the screen below stay as they were`() {
        val stake = home().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Detail(A)), NavEvent.OpenStake(B, seekerKnown = true))
        val tour = stake.then(NavEvent.OpenTour(startCard = 2, returnTo = stake.sheet as Sheet.Stake))
        assertFalse(tour.tour!!.firstRun)
        assertEquals(stake, tour.then(NavEvent.TourDone))
        assertEquals(Screen.Detail(A), tour.top)
    }

    private companion object {
        const val A = "CohortA"
        const val B = "CohortB"
        const val DAY = 20_400L

        fun home() = NavState().then(NavEvent.Connected(tourSeen = true))

        /** Today with a Holdout running: T2. */
        fun running() = home().then(NavEvent.ChainMoved(mapOf(A to Stage.Active(5, todayDone = false))))

        fun stakeSheet() = home().then(NavEvent.OpenStake(A, tier = 1, seekerKnown = true))

        fun joining() = stakeSheet().then(NavEvent.TxStarted(TxKind.Join(A, 1)))

        fun claimable() = Stage.ReadyToClaim(1_412_000_000uL, 1_250_000_000uL, 162_000_000uL)
    }
}
