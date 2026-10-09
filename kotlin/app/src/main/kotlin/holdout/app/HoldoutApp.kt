package holdout.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import holdout.app.about.AboutScreen
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.claim.ClaimScreen
import holdout.app.cohort.CohortDetailScreen
import holdout.app.cohort.ForfeitScreen
import holdout.app.faucet.FaucetSheet
import holdout.app.holdout.HoldoutViewModel
import holdout.app.home.CohortHome
import holdout.app.home.HoldoutOffer
import holdout.app.home.HomeScreen
import holdout.app.home.HomeState
import holdout.app.leaderboard.LeaderboardScreen
import holdout.app.lobby.LobbyScreen
import holdout.app.names.SeekerNameRepository
import holdout.app.reminders.ReminderScheduler
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
import holdout.app.ui.LocalSeekerNames
import holdout.app.ui.MemberIdentity
import holdout.app.ui.Reading
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestCohortBadge
import holdout.app.ui.theme.HoldoutColors
import holdout.app.wallet.ConnectScreen
import holdout.app.wallet.WalletRepository
import holdout.core.ChainClock
import holdout.core.Derive
import holdout.core.HoldoutDay
import holdout.core.Journey
import holdout.core.Leaderboard
import holdout.core.LeagueParams
import holdout.core.Reminders
import holdout.core.ScriptedWallets
import holdout.core.SeekerNameReader
import holdout.core.Stage
import holdout.repcounter.Stakes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Every screen past the connect screen (contracts/android-app §5). Holdouts are named by cohort address. */
private sealed interface Route {
    data object Home : Route
    data object Faucet : Route
    data object About : Route
    data object Tour : Route
    data class Leaderboards(val board: Leaderboard.Board) : Route
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
 *                                            ├─ a Holdout open to join → stake → lobby
 *                                            ├─ Holdout running → cohort day sitting → submit
 *                                            └─ forfeit · settling · claim · roster · about
 * ```
 *
 * **A wallet comes first, even for the free Daily 25** (FR-001a): before it,
 * only the connect screen and no-wallet help. Everything the Daily 25 does after
 * that works with no connection at all (FR-024b). Which Holdout screen shows
 * is decided by `Journey.stage` from chain state and chain time.
 *
 * Members are named by their Seeker ID where they have one (spec 003): the
 * names kept on the phone are provided to every screen through
 * [LocalSeekerNames], and looked up lazily by one repository per process.
 */
@Composable
fun HoldoutApp(sender: ActivityResultSender, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store: Store = remember { MmkvStore.create(context) }
    val seekerNames = remember { ProcessSeekerNames.repository(store) }
    val names by seekerNames.names.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalSeekerNames provides names) {
        HoldoutScreens(sender, store, seekerNames, modifier)
    }
}

/**
 * The one [SeekerNameRepository] per app process (spec 003 data-model §4):
 * its queue and worker outlive any Activity, so a rotation never starts a
 * second worker or loses the pace.
 */
private object ProcessSeekerNames {
    @Volatile
    private var instance: SeekerNameRepository? = null

    fun repository(store: Store): SeekerNameRepository = instance ?: synchronized(this) {
        instance ?: SeekerNameRepository(
            nameOf = { wallet -> reader.nameOf(SolanaPublicKey.from(wallet)) },
            store = store,
        ).also { instance = it }
    }

    private val reader by lazy { SeekerNameReader(BuildConfig.NAMES_RPC_URL) }
}

@Composable
private fun HoldoutScreens(sender: ActivityResultSender, store: Store, seekerNames: SeekerNameRepository, modifier: Modifier) {
    val context = LocalContext.current
    val streaks = remember { StreakStore(store) }
    val offlineDays = remember { OfflineDays(store, streaks, DAILY_25) }
    val boot = remember { bootCount(context) }
    val scope = rememberCoroutineScope()

    val clockViewModel: MainViewModel = viewModel()
    val holdouts: HoldoutViewModel = viewModel { HoldoutViewModel(store) }
    val chain by holdouts.state.collectAsStateWithLifecycle()
    val rosters by holdouts.rosters.collectAsStateWithLifecycle()
    val leaderboardRead by holdouts.leaderboard.collectAsStateWithLifecycle()

    // The wallet a previous run connected is restored with its auth token, so a
    // restart does not ask again (contracts/android-app §3).
    var address by remember { mutableStateOf(holdouts.wallet.restored) }
    // The connected wallet's own Seeker ID is checked again on every connect,
    // a restored session included (spec 003 FR-007); a disconnect stops it.
    LaunchedEffect(address) { seekerNames.connected(address?.base58()) }
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
    // Nothing is read from the chain while the app is not in front: every loop
    // below runs only while started, and starts again on return.
    InFrontLoop(Unit) {
        run {
            if (clockViewModel.refreshChainTime(clock)) {
                val elapsed = android.os.SystemClock.elapsedRealtime()
                val chainNow = clock.nowSeconds()
                val slot = clock.slot
                if (boot >= 0 && chainNow != null && slot != null) store.setChainReading(SavedChainReading(chainNow, slot, boot, elapsed))
            }
            // With no reading yet, the network's return asks again at once (below).
            if (clock.hasReading) delay(CHAIN_REFRESH_MILLIS) else clockViewModel.retry.await(CHAIN_RETRY_MILLIS)
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
    // The default network is followed for as long as the view model lives, not only
    // while started: a loss while the wallet holds the screen must still re-arm the
    // automatic send (T098). Its return reads at once only while in front.
    LaunchedEffect(holdouts) {
        holdouts.followNetwork(context.applicationContext.getSystemService(android.net.ConnectivityManager::class.java)) {
            clockViewModel.retry.networkBack()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(address, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            holdouts.watch(address)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                holdouts.pause()
            }
        }
    }

    // On-device reminders (FR-068): the whole schedule, recomputed from each
    // chain read and handed to the alarm service. A day done on this phone but
    // not on chain is still reminded — to submit it (ReminderReceiver).
    val reminders = remember { ReminderScheduler(context) }
    // Also when a member falls into breach: nothing on chain changes at a missed
    // deadline until the crank records it, but every later reminder must go at once.
    val inBreach = now?.let { n -> chain.view?.holdouts?.count { h -> h.membership?.let { Derive.inBreach(it, h.cohort, n) } == true } }
    // And when the notification prompt is answered: until then nothing could be scheduled.
    var notificationsAnswered by remember { mutableStateOf(0) }
    // And when notifications are switched on or off in the phone's settings: read
    // again every time the app comes back to the front.
    var notificationsOn by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notificationsOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    LaunchedEffect(address, chain.view, inBreach, notificationsAnswered, notificationsOn) {
        val me = address ?: return@LaunchedEffect
        val view = chain.view ?: return@LaunchedEffect
        val chainNow = clock.nowSeconds() ?: return@LaunchedEffect
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val held = view.holdouts.mapNotNull { h -> h.membership?.let { h.cohort to it } }
        val schedule = Reminders.schedule(held, { c -> view.holdouts.first { it.cohort == c }.address }, chainNow)
        reminders.sync(me.base58(), schedule, chainNow, elapsed)
    }

    // Asked once, at the first join, with the reason (FR-068a); the join goes ahead either way.
    var joinAfterAsking by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askForNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAnswered++
        joinAfterAsking?.invoke()
        joinAfterAsking = null
    }
    // A member already in a Holdout — joined before reminders existed, or on
    // another install — is asked the first time the app finds them in one.
    LaunchedEffect(address, chain.view) {
        val now = clock.nowSeconds() ?: return@LaunchedEffect
        if (address == null || android.os.Build.VERSION.SDK_INT < 33 || store.askedForNotifications()) return@LaunchedEffect
        if (chain.view?.mine(now).isNullOrEmpty()) return@LaunchedEffect
        store.setAskedForNotifications()
        askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    fun joinAsking(join: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !store.askedForNotifications()) {
            store.setAskedForNotifications()
            joinAfterAsking = join
            askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            join()
        }
    }

    var route by remember { mutableStateOf<Route>(Route.Home) }
    var sitting by remember { mutableStateOf<Sitting?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    val me = address
    if (me == null) {
        // The live list's running Holdouts show figures derived from their rosters,
        // as every screen behind the wallet does — read here, and kept fresh.
        val liveNow = now?.let { n -> chain.view?.live(n) }.orEmpty()
        val runningNow = now?.let { n -> liveNow.filter { it.status(n) == holdout.core.CohortStatus.Running }.map { it.address } }.orEmpty()
        InFrontLoop(runningNow) {
            if (runningNow.isEmpty()) kotlinx.coroutines.awaitCancellation()
            runningNow.forEach { holdouts.loadRoster(it) }
            delay(ROSTER_REFRESH_MILLIS)
        }
        ConnectScreen(
            busy = connecting,
            message = connectMessage,
            noWallet = noWallet,
            live = liveNow,
            now = now,
            rosters = rosters,
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
            modifier = modifier,
        )
        return
    }
    val wallet = me.base58()

    // Compose state, not a storage read: storage is not observed, so finishing the
    // tour must change something this composition reads or the screen never leaves it.
    var tourSeen by remember(wallet) { mutableStateOf(store.hasSeenTour(wallet)) }

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

    val chainNow = now
    val view = chain.view
    fun holdout(address: SolanaPublicKey): Holdout? = view?.holdouts?.firstOrNull { it.address == address }

    /** Today's day of [h] done on this phone and not yet on chain, if any. Only today's counts: an older one left behind must not hide it. */
    @Composable
    fun pendingTodayOf(h: Holdout, stage: Stage.Active): Int? {
        val pending = remember(wallet, h.address, refreshToken, chain.busy) { holdouts.pendingDay(h) }
        val locallyDone = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.isDayComplete(wallet, cohortScope(h), stage.dayIndex.toLong()) }
        return pending?.takeIf { it == stage.dayIndex } ?: stage.dayIndex.takeIf { locallyDone && !stage.todayDone }
    }

    fun submitDayOf(h: Holdout, day: Int) {
        // A day recovered from the phone's own record — the app closed after the
        // last rep, before the sitting's own exit ran — is that day's training for
        // the streak as well. Recording the same date twice changes nothing.
        if (!h.isTest) streaks.recordCompletedDay(wallet, HoldoutDay.indexOf(h.cohort.start + day * h.cohort.dayLength))
        holdouts.submitDay(sender, h, day, h.cohort.repsPerDay)
    }

    /**
     * A day waiting for the network sends itself once the chain answers again
     * (FR-024b Phase 2, T098) — on whichever screen the member is, the tour and
     * the Daily 25 included, short of a cohort sitting, and again after the
     * system reports the network lost and the chain answers once more.
     * [whenFree] returns once the screen may give way to the wallet; [key] is
     * what it waits on.
     */
    @Composable
    fun SendWaitingDay(key: Any? = null, whenFree: suspend () -> Unit = {}) {
        val running = chainNow?.let { n -> view?.mine(n)?.firstNotNullOfOrNull { h -> (h.stage(n) as? Stage.Active)?.let { h to it } } } ?: return
        val (h, stage) = running
        val pendingToday = pendingTodayOf(h, stage)
        LaunchedEffect(h.address, pendingToday, stage.todayDone, chain.offline, chain.busy, chain.autoSubmit, key) {
            if (pendingToday == null) return@LaunchedEffect
            whenFree()
            if (holdouts.autoSubmitDue(h, pendingToday, stage.todayDone)) submitDayOf(h, pendingToday)
        }
    }

    // ---- a sitting -------------------------------------------------------------
    when (val s = sitting) {
        is Sitting.Daily25 -> {
            val vm = remember(wallet, s.day, refreshToken) {
                SittingViewModel(
                    store = store,
                    streaks = streaks,
                    wallet = wallet,
                    scope = Store.CHALLENGE,
                    dayIndex = s.day,
                    target = DAILY_25,
                    chainSecondsNow = { clock.nowSeconds() },
                    stakes = Stakes.DAILY,
                )
            }
            DisposableEffect(vm) { onDispose { vm.release() } }
            // A cohort day waiting for the network goes over the Daily 25 too: a sitting
            // has no pause limit, and the day's deadline must not pass with the app open
            // and connected. Never mid-set, though — leaving for the wallet then would
            // cost the set's loose reps; it opens once the set ends. Out of a set the
            // wallet's round trip costs nothing: banked blocks are stored, and this
            // sitting, held in the composition, is where the member comes back to.
            SendWaitingDay(vm) { vm.uiState.first { !it.step.inSet } }
            val done = {
                sitting = null
                refreshToken += 1
            }
            SittingScreen(viewModel = vm, onFinished = done, onCancelled = { done() }, modifier = modifier)
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
                    // The cohort's own form (H35): Hard from the toes, Easy with knees
                    // allowed — 45° only either way (FR-021a). An unknown form never
                    // reaches here: Start refuses it.
                    stakes = Stakes.ofForm(h.cohort.form) ?: Stakes.STAKED,
                    // The cohort's own days — ten minutes each in the short test cohort.
                    dayOf = { Derive.dayIndex(h.cohort, it).toLong() },
                    // A Holdout day ends at its deadline: no rolling into the next (a missed day is a breach).
                    rollsOver = false,
                    timeLeftAt = { Derive.timeLeftToday(h.cohort, it) },
                )
            }
            DisposableEffect(vm) { onDispose { vm.release() } }
            val done = {
                sitting = null
                refreshToken += 1
                // The day it started on, or the next one if it ran across a boundary.
                val today = clock.nowSeconds()?.let { Derive.dayIndex(h.cohort, it) }
                val completed = listOfNotNull(s.day, today).distinct().firstOrNull { store.isDayComplete(wallet, cohortScope, it.toLong()) }
                if (completed != null) {
                    // A completed cohort day is that day's training for the streak too, so
                    // nobody trains twice (FR-050) — judged leagues only, whose days are real days.
                    // The day the reps were done on — the cohort day's own date — not the day
                    // this screen is left on: across 05:00 UTC those are two different days.
                    if (!h.isTest) streaks.recordCompletedDay(wallet, HoldoutDay.indexOf(h.cohort.start + completed * h.cohort.dayLength))
                    holdouts.submitDay(sender, h, completed, h.cohort.repsPerDay)
                }
            }
            SittingScreen(
                viewModel = vm,
                onFinished = done,
                onCancelled = { done() },
                modifier = modifier,
                finishLabel = "Submit day ${s.day + 1} to the chain",
            )
            return
        }
        null -> Unit
    }

    SendWaitingDay()

    // The tour — first run, or opened again from About — shows only after the
    // automatic send above is composed, so a reconnect during it still sends.
    if (!tourSeen || route == Route.Tour) {
        TourScreen(
            onDone = {
                store.setSeenTour(wallet)
                tourSeen = true
                route = Route.Home
            },
            modifier = modifier,
        )
        return
    }

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
        InFrontLoop(target.address) {
            run {
                holdouts.loadRoster(target.address)
                delay(ROSTER_REFRESH_MILLIS)
            }
        }
        val members = rosters[target.address]
        val stage = target.stage(chainNow)
        // Only the claim made from this cohort: another cohort's transaction is never shown as this one's.
        val claimSignature = chain.lastClaim?.takeIf { it.first == target.address }?.second
        // A join that landed moves the stake sheet on to the lobby. The lobby lasts
        // until the start: a leave, or the Holdout starting, takes it home, where
        // the day's sitting is (or the forfeit, or the claim).
        LaunchedEffect(routed, stage) {
            if (routed is Route.Stake && (stage == Stage.Lobby || stage == Stage.LobbyLocked)) route = Route.Lobby(target.address)
            if (routed is Route.Lobby && stage != Stage.Lobby && stage != Stage.LobbyLocked) route = Route.Home
        }
        when (routed) {
            is Route.Stake -> StakeSheet(
                h = target,
                skrBalance = view?.skr ?: 0uL,
                lamports = view?.lamports ?: 0L,
                now = chainNow,
                joinBlocker = Journey.joinBlocker(target.cohort, view?.profile, view?.runningEnd(chainNow), chainNow),
                busy = chain.busy,
                message = chain.message,
                messageIsWarning = chain.messageIsWarning,
                onStake = { tier -> joinAsking { holdouts.join(sender, target, tier, chainNow) } },
                onGetTokens = { route = Route.Faucet },
                onBack = goHome,
                modifier = modifier,
            )
            is Route.Lobby -> LobbyScreen(target, chainNow, chain.busy, chain.message, chain.messageIsWarning, { holdouts.leave(sender, target) }, { route = Route.Roster(target.address) }, goHome, modifier)
            is Route.Detail -> CohortDetailScreen(target, members, stage, chainNow, claimSignature, { route = Route.Roster(target.address) }, goHome, modifier)
            is Route.Roster -> RosterScreen(target, members, wallet, chainNow, goHome, modifier, onShowing = seekerNames::showing)
            is Route.Forfeit -> ForfeitScreen(
                h = target,
                stage = stage as? Stage.Forfeited ?: Stage.Forfeited(target.membership?.daysCompleted ?: 0, recorded = false),
                streak = streaks.read(wallet, dayIndex).current,
                onDaily25 = goHome,
                // The way back is the next 7-day Holdout's stake sheet, straight (screen map §8);
                // none open to join right now, and the button is not shown.
                onNextHoldout = chainNow?.let { now -> view?.openToJoin(now) }
                    ?.firstOrNull { it.cohort.leagueId == LeagueParams.LEAGUE_7D }
                    ?.let { next -> { route = Route.Stake(next.address) } },
                onBack = goHome,
                modifier = modifier,
            )
            is Route.Claim -> ClaimScreen(target, stage, chain.busy, chain.message, chain.messageIsWarning, claimSignature, { holdouts.claim(sender, target) }, goHome, modifier)
            else -> Unit
        }
        return
    }

    when (routed) {
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
            AboutScreen(
                onTour = { route = Route.Tour },
                onBack = goHome,
                modifier = modifier,
                onDisconnect = {
                    scope.launch {
                        holdouts.wallet.disconnect(sender)
                        reminders.clear()
                        route = Route.Home
                        address = null
                    }
                },
            )
            return
        }
        is Route.Leaderboards -> {
            InFrontLoop(Unit) {
                holdouts.loadLeaderboard()
                delay(ROSTER_REFRESH_MILLIS)
            }
            val read = leaderboardRead
            // The test board exists only where a test league does (FR-044a).
            val boards = Leaderboard.Board.entries.filter { it != Leaderboard.Board.TEST || read?.cohortsAfter.orEmpty().any { c -> Leaderboard.Board.TEST.covers(c.leagueId) } }
            val board = routed.board
            val shown = if (read == null || chainNow == null) null else Leaderboard.view(board, read, chainNow, ScriptedWallets::isScripted)
            LeaderboardScreen(board, boards, shown, address, { route = Route.Leaderboards(it) }, goHome, modifier, onShowing = seekerNames::showing)
            return
        }
        else -> Unit
    }

    // ---- home --------------------------------------------------------------------
    // Finish anything a killed process left half-written, then read the day again.
    LaunchedEffect(wallet, dayIndex) {
        if (dayIndex != Store.UNKNOWN_DAY && BankingController.recover(store, streaks, wallet, Store.CHALLENGE, dayIndex, DAILY_25)) refreshToken += 1
    }

    // The member's own Holdouts that are not running today: lobby, forfeit, settling, claim.
    // Shown under a running one too: a lobby joined for later must stay reachable, or
    // its Leave — the whole stake back — could not be had before joining closes.
    @Composable
    fun ownHoldoutCards(now: Long) {
        view?.mine(now)?.forEach { h ->
            val stage = h.stage(now)
            val (label, next) = when (stage) {
                Stage.Lobby, Stage.LobbyLocked -> "In the lobby · starts in ${Format.countdown(h.cohort.start - now)}" to Route.Lobby(h.address)
                is Stage.Forfeited -> "Out after ${stage.daysLasted} day${if (stage.daysLasted == 1) "" else "s"}" to Route.Forfeit(h.address)
                Stage.Settling -> "You finished · settlement in progress" to Route.Detail(h.address)
                is Stage.ReadyToClaim -> "You finished · claim ${Format.skr(stage.amount)}" to Route.Claim(h.address)
                else -> null to null
            }
            if (label != null && next != null) {
                HoldoutCard(Modifier.clickable { route = next }) {
                    if (h.isTest) TestCohortBadge(h.cohort.dayLength)
                    Text(Format.holdoutTitle(h.cohort), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = if (stage is Stage.Forfeited) HoldoutColors.Warning else HoldoutColors.Success)
                }
            }
        }
    }

    // A Holdout running with the member standing in it leads home (PRD §10.1a).
    val active = chainNow?.let { n -> view?.mine(n)?.firstNotNullOfOrNull { h -> (h.stage(n) as? Stage.Active)?.let { h to it } } }
    if (active != null && chainNow != null) {
        val (h, stage) = active
        InFrontLoop(h.address) {
            run {
                holdouts.loadRoster(h.address)
                delay(ROSTER_REFRESH_MILLIS)
            }
        }
        val cohortScope = cohortScope(h)
        // A process killed between banking the last block and marking the day done
        // leaves the target banked and no marker: finish it, as for the Daily 25,
        // or the day could be neither trained again nor submitted.
        LaunchedEffect(wallet, h.address, stage.dayIndex) {
            if (BankingController.recover(store, streaks, wallet, cohortScope, stage.dayIndex.toLong(), h.cohort.repsPerDay)) refreshToken += 1
        }
        val banked = remember(wallet, h.address, stage.dayIndex, refreshToken) { store.bankedReps(wallet, cohortScope, stage.dayIndex.toLong()) }
        val pendingToday = pendingTodayOf(h, stage)
        val submit = { day: Int -> submitDayOf(h, day) }
        CohortHome(
            h = h,
            members = rosters[h.address],
            stage = stage,
            now = chainNow,
            banked = banked,
            pendingDay = pendingToday,
            autoSendTried = pendingToday != null && chain.autoSubmit.tried(h.address.base58(), pendingToday),
            offline = chain.offline,
            busy = chain.busy,
            message = chain.message,
            messageIsWarning = chain.messageIsWarning,
            onStart = {
                // An exercise or a form this build does not know is refused, never guessed (FR-014, H30, H35).
                if (h.cohort.exercise != LeagueParams.EXERCISE_PUSHUP) return@CohortHome
                if (Stakes.ofForm(h.cohort.form) == null) return@CohortHome
                sitting = Sitting.CohortDay(h, stage.dayIndex)
            },
            onSubmit = submit,
            onDetail = { route = Route.Detail(h.address) },
            onAbout = { route = Route.About },
            onRoster = { route = Route.Roster(h.address) },
            onDaily25 = { sitting = Sitting.Daily25(dayIndex) },
            onGetTokens = { route = Route.Faucet },
            modifier = modifier,
            otherHoldouts = { ownHoldoutCards(chainNow) },
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
            freezes = streak.freezes,
            toes = holdout.app.streak.ToesMeter(store, DAILY_25).read(wallet, dayIndex),
            holdoutLine = chainNow?.let { n -> view?.let { v -> holdout.app.home.DailyHoldoutLine.of(v.openToJoin(n).map { it.cohort }, v.profile, n) } },
            secondsLeftToday = chainNow?.let { HoldoutDay.secondsLeftIn(it) },
        ),
        onStart = {
            if (dayIndex == Store.UNKNOWN_DAY) offlineDays.sittingStarted(wallet, boot, android.os.SystemClock.elapsedRealtime())
            sitting = Sitting.Daily25(dayIndex)
        },
        modifier = modifier,
        below = {
            if (chainNow == null || view == null) {
                if (chain.offline) {
                    SectionLabel("${Deployment.networkName} unreachable — the Daily 25 still works")
                } else {
                    Reading { SectionLabel("Reading the chain…") }
                }
                return@HomeScreen
            }
            if (view.config == null) {
                SectionLabel("Holdouts are not live on devnet yet")
                return@HomeScreen
            }
            ownHoldoutCards(chainNow)
            val past = view.claimed(chainNow)
            if (past.isNotEmpty()) SectionLabel("Past Holdouts")
            past.forEach { h ->
                val claimed = h.stage(chainNow) as? Stage.Claimed ?: return@forEach
                HoldoutCard(Modifier.clickable { route = Route.Detail(h.address) }) {
                    if (h.isTest) TestCohortBadge(h.cohort.dayLength)
                    Text(Format.holdoutTitle(h.cohort), style = MaterialTheme.typography.titleMedium, color = HoldoutColors.TextPrimary)
                    Text("Finished · claimed ${Format.skr(claimed.amount)}", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextSecondary)
                }
            }
            val open = view.openToJoin(chainNow)
            if (open.isNotEmpty()) SectionLabel("Holdouts open to join")
            open.forEach { h -> HoldoutOffer(h, chainNow, onClick = { route = Route.Stake(h.address) }) }
            SecondaryButton("Leaderboards", { route = Route.Leaderboards(Leaderboard.Board.EASY_7D) })
            SecondaryButton("Get test tokens · ${Format.skr(view.skr)} held", { route = Route.Faucet })
            TextButton(onClick = { route = Route.About }) { Text("About Holdout", color = HoldoutColors.TextTertiary) }
            TextButton(onClick = {
                scope.launch {
                    holdouts.wallet.disconnect(sender)
                    reminders.clear()
                    address = null
                }
            }) {
                Text("Disconnect ", color = HoldoutColors.TextTertiary)
                MemberIdentity(wallet, isYou = false, budget = 28, color = HoldoutColors.TextTertiary, style = LocalTextStyle.current)
            }
        },
    )
}

/** Where a Holdout's sittings bank on this phone: per wallet, per cohort, per cohort day (data-model §4). */
private fun cohortScope(h: Holdout) = "cohort:${h.address.base58()}"

private const val DAILY_25 = 25

private const val CHAIN_RETRY_MILLIS = 30_000L
private const val CHAIN_REFRESH_MILLIS = 600_000L
private const val ROSTER_REFRESH_MILLIS = 30_000L

/**
 * Which boot of the phone this is, to tell whether a monotonic-clock reading
 * saved earlier still means anything. -1 when the platform will not say.
 */
private fun bootCount(context: android.content.Context): Int = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)

/** Repeats [pass] for as long as the app is in front; stops when it is not, and starts again on return. */
@Composable
private fun InFrontLoop(key: Any?, pass: suspend () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(key, owner) {
        owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) pass()
        }
    }
}
