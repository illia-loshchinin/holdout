package holdout.app.nav

import holdout.core.Stage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The bar's dots (DS BottomNav, screen map §3): each a pure function, its meaning in the tab's accessible name. */
class BadgesTest {
    @Test
    fun `Today - a dot while the Daily 25 is not done, none once it is`() {
        assertTrue(Badges.today(running = null, holdoutDayDoneOnPhone = false, daily25Done = false))
        assertFalse(Badges.today(running = null, holdoutDayDoneOnPhone = false, daily25Done = true))
    }

    @Test
    fun `Today in a Holdout - the Holdout's day decides, not the Daily 25`() {
        assertTrue(Badges.today(Stage.Active(3, todayDone = false), holdoutDayDoneOnPhone = false, daily25Done = true))
        assertFalse(Badges.today(Stage.Active(3, todayDone = true), holdoutDayDoneOnPhone = false, daily25Done = false))
        // Done on this phone, waiting to be sent: the reps are done.
        assertFalse(Badges.today(Stage.Active(3, todayDone = false), holdoutDayDoneOnPhone = true, daily25Done = false))
    }

    @Test
    fun `Standing - dropped since the member last looked`() {
        assertEquals(3, Badges.droppedSinceLooked(standingNow = 186, lastLooked = 189, startedWith = 312))
        assertEquals(0, Badges.droppedSinceLooked(standingNow = 189, lastLooked = 189, startedWith = 312))
    }

    @Test
    fun `Standing - never looked, compared with how many started`() {
        assertEquals(2, Badges.droppedSinceLooked(standingNow = 12, lastLooked = null, startedWith = 14))
        assertEquals(0, Badges.droppedSinceLooked(standingNow = 14, lastLooked = null, startedWith = 14))
    }

    @Test
    fun `Standing - no dot before the roster is read, nor for a count that went up`() {
        assertEquals(0, Badges.droppedSinceLooked(standingNow = null, lastLooked = 10, startedWith = 14))
        assertEquals(0, Badges.droppedSinceLooked(standingNow = 12, lastLooked = 10, startedWith = 14))
    }

    @Test
    fun `Holdouts - a dot when a claim is waiting`() {
        assertTrue(Badges.holdouts(listOf(Stage.Lobby, Stage.ReadyToClaim(5uL, 4uL, 1uL))))
        assertFalse(Badges.holdouts(listOf(Stage.Lobby, Stage.Settling, Stage.Claimed(5uL))))
        assertFalse(Badges.holdouts(emptyList()))
    }

    @Test
    fun `Me - a dot when SOL is too low to submit or SKR too low for the cheapest open chip`() {
        val enoughSol = 10_000_000L
        assertFalse(Badges.me(skr = 2_000uL, lamports = enoughSol, cheapestOpenChip = 1_000uL))
        assertTrue(Badges.me(skr = 2_000uL, lamports = 1_000L, cheapestOpenChip = 1_000uL))
        assertTrue(Badges.me(skr = 500uL, lamports = enoughSol, cheapestOpenChip = 1_000uL))
        // Nothing open to join: SKR cannot be too low to stake.
        assertFalse(Badges.me(skr = 0uL, lamports = enoughSol, cheapestOpenChip = null))
    }

    @Test
    fun `each dot's meaning is in the tab's accessible name, and a tab with no dot is just its name`() {
        val all = TabBadges(today = true, standingDropped = 3, holdouts = true, me = true)
        assertEquals("Today, today's reps aren't done", all.accessibleName(Tab.Today))
        assertEquals("Standing, 3 dropped since you last looked", all.accessibleName(Tab.Standing))
        assertEquals("Holdouts, a claim is waiting", all.accessibleName(Tab.Holdouts))
        assertEquals("Me, low on SKR or SOL", all.accessibleName(Tab.Me))
        assertEquals("Standing, 1 dropped since you last looked", all.copy(standingDropped = 1).accessibleName(Tab.Standing))
        val none = TabBadges()
        for (tab in Tab.entries) {
            assertFalse(none.dot(tab))
            assertEquals(tab.label, none.accessibleName(tab))
        }
    }
}
