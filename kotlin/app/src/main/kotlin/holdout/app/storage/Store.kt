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

    /**
     * When the last Daily 25 sitting started while chain time was unknown, on
     * this boot's monotonic clock — so the reps it banked under [UNKNOWN_DAY]
     * can be put on their real day once the chain answers (`OfflineDays`).
     */
    fun offlineSittingMark(wallet: String): OfflineMark?
    fun setOfflineSittingMark(wallet: String, mark: OfflineMark?)

    /** The chain's last answer, kept so an app restart offline still knows the day (research R7). */
    fun chainReading(): SavedChainReading?
    fun setChainReading(reading: SavedChainReading)

    companion object {
        /** The scope for the free Daily 25, which belongs to no cohort. */
        const val CHALLENGE = "challenge"

        /** Never set: a wallet that has never completed a day has no last day. */
        const val NO_DAY = Long.MIN_VALUE

        /**
         * Where a Daily 25 banks before the chain has ever been read. Never
         * shown as a day: `OfflineDays` moves it onto the day it was done.
         */
        const val UNKNOWN_DAY = Long.MIN_VALUE + 1
    }
}

/**
 * A moment on one boot's monotonic clock: meaningless after a reboot, so the
 * boot is kept with it. [settled] journals a transfer onto a real day before
 * it is applied, so a transfer killed half-way is finished, never repeated.
 */
data class OfflineMark(val boot: Int, val elapsedMillis: Long, val settled: SettledOffline? = null)

/** An offline bucket being moved onto [day]: the day's total afterwards, absolute, and whether it completes it. */
data class SettledOffline(val day: Long, val total: Int, val complete: Boolean)

/** What the chain said, and when on this boot's monotonic clock it said it. */
data class SavedChainReading(val chainSeconds: Long, val slot: Long, val boot: Int, val elapsedMillis: Long)

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

    // The streak and its day are one record, written in one go: a process
    // killed between two writes would otherwise leave a count with the wrong
    // day, and a later recovery would add to it twice. The two older keys are
    // still read for a record written before this.
    private fun streakRecord(wallet: String): Pair<Int, Long>? = kv.decodeString("streak_rec:$wallet")?.split(',')?.let { (count, day) -> count.toInt() to day.toLong() }

    override fun streak(wallet: String): Int = streakRecord(wallet)?.first ?: kv.decodeInt("streak:$wallet", 0)

    override fun lastStreakDay(wallet: String): Long = streakRecord(wallet)?.second ?: kv.decodeLong("streak_day:$wallet", Store.NO_DAY)

    override fun setStreak(wallet: String, streak: Int, dayIndex: Long) {
        kv.encode("streak_rec:$wallet", "$streak,$dayIndex")
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

    // One record too: `boot,elapsed` or, once a transfer is journalled,
    // `boot,elapsed,day,total,complete`.
    override fun offlineSittingMark(wallet: String): OfflineMark? {
        val f = kv.decodeString("offline_rec:$wallet")?.split(',') ?: return null
        val settled = if (f.size >= 5) SettledOffline(f[2].toLong(), f[3].toInt(), f[4].toBoolean()) else null
        return OfflineMark(f[0].toInt(), f[1].toLong(), settled)
    }

    override fun setOfflineSittingMark(wallet: String, mark: OfflineMark?) {
        if (mark == null) {
            kv.removeValueForKey("offline_rec:$wallet")
        } else {
            val settled = mark.settled?.let { ",${it.day},${it.total},${it.complete}" }.orEmpty()
            kv.encode("offline_rec:$wallet", "${mark.boot},${mark.elapsedMillis}$settled")
        }
    }

    // One record, written in one go: a process killed between separate writes
    // would pair a new chain time with an old uptime, and the restored clock
    // would run ahead by the gap — up to the ten-minute refresh.
    override fun chainReading(): SavedChainReading? {
        val f = kv.decodeString("chain_rec")?.split(',')?.takeIf { it.size == 4 } ?: return null
        return SavedChainReading(f[0].toLong(), f[1].toLong(), f[2].toInt(), f[3].toLong())
    }

    override fun setChainReading(reading: SavedChainReading) {
        kv.encode("chain_rec", "${reading.chainSeconds},${reading.slot},${reading.boot},${reading.elapsedMillis}")
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

    override fun offlineSittingMark(wallet: String): OfflineMark? = values["offline:$wallet"] as? OfflineMark

    override fun setOfflineSittingMark(wallet: String, mark: OfflineMark?) {
        if (mark == null) values.remove("offline:$wallet") else values["offline:$wallet"] = mark
    }

    override fun chainReading(): SavedChainReading? = values["chain"] as? SavedChainReading

    override fun setChainReading(reading: SavedChainReading) {
        values["chain"] = reading
    }
}
