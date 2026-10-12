package holdout.app.stake

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.testutil.AnimationsOff
import holdout.app.testutil.SEEKER
import holdout.app.ui.HoldoutSheet
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
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The stake sheet fits a Seeker screen with nothing to scroll (owner, 12 Oct):
 * drawn as the shell draws it, in [HoldoutSheet] (at most 92 % of the 829 dp app
 * area), in every state a member meets — a Spark or Habit, the Hackathon round
 * in its last minutes, a comeback round, short of SKR or SOL, a held place.
 * Native graphics, so text is measured with real fonts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SEEKER)
class StakeSheetFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val animations = AnimationsOff()

    private val skr = 1_000_000uL
    private val day = 86_400L
    private val start = 1_791_176_400L
    private val now = start - 3_600 - 2 * day - 3 * 3_600 - 1_200

    private fun holdout(form: Int = LeagueParams.FORM_KNEES_ALLOWED, days: Int = 7, leagueId: Int = LeagueParams.LEAGUE_7D, dayLength: Long = day, cutoff: Long = start - 3_600, reps: Int = 25, joined: Long = 14) = Holdout(
        address = SolanaPublicKey(ByteArray(32) { 3 }),
        cohort = CohortSnapshot(leagueId, 1, start, cutoff, days, reps, dayLength, 0, listOf(460uL * skr, 1_170uL * skr, 2_340uL * skr, 4_690uL * skr), memberCount = joined, heldStake = 12_890uL * skr, form = form),
        league = if (leagueId < LeagueParams.TEST_LEAGUE_MIN) LeagueSnapshot(LeagueParams.judged(leagueId, start), nextIndex = 3) else null,
        membership = null,
    )

    /** The Hackathon round, a minute before it starts (its joining closes at the start). */
    private val hackathon = holdout(form = LeagueParams.FORM_KNEES_ALLOWED, days = 3, leagueId = LeagueParams.TEST_LEAGUE_MIN + 2, dayLength = 600, cutoff = start, reps = 5, joined = 4)
    private val lastMinute = start - 57

    private fun comeback(round: Holdout) = Comeback.Offer(
        slot = 0,
        lost = 4_600uL * skr,
        tier = 0,
        missed = start - 5 * day,
        firstRound = 1,
        forfeited = Comeback.Entry(SolanaPublicKey(ByteArray(32) { 8 }), round.cohort, null),
        rounds = listOf(Comeback.Round(start, start - 7 * day, Comeback.Entry(round.address, round.cohort, null), CohortStatus.Open)),
        held = null,
    )

    private data class Scene(
        val h: Holdout,
        val at: Long,
        val skrBalance: ULong = 10_000uL * 1_000_000uL,
        val lamports: Long = 1_000_000_000L,
        val blocker: Journey.JoinBlocker? = null,
        val offer: Comeback.Offer? = null,
    )

    private var scene by mutableStateOf<Scene?>(null)

    private fun overflow(next: Scene): Float {
        compose.runOnUiThread { scene = next }
        compose.waitForIdle()
        return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
            .maxOf { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
    }

    @Test
    fun `no state of the stake sheet scrolls on a Seeker`() {
        compose.setContent {
            HoldoutTheme {
                Box(Modifier.fillMaxSize()) {
                    scene?.let { sc ->
                        // A new sheet per scene, as the shell opens one per Holdout.
                        key(sc) {
                            HoldoutSheet(onDismiss = {}) {
                                StakeSheet(
                                    h = sc.h, skrBalance = sc.skrBalance, lamports = sc.lamports, now = sc.at, joinBlocker = sc.blocker, busy = false,
                                    message = null, messageIsWarning = true, onStake = {}, onGetTokens = {}, comeback = sc.offer, onSeeLobby = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        val states = linkedMapOf(
            "spark" to Scene(holdout(), now),
            "habit" to Scene(holdout(form = LeagueParams.FORM_TOES, days = 21, leagueId = LeagueParams.LEAGUE_21D), now),
            "hackathon, last minute" to Scene(hackathon, lastMinute),
            "hackathon comeback, last minute" to Scene(hackathon, lastMinute, offer = comeback(hackathon)),
            "spark, short of SKR" to Scene(holdout(), now, skrBalance = 100uL * skr),
            "spark, short of SOL" to Scene(holdout(), now, lamports = 0L),
            "spark, place held" to Scene(holdout(), now, blocker = Journey.JoinBlocker.LobbyPlaceTaken),
            "hackathon, short of SKR, last minute" to Scene(hackathon, lastMinute, skrBalance = 100uL * skr),
        )
        val px = states.mapValues { overflow(it.value) }
        assertEquals(emptyMap(), px.filterValues { it > 0f }, "states that scroll, by how many px at 480 dpi (3 px = 1 dp)")
    }
}
