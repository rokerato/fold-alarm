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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * The alarm, and the button that starts standby.
 *
 * Laid out in One UI's idiom and set in the phone's own font: near-black ground,
 * generously rounded cards, capsule controls, one clear primary action. On the
 * inner screen it is two columns -- the alarm and what to do next on the left,
 * everything that tunes it on the right -- and one column where only one fits.
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: AlarmPrefs

    private val time = mutableStateOf("")
    private val enabled = mutableStateOf(false)
    private val days = mutableStateOf(AlarmPrefs.EVERY_DAY)
    private val snooze = mutableStateOf(9)
    private val brightness = mutableStateOf(1f)
    private val warmth = mutableStateOf(0.75f)
    private val clockWeight = mutableStateOf(0)
    private val nightTint = mutableStateOf(true)
    private val nightBlank = mutableStateOf(false)
    private val nightBlankMinutes = mutableStateOf(10)
    private val sunriseMode = mutableStateOf(0)
    private val sunriseMinutes = mutableStateOf(20)
    private val glowSwapped = mutableStateOf(false)
    private val showDiagnostics = mutableStateOf(false)
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
        time.value = TimeText.clock(this, prefs.hour, prefs.minute)
        enabled.value = prefs.enabled
        days.value = prefs.days
        snooze.value = prefs.snoozeMinutes
        brightness.value = prefs.lampBrightness
        warmth.value = prefs.lampWarmth
        clockWeight.value = prefs.clockWeight
        nightTint.value = prefs.nightTint
        nightBlank.value = prefs.nightBlank
        nightBlankMinutes.value = prefs.nightBlankMinutes
        sunriseMode.value = prefs.sunriseMode
        sunriseMinutes.value = prefs.sunriseMinutes
        glowSwapped.value = prefs.glowSwapped
        showDiagnostics.value = prefs.showDiagnostics
        needsExactAlarm.value = !AlarmScheduler.canScheduleExact(this)
        needsNotifications.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        val now = System.currentTimeMillis()
        nextRing.value = when {
            !prefs.enabled -> "Off"
            prefs.nextTrigger > now -> TimeText.untilRing(this, now, prefs.nextTrigger)
            else -> "Not scheduled"
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

    private fun toggleDay(bit: Int) {
        prefs.days = prefs.days xor bit
        if (prefs.enabled) AlarmScheduler.sync(this)
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
        LaunchedEffect(Unit) {
            // "Rings in 7 h 12 min" should not go stale while the screen is open.
            while (true) {
                delay(30_000L)
                refresh()
            }
        }
        Surface(color = GROUND, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 600.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 40.dp, end = 40.dp, top = 56.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Column(
                            Modifier
                                .weight(0.8f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) { Hero() }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(36.dp))
                            Tuning()
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 28.dp)
                    ) {
                        Hero()
                        Spacer(Modifier.height(32.dp))
                        Tuning()
                    }
                }
            }
        }
    }

    /** The alarm itself, the days it repeats, and the one thing to do next. */
    @Composable
    private fun Hero() {
        Text("Fold Alarm", color = MUTED, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(56.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                time.value,
                color = INK,
                fontSize = 96.sp,
                fontWeight = FontWeight.ExtraLight,
                modifier = Modifier.clickable { pickTime() }
            )
            Switch(
                checked = enabled.value,
                onCheckedChange = { setEnabled(it) },
                colors = switchColours(),
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
        Text(
            nextRing.value,
            color = if (enabled.value) AMBER else MUTED,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(22.dp))
        Days()
        if (days.value == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No days chosen: rings once, then switches off.",
                color = MUTED,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (needsExactAlarm.value || needsNotifications.value) {
            Spacer(Modifier.height(20.dp))
            Permissions()
        }

        Spacer(Modifier.height(40.dp))
        Capsule("Start standby", filled = true) { startStandby() }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tent the phone, cover screen facing you.",
            color = MUTED,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    /** Sunday first. Tapping a day adds or removes it. */
    @Composable
    private fun Days() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DAY_LETTERS.forEachIndexed { index, letter ->
                val bit = 1 shl index
                val on = days.value and bit != 0
                Surface(
                    color = if (on) CHIP else Color.Transparent,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable { toggleDay(bit) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            letter,
                            color = if (on) AMBER else MUTED,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Permissions() {
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

    /** Everything that tunes the night and the morning. */
    @Composable
    private fun Tuning() {
        Section("At night", first = true)
        Card {
            Toggle(
                "Red clock in the dark",
                "Follows the room's light. Red spares the blue subpixels, which age fastest.",
                nightTint.value
            ) {
                nightTint.value = it
                prefs.nightTint = it
            }
            Rule()
            Label("Clock")
            Segmented(listOf("Thin", "Light", "Regular"), clockWeight.value) {
                clockWeight.value = it
                prefs.clockWeight = it
            }
            Rule()
            Toggle(
                "Blank the clock at night",
                "A tap wakes it. Off by default: a bedside clock you cannot read has failed.",
                nightBlank.value
            ) {
                nightBlank.value = it
                prefs.nightBlank = it
            }
            if (nightBlank.value) {
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    "Blank after",
                    "${nightBlankMinutes.value} min",
                    nightBlankMinutes.value.toFloat(),
                    1f..60f
                ) {
                    nightBlankMinutes.value = it.toInt()
                    prefs.nightBlankMinutes = it.toInt()
                }
            }
        }

        Section("Waking up")
        Card {
            Label("Light")
            Segmented(listOf("Sunrise", "At alarm", "Off"), sunriseMode.value) {
                sunriseMode.value = it
                prefs.sunriseMode = it
            }
            if (sunriseMode.value == 0) {
                Rule()
                SliderRow(
                    "Sunrise starts",
                    "${sunriseMinutes.value} min before",
                    sunriseMinutes.value.toFloat(),
                    5f..45f
                ) {
                    sunriseMinutes.value = it.toInt()
                    prefs.sunriseMinutes = it.toInt()
                }
            }
            if (sunriseMode.value != 2) {
                Rule()
                SliderRow(
                    "Brightness",
                    "${(brightness.value * 100).toInt()}%",
                    brightness.value,
                    0.15f..1f
                ) {
                    brightness.value = it
                    prefs.lampBrightness = it
                }
                Spacer(Modifier.height(8.dp))
                SliderRow("Warmth", warmthName(warmth.value), warmth.value, 0f..1f) {
                    warmth.value = it
                    prefs.lampWarmth = it
                }
            }
            Rule()
            SliderRow("Snooze", "${snooze.value} min", snooze.value.toFloat(), 1f..30f) {
                snooze.value = it.toInt()
                prefs.snoozeMinutes = it.toInt()
            }
        }

        Section("If something looks wrong")
        Card {
            Toggle(
                "Swap the glowing half",
                "Only the half beside the cover screen should light, so its light reaches the room off the table. Swap it if the other half lights.",
                glowSwapped.value
            ) {
                glowSwapped.value = it
                prefs.glowSwapped = it
            }
            Rule()
            Toggle(
                "Show diagnostics on the cover",
                "The current mode and light reading, under the clock.",
                showDiagnostics.value
            ) {
                showDiagnostics.value = it
                prefs.showDiagnostics = it
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            Link("Ring now") { startStandby(ringing = true) }
            Link("Diagnostics") {
                startActivity(Intent(this@MainActivity, ProbeActivity::class.java))
            }
        }
    }

    private fun warmthName(value: Float): String = when {
        value < 0.25f -> "Daylight"
        value < 0.5f -> "Soft white"
        value < 0.8f -> "Warm"
        else -> "Candle"
    }

    // ---- building blocks ----------------------------------------------------

    @Composable
    private fun Card(tint: Color = CARD, content: @Composable () -> Unit) {
        Surface(
            color = tint,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { content() }
        }
    }

    @Composable
    private fun Section(title: String, first: Boolean = false) {
        if (!first) Spacer(Modifier.height(22.dp))
        Text(
            title,
            color = MUTED,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
        )
    }

    /** A hairline between rows of one card, as One UI lists do. */
    @Composable
    private fun Rule() {
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RULE)
        )
        Spacer(Modifier.height(14.dp))
    }

    @Composable
    private fun Label(text: String) {
        Text(text, color = INK, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
    }

    @Composable
    private fun Link(label: String, onClick: () -> Unit) {
        Text(
            label,
            color = MUTED,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        )
    }

    @Composable
    private fun Toggle(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(title, color = INK, fontSize = 16.sp)
                Spacer(Modifier.height(3.dp))
                Text(note, color = MUTED, fontSize = 13.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange, colors = switchColours())
        }
    }

    @Composable
    private fun switchColours() = SwitchDefaults.colors(
        checkedTrackColor = AMBER,
        checkedThumbColor = Color(0xFF1A1200),
        checkedBorderColor = AMBER
    )

    @Composable
    private fun SliderRow(
        label: String,
        reading: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = INK, fontSize = 16.sp)
            Text(reading, color = MUTED, fontSize = 15.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = INK,
                activeTrackColor = AMBER,
                inactiveTrackColor = PILL
            )
        )
    }

    /**
     * A capsule track holding its choices, the chosen one lifted out in white --
     * One UI's segmented control, used where a dropdown would hide things.
     */
    @Composable
    private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PILL, RoundedCornerShape(50))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Surface(
                    color = if (active) INK else Color.Transparent,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                ) {
                    Text(
                        label,
                        color = if (active) GROUND else INK,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
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
                .height(if (filled) 60.dp else 52.dp)
        ) {
            Text(label, fontSize = if (filled) 18.sp else 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    private companion object {
        val GROUND = Color(0xFF0B0B0C)
        val CARD = Color(0xFF171719)
        val PILL = Color(0xFF262629)
        val WARN = Color(0xFF2E2413)
        val CHIP = Color(0xFF3A2E1C)
        val RULE = Color(0xFF242427)
        val INK = Color(0xFFF2F2F2)
        val MUTED = Color(0xFF8E8E93)
        val AMBER = Color(0xFFF0B266)

        /** Sunday first; index i is bit i of [AlarmPrefs.days]. */
        val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
    }
}
