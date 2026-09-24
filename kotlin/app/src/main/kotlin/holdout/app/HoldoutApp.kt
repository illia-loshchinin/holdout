package holdout.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.sitting.SittingScreen
import holdout.app.sitting.SittingViewModel
import holdout.app.storage.MmkvStore
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.wallet.ConnectScreen
import holdout.core.ChainClock
import holdout.core.HoldoutDay
import holdout.repcounter.Stakes
import kotlinx.coroutines.delay

/**
 * The shell, as far as the Daily 25 needs it (US1 partial, US5A).
 *
 * ```
 * connect wallet  →  home (today's 25)  →  sitting  →  home
 * ```
 *
 * **A wallet comes first, even for the free Daily 25** (FR-001a). One identity
 * from the first tap; guest mode was considered and dropped. Everything the
 * Daily 25 does after that works with no connection at all (FR-024b).
 *
 * The ladder, the faucet, the tour and the staked Holdouts are the rest of US1
 * and US6 — T042, T043, T044, T054 onward. This is deliberately the shortest
 * path from a cold install to 25 verified push-ups.
 */
@Composable
fun HoldoutApp(sender: ActivityResultSender, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store: Store = remember { MmkvStore.create(context) }
    val streaks = remember { StreakStore(store) }

    val walletViewModel: MainViewModel = viewModel()
    val wallet by walletViewModel.uiState.collectAsStateWithLifecycle()

    // Chain time, never the wall clock (research R7). The Daily 25 needs no
    // connection, so until the chain has been read once the screen says it does
    // not know how long is left rather than inventing a countdown.
    val clock = remember { ChainClock { android.os.SystemClock.elapsedRealtime() } }
    LaunchedEffect(wallet.address) {
        if (wallet.address != null) walletViewModel.refreshChainTime(clock)
    }

    // The clock is a plain object, not Compose state: reading it in the body
    // gives whatever it said on the last recomposition. So it is sampled here
    // once a second. Without this, home read the day before the chain had
    // answered — the "not known yet" slot, always empty — and kept showing it
    // after the chain did, so a member with ten banked reps was shown 25 to go.
    // It also keeps the "left today" countdown moving.
    var now by remember { mutableStateOf(clock.nowSeconds()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.nowSeconds()
            delay(1_000)
        }
    }

    /**
     * The day a sitting is filed under, fixed when it starts. The day can change
     * under a sitting — the chain answering late, or the 05:00 UTC turnover —
     * and a sitting whose day changed would be rebuilt from scratch, losing its
     * loose reps and banking the rest under a different date.
     */
    var sittingDay by remember { mutableStateOf<Long?>(null) }

    // Test builds only: sittings under staked rules, filed under their own
    // scope so a test run never banks into — or completes — the real Daily 25.
    var stakedTest by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }

    val address = wallet.address
    if (address == null) {
        ConnectScreen(
            state = wallet,
            onConnect = { walletViewModel.connect(sender) },
            modifier = modifier,
            onSpikeTransaction = { walletViewModel.sendDevnetTransaction(sender) },
        )
        return
    }

    val dayIndex = now?.let { HoldoutDay.indexOf(it) } ?: LOCAL_DAY_UNKNOWN

    val inSittingDay = sittingDay
    if (inSittingDay != null) {
        val sitting = remember(address, inSittingDay, refreshToken, stakedTest) {
            SittingViewModel(
                store = store,
                streaks = streaks,
                wallet = address,
                scope = if (stakedTest) TEST_STAKED_SCOPE else Store.CHALLENGE,
                dayIndex = inSittingDay,
                target = DAILY_25,
                chainSecondsNow = { clock.nowSeconds() },
                // Free, so any placement. Staked sittings arrive with the
                // cohorts (T054 onward) and pass Stakes.STAKED: side or 45°.
                stakes = if (stakedTest) Stakes.STAKED else Stakes.DAILY,
            )
        }
        SittingScreen(
            viewModel = sitting,
            onFinished = {
                sittingDay = null
                refreshToken += 1
            },
            onCancelled = {
                sittingDay = null
                refreshToken += 1
            },
            modifier = modifier,
        )
        return
    }

    val banked = remember(address, dayIndex, refreshToken) {
        store.bankedReps(address, Store.CHALLENGE, dayIndex)
    }
    val complete = remember(address, dayIndex, refreshToken) {
        store.isDayComplete(address, Store.CHALLENGE, dayIndex)
    }
    val streak = remember(address, dayIndex, refreshToken) { streaks.read(address, dayIndex) }

    HomeScreen(
        state = HomeState(
            remainingToday = (DAILY_25 - banked).coerceAtLeast(0),
            bankedToday = banked,
            target = DAILY_25,
            completedToday = complete,
            streak = streak.current,
            secondsLeftToday = now?.let { HoldoutDay.secondsLeftIn(it) },
        ),
        onStart = { sittingDay = dayIndex },
        modifier = modifier,
        stakedTest = stakedTest,
        onStakedTestChange = if (BuildConfig.DEBUG) ({ stakedTest = it }) else null,
        onResetToday = if (BuildConfig.DEBUG) {
            {
                // Both scopes: the real Daily 25 and the staked-rules test runs.
                // The streak is left alone — re-finishing today cannot add to it twice.
                store.clearDay(address, Store.CHALLENGE, dayIndex)
                store.clearDay(address, TEST_STAKED_SCOPE, dayIndex)
                refreshToken += 1
            }
        } else {
            null
        },
    )
}

private const val DAILY_25 = 25

/** Where test sittings under staked rules bank, apart from the real Daily 25. */
private const val TEST_STAKED_SCOPE = "test-staked"

/**
 * The day index used before the chain has been read once.
 *
 * It is a real, distinct slot rather than "today": a day's progress must never
 * be filed under a date the app only guessed at, and merged with a real one
 * later. The first successful chain read moves the member onto the true day,
 * and this slot is simply never looked at again.
 */
private const val LOCAL_DAY_UNKNOWN = Long.MIN_VALUE + 1
