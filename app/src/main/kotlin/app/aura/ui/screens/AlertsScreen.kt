package app.aura.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.alerts.Notifier
import app.aura.ui.LocalActions
import app.aura.ui.LocalGraph
import app.aura.ui.components.*
import app.aura.ui.edit
import app.aura.ui.theme.AuraColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AlertsScreen() {
    val g = LocalGraph.current
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val notif by actions.notifGranted

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("Alerts", "Rules are checked every few seconds while AURA is visible or monitoring is on, and every ~15 minutes by Android's background scheduler otherwise.") }

        if (!notif) item {
            GlassCard(Modifier.fillMaxWidth()) {
                NoteBox("Notifications are blocked, so alerts cannot be shown. Allow them to receive alerts.", tone = AuraColors.Bad)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuraButton("Allow", { actions.requestNotifications() })
                    GhostButton("App settings", { actions.openAppSettings() })
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Battery temperature", "Notify when the battery reaches the limit (15 min cooldown).", st.tempAlertOn, { v -> g.edit { it.copy(tempAlertOn = v) } })
                SliderRow("Limit", if (st.tempF) String.format(Locale.US, "%.0f °F", st.tempAlertC * 9 / 5 + 32) else "${st.tempAlertC.roundToInt()} °C", st.tempAlertC, 35f..55f, 19, st.tempAlertOn) { v -> g.edit { it.copy(tempAlertC = v.roundToInt().toFloat()) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Battery level reached", "Fires when the level crosses the target, charging or discharging.", st.levelAlertOn, { v -> g.edit { it.copy(levelAlertOn = v) } })
                SliderRow("Target", "${st.levelAlertPct} %", st.levelAlertPct.toFloat(), 5f..100f, 18, st.levelAlertOn) { v -> g.edit { it.copy(levelAlertPct = (v / 5).roundToInt() * 5) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Unusually slow charging", "Battery-side power stays below the limit while charging under 80 %.", st.slowAlertOn, { v -> g.edit { it.copy(slowAlertOn = v) } })
                SliderRow("Below", String.format(Locale.US, "%.1f W", st.slowAlertW), st.slowAlertW, 1f..15f, 27, st.slowAlertOn) { v -> g.edit { it.copy(slowAlertW = (v * 2).roundToInt() / 2f) } }
                SliderRow("For at least", "${st.slowAlertMin} min", st.slowAlertMin.toFloat(), 1f..15f, 13, st.slowAlertOn) { v -> g.edit { it.copy(slowAlertMin = v.roundToInt()) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Connection dropped", "No internet connection for more than 20 s.", st.dropAlertOn, { v -> g.edit { it.copy(dropAlertOn = v) } })
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Speed below threshold", "Checked after each real speed test (manual or scheduled).", st.speedAlertOn, { v -> g.edit { it.copy(speedAlertOn = v) } })
                SliderRow("Download below", "${st.speedAlertMbps.roundToInt()} Mbps", st.speedAlertMbps, 1f..100f, 0, st.speedAlertOn) { v -> g.edit { it.copy(speedAlertMbps = v.roundToInt().toFloat()) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow("Storage nearly full", "Internal storage used above the limit.", st.storageAlertOn, { v -> g.edit { it.copy(storageAlertOn = v) } })
                SliderRow("Used above", "${st.storageAlertPct} %", st.storageAlertPct.toFloat(), 70f..99f, 28, st.storageAlertOn) { v -> g.edit { it.copy(storageAlertPct = v.roundToInt()) } }
            }
        }

        item { SectionHeader("Background behaviour") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    "Continuous monitoring (foreground service)",
                    "Keeps sampling and alerts running with the screen off. Shows a permanent notification. Uses a little extra battery.",
                    st.bgMonitoring, { actions.setBackgroundMonitoring(it) },
                )
                SwitchRow(
                    "Periodic background check (WorkManager)",
                    "About every 15 min Android lets AURA record a sample and evaluate alerts. Timing is up to the system (Doze).",
                    st.periodicWork, { v -> g.edit { it.copy(periodicWork = v) } },
                )
                SwitchRow(
                    "Scheduled speed test (Wi-Fi only)",
                    "Runs a short 3 s download test in the periodic check, only on unmetered networks.",
                    st.periodicSpeed, { v -> g.edit { it.copy(periodicSpeed = v) } }, enabled = st.periodicWork,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    GhostButton("Battery optimisation settings", { actions.openBatterySettings() })
                }
                Text("If alerts stop while the screen is off, exclude AURA from battery optimisation, or enable continuous monitoring.", fontSize = 10.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            GhostButton("Send a test notification", { Notifier.alert(ctx, 99, "AURA test", "Notifications are working.") })
        }
    }
}
