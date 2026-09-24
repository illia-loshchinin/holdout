package holdout.app.storage

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * Everything the device remembers (T045, FR-023, FR-024a, data-model §4).
 *
 * **Keys are scoped per wallet, per cohort (or [CHALLENGE]) and per day index**,
 * so two wallets on one phone never see each other's progress and yesterday's
 * banked reps can never be mistaken for today's.
 *
 * **Writes are synchronous, before anything shows as banked.** The member who
 * banks ten reps and loses the app to the system still has ten reps (FR-023).
 *
 * **Nothing here syncs anywhere.** Android backup and device transfer are both
 * opted out of in the manifest and the extraction rules, so no restore can
 * carry an unfinished day to another device (FR-024a, ES-3a). A wiped or
 * replaced phone simply starts that day at zero, and the member may still
 * finish it there before the deadline. A lost day is never recoverable, and
 * that is the design, not an omission.
 *
 * An interface, so the banking and streak rules can be tested on the JVM with
 * no phone in the room — the same reason `repcounter` is a plain module.
 */
interface Store {

    fun bankedReps(wallet: String, scope: String, dayIndex: Long): Int
    fun setBankedReps(wallet: String, scope: String, dayIndex: Long, reps: Int)

    fun isDayComplete(wallet: String, scope: String, dayIndex: Long): Boolean
    fun markDayComplete(wallet: String, scope: String, dayIndex: Long)

    /**
     * Forget one day's banked reps and completion. **Test builds only** — the
     * product never un-banks a rep (FR-023); this exists so a day can be done
     * again on the same phone while the counter is being tuned.
     */
    fun clearDay(wallet: String, scope: String, dayIndex: Long)

    fun streak(wallet: String): Int
    fun lastStreakDay(wallet: String): Long
    fun setStreak(wallet: String, streak: Int, dayIndex: Long)

    fun longestStreak(wallet: String): Int
    fun setLongestStreak(wallet: String, streak: Int)

    fun hasSeenTour(wallet: String): Boolean
    fun setSeenTour(wallet: String)

    /** Whether the member asked not to see the sitting's set-up guide again, for these [stakes]. */
    fun isSetupGuideHidden(wallet: String, stakes: String): Boolean
    fun hideSetupGuide(wallet: String, stakes: String)

    companion object {
        /** The scope for the free Daily 25, which belongs to no cohort. */
        const val CHALLENGE = "challenge"

        /** Never set: a wallet that has never completed a day has no last day. */
        const val NO_DAY = Long.MIN_VALUE
    }
}

/**
 * MMKV backs it: an mmap'd store, so a write is durable as soon as it returns
 * and survives a process kill with no explicit flush. That is exactly what
 * FR-023 asks of banked reps.
 */
class MmkvStore private constructor(private val kv: MMKV) : Store {

    companion object {
        fun create(context: Context): MmkvStore {
            MMKV.initialize(context)
            return MmkvStore(requireNotNull(MMKV.defaultMMKV()) { "MMKV failed to open" })
        }
    }

    private fun dayKey(wallet: String, scope: String, dayIndex: Long, field: String) = "$field:$wallet:$scope:$dayIndex"

    override fun bankedReps(wallet: String, scope: String, dayIndex: Long): Int = kv.decodeInt(dayKey(wallet, scope, dayIndex, "banked"), 0)

    override fun setBankedReps(wallet: String, scope: String, dayIndex: Long, reps: Int) {
        kv.encode(dayKey(wallet, scope, dayIndex, "banked"), reps)
    }

    override fun isDayComplete(wallet: String, scope: String, dayIndex: Long): Boolean = kv.decodeBool(dayKey(wallet, scope, dayIndex, "done"), false)

    override fun markDayComplete(wallet: String, scope: String, dayIndex: Long) {
        kv.encode(dayKey(wallet, scope, dayIndex, "done"), true)
    }

    override fun clearDay(wallet: String, scope: String, dayIndex: Long) {
        kv.removeValuesForKeys(
            arrayOf(dayKey(wallet, scope, dayIndex, "banked"), dayKey(wallet, scope, dayIndex, "done")),
        )
    }

    override fun streak(wallet: String): Int = kv.decodeInt("streak:$wallet", 0)

    override fun lastStreakDay(wallet: String): Long = kv.decodeLong("streak_day:$wallet", Store.NO_DAY)

    override fun setStreak(wallet: String, streak: Int, dayIndex: Long) {
        kv.encode("streak:$wallet", streak)
        kv.encode("streak_day:$wallet", dayIndex)
    }

    override fun longestStreak(wallet: String): Int = kv.decodeInt("streak_best:$wallet", 0)

    override fun setLongestStreak(wallet: String, streak: Int) {
        kv.encode("streak_best:$wallet", streak)
    }

    override fun hasSeenTour(wallet: String): Boolean = kv.decodeBool("tour:$wallet", false)

    override fun isSetupGuideHidden(wallet: String, stakes: String): Boolean = kv.decodeBool("setup_hidden:$wallet:$stakes", false)

    override fun hideSetupGuide(wallet: String, stakes: String) {
        kv.encode("setup_hidden:$wallet:$stakes", true)
    }

    override fun setSeenTour(wallet: String) {
        kv.encode("tour:$wallet", true)
    }
}

/** In-memory, for unit tests and Compose previews. */
class InMemoryStore : Store {
    private val values = mutableMapOf<String, Any>()

    private fun dayKey(wallet: String, scope: String, dayIndex: Long, field: String) = "$field:$wallet:$scope:$dayIndex"

    override fun bankedReps(wallet: String, scope: String, dayIndex: Long): Int = values[dayKey(wallet, scope, dayIndex, "banked")] as? Int ?: 0

    override fun setBankedReps(wallet: String, scope: String, dayIndex: Long, reps: Int) {
        values[dayKey(wallet, scope, dayIndex, "banked")] = reps
    }

    override fun isDayComplete(wallet: String, scope: String, dayIndex: Long): Boolean = values[dayKey(wallet, scope, dayIndex, "done")] as? Boolean ?: false

    override fun markDayComplete(wallet: String, scope: String, dayIndex: Long) {
        values[dayKey(wallet, scope, dayIndex, "done")] = true
    }

    override fun clearDay(wallet: String, scope: String, dayIndex: Long) {
        values.remove(dayKey(wallet, scope, dayIndex, "banked"))
        values.remove(dayKey(wallet, scope, dayIndex, "done"))
    }

    override fun streak(wallet: String): Int = values["streak:$wallet"] as? Int ?: 0

    override fun lastStreakDay(wallet: String): Long = values["streak_day:$wallet"] as? Long ?: Store.NO_DAY

    override fun setStreak(wallet: String, streak: Int, dayIndex: Long) {
        values["streak:$wallet"] = streak
        values["streak_day:$wallet"] = dayIndex
    }

    override fun longestStreak(wallet: String): Int = values["streak_best:$wallet"] as? Int ?: 0

    override fun setLongestStreak(wallet: String, streak: Int) {
        values["streak_best:$wallet"] = streak
    }

    override fun hasSeenTour(wallet: String): Boolean = values["tour:$wallet"] as? Boolean ?: false

    override fun isSetupGuideHidden(wallet: String, stakes: String): Boolean = values["setup_hidden:$wallet:$stakes"] as? Boolean ?: false

    override fun hideSetupGuide(wallet: String, stakes: String) {
        values["setup_hidden:$wallet:$stakes"] = true
    }

    override fun setSeenTour(wallet: String) {
        values["tour:$wallet"] = true
    }
}
