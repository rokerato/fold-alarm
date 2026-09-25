package dev.rokerato.foldprobe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires the alarm.
 *
 * Background activity starts are blocked from Android 10, so the ringing screen is
 * launched through a high-importance notification carrying a full-screen intent --
 * the sanctioned route, and the one that shows over the lock screen. The direct
 * start is attempted too, for the case where the app is already in the foreground.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return

        // Standby may already be running, in which case singleTask delivers this to
        // onNewIntent and it rings in place; otherwise this launches it ringing.
        val alarmIntent = Intent(context, StandbyActivity::class.java)
            .putExtra(StandbyActivity.EXTRA_RING, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ensureChannel(context)
        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText("Good morning")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)

        runCatching { context.startActivity(alarmIntent) }

        // The next day it repeats on. An alarm set for no days rings once and
        // switches itself off, as a one-off alarm does on any other clock.
        val prefs = AlarmPrefs(context)
        if (prefs.enabled) {
            if (prefs.repeats) {
                AlarmScheduler.schedule(context, prefs.nextOccurrence(System.currentTimeMillis() + 60_000L))
            } else {
                prefs.enabled = false
                prefs.nextTrigger = 0L
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "dev.rokerato.foldprobe.ALARM"
        const val CHANNEL_ID = "alarm"
        const val NOTIFICATION_ID = 42

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Wakes the screen when the alarm rings"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }

        fun dismissNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}
