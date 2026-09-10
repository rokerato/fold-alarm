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
 * The view shown on the cover display during a dual-screen presentation session.
 *
 * Deliberately built from plain Android views rather than Compose: a presentation
 * container on a secondary display has no lifecycle owner of its own, and the point
 * of this probe is to test the platform behaviour, not the UI toolkit on top of it.
 *
 * The two buttons exist to answer a specific question: does touch input actually
 * route to a window presented on the cover screen? If [onTap] never fires, it does not.
 */
class CoverScreen(context: Context, private val onTap: (String) -> Unit) {

    private val tapReadout: TextView
    val root: View

    init {
        val amber = Color.parseColor("#F0B266")

        tapReadout = TextView(context).apply {
            text = "waiting for a tap…"
            textSize = 13f
            setTextColor(Color.parseColor("#8A8A8A"))
            gravity = Gravity.CENTER
        }

        val greeting = TextView(context).apply {
            text = "GOOD MORNING"
            textSize = 15f
            setTextColor(amber)
            letterSpacing = 0.08f
        }

        val clock = TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = 64f
            setTextColor(Color.WHITE)
        }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(pill(context, "stop", Color.parseColor("#3A3A3A"), Color.WHITE))
            addView(pill(context, "snooze", amber, Color.parseColor("#1A1200")))
        }

        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(40, 40, 40, 40)
            addView(greeting)
            addView(clock)
            addView(buttons)
            addView(tapReadout)
        }
    }

    private fun pill(context: Context, label: String, bg: Int, fg: Int): View =
        TextView(context).apply {
            text = label
            textSize = 20f
            setTextColor(fg)
            gravity = Gravity.CENTER
            setPadding(48, 28, 48, 28)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 96f
                setColor(bg)
            }
            isClickable = true
            setOnClickListener { onTap(label) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(12, 24, 12, 24) }
        }

    fun reportTap(label: String, count: Int) {
        tapReadout.text = "TOUCH WORKS — '$label', $count tap(s)"
        tapReadout.setTextColor(Color.parseColor("#7BD88F"))
    }
}
