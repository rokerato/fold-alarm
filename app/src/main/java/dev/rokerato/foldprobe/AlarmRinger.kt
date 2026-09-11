package dev.rokerato.foldprobe

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Plays the user's alarm tone on the alarm stream, and vibrates alongside it. */
class AlarmRinger(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
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
                start()
            }
        }.getOrNull()
        startVibration()
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
