package holdout.app.claim

import holdout.app.ui.Format
import holdout.core.CohortSnapshot

/**
 * The claim screen's words (H7, board 7), kept pure so they can be tested: by
 * level and length, never a date or a clock time, and never an amount in
 * dollars (constitution VI, XIII; rulings G3, G4, D-D1).
 */
internal object ClaimText {
    const val FOOTER = "One signature. It waits here until you claim."
    const val NO_SOL = "Not enough devnet SOL for the fee. Test tokens include some."
    const val GET_TOKENS = "Get test tokens"
    const val NOBODY_DROPPED = "Nobody dropped out: you get exactly your stake back."

    /** `Spark lit: you held out 7 days.` / `You held out 21 days.` */
    fun opener(c: CohortSnapshot): String {
        val held = "you held out ${c.durationDays} ${if (c.durationDays == 1) "day" else "days"}"
        return if (Format.levelName(c.form) == "Spark") "Spark lit: $held." else held.replaceFirstChar { it.uppercase() } + "."
    }

    /** Members who finished: `Σ finishers_per_tier` of the settled Holdout. */
    fun finishers(c: CohortSnapshot): Long = c.finishersPerTier.sum()

    /**
     * Who dropped out and who split the pool, from the settled Holdout alone:
     * `4 of 12 dropped out. The 8 who finished split the pool and its boost.`, or
     * `Nobody dropped out: you get exactly your stake back.` Null when the
     * Holdout records no finisher (nothing is shared, nothing to say).
     */
    fun droppedLine(c: CohortSnapshot): String? {
        val finished = finishers(c)
        if (finished <= 0) return null
        val dropped = c.memberCount - finished
        return when {
            dropped <= 0 -> NOBODY_DROPPED
            finished == 1L -> "$dropped of ${c.memberCount} dropped out. The 1 who finished takes the pool and its boost."
            else -> "$dropped of ${c.memberCount} dropped out. The $finished who finished split the pool and its boost."
        }
    }

    /** The button: `Claim 1,412 SKR`. */
    fun button(amount: ULong): String = "Claim ${Format.skr(amount)}"

    /** The amount's number and the unit on their own: the StatBlock shows the number, its caption the unit. */
    fun amountNumber(amount: ULong): String = Format.skr(amount).removeSuffix(" SKR")

    /** The StatBlock caption under the amount. */
    const val AMOUNT_CAPTION = "SKR · yours to claim"
    const val CLAIMED_CAPTION = "SKR · claimed"
}
