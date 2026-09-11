package dev.rokerato.foldprobe

import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Works out how far to rotate content shown on the cover display.
 *
 * A window presented on the cover screen keeps that display's natural portrait
 * orientation however the phone is physically held, and the presenter exposes no
 * orientation flag, so the content is rotated inside the window instead. The angle
 * comes from gravity in the device's own coordinate frame.
 *
 * Verified on a Galaxy Z Fold 6 standing tented on a table: gravity read
 * x=-9.6, y=-0.0, giving 270 degrees, which matched the rotation chosen by hand.
 */
object CoverRotation {

    /** Gravity angle in degrees; zero means the device's natural "up" points up. */
    fun angleOf(gravityX: Float, gravityY: Float): Float =
        Math.toDegrees(atan2(gravityX.toDouble(), gravityY.toDouble())).toFloat()

    /** The nearest quarter turn, normalised to 0, 90, 180 or 270. */
    fun quadrantOf(gravityX: Float, gravityY: Float): Float =
        snapToQuadrant(angleOf(gravityX, gravityY))

    fun snapToQuadrant(degrees: Float): Float {
        val snapped = (degrees / 90f).roundToInt() * 90f
        return ((snapped % 360f) + 360f) % 360f
    }
}
