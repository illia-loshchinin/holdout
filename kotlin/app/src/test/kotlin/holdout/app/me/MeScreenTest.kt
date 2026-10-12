package holdout.app.me

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import holdout.app.sitting.PHONE
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.LocalSeekerNames
import holdout.app.ui.StreakState
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * W1 Me (board Me; FR-069d, rulings "Onboarding / Me"): who you are with the
 * address to copy, what you hold and the faucet, the daily streak with its
 * freezes, About, the tour again, Disconnect, and the meaning of the tab's dot.
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
    private var tours = 0
    private var disconnects = 0

    private fun show(state: MeState = funded, names: Map<String, String> = emptyMap()) {
        tokens = 0
        abouts = 0
        tours = 0
        disconnects = 0
        compose.setContent {
            HoldoutTheme {
                CompositionLocalProvider(LocalSeekerNames provides names) {
                    MeScreen(state, onGetTokens = { tokens++ }, onAbout = { abouts++ }, onTour = { tours++ }, onDisconnect = { disconnects++ })
                }
            }
        }
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
    fun `the daily streak carries its flame, its words and the freezes held`() {
        show(funded.copy(freezes = 2))

        compose.onNodeWithContentDescription("12-day streak").assertIsDisplayed()
        compose.onNodeWithText("day streak").assertIsDisplayed()
        compose.onNodeWithText("The Daily 25 · a forfeit never breaks it").assertIsDisplayed()
        compose.onNodeWithText("2 freezes held").assertIsDisplayed()
    }

    @Test
    fun `one freeze reads in the singular and none says nothing`() {
        show(funded.copy(freezes = 1))
        compose.onNodeWithText("1 freeze held").assertIsDisplayed()
    }

    @Test
    fun `no freezes held says nothing about freezes`() {
        show()
        compose.onAllNodes(hasText("freeze", substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    @Test
    fun `a broken streak is named broken`() {
        show(funded.copy(streak = 0, streakState = StreakState.Broken))
        compose.onNodeWithContentDescription("Streak broken").assertIsDisplayed()
    }

    @Test
    fun `what you hold is test SKR and devnet SOL with the faucet one tap away`() {
        show()

        compose.onNodeWithText("You hold", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Test SKR").assertIsDisplayed()
        compose.onNodeWithText("4,750 SKR").assertIsDisplayed()
        compose.onNodeWithText("Devnet SOL for fees").assertIsDisplayed()
        compose.onNodeWithText("0.012 SOL").assertIsDisplayed()
        compose.onNodeWithText("One tap: 5,000 test SKR, plus SOL for fees. Test network, test token.").assertIsDisplayed()
        compose.onNodeWithText("Get test tokens").performScrollTo().performClick()
        assertEquals(1, tokens)
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
    fun `about, the tour and disconnect each go where they say`() {
        show()

        compose.onNodeWithText("devnet · test token").assertIsDisplayed()
        compose.onNodeWithText("About Holdout").performScrollTo().performClick()
        compose.onNodeWithText("5 cards").assertIsDisplayed()
        compose.onNodeWithText("How Holdout works").performScrollTo().performClick()
        compose.onNodeWithText("Disconnect wallet").performClick()
        compose.onNodeWithText("Switched networks? Disconnect and connect again.").assertIsDisplayed()

        assertEquals(listOf(1, 1, 1), listOf(abouts, tours, disconnects))
    }
}
