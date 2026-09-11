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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

/** Setting the alarm, and the two permissions Android requires before it can ring. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: AlarmPrefs

    private val time = mutableStateOf("")
    private val enabled = mutableStateOf(false)
    private val snooze = mutableStateOf(9)
    private val brightness = mutableStateOf(1f)
    private val warmth = mutableStateOf(0.75f)
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
            MaterialTheme(colorScheme = darkColorScheme()) { SettingsScreen() }
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
        needsExactAlarm.value = !AlarmScheduler.canScheduleExact(this)
        needsNotifications.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        nextRing.value = if (prefs.enabled && prefs.nextTrigger > 0L) {
            SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(prefs.nextTrigger))
        } else {
            "off"
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

    @Composable
    private fun SettingsScreen() {
        Surface(color = Color(0xFF0E0E0E), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("Fold Alarm", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Tent the phone: the clock goes on the cover screen, the inner screen becomes a lamp.",
                    color = Color(0xFF999999),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        time.value,
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Light
                    )
                    Switch(checked = enabled.value, onCheckedChange = { setEnabled(it) })
                }
                Text("next ring: ${nextRing.value}", color = Color(0xFFF0B266), fontSize = 13.sp)

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickTime() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change time")
                }

                if (needsExactAlarm.value || needsNotifications.value) {
                    Spacer(Modifier.height(20.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A12))) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Android needs permission before this can wake you",
                                color = Color(0xFFF0B266),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            if (needsExactAlarm.value) {
                                Text(
                                    "Exact alarms are off, so the alarm may fire late or not at all.",
                                    color = Color(0xFFDDDDDD),
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = { openExactAlarmSettings() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Allow exact alarms") }
                                Spacer(Modifier.height(8.dp))
                            }
                            if (needsNotifications.value) {
                                Text(
                                    "Notifications are off. The ringing screen is launched by one.",
                                    color = Color(0xFFDDDDDD),
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Allow notifications") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text("Snooze: ${snooze.value} min", color = Color.White, fontSize = 15.sp)
                Slider(
                    value = snooze.value.toFloat(),
                    onValueChange = {
                        snooze.value = it.toInt()
                        prefs.snoozeMinutes = it.toInt()
                    },
                    valueRange = 1f..30f
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Lamp brightness: ${(brightness.value * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Slider(
                    value = brightness.value,
                    onValueChange = {
                        brightness.value = it
                        prefs.lampBrightness = it
                    },
                    valueRange = 0.15f..1f
                )

                Text(
                    "Lamp warmth: ${(warmth.value * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Slider(
                    value = warmth.value,
                    onValueChange = {
                        warmth.value = it
                        prefs.lampWarmth = it
                    },
                    valueRange = 0f..1f
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { startActivity(Intent(this@MainActivity, AlarmActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ring now (preview)") }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { startActivity(Intent(this@MainActivity, ProbeActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Diagnostics") }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Debug-signed build for sideloading.",
                    color = Color(0xFF666666),
                    fontSize = 12.sp
                )
            }
        }
    }
}
