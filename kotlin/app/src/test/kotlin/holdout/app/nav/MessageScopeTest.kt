package holdout.app.nav

import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.core.Stage
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The view model's last action message belongs to the screen it was said on (ruling G7; the
 * "Day 3 is on chain." that showed on the claim screen): leaving that screen ends it.
 * [MessageScope.left] says when, from the navigation model alone.
 */
class MessageScopeTest {
    private fun home() = NavState().then(NavEvent.Connected(tourSeen = true))

    private fun running() = home().then(NavEvent.ChainMoved(mapOf(A to Stage.Active(2, todayDone = true))))

    private fun left(before: NavState, vararg events: NavEvent) = MessageScope.left(before, before.then(*events))

    @Test
    fun `a message said on Today is over when the member goes to another tab`() {
        assertTrue(left(running(), NavEvent.SelectTab(Tab.Standing)))
        assertTrue(left(running(), NavEvent.SelectTab(Tab.Holdouts)))
    }

    @Test
    fun `the day that landed on Today does not outlive the walk to the claim - Standing, then the claim screen`() {
        val today = running()
        val standing = today.then(NavEvent.ChainMoved(mapOf(A to Stage.Settling)))
        assertTrue(MessageScope.left(today, standing))
        val claim = standing.then(NavEvent.ChainMoved(mapOf(A to Stage.ReadyToClaim(1_412_000_000uL, 1_250_000_000uL, 162_000_000uL))))
        assertTrue(MessageScope.left(standing, claim))
    }

    @Test
    fun `a pushed screen and its back are other screens`() {
        val roster = running().then(NavEvent.SelectTab(Tab.Standing), NavEvent.Open(Screen.Roster(A)))
        assertTrue(MessageScope.left(running().then(NavEvent.SelectTab(Tab.Standing)), roster))
        assertTrue(left(roster, NavEvent.Back))
    }

    @Test
    fun `a sheet over a screen is another screen, and its close brings the first back`() {
        val holdouts = running().then(NavEvent.SelectTab(Tab.Holdouts))
        val stake = holdouts.then(NavEvent.OpenStake(A, seekerKnown = true))
        assertTrue(MessageScope.left(holdouts, stake))
        assertTrue(left(stake, NavEvent.CloseSheet))
    }

    @Test
    fun `a message said on the stake sheet survives what the sheet does to itself`() {
        val stake = home().then(NavEvent.OpenStake(A, seekerKnown = true))
        assertFalse(left(stake, NavEvent.StakeTier(2)))
        assertFalse(left(stake, NavEvent.SeekerChecked(holdout.app.stake.SeekerStatus.Confirmed)))
    }

    @Test
    fun `a message about a send stays through the signing flow that opens and closes over the same screen`() {
        val claim = running().then(NavEvent.ChainMoved(mapOf(A to Stage.Settling)), NavEvent.Open(Screen.Claim(A)))
        val signing = claim.then(NavEvent.TxStarted(TxKind.Claim(A)))
        assertFalse(MessageScope.left(claim, signing))
        val failed = signing.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Failed(mayStillLand = false, message = "No."))), NavEvent.TxBack)
        assertFalse(MessageScope.left(signing, failed))
        assertFalse(MessageScope.left(claim, failed))
    }

    @Test
    fun `a sitting over Today does not end what Today was told`() {
        assertFalse(left(running(), NavEvent.StartSitting(Sitting.HoldoutDay(A, 2))))
    }

    @Test
    fun `a lobby that says You left keeps its message until the member goes back`() {
        val lobby = home().then(NavEvent.ChainMoved(mapOf(A to Stage.Lobby)), NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Lobby(A)))
        assertFalse(left(lobby, NavEvent.ChainMoved(mapOf(A to Stage.NotJoined))))
        assertTrue(left(lobby.then(NavEvent.ChainMoved(mapOf(A to Stage.NotJoined))), NavEvent.Back))
    }

    @Test
    fun `an event that moves nothing leaves the message alone`() {
        assertFalse(left(running(), NavEvent.Back))
        assertFalse(left(running(), NavEvent.ChainMoved(mapOf(A to Stage.Active(2, todayDone = true)))))
    }

    private companion object {
        const val A = "CohortA"
    }
}
