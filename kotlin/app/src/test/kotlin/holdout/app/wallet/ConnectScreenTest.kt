package holdout.app.wallet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.TALL_PHONE
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * A1 First open, A3 No wallet and the wait for the wallet (boards Connect,
 * ConnectNetwork, NoWallet, WalletConnect; FR-001a, FR-001b, FR-053; rulings
 * "Onboarding / Me"): the two products in a few short lines, the live list as
 * one card with a row per level, the wallet wait with no Cancel, the notices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PHONE)
class ConnectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val animations = AnimationsOff()

    private val day = 86_400L
    private val now = 1_791_176_400L + 3 * day
    private val wallet = SolanaPublicKey(ByteArray(32) { 0x11 })

    private fun cohort(n: Int, start: Long, form: Int, days: Int, joined: Long, held: ULong = 0uL, pool: ULong = 0uL, leagueId: Int = 0) = CohortSnapshot(
        leagueId = leagueId, index = n.toLong(), start = start, joinCutoff = start - 3_600, durationDays = days, repsPerDay = 25,
        dayLength = day, exercise = 0, chips = listOf(500_000_000uL, 1_250_000_000uL, 2_500_000_000uL, 5_000_000_000uL),
        memberCount = joined, heldStake = held, pool = pool, form = form,
    )

    private fun holdout(c: CohortSnapshot, boost: ULong? = null) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { (c.index + 1).toByte() }),
        cohort = c,
        league = boost?.let { LeagueSnapshot(LeagueParams.judged(c.leagueId, c.start), nextIndex = 0, boostReserve = it) },
        membership = null,
    )

    private fun show(
        busy: Boolean = false,
        message: String? = null,
        noWallet: Boolean = false,
        live: List<Holdout> = emptyList(),
        rosters: Map<SolanaPublicKey, List<MembershipSnapshot>> = emptyMap(),
        onConnect: () -> Unit = {},
    ) {
        compose.setContent {
            HoldoutTheme {
                ConnectScreen(busy = busy, message = message, noWallet = noWallet, live = live, now = now, rosters = rosters, onConnect = onConnect)
            }
        }
    }

    // A running Spark and the next open one, a running Habit with no open one.
    private val runningSpark = holdout(cohort(1, now - 2 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 20, held = 12_500_000_000uL, pool = 6_125_000_000uL))
    private val openSpark = holdout(cohort(2, now + 5 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 14, held = 12_500_000_000uL), boost = 480_000_000uL)
    private val runningHabit = holdout(cohort(3, now - 11 * day, LeagueParams.FORM_TOES, 21, joined = 312, pool = 48_200_000_000uL))

    @Test
    fun `first open says what each product is in a few short lines`() {
        show()

        // The wordmark: HOLDOUT in tracked capitals, read aloud as "Holdout" (owner, 12 Oct).
        compose.onNodeWithText("HOLDOUT").assertIsDisplayed()
        compose.onNodeWithContentDescription("Holdout").assertIsDisplayed()
        compose.onNodeWithText("Stake on yourself.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Prove it every day.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The Daily 25 — free, every day: get stronger at your own pace. 25 push-ups, counted by the camera.\nYour streak grows.").assertIsDisplayed()
        compose.onNodeWithText("A Holdout — stake SKR on a 7-day Spark or a 21-day Habit.\nMiss a day: your stake joins the prize pool.\nFinish: take it back plus a share of the pool.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: devnet").assertIsDisplayed()
    }

    @Test
    fun `connect wallet asks for the wallet and a quiet action explains how to get one`() {
        var connects = 0
        show(onConnect = { connects++ })

        compose.onNodeWithText("Connect wallet").performClick()
        assertEquals(1, connects)
        compose.onNodeWithText("No wallet yet?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `no wallet yet opens the help with no live list and connect still works`() {
        var connects = 0
        show(live = listOf(runningSpark), onConnect = { connects++ })
        compose.onNodeWithText("Live right now", ignoreCase = true).assertIsDisplayed()

        compose.onNodeWithText("No wallet yet?").performClick()

        compose.onNodeWithText("You need a Solana wallet").assertIsDisplayed()
        compose.onNodeWithText("Even the free Daily 25 needs one. It holds your keys — Holdout never does.").assertIsDisplayed()
        compose.onNodeWithText("On a Seeker — Seed Vault Wallet is built in.").assertIsDisplayed()
        compose.onNodeWithText("On other phones — install a Solana wallet with Mobile Wallet Adapter, set it to devnet, come back.").assertIsDisplayed()
        compose.onNodeWithText("Test network · test token", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Live right now", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("No wallet yet?").assertDoesNotExist()
        compose.onNodeWithText("Connect wallet").performClick()
        assertEquals(1, connects)
    }

    @Test
    fun `a connect that found no wallet app shows the same help straight away`() {
        show(noWallet = true)

        compose.onNodeWithText("You need a Solana wallet").assertIsDisplayed()
        compose.onNodeWithText("Connect wallet").assertIsDisplayed()
    }

    @Test
    fun `waiting for the wallet is its own screen with no cancel`() {
        show(busy = true, live = listOf(runningSpark))

        compose.onNodeWithText("Waiting for your wallet…").assertIsDisplayed()
        compose.onNodeWithText("Approve or decline in your wallet. Nothing moves until you approve.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("Connect wallet").assertDoesNotExist()
    }

    @Test
    fun `the wrong network notice says nothing was signed and what to do`() {
        show(message = WalletRepository.WRONG_NETWORK)

        compose.onNodeWithText("Your wallet isn't on devnet, so nothing was signed. Switch it to devnet, then connect again.").assertIsDisplayed()
        compose.onNodeWithText("Connect wallet").assertIsDisplayed()
    }

    @Test
    fun `any other connect failure gets one plain line`() {
        show(message = "java.net.SocketTimeoutException: connect timed out")

        compose.onNodeWithText("We couldn't connect to your wallet. Try again.").assertIsDisplayed()
        compose.onAllNodesWithText("SocketTimeoutException", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the live list is one card with a row per level, what is running and what is open`() {
        show(live = listOf(runningSpark, openSpark, runningHabit))

        compose.onNodeWithText("Live right now", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Test network, test token.").assertIsDisplayed()
        // Running Spark: the label with the day, the status word, who started, the pool.
        compose.onNodeWithText("SPARK · 7-DAY · DAY 3 OF 7").assertIsDisplayed()
        compose.onNodeWithText("20 started").assertIsDisplayed()
        compose.onNodeWithText("6,125 SKR").assertIsDisplayed()
        // The open one of the same level hangs under it: at stake, joined, how long joining stays open, the boost (FR-053).
        compose.onNodeWithText("Next: 12,500 SKR at stake · 14 joined").assertIsDisplayed()
        compose.onNodeWithText("Joining closes in 4d 23h").assertIsDisplayed()
        compose.onNodeWithText("Prize boost: up to 480 SKR").assertIsDisplayed()
        // Running Habit with nothing open at its level.
        compose.onNodeWithText("HABIT · 21-DAY · DAY 12 OF 21").assertIsDisplayed()
        compose.onNodeWithText("312 started").assertIsDisplayed()
        compose.onNodeWithText("48,200 SKR").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Status: running").assertCountEquals(2)
        compose.onAllNodesWithText("prize pool", ignoreCase = true).assertCountEquals(2)
    }

    @Test
    fun `still standing comes from the roster once it is read`() {
        val members = listOf(
            MembershipSnapshot(wallet, 0, 500_000_000uL, now - 5 * day, 2, MembershipSnapshot.State.Joined),
            MembershipSnapshot(SolanaPublicKey(ByteArray(32) { 0x22 }), 0, 500_000_000uL, now - 5 * day, 2, MembershipSnapshot.State.Joined),
            MembershipSnapshot(SolanaPublicKey(ByteArray(32) { 0x33 }), 0, 500_000_000uL, now - 5 * day, 0, MembershipSnapshot.State.Forfeited),
        )
        val three = holdout(cohort(1, now - 2 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 3, pool = 1_000_000uL))
        show(live = listOf(three), rosters = mapOf(three.address to members))

        compose.onNodeWithText("2 / 3 still standing").assertIsDisplayed()
    }

    @Test
    fun `an open Holdout with nothing running at its level gets its own row`() {
        show(live = listOf(openSpark))

        compose.onNodeWithText("SPARK · 7-DAY").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status: open").assertIsDisplayed()
        compose.onNodeWithText("12,500 SKR at stake · 14 joined").assertIsDisplayed()
        compose.onNodeWithText("Joining closes in 4d 23h").assertIsDisplayed()
    }

    @Test
    fun `a Hackathon Holdout keeps its disclosed badge`() {
        val test = holdout(cohort(9, now + day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 1, leagueId = LeagueParams.TEST_LEAGUE_MIN))
        show(live = listOf(test))

        compose.onNodeWithText("DEVNET COHORT", substring = true).assertIsDisplayed()
    }

    @Test
    fun `both Hackathon lobbies get their own row, Spark first, after the real levels`() {
        val testStart = now + 2 * 3_600
        val habitTest = holdout(cohort(8, testStart, LeagueParams.FORM_TOES, 3, joined = 1, leagueId = LeagueParams.TEST_LEAGUE_MIN + 1))
        val sparkTest = holdout(cohort(9, testStart, LeagueParams.FORM_KNEES_ALLOWED, 3, joined = 1, leagueId = LeagueParams.TEST_LEAGUE_MIN))
        val spark = holdout(cohort(1, now + 2 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 5))
        val rows = liveRows(listOf(habitTest, spark, sparkTest), now)

        assertEquals(listOf(spark, sparkTest, habitTest).map { it.address }, rows.map { (it.running ?: it.open)!!.address })
    }

    @Test
    fun `no dollar figures, banned words or judges anywhere`() {
        show(live = listOf(runningSpark, openSpark, runningHabit))

        listOf("≈", "Dollar values", "cohort", "Easy", "Hard", "judge", "earn").forEach {
            compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
