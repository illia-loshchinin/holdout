package holdout.app.sitting

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.repcounter.Stakes

/**
 * The words of the sitting path that are built from state: the top bar's label,
 * the time lines, the guide's level label and the level's rule sentence (boards
 * 15–20 and 6; rulings C-D2, G2, G3; 002 FR-014, FR-047). Pure, so a JVM test
 * can pin every one. Labels come back lower-case, as the boards write them, and
 * the screens upper-case them as they do every label.
 *
 * Nothing here is a calendar date or a clock time (constitution VI): the time
 * is a countdown ([Format.countdown]).
 */
object SittingCopy {
    /** The last ten minutes of a Holdout day (001 FR-047, ES-3): said before and during the sitting. */
    const val LAST_MINUTES = "Under 10 minutes left. Unfinished reps won't count."

    /** The Daily 25's stand-in for a level sentence: it is counted exactly as a Spark (002 FR-014). */
    const val COUNTED_AS_THE_SPARK = "counted as the Spark"

    /** The top bar while counting and on the camera and countdown screens: `day 12 · 25 reps` / `the daily 25`. */
    fun countingLabel(dayNumber: Int?, target: Int): String = if (dayNumber == null) DAILY else "day $dayNumber · $target reps"

    /** The top bar while getting into position. */
    fun positionLabel(dayNumber: Int?): String = "${if (dayNumber == null) DAILY else "day $dayNumber"} · get into position"

    /** Under the bar: a Holdout day counts down to its deadline, the Daily 25 to the end of today. */
    fun timeLine(seconds: Long, holdoutDay: Boolean): String = if (holdoutDay) "${Format.countdown(seconds)} to today's deadline" else "${Format.countdown(seconds)} left today"

    /** Beside the bars while counting. */
    fun timeLeft(seconds: Long): String = "${Format.countdown(seconds)} left"

    /** Opposite the set-up guide's title: `habit · 21-day`, `spark · 7-day`, `the daily 25`. */
    fun levelLabel(stakes: Stakes): String = when (stakes) {
        Stakes.STAKED -> "habit · 21-day"
        Stakes.EASY -> "spark · 7-day"
        Stakes.DAILY -> DAILY
    }

    /** The same label for a Holdout day, read from its cohort: a test Holdout of 3 days says `habit · 3-day`, not 21. */
    fun levelLabel(cohort: CohortSnapshot): String = Format.level(cohort).lowercase()

    /**
     * The sentence under a sitting's set-up and pre-flight page: the Holdout's
     * own level rule, word for word (`Stakes`, read by `Format.levelRule`), or
     * for the Daily 25 the caption that it is counted as the Spark. A Spark
     * never shows the Habit sentence, nor the reverse.
     */
    fun ruleCaption(stakes: Stakes): String = when (stakes) {
        Stakes.STAKED -> Stakes.STAKED_RULE
        Stakes.EASY -> Stakes.EASY_RULE
        Stakes.DAILY -> COUNTED_AS_THE_SPARK
    }

    private const val DAILY = "the daily 25"
}
