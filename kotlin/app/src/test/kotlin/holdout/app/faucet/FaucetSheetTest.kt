package holdout.app.faucet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import holdout.app.sitting.PHONE
import holdout.app.testutil.AnimationsOff
import holdout.app.ui.HoldoutSheet
import holdout.app.ui.theme.HoldoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The faucet's sheet content (board FaucetSheet, DS Sheet; ES-0, ES-0a, ES-0b;
 * rulings G8): what you hold, one tap a day, the outcome in one line, the wait
 * on the wallet; no "← Back" inside the sheet, only the way back to the stake
 * sheet when it opened from there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class FaucetSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val now = 1_791_176_400L + 3 * 86_400L

    private var claims = 0
    private var backs = 0

    private fun show(
        claimedToday: Boolean = false,
        busy: Boolean = false,
        outcome: FaucetOutcome? = null,
        backLabel: String? = null,
    ) {
        claims = 0
        backs = 0
        compose.setContent {
            HoldoutTheme {
                FaucetSheet(
                    skr = 4_750_000_000uL,
                    lamports = 12_000_000L,
                    maxTopTier = 5_000_000_000uL,
                    claimedToday = claimedToday,
                    now = now,
                    busy = busy,
                    outcome = outcome,
                    onClaim = { claims++ },
                    onBack = { backs++ },
                    backLabel = backLabel,
                )
            }
        }
    }

    @Test
    fun `ready, the title, what you hold, the one-tap offer and the button`() {
        show()

        compose.onNodeWithText("Get test tokens").assertIsDisplayed()
        compose.onNodeWithText("Test SKR").assertIsDisplayed()
        compose.onNodeWithText("4,750 SKR").assertIsDisplayed()
        compose.onNodeWithText("Devnet SOL for fees").assertIsDisplayed()
        compose.onNodeWithText("0.012 SOL").assertIsDisplayed()
        compose.onNodeWithText("One tap: 5,000 test SKR, plus SOL for fees.").assertIsDisplayed()
        compose.onNodeWithText("Once a day · test tokens have no value").assertIsDisplayed()
        compose.onNodeWithText("Get them").assertIsEnabled().performClick()
        assertEquals(1, claims)
    }

    @Test
    fun `there is no back button inside the sheet`() {
        show()

        compose.onNodeWithText("← Back").assertDoesNotExist()
        compose.onNodeWithText("Back", substring = true).assertDoesNotExist()
    }

    @Test
    fun `waiting for the wallet disables the button and says so`() {
        show(busy = true)

        compose.onNodeWithText("Waiting for your wallet…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Get them").assertDoesNotExist()
    }

    @Test
    fun `funded says the tokens are in, and the button is spent`() {
        show(outcome = FaucetOutcome.Funded("sig"))

        compose.onNodeWithText("Done. The tokens are in your wallet.").assertIsDisplayed()
        compose.onNodeWithText("Get them").assertIsNotEnabled()
    }

    @Test
    fun `claimed today says when it is back`() {
        show(outcome = FaucetOutcome.UsedToday(returnsAt = now + 6 * 3_600 + 40 * 60))

        compose.onNodeWithText("Claimed today. Back in 6h 40m.").assertIsDisplayed()
        compose.onNodeWithText("Get them").assertIsNotEnabled()
    }

    @Test
    fun `an account that already claimed today says so before any tap`() {
        show(claimedToday = true)

        compose.onNodeWithText("Claimed today. Back in", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Get them").assertIsNotEnabled()
    }

    @Test
    fun `the faucet running dry never blames the member`() {
        show(outcome = FaucetOutcome.CannotPayOut)

        compose.onNodeWithText("The faucet can't pay out right now. Try again later.").assertIsDisplayed()
        compose.onNodeWithText("Get them").assertIsEnabled()
    }

    @Test
    fun `no wallet and a failed claim each get a line`() {
        show(outcome = FaucetOutcome.NoWallet)
        compose.onNodeWithText("No Solana wallet on this phone.").assertIsDisplayed()
    }

    @Test
    fun `a failed claim shows why`() {
        show(outcome = FaucetOutcome.Failed("devnet did not answer"))
        compose.onNodeWithText("devnet did not answer").assertIsDisplayed()
    }

    @Test
    fun `opened from the stake sheet it offers the way back, quietly until the tokens are in`() {
        show(backLabel = "Back to the stake sheet")

        compose.onNodeWithText("Back to the stake sheet").assertIsDisplayed().performClick()
        assertEquals(1, backs)
        compose.onNodeWithText("Get them").assertIsEnabled()
    }

    @Test
    fun `once the tokens are in, the way back to the stake sheet is the main button`() {
        show(outcome = FaucetOutcome.Funded("sig"), backLabel = "Back to the stake sheet")

        compose.onNodeWithText("Back to the stake sheet").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, backs)
        compose.onNodeWithText("Get them").assertDoesNotExist()
    }

    @Test
    fun `it sits in the sheet over the screen that opened it`() {
        compose.setContent {
            HoldoutTheme {
                Box(Modifier.fillMaxSize()) {
                    Text("Me root")
                    HoldoutSheet(onDismiss = {}) {
                        FaucetSheet(4_750_000_000uL, 12_000_000L, 5_000_000_000uL, claimedToday = false, now = now, busy = false, outcome = null, onClaim = {}, onBack = {})
                    }
                }
            }
        }

        compose.onNodeWithText("Me root").assertExists()
        compose.onNodeWithText("Get test tokens").assertIsDisplayed()
        compose.onNodeWithText("Get them").assertIsDisplayed()
    }
}
