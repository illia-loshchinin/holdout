package holdout.app.nav

/**
 * Which screen a view-model message belongs to. `HoldoutViewModel` keeps the last action's
 * message ("Day 3 is on chain.", a declined signature) for the screen that action was taken
 * on; it must not turn up on another — it once showed on the claim screen. A message ends
 * when the member leaves that screen: another tab, a screen pushed or popped, a sheet opened
 * or closed. The signing flow and a sitting are not screens of their own for this: they open
 * over the screen that asked for them, and a send's outcome is read back there.
 */
object MessageScope {
    /** The screen the member is on, as the tab, its top pushed screen and the kind of sheet over it. */
    private fun screen(s: NavState): Triple<Tab, Screen?, Any?> = Triple(s.tab, s.top, s.sheet?.let { it::class })

    /** Whether the move from [before] to [after] leaves the screen a message was said on. */
    fun left(before: NavState, after: NavState): Boolean = screen(before) != screen(after)
}
