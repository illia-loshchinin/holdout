package holdout.app.ui

/**
 * A day's reps cut into the blocks that bank (a block of 10 banks for good):
 * 25 reps are `[10, 10, 5]`. The last block may be short; reps short of the
 * next block are never kept, so they are never drawn as banked. Today's hero
 * draws them as [holdout.app.home.BankBars] and the sitting as its own bars.
 */
fun bankBlocks(reps: Int, blockSize: Int = BLOCK_REPS): List<Int> {
    if (reps <= 0) return emptyList()
    return List(reps / blockSize) { blockSize } + listOfNotNull((reps % blockSize).takeIf { it > 0 })
}

/** Reps in one banked block. */
const val BLOCK_REPS = 10
