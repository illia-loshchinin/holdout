package holdout.app.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Holdout
import holdout.app.ui.theme.HoldoutTheme
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import holdout.core.LeagueSnapshot
import holdout.core.MembershipSnapshot
import holdout.core.Stage

private const val SKR = 1_000_000uL
private const val DAY = 86_400L
private const val START = 1_791_176_400L

/** Day 12 of 21, 4h 06m before the day's deadline. */
private const val NOW = START + 12 * DAY - 14_760 - 1

private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

private fun member(n: Int, days: Int) = MembershipSnapshot(key(n), 1, 1_250uL * SKR, START - DAY, days, MembershipSnapshot.State.Joined)

private val ME = member(1, 11)
private val ROSTER = listOf(ME, member(2, 11), member(3, 11), member(4, 11), member(5, 10))

private val HABIT = Holdout(
    address = key(9),
    cohort = CohortSnapshot(
        LeagueParams.LEAGUE_21D,
        1,
        START,
        START - 3_600,
        21,
        25,
        DAY,
        0,
        listOf(500uL * SKR, 1_250uL * SKR, 2_500uL * SKR, 5_000uL * SKR),
        memberCount = 5,
        pool = 100uL * SKR,
        form = LeagueParams.FORM_TOES,
    ),
    league = LeagueSnapshot(LeagueParams.judged(LeagueParams.LEAGUE_21D, START), nextIndex = 3, boostReserve = 1_200uL * SKR),
    membership = ME,
)

@Composable
private fun Today(
    banked: Int = 0,
    todayDone: Boolean = false,
    pendingDay: Int? = null,
    autoSendTried: Boolean = false,
    offline: Boolean = false,
    canPayFees: Boolean = true,
    at: Long = NOW,
    dayIndex: Int = 11,
    crowd: List<MembershipSnapshot> = ROSTER,
) {
    HoldoutTheme {
        CohortHome(
            h = HABIT,
            members = crowd,
            stage = Stage.Active(dayIndex, todayDone),
            now = at,
            banked = banked,
            offline = offline,
            pendingDay = pendingDay,
            autoSendTried = autoSendTried,
            busy = false,
            message = null,
            messageIsWarning = false,
            onStart = {},
            onSubmit = {},
            onEvent = {},
            onGetTokens = {},
            streak = 12,
            canPayFees = canPayFees,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayToDoPreview() = Today()

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayPartwayPreview() = Today(banked = 10)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayLastTenPreview() = Today(banked = 10, at = START + 12 * DAY - 1 - 510)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayOfflinePreview() = Today(offline = true)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayWaitingPreview() = Today(pendingDay = 11, offline = true)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayTapToSubmitPreview() = Today(pendingDay = 11, autoSendTried = true)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayShortOfSolPreview() = Today(pendingDay = 11, canPayFees = false)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodaySubmittedPreview() = Today(todayDone = true)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayLastDaySubmittedPreview() = Today(todayDone = true, dayIndex = 20, at = START + 21 * DAY - 14_760 - 1)

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 900)
@Composable
private fun TodayNobodyDroppedPreview() = Today(crowd = ROSTER.take(4))
