package holdout.app.stake

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.Journey
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot

private const val SKR = 1_000_000uL
private const val DAY = 86_400L
private const val START = 1_791_176_400L

private fun cohort(form: Int, days: Int, leagueId: Int, joined: Long, held: ULong) = Holdout(
    address = SolanaPublicKey(ByteArray(32) { 3 }),
    cohort = CohortSnapshot(
        leagueId,
        1,
        START,
        START - 3_600,
        days,
        25,
        DAY,
        0,
        listOf(500uL * SKR, 1_250uL * SKR, 2_500uL * SKR, 5_000uL * SKR),
        memberCount = joined,
        heldStake = held,
        form = form,
    ),
    league = LeagueSnapshot(LeagueParams.judged(leagueId, START), nextIndex = 3),
    membership = null,
)

private val SPARK = cohort(LeagueParams.FORM_KNEES_ALLOWED, 7, LeagueParams.LEAGUE_7D, 14, 12_500uL * SKR)
private val HABIT = cohort(LeagueParams.FORM_TOES, 21, LeagueParams.LEAGUE_21D, 9, 9_000uL * SKR)

/** Two days, three hours and twenty minutes before joining closes. */
private const val NOW = START - 3_600 - 2 * DAY - 3 * 3_600 - 1_200

@Composable
private fun Sheet(h: Holdout = SPARK, skr: ULong = 10_000uL * SKR, lamports: Long = 1_000_000_000L, blocker: Journey.JoinBlocker? = null, busy: Boolean = false, at: Long = NOW) {
    HoldoutTheme {
        StakeSheet(
            h = h,
            skrBalance = skr,
            lamports = lamports,
            now = at,
            joinBlocker = blocker,
            busy = busy,
            message = null,
            messageIsWarning = false,
            onStake = {},
            onGetTokens = {},
            initialTier = 1,
            onSeeLobby = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeSparkPreview() = Sheet()

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeHabitPreview() = Sheet(h = HABIT)

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeShortOfSkrPreview() = Sheet(skr = 400uL * SKR)

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeShortOfSolPreview() = Sheet(lamports = 1_000L)

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeAlreadyInLobbyPreview() = Sheet(blocker = Journey.JoinBlocker.LobbyPlaceTaken)

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeClosingPreview() = Sheet(at = START - 3_600 - 510)

@Preview(showBackground = true, backgroundColor = 0xFF121B1D, heightDp = 1100)
@Composable
private fun StakeSigningPreview() = Sheet(busy = true)
