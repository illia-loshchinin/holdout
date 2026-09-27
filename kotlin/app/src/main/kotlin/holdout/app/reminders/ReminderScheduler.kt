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
 */
class ReminderScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Schedules [reminders] for [wallet]. [chainNow] and [elapsedNow] must be one
     * reading taken together. With notifications off, nothing is scheduled —
     * and anything scheduled before is cancelled.
     */
    fun sync(wallet: String, reminders: List<Reminder>, chainNow: Long, elapsedNow: Long = SystemClock.elapsedRealtime()) {
        val wanted = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) reminders else emptyList()
        val previous = ids(KEY_IDS)
        (previous - wanted.map { it.id }.toSet()).forEach { cancel(it) }
        wanted.forEach { r ->
            val at = Reminders.elapsedMillisAt(r.at, chainNow, elapsedNow)
            val deadline = Reminders.elapsedMillisAt(r.deadline, chainNow, elapsedNow)
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, Reminders.windowMillis(r.dayLength), pending(r.id, intent(ACTION_ON_TIME, wallet, r, deadline)))
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(r.id, intent(ACTION_WHILE_IDLE, wallet, r, deadline)))
        }
        // Every alarm of a delivered reminder is cancelled above or still to
        // come, so its "posted" mark is only needed until this sync.
        val touched = previous + wanted.map { it.id }
        prefs.edit()
            .putStringSet(KEY_IDS, wanted.map { it.id.toString() }.toSet())
            .putStringSet(KEY_POSTED, (ids(KEY_POSTED) - touched).map { it.toString() }.toSet())
            .apply()
    }

    /** Cancels every reminder — on disconnect. */
    fun clear() {
        ids(KEY_IDS).forEach { cancel(it) }
        prefs.edit().remove(KEY_IDS).remove(KEY_POSTED).apply()
    }

    private fun ids(key: String): Set<Int> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    // Null: the single alarm an earlier build set, with no action.
    private fun cancel(id: Int) = listOf(null, ACTION_ON_TIME, ACTION_WHILE_IDLE).forEach { alarms.cancel(pending(id, Intent(it, null, context, ReminderReceiver::class.java))) }

    private fun intent(action: String, wallet: String, r: Reminder, deadlineElapsed: Long) = Intent(action, null, context, ReminderReceiver::class.java).apply {
        putExtra(ReminderReceiver.EXTRA_ID, r.id)
        putExtra(ReminderReceiver.EXTRA_WALLET, wallet)
        putExtra(ReminderReceiver.EXTRA_COHORT, r.cohort.base58())
        putExtra(ReminderReceiver.EXTRA_KIND, r.kind.name)
        putExtra(ReminderReceiver.EXTRA_DAY, r.dayIndex)
        putExtra(ReminderReceiver.EXTRA_REPS, r.repsPerDay)
        putExtra(ReminderReceiver.EXTRA_DAYS, r.durationDays)
        putExtra(ReminderReceiver.EXTRA_DAY_LENGTH, r.dayLength)
        putExtra(ReminderReceiver.EXTRA_TEST, Deployment.isTestLeague(r.leagueId))
        putExtra(ReminderReceiver.EXTRA_DEADLINE_ELAPSED, deadlineElapsed)
    }

    // The action tells the two alarms of one reminder apart: same request code, different intent.
    private fun pending(id: Int, intent: Intent): PendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val PREFS = "reminders"
        private const val KEY_IDS = "scheduled_ids"
        private const val ACTION_ON_TIME = "holdout.app.reminder.ON_TIME"
        private const val ACTION_WHILE_IDLE = "holdout.app.reminder.WHILE_IDLE"

        /** Reminders already posted, so the second of a reminder's two alarms stays quiet. */
        const val KEY_POSTED = "posted_ids"

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
