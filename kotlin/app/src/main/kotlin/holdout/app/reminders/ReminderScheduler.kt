package holdout.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import holdout.app.chain.Deployment
import holdout.core.Reminder
import holdout.core.Reminders

/**
 * Hands the reminder schedule (FR-068, research R21) to the phone's alarm
 * service. [sync] is called on every chain refresh with the whole schedule:
 * it sets what is new, replaces what moved and cancels what is gone, so the
 * phone always holds exactly the reminders chain state says are due.
 *
 * Alarms are inexact `ELAPSED_REALTIME_WAKEUP` — the monotonic clock, set from
 * a chain reading, never the wall clock — and no exact-alarm or boot
 * permission is used: after a restart they return when the app next reads the
 * chain (FR-068a).
 *
 * Each reminder is set twice, and the receiver posts whichever fires first:
 * - **on time** — `setWindow`, bounded so it lands before the deadline while
 *   the phone is awake, but held by Doze until its next maintenance window;
 * - **while idle** — `setAndAllowWhileIdle`, which Doze lets through, though
 *   later: without the exact-alarm permission nothing can promise an idle
 *   phone a reminder before the deadline.
 *
 * A day done on the phone and not on chain (FR-068, 2026-10-11) also has a
 * standing, ongoing notice — the schedule's tick 0, posted here rather than by
 * an alarm, so it is there as soon as the day is done and again after a
 * restart — which the hourly NotSent alarms re-post silently. [sync] takes it
 * down when the schedule no longer has it: the day reached the chain, or its
 * deadline passed (the notice also times out at the deadline by itself).
 */
class ReminderScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Both channels exist, with their current descriptions, from the app's start.
    init {
        ReminderNotices.ensureChannels(this.context)
    }

    /**
     * Schedules [reminders] for [wallet]. [chainNow] and [elapsedNow] must be one
     * reading taken together. With notifications off, nothing is scheduled —
     * and anything scheduled before is cancelled.
     */
    fun sync(wallet: String, reminders: List<Reminder>, chainNow: Long, elapsedNow: Long = SystemClock.elapsedRealtime()) {
        val wanted = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) reminders else emptyList()
        val previous = ids(KEY_IDS)
        val cancelled = previous - wanted.map { it.id }.toSet()
        cancelled.forEach { cancel(it) }
        val posted = ids(KEY_POSTED)
        // The standing notices: posted when missing (a new one, a restart, a swipe on
        // Android 14), updated in place by the hourly re-posts, taken down when gone.
        val standing = wanted.filter { it.standing }
        val standingIds = standing.map { Reminders.notificationId(it.cohort.base58(), it.kind, it.dayIndex) }.toSet()
        (ids(KEY_STANDING) - standingIds).forEach { ReminderNotices.cancel(context, it) }
        standing.forEach { r ->
            val id = Reminders.notificationId(r.cohort.base58(), r.kind, r.dayIndex)
            if (!ReminderNotices.isShown(context, id)) {
                ReminderNotices.postNotSent(context, r.id, id, r.dayIndex, r.durationDays, r.dayLength, Deployment.isTestLeague(r.leagueId), r.deadline - chainNow)
            }
        }
        wanted.filterNot { it.standing }.forEach { r ->
            // Due and already said: nothing left to fire.
            if (r.at <= chainNow && r.id in posted) return@forEach
            val at = Reminders.elapsedMillisAt(r.at, chainNow, elapsedNow)
            val deadline = Reminders.elapsedMillisAt(r.deadline, chainNow, elapsedNow)
            // Already due and inside its delivery window, with its alarm still
            // live: it may yet fire late, so its time is left alone — but what it
            // carries is refreshed in place (FLAG_UPDATE_CURRENT), or a day
            // confirmed after the reminder came due would still read as missing
            // and the deadline reminder stay quiet. With no live alarm — fired
            // and spent (the receiver cancels its token), dropped by a reboot or
            // a force-stop, or a membership first seen after its time — it is set
            // now, to fire at once, with what the chain says now.
            if (r.at <= chainNow && alive(r.id)) {
                pending(r.id, intent(ACTION_ON_TIME, wallet, r, deadline))
                pending(r.id, intent(ACTION_WHILE_IDLE, wallet, r, deadline))
                return@forEach
            }
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, Reminders.windowMillis(r), pending(r.id, intent(ACTION_ON_TIME, wallet, r, deadline)))
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(r.id, intent(ACTION_WHILE_IDLE, wallet, r, deadline)))
        }
        // A cancelled reminder has no alarm left to fire, so its "posted" mark is
        // no longer needed; one still in its window keeps the mark, so its second alarm stays quiet.
        prefs.edit()
            .putStringSet(KEY_IDS, wanted.map { it.id.toString() }.toSet())
            .putStringSet(KEY_STANDING, standingIds.map { it.toString() }.toSet())
            .putStringSet(KEY_POSTED, (ids(KEY_POSTED) - cancelled).map { it.toString() }.toSet())
            .apply()
    }

    /** Cancels every reminder — on disconnect. */
    fun clear() {
        ids(KEY_IDS).forEach { cancel(it) }
        ids(KEY_STANDING).forEach { ReminderNotices.cancel(context, it) }
        prefs.edit().remove(KEY_IDS).remove(KEY_POSTED).remove(KEY_STANDING).apply()
    }

    private fun ids(key: String): Set<Int> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    // Null: the single alarm an earlier build set, with no action. The token goes
    // with the alarm: left behind, [alive] would take it for an alarm still set.
    private fun cancel(id: Int) = listOf(null, ACTION_ON_TIME, ACTION_WHILE_IDLE).forEach { action ->
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(action, null, context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { token ->
            alarms.cancel(token)
            token.cancel()
        }
    }

    private fun intent(action: String, wallet: String, r: Reminder, deadlineElapsed: Long) = Intent(action, null, context, ReminderReceiver::class.java).apply {
        putExtra(ReminderReceiver.EXTRA_ID, r.id)
        putExtra(ReminderReceiver.EXTRA_WALLET, wallet)
        putExtra(ReminderReceiver.EXTRA_COHORT, r.cohort.base58())
        putExtra(ReminderReceiver.EXTRA_KIND, r.kind.name)
        putExtra(ReminderReceiver.EXTRA_DAY, r.dayIndex)
        putExtra(ReminderReceiver.EXTRA_REPS, r.repsPerDay)
        putExtra(ReminderReceiver.EXTRA_DAYS, r.durationDays)
        putExtra(ReminderReceiver.EXTRA_DAY_LENGTH, r.dayLength)
        putExtra(ReminderReceiver.EXTRA_DAYS_ON_CHAIN, r.daysOnChain)
        putExtra(ReminderReceiver.EXTRA_TEST, Deployment.isTestLeague(r.leagueId))
        putExtra(ReminderReceiver.EXTRA_DEADLINE_ELAPSED, deadlineElapsed)
    }

    /**
     * Whether [id]'s on-time alarm is still held. Its PendingIntent outlives the
     * alarm unless cancelled, so the receiver cancels it once it fires
     * ([spent]); a reboot or a force-stop drops it too.
     */
    private fun alive(id: Int): Boolean = PendingIntent.getBroadcast(
        context,
        id,
        Intent(ACTION_ON_TIME, null, context, ReminderReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    // The action tells the two alarms of one reminder apart: same request code, different intent.
    private fun pending(id: Int, intent: Intent): PendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val PREFS = "reminders"
        private const val KEY_IDS = "scheduled_ids"

        /** Notification ids of the standing not-sent notices posted, so one no longer wanted is taken down. */
        private const val KEY_STANDING = "standing_ids"
        private const val ACTION_ON_TIME = "holdout.app.reminder.ON_TIME"
        private const val ACTION_WHILE_IDLE = "holdout.app.reminder.WHILE_IDLE"

        /** Reminders already posted, so the second of a reminder's two alarms stays quiet. */
        const val KEY_POSTED = "posted_ids"

        /**
         * The fired alarm's token, cancelled: a PendingIntent outlives the alarm
         * that sent it, and [alive] would take a spent one for one still to fire.
         */
        fun spent(context: Context, intent: Intent) {
            if (!intent.hasExtra(ReminderReceiver.EXTRA_ID)) return
            PendingIntent.getBroadcast(
                context,
                intent.getIntExtra(ReminderReceiver.EXTRA_ID, 0),
                Intent(intent.action, null, context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.cancel()
        }

        /** Marks [id] posted; false when it already was. */
        fun markPosted(context: Context, id: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val posted = prefs.getStringSet(KEY_POSTED, emptySet()).orEmpty()
            if (id.toString() in posted) return false
            prefs.edit().putStringSet(KEY_POSTED, posted + id.toString()).apply()
            return true
        }
    }
}
