package holdout.app.screenshots

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.home.HoldoutsBlock
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.home.OfferCardState
import holdout.app.lobby.LobbyScreen
import holdout.app.stake.SeekerGateSheet
import holdout.app.stake.SeekerStatus
import holdout.app.stake.StakeSheet
import holdout.app.testutil.AnimationsOff
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stake sheet over Today, with the Seeker gate's states, and the lobby, open and locked. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class StakeAndLobbyShots {
    @get:Rule(order = 0)
    val animationsOff = AnimationsOff()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L

    /** Two days, three hours before joining closes. */
    private val joinNow = start - 3_600 - 2 * day - 3 * 3_600 - 1_200

    private val openNow = start - 2 * day - 4 * 3_600

    /** Forty-one minutes before the start: joining closed 23 minutes ago. */
    private val lockedNow = start - 41 * 60 - 10

    private fun holdout(form: Int = LeagueParams.FORM_KNEES_ALLOWED, days: Int = 7, leagueId: Int = LeagueParams.LEAGUE_7D, joined: Long = 14, held: ULong = 12_500uL * skr, member: Boolean = false) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { 3 }),
        cohort = CohortSnapshot(leagueId, 1, start, start - 3_600, days, 25, day, 0, listOf(500uL * skr, 1_250uL * skr, 2_500uL * skr, 5_000uL * skr), memberCount = joined, heldStake = held, form = form),
        league = LeagueSnapshot(LeagueParams.judged(leagueId, start), nextIndex = 3).copy(boostReserve = 480uL * skr),
        membership = if (member) MembershipSnapshot(SolanaPublicKey(ByteArray(32) { 9 }), 1, 1_250uL * skr, start - 3 * day, 0, MembershipSnapshot.State.Joined) else null,
    )

    private val behind = HomeState(
        remainingToday = 25,
        bankedToday = 0,
        target = 25,
        completedToday = false,
        streak = 12,
        secondsLeftToday = 14_760,
        holdouts = HoldoutsBlock.Open(
            listOf(
                OfferCardState("spark", "Spark · 7-day", "Light it up · knees allowed", "12,500 SKR", "at stake · 14 joined", "Joining closes in 2d 3h", "Prize boost: up to 480 SKR", false, 0),
            ),
        ),
    )

    private fun stake(
        name: String,
        h: Holdout = holdout(),
        skrBalance: ULong = 10_000uL * skr,
        lamports: Long = 1_000_000_000L,
        tier: Int = 1,
    ) = compose.shoot("stake", name) {
        Sheeted(behind = { HomeScreen(behind, {}, {}, {}, {}) }) {
            StakeSheet(
                h = h,
                skrBalance = skrBalance,
                lamports = lamports,
                now = joinNow,
                joinBlocker = null,
                busy = false,
                message = null,
                messageIsWarning = true,
                onStake = {},
                onGetTokens = {},
                initialTier = tier,
                showNotificationReason = true,
            )
        }
    }

    @Test
    fun spark() = stake("spark")

    @Test
    fun habit() = stake("habit", holdout(LeagueParams.FORM_TOES, 21, LeagueParams.LEAGUE_21D, joined = 9, held = 9_000uL * skr))

    @Test
    fun shortOfSkr() = stake("short-of-skr", skrBalance = 400uL * skr)

    private fun gate(name: String, status: SeekerStatus) = compose.shoot("stake", name) {
        Sheeted(behind = { HomeScreen(behind, {}, {}, {}, {}) }) {
            SeekerGateSheet(status, onGetSeeker = {}, onRetry = {}, onNotNow = {})
        }
    }

    @Test
    fun gateChecking() = gate("gate-checking", SeekerStatus.Checking)

    @Test
    fun gateNotASeeker() = gate("gate-not-a-seeker", SeekerStatus.NotSeeker)

    @Test
    fun gateCouldNotCheck() = gate("gate-could-not-check", SeekerStatus.CouldNotCheck)

    private fun lobby(name: String, now: Long, member: Boolean = true, left: String? = null) = compose.shoot("lobby", name, heightDp = 1100) {
        LobbyScreen(holdout(member = member), now, busy = false, message = null, messageIsWarning = true, onLeave = {}, onRoster = {}, onBack = {}, leftStake = left, notificationsOn = true)
    }

    @Test
    fun lobbyOpen() = lobby("open", openNow)

    @Test
    fun lobbyLocked() = lobby("locked", lockedNow)

    @Test
    fun lobbyVisitor() = lobby("visitor", openNow, member = false)
}
