package holdout.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import holdout.app.MainActivity
import holdout.app.R
import holdout.app.ui.Format

/**
 * The notifications a reminder shows (FR-068): their channels, wording and
 * posting, shared by the alarm receiver and by the scheduler, which posts the
 * standing not-sent notice itself.
 */
object ReminderNotices {
    const val CHANNEL = "holdout_reminders"

    /** The silent channel: a long day's reminder before its deadline and every not-sent notice, shown in the shade without sound or vibration. */
    const val CHANNEL_QUIET = "holdout_reminders_quiet"

    /**
     * Creates both channels, and brings an existing install's names and
     * descriptions up to date: `createNotificationChannel` on a channel that
     * exists changes only those, never the importance or what the member set.
     * Called when the app starts, so both are in the phone's settings before
     * the first reminder is posted.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Holdout reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a Holdout starts, halfway through a day that is not done yet, and when a short test Holdout you finished has probably settled"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "Holdout reminders, silent", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Before a day's deadline, which may fall at night, every hour while a day you've done isn't on chain yet, and when a Holdout you finished has probably settled: shown without sound or vibration"
            },
        )
    }

    /** "7-day Holdout", and for the short test cohort its day length too. */
    fun holdoutLabel(days: Int, test: Boolean, dayLength: Long): String = "$days-day Holdout" + if (test) " · short test cohort, ${Format.dayLength(dayLength)} days" else ""

    /** The finished notice (FR-068b): *"You finished your 7-day Holdout"*, with the test cohort's label. */
    fun finishedTitle(days: Int, test: Boolean, dayLength: Long): String = "You finished your ${holdoutLabel(days, test, dayLength)}"

    /**
     * It reads no chain (FR-068a), so it says *probably*; the comeback line has
     * no amount, and says *should*, as the reserve may cut it (FR-037e).
     */
    fun finishedText(comeback: Boolean): String = "Settlement usually comes within 15 minutes of the end, so your claim is probably ready: open Holdout to claim it." +
        if (comeback) " It should include a comeback refund too." else ""

    fun notSentTitle(day: Int): String = "Day ${day + 1} is done, but not on chain yet"

    fun notSentText(leftSeconds: Long, holdout: String): String = "Open Holdout and submit it: about ${Format.countdown(leftSeconds)} left in your $holdout. Only the chain counts it."

    /**
     * Posts the day-done-but-not-on-chain notice (FR-068, 2026-10-11): silent (but for a short day's deadline reminder, which sounds),
     * ongoing, and gone at the deadline. Re-posting under the same [id] updates
     * it in place, which is how it comes back every hour.
     */
    fun postNotSent(context: Context, requestId: Int, id: Int, day: Int, days: Int, dayLength: Long, test: Boolean, leftSeconds: Long, silent: Boolean = true) {
        post(context, requestId, id, notSentTitle(day), notSentText(leftSeconds, holdoutLabel(days, test, dayLength)), silent = silent, ongoing = true, leftSeconds = leftSeconds)
    }

    /**
     * [silent] puts it on the silent channel (the channel's importance, not the
     * notification's priority, decides at API 26 and up); [ongoing] keeps it in
     * the shade — from Android 14 a member may still swipe it away, and the next
     * hourly re-post brings it back. An ongoing notice times out [leftSeconds] later, at the deadline.
     */
    fun post(context: Context, requestId: Int, id: Int, title: String, text: String, silent: Boolean, ongoing: Boolean, leftSeconds: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannels(context)
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
                .setAutoCancel(!ongoing)
                .setOngoing(ongoing)
                .setSilent(silent)
                .apply { if (ongoing) setTimeoutAfter((leftSeconds * 1_000).coerceAtLeast(1)) }
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Whether the notification [id] is in the shade now. */
    fun isShown(context: Context, id: Int): Boolean = runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == id } }.getOrDefault(false)

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
