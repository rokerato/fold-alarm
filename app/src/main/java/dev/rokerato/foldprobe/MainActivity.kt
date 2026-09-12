package dev.rokerato.foldprobe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings, and the button that starts standby.
 *
 * Laid out in One UI's idiom — near-black ground, generously rounded cards, capsule
 * controls, one clear primary action — held to Material 3's structure underneath.
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: AlarmPrefs

    private val time = mutableStateOf("")
    private val enabled = mutableStateOf(false)
    private val snooze = mutableStateOf(9)
    private val brightness = mutableStateOf(1f)
    private val warmth = mutableStateOf(0.75f)
    private val clockWeight = mutableStateOf(0)
    private val nightTint = mutableStateOf(true)
    private val nightBlank = mutableStateOf(false)
    private val nightBlankMinutes = mutableStateOf(10)
    private val sunriseMode = mutableStateOf(0)
    private val sunriseMinutes = mutableStateOf(20)
    private val glowFirstHalf = mutableStateOf(false)
    private val nextRing = mutableStateOf("")
    private val needsExactAlarm = mutableStateOf(false)
    private val needsNotifications = mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AlarmPrefs(this)
        AlarmReceiver.ensureChannel(this)
        refresh()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AMBER, surface = CARD)) {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        time.value = "%02d:%02d".format(prefs.hour, prefs.minute)
        enabled.value = prefs.enabled
        snooze.value = prefs.snoozeMinutes
        brightness.value = prefs.lampBrightness
        warmth.value = prefs.lampWarmth
        clockWeight.value = prefs.clockWeight
        nightTint.value = prefs.nightTint
        nightBlank.value = prefs.nightBlank
        nightBlankMinutes.value = prefs.nightBlankMinutes
        sunriseMode.value = prefs.sunriseMode
        sunriseMinutes.value = prefs.sunriseMinutes
        glowFirstHalf.value = prefs.glowFirstHalf
        needsExactAlarm.value = !AlarmScheduler.canScheduleExact(this)
        needsNotifications.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        nextRing.value = if (prefs.enabled && prefs.nextTrigger > 0L) {
            SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(prefs.nextTrigger))
        } else {
            "Off"
        }
    }

    private fun pickTime() {
        android.app.TimePickerDialog(
            this,
            { _, hour, minute ->
                prefs.hour = hour
                prefs.minute = minute
                if (prefs.enabled) AlarmScheduler.sync(this)
                refresh()
            },
            prefs.hour,
            prefs.minute,
            android.text.format.DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun setEnabled(value: Boolean) {
        prefs.enabled = value
        AlarmScheduler.sync(this)
        refresh()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
    }

    private fun startStandby(ringing: Boolean = false) {
        startActivity(
            Intent(this, StandbyActivity::class.java)
                .putExtra(StandbyActivity.EXTRA_RING, ringing)
        )
    }

    // ---- screen -------------------------------------------------------------

    @Composable
    private fun SettingsScreen() {
        Surface(color = GROUND, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Text("Fold Alarm", color = INK, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tent the phone. The clock goes on the cover screen; the inner half glows.",
                    color = MUTED,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(24.dp))

                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.clickable { pickTime() }) {
                            Text(
                                time.value,
                                color = INK,
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Thin
                            )
                            Text("Next: ${nextRing.value}", color = AMBER, fontSize = 13.sp)
                        }
                        Switch(
                            checked = enabled.value,
                            onCheckedChange = { setEnabled(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = AMBER)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Capsule("Change time", filled = false) { pickTime() }
                }

                Spacer(Modifier.height(16.dp))
                Capsule("Start standby", filled = true) { startStandby() }

                if (needsExactAlarm.value || needsNotifications.value) {
                    Spacer(Modifier.height(16.dp))
                    Card(tint = WARN) {
                        Text(
                            "Android needs permission before this can wake you",
                            color = AMBER,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (needsExactAlarm.value) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Exact alarms are off, so the alarm may fire late or not at all.",
                                color = INK,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Capsule("Allow exact alarms", filled = true) { openExactAlarmSettings() }
                        }
                        if (needsNotifications.value) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Notifications are off. They launch the alarm when standby is not running.",
                                color = INK,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Capsule("Allow notifications", filled = true) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    }
                }

                Section("Standby")
                Card {
                    Label("Clock weight")
                    Segmented(
                        options = listOf("Thin", "Light", "Regular", "Medium"),
                        selected = clockWeight.value
                    ) {
                        clockWeight.value = it
                        prefs.clockWeight = it
                    }
                    Spacer(Modifier.height(16.dp))
                    Toggle(
                        "Dim and redden at night",
                        "Follows the room's light. Red spares the blue subpixels, which age fastest.",
                        nightTint.value
                    ) {
                        nightTint.value = it
                        prefs.nightTint = it
                    }
                    Spacer(Modifier.height(16.dp))
                    Toggle(
                        "Blank the clock at night",
                        "Off by default: a bedside clock you cannot read has failed. A tap wakes it.",
                        nightBlank.value
                    ) {
                        nightBlank.value = it
                        prefs.nightBlank = it
                    }
                    if (nightBlank.value) {
                        Spacer(Modifier.height(12.dp))
                        SliderRow(
                            "Blank after ${nightBlankMinutes.value} min",
                            nightBlankMinutes.value.toFloat(),
                            1f..60f
                        ) {
                            nightBlankMinutes.value = it.toInt()
                            prefs.nightBlankMinutes = it.toInt()
                        }
                    }
                }

                Section("Waking")
                Card {
                    Label("Glow")
                    Segmented(
                        options = listOf("Ramp up", "At alarm", "Off"),
                        selected = sunriseMode.value
                    ) {
                        sunriseMode.value = it
                        prefs.sunriseMode = it
                    }
                    if (sunriseMode.value == 0) {
                        Spacer(Modifier.height(12.dp))
                        SliderRow(
                            "Ramp over ${sunriseMinutes.value} min",
                            sunriseMinutes.value.toFloat(),
                            5f..45f
                        ) {
                            sunriseMinutes.value = it.toInt()
                            prefs.sunriseMinutes = it.toInt()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SliderRow(
                        "Brightness ${(brightness.value * 100).toInt()}%",
                        brightness.value,
                        0.15f..1f
                    ) {
                        brightness.value = it
                        prefs.lampBrightness = it
                    }
                    SliderRow(
                        "Warmth ${(warmth.value * 100).toInt()}%",
                        warmth.value,
                        0f..1f
                    ) {
                        warmth.value = it
                        prefs.lampWarmth = it
                    }
                    Spacer(Modifier.height(16.dp))
                    Toggle(
                        "Glow from the other half",
                        "Only the half beside the cover screen should light, so it glows off the table rather than shining at you. Flip this if the wrong half lights.",
                        glowFirstHalf.value
                    ) {
                        glowFirstHalf.value = it
                        prefs.glowFirstHalf = it
                    }
                    Spacer(Modifier.height(16.dp))
                    SliderRow("Snooze ${snooze.value} min", snooze.value.toFloat(), 1f..30f) {
                        snooze.value = it.toInt()
                        prefs.snoozeMinutes = it.toInt()
                    }
                }

                Spacer(Modifier.height(24.dp))
                Capsule("Ring now (preview)", filled = false) { startStandby(ringing = true) }
                Spacer(Modifier.height(10.dp))
                Capsule("Diagnostics", filled = false) {
                    startActivity(Intent(this@MainActivity, ProbeActivity::class.java))
                }

                Spacer(Modifier.height(28.dp))
                Text("Debug-signed build for sideloading.", color = FAINT, fontSize = 12.sp)
            }
        }
    }

    // ---- building blocks ----------------------------------------------------

    @Composable
    private fun Card(tint: Color = CARD, content: @Composable () -> Unit) {
        Surface(
            color = tint,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) { content() }
        }
    }

    @Composable
    private fun Section(title: String) {
        Spacer(Modifier.height(26.dp))
        Text(
            title,
            color = MUTED,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        )
    }

    @Composable
    private fun Label(text: String) {
        Text(text, color = INK, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
    }

    @Composable
    private fun Toggle(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.padding(end = 16.dp)) {
                Text(title, color = INK, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(note, color = MUTED, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AMBER)
            )
        }
    }

    @Composable
    private fun SliderRow(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit
    ) {
        Text(label, color = INK, fontSize = 14.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = AMBER, activeTrackColor = AMBER)
        )
    }

    /** A capsule row of choices — One UI's shape, used where a dropdown would hide things. */
    @Composable
    private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Surface(
                    color = if (active) AMBER else PILL,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                ) {
                    Text(
                        label,
                        color = if (active) Color(0xFF1A1200) else INK,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    @Composable
    private fun Capsule(label: String, filled: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (filled) AMBER else PILL,
                contentColor = if (filled) Color(0xFF1A1200) else INK
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }

    private companion object {
        val GROUND = Color(0xFF0B0B0C)
        val CARD = Color(0xFF171719)
        val PILL = Color(0xFF262629)
        val WARN = Color(0xFF2E2413)
        val INK = Color(0xFFF2F2F2)
        val MUTED = Color(0xFF8E8E93)
        val FAINT = Color(0xFF5A5A5F)
        val AMBER = Color(0xFFF0B266)
    }
}
