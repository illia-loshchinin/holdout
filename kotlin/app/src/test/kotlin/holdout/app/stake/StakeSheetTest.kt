package holdout.app.stake

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.CohortStatus
import holdout.core.Comeback
import holdout.core.Journey
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stake confirm (board 4 · 4c; ruling G8; US-6, FR-041, FR-059, ES-12): the
 * words of each level, the chips, the lines every join carries, each way the
 * stake can be blocked, and the buttons' callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class StakeSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L

    /** Two days, three hours before joining closes. */
    private val now = start - 3_600 - 2 * day - 3 * 3_600 - 1_200

    private fun holdout(form: Int = LeagueParams.FORM_KNEES_ALLOWED, days: Int = 7, leagueId: Int = LeagueParams.LEAGUE_7D, joined: Long = 14, held: ULong = 12_500uL * skr, dayLength: Long = day, cutoff: Long = start - 3_600, reps: Int = 25, boost: ULong = 0uL) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { 3 }),
        cohort = CohortSnapshot(leagueId, 1, start, cutoff, days, reps, dayLength, 0, listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr), memberCount = joined, heldStake = held, form = form),
        league = if (leagueId < LeagueParams.TEST_LEAGUE_MIN) LeagueSnapshot(LeagueParams.judged(leagueId, start), nextIndex = 3, boostReserve = boost) else null,
        membership = null,
    )

    private val staked = mutableListOf<Int>()
    private val tiersPicked = mutableListOf<Int>()
    private var tokens = 0
    private var lobbies = 0
    private var tours = 0

    private fun show(
        h: Holdout = holdout(),
        skrBalance: ULong = 10_000uL * skr,
        lamports: Long = 1_000_000_000L,
        at: Long = now,
        blocker: Journey.JoinBlocker? = null,
        busy: Boolean = false,
        message: String? = null,
        initialTier: Int = 1,
        notificationReason: Boolean = true,
        seeLobby: Boolean = false,
        comeback: Comeback.Offer? = null,
    ) {
        tours = 0
        compose.setContent {
            HoldoutTheme {
                StakeSheet(
                    h = h,
                    skrBalance = skrBalance,
                    lamports = lamports,
                    now = at,
                    joinBlocker = blocker,
                    busy = busy,
                    message = message,
                    messageIsWarning = true,
                    onStake = { staked += it },
                    onGetTokens = { tokens++ },
                    initialTier = initialTier,
                    onTierChange = { tiersPicked += it },
                    onSeeLobby = if (seeLobby) ({ lobbies++ }) else null,
                    showNotificationReason = notificationReason,
                    comeback = comeback,
                    onHowItWorks = { tours++ },
                )
            }
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onNode(hasText(s, substring = substring))

    @Test
    fun `Spark - the title, the level line, when it starts and the rule for push-ups from the knees`() {
        show()
        text("Join the Spark · 7-day Holdout").assertExists()
        text("Light it up: 25 push-ups a day for 7 days.").assertExists()
        text("Starts in 2d 4h").assertExists()
        text("At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count.").assertExists()
        // The rule is a line of its own now, with no box or label (the sheet fits a Seeker screen).
        text("HOW REPS ARE COUNTED HERE").assertDoesNotExist()
    }

    @Test
    fun `Habit - its own level line and rule, never the Spark sentence`() {
        show(holdout(form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D, held = 9_000uL * skr, joined = 9))
        text("Join the Habit · 21-day Holdout").assertExists()
        text("Make it stick: 25 push-ups a day for 21 days.").assertExists()
        text("At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count.").assertExists()
        text("push-ups from the knees still count", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the four chips show the dollar tier over the exact SKR, and the pick moves the stake`() {
        show(initialTier = 0)
        compose.onNodeWithContentDescription("≈ \$10, 500 SKR").assertIsSelected()
        compose.onNodeWithContentDescription("≈ \$25, 1,250 SKR").assertExists()
        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertExists()
        compose.onNodeWithContentDescription("≈ \$100, 5,000 SKR").assertExists()
        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").performClick()
        text("Stake 2,500 SKR").performScrollTo().performClick()
        assertEquals(listOf(2), staked)
    }

    @Test
    fun `a chip pick is reported, so the pick can outlive the sheet`() {
        show(initialTier = 0)
        compose.onNodeWithContentDescription("≈ \$100, 5,000 SKR").performClick()
        compose.onNodeWithContentDescription("≈ \$25, 1,250 SKR").performClick()
        assertEquals(listOf(3, 1), tiersPicked)
    }

    @Test
    fun `what is at stake now and how many joined, and when joining closes - the stake itself is the chip and the button`() {
        show()
        text("Your stake").assertDoesNotExist()
        // The chip alone carries the dollar label; the button names the SKR.
        compose.onAllNodes(hasText("≈ \$25")).assertCountEquals(1)
        text("Stake 1,250 SKR").assertExists()
        text("At stake now").assertExists()
        text("12,500 SKR").assertExists()
        text("14 joined").assertExists()
        text("Joining closes in").assertExists()
        text("2d 3h").assertExists()
    }

    @Test
    fun `every join carries the refund line word for word, the one line on losing, the full refund and the footer`() {
        show()
        text("If nobody drops out, you get exactly your stake back.").assertExists()
        text("Miss a day and you lose your stake.").assertExists()
        text("Full refund until joining closes.").assertExists()
        text("Test network, test token. Dollar values are approximate.").assertExists()
    }

    @Test
    fun `the sheet says nothing of where a lost stake goes - no split, no what-if-nobody-finishes`() {
        show()
        text("70 %", substring = true).assertDoesNotExist()
        text("%", substring = true).assertDoesNotExist()
        text("If nobody finishes", substring = true).assertDoesNotExist()
        text("prize pool", substring = true).assertDoesNotExist()
        text("comeback", substring = true).assertDoesNotExist()
        text("to the project", substring = true).assertDoesNotExist()
    }

    @Test
    fun `How it works sits beside the one line, the full refund under it`() {
        show()
        val line = text("Miss a day and you lose your stake.").getBoundsInRoot()
        val refund = text("Full refund until joining closes.").getBoundsInRoot()
        val action = text("How it works").getBoundsInRoot()
        // The full refund is under the line; the action is beside it, on its row, and a full touch target.
        assertTrue(refund.top >= line.bottom - 1.dp, "refund $refund under line $line")
        assertTrue(action.left >= line.right - 1.dp || action.left > line.left, "action $action beside line $line")
        assertTrue(action.top < refund.top, "action $action on the line's row, above the refund $refund")
    }

    @Test
    fun `How it works is a button that opens the tour`() {
        show()
        text("How it works").assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(1, tours)
        assertEquals(emptyList(), staked, "nothing was staked")
    }

    @Test
    fun `the prize boost is its own line when the reserve holds more than nothing`() {
        show(holdout(boost = 480uL * skr))
        text("Prize boost: up to 480 SKR").assertExists()
        val boost = text("Prize boost: up to 480 SKR").getBoundsInRoot()
        val line = text("Miss a day and you lose your stake.").getBoundsInRoot()
        assertTrue(boost.top >= line.bottom || boost.bottom <= line.top, "its own line, not inside another: $boost vs $line")
    }

    @Test
    fun `an empty reserve shows no boost line`() {
        show(holdout(boost = 0uL))
        text("Prize boost", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a Hackathon round has no judged league, so no boost line`() {
        show(holdout(leagueId = LeagueParams.TEST_LEAGUE_MIN, days = 3, dayLength = 600, cutoff = start, reps = 5), at = start - 750)
        text("Prize boost", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the notification reason shows while notifications are off and goes once they are on`() {
        show()
        text("Allow notifications and this phone reminds you before each deadline. Reminders never leave the phone.").assertExists()
    }

    @Test
    fun `with notifications on the reason is not repeated`() {
        show(notificationReason = false)
        text("Allow notifications", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no Back button - the sheet closes by drag or scrim`() {
        show()
        text("← Back").assertDoesNotExist()
        text("Back", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the stake button stakes the chosen chip`() {
        show(initialTier = 1)
        text("Stake 1,250 SKR").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf(1), staked)
    }

    @Test
    fun `while the wallet is out the button says so and can't be tapped twice`() {
        show(busy = true)
        text("Waiting for your wallet…").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `short of SKR - what is held against what the chip needs, and Get test tokens`() {
        show(skrBalance = 400uL * skr, initialTier = 1)
        text("You hold 400 SKR. This chip needs 1,250 SKR.").assertExists()
        // Get test tokens takes the stake button's place.
        text("Stake 1,250 SKR").assertDoesNotExist()
        text("Get test tokens").performScrollTo().performClick()
        assertEquals(1, tokens)
    }

    @Test
    fun `short of SOL - the faucet sends some with the test tokens`() {
        show(lamports = 1_000L)
        text("Your wallet has too little devnet SOL to pay for joining. The faucet sends some with the test tokens.").assertExists()
        text("Stake 1,250 SKR").assertDoesNotExist()
        text("Get test tokens").performScrollTo().performClick()
        assertEquals(1, tokens)
    }

    @Test
    fun `joining closed disables the stake and says why`() {
        show(blocker = Journey.JoinBlocker.JoiningClosed)
        text("Joining has closed for this Holdout.").assertExists()
        text("Stake 1,250 SKR").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a place already held in a lobby points to that lobby`() {
        show(blocker = Journey.JoinBlocker.LobbyPlaceTaken, seeLobby = true)
        text("You already hold a place in a Holdout that hasn't started.").assertExists()
        text("See your lobby").performScrollTo().performClick()
        assertEquals(1, lobbies)
        // See your lobby takes the stake button's place.
        text("Stake 1,250 SKR").assertDoesNotExist()
    }

    @Test
    fun `a Holdout that starts before the current one ends can't be joined`() {
        show(blocker = Journey.JoinBlocker.Overlaps)
        text("This Holdout starts before your current one ends.").assertExists()
        text("See your lobby").assertDoesNotExist()
        text("Stake 1,250 SKR").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `in the last minutes before the cutoff the sheet warns a transaction may land too late (ES-12)`() {
        show(at = start - 3_600 - 510)
        // The countdown above says how long; the warning says what it means.
        text("8m 30s").assertExists()
        text("A transaction signed now may land too late.").assertExists()
        // The warning is not a block: the stake is still possible.
        text("Stake 1,250 SKR").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `earlier than that there is no warning`() {
        show()
        text("may land too late", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a comeback round says exactly what finishing it brings back for the chip chosen`() {
        val round = holdout()
        val lost = Comeback.Entry(SolanaPublicKey(ByteArray(32) { 8 }), round.cohort, null)
        val offer = Comeback.Offer(
            slot = 0,
            lost = 1_250uL * skr,
            tier = 1,
            missed = start - 5 * day,
            firstRound = 1,
            forfeited = lost,
            rounds = listOf(Comeback.Round(start, start - 7 * day, Comeback.Entry(round.address, round.cohort, null), CohortStatus.Open)),
            held = null,
        )
        show(round, initialTier = 1, comeback = offer)
        text("Finish it and 125 SKR of the stake you lost comes back.").assertExists()
        // The refund is capped at the new stake, and 125 SKR is below the smallest chip, so the pick does not change it.
        compose.onNodeWithContentDescription("≈ \$10, 500 SKR").performClick()
        text("Finish it and 125 SKR of the stake you lost comes back.").assertExists()
    }

    /** What a refresh of the chain changes under an open sheet: the clock, the roster count, the held stake, the balance. */
    private class Live(h: Holdout, at: Long, balance: ULong) {
        var h by mutableStateOf(h)
        var at by mutableLongStateOf(at)
        var balance by mutableStateOf(balance)
    }

    private fun comebackOffer(round: Holdout, tier: Int) = Comeback.Offer(
        slot = 0,
        lost = 2_500uL * skr,
        tier = tier,
        missed = start - 5 * day,
        firstRound = 1,
        forfeited = Comeback.Entry(SolanaPublicKey(ByteArray(32) { 8 }), round.cohort, null),
        rounds = listOf(Comeback.Round(start, start - 7 * day, Comeback.Entry(round.address, round.cohort, null), CohortStatus.Open)),
        held = null,
    )

    @Test
    fun `a comeback sheet keeps the lost stake's chip when the chain refreshes under it`() {
        val round = holdout()
        val live = Live(round, now, 10_000uL * skr)
        compose.setContent {
            HoldoutTheme {
                StakeSheet(
                    h = live.h,
                    skrBalance = live.balance,
                    lamports = 1_000_000_000L,
                    now = live.at,
                    joinBlocker = null,
                    busy = false,
                    message = null,
                    messageIsWarning = true,
                    onStake = { staked += it },
                    onGetTokens = {},
                    initialTier = 2,
                    onTierChange = { tiersPicked += it },
                    comeback = comebackOffer(round, tier = 2),
                )
            }
        }
        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertIsSelected()

        // A refresh: later clock, one more member, more held stake, a different balance, a new snapshot of the same Holdout.
        live.at += 30
        live.h = round.copy(cohort = round.cohort.copy(memberCount = round.cohort.memberCount + 1, heldStake = round.cohort.heldStake + 1_250uL * skr))
        live.balance = 9_000uL * skr
        compose.waitForIdle()

        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertIsSelected()
        compose.onNodeWithContentDescription("≈ \$25, 1,250 SKR").assertIsNotSelected()
        assertEquals(emptyList(), tiersPicked, "nothing was picked")
    }

    @Test
    fun `a scroll that starts on a chip does not pick it`() {
        val round = holdout()
        compose.setContent {
            HoldoutTheme {
                StakeSheet(
                    h = round,
                    skrBalance = 10_000uL * skr,
                    lamports = 1_000_000_000L,
                    now = now,
                    joinBlocker = null,
                    busy = false,
                    message = null,
                    messageIsWarning = true,
                    onStake = {},
                    onGetTokens = {},
                    modifier = Modifier.height(360.dp),
                    initialTier = 2,
                    onTierChange = { tiersPicked += it },
                    comeback = comebackOffer(round, tier = 2),
                )
            }
        }
        compose.onNodeWithContentDescription("≈ \$25, 1,250 SKR").performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f, durationMillis = 40) }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertIsSelected()
        assertEquals(emptyList(), tiersPicked, "nothing was picked")
    }

    @Test
    fun `a coarse swipe, down then one far move then up, that starts on a chip does not pick it`() {
        val round = holdout()
        compose.setContent {
            HoldoutTheme {
                StakeSheet(
                    h = round,
                    skrBalance = 10_000uL * skr,
                    lamports = 1_000_000_000L,
                    now = now,
                    joinBlocker = null,
                    busy = false,
                    message = null,
                    messageIsWarning = true,
                    onStake = {},
                    onGetTokens = {},
                    modifier = Modifier.height(360.dp),
                    initialTier = 2,
                    onTierChange = { tiersPicked += it },
                    comeback = comebackOffer(round, tier = 2),
                )
            }
        }
        compose.onNodeWithContentDescription("≈ \$25, 1,250 SKR").performTouchInput {
            down(center)
            advanceEventTime(10)
            moveTo(center - androidx.compose.ui.geometry.Offset(0f, 300f))
            up()
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("≈ \$50, 2,500 SKR").assertIsSelected()
        assertEquals(emptyList(), tiersPicked, "nothing was picked")
    }

    @Test
    fun `without an offer for this Holdout nothing is promised`() {
        show()
        text("of the stake you lost comes back", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a Hackathon Holdout wears its badge and its own words`() {
        val test = holdout(leagueId = LeagueParams.TEST_LEAGUE_MIN, days = 3, dayLength = 600, joined = 3, held = 250uL * skr, cutoff = start, reps = 5)
        show(test, at = start - 750)
        text("DEVNET COHORT · 10-MINUTE DAYS").assertExists()
        text("Join the Hackathon · 3-day Holdout").assertExists()
        text("A quick taste: 5 push-ups a day for 3 days. Joinable right up to its start.").assertExists()
        text("Starts in 12m 30s").assertExists()
        text("Miss a day and you lose your stake.").assertExists()
        text("If nobody finishes", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no dollar figure sits beside a pool or a total`() {
        show()
        // The only dollar labels are the chips'.
        compose.onAllNodes(hasText("≈ \$25")).assertCountEquals(1)
        text("12,500 SKR ≈", substring = true).assertDoesNotExist()
    }
}
