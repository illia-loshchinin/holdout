package holdout.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import holdout.app.MainActivity
import holdout.app.R
import holdout.app.storage.MmkvStore
import holdout.app.ui.Format
import holdout.core.Reminder

/**
 * Posts a reminder when the first of its alarms fires (FR-068), and nothing
 * once the deadline has passed. A day done on the phone but not on chain is
 * reminded to be submitted; a day that reached the chain was cancelled by the
 * app's next sync. Names the Holdout, labels the short test cohort, and says
 * the reps still needed and the time left; never an amount. Nothing is sent
 * anywhere: the notification is the whole of it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wallet = intent.getStringExtra(EXTRA_WALLET) ?: return
        val cohort = intent.getStringExtra(EXTRA_COHORT) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { Reminder.Kind.valueOf(it) }.getOrNull() } ?: return
        val day = intent.getIntExtra(EXTRA_DAY, 0)
        val reps = intent.getIntExtra(EXTRA_REPS, 25)
        val days = intent.getIntExtra(EXTRA_DAYS, 7)
        val dayLength = intent.getLongExtra(EXTRA_DAY_LENGTH, 86_400)
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        val deadline = intent.getLongExtra(EXTRA_DEADLINE_ELAPSED, 0)

        // The same scope the sitting banks under (HoldoutApp.cohortScope).
        val store = MmkvStore.create(context)
        val scope = "cohort:$cohort"
        if (store.walletSession()?.address != wallet) return
        // A deadline already gone: a late alarm (Doze) says nothing rather than something wrong.
        val left = (deadline - SystemClock.elapsedRealtime()) / 1_000
        if (left <= 0) return
        // A member who missed the day before is out, recorded yet or not, and the
        // app may not have run since to cancel what it scheduled: no reminder to
        // go on. A day the chain had when this was scheduled is done wherever it
        // was done — another phone, or this one before a reinstall; for a later
        // day only the phone's own record can say.
        val daysOnChain = intent.getIntExtra(EXTRA_DAYS_ON_CHAIN, 0)
        val previousDone = day <= daysOnChain || store.isDayComplete(wallet, scope, day - 1L) || store.pendingCohortDay(wallet, cohort) == day - 1
        if (kind == Reminder.Kind.Deadline && !previousDone) return
        // Each reminder has two alarms (ReminderScheduler); only the first to fire posts.
        if (intent.hasExtra(EXTRA_ID) && !ReminderScheduler.markPosted(context, intent.getIntExtra(EXTRA_ID, 0))) return
        // Done on this phone is not done: only the chain counts it, so say to submit it.
        val doneHere = store.isDayComplete(wallet, scope, day.toLong()) || store.pendingCohortDay(wallet, cohort) == day
        val owed = (reps - store.bankedReps(wallet, scope, day.toLong())).coerceAtLeast(0)

        val holdout = "$days-day Holdout" + if (test) " · short test cohort, ${Format.dayLength(dayLength)} days" else ""
        val (title, text) = when {
            kind == Reminder.Kind.Start -> "Your $holdout has started" to "Day 1 is open: $reps push-ups before the deadline, ${Format.countdown(left)} from now."
            doneHere -> "Day ${day + 1} is done, but not on chain yet" to "Open Holdout and submit it: about ${Format.countdown(left)} left in your $holdout. Only the chain counts it."
            else -> "Day ${day + 1}: $owed push-ups still to do" to "About ${Format.countdown(left)} left in your $holdout. The deadline is the chain's, not this reminder's."
        }
        post(context, requestId = intent.hashCode(), id = 31 * cohort.hashCode() + day * 2 + kind.ordinal, title = title, text = text)
    }

    private fun post(context: Context, requestId: Int, id: Int, title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            requestId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        const val CHANNEL = "holdout_reminders"
        const val EXTRA_ID = "id"
        const val EXTRA_WALLET = "wallet"
        const val EXTRA_COHORT = "cohort"
        const val EXTRA_KIND = "kind"
        const val EXTRA_DAY = "day"
        const val EXTRA_REPS = "reps"
        const val EXTRA_DAYS = "days"
        const val EXTRA_DAY_LENGTH = "day_length"
        const val EXTRA_DAYS_ON_CHAIN = "days_on_chain"
        const val EXTRA_TEST = "test"
        const val EXTRA_DEADLINE_ELAPSED = "deadline_elapsed"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Holdout reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a Holdout starts, and before a day's deadline while that day is not done"
                },
            )
        }
    }
}
