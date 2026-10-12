package holdout.app.nav

import holdout.core.Leaderboard

/**
 * [NavState] as plain strings, to survive the process dying behind the app
 * (`rememberSaveable` in `HoldoutApp`). Pure Kotlin, tested on the JVM.
 *
 * ```
 *  v1
 *  tab|Holdouts
 *  stack|Holdouts|Lobby|7xKXtg…        one line per pushed screen, bottom first
 * ```
 *
 * What is kept is the tab and the screens pushed on each tab, a Holdout by its
 * base58 address. A sheet, the signing flow, a sitting and the tour are not:
 * the wallet and the camera cannot be resumed, banked reps are on disk and
 * come back through Today's crash page, and the first-run tour is decided from
 * the store. The chain's last stages are not kept either, so the first read
 * after a restart is not mistaken for a change that moves the member.
 *
 * Anything unreadable is dropped, never guessed: the member lands on Today.
 */
object NavSaved {
    private const val VERSION = "v1"

    /** Nothing when there is no wallet or the first-run tour is up: that state is made again from the store. */
    fun encode(s: NavState): List<String> {
        if (!s.connected || s.tour != null) return emptyList()
        return buildList {
            add(VERSION)
            add("tab|${s.tab.name}")
            for ((tab, screens) in s.stacks) for (screen in screens) encode(screen)?.let { add("stack|${tab.name}|$it") }
        }
    }

    /** The saved state, or a disconnected [NavState] when there is none (or it is from another version). */
    fun decode(saved: List<String>?): NavState {
        if (saved.isNullOrEmpty() || saved.first() != VERSION) return NavState()
        var tab = Tab.Today
        val stacks = mutableMapOf<Tab, List<Screen>>()
        for (line in saved.drop(1)) {
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "tab" -> Tab.entries.firstOrNull { it.name == parts.getOrNull(1) }?.let { tab = it }
                "stack" -> {
                    val on = Tab.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: continue
                    val screen = decodeScreen(parts.drop(2)) ?: continue
                    stacks[on] = stacks[on].orEmpty() + screen
                }
            }
        }
        return NavState(connected = true, tab = tab, stacks = stacks)
    }

    private fun encode(screen: Screen): String? = when (screen) {
        is Screen.Lobby -> "Lobby|${screen.cohort}"
        is Screen.Detail -> "Detail|${screen.cohort}"
        is Screen.Roster -> "Roster|${screen.cohort}"
        is Screen.Forfeit -> "Forfeit|${screen.cohort}"
        is Screen.Claim -> "Claim|${screen.cohort}"
        is Screen.Leaderboards -> "Leaderboards|${screen.board.name}"
        Screen.About -> "About"
    }

    private fun decodeScreen(parts: List<String>): Screen? {
        val arg = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
        return when (parts.firstOrNull()) {
            "Lobby" -> arg?.let { Screen.Lobby(it) }
            "Detail" -> arg?.let { Screen.Detail(it) }
            "Roster" -> arg?.let { Screen.Roster(it) }
            "Forfeit" -> arg?.let { Screen.Forfeit(it) }
            "Claim" -> arg?.let { Screen.Claim(it) }
            "Leaderboards" -> Leaderboard.Board.entries.firstOrNull { it.name == arg }?.let { Screen.Leaderboards(it) }
            "About" -> Screen.About
            else -> null
        }
    }
}
