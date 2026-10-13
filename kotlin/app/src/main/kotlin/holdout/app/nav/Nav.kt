package holdout.app.nav

import holdout.app.chain.ChainView
import holdout.app.stake.SeekerStatus
import holdout.app.wallet.TxOutcome
import holdout.app.wallet.TxPhase
import holdout.core.Leaderboard
import holdout.core.Stage

/**
 * Where the member is in the app, as plain data: the four tabs with a back
 * stack each, and what can cover them — a bottom sheet, the wallet-signing
 * flow, a sitting, the tour. No Compose and no Android: every transition of
 * the screen map (§3, §4; rulings G1, G7, G8) is [Nav.reduce] on a
 * [NavState] and a [NavEvent], tested on the JVM.
 *
 * ```
 *  tour (first run, or "How Holdout works")            full screen, no bar
 *  sitting (the Daily 25, a Holdout day)               full screen, no bar
 *  signing flow (join · submit · claim, You're in)     full screen, no bar
 *  sheet (stake confirm with its Seeker gate · faucet) over the screen that opened it
 *  tab: Today · Standing · Holdouts · Me               root (bar) + pushed screens (back arrow)
 * ```
 *
 * Holdouts are named by their cohort address in base58.
 */
enum class Tab(val label: String) {
    Today("Today"),
    Standing("Standing"),
    Holdouts("Holdouts"),
    Me("Me"),
}

/** A screen pushed above a tab root. It carries a back arrow and no bar. */
sealed interface Screen {
    /** H3. */
    data class Lobby(val cohort: String) : Screen

    /** H4, opened from a list rather than as the Standing root. */
    data class Detail(val cohort: String) : Screen

    /** H5. */
    data class Roster(val cohort: String) : Screen

    /** H6. */
    data class Forfeit(val cohort: String) : Screen

    /** H7. */
    data class Claim(val cohort: String) : Screen

    /** L1. */
    data class Leaderboards(val board: Leaderboard.Board) : Screen

    /** X1. */
    data object About : Screen
}

/** A bottom sheet over the screen that opened it (G8). */
sealed interface Sheet {
    /** H2: the stake confirm, behind the Seeker gate until [gate] is [SeekerStatus.Confirmed]. [tier] is the chip it opens on. */
    data class Stake(val cohort: String, val tier: Int = 0, val gate: SeekerStatus = SeekerStatus.Checking) : Sheet

    /** The faucet; closing it goes back to [returnTo], the stake sheet that sent the member for tokens (ES-0a), if any. */
    data class Faucet(val returnTo: Stake? = null) : Sheet
}

/** A sitting in progress: full screen, the phone on the floor. */
sealed interface Sitting {
    /** The free Daily 25, filed under the global day fixed when it started. */
    data class Daily25(val day: Long) : Sitting

    /** One day of a Holdout. */
    data class HoldoutDay(val cohort: String, val day: Int) : Sitting
}

/** What the signing flow is signing (G7). */
sealed interface TxKind {
    val cohort: String

    data class Join(override val cohort: String, val tier: Int) : TxKind

    data class Submit(override val cohort: String, val day: Int) : TxKind

    data class Claim(override val cohort: String) : TxKind
}

/** The signing flow: waiting for your wallet → confirming → a result; a landed join then shows You're in. */
data class TxFlow(val kind: TxKind, val phase: TxPhase = TxPhase.WaitingForWallet, val youreIn: Boolean = false)

/** The tour: on first run it cannot be left but by finishing it (004 FR-013). */
data class Tour(val firstRun: Boolean)

data class NavState(
    /** A wallet is connected; before one, only the connect screen (FR-001a). */
    val connected: Boolean = false,
    val tour: Tour? = null,
    val tab: Tab = Tab.Today,
    /** The screens pushed above each tab's root, bottom first; a tab with none is at its root. */
    val stacks: Map<Tab, List<Screen>> = emptyMap(),
    val sheet: Sheet? = null,
    val tx: TxFlow? = null,
    val sitting: Sitting? = null,
    /** Each Holdout's stage as last read, so a change seen while the app is open can move the member (§4 dotted arrows). */
    val stages: Map<String, Stage> = emptyMap(),
) {
    /** The shown tab's pushed screens. */
    val stack: List<Screen> get() = stacks[tab].orEmpty()

    /** The pushed screen shown, or null on the tab's root. */
    val top: Screen? get() = stack.lastOrNull()

    val onRoot: Boolean get() = top == null

    /** Something full screen covers the tabs: the connect screen, the tour, a sitting or the signing flow. */
    val fullScreen: Boolean get() = !connected || tour != null || sitting != null || tx != null

    /** The bar shows on tab roots only, never under a full-screen step (G1). A sheet covers it with its scrim. */
    val showsBar: Boolean get() = !fullScreen && onRoot

    /**
     * Whether what is in front shows live chain data, so the app's 30-second pulse reads (FR-071): the
     * connect screen's live list, the signing flow, Today, Standing, Holdouts and the screens pushed on
     * them, the leaderboards (read again when the pulse sees a settlement move). Not Me, About, the tour
     * or a sitting: Me's balances and badge are as of the last read, which the wallet's own actions and a
     * live screen refresh; the others show no chain data.
     */
    val showsLiveChain: Boolean get() = when {
        !connected || tx != null -> true
        tour != null || sitting != null -> false
        else -> when (top) {
            null -> tab != Tab.Me
            Screen.About -> false
            else -> true
        }
    }

    /** Whether a system Back does anything here; when not, it leaves the app (or the screen shown handles it itself). */
    val canGoBack: Boolean get() = Nav.back(this) != null
}

sealed interface NavEvent {
    /** The wallet connected (N1); [tourSeen] for this wallet on this phone. */
    data class Connected(val tourSeen: Boolean) : NavEvent

    data object Disconnected : NavEvent

    /** The app came to the front from the launcher or a reminder (N2): Today. */
    data object AppOpened : NavEvent

    /** "How Holdout works" / "Tour again". */
    data object OpenTour : NavEvent

    data object TourDone : NavEvent

    /** A tap on the bar: the tab as it was left, or its root when it is the tab shown. */
    data class SelectTab(val tab: Tab) : NavEvent

    /** A link that names a tab root ("Join one to have a crowd", H0's running card, Today's crowd strip): that root, whatever the tab kept. */
    data class GoToRoot(val tab: Tab) : NavEvent

    /** Pushes [screen] on the shown tab. */
    data class Open(val screen: Screen) : NavEvent

    /** Replaces the screen on top in place: another board on L1. */
    data class Swap(val screen: Screen) : NavEvent

    data object Back : NavEvent

    /** Join: the stake sheet on [tier]. [seekerKnown]: this wallet is confirmed on this phone, so the gate is passed (FR-003b). */
    data class OpenStake(val cohort: String, val tier: Int = 0, val seekerKnown: Boolean = false) : NavEvent

    /** The chip picked on the stake sheet, so it is still picked after the faucet round trip (ES-0a). */
    data class StakeTier(val tier: Int) : NavEvent

    data class SeekerChecked(val status: SeekerStatus) : NavEvent

    data object SeekerRetry : NavEvent

    /** "Get test tokens": the faucet sheet, which returns to the stake sheet when one sent the member (ES-0a). */
    data object OpenFaucet : NavEvent

    /** A drag down or a scrim tap. */
    data object CloseSheet : NavEvent

    data class StartSitting(val sitting: Sitting) : NavEvent

    /** The sitting closed — done, stopped or its day sent on: Today. */
    data object SittingEnded : NavEvent

    /** The wallet was asked to sign [kind]. */
    data class TxStarted(val kind: TxKind) : NavEvent

    data class TxProgress(val phase: TxPhase) : NavEvent

    /** "Try again": the same action, a new signature asked of the wallet. */
    data object TxRetry : NavEvent

    /** "Back" on a result, or leaving the confirming screen ("You can leave this screen"). */
    data object TxBack : NavEvent

    /** "Done" after a claim: H0. */
    data object TxDone : NavEvent

    /** A join that landed in a Holdout already under way (a late comeback join, FR-037e): no lobby to wait in, so Today, its day. */
    data object JoinedUnderWay : NavEvent

    /** You're in → "See the lobby". */
    data object SeeLobby : NavEvent

    /** Joining closed first → the Holdouts still open, on H0. */
    data object SeeOpenHoldouts : NavEvent

    /** Deadline passed → "What happens now": the forfeit. */
    data object WhatHappensNow : NavEvent

    /** The chain was read: every Holdout's stage now. */
    data class ChainMoved(val stages: Map<String, Stage>) : NavEvent
}

/** Applies [events] in order. */
fun NavState.then(vararg events: NavEvent): NavState = events.fold(this) { s, e -> Nav.reduce(s, e) }

/** Every Holdout's stage in [this] view at [now], for [NavEvent.ChainMoved]. */
fun ChainView.stages(now: Long): Map<String, Stage> = holdouts.associate { it.address.base58() to it.stage(now) }

object Nav {
    fun reduce(s: NavState, e: NavEvent): NavState {
        // Before a wallet there is only the connect screen.
        if (!s.connected && e !is NavEvent.Connected) return s
        return when (e) {
            is NavEvent.Connected -> NavState(connected = true, tour = if (e.tourSeen) null else Tour(firstRun = true), stages = s.stages)
            NavEvent.Disconnected -> NavState()
            NavEvent.AppOpened -> s.copy(tab = Tab.Today, stacks = s.stacks - Tab.Today, sheet = null)
            NavEvent.OpenTour -> if (s.sitting != null || s.tx != null) s else s.copy(tour = Tour(firstRun = false), sheet = null)
            NavEvent.TourDone -> when {
                s.tour == null -> s
                s.tour.firstRun -> s.copy(tour = null, tab = Tab.Today, stacks = emptyMap())
                else -> s.copy(tour = null)
            }
            is NavEvent.SelectTab -> when {
                s.fullScreen || s.sheet != null -> s
                e.tab == s.tab -> s.copy(stacks = s.stacks - e.tab)
                else -> s.copy(tab = e.tab)
            }
            is NavEvent.GoToRoot -> if (s.fullScreen || s.sheet != null) s else s.copy(tab = e.tab, stacks = s.stacks - e.tab)
            is NavEvent.Open -> if (s.fullScreen || s.sheet != null || s.top == e.screen) s else s.push(e.screen)
            is NavEvent.Swap -> if (s.fullScreen || s.top == null) s else s.withStack(s.stack.dropLast(1) + e.screen)
            NavEvent.Back -> back(s) ?: s
            is NavEvent.OpenStake -> when {
                s.sitting != null || s.tour != null -> s
                else -> s.copy(tx = null, sheet = Sheet.Stake(e.cohort, e.tier, if (e.seekerKnown) SeekerStatus.Confirmed else SeekerStatus.Checking))
            }
            is NavEvent.StakeTier -> s.updateStake { it.copy(tier = e.tier) }
            is NavEvent.SeekerChecked -> s.updateStake { if (it.gate == SeekerStatus.Confirmed) it else it.copy(gate = e.status) }
            NavEvent.SeekerRetry -> s.updateStake { if (it.gate == SeekerStatus.Confirmed) it else it.copy(gate = SeekerStatus.Checking) }
            NavEvent.OpenFaucet -> when {
                s.sitting != null || s.tour != null -> s
                else -> s.copy(tx = null, sheet = Sheet.Faucet(returnTo = s.sheet as? Sheet.Stake ?: (s.sheet as? Sheet.Faucet)?.returnTo))
            }
            NavEvent.CloseSheet -> s.copy(sheet = (s.sheet as? Sheet.Faucet)?.returnTo)
            is NavEvent.StartSitting -> if (s.tour != null) s else s.copy(sitting = e.sitting, sheet = null, tx = null)
            NavEvent.SittingEnded -> if (s.sitting == null) s else s.copy(sitting = null, tab = Tab.Today, stacks = s.stacks - Tab.Today)
            is NavEvent.TxStarted -> when {
                // A sitting or the tour is never covered: a send that starts under one stays a notice.
                s.sitting != null || s.tour != null -> s
                s.tx?.kind == e.kind -> s.copy(tx = s.tx.copy(phase = TxPhase.WaitingForWallet, youreIn = false))
                else -> s.copy(tx = TxFlow(e.kind))
            }
            is NavEvent.TxProgress -> progress(s, e.phase)
            NavEvent.TxRetry -> s.tx?.let { s.copy(tx = it.copy(phase = TxPhase.WaitingForWallet, youreIn = false)) } ?: s
            NavEvent.TxBack -> txBack(s) ?: s
            NavEvent.TxDone -> s.tx?.let { tx ->
                s.copy(tx = null, sheet = null, tab = Tab.Holdouts, stacks = s.without { it == Screen.Claim(tx.kind.cohort) } - Tab.Holdouts)
            } ?: s
            NavEvent.JoinedUnderWay -> if (s.tx?.youreIn == true) s.copy(tx = null, sheet = null, tab = Tab.Today, stacks = s.stacks - Tab.Today) else s
            NavEvent.SeeLobby -> s.tx?.let { tx -> s.copy(tx = null, sheet = null).push(Screen.Lobby(tx.kind.cohort)) } ?: s
            NavEvent.SeeOpenHoldouts -> if (s.tx == null) s else s.copy(tx = null, sheet = null, tab = Tab.Holdouts, stacks = s.stacks - Tab.Holdouts)
            NavEvent.WhatHappensNow -> s.tx?.let { tx ->
                s.copy(tx = null, sheet = null, tab = Tab.Today, stacks = s.stacks + (Tab.Today to listOf(Screen.Forfeit(tx.kind.cohort))))
            } ?: s
            is NavEvent.ChainMoved -> chainMoved(s, e.stages)
        }
    }

    /** What a system Back does, or null when it is not the model's: it leaves the app, or the screen shown handles it. */
    fun back(s: NavState): NavState? = when {
        !s.connected -> null
        s.tx != null -> txBack(s)
        // The sitting has its own way out (✕, then the stopped summary).
        s.sitting != null -> null
        s.tour != null -> if (s.tour.firstRun) null else s.copy(tour = null)
        s.sheet != null -> s.copy(sheet = (s.sheet as? Sheet.Faucet)?.returnTo)
        // "You left" on the lobby: Back to Holdouts is H0, wherever the lobby was opened from.
        (s.top as? Screen.Lobby)?.let { s.stages[it.cohort] == Stage.NotJoined } == true ->
            s.copy(tab = Tab.Holdouts, stacks = s.without { it == s.top } - Tab.Holdouts)
        s.stack.isNotEmpty() -> s.withStack(s.stack.dropLast(1))
        s.tab != Tab.Today -> s.copy(tab = Tab.Today)
        else -> null
    }

    /** Leaving the signing flow: never while the wallet holds the screen — there is no cancel (G7). */
    private fun txBack(s: NavState): NavState? {
        val tx = s.tx ?: return null
        return when (val phase = tx.phase) {
            TxPhase.WaitingForWallet -> s
            // "You can leave this screen": back where the action started; a join's sheet closes with it.
            TxPhase.Confirming -> s.copy(tx = null, sheet = null)
            is TxPhase.Done -> when (phase.outcome) {
                // A join that cannot be tried again leaves its sheet too.
                TxOutcome.JoiningClosed -> s.copy(tx = null, sheet = null)
                is TxOutcome.Landed -> s.copy(tx = null, sheet = null)
                // Declined, failed, refused: back on the stake sheet, the claim, or Today.
                else -> s.copy(tx = null)
            }
        }
    }

    private fun progress(s: NavState, phase: TxPhase): NavState {
        val tx = s.tx ?: return s
        val landed = (phase as? TxPhase.Done)?.outcome is TxOutcome.Landed
        return when {
            // A join that lands goes straight to You're in (G7, B-D19).
            landed && tx.kind is TxKind.Join -> s.copy(tx = tx.copy(phase = phase, youreIn = true), sheet = null)
            // A day that lands returns to Today, which says it is on chain (G7).
            landed && tx.kind is TxKind.Submit -> s.copy(tx = null, tab = Tab.Today, stacks = s.stacks - Tab.Today)
            else -> s.copy(tx = tx.copy(phase = phase))
        }
    }

    /**
     * The dotted arrows of §4: what the chain moving a Holdout does to where the
     * member is. Only a change seen while the app is open moves anything, and
     * nothing moves under a full-screen step or a sheet — but a lobby that no
     * longer is one leaves every stack whatever is shown.
     */
    private fun chainMoved(s: NavState, stages: Map<String, Stage>): NavState {
        var n = s.copy(stages = stages)
        for ((cohort, now) in stages) {
            val before = s.stages[cohort] ?: continue
            if (before == now) continue
            val lobby = Screen.Lobby(cohort)
            val inLobby = now == Stage.Lobby || now == Stage.LobbyLocked
            val wasLobby = before == Stage.Lobby || before == Stage.LobbyLocked
            val lobbyShown = n.visible(lobby)
            if (!inLobby && n.stacks.values.any { lobby in it }) {
                // A leave that lands under the lobby shown: it stays to say "You left" until Back to Holdouts (see [back]).
                if (lobbyShown && now == Stage.NotJoined) continue
                n = n.copy(stacks = n.without { it == lobby })
                // Left (H3 Leave → H0) or started (H3 → T2).
                if (lobbyShown) {
                    n = if (now is Stage.Active) n.copy(tab = Tab.Today, stacks = n.stacks - Tab.Today) else n.copy(tab = Tab.Holdouts, stacks = n.stacks - Tab.Holdouts)
                }
                continue
            }
            if (n.fullScreen) continue
            when {
                // A join landed with no signing flow showing: the sheet gives way to the lobby.
                inLobby && !wasLobby && (n.sheet as? Sheet.Stake)?.cohort == cohort -> n = n.copy(sheet = null).push(lobby)
                n.sheet != null -> Unit
                // T2 -. missed a deadline .-> H6
                before is Stage.Active && now is Stage.Forfeited && n.tab == Tab.Today && n.onRoot -> n = n.push(Screen.Forfeit(cohort))
                // T2 -. last day passed .-> H4, the Standing root.
                before is Stage.Active && now == Stage.Settling && n.tab == Tab.Today && n.onRoot -> n = n.copy(tab = Tab.Standing, stacks = n.stacks - Tab.Standing)
                // T2 -. ended and settled between two reads .-> H4 then H7: as if it had passed through settling.
                before is Stage.Active && now is Stage.ReadyToClaim && n.tab == Tab.Today && n.onRoot ->
                    n = n.copy(tab = Tab.Standing, stacks = n.stacks - Tab.Standing).push(Screen.Claim(cohort))
                // H4 -. settled .-> H7 (from settling, or straight from the last day when the reads skipped it)
                (before == Stage.Settling || before is Stage.Active) && now is Stage.ReadyToClaim && (n.top == Screen.Detail(cohort) || (n.tab == Tab.Standing && n.onRoot)) -> n = n.push(Screen.Claim(cohort))
            }
        }
        return n
    }

    private fun NavState.visible(screen: Screen): Boolean = !fullScreen && sheet == null && top == screen

    private fun NavState.push(screen: Screen): NavState = if (top == screen) this else withStack(stack + screen)

    private fun NavState.withStack(stack: List<Screen>): NavState = copy(stacks = if (stack.isEmpty()) stacks - tab else stacks + (tab to stack))

    private fun NavState.without(drop: (Screen) -> Boolean): Map<Tab, List<Screen>> = stacks.mapValues { (_, v) -> v.filterNot(drop) }.filterValues { it.isNotEmpty() }

    private fun NavState.updateStake(change: (Sheet.Stake) -> Sheet.Stake): NavState = (sheet as? Sheet.Stake)?.let { copy(sheet = change(it)) } ?: this
}
