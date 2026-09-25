package dev.rokerato.foldprobe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * Everything the cover screen shows, across every standby mode.
 *
 * Two arrangements in one view tree. Standby -- clock, night and the peek between
 * them -- is a single centred clock whose weight, colour and company change while it
 * stays put, so the night reads as one surface changing its mind. Ringing is the one
 * moment that earns a different layout: the time moves aside to make room for two
 * buttons sized for a thumb that is still asleep.
 *
 * Set in Jost ([CoverFont]). Plain views rather than Compose -- a presentation on a
 * secondary display has no lifecycle owner of its own -- and laid out in landscape,
 * since a tented phone puts this display on its side. [RotatableHost] turns it the
 * right way up.
 */
class StandbyCoverScreen(
    private val context: Context,
    private val onStop: () -> Unit,
    private val onSnooze: () -> Unit,
    private val onExit: () -> Unit,
    private val onTap: () -> Unit
) {

    private val density = context.resources.displayMetrics.density

    // Standby: the centred clock and the line beneath it.
    private val clock: TextClock
    private val infoRow: LinearLayout
    private val alarmGlyph: AlarmGlyph
    private val alarmText: TextView
    private val alarmDot: View
    private val peekLine: TextView
    private val subtitle: TextView
    private val standby: LinearLayout

    // Ringing: the time to one side, snooze and stop to the other.
    private val ringing: LinearLayout
    private val snoozeUntil: TextView

    private val exit: TextView

    val root: FrameLayout

    private var mode = StandbyMode.CLOCK
    private var peeking = false

    init {
        clock = clockView(sizeSp = 168f)

        val date = TextClock(context).apply {
            format12Hour = DATE_FORMAT
            format24Hour = DATE_FORMAT
            textSize = 18f
            setTextColor(DAY_MUTED)
            typeface = CoverFont.weight(context, CoverFont.MEDIUM)
        }
        alarmDot = dot(Color.parseColor("#4A4A4A"))
        alarmGlyph = AlarmGlyph(context).apply { colour = AMBER }
        alarmText = TextView(context).apply {
            textSize = 18f
            setTextColor(AMBER)
            typeface = CoverFont.weight(context, CoverFont.MEDIUM)
            fontFeatureSettings = CoverFont.FIGURES
        }
        infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(date)
            addView(alarmDot, margins(start = 12, end = 12, width = 4, height = 4))
            addView(alarmGlyph, margins(end = 6, width = 16, height = 16))
            addView(alarmText)
        }

        peekLine = TextView(context).apply {
            textSize = 18f
            setTextColor(NIGHT_LINE)
            typeface = CoverFont.weight(context, CoverFont.MEDIUM)
            fontFeatureSettings = CoverFont.FIGURES
            visibility = View.GONE
        }

        subtitle = TextView(context).apply {
            textSize = 14f
            setTextColor(AMBER)
            typeface = CoverFont.weight(context, CoverFont.REGULAR)
            fontFeatureSettings = CoverFont.FIGURES
            visibility = View.GONE
        }

        standby = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(clock)
            addView(infoRow, margins(top = 6))
            addView(peekLine, margins(top = 8))
            addView(subtitle, margins(top = 10))
        }

        // ---- ringing ----

        val ringClock = clockView(sizeSp = 150f).apply {
            typeface = CoverFont.weight(context, CoverFont.LIGHT)
        }
        val greeting = TextView(context).apply {
            text = "Good morning"
            textSize = 18f
            setTextColor(AMBER)
            typeface = CoverFont.weight(context, CoverFont.MEDIUM)
        }
        val ringDate = TextClock(context).apply {
            format12Hour = DATE_FORMAT
            format24Hour = DATE_FORMAT
            textSize = 17f
            setTextColor(DAY_MUTED)
            typeface = CoverFont.weight(context, CoverFont.MEDIUM)
        }
        val timeColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(greeting)
            addView(ringClock, margins(top = 4))
            addView(ringDate, margins(top = 4))
        }

        snoozeUntil = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#B31A1200"))
            typeface = CoverFont.weight(context, CoverFont.MEDIUM)
            fontFeatureSettings = CoverFont.FIGURES
            gravity = Gravity.CENTER
        }
        // Snooze is the big one. Hit half asleep, the larger target should be the
        // one whose mistake costs nine minutes rather than the morning.
        val snooze = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = capsule(AMBER)
            isClickable = true
            setOnClickListener { onSnooze() }
            addView(TextView(context).apply {
                text = "Snooze"
                textSize = 24f
                setTextColor(Color.parseColor("#1A1200"))
                typeface = CoverFont.weight(context, CoverFont.MEDIUM)
                gravity = Gravity.CENTER
            })
            addView(snoozeUntil, margins(top = 2))
        }
        val stop = TextView(context).apply {
            text = "Stop"
            textSize = 22f
            setTextColor(DAY_CLOCK)
            typeface = CoverFont.weight(context, CoverFont.REGULAR)
            gravity = Gravity.CENTER
            background = capsule(Color.parseColor("#262629"))
            isClickable = true
            setOnClickListener { onStop() }
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(snooze, LinearLayout.LayoutParams(dp(280), dp(104)))
            addView(stop, LinearLayout.LayoutParams(dp(280), dp(72)).apply { topMargin = dp(14) })
        }

        ringing = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(48), 0, dp(48), 0)
            visibility = View.GONE
            addView(timeColumn)
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(buttons)
        }

        exit = TextView(context).apply {
            text = "✕"
            textSize = 17f
            setTextColor(Color.parseColor("#6E6E6E"))
            gravity = Gravity.CENTER
            // A comfortable target without a visible chrome box around it.
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
            setOnClickListener { onExit() }
        }

        root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(standby, fill())
            addView(ringing, fill())
            addView(
                exit,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            )
            isClickable = true
            setOnClickListener { onTap() }
        }

        applyMode(StandbyMode.CLOCK, peek = false)
    }

    // ---- state --------------------------------------------------------------

    fun applyMode(next: StandbyMode, peek: Boolean) {
        val wasRinging = mode == StandbyMode.RINGING
        mode = next
        peeking = peek
        val isRinging = next == StandbyMode.RINGING
        val night = next == StandbyMode.NIGHT || next == StandbyMode.SUNRISE

        if (isRinging != wasRinging || ringing.visibility == standby.visibility) {
            ringing.visibility = if (isRinging) View.VISIBLE else View.GONE
            standby.visibility = if (isRinging) View.GONE else View.VISIBLE
        }

        // Hidden at night so a half-awake hand cannot leave standby by accident; a
        // peek brings it back for anyone who actually means it. Never while ringing:
        // stop and snooze are the only ways out of an alarm.
        exit.fadeTo(if (isRinging || (night && !peek)) 0f else 1f)
        exit.isClickable = !isRinging && (!night || peek)
        exit.setTextColor(if (night) NIGHT_MUTED else Color.parseColor("#6E6E6E"))

        infoRow.visibility = if (night) View.GONE else View.VISIBLE
        peekLine.visibility = if (night && peek) View.VISIBLE else View.GONE

        if (night) {
            clock.setTextColor(if (peek) NIGHT_CLOCK_PEEK else NIGHT_CLOCK)
            // The thinnest weight lights the fewest pixels, all night.
            clock.typeface = CoverFont.weight(
                context,
                if (peek) CoverFont.THIN + 50 else CoverFont.THIN
            )
            clock.textSize = 184f
        } else {
            clock.setTextColor(DAY_CLOCK)
            clock.typeface = CoverFont.weight(context, CoverFont.forPreference(prefsWeight))
            clock.textSize = 168f
        }
        subtitle.setTextColor(if (night) NIGHT_LINE else AMBER)
        subtitle.fadeTo(if (night && !peek && !forceSubtitle) 0f else 1f)
    }

    /**
     * The next alarm, as a time ("6:30") and how far off it is ("in 4 h 13 min");
     * null when none is set.
     */
    fun setAlarm(time: String?, countdown: String?) {
        val set = time != null
        alarmDot.visibility = if (set) View.VISIBLE else View.GONE
        alarmGlyph.visibility = if (set) View.VISIBLE else View.GONE
        alarmText.visibility = if (set) View.VISIBLE else View.GONE
        alarmText.text = time ?: ""
        peekLine.text = if (set) "Alarm $time  ·  $countdown" else "No alarm set"
    }

    /** When a snooze pressed now would ring again. */
    fun setSnoozeUntil(time: String) {
        snoozeUntil.text = "until $time"
    }

    /** A line under the clock for what must be said: a battery warning, diagnostics. */
    fun setSubtitle(text: String?) {
        if (text.isNullOrEmpty()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = text
        }
    }

    /** Drift, so the brightest thing on screen never sits still for a whole night. */
    fun setShift(x: Float, y: Float) {
        standby.animate().translationX(x).translationY(y).setDuration(4_000L).start()
    }

    /** Blanked for the night; a tap restores it. */
    fun setBlanked(blanked: Boolean) {
        standby.fadeTo(if (blanked) 0f else 1f)
        if (blanked) exit.fadeTo(0f)
    }

    /** Keep the subtitle readable at night too, while diagnostics are on. */
    var forceSubtitle: Boolean = false

    var prefsWeight: Int = 0
        set(value) {
            if (field == value) return
            field = value
            if (mode == StandbyMode.CLOCK) {
                clock.typeface = CoverFont.weight(context, CoverFont.forPreference(value))
            }
        }

    // ---- building blocks ----------------------------------------------------

    private fun clockView(sizeSp: Float) = TextClock(context).apply {
        format12Hour = "h:mm"
        format24Hour = "H:mm"
        textSize = sizeSp
        includeFontPadding = false
        letterSpacing = -0.02f
        fontFeatureSettings = CoverFont.FIGURES
        setTextColor(DAY_CLOCK)
        typeface = CoverFont.weight(context, CoverFont.THIN)
    }

    private fun View.fadeTo(target: Float) {
        if (alpha == target) return
        animate().alpha(target).setDuration(FADE_MS).start()
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun dot(colour: Int) = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colour)
        }
    }

    private fun capsule(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(100).toFloat()
        setColor(fill)
    }

    private fun fill() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )

    private fun margins(
        top: Int = 0,
        start: Int = 0,
        end: Int = 0,
        width: Int? = null,
        height: Int? = null
    ) = LinearLayout.LayoutParams(
        width?.let { dp(it) } ?: LinearLayout.LayoutParams.WRAP_CONTENT,
        height?.let { dp(it) } ?: LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        marginStart = dp(start)
        marginEnd = dp(end)
    }

    /** A small alarm-clock mark, drawn rather than borrowed, to match Jost's stroke. */
    private class AlarmGlyph(context: Context) : View(context) {
        var colour: Int = Color.WHITE

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val u = width / 16f
            paint.color = colour
            paint.strokeWidth = 1.5f * u
            canvas.drawCircle(8f * u, 9f * u, 5.2f * u, paint)
            canvas.drawLine(8f * u, 6.4f * u, 8f * u, 9f * u, paint)
            canvas.drawLine(8f * u, 9f * u, 9.8f * u, 10.2f * u, paint)
            canvas.drawLine(3f * u, 2.6f * u, 1.6f * u, 4f * u, paint)
            canvas.drawLine(13f * u, 2.6f * u, 14.4f * u, 4f * u, paint)
        }
    }

    private companion object {
        const val DATE_FORMAT = "EEEE d MMMM"

        val AMBER = Color.parseColor("#F0B266")
        val DAY_CLOCK = Color.parseColor("#F4F4F4")
        val DAY_MUTED = Color.parseColor("#9A9A9A")

        // Deep reds, chosen as emitted values rather than dimmed whites: red barely
        // drives the blue subpixel, which is the one that ages fastest.
        val NIGHT_CLOCK = Color.parseColor("#7A2418")
        val NIGHT_CLOCK_PEEK = Color.parseColor("#D9553A")
        val NIGHT_LINE = Color.parseColor("#8A3020")
        val NIGHT_MUTED = Color.parseColor("#5A1C12")

        const val FADE_MS = 900L
    }
}
