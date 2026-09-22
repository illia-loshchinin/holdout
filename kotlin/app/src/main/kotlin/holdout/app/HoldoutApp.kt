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

    var inSitting by remember { mutableStateOf(false) }
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

    val now = clock.nowSeconds()
    val dayIndex = now?.let { HoldoutDay.indexOf(it) } ?: LOCAL_DAY_UNKNOWN

    if (inSitting) {
        val sitting = remember(address, dayIndex, refreshToken) {
            SittingViewModel(
                store = store,
                streaks = streaks,
                wallet = address,
                scope = Store.CHALLENGE,
                dayIndex = dayIndex,
                target = DAILY_25,
                chainSecondsNow = { clock.nowSeconds() },
            )
        }
        SittingScreen(
            viewModel = sitting,
            onFinished = {
                inSitting = false
                refreshToken += 1
            },
            onCancelled = {
                inSitting = false
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
        onStart = { inSitting = true },
        modifier = modifier,
    )
}

private const val DAILY_25 = 25

/**
 * The day index used before the chain has been read once.
 *
 * It is a real, distinct slot rather than "today": a day's progress must never
 * be filed under a date the app only guessed at, and merged with a real one
 * later. The first successful chain read moves the member onto the true day,
 * and this slot is simply never looked at again.
 */
private const val LOCAL_DAY_UNKNOWN = Long.MIN_VALUE + 1
