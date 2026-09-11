package dev.rokerato.foldprobe

import android.content.Context
import java.util.Calendar

/** The single daily alarm, and how the lamp should look when it fires. */
class AlarmPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("fold_alarm", Context.MODE_PRIVATE)

    var hour: Int
        get() = prefs.getInt(KEY_HOUR, 6)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var minute: Int
        get() = prefs.getInt(KEY_MINUTE, 30)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE, 9)
        set(value) = prefs.edit().putInt(KEY_SNOOZE, value).apply()

    /** Lamp brightness, 0.15 to 1.0. Also drives the window's screen brightness. */
    var lampBrightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_BRIGHTNESS, value).apply()

    /** 0 is a cool white lamp, 1 a deep amber one. */
    var lampWarmth: Float
        get() = prefs.getFloat(KEY_WARMTH, 0.75f)
        set(value) = prefs.edit().putFloat(KEY_WARMTH, value).apply()

    /** Epoch millis of the next ring, snooze included; 0 when nothing is scheduled. */
    var nextTrigger: Long
        get() = prefs.getLong(KEY_NEXT, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT, value).apply()

    /** The next time the wall clock next shows [hour]:[minute], today or tomorrow. */
    fun nextOccurrence(now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private companion object {
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_ENABLED = "enabled"
        const val KEY_SNOOZE = "snooze_minutes"
        const val KEY_BRIGHTNESS = "lamp_brightness"
        const val KEY_WARMTH = "lamp_warmth"
        const val KEY_NEXT = "next_trigger"
    }
}
