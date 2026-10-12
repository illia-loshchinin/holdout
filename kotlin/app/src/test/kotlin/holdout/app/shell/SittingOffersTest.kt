package holdout.app.shell

import com.solana.publickey.SolanaPublicKey
import holdout.app.nav.Nav
import holdout.app.nav.NavEvent
import holdout.app.nav.NavState
import holdout.app.nav.Sheet
import holdout.app.nav.Sitting
import holdout.app.nav.then
import holdout.app.testutil.ChainFixtures.DAY
import holdout.app.testutil.ChainFixtures.START
import holdout.app.testutil.ChainFixtures.holdout
import holdout.app.testutil.ChainFixtures.me
import holdout.app.testutil.ChainFixtures.member
import holdout.app.testutil.ChainFixtures.view
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import holdout.core.ProfileSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a finished Daily 25 points on to (wiring item 4, rulings C-D8, G12): the
 * next Holdout to join, or a comeback in its place — and nothing while a
 * Holdout runs or sits in a lobby, or for a wallet known not to be a Seeker.
 */
class SittingOffersTest {
    private val now = START - 2 * DAY
    private val open = holdout(1, START)

    @Test
    fun `with no Holdout running or in a lobby the Daily 25 offers the next one with when joining closes`() {
        val offer = SittingOffers.of(view(open), now, seekerKnown = false, knownNotSeeker = false)!!
        assertEquals("Stake on yourself next", offer.offer.title)
        assertEquals("Spark · 7-day · joining closes in 1d 23h", offer.offer.subtitle)
        assertEquals(NavEvent.OpenStake(open.address.base58(), seekerKnown = false), offer.go)
    }

    @Test
    fun `a confirmed Seeker goes straight to the stake confirm`() {
        val offer = SittingOffers.of(view(open), now, seekerKnown = true, knownNotSeeker = false)!!
        assertEquals(NavEvent.OpenStake(open.address.base58(), seekerKnown = true), offer.go)
    }

    @Test
    fun `the Holdout closing soonest is the one offered`() {
        val later = holdout(2, START + 3 * DAY)
        val offer = SittingOffers.of(view(later, open), now, seekerKnown = false, knownNotSeeker = false)!!
        assertEquals(open.address.base58(), (offer.go as NavEvent.OpenStake).cohort)
    }

    @Test
    fun `a Holdout running with the member standing in it means no offer`() {
        val running = holdout(2, now - 2 * DAY, member = member(2))
        assertNull(SittingOffers.of(view(running, open), now, seekerKnown = false, knownNotSeeker = false))
    }

    @Test
    fun `a Holdout the member waits for in its lobby means no offer`() {
        val lobby = holdout(2, START + DAY, member = member(0))
        assertNull(SittingOffers.of(view(lobby, open), now, seekerKnown = false, knownNotSeeker = false))
    }

    @Test
    fun `a forfeited Holdout does not hold the offer back`() {
        val forfeited = holdout(2, now - 9 * DAY, member = member(3, MembershipSnapshot.State.Forfeited))
        assertNotNull(SittingOffers.of(view(forfeited, open), now, seekerKnown = false, knownNotSeeker = false))
    }

    @Test
    fun `a Holdout the program would refuse is not offered - a lobby place held that this view does not show`() {
        val elsewhere = ProfileSnapshot.Pending(SolanaPublicKey(ByteArray(32) { 9 }), START + 5 * DAY, START + 12 * DAY)
        val profile = ProfileSnapshot(me, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, null, elsewhere)
        assertNull(SittingOffers.of(view(open, profile = profile), now, seekerKnown = false, knownNotSeeker = false))
    }

    @Test
    fun `a wallet known not to hold a Seeker is never offered a stake it cannot make`() {
        assertNull(SittingOffers.of(view(open), now, seekerKnown = false, knownNotSeeker = true))
    }

    @Test
    fun `nothing is offered before the chain has answered, without a program, or with nothing open`() {
        assertNull(SittingOffers.of(null, now, seekerKnown = false, knownNotSeeker = false))
        assertNull(SittingOffers.of(view(open), null, seekerKnown = false, knownNotSeeker = false))
        assertNull(SittingOffers.of(view(open, cfg = null), now, seekerKnown = false, knownNotSeeker = false))
        assertNull(SittingOffers.of(view(holdout(2, now - 3 * DAY)), now, seekerKnown = false, knownNotSeeker = false))
    }

    @Test
    fun `a comeback offer takes the place of the upsell`() {
        // A 7-day Spark whose day 6 was missed, and the next round still open to join.
        val missed = holdout(2, now - 6 * DAY, member = member(5), cutoff = now - 6 * DAY - 3_600)
        val round = holdout(3, now + DAY)
        val offer = SittingOffers.of(view(missed, round), now, seekerKnown = true, knownNotSeeker = false)!!
        assertEquals("Get your stake back", offer.offer.title)
        assertTrue(offer.offer.subtitle.startsWith("Get back 125 SKR of your lost stake"), offer.offer.subtitle)
        assertEquals(NavEvent.OpenStake(round.address.base58(), 1, seekerKnown = true), offer.go)
    }

    @Test
    fun `tapping the offer ends the sitting, then the stake sheet opens over Today`() {
        val inSitting = NavState().then(NavEvent.Connected(tourSeen = true), NavEvent.StartSitting(Sitting.Daily25(100)))
        // Inside the sitting the sheet would be refused: the host ends the sitting first.
        assertEquals(inSitting, Nav.reduce(inSitting, NavEvent.OpenStake(open.address.base58())))
        val offer = SittingOffers.of(view(open), now, seekerKnown = false, knownNotSeeker = false)!!
        val after = inSitting.then(NavEvent.SittingEnded, offer.go)
        assertNull(after.sitting)
        assertIs<Sheet.Stake>(after.sheet)
        assertEquals(open.address.base58(), (after.sheet as Sheet.Stake).cohort)
    }

    @Test
    fun `a test Holdout is offered under its own name`() {
        val test = holdout(2, now + 750, leagueId = LeagueParams.TEST_LEAGUE_MIN, days = 3, reps = 5, dayLength = 600, cutoff = now + 750)
        val offer = SittingOffers.of(view(test), now, seekerKnown = false, knownNotSeeker = false)!!
        assertEquals("Hackathon Holdout · starts in 12m 30s", offer.offer.subtitle)
    }
}
