package dev.rokerato.foldprobe

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
 * One view tree rather than four, because the modes are meant to feel like one
 * surface changing its mind rather than four screens swapping: the clock never
 * moves between states, only its weight, colour and company change.
 *
 * Plain views rather than Compose — a presentation on a secondary display has no
 * lifecycle owner of its own — and laid out in landscape, since a tented phone puts
 * this display on its side. [RotatableHost] turns it the right way up.
 */
class StandbyCoverScreen(
    private val context: Context,
    private val onStop: () -> Unit,
    private val onSnooze: () -> Unit,
    private val onExit: () -> Unit,
    private val onTap: () -> Unit
) {

    private val density = context.resources.displayMetrics.density

    private val greeting: TextView
    private val clock: TextClock
    private val date: TextClock
    private val subtitle: TextView
    private val buttons: LinearLayout
    private val exit: TextView
    private val centre: LinearLayout

    val root: FrameLayout

    private var mode = StandbyMode.CLOCK
    private var peeking = false

    init {
        greeting = TextView(context).apply {
            text = "GOOD MORNING"
            textSize = 15f
            letterSpacing = 0.14f
            setTextColor(AMBER)
            typeface = weightOf(3)
            visibility = View.GONE
        }

        clock = TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 96f
            includeFontPadding = false
            setTextColor(DAY_CLOCK)
            typeface = weightOf(0)
        }

        date = TextClock(context).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 15f
            setTextColor(DAY_MUTED)
            typeface = weightOf(2)
        }

        subtitle = TextView(context).apply {
            textSize = 13f
            setTextColor(AMBER)
            typeface = weightOf(2)
            visibility = View.GONE
        }

        buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(pill("stop", Color.parseColor("#2E2E2E"), Color.WHITE) { onStop() })
            addView(pill("snooze", AMBER, Color.parseColor("#1A1200")) { onSnooze() })
        }

        centre = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(greeting, spacing(bottom = 6))
            addView(clock)
            addView(date, spacing(top = 2))
            addView(subtitle, spacing(top = 10))
            addView(buttons, spacing(top = 18))
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
            addView(
                centre,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            )
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
        mode = next
        peeking = peek
        val night = next == StandbyMode.NIGHT || next == StandbyMode.SUNRISE

        greeting.visibility = if (next == StandbyMode.RINGING) View.VISIBLE else View.GONE
        buttons.visibility = if (next == StandbyMode.RINGING) View.VISIBLE else View.GONE
        date.visibility = if (night) View.GONE else View.VISIBLE
        // Hidden at night so a half-awake hand cannot cancel the alarm; a peek
        // brings it back for anyone who actually means it.
        exit.fadeTo(if (night && !peek) 0f else 1f)

        when {
            next == StandbyMode.RINGING -> {
                clock.setTextColor(DAY_CLOCK)
                clock.typeface = weightOf(1)
                clock.textSize = 88f
            }
            night -> {
                clock.setTextColor(if (peek) NIGHT_CLOCK_PEEK else NIGHT_CLOCK)
                clock.typeface = weightOf(0)
                clock.textSize = 104f
            }
            else -> {
                clock.setTextColor(DAY_CLOCK)
                clock.typeface = weightOf(prefsWeight)
                clock.textSize = 96f
            }
        }
        subtitle.setTextColor(if (night) NIGHT_MUTED else AMBER)
        subtitle.fadeTo(if (night && !peek && !forceSubtitle) 0f else 1f)
    }

    /** The line under the clock: next alarm in the day, a battery warning if one is due. */
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
        centre.animate().translationX(x).translationY(y).setDuration(4_000L).start()
    }

    /** Blanked for the night; a tap restores it. */
    fun setBlanked(blanked: Boolean) {
        centre.fadeTo(if (blanked) 0f else 1f)
        if (blanked) exit.fadeTo(0f)
    }

    /** Keep the subtitle readable at night too, while diagnostics are on. */
    var forceSubtitle: Boolean = false

    var prefsWeight: Int = 0
        set(value) {
            field = value
            if (mode == StandbyMode.CLOCK) clock.typeface = weightOf(value)
        }

    // ---- building blocks ----------------------------------------------------

    private fun View.fadeTo(target: Float) {
        if (alpha == target) return
        animate().alpha(target).setDuration(FADE_MS).start()
    }

    private fun weightOf(step: Int): Typeface = Typeface.create(
        when (step) {
            0 -> "sans-serif-thin"
            1 -> "sans-serif-light"
            2 -> "sans-serif"
            else -> "sans-serif-medium"
        },
        Typeface.NORMAL
    )

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun spacing(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun pill(label: String, fill: Int, ink: Int, onClick: () -> Unit): View =
        TextView(context).apply {
            text = label
            textSize = 21f
            setTextColor(ink)
            typeface = weightOf(2)
            gravity = Gravity.CENTER
            // Pressed half asleep, in the dark: generous, and capsule-shaped in the
            // One UI idiom rather than a rectangle with rounded corners.
            setPadding(dp(34), dp(15), dp(34), dp(15))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(100).toFloat()
                setColor(fill)
            }
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(6), 0, dp(6), 0) }
        }

    private companion object {
        val AMBER = Color.parseColor("#F0B266")
        val DAY_CLOCK = Color.parseColor("#F4F4F4")
        val DAY_MUTED = Color.parseColor("#8C8C8C")

        // Deep reds, chosen as emitted values rather than dimmed whites: red barely
        // drives the blue subpixel, which is the one that ages fastest.
        val NIGHT_CLOCK = Color.parseColor("#7A2418")
        val NIGHT_CLOCK_PEEK = Color.parseColor("#D9553A")
        val NIGHT_MUTED = Color.parseColor("#5A1C12")

        const val FADE_MS = 900L
    }
}
