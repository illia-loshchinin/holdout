package holdout.app.me

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import holdout.app.sitting.PHONE
import holdout.app.streak.ToesMeter
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.LocalSeekerNames
import holdout.app.ui.StreakState
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * W1 Me (board Me; FR-069d, FR-052a, FR-049, rulings "Onboarding / Me"): who you are with the
 * address to copy, the Daily 25's statistics (streak, longest, freezes, toes meter), Leaderboards with the
 * best rank, what you hold and the faucet, About, Disconnect, and the meaning of the tab's dot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class MeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val wallet = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

    private val funded = MeState(wallet, skr = 4_750_000_000uL, lamports = 12_000_000L, faucetSkr = 5_000_000_000uL, streak = 12)

    private var tokens = 0
    private var abouts = 0
    private var boards = 0
    private var disconnects = 0

    private fun show(state: MeState = funded, names: Map<String, String> = emptyMap()) {
        tokens = 0
        abouts = 0
        boards = 0
        disconnects = 0
        compose.setContent {
            HoldoutTheme {
                CompositionLocalProvider(LocalSeekerNames provides names) {
                    MeScreen(state, onGetTokens = { tokens++ }, onAbout = { abouts++ }, onDisconnect = { disconnects++ }, onLeaderboards = { boards++ })
                }
            }
        }
    }

    private fun colourOf(text: String): Color {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(result)
        return result.first().layoutInput.style.color
    }

    @Test
    fun `identity is the Seeker ID under the title, the short address beside a copy control and the network`() {
        show(names = mapOf(wallet to "nova.skr"))

        compose.onNodeWithText("Me").assertIsDisplayed()
        compose.onNodeWithText("nova.skr").assertIsDisplayed()
        compose.onNodeWithText("7xKX…gAsU").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy wallet address").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: devnet").assertIsDisplayed()
    }

    @Test
    fun `a wallet with no Seeker ID shows only its address`() {
        show()

        compose.onNodeWithText("7xKX…gAsU").assertIsDisplayed()
        compose.onNodeWithText("nova.skr").assertDoesNotExist()
    }

    @Test
    fun `tapping the address copies the whole wallet and says Copied for a moment`() {
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithContentDescription("Copy wallet address").performClick()
        compose.mainClock.advanceTimeByFrame()

        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(wallet, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        compose.onNodeWithText("Copied").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("7xKX…gAsU").assertIsDisplayed()
        compose.onNodeWithText("Copied").assertDoesNotExist()
    }

    @Test
    fun `the Daily 25 card carries the streak, the longest, the freezes and when the next comes`() {
        show(funded.copy(streak = 12, longest = 21, freezes = 1))

        compose.onNodeWithText("THE DAILY 25").assertIsDisplayed()
        compose.onNodeWithContentDescription("12-day streak").assertIsDisplayed()
        compose.onNodeWithText("day streak").assertIsDisplayed()
        compose.onNodeWithText("Longest 21 days").assertIsDisplayed()
        compose.onNodeWithText("Freezes 1 of 2").assertIsDisplayed()
        // 12 days in: the streak reaches 14 in 2 more days, which earns the next freeze.
        compose.onNodeWithText("Next freeze in 2 days").assertIsDisplayed()
    }

    @Test
    fun `with both freezes held there is no next freeze to wait for`() {
        show(funded.copy(streak = 14, longest = 14, freezes = 2))

        compose.onNodeWithText("Freezes 2 of 2").assertIsDisplayed()
        compose.onAllNodes(hasText("Next freeze", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `no freezes held yet counts the whole way to the first`() {
        show(funded.copy(streak = 0, longest = 0, freezes = 0))

        compose.onNodeWithText("Freezes 0 of 2").assertIsDisplayed()
        compose.onNodeWithText("Next freeze in 7 days").assertIsDisplayed()
        compose.onNodeWithText("Longest 0 days").assertIsDisplayed()
    }

    @Test
    fun `a single day reads in the singular`() {
        show(funded.copy(streak = 6, longest = 1, freezes = 0))

        compose.onNodeWithText("Longest 1 day").assertIsDisplayed()
        compose.onNodeWithText("Next freeze in 1 day").assertIsDisplayed()
    }

    @Test
    fun `the next freeze is worked out from the streak and the freezes held`() {
        assertEquals(2, MeState(wallet, null, null, null, streak = 12, freezes = 1).nextFreezeInDays)
        assertEquals(7, MeState(wallet, null, null, null, streak = 14, freezes = 0).nextFreezeInDays)
        assertEquals(7, MeState(wallet, null, null, null, streak = 0, freezes = 0).nextFreezeInDays)
        assertEquals(1, MeState(wallet, null, null, null, streak = 13, freezes = 0).nextFreezeInDays)
        assertEquals(null, MeState(wallet, null, null, null, streak = 3, freezes = 2).nextFreezeInDays)
    }

    @Test
    fun `the toes meter moved here from Today - today's toes, seven bars and the best day`() {
        val week = listOf(0, 10, 25, 25, 0, 18, 18)
        show(funded.copy(toes = ToesMeter.Meter(toes = 18, measured = 25, week = week, best = 25)))

        compose.onNodeWithText("18 of 25 from your toes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Best: 25 from your toes").assertIsDisplayed()
    }

    @Test
    fun `no toes reading yet tells how to see one`() {
        show(funded.copy(toes = ToesMeter.Meter(toes = 0, measured = 0, week = List(7) { 0 }, best = 0)))
        compose.onNodeWithText("Do it with the phone at 45° to see how many come from your toes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Leaderboards is a row that shows the member's best rank and opens the boards`() {
        show(funded.copy(leaderboardMeta = "#4 in Spark · 7-day"))

        compose.onNodeWithText("#4 in Spark · 7-day").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Leaderboards").performScrollTo().performClick()
        assertEquals(1, boards)
    }

    @Test
    fun `no rank, no meta - the row is just Leaderboards`() {
        show()

        compose.onNodeWithText("Leaderboards").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("#", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `a broken streak is named broken`() {
        show(funded.copy(streak = 0, streakState = StreakState.Broken))
        compose.onNodeWithContentDescription("Streak broken").assertIsDisplayed()
    }

    @Test
    fun `what you hold is test SKR and devnet SOL with the faucet one tap away`() {
        show()

        compose.onNodeWithText("Wallet", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Test SKR").assertIsDisplayed()
        compose.onNodeWithText("4,750 SKR").assertIsDisplayed()
        compose.onNodeWithText("Devnet SOL for fees").assertIsDisplayed()
        compose.onNodeWithText("0.012 SOL").assertIsDisplayed()
        compose.onNodeWithText("One tap: 5,000 test SKR, plus SOL for fees. Test network, test token.").assertIsDisplayed()
    }

    @Test
    fun `with enough funds Get test tokens is a quiet row with what a tap gives, not the white button`() {
        show()

        compose.onNodeWithText("5,000 SKR + SOL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Get test tokens").performScrollTo().performClick()
        assertEquals(1, tokens)
        // The white pill sets its label in OnLight; a row sets it in the primary text colour.
        assertEquals(HoldoutColors.TextPrimary, colourOf("Get test tokens"))
    }

    @Test
    fun `short of SKR or SOL, Get test tokens becomes the one white button`() {
        show(funded.copy(lowOnFunds = true))

        compose.onNodeWithText("Get test tokens").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, tokens)
        assertEquals(HoldoutColors.OnLight, colourOf("Get test tokens"))
        compose.onNodeWithText("5,000 SKR + SOL").assertDoesNotExist()
        compose.onAllNodes(hasText("Get test tokens")).assertCountEquals(1)
    }

    @Test
    fun `before the chain is read the balances say so instead of showing zeros`() {
        show(funded.copy(skr = null, lamports = null, faucetSkr = null))

        compose.onNodeWithText("Reading devnet…").assertIsDisplayed()
        compose.onNodeWithText("0 SKR").assertDoesNotExist()
    }

    @Test
    fun `too little SKR or SOL is said as a line, the meaning of the tab's dot`() {
        show(funded.copy(lowOnFunds = true))
        compose.onNodeWithText("Too little SKR or SOL to stake or submit. Get test tokens first.").assertIsDisplayed()
    }

    @Test
    fun `enough funds say nothing about the dot`() {
        show()
        compose.onAllNodes(hasText("Too little", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `one About Holdout row says how it works, there is no tour row, and Disconnect stays`() {
        show()

        compose.onNodeWithText("how it works").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("About Holdout").performScrollTo().performClick()
        compose.onNodeWithText("How Holdout works", substring = true).assertDoesNotExist()
        compose.onNodeWithText("5 cards", substring = true).assertDoesNotExist()
        compose.onNodeWithText("6 cards", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Disconnect wallet").performClick()
        compose.onNodeWithText("Switched networks? Disconnect and connect again.").assertIsDisplayed()

        assertEquals(listOf(1, 1), listOf(abouts, disconnects))
    }
}
