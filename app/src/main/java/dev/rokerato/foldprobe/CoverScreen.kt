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
 * The view shown on the cover display during a dual-screen presentation session,
 * laid out in landscape because a tented phone puts the cover screen on its side.
 * [RotatableHost] does the actual rotating.
 *
 * Deliberately built from plain Android views rather than Compose: a presentation
 * container on a secondary display has no lifecycle owner of its own.
 */
class CoverScreen(
    context: Context,
    private val onTap: (String) -> Unit,
    private val onRotate: () -> Unit
) {

    private val diagnostics: TextView
    val root: View

    init {
        val amber = Color.parseColor("#F0B266")

        diagnostics = TextView(context).apply {
            text = "waiting for a tap…"
            textSize = 11f
            setTextColor(Color.parseColor("#8A8A8A"))
        }

        val greeting = TextView(context).apply {
            text = "⏰ GOOD MORNING"
            textSize = 15f
            setTextColor(amber)
            letterSpacing = 0.08f
        }

        val clock = TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 72f
            setTextColor(Color.WHITE)
        }

        // Greeting on the left, clock on the right — as in the reference mockup.
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(greeting, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(clock)
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(pill(context, "stop", Color.parseColor("#3A3A3A"), Color.WHITE) { onTap("stop") })
            addView(pill(context, "snooze", amber, Color.parseColor("#1A1200")) { onTap("snooze") })
        }

        val rotateButton = pill(context, "rotate ↻", Color.parseColor("#1E1E1E"), Color.parseColor("#AAAAAA")) {
            onRotate()
        }

        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(56, 32, 56, 32)
            addView(topRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(buttonRow)
            addView(rotateButton)
            addView(diagnostics)
        }
    }

    private fun pill(
        context: Context,
        label: String,
        fillColor: Int,
        labelColor: Int,
        onClick: () -> Unit
    ): View = TextView(context).apply {
        text = label
        textSize = 20f
        setTextColor(labelColor)
        gravity = Gravity.CENTER
        setPadding(48, 24, 48, 24)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 96f
            setColor(fillColor)
        }
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(10, 14, 10, 6) }
    }

    /** Mirrors probe state onto the cover screen, which is the only screen facing the
     *  user when the phone is tented. */
    fun showDiagnostics(text: String) {
        diagnostics.text = text
    }

    fun reportTap(label: String, count: Int) {
        diagnostics.setTextColor(Color.parseColor("#7BD88F"))
        diagnostics.text = "TOUCH WORKS — '$label', $count tap(s)"
    }
}
