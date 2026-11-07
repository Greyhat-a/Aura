package app.aura.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.monitor.*
import app.aura.settings.Settings
import app.aura.ui.*
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent

private class DashData(
    val st: Settings, val bat: BatterySnapshot?, val sys: SystemSnapshot?, val net: NetworkSnapshot, val probe: ProbeResult?,
    val bh: List<BatterySnapshot>, val sh: List<SystemSnapshot>, val nh: List<NetworkSnapshot>,
)

private fun target(id: String): Screen = when (id) {
    "BATTERY", "POWER", "TEMP", "G_POWER", "G_TEMP", "G_VOLT" -> Screen.CHARGE
    "DOWNUP", "CONN", "G_NET" -> Screen.NET
    else -> Screen.HW
}

@Composable
fun DashboardScreen(go: (Screen) -> Unit) {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val bat by g.battery.state.collectAsStateWithLifecycle()
    val sys by g.system.state.collectAsStateWithLifecycle()
    val net by g.network.state.collectAsStateWithLifecycle()
    val probe by g.probe.state.collectAsStateWithLifecycle()
    val bh by g.hub.batteryHistory.collectAsStateWithLifecycle()
    val sh by g.hub.systemHistory.collectAsStateWithLifecycle()
    val nh by g.hub.netHistory.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    val cols = LocalCols.current
    val d = DashData(st, bat, sys, net, probe, bh, sh, nh)
    val visible = st.dashboard.filter { it.visible }
    val hidden = st.dashboard.filter { !it.visible }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AURA", fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp, color = LocalAccent.current)
                    Text("Command centre · live device measurements", fontSize = 11.sp, color = AuraColors.Muted)
                }
                if (edit) GhostButton("Reset", { g.edit { it.copy(dashboard = DashCards.defaults) } })
                Spacer(Modifier.width(8.dp))
                if (edit) AuraButton("Done", { edit = false }) else GhostButton("Customise", { edit = true })
            }
        }
        if (edit) item(span = { GridItemSpan(maxLineSpan) }) {
            NoteBox("Customise mode: ‹ › move a card, S/M/L resizes it, × hides it. Hidden cards can be added back below.")
        }
        items(visible, key = { it.id }, span = { GridItemSpan(DashCards.effectiveSpan(it.span, cols)) }) { cfg ->
            GlassCard(Modifier.fillMaxWidth(), onClick = if (edit) null else ({ go(target(cfg.id)) })) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(DashCards.titles[cfg.id] ?: cfg.id, Modifier.weight(1f))
                    if (edit) {
                        EditBtn("‹") { g.edit { it.copy(dashboard = DashCards.move(it.dashboard, cfg.id, -1)) } }
                        EditBtn("›") { g.edit { it.copy(dashboard = DashCards.move(it.dashboard, cfg.id, +1)) } }
                        EditBtn(listOf("S", "M", "L")[cfg.span.coerceIn(1, 3) - 1]) { g.edit { it.copy(dashboard = DashCards.cycleSpan(it.dashboard, cfg.id)) } }
                        EditBtn("×") { g.edit { it.copy(dashboard = DashCards.setVisible(it.dashboard, cfg.id, false)) } }
                    }
                }
                CardBody(cfg.id, d)
            }
        }
        if (edit) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionHeader("Add monitoring cards")
                    if (hidden.isEmpty()) Text("All cards are on the dashboard.", fontSize = 12.sp, color = AuraColors.Muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hidden.forEach { c ->
                            GhostButton("+ ${DashCards.titles[c.id] ?: c.id}", { g.edit { it.copy(dashboard = DashCards.setVisible(it.dashboard, c.id, true)) } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBtn(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 15.sp, color = LocalAccent.current,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

@Composable
private fun CardBody(id: String, d: DashData) {
    val st = d.st
    val b = d.bat
    val accent = LocalAccent.current
    when (id) {
        "BATTERY" -> if (b == null) Waiting() else {
            val shown by animateIntAsState(b.levelPct, tween(900), label = "pct")
            val color = when {
                b.charging -> AuraColors.Good
                b.levelPct <= 15 -> AuraColors.Bad
                b.levelPct <= 30 -> AuraColors.Warn
                else -> accent
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ring(b.levelPct / 100f, color, Modifier.size(128.dp), stroke = 10.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$shown", fontSize = 40.sp, fontWeight = FontWeight.Light)
                        Text("%", fontSize = 16.sp, color = AuraColors.Muted, modifier = Modifier.padding(bottom = 7.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(b.statusText, color)
                    Text(b.sourceText, fontSize = 12.sp, color = AuraColors.Muted)
                    val eta = if (b.charging || b.full) b.etaFull else b.etaEmpty
                    Text(
                        (if (b.charging || b.full) "Full in " else "Left ") + eta.show { Fmt.duration(it) },
                        fontSize = 14.sp,
                    )
                    Text("Health: ${b.healthText}", fontSize = 12.sp, color = AuraColors.Muted)
                }
            }
        }
        "POWER" -> if (b == null) Waiting() else RMetric(
            if (b.charging) "Into battery" else "Battery draw", b.powerMw, big = true,
            sub = if (b.charging) "battery-side, not wall adapter" else "negative = discharging",
        ) { Fmt.power(it, st.powerMilliwatts) }
        "DOWNUP" -> Column {
            RMetric("Download", d.net.downMbps, big = true, sub = "live traffic, not a test") { Fmt.speed(it, st.speedMegabytes) }
            RMetric("Upload", d.net.upMbps) { Fmt.speed(it, st.speedMegabytes) }
        }
        "CPU" -> {
            val s = d.sys
            if (s == null) Waiting() else RMetric("Load", s.cpuLoad, big = true, sub = s.cpuLoadKind ?: "no readable counters") { Fmt.pct(it) }
        }
        "RAM" -> d.sys?.let { s ->
            Metric("Used", Fmt.pct(s.ramUsedPct), big = true, sub = "${Fmt.bytes(s.ramUsed)} of ${Fmt.bytes(s.ramTotal)}", info = "ActivityManager.MemoryInfo (total − available)")
        } ?: Waiting()
        "TEMP" -> if (b == null) Waiting() else Metric(
            "Battery", Fmt.temp(b.tempC, st.tempF), big = true,
            valueColor = if (b.tempC >= st.tempAlertC) AuraColors.Bad else if (b.tempC >= st.tempAlertC - 4) AuraColors.Warn else AuraColors.Text,
            sub = b.healthText, info = "BatteryManager EXTRA_TEMPERATURE (battery thermistor)",
        )
        "CONN" -> {
            val c = d.net.conn
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.typeLabel, fontSize = 15.sp, color = if (c.connected) AuraColors.Text else AuraColors.Bad, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.connected) Pill(if (c.validated) "Online" else "No internet", if (c.validated) AuraColors.Good else AuraColors.Warn)
                    else Pill("Offline", AuraColors.Bad)
                    if (c.metered) Pill("Metered", AuraColors.Warn)
                }
                Text("Ping " + (d.probe?.latencyMs?.let { Fmt.ms(it) } ?: "–") + " · jitter " + (d.probe?.jitterMs?.let { Fmt.ms(it) } ?: "–"), fontSize = 11.sp, color = AuraColors.Muted)
            }
        }
        "UPTIME" -> Metric("Since boot", d.sys?.let { Fmt.uptime(it.uptimeMs) } ?: NA, big = true, info = "SystemClock.elapsedRealtime() (includes deep sleep)")
        "STORAGE" -> d.sys?.let { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Fmt.bytes(s.storageUsed)} used", fontSize = 16.sp)
                    Text("${Fmt.bytes(s.storageAvail)} free of ${Fmt.bytes(s.storageTotal)}", fontSize = 12.sp, color = AuraColors.Muted)
                }
                UsageBar(s.storageUsedPct / 100f, if (s.storageUsedPct >= st.storageAlertPct) AuraColors.Bad else accent)
            }
        } ?: Waiting()
        "G_POWER" -> Chart(
            listOf(Series("Power", accent, d.bh.powerPts(st.powerMilliwatts).thin(240))), style = st.graphStyle,
            unit = if (st.powerMilliwatts) "mW" else "W", height = 110.dp, decimals = if (st.powerMilliwatts) 0 else 2,
        )
        "G_TEMP" -> Chart(
            listOf(Series("Temp", AuraColors.Warn, d.bh.tempPts(st.tempF).thin(240))), style = st.graphStyle,
            unit = if (st.tempF) "°F" else "°C", height = 110.dp,
        )
        "G_VOLT" -> Chart(
            listOf(Series("Voltage", Color(0xFF9B8CFF), d.bh.voltPts().thin(240))), style = st.graphStyle,
            unit = "mV", height = 110.dp, decimals = 0,
        )
        "G_CPU" -> Chart(
            listOf(Series("CPU", AuraColors.Good, d.sh.cpuPts().thin(240)), Series("RAM", accent, d.sh.ramPts().thin(240))),
            style = st.graphStyle, unit = "%", height = 110.dp, yMin = 0f, yMax = 100f, decimals = 0,
        )
        "G_NET" -> Chart(
            listOf(
                Series("↓", accent, d.nh.downPts(st.speedMegabytes).thin(240)),
                Series("↑", Color(0xFFFF6BD6), d.nh.upPts(st.speedMegabytes).thin(240)),
            ),
            style = st.graphStyle, unit = Fmt.speedUnit(st.speedMegabytes), height = 110.dp, yMin = 0f, decimals = 2,
        )
    }
}

@Composable
private fun Waiting() {
    Text("Waiting for first sample…", fontSize = 12.sp, color = AuraColors.Muted)
}
