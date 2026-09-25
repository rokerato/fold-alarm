package dev.rokerato.foldprobe

import android.content.Context
import java.util.Calendar

/** The one alarm, the days it repeats on, and how the lamp should look when it fires. */
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

    /**
     * The days the alarm repeats on, one bit per [Calendar.DAY_OF_WEEK]: bit 0 is
     * Sunday, bit 6 Saturday. Every day by default, which is how the alarm behaved
     * before days could be chosen. No days at all means ring once, then switch off.
     */
    var days: Int
        get() = prefs.getInt(KEY_DAYS, EVERY_DAY)
        set(value) = prefs.edit().putInt(KEY_DAYS, value and EVERY_DAY).apply()

    fun ringsOn(dayOfWeek: Int): Boolean = days and (1 shl (dayOfWeek - 1)) != 0

    val repeats: Boolean
        get() = days != 0

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

    // ---- standby ------------------------------------------------------------

    /** 0 thin, 1 light, 2 regular. Thin reads best, and wears least. */
    var clockWeight: Int
        get() = prefs.getInt(KEY_WEIGHT, 0).coerceIn(0, 2)
        set(value) = prefs.edit().putInt(KEY_WEIGHT, value).apply()

    /** Dim the clock and tint it red once the room goes dark. */
    var nightTint: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_TINT, true)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_TINT, value).apply()

    /** 0 ramp up before the alarm, 1 light at the alarm, 2 no glow at all. */
    var sunriseMode: Int
        get() = prefs.getInt(KEY_SUNRISE_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_SUNRISE_MODE, value).apply()

    /** How long the ramp takes, ending exactly at the alarm. */
    var sunriseMinutes: Int
        get() = prefs.getInt(KEY_SUNRISE_MINUTES, 20)
        set(value) = prefs.edit().putInt(KEY_SUNRISE_MINUTES, value).apply()

    /**
     * Swaps which half of the inner display glows.
     *
     * Only the half nearest the cover screen faces into the tent, so its light
     * reaches the room bounced off the table rather than head-on. No API says which
     * half that is; measured on a tented Z Fold 6 it is the first half, so that is
     * the default and this only exists for a device where it is the other one.
     */
    var glowSwapped: Boolean
        get() = prefs.getBoolean(KEY_GLOW_SWAPPED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLOW_SWAPPED, value).apply()

    /** Which half to light, after the swap. */
    val glowFirstHalf: Boolean
        get() = !glowSwapped

    /** Show lux and the current mode on the cover screen, for testing. */
    var showDiagnostics: Boolean
        get() = prefs.getBoolean(KEY_DIAGNOSTICS, false)
        set(value) = prefs.edit().putBoolean(KEY_DIAGNOSTICS, value).apply()

    /** Blank the cover screen after a while at night; a tap brings it back. */
    var nightBlank: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_BLANK, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_BLANK, value).apply()

    var nightBlankMinutes: Int
        get() = prefs.getInt(KEY_NIGHT_BLANK_MINUTES, 10)
        set(value) = prefs.edit().putInt(KEY_NIGHT_BLANK_MINUTES, value).apply()

    /** Epoch millis of the next ring, snooze included; 0 when nothing is scheduled. */
    var nextTrigger: Long
        get() = prefs.getLong(KEY_NEXT, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT, value).apply()

    /**
     * The next time the wall clock shows [hour]:[minute] on a day the alarm repeats
     * on -- or on any day, when it repeats on none and is ringing just once.
     */
    fun nextOccurrence(now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        if (!repeats) return calendar.timeInMillis
        repeat(7) {
            if (ringsOn(calendar.get(Calendar.DAY_OF_WEEK))) return calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    companion object {
        const val EVERY_DAY = 0b111_1111

        private const val KEY_DAYS = "days"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SNOOZE = "snooze_minutes"
        private const val KEY_BRIGHTNESS = "lamp_brightness"
        private const val KEY_WARMTH = "lamp_warmth"
        private const val KEY_NEXT = "next_trigger"
        private const val KEY_WEIGHT = "clock_weight"
        private const val KEY_NIGHT_TINT = "night_tint"
        private const val KEY_SUNRISE_MODE = "sunrise_mode"
        private const val KEY_SUNRISE_MINUTES = "sunrise_minutes"
        private const val KEY_GLOW_SWAPPED = "glow_swapped"
        private const val KEY_DIAGNOSTICS = "show_diagnostics"
        private const val KEY_NIGHT_BLANK = "night_blank"
        private const val KEY_NIGHT_BLANK_MINUTES = "night_blank_minutes"
    }
}
