package dev.rokerato.foldprobe

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Standby: the bedside clock, and the alarm it becomes.
 *
 * Tented, the cover display faces the sleeper and carries the clock; the inner
 * display faces down into the tent and stays black until the alarm approaches, when
 * the half nearest the cover screen glows — light that reaches the room off the
 * table rather than head-on.
 *
 * One activity owns the whole night. [StandbyMode] values are states inside it, not
 * separate screens, so the clock never jumps and every change cross-fades.
 *
 * It also rings in place. When standby is already running there is no need for the
 * full-screen-intent notification to launch anything, which takes the least reliable
 * link out of the chain for the case that matters most.
 */
@OptIn(ExperimentalWindowApi::class)
class StandbyActivity : ComponentActivity(), SensorEventListener {

    private lateinit var prefs: AlarmPrefs
    private lateinit var controller: WindowAreaController
    private lateinit var executor: Executor
    private val ringer by lazy { AlarmRinger(this) }

    private var rearInfo: WindowAreaInfo? = null
    private var presenter: WindowAreaSessionPresenter? = null
    private var cover: StandbyCoverScreen? = null
    private var coverHost: RotatableHost? = null

    private var sensorManager: SensorManager? = null
    private val gravity = FloatArray(3)
    private var appliedRotation = -1f
    private val pixelShift = PixelShift()
    private lateinit var ambient: AmbientLight

    private var isDark = false
    private var ringing = false
    private var watchedTarget = 0L
    private var peekUntil = 0L
    private var lastInteraction = System.currentTimeMillis()

    private val mode = mutableStateOf(StandbyMode.CLOCK)
    private val glow = mutableStateOf(0f)
    private val foldVertical = mutableStateOf(true)
    private val onCover = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AlarmPrefs(this)
        controller = WindowAreaController.getOrCreate()
        executor = ContextCompat.getMainExecutor(this)
        sensorManager = getSystemService(SensorManager::class.java)
        ambient = AmbientLight(this) { dark ->
            isDark = dark
            lastInteraction = System.currentTimeMillis()
            refresh()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (intent?.getBooleanExtra(EXTRA_RING, false) == true) startRinging()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.windowAreaInfos.collect(::onAreas)
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@StandbyActivity)
                    .windowLayoutInfo(this@StandbyActivity)
                    .collect(::onLayout)
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    tick()
                    delay(1_000L)
                }
            }
        }

        setContent { InnerScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The alarm fired while standby was already up: ring in place.
        if (intent.getBooleanExtra(EXTRA_RING, false)) startRinging()
    }

    override fun onResume() {
        super.onResume()
        ambient.start()
        sensorManager?.let { manager ->
            manager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensor ->
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ambient.stop()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ringer.stop()
        runCatching { presenter?.close() }
    }

    // ---- the clock that drives everything -----------------------------------

    private fun tick() {
        val now = System.currentTimeMillis()

        val target = prefs.nextTrigger
        if (target > now && target != watchedTarget) watchedTarget = target
        if (!ringing && watchedTarget in 1..now) startRinging()

        if (peekUntil in 1..now) {
            peekUntil = 0
            refresh()
        }
        if (pixelShift.stepIfDue(now)) {
            cover?.setShift(pixelShift.offsetX, pixelShift.offsetY)
        }
        // The window area can be taken back; notice and re-present rather than
        // leaving a blank cover screen until morning.
        if (presenter == null) rearInfo?.let(::tryPresent)

        refresh()
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val next = when {
            ringing -> StandbyMode.RINGING
            sunriseProgress(now) != null -> StandbyMode.SUNRISE
            isDark && prefs.nightTint -> StandbyMode.NIGHT
            else -> StandbyMode.CLOCK
        }
        mode.value = next

        glow.value = when {
            ringing -> if (prefs.sunriseMode == SUNRISE_OFF) 0f else 1f
            next == StandbyMode.SUNRISE -> sunriseProgress(now) ?: 0f
            else -> 0f
        }

        val peek = peekUntil > now
        cover?.applyMode(next, peek)
        cover?.setSubtitle(subtitleFor(next))
        cover?.prefsWeight = prefs.clockWeight

        val blanked = prefs.nightBlank &&
            next == StandbyMode.NIGHT &&
            !peek &&
            now - lastInteraction > prefs.nightBlankMinutes * 60_000L
        cover?.setBlanked(blanked)

        // The inner display is the lamp and nothing else, so its brightness follows
        // the glow and sits at the floor whenever there is none.
        window.attributes = window.attributes.apply {
            screenBrightness = (glow.value * prefs.lampBrightness).coerceIn(0.02f, 1f)
        }
    }

    /** Fraction through the sunrise window, or null when we are not in one. */
    private fun sunriseProgress(now: Long): Float? {
        if (prefs.sunriseMode != SUNRISE_RAMP) return null
        val target = watchedTarget.takeIf { it > now } ?: return null
        val window = prefs.sunriseMinutes * 60_000L
        val start = target - window
        if (now < start) return null
        return ((now - start).toFloat() / window).coerceIn(0f, 1f)
    }

    private fun subtitleFor(next: StandbyMode): String? {
        if (next == StandbyMode.RINGING) return null
        val battery = batteryWarning()
        if (battery != null) return battery
        val target = prefs.nextTrigger
        if (!prefs.enabled || target <= 0L) return "No alarm set"
        return "Alarm " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(target))
    }

    /** Eight hours of cover screen with no charger is fine; eight hours from 15% is not. */
    private fun batteryWarning(): String? {
        val manager = getSystemService(BatteryManager::class.java) ?: return null
        if (manager.isCharging) return null
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 1..30) "Battery $level% · not charging" else null
    }

    // ---- alarm --------------------------------------------------------------

    private fun startRinging() {
        if (ringing) return
        ringing = true
        watchedTarget = 0L
        AlarmReceiver.dismissNotification(this)
        ringer.start(rampSeconds = RAMP_SECONDS)
        refresh()
    }

    private fun stopAlarm() {
        ringer.stop()
        AlarmReceiver.dismissNotification(this)
        ringing = false
        lastInteraction = System.currentTimeMillis()
        refresh()
    }

    private fun snoozeAlarm() {
        ringer.stop()
        AlarmReceiver.dismissNotification(this)
        ringing = false
        AlarmScheduler.schedule(this, System.currentTimeMillis() + prefs.snoozeMinutes * 60_000L)
        lastInteraction = System.currentTimeMillis()
        refresh()
    }

    private fun onCoverTap() {
        lastInteraction = System.currentTimeMillis()
        peekUntil = lastInteraction + PEEK_MS
        refresh()
    }

    // ---- displays -----------------------------------------------------------

    private fun onAreas(infos: List<WindowAreaInfo>) {
        val rear = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } ?: return
        rearInfo = rear
        if (presenter == null) tryPresent(rear)
    }

    private fun tryPresent(info: WindowAreaInfo) {
        val status = runCatching {
            info.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status
        }.getOrNull()
        if (status != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) return
        runCatching {
            controller.presentContentOnWindowArea(
                token = info.token,
                activity = this,
                executor = executor,
                windowAreaPresentationSessionCallback = object : WindowAreaPresentationSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                        presenter = session
                        val screen = StandbyCoverScreen(
                            context = session.context,
                            onStop = { stopAlarm() },
                            onSnooze = { snoozeAlarm() },
                            onExit = { finish() },
                            onTap = { onCoverTap() }
                        )
                        val host = RotatableHost(session.context).apply {
                            addView(screen.root)
                            contentRotation = CoverRotation.quadrantOf(gravity[0], gravity[1])
                        }
                        cover = screen
                        coverHost = host
                        session.setContentView(host)
                        Log.i(TAG, "cover session started")
                        onCover.value = true
                        refresh()
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        Log.i(TAG, "cover session ended" + (t?.let { ": ${it.message}" } ?: ""))
                        presenter = null
                        cover = null
                        coverHost = null
                        onCover.value = false
                    }

                    // Logged rather than acted on: a system overlay taking the cover
                    // screen is the likeliest signature of a notification landing on
                    // it, and we do not yet know what One UI does here. An overnight
                    // run leaves a record to design against.
                    override fun onContainerVisibilityChanged(isVisible: Boolean) {
                        Log.i(TAG, "cover container visible = $isVisible")
                    }
                }
            )
        }
    }

    private fun onLayout(info: WindowLayoutInfo) {
        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        foldVertical.value = fold == null ||
            fold.orientation == FoldingFeature.Orientation.VERTICAL
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

    // ---- the inner display --------------------------------------------------

    /**
     * Black, except for the glow. Only one half lights: the half that shares its
     * edge with the cover screen points away from the sleeper when the phone is
     * tented, so its light arrives bounced rather than direct. The other half stays
     * at pure black, where OLED pixels are simply off.
     */
    @Composable
    private fun InnerScreen() {
        val lit by animateColorAsState(
            targetValue = glowColour(glow.value),
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 2_000),
            label = "glow"
        )
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (foldVertical.value) {
                Row(Modifier.fillMaxSize()) {
                    GlowHalf(prefs.glowFirstHalf, lit, Modifier.weight(1f).fillMaxHeight())
                    GlowHalf(!prefs.glowFirstHalf, lit, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    GlowHalf(prefs.glowFirstHalf, lit, Modifier.weight(1f).fillMaxWidth())
                    GlowHalf(!prefs.glowFirstHalf, lit, Modifier.weight(1f).fillMaxWidth())
                }
            }
            if (!onCover.value) Fallback(Modifier.align(Alignment.Center))
        }
    }

    @Composable
    private fun GlowHalf(lit: Boolean, colour: Color, modifier: Modifier) {
        Box(modifier.background(if (lit) colour else Color.Black))
    }

    /**
     * Shown only when the cover screen is unavailable. An alarm that cannot be
     * dismissed would be worse than one that is merely less pretty.
     */
    @Composable
    private fun Fallback(modifier: Modifier) {
        Column(
            modifier = modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val alarmSounding = mode.value == StandbyMode.RINGING
            Text(
                if (alarmSounding) "GOOD MORNING" else "Standby",
                color = Color(0xCCFFFFFF),
                fontSize = 20.sp,
                fontWeight = FontWeight.Light
            )
            if (alarmSounding) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = { stopAlarm() }) { Text("Stop") }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { snoozeAlarm() }) {
                    Text("Snooze ${prefs.snoozeMinutes} min", color = Color(0xAAFFFFFF))
                }
            }
        }
    }

    /**
     * Deep red at the start of the ramp, warm amber by the end — the emitted colour
     * carries the brightness, so a dim glow is a dim colour rather than a bright one
     * turned down.
     */
    private fun glowColour(intensity: Float): Color {
        if (intensity <= 0f) return Color.Black
        val v = (intensity * prefs.lampBrightness).coerceIn(0f, 1f)
        val warmth = prefs.lampWarmth.coerceIn(0f, 1f)
        return Color(
            red = v,
            green = (0.12f + (0.62f - 0.30f * warmth) * intensity) * v,
            blue = (0.01f + (0.45f - 0.40f * warmth) * intensity) * v
        )
    }

    companion object {
        const val TAG = "FoldAlarm"
        const val EXTRA_RING = "ring"
        private const val SUNRISE_RAMP = 0
        private const val SUNRISE_OFF = 2
        private const val PEEK_MS = 6_000L
        private const val RAMP_SECONDS = 30
    }
}
