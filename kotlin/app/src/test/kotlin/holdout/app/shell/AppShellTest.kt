package holdout.app.shell

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import holdout.app.holdouts.FinishedHoldout
import holdout.app.holdouts.MyHoldoutsScreen
import holdout.app.holdouts.MyHoldoutsState
import holdout.app.holdouts.OpenHoldout
import holdout.app.holdouts.RunningHoldout
import holdout.app.holdouts.YourHoldout
import holdout.app.holdouts.YourKind
import holdout.app.nav.Nav
import holdout.app.nav.NavEvent
import holdout.app.nav.NavState
import holdout.app.nav.Screen
import holdout.app.nav.Sheet
import holdout.app.nav.Sitting
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.nav.TxKind
import holdout.app.nav.then
import holdout.app.tx.SigningScreen
import holdout.app.tx.SigningState
import holdout.app.tx.YoureInScreen
import holdout.app.tx.YoureInState
import holdout.app.ui.ChipTone
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shell with fake data (rulings G1, G7, G8): the bar on roots only, tab
 * taps, the badges' accessible names, H0's cards, the stake sheet over its
 * opener and the faucet's way back, and the signing flow's results with the
 * buttons going where G7 says. No chain, wallet, storage or camera.
 */
@RunWith(RobolectricTestRunner::class)
class AppShellTest {
    @get:Rule
    val compose = createComposeRule()

    private var nav = NavState()
    private var send: (NavEvent) -> Unit = {}

    private fun event(e: NavEvent) = compose.runOnIdle { send(e) }

    private fun show(start: NavState, badges: TabBadges = TabBadges()) {
        nav = start
        compose.setContent { HoldoutTheme { Harness(start, badges) } }
    }

    @Composable
    private fun Harness(start: NavState, badges: TabBadges) {
        var state by remember { mutableStateOf(start) }
        val onEvent: (NavEvent) -> Unit = {
            state = Nav.reduce(state, it)
            nav = state
        }
        send = onEvent
        AppShell(
            nav = state,
            badges = badges,
            onEvent = onEvent,
            connect = { Text("Connect a wallet") },
            tour = { Text("The tour") },
            sitting = { Text("Sitting: $it") },
            signing = { tx ->
                if (tx.youreIn) {
                    YoureInScreen(YoureInState("Spark · 7-day", "2d 4h", 15, "13,750 SKR", "1,250 SKR", "2d 3h", daily25Done = false), { onEvent(NavEvent.StartSitting(Sitting.Daily25(1))) }, onEvent)
                } else {
                    SigningScreen(SigningState(tx.kind, tx.phase, "Spark · 7-day", amount = "15 SKR"), onEvent)
                }
            },
            root = { tab ->
                if (tab == Tab.Holdouts) {
                    MyHoldoutsScreen(H0, onEvent, onJoin = { onEvent(NavEvent.OpenStake(it, seekerKnown = true)) })
                } else {
                    Text("${tab.label} root")
                }
            },
            screen = { Text("Pushed: $it") },
            sheet = { s ->
                when (s) {
                    is Sheet.Stake -> {
                        Text("Stake sheet ${s.cohort}")
                        TextButton(onClick = { onEvent(NavEvent.OpenFaucet) }) { Text("Get test tokens") }
                    }
                    is Sheet.Faucet -> Text("Faucet sheet")
                }
            },
        )
    }

    @Test
    fun theBarShowsOnRootsAndTabTapsSwitchRoots() {
        show(HOME)
        compose.onNodeWithText("Today root").assertExists()
        compose.onNodeWithContentDescription("Today").assertIsSelected()
        compose.onNodeWithContentDescription("Standing").performClick()
        compose.onNodeWithText("Standing root").assertExists()
        compose.onNodeWithContentDescription("Me").performClick()
        compose.onNodeWithText("Me root").assertExists()
        assertEquals(Tab.Me, nav.tab)
    }

    @Test
    fun theBarIsGoneOnPushedScreensAndFullScreenSteps() {
        show(HOME.then(NavEvent.Open(Screen.About)))
        compose.onNodeWithText("Pushed: About").assertExists()
        compose.onNodeWithContentDescription("Today").assertDoesNotExist()

        event(NavEvent.Back)
        compose.onNodeWithContentDescription("Today").assertExists()
        event(NavEvent.StartSitting(Sitting.Daily25(1)))
        compose.onNodeWithText("Sitting: Daily25(day=1)").assertExists()
        compose.onNodeWithContentDescription("Today").assertDoesNotExist()
    }

    @Test
    fun beforeAWalletOnlyTheConnectScreen() {
        show(NavState())
        compose.onNodeWithText("Connect a wallet").assertExists()
        compose.onNodeWithContentDescription("Today").assertDoesNotExist()
    }

    @Test
    fun eachBadgeSaysItsMeaningInTheTabsAccessibleName() {
        show(HOME, TabBadges(today = true, standingDropped = 3, holdouts = true, me = true))
        compose.onNodeWithContentDescription("Today, today's reps aren't done").assertExists()
        compose.onNodeWithContentDescription("Standing, 3 dropped since you last looked").assertExists()
        compose.onNodeWithContentDescription("Holdouts, a claim is waiting").assertExists()
        compose.onNodeWithContentDescription("Me, low on SKR or SOL").assertExists()
    }

    @Test
    fun h0CardsGoWhereTheScreenMapSays() {
        show(HOME.then(NavEvent.SelectTab(Tab.Holdouts)))
        compose.onNodeWithText("Lobby card").performClick()
        compose.onNodeWithText("Pushed: Lobby(cohort=l)").assertExists()
        assertEquals(Tab.Holdouts, nav.tab)

        event(NavEvent.Back)
        compose.onNodeWithText("Running card").performClick()
        compose.onNodeWithText("Today root").assertExists()

        event(NavEvent.SelectTab(Tab.Holdouts))
        compose.onNodeWithText("Watched card").performScrollTo().performClick()
        compose.onNodeWithText("Pushed: Detail(cohort=w)").assertExists()

        event(NavEvent.Back)
        compose.onNodeWithText("Finished line →").performScrollTo().performClick()
        compose.onNodeWithText("Pushed: Detail(cohort=f)").assertExists()
    }

    @Test
    fun theStakeSheetOpensOverItsOpenerAndTheFaucetReturnsToIt() {
        show(HOME.then(NavEvent.SelectTab(Tab.Holdouts)))
        compose.onNodeWithText("Join").performScrollTo().performClick()
        compose.onNodeWithText("Stake sheet o").assertExists()
        // The opener stays composed under the scrim.
        compose.onNodeWithText("Holdouts").assertExists()

        compose.onNodeWithText("Get test tokens").performClick()
        compose.onNodeWithText("Faucet sheet").assertExists()
        compose.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Stake sheet o").assertExists()
        compose.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Stake sheet o").assertDoesNotExist()
        assertNull(nav.sheet)
        assertEquals(Tab.Holdouts, nav.tab)
    }

    @Test
    fun waitingForYourWalletHasNoCancel() {
        show(JOINING)
        compose.onNodeWithText("Waiting for your wallet").assertExists()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Today").assertDoesNotExist()
    }

    @Test
    fun aDeclinedJoinTriesAgainOrGoesBackToTheStakeSheet() {
        show(JOINING.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Declined))))
        compose.onNodeWithText("You declined in your wallet").assertExists()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Waiting for your wallet").assertExists()

        event(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Declined)))
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Stake sheet A").assertExists()
    }

    @Test
    fun aFailureThatMayStillLandNeverSaysTheFundsAreSafe() {
        show(JOINING.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Failed(mayStillLand = true, message = "x")))))
        compose.onNodeWithText("Not confirmed yet").assertExists()
        compose.onNodeWithText("Your SKR is still yours.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("check again in a minute", substring = true).assertExists()
    }

    @Test
    fun aLandedJoinShowsYoureInThenTheLobby() {
        show(JOINING.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("s")))))
        compose.onNodeWithText("You're in.").assertExists()
        compose.onNodeWithText("See the lobby").performScrollTo().performClick()
        compose.onNodeWithText("Pushed: Lobby(cohort=A)").assertExists()
    }

    @Test
    fun youreInStartsTodaysDaily25() {
        show(JOINING.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("s")))))
        compose.onNodeWithText("Do today's Daily 25").performScrollTo().performClick()
        compose.onNodeWithText("Sitting: Daily25(day=1)").assertExists()
    }

    @Test
    fun joiningClosedFirstShowsTheHoldoutsOpenToJoin() {
        show(JOINING.then(NavEvent.TxProgress(TxPhase.Done(TxOutcome.JoiningClosed))))
        compose.onNodeWithText("Joining closed first").assertExists()
        compose.onNodeWithText("See open Holdouts").performClick()
        compose.onNodeWithText("Holdouts").assertExists()
        assertEquals(Tab.Holdouts, nav.tab)
    }

    @Test
    fun aClaimThatLandedShowsTheAmountAndDoneGoesToH0() {
        show(HOME.then(NavEvent.Open(Screen.Claim("A")), NavEvent.TxStarted(TxKind.Claim("A")), NavEvent.TxProgress(TxPhase.Done(TxOutcome.Landed("4Bx9pQ7Qk2abcdef")))))
        compose.onNodeWithText("15 SKR").assertExists()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(Tab.Holdouts, nav.tab)
        assertTrue(nav.onRoot)
    }

    @Test
    fun aSubmitPastItsDeadlineLeadsToTheForfeit() {
        show(HOME.then(NavEvent.TxStarted(TxKind.Submit("A", 6)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.DeadlinePassed))))
        compose.onNodeWithText("The deadline passed").assertExists()
        compose.onNodeWithText("What happens now").performClick()
        compose.onNodeWithText("Pushed: Forfeit(cohort=A)").assertExists()
    }

    @Test
    fun shortOfSolOpensTheFaucetSheet() {
        show(HOME.then(NavEvent.TxStarted(TxKind.Submit("A", 6)), NavEvent.TxProgress(TxPhase.Done(TxOutcome.NotEnoughSol))))
        compose.onNodeWithText("Get test tokens").performClick()
        compose.onNodeWithText("Faucet sheet").assertExists()
        compose.onNodeWithText("Today root").assertExists()
    }

    @Test
    fun theConfirmingChecklistCanBeLeft() {
        show(JOINING.then(NavEvent.TxProgress(TxPhase.Confirming)))
        compose.onNodeWithContentDescription("Approved in your wallet, done").assertExists()
        compose.onNodeWithContentDescription("Confirming your place, in progress").assertExists()
        compose.onNodeWithText("Leave this screen").performScrollTo().performClick()
        compose.onNodeWithText("Today root").assertExists()
    }

    private companion object {
        val HOME = NavState().then(NavEvent.Connected(tourSeen = true))
        val JOINING = HOME.then(NavEvent.OpenStake("A", seekerKnown = true), NavEvent.TxStarted(TxKind.Join("A", 0)))

        val H0 = MyHoldoutsState(
            yours = listOf(
                YourHoldout("r", "Running card", YourKind.Running, ChipTone.Success, "Day 3 of 7 · 12 started", "15 push-ups to go today", false, NavEvent.SelectTab(Tab.Today)),
                YourHoldout("l", "Lobby card", YourKind.Lobby, ChipTone.Accent, "Joined · 10 SKR staked", "starts in 2d 0h", false, NavEvent.Open(Screen.Lobby("l"))),
            ),
            open = listOf(OpenHoldout("o", "Offer card", "9,000 SKR", "at stake · 9 joined", null, "joining closes in 1d 23h", null, false)),
            finished = listOf(FinishedHoldout("f", "Finished line", NavEvent.Open(Screen.Detail("f")))),
            running = listOf(RunningHoldout("w", "Watched card", "12 started · prize pool 0 SKR", false)),
        )
    }
}
