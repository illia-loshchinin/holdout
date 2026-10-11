package holdout.app

import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * "The app was opened" (FR-069: the app opens on Today, including from a
 * reminder). A reminder's tap re-uses the running activity — it arrives as a
 * new intent, with no recreation to start the app over — so [MainActivity]
 * reports it here, and `HoldoutApp` turns it into `NavEvent.AppOpened`.
 */
object AppOpen {
    /** The action a reminder's tap carries ([holdout.app.reminders.ReminderNotices.openAppIntent]). */
    const val ACTION_OPEN_TODAY = "holdout.app.action.OPEN_TODAY"

    /** Whether [intent] is the launcher or a reminder asking for the app, as opposed to anything else a new intent might be. */
    fun opensToday(intent: Intent?): Boolean = intent?.action == Intent.ACTION_MAIN || intent?.action == ACTION_OPEN_TODAY
}

/**
 * Opens reported to the app. Kept until someone listens (the activity can
 * report one before the screens are composed) and conflated: opened twice
 * before it is read is one open.
 */
class AppOpens {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    /** Safe from any thread. */
    fun opened() {
        channel.trySend(Unit)
    }

    val flow: Flow<Unit> = channel.receiveAsFlow()
}
