package holdout.app.wallet

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.SEEKER
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first-open card fits one Seeker screen with nothing to scroll (FR-053, board Connect, 2026-10-14):
 * the headline, both product lines, a Spark and a Habit running each with its next round, the wrong-network
 * notice and Connect wallet, at 400 × 829 dp. Native graphics, so text is measured with real fonts and the
 * bullets' and dots' colours can be read from pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SEEKER)
class ConnectFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    private val day = 86_400L
    private val now = 1_791_176_400L + 3 * day

    private fun cohort(n: Int, start: Long, form: Int, days: Int, joined: Long, held: ULong = 0uL, pool: ULong = 0uL) = CohortSnapshot(
        leagueId = if (days == 7) LeagueParams.LEAGUE_7D else LeagueParams.LEAGUE_21D, index = n.toLong(), start = start, joinCutoff = start - 3_600, durationDays = days, repsPerDay = 25,
        dayLength = day, exercise = 0, chips = listOf(500_000_000uL, 1_250_000_000uL, 2_500_000_000uL, 5_000_000_000uL),
        memberCount = joined, heldStake = held, pool = pool, form = form,
    )

    private fun holdout(c: CohortSnapshot, boost: ULong = 0uL) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { (c.index + 1).toByte() }),
        cohort = c,
        league = LeagueSnapshot(LeagueParams.judged(c.leagueId, c.start), nextIndex = 0, boostReserve = boost),
        membership = null,
    )

    // The board's two rows: a running Spark and a running Habit, each with the next round open under it.
    private val live = listOf(
        holdout(cohort(1, now - 2 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 20, held = 12_500_000_000uL, pool = 6_125_000_000uL)),
        holdout(cohort(2, now + 5 * day, LeagueParams.FORM_KNEES_ALLOWED, 7, joined = 14, held = 12_500_000_000uL), boost = 480_000_000uL),
        holdout(cohort(3, now - 11 * day, LeagueParams.FORM_TOES, 21, joined = 312, pool = 48_200_000_000uL)),
        holdout(cohort(4, now + 5 * day, LeagueParams.FORM_TOES, 21, joined = 9, held = 9_000_000_000uL), boost = 1_200_000_000uL),
    )

    private fun show(message: String?) = compose.setContent {
        HoldoutTheme { ConnectScreen(busy = false, message = message, noWallet = false, live = live, now = now, onConnect = {}) }
    }

    private fun overflowPx(): Float {
        compose.waitForIdle()
        return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
            .maxOf { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
    }

    @Test
    fun `two running Holdouts with their next rounds and the wrong-network notice do not scroll on a Seeker`() {
        show(WalletRepository.WRONG_NETWORK)

        assertEquals(0f, overflowPx(), "px to scroll at 480 dpi (3 px = 1 dp)")
        // Everything the member needs is on screen: the notice and the button end inside the 829 dp.
        assertTrue(compose.onNodeWithText("Connect wallet").getBoundsInRoot().bottom <= 829.dp)
        assertTrue(compose.onNodeWithText("No wallet yet?").getBoundsInRoot().bottom <= 829.dp)
    }

    @Test
    fun `the same two rows without a notice do not scroll either`() {
        show(null)
        assertEquals(0f, overflowPx(), "px to scroll with no notice")
    }

    @Test
    fun `both product bullets are Accent and each running Holdout has a Success dot`() {
        show(null)
        compose.waitForIdle()

        val bullets = compose.onAllNodesWithTag("product-dot")
        assertEquals(2, bullets.fetchSemanticsNodes().size)
        for (i in 0..1) {
            val pixels = bullets[i].captureToImage().toPixelMap()
            assertEquals(HoldoutColors.Accent, pixels[pixels.width / 2, pixels.height / 2], "bullet $i")
        }
        val dots = compose.onAllNodesWithTag("running-dot")
        assertEquals(2, dots.fetchSemanticsNodes().size)
        for (i in 0..1) {
            val pixels = dots[i].captureToImage().toPixelMap()
            assertEquals(HoldoutColors.Success, pixels[pixels.width / 2, pixels.height / 2], "running dot $i")
        }
    }
}
