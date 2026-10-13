package holdout.app.wallet

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
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
import holdout.app.testutil.textColors
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.app.ui.theme.Spacing
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `the wrong network notice is a calm status card, not a warning`() {
        show(message = WalletRepository.WRONG_NETWORK)

        val notice = compose.onNodeWithText("Your wallet isn't on devnet, so nothing was signed. Switch it to devnet, then connect again.")
        notice.assertIsDisplayed()
        notice.assertCalmStatus()
        compose.onNodeWithText("Connect wallet").assertIsDisplayed()
    }

    @Test
    fun `any other connect failure gets one plain line, calm too`() {
        show(message = "java.net.SocketTimeoutException: connect timed out")

        val notice = compose.onNodeWithText("We couldn't connect to your wallet. Try again.")
        notice.assertIsDisplayed()
        notice.assertCalmStatus()
        compose.onAllNodesWithText("SocketTimeoutException", substring = true).assertCountEquals(0)
    }

    /** A polite live region, and no text in `Warning`: the notice informs, it does not alarm (FR-053, 2026-10-14). */
    private fun SemanticsNodeInteraction.assertCalmStatus() {
        assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertFalse(HoldoutColors.Warning in textColors(), "no Warning-coloured text in the notice")
        assertTrue(HoldoutColors.TextPrimary in textColors(), "the notice reads in the primary text colour")
    }

    @Test
    fun `the live list is one card with a row per running Holdout, its count, its pool and its next round`() {
        show(live = listOf(runningSpark, openSpark, runningHabit))

        compose.onNodeWithText("Live right now", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Test network, test token.").assertIsDisplayed()
        // Running Spark: the label with the day, who started, the pool in the money colour.
        compose.onNodeWithText("SPARK · 7-DAY · DAY 3 OF 7").assertIsDisplayed()
        compose.onNodeWithText("20 started").assertIsDisplayed()
        assertTrue(HoldoutColors.Success in compose.onNodeWithText("6,125 SKR").textColors(), "the pool is in Success")
        // The next round of the same level hangs under it, on its own block (FR-053): what is at stake, who joined, how long joining stays open, the boost.
        compose.onNodeWithText("NEXT ROUND").assertIsDisplayed()
        compose.onNodeWithText("closes in 4d 23h").assertIsDisplayed()
        compose.onNodeWithText("12,500 SKR at stake · 14 joined").assertIsDisplayed()
        compose.onNodeWithText("up to 480 boost").assertIsDisplayed()
        // Running Habit with nothing open at its level: no block under it.
        compose.onNodeWithText("HABIT · 21-DAY · DAY 12 OF 21").assertIsDisplayed()
        compose.onNodeWithText("312 started").assertIsDisplayed()
        assertTrue(HoldoutColors.Success in compose.onNodeWithText("48,200 SKR").textColors(), "the pool is in Success")
        compose.onAllNodesWithText("NEXT ROUND").assertCountEquals(1)
        compose.onAllNodesWithText("prize pool", ignoreCase = true).assertCountEquals(2)
    }

    @Test
    fun `each running Holdout says running in a word beside a green dot, not as a chip`() {
        show(live = listOf(runningSpark, openSpark, runningHabit))

        compose.onAllNodesWithText("running").assertCountEquals(2)
        compose.onAllNodesWithText("RUNNING").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Status: running").assertCountEquals(0)
    }

    @Test
    fun `the next round's countdown is Accent, the boost has no plus, and an empty reserve omits it`() {
        show(live = listOf(runningSpark, openSpark))

        assertTrue(HoldoutColors.Accent in compose.onNodeWithText("closes in 4d 23h").textColors(), "the countdown is in Accent")
        compose.onAllNodes(hasText("+", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Joining closes in", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Prize boost", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Next:", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `a next round with an empty boost reserve has no boost text`() {
        val noBoost = holdout(cohort(2, now + 5 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 14, held = 12_500_000_000uL), boost = 0uL)
        show(live = listOf(runningSpark, noBoost))

        compose.onNodeWithText("NEXT ROUND").assertIsDisplayed()
        compose.onAllNodes(hasText("boost", substring = true, ignoreCase = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("up to", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `the count has room above it, a full gap under the running row's label`() {
        show(live = listOf(runningSpark))

        val label = compose.onNodeWithText("SPARK · 7-DAY · DAY 3 OF 7").getBoundsInRoot()
        val count = compose.onNodeWithText("20 started").getBoundsInRoot()
        assertTrue(count.top - label.bottom >= Spacing.betweenItems, "count ${count.top} under label ${label.bottom}")
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
        compose.onNodeWithText("open").assertIsDisplayed()
        compose.onNodeWithText("NEXT ROUND").assertIsDisplayed()
        compose.onNodeWithText("12,500 SKR at stake · 14 joined").assertIsDisplayed()
        compose.onNodeWithText("closes in 4d 23h").assertIsDisplayed()
        compose.onNodeWithText("up to 480 boost").assertIsDisplayed()
    }

    @Test
    fun `a Hackathon Holdout keeps its disclosed badge`() {
        val test = holdout(cohort(9, now + day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 1, leagueId = LeagueParams.TEST_LEAGUE_MIN))
        show(live = listOf(test))

        compose.onNodeWithText("DEVNET COHORT", substring = true).assertIsDisplayed()
    }

    @Test
    fun `no dollar figures, banned words or judges anywhere`() {
        show(live = listOf(runningSpark, openSpark, runningHabit))

        listOf("≈", "Dollar values", "cohort", "Easy", "Hard", "judge", "earn").forEach {
            compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }
}
