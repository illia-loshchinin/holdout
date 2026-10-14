package holdout.app.reminders

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import holdout.app.storage.MmkvStore
import holdout.app.ui.Format
import holdout.core.Reminder
import holdout.core.Reminders

/**
 * Posts a reminder when the first of its alarms fires (FR-068), and nothing
 * once the deadline has passed. A day done on the phone but not on chain is
 * kept in the shade as a silent, ongoing notice to submit it, re-posted every
 * hour until the chain has it (FR-068, 2026-10-11); a day that reached the
 * chain was cancelled by the app's next sync. Names the Holdout, labels the
 * short test cohort, and says the reps still needed and the time left; never an
 * amount. A finished Holdout gets one notice that its claim is probably ready
 * (FR-068b), with no chain read. Nothing is sent anywhere: the notification is
 * the whole of it.
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
        // An alarm an earlier build set has no form: its notice names the round by length.
        val form = intent.getIntExtra(EXTRA_FORM, Reminders.FORM_UNKNOWN)
        val deadline = intent.getLongExtra(EXTRA_DEADLINE_ELAPSED, 0)

        // The same scope the sitting banks under (HoldoutApp.cohortScope).
        val store = MmkvStore.create(context)
        val scope = "cohort:$cohort"
        if (store.walletSession()?.address != wallet) return
        // A deadline already gone: a late alarm (Doze) says nothing rather than something wrong.
        val left = (deadline - SystemClock.elapsedRealtime()) / 1_000
        if (left <= 0) return
        if (kind == Reminder.Kind.Finished) {
            // Every day was on chain when it was scheduled, and the app cancels it once it
            // has seen the cohort settled: it reads no chain itself (FR-068a, FR-068b).
            if (intent.hasExtra(EXTRA_ID) && !ReminderScheduler.markPosted(context, intent.getIntExtra(EXTRA_ID, 0))) return
            // With the app on screen, its own card says where settlement stands — maybe
            // "taking longer than usual" — and a "probably ready" over it would contradict it.
            if (inForeground()) return
            val text = ReminderNotices.finishedText(intent.getBooleanExtra(EXTRA_COMEBACK, false))
            ReminderNotices.post(
                context,
                intent.hashCode(),
                Reminders.notificationId(cohort, kind, day),
                ReminderNotices.finishedTitle(days, test, dayLength, form),
                text,
                silent = Reminders.isSilent(kind, dayLength),
                ongoing = false,
                leftSeconds = left,
            )
            return
        }
        // A member who missed the day before is out, recorded yet or not, and the
        // app may not have run since to cancel what it scheduled: no reminder to
        // go on. Only the chain says a day is done: one done on this phone and
        // never submitted was forfeited at its deadline all the same. So the alarm
        // carries the days on chain when it was scheduled — every submit that lands
        // reschedules them — and a day past those stays quiet.
        val daysOnChain = intent.getIntExtra(EXTRA_DAYS_ON_CHAIN, 0)
        if (kind != Reminder.Kind.Start && day > daysOnChain) return
        // Done on this phone is not done: only the chain counts it, so say to submit it.
        val doneHere = store.isDayComplete(wallet, scope, day.toLong()) || store.pendingCohortDay(wallet, cohort) == day
        // The hourly notice is for a day done here; one the phone no longer holds as done has nothing to say.
        if (kind == Reminder.Kind.NotSent && !doneHere) return
        // Each reminder has two alarms (ReminderScheduler); only the first to fire posts.
        if (intent.hasExtra(EXTRA_ID) && !ReminderScheduler.markPosted(context, intent.getIntExtra(EXTRA_ID, 0))) return

        val requestId = intent.hashCode()
        val id = Reminders.notificationId(cohort, kind, day)
        if (doneHere && kind != Reminder.Kind.Start) {
            // Whichever reminder of the day arrives, a day done here and not on chain keeps the standing notice.
            ReminderNotices.postNotSent(context, requestId, id, day, days, dayLength, test, left, silent = Reminders.isSilent(kind, dayLength), form = form)
            return
        }
        val owed = (reps - store.bankedReps(wallet, scope, day.toLong())).coerceAtLeast(0)
        val holdout = ReminderNotices.holdoutLabel(days, test, dayLength, form)
        val (title, text) = when (kind) {
            Reminder.Kind.Start -> "Your $holdout has started" to "Day 1 is open: $reps push-ups before the deadline, ${Format.countdown(left)} from now."
            else -> "Day ${day + 1}: $owed push-ups still to do" to "About ${Format.countdown(left)} left in your $holdout. The deadline is the chain's, not this reminder's."
        }
        ReminderNotices.post(context, requestId, id, title, text, silent = Reminders.isSilent(kind, dayLength), ongoing = false, leftSeconds = left)
    }

    private fun inForeground(): Boolean = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    companion object {
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
        const val EXTRA_FORM = "form"
        const val EXTRA_DEADLINE_ELAPSED = "deadline_elapsed"
        const val EXTRA_COMEBACK = "comeback"
    }
}
