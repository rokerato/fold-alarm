package dev.rokerato.foldprobe

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.os.VibratorManager

/** Plays the user's alarm tone on the alarm stream, and vibrates alongside it. */
class AlarmRinger(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /**
     * @param rampSeconds fade the alarm in over this long, from silence. Waking to a
     *   blast is a worse way to start a day than waking slowly, and a fade still
     *   reaches full volume well inside a minute.
     */
    fun start(rampSeconds: Int = 0) {
        if (player != null) return
        val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, tone)
                isLooping = true
                prepare()
                if (rampSeconds > 0) setVolume(0f, 0f)
                start()
            }
        }.getOrNull()
        if (rampSeconds > 0) rampVolume(rampSeconds)
        startVibration()
    }

    private fun rampVolume(seconds: Int) {
        val handler = Handler(Looper.getMainLooper())
        val steps = seconds * 4
        for (step in 1..steps) {
            handler.postDelayed({
                val level = step.toFloat() / steps
                runCatching { player?.setVolume(level, level) }
            }, step * 250L)
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun startVibration() {
        val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!service.hasVibrator()) return
        vibrator = service
        runCatching {
            service.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 600L, 900L), 0)
            )
        }
    }
}
