package holdout.app.streak

import holdout.app.storage.Store
import holdout.repcounter.RepStance

/**
 * The Daily 25's toes meter (H35): of today's reps measured at 45°, how many
 * Hard's own rule would have counted — "18 of 25 from your toes". A free,
 * personal yardstick on the way from Easy to Hard. It gates nothing, pays
 * nothing and is on no leaderboard (FR-051); like the streak, it lives on the
 * phone. Head-on reps are not measured at all: the legs point into the lens,
 * and the meter does not guess.
 */
class ToesMeter(private val store: Store, private val target: Int) {

    data class Meter(
        /** Today's reps from the toes, never more than the target. */
        val toes: Int,
        /** Today's reps measured at 45°, never more than the target. */
        val measured: Int,
        /** Reps from the toes on each of the last seven days, oldest first. */
        val week: List<Int>,
        /** The most from the toes on any one day. */
        val best: Int,
    ) {
        /** Every one of the day's reps from the toes: a day Hard would have counted. */
        val hardDay: Boolean get() = measured > 0 && toes == measured
    }

    /** One counted rep of the Daily 25 on [dayIndex]; [stance] is null head-on. */
    fun record(wallet: String, dayIndex: Long, stance: RepStance?) {
        if (stance == null) return
        val (toes, measured) = store.toesDay(wallet, dayIndex)
        if (measured >= target) return
        val nextToes = if (stance == RepStance.TOES) toes + 1 else toes
        store.setToesDay(wallet, dayIndex, nextToes, measured + 1)
        if (nextToes > store.bestToes(wallet)) store.setBestToes(wallet, nextToes)
    }

    fun read(wallet: String, dayIndex: Long): Meter {
        val (toes, measured) = store.toesDay(wallet, dayIndex)
        return Meter(
            toes = toes,
            measured = measured,
            week = (dayIndex - 6..dayIndex).map { store.toesDay(wallet, it).first },
            best = store.bestToes(wallet),
        )
    }
}
