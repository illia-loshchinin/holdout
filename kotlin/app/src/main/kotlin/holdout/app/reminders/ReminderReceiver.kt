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
import holdout.core.Reminders

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
        // This alarm is spent whatever happens below: the next chain read sets it
        // again if the reminder is still due and has not been said.
        ReminderScheduler.spent(context, intent)
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
        // go on. Only the chain says a day is done: one done on this phone and
        // never submitted was forfeited at its deadline all the same. So the alarm
        // carries the days on chain when it was scheduled — every submit that lands
        // reschedules them — and a day past those stays quiet.
        val daysOnChain = intent.getIntExtra(EXTRA_DAYS_ON_CHAIN, 0)
        if (kind != Reminder.Kind.Start && day > daysOnChain) return
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
        // A long day's reminder before its deadline may fall at night: it is posted silently (FR-068).
        val silent = kind == Reminder.Kind.Deadline && Reminders.hasMidday(dayLength)
        post(context, requestId = intent.hashCode(), id = 31 * cohort.hashCode() + day * 3 + kind.ordinal, title = title, text = text, silent = silent)
    }

    private fun post(context: Context, requestId: Int, id: Int, title: String, text: String, silent: Boolean) {
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
                .Builder(context, if (silent) CHANNEL_QUIET else CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
                .setSilent(silent)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        const val CHANNEL = "holdout_reminders"

        /** The silent channel: a long day's reminder before its deadline, shown in the shade without sound or vibration. */
        const val CHANNEL_QUIET = "holdout_reminders_quiet"
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
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Holdout reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "When a Holdout starts, and during a day that is not done yet"
                    },
                )
            }
            if (manager.getNotificationChannel(CHANNEL_QUIET) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_QUIET, "Holdout reminders, silent", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Before a day's deadline, which may fall at night: shown without sound or vibration"
                    },
                )
            }
        }
    }
}
