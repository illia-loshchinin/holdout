package holdout.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import holdout.app.about.AboutScreen
import holdout.app.about.WalkthroughScreen
import holdout.app.chain.Holdout
import holdout.app.claim.ClaimScreen
import holdout.app.cohort.CohortDetailScreen
import holdout.app.cohort.ForfeitScreen
import holdout.app.faucet.FaucetSheet
import holdout.app.holdout.HoldoutViewModel
import holdout.app.home.CohortHome
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.ladder.HoldoutOffer
import holdout.app.ladder.LadderScreen
import holdout.app.lobby.LobbyScreen
import holdout.app.roster.RosterScreen
import holdout.app.sitting.BankingController
import holdout.app.sitting.SittingScreen
import holdout.app.sitting.SittingViewModel
import holdout.app.stake.StakeSheet
import holdout.app.storage.MmkvStore
import holdout.app.storage.SavedChainReading
import holdout.app.storage.Store
import holdout.app.streak.OfflineDays
import holdout.app.streak.StreakStore
import holdout.app.tour.TourScreen
import holdout.app.ui.Format
import holdout.app.ui.HoldoutCard
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.wallet.ConnectScreen
import holdout.app.wallet.WalletRepository
import holdout.core.ChainClock
import holdout.core.HoldoutDay
import holdout.core.Journey
import holdout.core.LeagueParams
import holdout.core.Stage
import holdout.repcounter.Stakes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Every screen past the connect screen (contracts/android-app §5). Holdouts are named by cohort address. */
private sealed interface Route {
    data object Home : Route
    data object Ladder : Route
    data object Faucet : Route
    data object About : Route
    data object Walkthrough : Route
    data object Tour : Route
    data class Stake(val cohort: SolanaPublicKey) : Route
    data class Lobby(val cohort: SolanaPublicKey) : Route
    data class Detail(val cohort: SolanaPublicKey) : Route
    data class Roster(val cohort: SolanaPublicKey) : Route
    data class Forfeit(val cohort: SolanaPublicKey) : Route
    data class Claim(val cohort: SolanaPublicKey) : Route
}

/** A sitting in progress: the free Daily 25, or one day of a Holdout (T065). */
private sealed interface Sitting {
    /** Filed under the global day fixed when it started. */
    data class Daily25(val day: Long) : Sitting

    /** One cohort day: target and exercise read from the cohort, never constants (FR-014, H30). */
    data class CohortDay(val holdout: Holdout, val day: Int) : Sitting
}

/**
 * The shell.
 *
 * ```
 * connect wallet → (tour, first run) → home ─┬─ the Daily 25 ─→ sitting
 *                                            ├─ ladder → stake → lobby
 *                                            ├─ Holdout running → cohort day sitting → submit
 *                                            └─ forfeit · settling · claim · roster · about
 * ```
 *
 * **A wallet comes first, even for the free Daily 25** (FR-001a); only the
 * recorded walkthrough plays before it. Everything the Daily 25 does after
 * that works with no connection at all (FR-024b). Which Holdout screen shows
 * is decided by `Journey.stage` from chain state and chain time.
 */
@Composable
fun HoldoutApp(sender: ActivityResultSender, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store: Store = remember { MmkvStore.create(context) }
    val streaks = remember { StreakStore(store) }
    val offlineDays = remember { OfflineDays(store, streaks, DAILY_25) }
    val boot = remember { bootCount(context) }
    val scope = rememberCoroutineScope()

    val clockViewModel: MainViewModel = viewModel()
    val holdouts: HoldoutViewModel = viewModel { HoldoutViewModel(store) }
    val chain by holdouts.state.collectAsStateWithLifecycle()
    val rosters by holdouts.rosters.collectAsStateWithLifecycle()

    // The wallet a previous run connected is restored with its auth token, so a
    // restart does not ask again (contracts/android-app §3).
    var address by remember { mutableStateOf(holdouts.wallet.restored) }
    var connecting by remember { mutableStateOf(false) }
    var connectMessage by remember { mutableStateOf<String?>(null) }
    var noWallet by remember { mutableStateOf(false) }

    // Chain time, never the wall clock (research R7). The last answer is saved,
    // so a restart offline still knows the day — on the same boot only, since
    // the monotonic clock restarts with the phone.
    val clock =
        remember {
            ChainClock { android.os.SystemClock.elapsedRealtime() }.also { c ->
                store.chainReading()?.takeIf { boot >= 0 && it.boot == boot }?.let { c.restore(it.chainSeconds, it.slot, it.elapsedMillis) }
            }
        }
    LaunchedEffect(Unit) {
        while (true) {
            if (clockViewModel.refreshChainTime(clock)) {
                val elapsed = android.os.SystemClock.elapsedRealtime()
                val chainNow = clock.nowSeconds()
                val slot = clock.slot
                if (boot >= 0 && chainNow != null && slot != null) store.setChainReading(SavedChainReading(chainNow, slot, boot, elapsed))
            }
            delay(if (clock.hasReading) CHAIN_REFRESH_MILLIS else CHAIN_RETRY_MILLIS)
        }
    }
    // The clock is a plain object, not Compose state: sampled once a second.
    var now by remember { mutableStateOf(clock.nowSeconds()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.nowSeconds()
            delay(1_000)
        }
    }
    LaunchedEffect(address) { holdouts.watch(address) }

    var route by remember { mutableStateOf<Route>(Route.Home) }
    var sitting by remember { mutableStateOf<Sitting?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    var stakedTest by remember { mutableStateOf(false) }
    var cleanScreen by remember { mutableStateOf(false) }
    var beforeWallet by remember { mutableStateOf(false) }

    val me = address
    if (me == null) {
        if (beforeWallet) {
            BackHandler { beforeWallet = false }
            WalkthroughScreen(onBack = { beforeWallet = false }, modifier = modifier)
            return
        }
        ConnectScreen(
            busy = connecting,
            message = connectMessage,
            noWallet = noWallet,
            live = now?.let { n -> chain.view?.live(n) }.orEmpty(),
            now = now,
            onConnect = {
                scope.launch {
                    connecting = true
                    connectMessage = null
                    when (val result = holdouts.wallet.connect(sender)) {
                        is WalletRepository.ConnectResult.Connected -> {
                            noWallet = false
                            address = result.address
                        }
                        WalletRepository.ConnectResult.NoWallet -> noWallet = true
                        is WalletRepository.ConnectResult.Failed -> connectMessage = result.message
                    }
                    connecting = false
                }
            },
            onWalkthrough = { beforeWallet = true },
            modifier = modifier,
        )
        return
    }
    val wallet = me.base58()

    if (!store.hasSeenTour(wallet) || route == Route.Tour) {
        TourScreen(
            onDone = {
                store.setSeenTour(wallet)
                route = Route.Home
                refreshToken += 1
            },
            modifier = modifier,
        )
        return
    }

    val dayIndex = now?.let { HoldoutDay.indexOf(it) } ?: Store.UNKNOWN_DAY

    // The chain has answered: a Daily 25 done before it did moves onto the day
    // it was really done (FR-024b). Never mid-sitting.
    val known = now != null
    val idle = sitting == null
    LaunchedEffect(wallet, known, idle) {
        if (!idle) return@LaunchedEffect
        val chainNow = now ?: return@LaunchedEffect
        if (offlineDays.settle(wallet, boot, android.os.SystemClock.elapsedRealtime(), chainNow) != null) refreshToken += 1
    }

    // ---- a sitting -------------------------------------------------------------
    when (val s = sitting) {
        is Sitting.Daily25 -> {
            val vm = remember(wallet, s.day, refreshToken, stakedTest) {
                SittingViewModel(
                    store = store,
                    streaks = streaks,
                    wallet = wallet,
                    scope = if (stakedTest) TEST_STAKED_SCOPE else Store.CHALLENGE,
                    dayIndex = s.day,
                    target = DAILY_25,
                    chainSecondsNow = { clock.nowSeconds() },
                    stakes = if (stakedTest) Stakes.STAKED else Stakes.DAILY,
                    traceDir = if (BuildConfig.DEBUG) java.io.File(context.filesDir, "traces") else null,
                )
            }
            DisposableEffect(vm) { onDispose { vm.release() } }
            val done = {
                sitting = null
                refreshToken += 1
            }
            SittingScreen(viewModel = vm, cleanScreen = cleanScreen, onFinished = done, onCancelled = { done() }, modifier = modifier)
            return
        }
        is Sitting.CohortDay -> {
            val h = s.holdout
            val cohortScope = cohortScope(h)
            val vm = remember(wallet, h.address, s.day, refreshToken) {
                SittingViewModel(
                    store = store,
                    streaks = streaks,
                    wallet = wallet,
                    scope = cohortScope,
                    dayIndex = s.day.toLong(),
                    target = h.cohort.repsPerDay,
                    chainSecondsNow = { clock.nowSeconds() },
                    // Every sitting of a staked Holdout: 45° only, from the toes (FR-021a).
                    stakes = Stakes.STAKED,
                    traceDir = if (BuildConfig.DEBUG) java.io.File(context.filesDir, "traces") else null,
                )
            }
            DisposableEffect(vm) { onDispose { vm.release() } }
            val done = {
                sitting = null
                refreshToken += 1
                if (store.isDayComplete(wallet, cohortScope, s.day.toLong())) {
                    // A completed cohort day is that day's training for the streak too, so
                    // nobody trains twice (FR-050) — judged leagues only, whose days are real days.
                    if (!h.isTest && dayIndex != Store.UNKNOWN_DAY) streaks.recordCompletedDay(wallet, dayIndex)
                    holdouts.submitDay(sender, h, s.day, h.cohort.repsPerDay)
                }
            }
            SittingScreen(viewModel = vm, cleanScreen = cleanScreen, onFinished = done, onCancelled = { done() }, modifier = modifier)
            return
        }
        null -> Unit
    }

    val chainNow = now
    val view = chain.view
    fun holdout(address: SolanaPublicKey): Holdout? = view?.holdouts?.firstOrNull { it.address == address }
    BackHandler(enabled = route != Route.Home) { route = Route.Home }
    val goHome = { route = Route.Home }

    // ---- routes that need a Holdout and chain time -----------------------------
    val routed = route
    val target = when (routed) {
        is Route.Stake -> holdout(routed.cohort)
        is Route.Lobby -> holdout(routed.cohort)
        is Route.Detail -> holdout(routed.cohort)
        is Route.Roster -> holdout(routed.cohort)
        is Route.Forfeit -> holdout(routed.cohort)
        is Route.Claim -> holdout(routed.cohort)
        else -> null
    }
    if (target != null && chainNow != null) {
        LaunchedEffect(target.address) {
            while (true) {
                holdouts.loadRoster(target.address)
                delay(ROSTER_REFRESH_MILLIS)
            }
        }
        val members = rosters[target.address]
        val stage = target.stage(chainNow)
        // A join that landed moves the stake sheet on to the lobby; a leave, the lobby back home.
        LaunchedEffect(routed, stage) {
            if (routed is Route.Stake && (stage == Stage.Lobby || stage == Stage.LobbyLocked)) route = Route.Lobby(target.address)
            if (routed is Route.Lobby && stage == Stage.NotJoined) route = Route.Home
        }
        when (routed) {
            is Route.Stake -> StakeSheet(
                h = target,
                skrBalance = view?.skr ?: 0uL,
                now = chainNow,
                joinBlocker = Journey.joinBlocker(target.cohort, view?.profile, view?.runningEnd(chainNow), chainNow),
                busy = chain.busy,
                message = chain.message,
                messageIsWarning = chain.messageIsWarning,
                onStake = { tier -> holdouts.join(sender, target, tier, chainNow) },
                onGetTokens = { route = Route.Faucet },
                onBack = goHome,
                modifier = modifier,
            )
            is Route.Lobby -> LobbyScreen(target, chainNow, chain.busy, chain.message, chain.messageIsWarning, { holdouts.leave(sender, target) }, { route = Route.Roster(target.address) }, goHome, modifier)
            is Route.Detail -> CohortDetailScreen(target, members, stage, chainNow, chain.lastClaimSignature, { route = Route.Roster(target.address) }, goHome, modifier)
            is Route.Roster -> RosterScreen(target, members, wallet, chainNow, goHome, modifier)
            is Route.Forfeit -> ForfeitScreen(
                h = target,
                stage = stage as? Stage.Forfeited ?: Stage.Forfeited(target.membership?.daysCompleted ?: 0, recorded = false),
                streak = streaks.read(wallet, dayIndex).current,
                onDaily25 = goHome,
                onNextHoldout = { route = Route.Ladder },
                onBack = goHome,
                modifier = modifier,
            )
            is Route.Claim -> ClaimScreen(target, stage, chain.busy, chain.message, chain.messageIsWarning, chain.lastClaimSignature, { holdouts.claim(sender, target) }, goHome, modifier)
            else -> Unit
        }
        return
    }

    when (routed) {
        Route.Ladder -> {
            LadderScreen(
                open = chainNow?.let { view?.openToJoin(it) }.orEmpty(),
                qualified = view?.profile?.qualified == true,
                now = chainNow ?: 0,
                onDaily25 = goHome,
                onHoldout = { route = Route.Stake(it.address) },
                onBack = goHome,
                modifier = modifier,
            )
            return
        }
        Route.Faucet -> {
            FaucetSheet(
                skr = view?.skr ?: 0uL,
                lamports = view?.lamports ?: 0,
                maxTopTier = view?.config?.maxTopTier ?: 0uL,
                claimedToday = chainNow != null && view?.profile?.claimedFaucetOn(HoldoutDay.indexOf(chainNow)) == true,
                now = chainNow ?: 0,
                busy = chain.busy,
                outcome = chain.lastFaucet,
                onClaim = { chainNow?.let { holdouts.claimFaucet(sender, it) } },
                onBack = {
                    holdouts.clearMessage()
                    route = Route.Home
                },
                modifier = modifier,
            )
            return
        }
        Route.About -> {
            AboutScreen(onWalkthrough = { route = Route.Walkthrough }, onTour = { route = Route.Tour }, onBack = goHome, modifier = modifier)
            return
        }
        Route.Walkthrough -> {
            WalkthroughScreen(onBack = { route = Route.About }, modifier = modifier)
            return
        }
        else -> Unit
    }

    // ---- home --------------------------------------------------------------------
    // Finish anything a killed process left half-written, then read the day again.
    LaunchedEffect(wallet, dayIndex) {
        if (dayIndex != Store.UNKNOWN_DAY && BankingController.recover(store, streaks, wallet, Store.CHALLENGE, dayIndex, DAILY_25)) refreshToken += 1
    }

    // A Holdout running with the member standing in it leads home (PRD §10.1a).
    val active = chainNow?.let { n -> view?.mine(n)?.firstNotNullOfOrNull { h -> (h.stage(n) as? Stage.Active)?.let { h to it } } }
    if (active != null && chainNow != null) {
        val (h, stage) = active
        LaunchedEffect(h.address) {
            while (true) {
                holdouts.loadRoster(h.address)
                delay(ROSTER_REFRESH_MILLIS)
            }
        }
        val cohortScope = cohortScope(h)
        val banked = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.bankedReps(wallet, cohortScope, stage.dayIndex.toLong()) }
        val pending = remember(wallet, h.address, refreshToken, chain.busy) { holdouts.pendingDay(h) }
        val locallyDone = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.isDayComplete(wallet, cohortScope, stage.dayIndex.toLong()) }
        CohortHome(
            h = h,
            members = rosters[h.address],
            stage = stage,
            now = chainNow,
            banked = banked,
            pendingDay = pending ?: stage.dayIndex.takeIf { locallyDone && !stage.todayDone },
            busy = chain.busy,
            message = chain.message,
            messageIsWarning = chain.messageIsWarning,
            onStart = {
                // An exercise this build does not know is refused, never guessed (FR-014, H30).
                if (h.cohort.exercise != LeagueParams.EXERCISE_PUSHUP) return@CohortHome
                sitting = Sitting.CohortDay(h, stage.dayIndex)
            },
            onSubmit = { day -> holdouts.submitDay(sender, h, day, h.cohort.repsPerDay) },
            onDetail = { route = Route.Detail(h.address) },
            onRoster = { route = Route.Roster(h.address) },
            onDaily25 = { sitting = Sitting.Daily25(dayIndex) },
            modifier = modifier,
        )
        return
    }

    val banked = remember(wallet, dayIndex, refreshToken) { store.bankedReps(wallet, Store.CHALLENGE, dayIndex) }
    val complete = remember(wallet, dayIndex, refreshToken) { store.isDayComplete(wallet, Store.CHALLENGE, dayIndex) }
    val streak = remember(wallet, dayIndex, refreshToken) { streaks.read(wallet, dayIndex) }

    HomeScreen(
        state = HomeState(
            remainingToday = (DAILY_25 - banked).coerceAtLeast(0),
            bankedToday = banked,
            target = DAILY_25,
            completedToday = complete,
            streak = streak.current,
            secondsLeftToday = chainNow?.let { HoldoutDay.secondsLeftIn(it) },
        ),
        onStart = {
            if (dayIndex == Store.UNKNOWN_DAY) offlineDays.sittingStarted(wallet, boot, android.os.SystemClock.elapsedRealtime())
            sitting = Sitting.Daily25(dayIndex)
        },
        modifier = modifier,
        stakedTest = stakedTest,
        onStakedTestChange = if (BuildConfig.DEBUG) ({ stakedTest = it }) else null,
        cleanScreen = cleanScreen,
        onCleanScreenChange = if (BuildConfig.DEBUG) ({ cleanScreen = it }) else null,
        onResetToday = if (BuildConfig.DEBUG) {
            {
                store.clearDay(wallet, Store.CHALLENGE, dayIndex)
                store.clearDay(wallet, TEST_STAKED_SCOPE, dayIndex)
                refreshToken += 1
            }
        } else {
            null
        },
        below = {
            if (chainNow == null || view == null) {
                SectionLabel(if (chain.offline) "Devnet unreachable — the Daily 25 still works" else "Reading devnet…")
                return@HomeScreen
            }
            if (view.config == null) {
                SectionLabel("Holdouts are not live on devnet yet")
                return@HomeScreen
            }
            // The member's own Holdouts that are not running today: lobby, forfeit, settling, claim.
            view.mine(chainNow).forEach { h ->
                val stage = h.stage(chainNow)
                val (label, next) = when (stage) {
                    Stage.Lobby, Stage.LobbyLocked -> "In the lobby · starts in ${Format.countdown(h.cohort.start - chainNow)}" to Route.Lobby(h.address)
                    is Stage.Forfeited -> "Out after ${stage.daysLasted} day${if (stage.daysLasted == 1) "" else "s"}" to Route.Forfeit(h.address)
                    Stage.Settling -> "You finished · settlement in progress" to Route.Detail(h.address)
                    is Stage.ReadyToClaim -> "You finished · claim ${Format.skr(stage.amount)}" to Route.Claim(h.address)
                    else -> null to null
                }
                if (label != null && next != null) {
                    HoldoutCard(Modifier.clickable { route = next }) {
                        if (h.isTest) TestCohortBadge(h.cohort.dayLength)
                        Text("${h.cohort.durationDays}-day Holdout", style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (stage is Stage.Forfeited) HoldoutColors.Warning else HoldoutColors.Success)
                    }
                }
            }
            val open = view.openToJoin(chainNow)
            if (open.isNotEmpty()) SectionLabel("Holdouts open to join")
            open.forEach { h -> HoldoutOffer(h, chainNow, onClick = { route = Route.Stake(h.address) }) }
            SecondaryButton("The ladder", { route = Route.Ladder })
            SecondaryButton("Get test tokens · ${Format.skr(view.skr)} held", { route = Route.Faucet })
            TextButton(onClick = { route = Route.About }) { Text("About · for judges", color = HoldoutColors.TextTertiary) }
            TextButton(onClick = {
                scope.launch {
                    holdouts.wallet.disconnect(sender)
                    address = null
                }
            }) { Text("Disconnect ${Format.short(wallet)}", color = HoldoutColors.TextTertiary) }
        },
    )
}

/** Where a Holdout's sittings bank on this phone: per wallet, per cohort, per cohort day (data-model §4). */
private fun cohortScope(h: Holdout) = "cohort:${h.address.base58()}"

private const val DAILY_25 = 25

/** Where test sittings under staked rules bank, apart from the real Daily 25. */
private const val TEST_STAKED_SCOPE = "test-staked"

private const val CHAIN_RETRY_MILLIS = 30_000L
private const val CHAIN_REFRESH_MILLIS = 600_000L
private const val ROSTER_REFRESH_MILLIS = 30_000L

/**
 * Which boot of the phone this is, to tell whether a monotonic-clock reading
 * saved earlier still means anything. -1 when the platform will not say.
 */
private fun bootCount(context: android.content.Context): Int = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
