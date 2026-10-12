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

    /**
     * The streak, its day and the [freezes] held after it, in one write: a
     * freeze earned or used on [dayIndex] can never be lost, or kept, apart
     * from the day that earned or used it.
     */
    fun setStreak(wallet: String, streak: Int, dayIndex: Long, freezes: Int)

    fun longestStreak(wallet: String): Int
    fun setLongestStreak(wallet: String, streak: Int)

    /** Streak freezes held, 0–2: earned when the streak reaches a multiple of seven (H35, FR-049). */
    fun streakFreezes(wallet: String): Int

    /** The toes meter (H35): a day's reps from the toes, and the reps measured at 45°. */
    fun toesDay(wallet: String, dayIndex: Long): Pair<Int, Int>
    fun setToesDay(wallet: String, dayIndex: Long, toes: Int, measured: Int)
    fun bestToes(wallet: String): Int
    fun setBestToes(wallet: String, toes: Int)

    fun hasSeenTour(wallet: String): Boolean
    fun setSeenTour(wallet: String)

    /** Whether the member asked not to see the sitting's set-up guide again, for these [stakes]. */
    fun isSetupGuideHidden(wallet: String, stakes: String): Boolean
    fun hideSetupGuide(wallet: String, stakes: String)

    /**
     * `sitting:{wallet}:{scope}` (data-model §4, ES-1, ES-2): when counting started
     * (epoch millis), or null. Written when counting starts and removed on every
     * clean exit of the sitting; found set when that sitting's screen is next
     * opened, the app died mid-sitting. [scope] is a cohort or [CHALLENGE]. It
     * never changes a banked figure and the counter never reads it.
     */
    fun sittingStartedAt(wallet: String, scope: String): Long?
    fun setSittingStartedAt(wallet: String, scope: String, startedAtMillis: Long?)

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

    /**
     * The connected wallet and the auth token its wallet app issued, so a
     * restart reconnects without asking again (contracts/android-app §3).
     * Null after a disconnect.
     */
    fun walletSession(): WalletSession?
    fun setWalletSession(session: WalletSession?)

    /**
     * A cohort day completed on this phone and not yet on chain — offline, or
     * the send failed — waiting to be sent: by itself once the chain answers,
     * or by "Done — tap to submit" if that send did not land (FR-024b, T066, T098).
     * The cohort day index, or null when nothing is waiting.
     */
    fun pendingCohortDay(wallet: String, cohort: String): Int?
    fun setPendingCohortDay(wallet: String, cohort: String, day: Int?)

    /**
     * The pending day already sent while the chain could be reached, so a
     * member who declined it in the wallet is not asked again after the app
     * restarts — only once the connection drops and returns (T098). Null when
     * nothing is held back.
     */
    fun autoSubmitMark(wallet: String): AutoSubmitMark?
    fun setAutoSubmitMark(wallet: String, mark: AutoSubmitMark?)

    /** Debug builds: whether sittings record the camera as video (constitution X, v1.5.0). Off until switched on. */
    fun recordsVideo(): Boolean
    fun setRecordsVideo(on: Boolean)

    /** Whether the member has been asked for notification permission — asked once, at the first join (FR-068a). */
    fun askedForNotifications(): Boolean
    fun setAskedForNotifications()

    /** The last answered Seeker ID lookup for [wallet] (spec 003 data-model §2), or null if none was kept. */
    fun seekerName(wallet: String): KeptName?

    /** Keeps [kept] for [wallet]. Holds at most [SEEKER_NAMES_MAX] wallets; the oldest check goes first. */
    fun setSeekerName(wallet: String, kept: KeptName)

    /** Every kept wallet that has a Seeker ID: wallet base58 → name. */
    fun allSeekerNames(): Map<String, String>

    /**
     * That [wallet] was found to hold a Seeker Genesis Token (`seeker:{wallet}`,
     * data-model §4, FR-003b), or null. Only a confirmation is ever kept, so an
     * outage never blocks a confirmed owner and a wallet without one is checked
     * again on its next join (FR-003d).
     */
    fun seekerConfirmation(wallet: String): SeekerConfirmation?
    fun setSeekerConfirmation(wallet: String, confirmation: SeekerConfirmation)

    /**
     * How many were still standing in [cohort] when [wallet] last looked at its
     * Standing (`looked:{wallet}:{cohort}`), or null: the Standing tab's dot is
     * for people who dropped since (DS BottomNav).
     */
    fun standingLooked(wallet: String, cohort: String): Int?
    fun setStandingLooked(wallet: String, cohort: String, standing: Int)

    companion object {
        /** Kept Seeker ID entries at most (research R7): every member a judge could meet, under 0.5 MB. */
        const val SEEKER_NAMES_MAX = 5_000

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

/**
 * An offline bucket being moved onto [day]: the day's total afterwards, absolute,
 * whether it completes it, and its toes meter afterwards, absolute too.
 */
data class SettledOffline(val day: Long, val total: Int, val complete: Boolean, val toes: Int = 0, val measured: Int = 0)

/**
 * The streak as MMKV holds it, one record: `streak,day,longest,freezes`. A
 * record from before the longest stops at `day`, and one from before freezes
 * at `longest`; those read [legacyFreezes], the freezes' old separate key.
 */
data class StreakRecord(val streak: Int, val day: Long, val longest: Int, val freezes: Int) {
    fun encode(): String = "$streak,$day,$longest,$freezes"

    companion object {
        fun decode(record: String, legacyFreezes: Int): StreakRecord {
            val f = record.split(',')
            return StreakRecord(f[0].toInt(), f[1].toLong(), f.getOrNull(2)?.toInt() ?: 0, f.getOrNull(3)?.toInt() ?: legacyFreezes)
        }
    }
}

/** A wallet address and the auth token that lets the app reuse its authorisation. */
data class WalletSession(val address: String, val authToken: String?)

/**
 * A wallet's last answered Seeker ID lookup (spec 003 data-model §2): its
 * chosen [name], or null for "checked, has none", and when it was checked on
 * the phone's wall clock — which decides nothing but when to ask again.
 */
data class KeptName(val name: String?, val checkedAtMillis: Long) {
    /** `<checkedAtMillis>|<name>`, the name empty for none. The time comes first: a name may hold a `|`. */
    fun encode(): String = "$checkedAtMillis|${name.orEmpty()}"

    companion object {
        fun decode(value: String): KeptName? {
            val bar = value.indexOf('|').takeIf { it > 0 } ?: return null
            val at = value.substring(0, bar).toLongOrNull() ?: return null
            return KeptName(value.substring(bar + 1).ifEmpty { null }, at)
        }

        /**
         * The wallet to forget so [adding] fits under [max]: the one checked
         * longest ago, or null when there is room or [adding] is already kept.
         */
        fun toDrop(kept: Map<String, KeptName>, adding: String, max: Int = Store.SEEKER_NAMES_MAX): String? = if (adding in kept || kept.size < max) null else kept.minByOrNull { it.value.checkedAtMillis }?.key
    }
}

/**
 * A wallet's Seeker Genesis Token, found on mainnet (001 FR-003b, data-model §4):
 * its [mint], the [tokenAccount] holding it — the two accounts a join sends
 * once the contract checks the token at mainnet (research R17) — and when it
 * was confirmed on the phone's wall clock, which decides nothing.
 */
data class SeekerConfirmation(val mint: String, val confirmedAtMillis: Long, val tokenAccount: String? = null) {
    /** `<confirmedAtMillis>|<mint>|<tokenAccount>`, the last empty when unknown. */
    fun encode(): String = "$confirmedAtMillis|$mint|${tokenAccount.orEmpty()}"

    companion object {
        fun decode(value: String): SeekerConfirmation? {
            val parts = value.split('|')
            val at = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val mint = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
            return SeekerConfirmation(mint, at, parts.getOrNull(2)?.takeIf { it.isNotEmpty() })
        }
    }
}

/** [day] of [cohort] was sent while the chain could be reached; [lost] if the network has gone since. */
data class AutoSubmitMark(val cohort: String, val day: Int, val lost: Boolean)

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

    // The streak, its day and the freezes are one record, written in one go:
    // a process killed between two writes would otherwise leave a count with
    // the wrong day, and a later recovery would add to it twice — or a day
    // with the freezes from before it, which nothing would ever correct. The
    // older separate keys are still read for a record written before this.
    private fun legacyFreezes(wallet: String): Int = kv.decodeInt("streak_freezes:$wallet", 0)

    private fun streakRecord(wallet: String): StreakRecord? = kv.decodeString("streak_rec:$wallet")?.let { StreakRecord.decode(it, legacyFreezes(wallet)) }

    override fun streak(wallet: String): Int = streakRecord(wallet)?.streak ?: kv.decodeInt("streak:$wallet", 0)

    override fun lastStreakDay(wallet: String): Long = streakRecord(wallet)?.day ?: kv.decodeLong("streak_day:$wallet", Store.NO_DAY)

    override fun setStreak(wallet: String, streak: Int, dayIndex: Long, freezes: Int) {
        // The longest rides in the same record, so a record-breaking day can
        // never be saved without its record.
        val longest = maxOf(longestStreak(wallet), streak)
        kv.encode("streak_rec:$wallet", StreakRecord(streak, dayIndex, longest, freezes).encode())
    }

    override fun longestStreak(wallet: String): Int = maxOf(streakRecord(wallet)?.longest ?: 0, kv.decodeInt("streak_best:$wallet", 0))

    override fun setLongestStreak(wallet: String, streak: Int) {
        kv.encode("streak_best:$wallet", streak)
    }

    override fun streakFreezes(wallet: String): Int = streakRecord(wallet)?.freezes ?: legacyFreezes(wallet)

    override fun toesDay(wallet: String, dayIndex: Long): Pair<Int, Int> = kv.decodeString("toes:$wallet:$dayIndex")?.split(',')?.let { it[0].toInt() to it[1].toInt() } ?: (0 to 0)

    override fun setToesDay(wallet: String, dayIndex: Long, toes: Int, measured: Int) {
        // One record, so the two can never disagree after a kill.
        kv.encode("toes:$wallet:$dayIndex", "$toes,$measured")
    }

    override fun bestToes(wallet: String): Int = kv.decodeInt("toes_best:$wallet", 0)

    override fun setBestToes(wallet: String, toes: Int) {
        kv.encode("toes_best:$wallet", toes)
    }

    override fun hasSeenTour(wallet: String): Boolean = kv.decodeBool("tour:$wallet", false)

    override fun isSetupGuideHidden(wallet: String, stakes: String): Boolean = kv.decodeBool("setup_hidden:$wallet:$stakes", false)

    override fun hideSetupGuide(wallet: String, stakes: String) {
        kv.encode("setup_hidden:$wallet:$stakes", true)
    }

    override fun sittingStartedAt(wallet: String, scope: String): Long? = kv.decodeLong("sitting:$wallet:$scope", NO_MARKER).takeIf { it != NO_MARKER }

    override fun setSittingStartedAt(wallet: String, scope: String, startedAtMillis: Long?) {
        if (startedAtMillis == null) kv.removeValueForKey("sitting:$wallet:$scope") else kv.encode("sitting:$wallet:$scope", startedAtMillis)
    }

    override fun setSeenTour(wallet: String) {
        kv.encode("tour:$wallet", true)
    }

    // One record too: `boot,elapsed` or, once a transfer is journalled,
    // `boot,elapsed,day,total,complete,toes,measured` (a journal from before
    // the toes meter stops at `complete`).
    override fun offlineSittingMark(wallet: String): OfflineMark? {
        val f = kv.decodeString("offline_rec:$wallet")?.split(',') ?: return null
        val settled = if (f.size >= 5) {
            SettledOffline(f[2].toLong(), f[3].toInt(), f[4].toBoolean(), f.getOrNull(5)?.toInt() ?: 0, f.getOrNull(6)?.toInt() ?: 0)
        } else {
            null
        }
        return OfflineMark(f[0].toInt(), f[1].toLong(), settled)
    }

    override fun setOfflineSittingMark(wallet: String, mark: OfflineMark?) {
        if (mark == null) {
            kv.removeValueForKey("offline_rec:$wallet")
        } else {
            val settled = mark.settled?.let { ",${it.day},${it.total},${it.complete},${it.toes},${it.measured}" }.orEmpty()
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

    override fun walletSession(): WalletSession? = kv.decodeString("session_address")?.let { WalletSession(it, kv.decodeString("session_token")) }

    override fun setWalletSession(session: WalletSession?) {
        if (session == null) {
            kv.removeValuesForKeys(arrayOf("session_address", "session_token"))
        } else {
            kv.encode("session_address", session.address)
            if (session.authToken == null) kv.removeValueForKey("session_token") else kv.encode("session_token", session.authToken)
        }
    }

    override fun askedForNotifications(): Boolean = kv.decodeBool("asked_notifications", false)

    override fun setAskedForNotifications() {
        kv.encode("asked_notifications", true)
    }

    override fun seekerName(wallet: String): KeptName? = kv.decodeString("$SEEKER_NAME$wallet")?.let(KeptName::decode)

    override fun setSeekerName(wallet: String, kept: KeptName) {
        KeptName.toDrop(keptNames(), wallet)?.let { kv.removeValueForKey("$SEEKER_NAME$it") }
        kv.encode("$SEEKER_NAME$wallet", kept.encode())
    }

    override fun allSeekerNames(): Map<String, String> = keptNames().mapNotNull { (wallet, kept) -> kept.name?.let { wallet to it } }.toMap()

    private fun keptNames(): Map<String, KeptName> = kv.allKeys().orEmpty()
        .filter { it.startsWith(SEEKER_NAME) }
        .mapNotNull { key -> kv.decodeString(key)?.let(KeptName::decode)?.let { key.removePrefix(SEEKER_NAME) to it } }
        .toMap()

    override fun seekerConfirmation(wallet: String): SeekerConfirmation? = kv.decodeString("$SEEKER_CONFIRMED$wallet")?.let(SeekerConfirmation::decode)

    override fun setSeekerConfirmation(wallet: String, confirmation: SeekerConfirmation) {
        kv.encode("$SEEKER_CONFIRMED$wallet", confirmation.encode())
    }

    override fun standingLooked(wallet: String, cohort: String): Int? = kv.decodeInt("looked:$wallet:$cohort", -1).takeIf { it >= 0 }

    override fun setStandingLooked(wallet: String, cohort: String, standing: Int) {
        kv.encode("looked:$wallet:$cohort", standing)
    }

    override fun recordsVideo(): Boolean = kv.decodeBool("records_video", false)

    override fun setRecordsVideo(on: Boolean) {
        kv.encode("records_video", on)
    }

    override fun pendingCohortDay(wallet: String, cohort: String): Int? = kv.decodeInt("pending:$wallet:$cohort", -1).takeIf { it >= 0 }

    override fun setPendingCohortDay(wallet: String, cohort: String, day: Int?) {
        if (day == null) kv.removeValueForKey("pending:$wallet:$cohort") else kv.encode("pending:$wallet:$cohort", day)
    }

    // One record, `cohort,day,lost`: a cohort address is base58, never a comma.
    override fun autoSubmitMark(wallet: String): AutoSubmitMark? {
        val f = kv.decodeString("auto_sent:$wallet")?.split(',')?.takeIf { it.size == 3 } ?: return null
        return AutoSubmitMark(f[0], f[1].toInt(), f[2].toBoolean())
    }

    override fun setAutoSubmitMark(wallet: String, mark: AutoSubmitMark?) {
        if (mark == null) kv.removeValueForKey("auto_sent:$wallet") else kv.encode("auto_sent:$wallet", "${mark.cohort},${mark.day},${mark.lost}")
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

    override fun setStreak(wallet: String, streak: Int, dayIndex: Long, freezes: Int) {
        values["streak:$wallet"] = streak
        values["streak_day:$wallet"] = dayIndex
        values["streak_best:$wallet"] = maxOf(longestStreak(wallet), streak)
        values["streak_freezes:$wallet"] = freezes
    }

    override fun longestStreak(wallet: String): Int = values["streak_best:$wallet"] as? Int ?: 0

    override fun setLongestStreak(wallet: String, streak: Int) {
        values["streak_best:$wallet"] = streak
    }

    override fun streakFreezes(wallet: String): Int = values["streak_freezes:$wallet"] as? Int ?: 0

    @Suppress("UNCHECKED_CAST")
    override fun toesDay(wallet: String, dayIndex: Long): Pair<Int, Int> = values["toes:$wallet:$dayIndex"] as? Pair<Int, Int> ?: (0 to 0)

    override fun setToesDay(wallet: String, dayIndex: Long, toes: Int, measured: Int) {
        values["toes:$wallet:$dayIndex"] = toes to measured
    }

    override fun bestToes(wallet: String): Int = values["toes_best:$wallet"] as? Int ?: 0

    override fun setBestToes(wallet: String, toes: Int) {
        values["toes_best:$wallet"] = toes
    }

    override fun hasSeenTour(wallet: String): Boolean = values["tour:$wallet"] as? Boolean ?: false

    override fun isSetupGuideHidden(wallet: String, stakes: String): Boolean = values["setup_hidden:$wallet:$stakes"] as? Boolean ?: false

    override fun hideSetupGuide(wallet: String, stakes: String) {
        values["setup_hidden:$wallet:$stakes"] = true
    }

    override fun sittingStartedAt(wallet: String, scope: String): Long? = values["sitting:$wallet:$scope"] as? Long

    override fun setSittingStartedAt(wallet: String, scope: String, startedAtMillis: Long?) {
        if (startedAtMillis == null) values.remove("sitting:$wallet:$scope") else values["sitting:$wallet:$scope"] = startedAtMillis
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

    override fun walletSession(): WalletSession? = values["session"] as? WalletSession

    override fun setWalletSession(session: WalletSession?) {
        if (session == null) values.remove("session") else values["session"] = session
    }

    override fun askedForNotifications(): Boolean = values["asked_notifications"] as? Boolean ?: false

    override fun setAskedForNotifications() {
        values["asked_notifications"] = true
    }

    override fun recordsVideo(): Boolean = values["records_video"] as? Boolean ?: false

    override fun setRecordsVideo(on: Boolean) {
        values["records_video"] = on
    }

    override fun pendingCohortDay(wallet: String, cohort: String): Int? = values["pending:$wallet:$cohort"] as? Int

    override fun setPendingCohortDay(wallet: String, cohort: String, day: Int?) {
        if (day == null) values.remove("pending:$wallet:$cohort") else values["pending:$wallet:$cohort"] = day
    }

    override fun autoSubmitMark(wallet: String): AutoSubmitMark? = values["auto_sent:$wallet"] as? AutoSubmitMark

    override fun setAutoSubmitMark(wallet: String, mark: AutoSubmitMark?) {
        if (mark == null) values.remove("auto_sent:$wallet") else values["auto_sent:$wallet"] = mark
    }

    override fun seekerName(wallet: String): KeptName? = values["$SEEKER_NAME$wallet"] as? KeptName

    override fun seekerConfirmation(wallet: String): SeekerConfirmation? = values["$SEEKER_CONFIRMED$wallet"] as? SeekerConfirmation

    override fun setSeekerConfirmation(wallet: String, confirmation: SeekerConfirmation) {
        values["$SEEKER_CONFIRMED$wallet"] = confirmation
    }

    override fun standingLooked(wallet: String, cohort: String): Int? = values["looked:$wallet:$cohort"] as? Int

    override fun setStandingLooked(wallet: String, cohort: String, standing: Int) {
        values["looked:$wallet:$cohort"] = standing
    }

    override fun setSeekerName(wallet: String, kept: KeptName) {
        KeptName.toDrop(keptNames(), wallet)?.let { values.remove("$SEEKER_NAME$it") }
        values["$SEEKER_NAME$wallet"] = kept
    }

    override fun allSeekerNames(): Map<String, String> = keptNames().mapNotNull { (wallet, kept) -> kept.name?.let { wallet to it } }.toMap()

    private fun keptNames(): Map<String, KeptName> = values
        .filterKeys { it.startsWith(SEEKER_NAME) }
        .mapNotNull { (key, value) -> (value as? KeptName)?.let { key.removePrefix(SEEKER_NAME) to it } }
        .toMap()
}

/** What MMKV reads for a key never written: no sitting marker (a real start time is never negative). */
private const val NO_MARKER = -1L

/** Key prefix of a kept Seeker ID entry: `seekerName:<wallet base58>` (spec 003 data-model §2). */
private const val SEEKER_NAME = "seekerName:"

/** Key prefix of a Seeker Genesis Token confirmation: `seeker:<wallet base58>` (001 data-model §4). */
private const val SEEKER_CONFIRMED = "seeker:"
