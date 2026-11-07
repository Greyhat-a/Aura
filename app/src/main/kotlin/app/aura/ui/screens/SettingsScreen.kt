package app.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.settings.Settings
import app.aura.ui.LocalGraph
import app.aura.ui.components.*
import app.aura.ui.edit
import app.aura.ui.theme.AuraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val accents = listOf(
    0xFF00B4FF, 0xFF2EE6A6, 0xFF9B8CFF, 0xFFFF6BD6, 0xFFFFB020, 0xFFFF5C5C, 0xFF38F2FF, 0xFFB6FF3B,
).map { it.toInt() }

@Composable
fun SettingsScreen() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    fun pick(options: List<Long>, cur: Long) = options.indexOf(cur).let { if (it < 0) 0 else it }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("Settings & personalisation") }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Accent colour")
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accents.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == st.accent) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { g.edit { it.copy(accent = c) } },
                        )
                    }
                }
                Label("3D animation style")
                ChipRow(listOf("Hologram", "Wireframe", "Solid glow"), st.animStyle, { v -> g.edit { it.copy(animStyle = v) } })
                Spacer(Modifier.height(8.dp))
                Label("Graph appearance")
                ChipRow(listOf("Line", "Area", "Bars"), st.graphStyle, { v -> g.edit { it.copy(graphStyle = v) } })
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Units")
                SwitchRow("Temperature in °F", null, st.tempF, { v -> g.edit { it.copy(tempF = v) } })
                SwitchRow("Speeds in MB/s (instead of Mbps)", null, st.speedMegabytes, { v -> g.edit { it.copy(speedMegabytes = v) } })
                SwitchRow("Power in mW (instead of W)", null, st.powerMilliwatts, { v -> g.edit { it.copy(powerMilliwatts = v) } })
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Refresh intervals (while the app is open)")
                val short = listOf(500L, 1000L, 2000L, 5000L)
                Text("Battery", fontSize = 12.sp, color = AuraColors.Muted)
                ChipRow(short.map { "${it / 1000.0} s" }, pick(short, st.batteryMs), { i -> g.edit { it.copy(batteryMs = short[i]) } })
                Text("CPU / RAM / system", fontSize = 12.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 6.dp))
                ChipRow(short.map { "${it / 1000.0} s" }, pick(short, st.systemMs), { i -> g.edit { it.copy(systemMs = short[i]) } })
                Text("Network traffic", fontSize = 12.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 6.dp))
                ChipRow(short.map { "${it / 1000.0} s" }, pick(short, st.netMs), { i -> g.edit { it.copy(netMs = short[i]) } })
                val probe = listOf(5, 10, 30, 60)
                Text("Latency probe", fontSize = 12.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 6.dp))
                ChipRow(probe.map { "$it s" }, probe.indexOf(st.probeSec).coerceAtLeast(0), { i -> g.edit { it.copy(probeSec = probe[i]) } })
                val log = listOf(10, 30, 60, 300)
                Text("History logging", fontSize = 12.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 6.dp))
                ChipRow(log.map { "$it s" }, log.indexOf(st.logSec).coerceAtLeast(0), { i -> g.edit { it.copy(logSec = log[i]) } })
                val bg = listOf(10_000L, 30_000L, 60_000L)
                Text("Background sampling (screen off, service on)", fontSize = 12.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 6.dp))
                ChipRow(bg.map { "${it / 1000} s" }, pick(bg, st.bgIntervalMs), { i -> g.edit { it.copy(bgIntervalMs = bg[i]) } })
                Text("Faster intervals give smoother graphs but use more battery.", fontSize = 10.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Facts about your hardware (used only if you enter them)")
                var rated by remember { mutableStateOf(if (st.ratedMah > 0) st.ratedMah.toString() else "") }
                OutlinedTextField(
                    rated, { rated = it.filter(Char::isDigit).take(5); g.edit { s -> s.copy(ratedMah = rated.toIntOrNull() ?: 0) } },
                    label = { Text("Rated battery capacity (mAh) — from your spec sheet") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                var adapter by remember { mutableStateOf(if (st.adapterW > 0f) st.adapterW.toString() else "") }
                OutlinedTextField(
                    adapter, { adapter = it.filter { c -> c.isDigit() || c == '.' }.take(5); g.edit { s -> s.copy(adapterW = adapter.toFloatOrNull() ?: 0f) } },
                    label = { Text("Charger rating (W) — printed on the adapter") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                var host by remember { mutableStateOf(st.probeHost) }
                OutlinedTextField(
                    host, { host = it.trim(); g.edit { s -> s.copy(probeHost = host) } },
                    label = { Text("Latency probe host (TCP 443)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text("AURA never assumes these: the Galaxy Tab A11's published figures are only used if you type them in.", fontSize = 10.sp, color = AuraColors.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Data & privacy")
                Text(
                    "All measurements stay on this device in a local database (kept 90 days). AURA's only network use is the latency probe, " +
                        "DNS timing and the speed tests you run. No accounts, no analytics.",
                    fontSize = 12.sp, color = AuraColors.Muted, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (!confirmClear) GhostButton("Delete all stored history…", { confirmClear = true })
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuraButton("Yes, delete", {
                        confirmClear = false
                        g.scope.launch { withContext(Dispatchers.IO) { g.db.clearAllTables() } }
                    }, danger = true)
                    GhostButton("Cancel", { confirmClear = false })
                }
            }
        }
        item { Text("AURA 2.0 · measurements are real or marked 'Not available'.", fontSize = 10.sp, color = AuraColors.Muted) }
    }
}
