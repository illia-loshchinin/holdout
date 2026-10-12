package holdout.app.claim

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.chain.Holdout
import holdout.app.testing.Fx
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Stage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H7, the claim (board 7): the opener by level, the amount, the rows, the dropped-out line, short of SOL. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class ClaimScreenTest {
    @get:Rule
    val animationsOff = AnimationsOff()

    @get:Rule
    val compose = createComposeRule()

    // 20 started a 7-day Spark, 11 finished and split the pool.
    private val spark = Fx.cohort(days = 7, joined = 20, start = Fx.START - 8 * Fx.DAY, settled = true, finishers = 11, share = 162_000_000uL)
    private val habit = Fx.cohort(days = 21, joined = 12, start = Fx.START - 22 * Fx.DAY, settled = true, finishers = 12, share = 0uL)
    private val ready = Stage.ReadyToClaim(amount = 1_412_000_000uL, stake = 1_250_000_000uL, share = 162_000_000uL)

    private class Taps {
        var claim = 0
        var back = 0
        var tokens = 0
    }

    private fun show(
        c: CohortSnapshot = spark,
        stage: Stage = ready,
        taps: Taps = Taps(),
        shortOfSol: Boolean = false,
        busy: Boolean = false,
        signature: String? = null,
        message: String? = null,
    ) {
        val h = Holdout(Fx.holdout(c).address, c, null, Fx.member(days = c.durationDays, state = MembershipSnapshot.State.Finished))
        compose.setContent {
            HoldoutTheme {
                ClaimScreen(h, stage, busy, message, messageIsWarning = true, lastClaimSignature = signature, onClaim = { taps.claim++ }, onBack = { taps.back++ }, shortOfSol = shortOfSol, onGetTokens = { taps.tokens++ })
            }
        }
    }

    @Test
    fun `a Spark - lit, the amount once as the counter, and what it is to claim`() {
        show()

        compose.onNodeWithText("SPARK · 7-DAY · FINISHED").assertIsDisplayed()
        compose.onNodeWithText("SETTLED").assertIsDisplayed()
        compose.onNodeWithText("Spark lit: you held out 7 days.").assertIsDisplayed()
        compose.onNodeWithText("1,412").assertIsDisplayed()
        compose.onNodeWithText("SKR · YOURS TO CLAIM").assertIsDisplayed()
    }

    @Test
    fun `a Habit - you held out 21 days, with no Spark in it`() {
        show(c = habit, stage = Stage.ReadyToClaim(1_300_000_000uL, 1_250_000_000uL, 50_000_000uL))

        compose.onNodeWithText("You held out 21 days.").assertIsDisplayed()
        compose.onNodeWithText("Spark lit", substring = true).assertDoesNotExist()
        compose.onNodeWithText("HABIT · 21-DAY · FINISHED").assertIsDisplayed()
    }

    @Test
    fun `the rows - stake back, share of the prize pool, and the dropped-out line`() {
        show()

        compose.onNodeWithText("Stake back").assertIsDisplayed()
        compose.onNodeWithText("1,250 SKR").assertIsDisplayed()
        compose.onNodeWithText("Share of the prize pool").assertIsDisplayed()
        compose.onNodeWithText("162 SKR").assertIsDisplayed()
        compose.onNodeWithText("9 of 20 dropped out. The 11 who finished split the pool and its boost.").assertIsDisplayed()
        compose.onNodeWithText("Comeback").assertDoesNotExist()
    }

    @Test
    fun `a comeback is its own row with its note, and the button claims the sum`() {
        show(stage = Stage.ReadyToClaim(1_662_000_000uL, 1_250_000_000uL, 162_000_000uL, comeback = 250_000_000uL))

        compose.onNodeWithText("Comeback").assertIsDisplayed()
        compose.onNodeWithText("250 SKR").assertIsDisplayed()
        compose.onNodeWithText("part of your last lost stake, back").assertIsDisplayed()
        compose.onNodeWithText("Claim 1,662 SKR").assertIsDisplayed()
    }

    @Test
    fun `nobody dropped out - you get exactly your stake back, with no share row`() {
        show(c = habit, stage = Stage.ReadyToClaim(1_250_000_000uL, 1_250_000_000uL, 0uL))

        compose.onNodeWithText("Nobody dropped out: you get exactly your stake back.").assertIsDisplayed()
        compose.onNodeWithText("Share of the prize pool").assertDoesNotExist()
    }

    @Test
    fun `the claim button signs once and the footer says the claim waits`() {
        val taps = Taps()
        show(taps = taps)

        compose.onNodeWithText("One signature. It waits here until you claim.").assertIsDisplayed()
        compose.onNodeWithText("Claim 1,412 SKR").assertIsEnabled().performClick()

        assertEquals(1, taps.claim)
    }

    @Test
    fun `waiting for the wallet - the button says so and cannot be pressed again`() {
        show(busy = true)
        compose.onNodeWithText("Waiting for your wallet…").assertIsNotEnabled()
    }

    @Test
    fun `short of SOL - the fee is explained, the faucet comes first and the claim waits`() {
        val taps = Taps()
        show(taps = taps, shortOfSol = true)

        compose.onNodeWithText("Not enough devnet SOL for the fee. Test tokens include some.").assertIsDisplayed()
        compose.onNodeWithText("Claim 1,412 SKR").assertIsNotEnabled()
        compose.onNodeWithText("Get test tokens").performClick()

        assertEquals(1, taps.tokens)
        assertEquals(0, taps.claim)
    }

    @Test
    fun `back goes to Holdouts`() {
        val taps = Taps()
        show(taps = taps)

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, taps.back)
    }

    @Test
    fun `no settled date or time, and no dollar figure`() {
        show(stage = Stage.ReadyToClaim(1_662_000_000uL, 1_250_000_000uL, 162_000_000uL, comeback = 250_000_000uL))

        compose.onNodeWithText("Settled", substring = true, ignoreCase = true).assertExists() // the chip, nothing more
        compose.onNodeWithText("UTC", substring = true).assertDoesNotExist()
        compose.onNodeWithText("$", substring = true).assertDoesNotExist()
        compose.onNodeWithText("≈", substring = true).assertDoesNotExist()
    }

    @Test
    fun `already claimed - the amount in success, the rows, and the explorer link`() {
        show(stage = Stage.Claimed(1_412_000_000uL, 1_250_000_000uL, 162_000_000uL), signature = "4Bx9pQ7Qk2abcdef7Qk2")

        compose.onNodeWithText("SKR · CLAIMED").assertIsDisplayed()
        compose.onNodeWithText("Stake back").assertIsDisplayed()
        compose.onNodeWithText("4Bx9…7Qk2 · view on explorer →").assertIsDisplayed()
        compose.onNodeWithText("Claim 1,412 SKR").assertDoesNotExist()
    }

    @Test
    fun `an error from the last try is shown above the button`() {
        show(message = "Nothing to claim here.")
        compose.onNodeWithText("Nothing to claim here.").assertIsDisplayed()
    }

    // ---- ClaimText (pure) -------------------------------------------------------------------------

    @Test
    fun `text - the dropped-out line from the settled Holdout alone`() {
        assertEquals("9 of 20 dropped out. The 11 who finished split the pool and its boost.", ClaimText.droppedLine(spark))
        assertEquals("Nobody dropped out: you get exactly your stake back.", ClaimText.droppedLine(habit))
        assertEquals("19 of 20 dropped out. The 1 who finished takes the pool and its boost.", ClaimText.droppedLine(Fx.cohort(joined = 20, finishers = 1, settled = true)))
        assertEquals("1 of 12 dropped out. The 11 who finished split the pool and its boost.", ClaimText.droppedLine(Fx.cohort(joined = 12, finishers = 11, settled = true)))
        assertNull(ClaimText.droppedLine(Fx.cohort(joined = 12, finishers = 0, settled = true)))
    }

    @Test
    fun `text - the opener by level and length`() {
        assertEquals("Spark lit: you held out 7 days.", ClaimText.opener(spark))
        assertEquals("You held out 21 days.", ClaimText.opener(habit))
        assertEquals("You held out 1 day.", ClaimText.opener(Fx.cohort(days = 1, form = 0)))
    }

    @Test
    fun `text - the amount without its unit, the button with it`() {
        assertEquals("1,412", ClaimText.amountNumber(1_412_000_000uL))
        assertEquals("12.5", ClaimText.amountNumber(12_500_000uL))
        assertEquals("Claim 1,412 SKR", ClaimText.button(1_412_000_000uL))
    }

    @Test
    fun `text - nothing says earn, a date or a dollar`() {
        val words = listOf(ClaimText.FOOTER, ClaimText.NO_SOL, ClaimText.NOBODY_DROPPED, ClaimText.AMOUNT_CAPTION, ClaimText.CLAIMED_CAPTION, ClaimText.opener(spark), ClaimText.droppedLine(spark)!!)
        words.forEach { w ->
            assertFalse(Regex("""earn|\$|≈|UTC|\d{1,2}:\d{2}""", RegexOption.IGNORE_CASE).containsMatchIn(w), "\"$w\"")
            assertTrue(w.isNotBlank())
        }
    }
}
