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
 */
class ReminderScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /**
     * Schedules [reminders] for [wallet]. [chainNow] and [elapsedNow] must be one
     * reading taken together. With notifications off, nothing is scheduled —
     * and anything scheduled before is cancelled.
     */
    fun sync(wallet: String, reminders: List<Reminder>, chainNow: Long, elapsedNow: Long = SystemClock.elapsedRealtime()) {
        val wanted = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) reminders else emptyList()
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        (previous - wanted.map { it.id }.toSet()).forEach { cancel(it) }
        wanted.forEach { r ->
            val at = Reminders.elapsedMillisAt(r.at, chainNow, elapsedNow)
            val deadline = Reminders.elapsedMillisAt(r.deadline, chainNow, elapsedNow)
            // A bounded window, not setAndAllowWhileIdle: on the Seeker that let
            // Android hold a reminder ~57 minutes — past a one-hour day's deadline.
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, Reminders.windowMillis(r.dayLength), pending(r.id, intent(wallet, r, deadline)))
        }
        prefs.edit().putStringSet(KEY_IDS, wanted.map { it.id.toString() }.toSet()).apply()
    }

    /** Cancels every reminder — on disconnect. */
    fun clear() {
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.forEach { cancel(it) }
        prefs.edit().remove(KEY_IDS).apply()
    }

    private fun cancel(id: Int) = alarms.cancel(pending(id, Intent(context, ReminderReceiver::class.java)))

    private fun intent(wallet: String, r: Reminder, deadlineElapsed: Long) = Intent(context, ReminderReceiver::class.java).apply {
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

    private fun pending(id: Int, intent: Intent): PendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val KEY_IDS = "scheduled_ids"
    }
}
