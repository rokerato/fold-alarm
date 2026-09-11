package dev.rokerato.foldprobe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms do not survive a reboot, so the stored one is re-applied on boot. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmScheduler.sync(context)
    }
}
