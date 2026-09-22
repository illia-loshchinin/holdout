package holdout.core

/**
 * Chain time on the device (research R7, T033).
 *
 * **Never the wall clock.** Every deadline in Holdout is judged by chain time
 * (FR-017), and a device clock can be wrong by minutes or moved on purpose. So
 * the app caches what the chain last said and advances it with a monotonic
 * elapsed-time source — `SystemClock.elapsedRealtime()` on Android — which
 * counts real seconds since boot and cannot be set by anyone.
 *
 * The drift this leaves is the difference between the phone's crystal and the
 * cluster's, over the minutes between reads. That is seconds at worst, and the
 * app refreshes whenever a cohort screen is visible.
 *
 * With no reading yet, [nowSeconds] returns null rather than guessing. A screen
 * that cannot say what time it is says so; it does not show a countdown built
 * on a number nobody vouched for.
 */
class ChainClock(
    /** Monotonic milliseconds since an arbitrary origin. Must never go backwards. */
    private val elapsedRealtimeMillis: () -> Long,
) {
    private data class Reading(
        val chainSeconds: Long,
        val slot: Long,
        val readAtElapsedMillis: Long,
    )

    @Volatile
    private var reading: Reading? = null

    /** Record what the cluster just said. */
    fun observe(chainSeconds: Long, slot: Long) {
        reading = Reading(chainSeconds, slot, elapsedRealtimeMillis())
    }

    /** Chain time now, or null when the chain has not been read yet. */
    fun nowSeconds(): Long? {
        val last = reading ?: return null
        val elapsed = (elapsedRealtimeMillis() - last.readAtElapsedMillis) / 1000
        return last.chainSeconds + elapsed.coerceAtLeast(0)
    }

    val slot: Long? get() = reading?.slot

    val hasReading: Boolean get() = reading != null
}

/**
 * The global day, and the 05:00 UTC boundary everything turns over on.
 *
 * Cohorts start Mondays at 05:00 UTC, deadlines fall there, and the Daily 25's
 * streak turns over there too (FR-049…FR-052, ES-9). One definition, used by
 * the contract's schedule maths and by every screen.
 */
object HoldoutDay {

    /** 05:00 UTC, in seconds past midnight. */
    const val BOUNDARY_OFFSET_SECONDS = 18_000L
    const val SECONDS_PER_DAY = 86_400L

    /** Which global day a chain timestamp falls in. Matches the program's `schedule.rs`. */
    fun indexOf(chainSeconds: Long): Long = Math.floorDiv(chainSeconds - BOUNDARY_OFFSET_SECONDS, SECONDS_PER_DAY)

    /** When the given day ends — the instant the streak turns over. */
    fun endOf(dayIndex: Long): Long = (dayIndex + 1) * SECONDS_PER_DAY + BOUNDARY_OFFSET_SECONDS

    /** Seconds left in the day containing [chainSeconds]. Never negative. */
    fun secondsLeftIn(chainSeconds: Long): Long = (endOf(indexOf(chainSeconds)) - chainSeconds).coerceAtLeast(0)
}
