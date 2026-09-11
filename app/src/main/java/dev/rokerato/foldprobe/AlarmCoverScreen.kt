package dev.rokerato.foldprobe

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * What the cover screen shows while the alarm rings: the time, and the only two
 * controls that matter at 6:30am.
 *
 * Laid out in landscape, because a tented phone puts this display on its side;
 * [RotatableHost] turns it the right way up. Plain views rather than Compose, as a
 * presentation on a secondary display carries no lifecycle owner of its own.
 */
class AlarmCoverScreen(
    context: Context,
    private val onStop: () -> Unit,
    private val onSnooze: () -> Unit
) {

    private val status: TextView
    val root: View

    init {
        val amber = Color.parseColor("#F0B266")

        status = TextView(context).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#707070"))
        }

        val greeting = TextView(context).apply {
            text = "⏰ GOOD MORNING"
            textSize = 16f
            setTextColor(amber)
            letterSpacing = 0.08f
        }

        val clock = TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 76f
            setTextColor(Color.WHITE)
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                greeting,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(clock)
        }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(pill(context, "stop", Color.parseColor("#3A3A3A"), Color.WHITE) { onStop() })
            addView(pill(context, "snooze", amber, Color.parseColor("#1A1200")) { onSnooze() })
        }

        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(64, 36, 64, 36)
            addView(
                topRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(buttons)
            addView(status)
        }
    }

    fun showStatus(text: String) {
        status.text = text
    }

    private fun pill(
        context: Context,
        label: String,
        fillColor: Int,
        labelColor: Int,
        onClick: () -> Unit
    ): View = TextView(context).apply {
        text = label
        textSize = 22f
        setTextColor(labelColor)
        gravity = Gravity.CENTER
        // Generous target: this gets pressed half asleep, in the dark.
        setPadding(72, 34, 72, 34)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 120f
            setColor(fillColor)
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(14, 18, 14, 10) }
    }
}
