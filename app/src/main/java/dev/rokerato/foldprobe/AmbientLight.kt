package dev.rokerato.foldprobe

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Decides whether the room is dark, from the ambient light sensor.
 *
 * Two things keep this from flickering on the boundary. The thresholds differ by
 * direction, so a room hovering around the trigger point settles instead of
 * oscillating; and a reading has to persist for [SETTLE_MS] before it counts, so a
 * hand passing over the sensor or a phone screen lighting up across the room does
 * not flip the display.
 */
class AmbientLight(
    private val context: Context,
    private val onDarkChanged: (Boolean) -> Unit
) : SensorEventListener {

    private var manager: SensorManager? = null
    private var isDark = false
    private var pendingDark: Boolean? = null
    private var pendingSince = 0L

    /** Last reading in lux, for diagnostics. */
    var lux: Float = -1f
        private set

    fun start() {
        val service = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = service.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        manager = service
        service.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        manager?.unregisterListener(this)
        manager = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT) return
        lux = event.values[0]

        val candidate = when {
            lux <= DARK_BELOW_LUX -> true
            lux >= LIGHT_ABOVE_LUX -> false
            else -> isDark // inside the hysteresis band, hold the current state
        }
        if (candidate == isDark) {
            pendingDark = null
            return
        }

        val now = System.currentTimeMillis()
        if (pendingDark != candidate) {
            pendingDark = candidate
            pendingSince = now
            return
        }
        if (now - pendingSince >= SETTLE_MS) {
            isDark = candidate
            pendingDark = null
            onDarkChanged(isDark)
        }
    }

    private companion object {
        /** A dark bedroom reads under a lux; a phone face-down nearby reads a few. */
        const val DARK_BELOW_LUX = 3f
        const val LIGHT_ABOVE_LUX = 12f
        const val SETTLE_MS = 4_000L
    }
}
