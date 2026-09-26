package holdout.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import holdout.app.sitting.BankingController
import holdout.app.sitting.SittingScreen
import holdout.app.sitting.SittingViewModel
import holdout.app.storage.MmkvStore
import holdout.app.storage.SavedChainReading
import holdout.app.storage.Store
import holdout.app.streak.OfflineDays
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
    val offlineDays = remember { OfflineDays(store, streaks, DAILY_25) }
    val boot = remember { bootCount(context) }

    val walletViewModel: MainViewModel = viewModel()
    val wallet by walletViewModel.uiState.collectAsStateWithLifecycle()

    // Chain time, never the wall clock (research R7). The Daily 25 needs no
    // connection, so until the chain has been read once the screen says it does
    // not know how long is left rather than inventing a countdown.
    //
    // The last answer is saved, so a restart offline still knows the day — on
    // the same boot only, since the monotonic clock restarts with the phone.
    // Until the chain answers it is asked every 30 seconds, then every 10
    // minutes to keep drift small.
    val clock =
        remember {
            ChainClock { android.os.SystemClock.elapsedRealtime() }.also { c ->
                // Only on a boot the platform can name: an unknown boot (-1)
                // could be any boot, and an uptime from another is meaningless.
                store.chainReading()?.takeIf { boot >= 0 && it.boot == boot }?.let { c.restore(it.chainSeconds, it.slot, it.elapsedMillis) }
            }
        }
    LaunchedEffect(wallet.address) {
        if (wallet.address == null) return@LaunchedEffect
        while (true) {
            if (walletViewModel.refreshChainTime(clock)) {
                // Chain time and the monotonic clock sampled together.
                val elapsed = android.os.SystemClock.elapsedRealtime()
                val chainNow = clock.nowSeconds()
                val slot = clock.slot
                if (boot >= 0 && chainNow != null && slot != null) store.setChainReading(SavedChainReading(chainNow, slot, boot, elapsed))
            }
            delay(if (clock.hasReading) CHAIN_REFRESH_MILLIS else CHAIN_RETRY_MILLIS)
        }
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

    // Test builds only: a camera view with nothing drawn over it, for screen
    // recordings that train the knee classifier.
    var cleanScreen by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }

    val address = wallet.address
    if (address == null) {
        ConnectScreen(
            state = wallet,
            onConnect = { walletViewModel.connect(sender) },
            modifier = modifier,
        )
        return
    }

    val dayIndex = now?.let { HoldoutDay.indexOf(it) } ?: Store.UNKNOWN_DAY

    // The chain has answered: a Daily 25 done before it did moves onto the
    // day it was really done (FR-024b), and home reads it from there. Never
    // mid-sitting: that sitting is still banking under the unknown day.
    val known = now != null
    val idle = sittingDay == null
    LaunchedEffect(address, known, idle) {
        if (!idle) return@LaunchedEffect
        val chainNow = now ?: return@LaunchedEffect
        if (offlineDays.settle(address, boot, android.os.SystemClock.elapsedRealtime(), chainNow) != null) refreshToken += 1
    }

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
                // Debug builds record every sitting's landmarks for tuning;
                // `scripts/pull-traces.sh` fetches them over USB.
                traceDir = if (BuildConfig.DEBUG) java.io.File(context.filesDir, "traces") else null,
            )
        }
        DisposableEffect(sitting) { onDispose { sitting.release() } }
        SittingScreen(
            viewModel = sitting,
            cleanScreen = cleanScreen,
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
        // Finish anything a killed process left half-written before reading it.
        if (dayIndex != Store.UNKNOWN_DAY) BankingController.recover(store, streaks, address, Store.CHALLENGE, dayIndex, DAILY_25)
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
        onStart = {
            if (dayIndex == Store.UNKNOWN_DAY) offlineDays.sittingStarted(address, boot, android.os.SystemClock.elapsedRealtime())
            sittingDay = dayIndex
        },
        modifier = modifier,
        stakedTest = stakedTest,
        onStakedTestChange = if (BuildConfig.DEBUG) ({ stakedTest = it }) else null,
        cleanScreen = cleanScreen,
        onCleanScreenChange = if (BuildConfig.DEBUG) ({ cleanScreen = it }) else null,
        onSpikeTransaction = if (BuildConfig.DEBUG) ({ walletViewModel.sendDevnetTransaction(sender) }) else null,
        spikeMessage = wallet.message,
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

private const val CHAIN_RETRY_MILLIS = 30_000L
private const val CHAIN_REFRESH_MILLIS = 600_000L

/**
 * Which boot of the phone this is, to tell whether a monotonic-clock reading
 * saved earlier still means anything. -1 when the platform will not say, which
 * matches no saved boot, so nothing is restored on a guess.
 */
private fun bootCount(context: android.content.Context): Int = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
