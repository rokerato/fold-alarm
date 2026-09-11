package dev.rokerato.foldprobe

import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * A capability probe for the "alarm clock on the cover screen, lamp on the inner
 * screen" concept. It answers three questions on real hardware:
 *
 *  1. Does this device report OPERATION_PRESENT_ON_AREA (dual-screen mode), which is
 *     the only third-party route to having both panels lit at once?
 *  2. Does touch input actually reach a window presented on the cover screen?
 *  3. Is the capability still available in a tented, half-folded posture?
 */
@OptIn(ExperimentalWindowApi::class)
class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var controller: WindowAreaController
    private lateinit var executor: Executor

    private var rearInfo: WindowAreaInfo? = null
    private var presenter: WindowAreaSessionPresenter? = null
    private var coverScreen: CoverScreen? = null
    private var coverHost: RotatableHost? = null
    private var tapCount = 0

    private var sensorManager: SensorManager? = null
    private val gravity = FloatArray(3)
    private var lastAutoQuadrant = -1f

    /** Read once when the session starts: the presenter is gone by the time the
     *  user unfolds the phone to read the report. */
    private var coverRotationAtSessionStart: Int? = null
    private var foldState = "unknown"
    private var lastLoggedFoldState = ""

    private val verdict = mutableStateOf("probing…")
    private val deviceDetail = mutableStateOf<List<String>>(emptyList())
    private val areaDetail = mutableStateOf<List<String>>(emptyList())
    private val foldDetail = mutableStateOf<List<String>>(emptyList())
    private val touchResult = mutableStateOf("not tested yet")
    private val log = mutableStateOf<List<String>>(emptyList())
    private val lampOn = mutableStateOf(false)
    private val coverRotation = mutableStateOf(0f)
    private val autoRotate = mutableStateOf(false)
    private val orientationDetail = mutableStateOf<List<String>>(emptyList())
    private val confirmed = mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = WindowAreaController.getOrCreate()
        executor = ContextCompat.getMainExecutor(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SensorManager::class.java)
        refreshDeviceDetail()
        refreshOrientationDetail()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.windowAreaInfos.collect(::onAreas)
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect(::onLayout)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (lampOn.value) LampScreen() else ReportScreen()
            }
        }
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GRAVITY) return
        System.arraycopy(event.values, 0, gravity, 0, 3)
        updateCoverDiagnostics()
        if (autoRotate.value) {
            val quadrant = nearestQuadrant(gravityAngle())
            if (quadrant != lastAutoQuadrant) {
                lastAutoQuadrant = quadrant
                applyCoverRotation(quadrant)
                refreshOrientationDetail()
            }
        }
    }

    // ---- platform callbacks -------------------------------------------------

    private fun onAreas(infos: List<WindowAreaInfo>) {
        if (infos.isEmpty()) {
            areaDetail.value = listOf("The platform reports no window areas at all.")
            verdict.value = "NO — dual-screen mode is not offered on this device"
            return
        }
        val lines = mutableListOf<String>()
        infos.forEach { info ->
            val present = capability(info, WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
            val transfer = capability(info, WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA)
            val bounds = info.metrics.bounds
            lines += "area: ${info.type}"
            lines += "  size ${bounds.width()} x ${bounds.height()} px"
            lines += "  PRESENT_ON_AREA (both screens): ${describe(present)}"
            lines += "  TRANSFER_ACTIVITY (cover only):  ${describe(transfer)}"
            if (info.type == WindowAreaInfo.Type.TYPE_REAR_FACING) {
                rearInfo = info
                verdict.value = when (present) {
                    WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE,
                    WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE ->
                        "YES — this device can light both screens at once"
                    WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE ->
                        "MAYBE — supported, but blocked in the current posture"
                    else ->
                        "NO — dual-screen mode is not offered on this device"
                }
            }
        }
        areaDetail.value = lines
    }

    private fun onLayout(info: WindowLayoutInfo) {
        refreshDeviceDetail()
        val folds = info.displayFeatures.filterIsInstance<FoldingFeature>()
        foldState = folds.firstOrNull()?.state?.toString() ?: "no folding feature"
        if (foldState != lastLoggedFoldState) {
            lastLoggedFoldState = foldState
            addLog("posture -> $foldState (cover rot ${coverRotation.value.toInt()} deg)")
        }
        foldDetail.value = if (folds.isEmpty()) {
            listOf("No folding feature reported — flat, shut, or running on the cover screen.")
        } else {
            folds.flatMap { f ->
                listOf(
                    "state:        ${f.state}   (HALF_OPENED means tented)",
                    "orientation:  ${f.orientation}",
                    "occlusion:    ${f.occlusionType}",
                    "separating:   ${f.isSeparating}",
                    "hinge bounds: ${f.bounds}"
                )
            }
        }
    }

    // ---- the three tests ----------------------------------------------------

    private fun startDualScreen() {
        if (presenter != null) {
            addLog("A session is already running — ignoring.")
            return
        }
        val info = rearInfo
        if (info == null) {
            addLog("No rear-facing window area available to present on.")
            return
        }
        try {
            controller.presentContentOnWindowArea(
                token = info.token,
                activity = this,
                executor = executor,
                windowAreaPresentationSessionCallback = object : WindowAreaPresentationSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                        presenter = session
                        addLog("SESSION STARTED — cover screen should be live now.")
                        coverRotationAtSessionStart =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                session.context.display?.rotation
                            } else {
                                null
                            }
                        val cover = CoverScreen(
                            context = session.context,
                            onTap = { label -> onCoverTap(label) },
                            onRotate = { cycleCoverRotation() },
                            onConfirm = { markOrientationCorrect() }
                        )
                        val host = RotatableHost(session.context).apply {
                            addView(cover.root)
                            contentRotation = coverRotation.value
                        }
                        coverScreen = cover
                        coverHost = host
                        session.setContentView(host)
                        updateCoverDiagnostics()
                        refreshOrientationDetail()
                        setLamp(true)
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        presenter = null
                        coverScreen = null
                        coverHost = null
                        coverRotationAtSessionStart = null
                        addLog("SESSION ENDED" + (t?.let { ": ${it.message}" } ?: " (normally)"))
                        setLamp(false)
                    }

                    override fun onContainerVisibilityChanged(isVisible: Boolean) {
                        addLog("cover container visible = $isVisible")
                    }
                }
            )
        } catch (e: Throwable) {
            addLog("presentContentOnWindowArea threw ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun transferToCover() {
        if (presenter != null) {
            addLog("End the dual-screen session first — the window area is already in use.")
            return
        }
        val info = rearInfo
        if (info == null) {
            addLog("No rear-facing window area available.")
            return
        }
        try {
            controller.transferActivityToWindowArea(
                token = info.token,
                activity = this,
                executor = executor,
                windowAreaSessionCallback = object : WindowAreaSessionCallback {
                    override fun onSessionStarted(session: WindowAreaSession) {
                        addLog("REAR TRANSFER STARTED — inner display should now be off.")
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        addLog("REAR TRANSFER ENDED" + (t?.let { ": ${it.message}" } ?: ""))
                    }
                }
            )
        } catch (e: Throwable) {
            addLog("transferActivityToWindowArea threw ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun onCoverTap(label: String) {
        tapCount++
        touchResult.value = "TOUCH WORKS — '$label', $tapCount tap(s)"
        coverScreen?.reportTap(label, tapCount)
        addLog("cover tap received: $label")
        refreshOrientationDetail()
    }

    private fun stopSession() {
        presenter?.close()
        presenter = null
        setLamp(false)
    }

    private fun setLamp(on: Boolean) {
        lampOn.value = on
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    // ---- cover screen orientation ------------------------------------------

    private fun cycleCoverRotation() {
        autoRotate.value = false
        applyCoverRotation(coverRotation.value + 90f)
        refreshOrientationDetail()
        addLog("cover rotation set to ${coverRotation.value.toInt()} degrees")
    }

    private fun applyCoverRotation(degrees: Float) {
        val normalised = ((degrees % 360f) + 360f) % 360f
        coverRotation.value = normalised
        coverHost?.contentRotation = normalised
        updateCoverDiagnostics()
    }

    /** Angle of the gravity vector in the device's own coordinate frame. Zero means
     *  the device's natural "up" is pointing up. */
    private fun gravityAngle(): Float =
        Math.toDegrees(atan2(gravity[0].toDouble(), gravity[1].toDouble())).toFloat()

    private fun nearestQuadrant(degrees: Float): Float {
        val snapped = (degrees / 90f).roundToInt() * 90f
        return ((snapped % 360f) + 360f) % 360f
    }

    private fun displayRotation(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation else null

    /**
     * Snapshots the orientation at the instant the user says it looks right.
     *
     * The report can only be copied from the inner display, which means unfolding
     * the phone first — so a live reading always describes the un-tented state.
     * This records the tented one while it is still on the table.
     */
    private fun markOrientationCorrect() {
        confirmed.value = listOf(
            "CONFIRMED cover rotation: ${coverRotation.value.toInt()} degrees",
            "mode:              " + if (autoRotate.value) "auto (from gravity)" else "manual",
            "posture:           $foldState",
            "main display:      ${rotationName(displayRotation())}",
            "cover display:     ${rotationName(coverRotationAtSessionStart)}",
            "gravity angle:     ${gravityAngle().roundToInt()} degrees",
            "gravity vector:    " +
                "x=${"%.1f".format(gravity[0])} " +
                "y=${"%.1f".format(gravity[1])} " +
                "z=${"%.1f".format(gravity[2])}"
        )
        coverScreen?.showDiagnostics(
            "RECORDED ${coverRotation.value.toInt()}deg / $foldState — now unfold and copy the report"
        )
        addLog("CONFIRMED ${coverRotation.value.toInt()} deg in posture $foldState")
    }

    private fun rotationName(rotation: Int?): String = when (rotation) {
        null -> "unknown"
        Surface.ROTATION_0 -> "0 degrees"
        Surface.ROTATION_90 -> "90 degrees"
        Surface.ROTATION_180 -> "180 degrees"
        Surface.ROTATION_270 -> "270 degrees"
        else -> rotation.toString()
    }

    private fun refreshOrientationDetail() {
        orientationDetail.value = listOf(
            "cover content rotation: ${coverRotation.value.toInt()} degrees" +
                if (autoRotate.value) " (auto)" else " (manual)",
            "main display rotation:  ${rotationName(displayRotation())}",
            "cover display rotation: ${rotationName(coverRotationAtSessionStart)}",
            "posture:                $foldState",
            "gravity angle:          ${gravityAngle().roundToInt()} degrees",
            "gravity vector:         " +
                "x=${"%.1f".format(gravity[0])} " +
                "y=${"%.1f".format(gravity[1])} " +
                "z=${"%.1f".format(gravity[2])}"
        )
    }

    /** The cover screen is the only screen facing the user when the phone is tented,
     *  so the numbers we need have to be readable there. */
    private fun updateCoverDiagnostics() {
        coverScreen?.showDiagnostics(
            "rot ${coverRotation.value.toInt()}deg" +
                (if (autoRotate.value) " auto" else "") +
                " · grav ${gravityAngle().roundToInt()}deg" +
                " · taps $tapCount"
        )
    }

    // ---- reporting ----------------------------------------------------------

    private fun capability(info: WindowAreaInfo, op: WindowAreaCapability.Operation) =
        try {
            info.getCapability(op)?.status
        } catch (e: Throwable) {
            null
        }

    private fun describe(status: WindowAreaCapability.Status?): String = when (status) {
        null -> "not reported"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED -> "UNSUPPORTED (device cannot)"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> "UNAVAILABLE (not right now)"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> "AVAILABLE (ready)"
        WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> "ACTIVE (running)"
        else -> status.toString()
    }

    private fun refreshDeviceDetail() {
        val dm = resources.displayMetrics
        deviceDetail.value = listOf(
            "device:  ${Build.MANUFACTURER} ${Build.MODEL}",
            "android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "current: ${dm.widthPixels} x ${dm.heightPixels} px @ ${dm.density}x"
        )
    }

    private fun addLog(line: String) {
        log.value = (log.value + line).takeLast(40)
    }

    private fun buildReport(): String = buildString {
        appendLine("=== Fold Probe report ===")
        appendLine("VERDICT: ${verdict.value}")
        appendLine()
        deviceDetail.value.forEach { appendLine(it) }
        appendLine()
        appendLine("-- window areas --")
        areaDetail.value.forEach { appendLine(it) }
        appendLine()
        appendLine("-- folding feature --")
        foldDetail.value.forEach { appendLine(it) }
        appendLine()
        appendLine("-- cover screen touch --")
        appendLine(touchResult.value)
        appendLine()
        appendLine("-- confirmed orientation --")
        if (confirmed.value.isEmpty()) {
            appendLine("not recorded")
        } else {
            confirmed.value.forEach { appendLine(it) }
        }
        appendLine()
        appendLine("-- cover screen orientation (live, after unfolding) --")
        orientationDetail.value.forEach { appendLine(it) }
        appendLine()
        appendLine("-- log --")
        log.value.forEach { appendLine(it) }
    }

    private fun copyReport() {
        refreshOrientationDetail()
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Fold Probe report", buildReport()))
        addLog("Report copied to clipboard.")
    }

    // ---- UI -----------------------------------------------------------------

    @Composable
    private fun LampScreen() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFFFB347)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "LAMP",
                    color = Color(0x33000000),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { stopSession() }) { Text("End session") }
            }
        }
    }

    @Composable
    private fun ReportScreen() {
        Surface(color = Color(0xFF101010), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    "Fold Probe",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    verdict.value,
                    color = Color(0xFFF0B266),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Section("Device", deviceDetail.value)
                Section("Window areas", areaDetail.value)
                Section("Fold posture (live)", foldDetail.value)
                Section("Cover screen touch", listOf(touchResult.value))
                Section(
                    "Confirmed orientation",
                    confirmed.value.ifEmpty {
                        listOf("Not recorded — tent the phone, get it reading right, then tap '\u2713 looks right' on the cover screen.")
                    }
                )
                Section("Cover screen orientation (live)", orientationDetail.value)

                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startDualScreen() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Test 1 — light BOTH screens")
                    }
                    Button(onClick = { transferToCover() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Test 2 — move to cover screen only")
                    }
                    Button(onClick = { cycleCoverRotation() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Rotate cover screen (now ${coverRotation.value.toInt()}\u00B0)")
                    }
                    Button(
                        onClick = {
                            autoRotate.value = !autoRotate.value
                            lastAutoQuadrant = -1f
                            refreshOrientationDetail()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Auto-rotate from gravity: " + if (autoRotate.value) "ON" else "OFF")
                    }
                    Button(onClick = { stopSession() }, modifier = Modifier.fillMaxWidth()) {
                        Text("End session")
                    }
                    Button(onClick = { copyReport() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Copy report to clipboard")
                    }
                }

                Section("Log", log.value)
            }
        }
    }

    @Composable
    private fun Section(title: String, lines: List<String>) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text(title, color = Color(0xFFF0B266), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (lines.isEmpty()) {
                Text("—", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            } else {
                lines.forEach {
                    Text(it, color = Color(0xFFDDDDDD), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
