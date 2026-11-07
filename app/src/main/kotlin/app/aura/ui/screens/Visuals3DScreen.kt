package app.aura.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.gl.*
import app.aura.ui.LocalGraph
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import kotlinx.coroutines.delay
import java.util.Locale

private val sceneNames = listOf("Battery", "Charging", "Network", "CPU", "RAM")

@Composable
fun Visuals3DScreen() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    var idx by rememberSaveable { mutableIntStateOf(0) }
    val renderer = remember { AuraRenderer() }
    val scenes = remember { listOf<GlScene>(BatteryScene(), ChargingScene(), NetworkScene(), CpuScene(), RamScene()) }

    renderer.scene = scenes[idx]
    renderer.style = st.animStyle
    val accent = Color(st.accent)
    val rgb = Rgb(accent.red, accent.green, accent.blue)
    var live by remember { mutableStateOf(SceneData()) }

    // Feed REAL readings to the renderer ~10x per second. Anything unavailable stays null (scenes show it as dim/idle).
    LaunchedEffect(rgb) {
        var tick = 0
        while (true) {
            val b = g.battery.state.value
            val s = g.system.state.value
            val n = g.network.state.value
            val p = g.probe.state.value
            val core = s?.coreLoad?.orNull()?.map { it?.coerceIn(0f, 1f) }
            val heat = s?.thermalHeadroom?.orNull()?.coerceIn(0f, 1f)
                ?: s?.takeIf { it.thermalStatus > 0 }?.let { (it.thermalStatus / 6f).coerceIn(0f, 1f) }
            val sd = SceneData(
                batteryPct = b?.levelPct?.toFloat(), charging = b?.charging == true, tempC = b?.tempC,
                powerW = b?.powerMw?.orNull()?.div(1000f), voltageMv = b?.voltageMv?.takeIf { it > 0 }?.toFloat(),
                currentMa = b?.currentMa?.orNull(),
                coreCount = g.system.cpuStatic.cores, coreActivity = core,
                cpuActivity = s?.cpuLoad?.orNull()?.div(100f), cpuHeat = heat,
                ramUsedFrac = s?.ramUsedPct?.div(100f), lowMemory = s?.lowMemory == true,
                downMbps = n.downMbps.orNull(), upMbps = n.upMbps.orNull(), latencyMs = p?.latencyMs,
                connected = n.conn.connected && n.conn.validated, accent = rgb,
            )
            renderer.data = sd
            if (tick++ % 5 == 0) live = sd      // text readout refreshes at 2 Hz
            delay(100)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("3D Lab", "Drag to rotate · pinch to zoom · double-tap toggles auto-rotate. Every visual is driven by live readings.")
        ChipRow(sceneNames, idx, { idx = it })
        Spacer(Modifier.height(8.dp))
        GlView(renderer, Modifier.weight(1f).fillMaxWidth().heightIn(min = 240.dp))
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            val d = live
            when (idx) {
                0 -> Info(
                    "Liquid level = battery % (${d.batteryPct?.toInt() ?: NA}). Colour = battery temperature (${d.tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: NA}), tinted green while charging. Particles flow up when charging, faster with more watts.",
                )
                1 -> Info(
                    "Ring speed & brightness = battery-side power (${d.powerW?.let { String.format(Locale.US, "%.1f W", it) } ?: NA}). Helix radius = voltage (${d.voltageMv?.toInt()?.let { "$it mV" } ?: NA}). Amber particle count/speed = current (${d.currentMa?.toInt()?.let { "$it mA" } ?: NA}). Idle when not charging.",
                )
                2 -> Info(
                    "Cyan packets in = live download (${d.downMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: NA}); magenta out = upload (${d.upMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: NA}). Density follows measured traffic; flight time follows measured latency (${d.latencyMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: NA}).",
                )
                3 -> Info(
                    if (d.coreActivity == null && d.cpuActivity == null)
                        "Not available on this device: Android blocks per-core utilisation and clock readings for apps here, so the cores stay dim instead of showing invented activity."
                    else "Core height/glow = per-core activity (utilisation if readable, otherwise cpufreq clock ratio). Chip colour = thermal headroom: blue cool → red throttling.",
                )
                else -> Info(
                    "Each block = 1/${20 * 12} of total RAM. Lit blocks = used memory (${d.ramUsedFrac?.let { String.format(Locale.US, "%.0f %%", it * 100) } ?: NA}). Turns amber > 70 %, red > 88 % or when Android flags low memory. Aggregate view, not physical page layout.",
                )
            }
        }
    }
}

@Composable
private fun Info(text: String) {
    Text(text, fontSize = 11.sp, color = AuraColors.Muted, lineHeight = 15.sp)
}
