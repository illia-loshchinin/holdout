package holdout.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.Deployment
import holdout.app.chain.Holdout
import holdout.app.holdout.HoldoutViewModel
import holdout.app.names.SeekerNameRepository
import holdout.app.nav.Badges
import holdout.app.nav.Nav
import holdout.app.nav.NavEvent
import holdout.app.nav.NavSaved
import holdout.app.nav.NavState
import holdout.app.nav.Screen
import holdout.app.nav.Sheet
import holdout.app.nav.Tab
import holdout.app.nav.TabBadges
import holdout.app.nav.TxKind
import holdout.app.nav.stages
import holdout.app.nav.then
import holdout.app.reminders.ReminderScheduler
import holdout.app.shell.AboutHost
import holdout.app.shell.AppShell
import holdout.app.shell.CHAIN_REFRESH_MILLIS
import holdout.app.shell.CHAIN_RETRY_MILLIS
import holdout.app.shell.ClaimHost
import holdout.app.shell.DetailHost
import holdout.app.shell.FaucetSheetHost
import holdout.app.shell.ForfeitHost
import holdout.app.shell.HoldoutsRoot
import holdout.app.shell.InFrontLoop
import holdout.app.shell.LeaderboardsHost
import holdout.app.shell.LobbyHost
import holdout.app.shell.MeRoot
import holdout.app.shell.ROSTER_REFRESH_MILLIS
import holdout.app.shell.RosterHost
import holdout.app.shell.SendWaitingDay
import holdout.app.shell.Shared
import holdout.app.shell.SittingHost
import holdout.app.shell.StakeSheetHost
import holdout.app.shell.StandingRoot
import holdout.app.shell.TabRoots
import holdout.app.shell.TodayRoot
import holdout.app.shell.TxFlowHost
import holdout.app.shell.cohortScope
import holdout.app.shell.lowOnFunds
import holdout.app.shell.pendingTodayOf
import holdout.app.shell.resend
import holdout.app.shell.standingDropped
import holdout.app.shell.standingHoldout
import holdout.app.stake.SeekerGate
import holdout.app.stake.SeekerStatus
import holdout.app.storage.MmkvStore
import holdout.app.storage.SavedChainReading
import holdout.app.storage.Store
import holdout.app.streak.OfflineDays
import holdout.app.streak.StreakStore
import holdout.app.tour.TourScreen
import holdout.app.ui.Format
import holdout.app.ui.LocalSeekerNames
import holdout.app.wallet.ConnectScreen
import holdout.app.wallet.SignAction
import holdout.app.wallet.TxPhase
import holdout.app.wallet.WalletRepository
import holdout.core.ChainClock
import holdout.core.CohortSnapshot
import holdout.core.Derive
import holdout.core.HoldoutDay
import holdout.core.MembershipSnapshot
import holdout.core.Reminders
import holdout.core.SeekerGenesisReader
import holdout.core.SeekerNameReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app: shared state, the loops that keep it fresh, and the router.
 *
 * ```
 * connect wallet → (tour, first run) → the 4-tab shell (rulings G1, FR-069)
 *   Today · Standing · Holdouts · Me      roots with the bottom bar   shell/TodayHost, StandingHost, HoldoutsHost, MeHost
 *   pushed screens                         back arrow, no bar          the same hosts
 *   stake confirm · faucet                 sheets over their opener    HoldoutsHost, MeHost
 *   sitting · signing flow · tour          full screen                 shell/SittingHost, TxFlowHost
 * ```
 *
 * Where the member is lives in one [NavState], changed only by
 * [Nav.reduce] (`holdout.app.nav`, tested on the JVM); [AppShell] draws it.
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

/**
 * The mainnet Seeker Genesis Token reader (research R17), on the same
 * read-only endpoint as Seeker ID names: one per process, made on first use.
 */
private object ProcessSeekerGenesis {
    val reader by lazy { SeekerGenesisReader(BuildConfig.NAMES_RPC_URL) }
}

@Composable
private fun HoldoutScreens(sender: ActivityResultSender, store: Store, seekerNames: SeekerNameRepository, modifier: Modifier) {
    val context = LocalContext.current
    val streaks = remember { StreakStore(store) }
    val offlineDays = remember { OfflineDays(store, streaks, holdout.app.shell.DAILY_25) }
    val boot = remember { bootCount(context) }
    val scope = rememberCoroutineScope()

    val clockViewModel: MainViewModel = viewModel()
    val holdouts: HoldoutViewModel = viewModel { HoldoutViewModel(store) }
    // Every join passes the Seeker check before the stake confirm opens (FR-003b).
    val seekerGate = remember { SeekerGate(store, check = { w -> ProcessSeekerGenesis.reader.check(SolanaPublicKey.from(w)) }, enabled = BuildConfig.SEEKER_GATE) }
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

    // Where the member is. The app opens on Today (G1); a restored wallet skips the connect screen.
    // The tab and the pushed screens survive the process dying behind the app (NavSaved);
    // a sheet, the signing flow and a sitting do not (banked reps come back through Today's crash page).
    val firstNav = { address?.let { NavState().then(NavEvent.Connected(tourSeen = store.hasSeenTour(it.base58()))) } ?: NavState() }
    var nav by rememberSaveable(stateSaver = NavStateSaver) { mutableStateOf(firstNav()) }
    // A restored state that disagrees with the wallet (none restored, or one that was disconnected) starts over.
    if (nav.connected != (address != null)) nav = firstNav()
    // A reminder's tap (or the launcher) reaching the running activity: Today (FR-069).
    LaunchedEffect(clockViewModel) { clockViewModel.opens.flow.collect { nav = Nav.reduce(nav, NavEvent.AppOpened) } }
    // This session's Seeker check found no token: no screen offers this wallet a stake it cannot make (G12).
    var knownNotSeeker by remember { mutableStateOf(false) }

    // Chain time, never the wall clock (research R7). The last answer is saved,
    // so a restart offline still knows the day — on the same boot only, since
    // the monotonic clock restarts with the phone.
    val clock =
        remember {
            ChainClock { android.os.SystemClock.elapsedRealtime() }.also { c ->
                store.chainReading()?.takeIf { boot >= 0 && it.boot == boot }?.let { c.restore(it.chainSeconds, it.slot, it.elapsedMillis) }
            }
        }

    // Chain time, read and saved the same way by the loop below and by a member's "Check again" (spec 005).
    suspend fun readChainTime() {
        if (clockViewModel.refreshChainTime(clock)) {
            val elapsed = android.os.SystemClock.elapsedRealtime()
            val chainNow = clock.nowSeconds()
            val slot = clock.slot
            if (boot >= 0 && chainNow != null && slot != null) store.setChainReading(SavedChainReading(chainNow, slot, boot, elapsed))
        }
    }

    // Nothing is read from the chain while the app is not in front: every loop
    // below runs only while started, and starts again on return.
    InFrontLoop(Unit) {
        run {
            readChainTime()
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

    // Bumped when a sitting ends or the phone settles a day on its own; the reminders read it too.
    var refreshToken by remember { mutableIntStateOf(0) }

    // On-device reminders (FR-068): the whole schedule, recomputed from each
    // chain read and handed to the alarm service. A day done on this phone but
    // not on chain is still reminded — to submit it (ReminderReceiver).
    val reminders = remember { ReminderScheduler(context) }
    // Also when a member falls into breach: nothing on chain changes at a missed
    // deadline until the crank records it, but every later reminder must go at once.
    val inBreach = now?.let { n -> chain.view?.holdouts?.count { h -> h.membership?.let { Derive.inBreach(it, h.cohort, n) } == true } }
    // And when the notification prompt is answered: until then nothing could be scheduled.
    var notificationsAnswered by remember { mutableIntStateOf(0) }
    // And when notifications are switched on or off in the phone's settings: read
    // again every time the app comes back to the front.
    var notificationsOn by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notificationsOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    // And when a day is done on this phone (a sitting ends) or its send settles: the
    // not-sent notice (FR-068, 2026-10-11) starts or stops with them.
    LaunchedEffect(address, chain.view, inBreach, notificationsAnswered, notificationsOn, refreshToken, chain.busy) {
        val me = address ?: return@LaunchedEffect
        val view = chain.view ?: return@LaunchedEffect
        val chainNow = clock.nowSeconds() ?: return@LaunchedEffect
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val held = view.holdouts.mapNotNull { h -> h.membership?.let { h.cohort to it } }
        // Done on this phone, whether or not it was sent: the day's record, or a send waiting.
        val doneOnPhone = { c: CohortSnapshot, day: Int ->
            val h = view.holdouts.first { it.cohort == c }
            store.isDayComplete(me.base58(), cohortScope(h), day.toLong()) || store.pendingCohortDay(me.base58(), h.address.base58()) == day
        }
        val addressOf = { c: CohortSnapshot -> view.holdouts.first { it.cohort == c }.address }
        // A finish that should earn a comeback refund (FR-037e): the notice says so (FR-068b).
        val comebackFor = { c: CohortSnapshot, m: MembershipSnapshot -> Reminders.comebackExpected(c, m, view.profile, held) }
        val schedule = Reminders.schedule(held, addressOf, chainNow, comebackFor = comebackFor, doneOnPhone = doneOnPhone)
        reminders.sync(me.base58(), schedule, chainNow, elapsed, withdraw = Reminders.finishedToWithdraw(held, addressOf))
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
        val chainNow = clock.nowSeconds() ?: return@LaunchedEffect
        if (address == null || android.os.Build.VERSION.SDK_INT < 33 || store.askedForNotifications()) return@LaunchedEffect
        if (chain.view?.mine(chainNow).isNullOrEmpty()) return@LaunchedEffect
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
                            nav = Nav.reduce(nav, NavEvent.Connected(tourSeen = store.hasSeenTour(result.address.base58())))
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

    // The chain has answered: a Daily 25 done before it did moves onto the day
    // it was really done (FR-024b). Never mid-sitting.
    val known = now != null
    val idle = nav.sitting == null
    LaunchedEffect(wallet, known, idle) {
        if (!idle) return@LaunchedEffect
        val chainNow = now ?: return@LaunchedEffect
        if (offlineDays.settle(wallet, boot, android.os.SystemClock.elapsedRealtime(), chainNow) != null) refreshToken += 1
    }

    fun submitDayOf(h: Holdout, day: Int) {
        // A day recovered from the phone's own record — the app closed after the
        // last rep, before the sitting's own exit ran — is that day's training for
        // the streak as well. Recording the same date twice changes nothing.
        if (!h.isTest) streaks.recordCompletedDay(wallet, HoldoutDay.indexOf(h.cohort.start + day * h.cohort.dayLength))
        holdouts.submitDay(sender, h, day, h.cohort.repsPerDay)
    }

    // A member's "Check again" while the chain can't be reached (spec 005): the
    // snapshot, chain time and the roster on screen, read now. Nothing is sent.
    fun checkChain(roster: SolanaPublicKey?) = holdouts.checkNow { check ->
        roster?.let { holdouts.loadRoster(it, check) }
        readChainTime()
    }

    lateinit var shared: Shared

    // Every move goes through the navigation model; the few with work outside it do it here.
    fun dispatch(e: NavEvent) {
        val before = nav
        nav = Nav.reduce(before, e)
        when (e) {
            NavEvent.TourDone -> store.setSeenTour(wallet)
            is NavEvent.SeekerChecked -> when (e.status) {
                SeekerStatus.NotSeeker -> knownNotSeeker = true
                SeekerStatus.Confirmed -> knownNotSeeker = false
                else -> Unit
            }
            // "Try again": the same signature, asked of the wallet again.
            NavEvent.TxRetry -> before.tx?.kind?.let { shared.resend(it) }
            else -> Unit
        }
        // The signing flow closed: its last outcome is no longer shown anywhere.
        if (before.tx != null && nav.tx == null) holdouts.clearSigning()
    }

    shared = Shared(
        sender = sender,
        store = store,
        streaks = streaks,
        offlineDays = offlineDays,
        holdouts = holdouts,
        seekerNames = seekerNames,
        clock = clock,
        boot = boot,
        wallet = wallet,
        address = me,
        chain = chain,
        rosters = rosters,
        now = now,
        nav = nav,
        refreshToken = refreshToken,
        bumpRefresh = { refreshToken += 1 },
        dispatch = ::dispatch,
        checkChain = ::checkChain,
        submitDayOf = ::submitDayOf,
        joinAsking = ::joinAsking,
        disconnect = {
            scope.launch {
                holdouts.wallet.disconnect(sender)
                reminders.clear()
                address = null
                knownNotSeeker = false
                nav = Nav.reduce(nav, NavEvent.Disconnected)
            }
        },
        seekerKnown = seekerGate.remembered(wallet),
        notificationsOn = notificationsOn,
        knownNotSeeker = knownNotSeeker,
    )

    // The chain moving a Holdout's stage moves the member (screen map §4, dotted arrows).
    val view = chain.view
    val chainNow = now
    val stages = remember(view, chainNow) { if (view == null || chainNow == null) null else view.stages(chainNow) }
    LaunchedEffect(stages) { stages?.let { dispatch(NavEvent.ChainMoved(it)) } }

    // A join, a day or a claim the wallet is asked to sign shows Holdout's own flow (G7, FR-070).
    var lastSigning by remember { mutableIntStateOf(0) }
    LaunchedEffect(chain.signing) {
        val signing = chain.signing ?: return@LaunchedEffect
        if (signing.id != lastSigning) {
            lastSigning = signing.id
            val cohort = signing.cohort.base58()
            dispatch(
                NavEvent.TxStarted(
                    when (signing.action) {
                        SignAction.Join -> TxKind.Join(cohort, signing.tier)
                        SignAction.Submit -> TxKind.Submit(cohort, signing.day)
                        SignAction.Claim -> TxKind.Claim(cohort)
                    },
                ),
            )
        }
        if (signing.phase != TxPhase.WaitingForWallet) dispatch(NavEvent.TxProgress(signing.phase))
    }

    // The Standing dot needs the roster of the Holdout Standing is about, wherever the member is.
    // The only loop on that roster while the Holdout runs (Today's and the detail screens leave it to this one);
    // a finished Holdout is looked at slowly.
    val standingAbout = shared.standingHoldout()
    val standingEvery = if (standingAbout != null && chainNow != null) TabRoots.rosterEveryMillis(standingAbout, chainNow) else ROSTER_REFRESH_MILLIS
    InFrontLoop(standingAbout?.address to standingEvery) {
        if (standingAbout == null) kotlinx.coroutines.awaitCancellation()
        holdouts.loadRoster(standingAbout.address)
        delay(standingEvery)
    }

    // A day waiting for the network sends itself once the chain answers again, on
    // whichever screen the member is (T098) — the sitting composes its own.
    if (nav.sitting == null) shared.SendWaitingDay()

    val running = shared.active()
    val badges = TabBadges(
        today = Badges.today(
            running = running?.second,
            holdoutDayDoneOnPhone = running?.let { (h, stage) -> shared.pendingTodayOf(h, stage) != null } == true,
            daily25Done = remember(wallet, shared.dayIndex, refreshToken) { store.isDayComplete(wallet, Store.CHALLENGE, shared.dayIndex) },
        ),
        standingDropped = shared.standingDropped(),
        holdouts = Badges.holdouts(nav.stages.values),
        me = shared.lowOnFunds(),
    )

    AppShell(
        nav = nav,
        badges = badges,
        onEvent = ::dispatch,
        connect = {},
        tour = { TourScreen(onDone = { dispatch(NavEvent.TourDone) }) },
        sitting = { shared.SittingHost(it) },
        signing = { shared.TxFlowHost(it) },
        root = { tab ->
            when (tab) {
                Tab.Today -> shared.TodayRoot()
                Tab.Standing -> shared.StandingRoot()
                Tab.Holdouts -> shared.HoldoutsRoot()
                Tab.Me -> shared.MeRoot()
            }
        },
        screen = { screen ->
            when (screen) {
                is Screen.Lobby -> shared.LobbyHost(screen.cohort)
                is Screen.Detail -> shared.DetailHost(screen.cohort)
                is Screen.Roster -> shared.RosterHost(screen.cohort)
                is Screen.Forfeit -> shared.ForfeitHost(screen.cohort)
                is Screen.Claim -> shared.ClaimHost(screen.cohort)
                is Screen.Leaderboards -> shared.LeaderboardsHost(screen.board, leaderboardRead)
                Screen.About -> shared.AboutHost()
            }
        },
        sheet = { sheet ->
            when (sheet) {
                is Sheet.Stake -> shared.StakeSheetHost(sheet, seekerGate)
                is Sheet.Faucet -> shared.FaucetSheetHost()
            }
        },
        modifier = modifier,
    )
}

/** [NavState] through `rememberSaveable`: the strings of [NavSaved], which a bundle holds. */
private val NavStateSaver = Saver<NavState, ArrayList<String>>(
    save = { ArrayList(NavSaved.encode(it)) },
    restore = { NavSaved.decode(it) },
)

/**
 * Which boot of the phone this is, to tell whether a monotonic-clock reading
 * saved earlier still means anything. -1 when the platform will not say.
 */
private fun bootCount(context: android.content.Context): Int = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)

/**
 * What the finished screen says for a cohort day completed while the chain
 * cannot be reached (001 FR-024b, decided 2026-10-10): not sent yet, saved on
 * the phone, and how it gets on chain — opened again before **that day's**
 * deadline and approved in the wallet. Past the deadline the day can no longer
 * be sent, and it says so. [nowSeconds] is chain time, null when unknown.
 */
internal fun offlineNote(h: Holdout, day: Int, nowSeconds: Long?, network: String = Deployment.networkName): Pair<String, String> = sittingNote(h, day, nowSeconds, offline = true, network = network)!!

/**
 * The finished screen's note for a cohort day just completed, or null when the day
 * can be sent now: the chain answers and that day's deadline has not passed. Past
 * the deadline the note is "Done — too late to send" whether the chain answers or
 * not — a submit would only fail with DeadlinePassed (001 FR-024b, AC-18).
 */
internal fun sittingNote(h: Holdout, day: Int, nowSeconds: Long?, offline: Boolean, network: String = Deployment.networkName): Pair<String, String>? {
    val n = day + 1
    val reps = h.cohort.repsPerDay
    val left = nowSeconds?.let { Derive.deadlineOf(h.cohort, day) - it }
    if (left != null && left <= 0) {
        val reach = if (offline) "$network can't be reached, and day" else "Day"
        return "Done — too late to send" to
            "$reach $n's deadline has passed, so it can no longer be sent. Your $reps are saved on this phone."
    }
    if (!offline) return null
    val deadline = left?.let { " (${Format.countdown(it)} left)" } ?: ""
    return "Done — not sent yet" to
        "$network can't be reached, so day $n isn't on chain yet. Your $reps are saved on this phone. " +
        "Open Holdout when it can be, before the deadline$deadline, and approve day $n in your wallet."
}
