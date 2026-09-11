package dev.rokerato.foldprobe

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules the alarm through [AlarmManager.setAlarmClock], which is the only
 * scheduling method that still fires punctually in Doze, and which surfaces the
 * alarm in the system status bar.
 */
object AlarmScheduler {

    private const val REQUEST_FIRE = 1
    private const val REQUEST_SHOW = 2

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** Schedules [triggerAtMillis] and records it. Returns false if the OS refuses. */
    fun schedule(context: Context, triggerAtMillis: Long): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        if (!canScheduleExact(context)) return false
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent(context)),
            fireIntent(context)
        )
        AlarmPrefs(context).nextTrigger = triggerAtMillis
        return true
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(context))
        AlarmPrefs(context).nextTrigger = 0L
    }

    /** Re-applies whatever the preferences currently say. */
    fun sync(context: Context): Boolean {
        val prefs = AlarmPrefs(context)
        return if (prefs.enabled) {
            schedule(context, prefs.nextOccurrence())
        } else {
            cancel(context)
            true
        }
    }

    private fun fireIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_FIRE,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
