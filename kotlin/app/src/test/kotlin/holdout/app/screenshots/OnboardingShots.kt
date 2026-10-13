package holdout.app.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.solana.publickey.SolanaPublicKey
import holdout.app.about.AboutScreen
import holdout.app.chain.Holdout
import holdout.app.faucet.FaucetOutcome
import holdout.app.faucet.FaucetSheet
import holdout.app.me.MeScreen
import holdout.app.me.MeState
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.streak.ToesMeter
import holdout.app.testutil.AnimationsOff
import holdout.app.tour.TourScreen
import holdout.app.ui.StreakState
import holdout.app.wallet.ConnectScreen
import holdout.app.wallet.WalletRepository
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** First open and the connect screen's states, the four tour cards, Me, the faucet sheet and About. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class OnboardingShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // ---- Connect ----------------------------------------------------------------------------------------

    private val day = 86_400L
    private val now = 1_791_176_400L + 3 * day

    private fun cohort(n: Int, start: Long, form: Int, days: Int, joined: Long, held: ULong = 0uL, pool: ULong = 0uL) = CohortSnapshot(
        leagueId = 0, index = n.toLong(), start = start, joinCutoff = start - 3_600, durationDays = days, repsPerDay = 25,
        dayLength = day, exercise = 0, chips = listOf(500_000_000uL, 1_250_000_000uL, 2_500_000_000uL, 5_000_000_000uL),
        memberCount = joined, heldStake = held, pool = pool, form = form,
    )

    private fun holdout(c: CohortSnapshot, boost: ULong? = null) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { (c.index + 1).toByte() }),
        cohort = c,
        league = boost?.let { LeagueSnapshot(LeagueParams.judged(c.leagueId, c.start), nextIndex = 0, boostReserve = it) },
        membership = null,
    )

    private val live = listOf(
        holdout(cohort(1, now - 2 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 20, held = 12_500_000_000uL, pool = 6_125_000_000uL)),
        holdout(cohort(2, now + 5 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 14, held = 12_500_000_000uL), boost = 480_000_000uL),
        holdout(cohort(3, now - 11 * day, LeagueParams.FORM_TOES, 21, joined = 312, pool = 48_200_000_000uL)),
    )

    private fun connect(name: String, busy: Boolean = false, message: String? = null, noWallet: Boolean = false, live: List<Holdout> = emptyList()) = compose.shoot("onboarding", name, heightDp = 1100) {
        ConnectScreen(busy = busy, message = message, noWallet = noWallet, live = live, now = now, onConnect = {})
    }

    @Test
    fun connectFirstOpen() = connect("connect-first-open")

    @Test
    fun connectLiveList() = connect("connect-live-list", live = live)

    @Test
    fun connectWaiting() = connect("connect-waiting", busy = true)

    @Test
    fun connectNoWallet() = connect("connect-no-wallet", noWallet = true)

    @Test
    fun connectWrongNetwork() = connect("connect-wrong-network", message = WalletRepository.WRONG_NETWORK)

    @Test
    fun connectFailed() = connect("connect-failed", message = "java.net.SocketTimeoutException: connect timed out")

    // ---- Tour -------------------------------------------------------------------------------------------

    // Card 4's figure is read from the assets on a background dispatcher, which Compose's idling does not wait for.
    private fun tour(card: Int) = compose.shoot(
        "onboarding",
        "tour-$card",
        after = {
            repeat(card - 1) { onNodeWithText("Next").performClick() }
            if (card == 4) {
                repeat(10) {
                    Thread.sleep(200)
                    mainClock.advanceTimeBy(100)
                    waitForIdle()
                }
            }
        },
    ) { TourScreen(onDone = {}) }

    @Test
    fun tour1() = tour(1)

    @Test
    fun tour2() = tour(2)

    @Test
    fun tour3() = tour(3)

    @Test
    fun tour4() = tour(4)

    @Test
    fun tour5() = tour(5)

    // ---- Me ---------------------------------------------------------------------------------------------

    private val meState = MeState(
        wallet = "7xQpAbCdEfGhJkLmNpQrStUvWxYz123456783fA9",
        skr = 4_750_000_000uL,
        lamports = 12_000_000L,
        faucetSkr = 5_000_000_000uL,
        streak = 12,
        freezes = 1,
        longest = 21,
        toes = ToesMeter.Meter(toes = 18, measured = 25, week = listOf(0, 10, 25, 25, 0, 18, 18), best = 25),
        leaderboardMeta = "#4 in Spark · 7-day",
    )

    private fun me(name: String, state: MeState, badges: TabBadges = TabBadges()) = compose.shoot("onboarding", name, heightDp = 1000) {
        Tabbed(Tab.Me, badges) { MeScreen(state, onGetTokens = {}, onAbout = {}, onDisconnect = {}) }
    }

    @Test
    fun me() = me("me", meState)

    @Test
    fun meLowOnFunds() = me("me-low-on-funds", meState.copy(skr = 400_000_000uL, lamports = 1_000L, lowOnFunds = true, streak = 0, streakState = StreakState.Broken, freezes = 0), TabBadges(me = true))

    // ---- Faucet sheet, About ------------------------------------------------------------------------------

    private fun faucet(name: String, outcome: FaucetOutcome? = null, back: String? = null, busy: Boolean = false) = compose.shoot("onboarding", name) {
        Sheeted(behind = { Tabbed(Tab.Me) { MeScreen(meState, {}, {}, {}) } }) {
            FaucetSheet(
                skr = 400_000_000uL,
                lamports = 1_000L,
                maxTopTier = 5_000_000_000uL,
                busy = busy,
                outcome = outcome,
                onClaim = {},
                onBack = {},
                backLabel = back,
            )
        }
    }

    @Test
    fun faucetReady() = faucet("faucet-ready")

    @Test
    fun faucetFromStake() = faucet("faucet-from-stake", back = "Back to the stake sheet")

    @Test
    fun faucetWaiting() = faucet("faucet-waiting", busy = true)

    @Test
    fun faucetFunded() = faucet("faucet-funded", outcome = FaucetOutcome.Funded("4Bx9pQ7Qk2abcdef"), back = "Back to the stake sheet")

    @Test
    fun faucetFundedFromMe() = faucet("faucet-funded-from-me", outcome = FaucetOutcome.Funded("4Bx9pQ7Qk2abcdef"))

    @Test
    fun faucetCannotPayOut() = faucet("faucet-cannot-pay-out", outcome = FaucetOutcome.CannotPayOut)

    @Test
    fun about() = compose.shoot("onboarding", "about") { AboutScreen(onTour = {}, onBack = {}, onDisconnect = {}) }
}
