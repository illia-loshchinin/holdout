package holdout.app.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import holdout.app.nav.NavEvent
import holdout.app.nav.TxKind
import holdout.app.testutil.AnimationsOff
import holdout.app.tx.SigningScreen
import holdout.app.tx.SigningState
import holdout.app.tx.YoureInScreen
import holdout.app.tx.YoureInState
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The full-screen signing flow for join, submit and claim, and You're in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SigningShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val join = SigningState(TxKind.Join("c", 1), TxPhase.WaitingForWallet, "SPARK · 7-DAY · JOINING", amount = "1,250 SKR")
    private val submit = SigningState(TxKind.Submit("c", 11), TxPhase.WaitingForWallet, "HABIT · 21-DAY · DAY 12", timeLeft = "4h 06m")
    private val claim = SigningState(
        TxKind.Claim("c"),
        TxPhase.WaitingForWallet,
        "SPARK · 7-DAY · CLAIMING",
        amount = "1,412 SKR",
        breakdown = "1,250 SKR stake back + 162 SKR from the pool.",
        upsell = "Make it a Habit · 21-day" to NavEvent.CloseSheet,
    )

    private fun phase(name: String, base: SigningState, p: TxPhase) = compose.shoot("signing", name, heightDp = 844) {
        SigningScreen(base.copy(phase = p), onEvent = {})
    }

    private val landed = TxPhase.Done(TxOutcome.Landed("4Bx9pQ7Qk2abcdefgh"))
    private val declined = TxPhase.Done(TxOutcome.Declined)
    private val failed = TxPhase.Done(TxOutcome.Failed(mayStillLand = true, message = "timed out"))

    @Test
    fun joinWaiting() = phase("join-waiting", join, TxPhase.WaitingForWallet)

    @Test
    fun joinConfirming() = phase("join-confirming", join, TxPhase.Confirming)

    @Test
    fun joinDeclined() = phase("join-declined", join, declined)

    @Test
    fun joinFailed() = phase("join-failed", join, failed)

    @Test
    fun joinClosed() = phase("join-closed", join, TxPhase.Done(TxOutcome.JoiningClosed))

    @Test
    fun submitWaiting() = phase("submit-waiting", submit, TxPhase.WaitingForWallet)

    @Test
    fun submitConfirming() = phase("submit-confirming", submit, TxPhase.Confirming)

    @Test
    fun submitLanded() = phase("submit-landed", submit, landed)

    @Test
    fun submitDeclined() = phase("submit-declined", submit, declined)

    @Test
    fun submitFailed() = phase("submit-failed", submit, failed)

    @Test
    fun submitDeadlinePassed() = phase("submit-deadline-passed", submit, TxPhase.Done(TxOutcome.DeadlinePassed))

    @Test
    fun submitNoSol() = phase("submit-no-sol", submit, TxPhase.Done(TxOutcome.NotEnoughSol))

    @Test
    fun claimWaiting() = phase("claim-waiting", claim, TxPhase.WaitingForWallet)

    @Test
    fun claimConfirming() = phase("claim-confirming", claim, TxPhase.Confirming)

    @Test
    fun claimLanded() = phase("claim-landed", claim, landed)

    @Test
    fun claimDeclined() = phase("claim-declined", claim, declined)

    @Test
    fun claimFailed() = phase("claim-failed", claim, failed)

    private val youreIn = YoureInState("Spark · 7-day", "2d 4h", 15, "13,750 SKR", "1,250 SKR", "2d 3h", daily25Done = false)

    @Test
    fun youreIn() = compose.shoot("signing", "youre-in", heightDp = 844) { YoureInScreen(youreIn, {}, {}) }

    @Test
    fun youreInDailyDone() = compose.shoot("signing", "youre-in-daily-done", heightDp = 844) { YoureInScreen(youreIn.copy(daily25Done = true), {}, {}) }
}
