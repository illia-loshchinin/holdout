package holdout.app.nav

import holdout.app.wallet.TxPhase
import holdout.core.Leaderboard
import holdout.core.Stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the member is survives the process dying behind the app (wiring item B):
 * the tab and the screens pushed on each tab are kept as plain strings, a
 * Holdout by its base58 address. Sheets, the signing flow, a sitting and the
 * tour are never restored — each has its own way back (banked reps on disk,
 * the wallet's own screen, the first-run marker).
 */
class NavSavedTest {
    private val a = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"
    private val b = "DRpbCBMxVnDK7maPM5tGv6MvB3v1sRMC86PZ8okm21hy"

    private fun connected() = NavState().then(NavEvent.Connected(tourSeen = true))

    private fun roundTrip(s: NavState): NavState = NavSaved.decode(NavSaved.encode(s))

    @Test
    fun `the tab and every pushed screen come back`() {
        val s = connected().then(
            NavEvent.SelectTab(Tab.Holdouts),
            NavEvent.Open(Screen.Lobby(a)),
            NavEvent.Open(Screen.Roster(a)),
            NavEvent.Open(Screen.Detail(b)),
            NavEvent.SelectTab(Tab.Me),
            NavEvent.Open(Screen.Leaderboards(Leaderboard.Board.HARD_21D)),
            NavEvent.Open(Screen.About),
            NavEvent.SelectTab(Tab.Today),
            NavEvent.Open(Screen.Detail(a)),
            NavEvent.Open(Screen.Forfeit(a)),
            NavEvent.Open(Screen.Claim(b)),
            NavEvent.SelectTab(Tab.Holdouts),
        )
        val back = roundTrip(s)
        assertTrue(back.connected)
        assertEquals(Tab.Holdouts, back.tab)
        assertEquals(s.stacks, back.stacks)
    }

    @Test
    fun `every leaderboard comes back as itself`() {
        for (board in Leaderboard.Board.entries) {
            val s = connected().then(NavEvent.SelectTab(Tab.Me), NavEvent.Open(Screen.Leaderboards(board)))
            assertEquals(Screen.Leaderboards(board), roundTrip(s).top)
        }
    }

    @Test
    fun `a tab at its root comes back at its root`() {
        val back = roundTrip(connected().then(NavEvent.SelectTab(Tab.Me)))
        assertEquals(Tab.Me, back.tab)
        assertTrue(back.onRoot)
        assertTrue(back.showsBar)
    }

    @Test
    fun `a sheet, the signing flow, a sitting and the tour are not restored`() {
        val withSheet = connected().then(NavEvent.OpenStake(a))
        assertEquals(null, roundTrip(withSheet).sheet)
        val signing = connected().then(NavEvent.TxStarted(TxKind.Claim(a)), NavEvent.TxProgress(TxPhase.Confirming))
        assertEquals(null, roundTrip(signing).tx)
        val sitting = connected().then(NavEvent.StartSitting(Sitting.Daily25(5)))
        assertEquals(null, roundTrip(sitting).sitting)
        assertEquals(Tab.Today, roundTrip(sitting).tab)
        val tour = connected().then(NavEvent.OpenTour())
        assertEquals(null, roundTrip(tour).tour)
    }

    @Test
    fun `what the chain showed last is not kept, so the first read after a restart moves nobody`() {
        val s = connected().then(NavEvent.ChainMoved(mapOf(a to Stage.Lobby)))
        assertTrue(roundTrip(s).stages.isEmpty())
    }

    @Test
    fun `a disconnected or first-run state saves nothing and restores as disconnected`() {
        assertTrue(NavSaved.encode(NavState()).isEmpty())
        assertFalse(NavSaved.decode(emptyList()).connected)
        val firstRun = NavState().then(NavEvent.Connected(tourSeen = false))
        assertTrue(NavSaved.encode(firstRun).isEmpty())
        assertFalse(NavSaved.decode(NavSaved.encode(firstRun)).connected)
    }

    @Test
    fun `anything unreadable is dropped and the member lands on Today`() {
        val back = NavSaved.decode(listOf("v1", "tab|Nowhere", "stack|Today|Lobby", "stack|Elsewhere|About", "stack|Me|Mystery|x", "junk"))
        assertTrue(back.connected)
        assertEquals(Tab.Today, back.tab)
        assertTrue(back.stacks.isEmpty())
        assertFalse(NavSaved.decode(listOf("v9", "tab|Me")).connected, "an unknown version is not guessed at")
        assertFalse(NavSaved.decode(null).connected)
    }

    @Test
    fun `a v1 state saved on the retired Standing tab restores on Today, its stack dropped, the others kept`() {
        val saved = listOf(
            "v1",
            "tab|Standing",
            "stack|Holdouts|Lobby|$a",
            "stack|Standing|Detail|$b",
            "stack|Standing|Roster|$b",
            "stack|Me|About",
            "stack|Today|Forfeit|$a",
        )
        val back = NavSaved.decode(saved)
        assertTrue(back.connected)
        assertEquals(Tab.Today, back.tab)
        assertEquals(mapOf(Tab.Holdouts to listOf<Screen>(Screen.Lobby(a)), Tab.Me to listOf<Screen>(Screen.About), Tab.Today to listOf<Screen>(Screen.Forfeit(a))), back.stacks)
        assertEquals(Screen.Forfeit(a), back.top, "Today's own pushed screen is shown")
        // Standing at its root, no stack: Today's root.
        val root = NavSaved.decode(listOf("v1", "tab|Standing"))
        assertEquals(Tab.Today, root.tab)
        assertTrue(root.onRoot)
        // And re-saved, the state is a plain three-tab one in the same format.
        assertEquals("v1", NavSaved.encode(back).first())
        assertEquals(back.stacks, roundTrip(back).stacks)
    }

    @Test
    fun `the saved form is plain strings a bundle can hold`() {
        val s = connected().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Lobby(a)))
        assertEquals(listOf("v1", "tab|Holdouts", "stack|Holdouts|Lobby|$a"), NavSaved.encode(s))
    }

    @Test
    fun `a restored state is a working one - Back pops the pushed screen, then leaves for Today`() {
        val s = connected().then(NavEvent.SelectTab(Tab.Holdouts), NavEvent.Open(Screen.Lobby(a)))
        val back = roundTrip(s).then(NavEvent.Back)
        assertTrue(back.onRoot)
        assertEquals(Tab.Holdouts, back.tab)
        assertEquals(Tab.Today, back.then(NavEvent.Back).tab)
    }
}
