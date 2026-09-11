package dev.rokerato.foldprobe

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

/**
 * The ringing screen.
 *
 * Tented on a bedside table, the inner display faces down into the tent and becomes
 * a lamp, while the cover display faces the sleeper and carries the clock and the
 * stop and snooze buttons. Both panels are lit at once through dual-screen mode
 * ([WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA]), which a Galaxy Z
 * Fold 6 supports despite the Android documentation listing only the Pixel Fold.
 *
 * Where dual-screen mode is unavailable the alarm still works: the same controls
 * are drawn on whichever display is active.
 */
@OptIn(ExperimentalWindowApi::class)
class AlarmActivity : ComponentActivity(), SensorEventListener {

    private lateinit var prefs: AlarmPrefs
    private lateinit var controller: WindowAreaController
    private lateinit var executor: Executor
    private val ringer by lazy { AlarmRinger(this) }

    private var rearInfo: WindowAreaInfo? = null
    private var presenter: WindowAreaSessionPresenter? = null
    private var coverScreen: AlarmCoverScreen? = null
    private var coverHost: RotatableHost? = null

    private var sensorManager: SensorManager? = null
    private val gravity = FloatArray(3)
    private var appliedRotation = -1f

    private val onCoverScreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AlarmPrefs(this)
        controller = WindowAreaController.getOrCreate()
        executor = ContextCompat.getMainExecutor(this)
        sensorManager = getSystemService(SensorManager::class.java)

        // Wake the screen and show over the lock screen: this has to be readable
        // without unlocking the phone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        applyLampBrightness()

        ringer.start()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.windowAreaInfos.collect(::onAreas)
            }
        }

        setContent { LampScreen() }
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.let { manager ->
            manager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensor ->
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ringer.stop()
        runCatching { presenter?.close() }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GRAVITY) return
        System.arraycopy(event.values, 0, gravity, 0, 3)
        val quadrant = CoverRotation.quadrantOf(gravity[0], gravity[1])
        if (quadrant != appliedRotation) {
            appliedRotation = quadrant
            coverHost?.contentRotation = quadrant
        }
    }

    private fun onAreas(infos: List<WindowAreaInfo>) {
        val rear = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } ?: return
        rearInfo = rear
        if (presenter != null) return
        val status = runCatching {
            rear.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status
        }.getOrNull()
        if (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) {
            startCoverScreen(rear)
        }
    }

    private fun startCoverScreen(info: WindowAreaInfo) {
        runCatching {
            controller.presentContentOnWindowArea(
                token = info.token,
                activity = this,
                executor = executor,
                windowAreaPresentationSessionCallback = object : WindowAreaPresentationSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                        presenter = session
                        val cover = AlarmCoverScreen(
                            context = session.context,
                            onStop = { stopAlarm() },
                            onSnooze = { snoozeAlarm() }
                        )
                        val host = RotatableHost(session.context).apply {
                            addView(cover.root)
                            contentRotation = CoverRotation.quadrantOf(gravity[0], gravity[1])
                        }
                        coverScreen = cover
                        coverHost = host
                        session.setContentView(host)
                        onCoverScreen.value = true
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        presenter = null
                        coverScreen = null
                        coverHost = null
                        onCoverScreen.value = false
                    }

                    override fun onContainerVisibilityChanged(isVisible: Boolean) = Unit
                }
            )
        }
    }

    private fun applyLampBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = prefs.lampBrightness.coerceIn(0.05f, 1f)
        }
    }

    private fun lampColor(): Color {
        // Warmth pulls green and blue down, leaving red, so 0 is near-white and 1 amber.
        val warmth = prefs.lampWarmth.coerceIn(0f, 1f)
        return Color(
            red = 1f,
            green = 1f - 0.30f * warmth,
            blue = 1f - 0.75f * warmth
        )
    }

    private fun stopAlarm() {
        ringer.stop()
        AlarmReceiver.dismissNotification(this)
        runCatching { presenter?.close() }
        finish()
    }

    private fun snoozeAlarm() {
        ringer.stop()
        AlarmReceiver.dismissNotification(this)
        val snoozeAt = System.currentTimeMillis() + prefs.snoozeMinutes * 60_000L
        AlarmScheduler.schedule(this, snoozeAt)
        coverScreen?.showStatus("snoozed ${prefs.snoozeMinutes} min")
        runCatching { presenter?.close() }
        finish()
    }

    /**
     * The inner display. When the cover screen has the controls this is purely a
     * lamp; otherwise it carries stop and snooze itself, so the alarm is never
     * undismissable.
     */
    @Composable
    private fun LampScreen() {
        Box(
            modifier = Modifier.fillMaxSize().background(lampColor()),
            contentAlignment = Alignment.Center
        ) {
            if (onCoverScreen.value) {
                Text(
                    "⏰",
                    fontSize = 64.sp,
                    color = Color(0x22000000)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "GOOD MORNING",
                        color = Color(0x99000000),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { stopAlarm() }) { Text("Stop") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { snoozeAlarm() }) {
                        Text("Snooze ${prefs.snoozeMinutes} min", color = Color(0xCC000000))
                    }
                }
            }
        }
    }
}
