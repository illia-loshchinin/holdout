package holdout.app.holdout

/**
 * A member's "Check again" while the chain can't be reached (spec 005): the
 * read the app makes every 30 seconds, asked for now. Offered only while the
 * chain can't be reached (two snapshot reads in a row failed, 001 FR-071); a second ask while one is out joins it. Any read that
 * answers — this one or the schedule's — leaves nothing to show. It is never
 * kept on the phone, and it never sends anything (FR-004): what a waiting day
 * does once the chain answers is 001 FR-024b's, unchanged.
 */
data class ChainCheck(
    /** A check's read is out. */
    val checking: Boolean = false,
    /** The last check ended and the chain did not answer. */
    val stillUnreachable: Boolean = false,
) {
    /** What to keep, and whether to start a read. */
    data class Step(val next: ChainCheck, val read: Boolean)

    /** The member asked, by the button or the pull. [offline]: the last chain read failed. */
    fun request(offline: Boolean): Step = if (!offline || checking) Step(this, read = false) else Step(copy(checking = true), read = true)

    /** The check's read came back; [answered] when the chain answered it. */
    fun finished(answered: Boolean): ChainCheck = if (answered) ChainCheck() else ChainCheck(stillUnreachable = true)

    /** Some read answered: the chain can be reached, so there is nothing to check. */
    fun answered(): ChainCheck = ChainCheck()
}

/**
 * The words of the devnet bar's *Retry* (spec 005, contracts/check-again-ui.md; reworded by 001 FR-069h,
 * 2026-10-14): they name the network, never "offline". A line starts with the network, so a local build's
 * "The local validator" keeps its capital.
 */
object CheckAgainCopy {
    const val BUTTON = "Retry"

    /** The button while a check's read is out. */
    const val CHECKING = "Checking…"

    fun stillUnreachable(network: String) = "$network still isn't answering"

    fun unreachable(network: String) = "$network isn't answering"
}
