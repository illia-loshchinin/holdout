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
        val all = TabBadges(today = true, holdouts = true, me = true)
        assertEquals("Today, today's reps are not done", all.accessibleName(Tab.Today))
        assertEquals("Holdouts, a claim is waiting", all.accessibleName(Tab.Holdouts))
        assertEquals("Me, SKR or SOL is too low", all.accessibleName(Tab.Me))
        val none = TabBadges()
        for (tab in Tab.entries) {
            assertFalse(none.dot(tab))
            assertEquals(tab.label, none.accessibleName(tab))
        }
    }

    @Test
    fun `three dots only - each tab's own, and nothing about people who dropped`() {
        assertEquals(3, Tab.entries.size)
        assertTrue(TabBadges(today = true).dot(Tab.Today))
        assertFalse(TabBadges(today = true).dot(Tab.Holdouts))
        assertFalse(TabBadges(today = true).dot(Tab.Me))
        assertTrue(TabBadges(holdouts = true).dot(Tab.Holdouts))
        assertTrue(TabBadges(me = true).dot(Tab.Me))
        val all = TabBadges(today = true, holdouts = true, me = true)
        for (tab in Tab.entries) {
            assertFalse(all.accessibleName(tab).contains("dropped", ignoreCase = true), tab.label)
        }
    }
}
