package dev.rokerato.foldprobe

import android.content.Context
import android.widget.FrameLayout

/**
 * Hosts a single child view and renders it rotated by a multiple of 90 degrees.
 *
 * A window presented on the cover display does not follow the device's physical
 * rotation — the cover screen keeps its natural portrait orientation even when the
 * phone is tented on its side. There is no orientation flag to set on a presented
 * window, so the content gets rotated inside it instead.
 *
 * At 90 or 270 degrees the child is measured with width and height swapped, so a
 * landscape layout fills a portrait display exactly once rotated.
 */
class RotatableHost(context: Context) : FrameLayout(context) {

    var contentRotation: Float = 0f
        set(value) {
            field = ((value % 360f) + 360f) % 360f
            requestLayout()
        }

    private val swapsAxes: Boolean
        get() = contentRotation == 90f || contentRotation == 270f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        getChildAt(0)?.let { child ->
            val childWidth = if (swapsAxes) height else width
            val childHeight = if (swapsAxes) width else height
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val childWidth = child.measuredWidth
        val childHeight = child.measuredHeight
        val offsetX = ((right - left) - childWidth) / 2
        val offsetY = ((bottom - top) - childHeight) / 2
        child.layout(offsetX, offsetY, offsetX + childWidth, offsetY + childHeight)
        child.pivotX = childWidth / 2f
        child.pivotY = childHeight / 2f
        child.rotation = contentRotation
    }
}
