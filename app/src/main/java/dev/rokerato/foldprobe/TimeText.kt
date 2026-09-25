package dev.rokerato.foldprobe

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Times as people say them: "6:30", "in 4 h 13 min", "Monday 6:30". */
object TimeText {

    /** Wall-clock time, following the phone's 12/24-hour setting, without padding. */
    fun clock(context: Context, millis: Long): String = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm",
        Locale.getDefault()
    ).format(Date(millis))

    fun clock(context: Context, hour: Int, minute: Int): String =
        if (DateFormat.is24HourFormat(context)) {
            "%d:%02d".format(hour, minute)
        } else {
            "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute)
        }

    /** "4 h 13 min", "13 min", or "under a minute". */
    fun span(millis: Long): String {
        val minutes = ((millis + 59_999L) / 60_000L).coerceAtLeast(0L)
        val hours = minutes / 60
        return when {
            minutes < 1 -> "under a minute"
            hours == 0L -> "$minutes min"
            minutes % 60 == 0L -> "$hours h"
            else -> "$hours h ${minutes % 60} min"
        }
    }

    /**
     * How the next ring is described: a countdown within a day, the weekday beyond
     * one, since "in 70 h 20 min" is arithmetic nobody should do at bedtime.
     */
    fun untilRing(context: Context, now: Long, target: Long): String =
        if (target - now < DAY) {
            "Rings in " + span(target - now)
        } else {
            "Rings " + SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(target)) +
                " " + clock(context, target)
        }

    private const val DAY = 24 * 60 * 60_000L
}
