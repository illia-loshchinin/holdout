package holdout.app.tx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.chain.ChainView
import holdout.app.nav.NavEvent
import holdout.app.nav.TxKind
import holdout.app.shell.claimUpsell
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.core.LeagueParams
import holdout.core.MembershipSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The claim's result in Holdout's own signing flow (FR-070, FR-070a; board 7b): the amount once, Done, and the next step up. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class ClaimResultTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<NavEvent>()

    private fun landed(upsell: Pair<String, NavEvent>?) = SigningState(
        TxKind.Claim("c"),
        TxPhase.Done(TxOutcome.Landed("4Bx9pQ7Qk2abcdef7Qk2")),
        "Spark · 7-day · claiming",
        amount = "1,412 SKR",
        breakdown = "1,250 SKR stake back + 162 SKR from the pool.",
        upsell = upsell,
    )

    @Test
    fun `a claim that landed shows the amount, what it is made of and the explorer link`() {
        compose.setContent { HoldoutTheme { SigningScreen(landed(null), { events += it }) } }

        compose.onNodeWithText("CLAIMED").assertIsDisplayed()
        compose.onNodeWithText("1,412 SKR").assertIsDisplayed()
        compose.onNodeWithText("1,250 SKR stake back + 162 SKR from the pool.").assertIsDisplayed()
        compose.onNodeWithText("4Bx9…7Qk2 · view on explorer →").assertIsDisplayed()
    }

    @Test
    fun `after a Spark - Make it a Habit, with Done beside it`() {
        compose.setContent { HoldoutTheme { SigningScreen(landed("Make it a Habit · 21-day" to NavEvent.OpenStake("h")), { events += it }) } }

        compose.onNodeWithText("Make it a Habit · 21-day").performClick()
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf(NavEvent.OpenStake("h"), NavEvent.TxDone), events)
    }

    @Test
    fun `with no Habit to offer - Done is the one button`() {
        compose.setContent { HoldoutTheme { SigningScreen(landed(null), { events += it }) } }

        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf<NavEvent>(NavEvent.TxDone), events)
    }

    // ---- the upsell itself: the length comes from the open Habit -------------------------------------

    private val now = Fx.START
    private val spark = Fx.holdout(Fx.cohort(days = 7, start = Fx.START - 8 * Fx.DAY, settled = true), Fx.member(days = 7, state = MembershipSnapshot.State.Claimed))

    private fun open(days: Int, n: Int = 5) = Fx.holdout(Fx.cohort(days = days, start = Fx.START + 2 * Fx.DAY, joined = 4).copy(index = n.toLong()), null)

    private fun view(vararg h: holdout.app.chain.Holdout) = ChainView(null, h.toList(), null, 0uL, 0L)

    @Test
    fun `upsell - a Spark claim offers the open Habit by its own length`() {
        val habit = open(21)
        val up = claimUpsell(spark, view(spark, habit), now, seekerKnown = true)

        assertEquals("Make it a Habit · 21-day", up?.first)
        assertEquals(NavEvent.OpenStake(habit.address.base58(), seekerKnown = true), up?.second)
    }

    @Test
    fun `upsell - the length is read from the open Habit, not typed here`() {
        val up = claimUpsell(spark, view(spark, open(28)), now, seekerKnown = false)
        assertEquals("Make it a Habit · 28-day", up?.first)
    }

    @Test
    fun `upsell - a Habit claim offers the next Habit`() {
        val habitDone = Fx.holdout(Fx.cohort(days = 21, start = Fx.START - 22 * Fx.DAY, settled = true), Fx.member(days = 21, state = MembershipSnapshot.State.Claimed))
        val up = claimUpsell(habitDone, view(habitDone, open(21)), now, seekerKnown = true)
        assertEquals("Join the next Habit · 21-day", up?.first)
    }

    private fun testHoldout(form: Int) = Fx.holdout(Fx.cohort(days = 3, leagueId = 100, dayLength = 600, form = form, settled = true), Fx.member(state = MembershipSnapshot.State.Claimed))

    @Test
    fun `upsell - none when no Habit is open`() {
        assertNull(claimUpsell(spark, view(spark), now, seekerKnown = true))
        assertNull(claimUpsell(testHoldout(form = 1), view(testHoldout(form = 1)), now, seekerKnown = true))
    }

    @Test
    fun `upsell - a claim after a short test Spark offers the open Habit like any Spark`() {
        val test = testHoldout(form = LeagueParams.FORM_KNEES_ALLOWED)
        val habit = open(21)
        val up = claimUpsell(test, view(test, habit), now, seekerKnown = true)

        assertEquals("Make it a Habit · 21-day", up?.first)
        assertEquals(NavEvent.OpenStake(habit.address.base58(), seekerKnown = true), up?.second)
    }

    @Test
    fun `upsell - a claim after a short test Holdout of the toes form offers the next Habit`() {
        val test = testHoldout(form = LeagueParams.FORM_TOES)
        val up = claimUpsell(test, view(test, open(21)), now, seekerKnown = false)

        assertEquals("Join the next Habit · 21-day", up?.first)
    }

    @Test
    fun `upsell - never offers a short test Holdout, whatever was claimed`() {
        val openTest = Fx.holdout(Fx.cohort(days = 3, leagueId = 100, dayLength = 600, form = LeagueParams.FORM_TOES, start = Fx.START + 2 * Fx.DAY, joined = 4), null)
        assertNull(claimUpsell(spark, view(spark, openTest), now, seekerKnown = true))
        val test = testHoldout(form = LeagueParams.FORM_KNEES_ALLOWED)
        assertNull(claimUpsell(test, view(test, openTest), now, seekerKnown = true))
    }
}
