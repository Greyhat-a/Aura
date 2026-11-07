package app.aura.ui.screens

import android.hardware.Sensor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.monitor.SensorExplorer
import app.aura.monitor.SystemSnapshot
import app.aura.ui.*
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent
import java.util.Locale

@Composable
fun HardwareScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Hardware monitor")
        ChipRow(listOf("CPU", "Memory", "Storage", "Display", "Sensors"), tab, { tab = it })
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> CpuTab(); 1 -> MemoryTab(); 2 -> StorageTab(); 3 -> DisplayTab(); else -> SensorsTab()
            }
        }
    }
}

private fun thermalName(s: Int) = when (s) {
    0 -> "None"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"; 4 -> "Critical"; 5 -> "Emergency"; 6 -> "Shutdown"; else -> "?"
}

@Composable
private fun CpuTab() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val s by g.system.state.collectAsStateWithLifecycle()
    val sh by g.hub.systemHistory.collectAsStateWithLifecycle()
    val cols = LocalCols.current
    val accent = LocalAccent.current
    val cpu = g.system.cpuStatic
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Processor")
                MetricGrid(cols) {
                    item { m -> Metric("Model", cpu.model, m, info = "Build.SOC_MODEL / /proc/cpuinfo / Build.HARDWARE") }
                    item { m -> Metric("Architecture", cpu.abis, m, info = "Build.SUPPORTED_ABIS") }
                    item { m -> Metric("Cores", "${cpu.cores}", m, info = "Runtime.availableProcessors()") }
                    item { m ->
                        val mx = cpu.maxFreqMhz.filterNotNull()
                        Metric("Max clocks", if (mx.isEmpty()) NA else mx.distinct().sortedDescending().joinToString(" / ") { "$it MHz" }, m, info = "cpufreq/cpuinfo_max_freq")
                    }
                    item { m -> Metric("Thermal state", s?.let { thermalName(it.thermalStatus) } ?: NA, m, info = "PowerManager.getCurrentThermalStatus()") }
                    item { m -> RMetric("Thermal headroom", s?.thermalHeadroom ?: Reading.Unavailable("No sample"), m, sub = "1.0 = throttling") { String.format(Locale.US, "%.2f", it) } }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Utilisation")
                val x = s
                if (x == null) EmptyHint("Waiting for first sample…") else {
                    RMetric("CPU", x.cpuLoad, big = true, sub = x.cpuLoadKind) { Fmt.pct(it) }
                    Metric("This app only", Fmt.pct(x.appCpuPct), info = "Process.getElapsedCpuTime() — always measurable, but only AURA's own share")
                }
                Chart(listOf(Series("CPU", accent, sh.cpuPts())), Modifier.padding(top = 6.dp), style = st.graphStyle, unit = "%", yMin = 0f, yMax = 100f, decimals = 0)
                NoteBox(
                    "Since Android 8, apps cannot read /proc/stat, so true whole-system CPU utilisation is usually blocked. " +
                        "When it is, AURA falls back to the cpufreq clock ratio (current ÷ max frequency), clearly labelled as a frequency index, " +
                        "and if even that is blocked it shows 'Not available' rather than a made-up number.",
                    tone = AuraColors.Warn,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Per-core frequency")
                val f = s?.coreFreqMhz
                val list = f?.orNull()
                if (f == null) EmptyHint("Waiting…")
                else if (list == null) Text("Not available on this device — ${f.sourceOrReason()}", fontSize = 12.sp, color = AuraColors.Muted)
                else list.forEachIndexed { i, mhz ->
                    val mx = cpu.maxFreqMhz.getOrNull(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("CPU$i", Modifier.width(48.dp), fontSize = 12.sp, color = AuraColors.Muted)
                        UsageBar(if (mhz != null && mx != null && mx > 0) mhz.toFloat() / mx else 0f, accent, Modifier.weight(1f))
                        Text(mhz?.let { "$it MHz" } ?: "offline", Modifier.width(80.dp).padding(start = 8.dp), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTab() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val s by g.system.state.collectAsStateWithLifecycle()
    val sh by g.hub.systemHistory.collectAsStateWithLifecycle()
    val cols = LocalCols.current
    val accent = LocalAccent.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            val x = s
            GlassCard(Modifier.fillMaxWidth()) {
                Label("RAM")
                if (x == null) EmptyHint("Waiting…") else {
                    MetricGrid(cols) {
                        item { m -> Metric("Total", Fmt.bytes(x.ramTotal), m, info = "ActivityManager.MemoryInfo.totalMem") }
                        item { m -> Metric("Available", Fmt.bytes(x.ramAvail), m, info = "MemoryInfo.availMem") }
                        item { m -> Metric("Used", Fmt.bytes(x.ramUsed), m) }
                        item { m -> Metric("Usage", Fmt.pct(x.ramUsedPct), m, big = true) }
                        item { m -> Metric("Low-memory flag", if (x.lowMemory) "Yes" else "No", m, sub = "threshold ${Fmt.bytes(x.ramThreshold)}") }
                        item { m -> Metric("Page cache", x.cached?.let { Fmt.bytes(it) } ?: NA, m, info = "/proc/meminfo Cached") }
                    }
                    UsageBar(x.ramUsedPct / 100f, accent, Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            val x = s
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Virtual memory (swap / zram)")
                if (x?.swapTotal == null) Text("Not available on this device — /proc/meminfo has no readable swap fields.", fontSize = 12.sp, color = AuraColors.Muted)
                else MetricGrid(cols) {
                    item { m -> Metric("Swap total", Fmt.bytes(x.swapTotal), m, info = "/proc/meminfo SwapTotal") }
                    item { m -> Metric("Swap used", x.swapFree?.let { Fmt.bytes(x.swapTotal - it) } ?: NA, m) }
                }
            }
        }
        item { ChartCard("RAM usage", listOf(Series("RAM", accent, sh.ramPts())), "%", st.graphStyle, yMin = 0f, yMax = 100f, decimals = 0) }
    }
}

@Composable
private fun StorageTab() {
    val g = LocalGraph.current
    val s by g.system.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val x = s
    GlassCard(Modifier.fillMaxWidth()) {
        Label("Internal storage")
        if (x == null) EmptyHint("Waiting…") else Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Ring(x.storageUsedPct / 100f, if (x.storageUsedPct > 90f) AuraColors.Bad else accent, Modifier.size(150.dp), stroke = 12.dp) {
                Text(Fmt.pct(x.storageUsedPct), fontSize = 28.sp)
            }
            Column {
                Metric("Capacity", Fmt.bytes(x.storageTotal), info = "StatFs(data directory).totalBytes — as seen by apps (excludes system partition)")
                Metric("Used", Fmt.bytes(x.storageUsed))
                Metric("Available", Fmt.bytes(x.storageAvail))
            }
        }
    }
}

@Composable
private fun DisplayTab() {
    val g = LocalGraph.current
    val s by g.system.state.collectAsStateWithLifecycle()
    val cols = LocalCols.current
    val x = s
    GlassCard(Modifier.fillMaxWidth()) {
        Label("Display")
        if (x == null) EmptyHint("Waiting…") else {
            val d = x.display
            MetricGrid(cols) {
                item { m -> Metric("Resolution", "${d.widthPx} × ${d.heightPx}", m, info = "Display.Mode physical size") }
                item { m -> Metric("Refresh rate", String.format(Locale.US, "%.0f Hz", d.refreshHz), m, sub = "now", info = "Display.getRefreshRate()") }
                item { m -> Metric("Supported rates", d.supportedHz.joinToString(" / ") { String.format(Locale.US, "%.0f", it) } + " Hz", m, info = "Display.getSupportedModes()") }
                item { m -> Metric("Density", "${d.densityDpi} dpi", m, info = "DisplayMetrics.densityDpi") }
                item { m ->
                    Metric(
                        "Brightness", d.brightness255?.let { "${it * 100 / 255} %" + if (d.autoBrightness == true) " (auto)" else "" } ?: NA, m,
                        info = "Settings.System.SCREEN_BRIGHTNESS (0–255)",
                    )
                }
                item { m -> Metric("Wide colour gamut", if (d.wideGamut) "Yes" else "No", m) }
                item { m ->
                    Metric(
                        "HDR", if (d.hdrTypes.isEmpty()) "None reported" else d.hdrTypes.joinToString { hdrName(it) }, m,
                        info = "Display.Mode.getSupportedHdrTypes() (Android 14+)",
                    )
                }
                item { m -> Metric("Panel technology", NA, m, info = "Android does not expose the panel type (LCD/OLED) to apps, so AURA does not guess it.") }
            }
        }
    }
}

private fun hdrName(t: Int) = when (t) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "type $t" }

@Composable
private fun SensorsTab() {
    val g = LocalGraph.current
    val sensors = remember { g.sensors.all() }
    var selected by remember { mutableStateOf<Sensor?>(null) }
    val accent = LocalAccent.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Key sensors")
                g.sensors.keyTypes.forEach { (type, name) ->
                    val sen = g.sensors.default(type)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontSize = 14.sp)
                        if (sen == null) Text("Not available on this device", fontSize = 12.sp, color = AuraColors.Muted)
                        else Text("Present — tap below to read", fontSize = 12.sp, color = AuraColors.Good)
                    }
                }
            }
        }
        selected?.let { sen -> item { SensorLive(sen) { selected = null } } }
        item { Label("All sensors (${sensors.size}) — tap to explore") }
        items(sensors.size) { i ->
            val sen = sensors[i]
            GlassCard(Modifier.fillMaxWidth(), onClick = { selected = sen }) {
                Text(sen.name, fontSize = 14.sp, color = if (sen == selected) accent else AuraColors.Text)
                Text(
                    "${sen.stringType.substringAfterLast('.')} · ${sen.vendor} · range ${sen.maximumRange} ${SensorExplorer.unit(sen.type)} · res ${sen.resolution} · ${sen.power} mA",
                    fontSize = 10.sp, color = AuraColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun SensorLive(sen: Sensor, close: () -> Unit) {
    val g = LocalGraph.current
    val accent = LocalAccent.current
    var values by remember(sen) { mutableStateOf<FloatArray?>(null) }
    var acc by remember(sen) { mutableIntStateOf(-1) }
    var series by remember(sen) { mutableStateOf<List<Pair<Long, Float>>>(emptyList()) }
    val streamable = g.sensors.canStream(sen)
    LaunchedEffect(sen) {
        if (streamable) g.sensors.stream(sen).collect { smp ->
            values = smp.values; acc = smp.accuracy
            val mag = kotlin.math.sqrt(smp.values.fold(0f) { a, v -> a + v * v })
            series = (series + (System.currentTimeMillis() to mag)).takeLast(150)
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("Live: ${sen.name}")
            Text("Close", Modifier.clickable { close() }, color = accent, fontSize = 12.sp)
        }
        val u = SensorExplorer.unit(sen.type)
        if (!streamable) Text("This is a one-shot / trigger sensor and cannot be streamed continuously.", fontSize = 12.sp, color = AuraColors.Muted)
        val v = values
        if (v == null && streamable) Text("Waiting for readings… (on-change sensors only report when the value changes)", fontSize = 12.sp, color = AuraColors.Muted)
        if (v != null) {
            val ax = SensorExplorer.axes(sen.type, v.size)
            MetricGrid(minOf(3, v.size.coerceAtLeast(1))) {
                v.forEachIndexed { i, x -> item { m -> Metric(ax[i], String.format(Locale.US, "%.3f %s", x, u), m) } }
            }
            Text("Accuracy: " + listOf("unreliable", "low", "medium", "high").getOrElse(acc) { "n/a" }, fontSize = 11.sp, color = AuraColors.Muted)
            Chart(listOf(Series("|v|", accent, series)), Modifier.padding(top = 4.dp), style = 0, unit = u, height = 100.dp, decimals = 2)
        }
    }
}
