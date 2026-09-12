package dev.rokerato.foldprobe

import kotlin.random.Random

/**
 * Slowly drifts static content around a few pixels so no one lit pixel carries the
 * same load all night. Samsung does the same for the status bar icons; the movement
 * is far too slow and small to notice, and the clock is the only thing on the cover
 * screen bright enough to matter.
 */
class PixelShift(private val radiusPx: Int = 10) {

    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    private var lastStep = 0L

    /** Returns true when the offset moved, so the caller can animate to it. */
    fun stepIfDue(now: Long = System.currentTimeMillis()): Boolean {
        if (now - lastStep < STEP_INTERVAL_MS) return false
        lastStep = now
        // A random walk rather than a fixed cycle: over a night it covers the area
        // evenly without ever settling into a repeating pattern of its own.
        offsetX = (Random.nextFloat() * 2f - 1f) * radiusPx
        offsetY = (Random.nextFloat() * 2f - 1f) * radiusPx
        return true
    }

    private companion object {
        const val STEP_INTERVAL_MS = 60_000L
    }
}
