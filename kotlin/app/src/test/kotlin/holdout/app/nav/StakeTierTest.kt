package holdout.app.nav

import holdout.app.stake.SeekerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The chip a member picks on the stake sheet follows the sheet in the nav
 * state, so "Get test tokens" and the way back to the stake sheet (ES-0a)
 * return it on the same chip.
 */
class StakeTierTest {
    private val cohort = "CohortA"

    private fun open() = NavState().then(NavEvent.Connected(tourSeen = true), NavEvent.OpenStake(cohort, seekerKnown = true))

    @Test
    fun `picking a chip moves the stake sheet's tier`() {
        val s = open().then(NavEvent.StakeTier(3))
        assertEquals(Sheet.Stake(cohort, 3, SeekerStatus.Confirmed), s.sheet)
    }

    @Test
    fun `the pick survives the faucet round trip, by the way back and by a close`() {
        val picked = open().then(NavEvent.StakeTier(2), NavEvent.OpenFaucet)
        assertEquals(Sheet.Stake(cohort, 2, SeekerStatus.Confirmed), (picked.sheet as Sheet.Faucet).returnTo)
        assertEquals(Sheet.Stake(cohort, 2, SeekerStatus.Confirmed), picked.then(NavEvent.CloseSheet).sheet)
        assertEquals(Sheet.Stake(cohort, 2, SeekerStatus.Confirmed), picked.then(NavEvent.Back).sheet)
    }

    @Test
    fun `the pick survives How it works, by finishing the tour and by Back`() {
        val picked = open().then(NavEvent.StakeTier(3))
        val tour = picked.then(NavEvent.OpenTour(startCard = 2, returnTo = picked.sheet as Sheet.Stake))
        assertNull(tour.sheet)
        assertEquals(Sheet.Stake(cohort, 3, SeekerStatus.Confirmed), tour.then(NavEvent.TourDone).sheet)
        assertEquals(Sheet.Stake(cohort, 3, SeekerStatus.Confirmed), tour.then(NavEvent.Back).sheet)
        assertNull(tour.then(NavEvent.TourDone).tour)
    }

    @Test
    fun `a chip pick while the tour is open does not open the sheet nor change the tier it returns to`() {
        val picked = open().then(NavEvent.StakeTier(1))
        val tour = picked.then(NavEvent.OpenTour(startCard = 2, returnTo = picked.sheet as Sheet.Stake))
        assertNull(tour.then(NavEvent.StakeTier(3)).sheet)
        assertEquals(Sheet.Stake(cohort, 1, SeekerStatus.Confirmed), tour.then(NavEvent.StakeTier(3), NavEvent.TourDone).sheet)
    }

    @Test
    fun `a pick with no stake sheet open changes nothing`() {
        val home = NavState().then(NavEvent.Connected(tourSeen = true))
        assertEquals(home, home.then(NavEvent.StakeTier(1)))
        assertNull(home.then(NavEvent.StakeTier(1)).sheet)
    }
}
