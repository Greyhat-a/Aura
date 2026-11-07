package app.aura.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.monitor.SpeedServers
import app.aura.ui.*
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun NetworkScreen() {
    val g = LocalGraph.current
    val actions = LocalActions.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val net by g.network.state.collectAsStateWithLifecycle()
    val probe by g.probe.state.collectAsStateWithLifecycle()
    val sp by g.speed.state.collectAsStateWithLifecycle()
    val nh by g.hub.netHistory.collectAsStateWithLifecycle()
    val phone by actions.phoneGranted
    val cols = LocalCols.current
    val accent = LocalAccent.current
    val pink = Color(0xFFFF6BD6)
    val c = net.conn
    val unit = Fmt.speedUnit(st.speedMegabytes)

    val now = remember { System.currentTimeMillis() }
    val results by g.db.dao().speedResults(now - 90L * 86_400_000, Long.MAX_VALUE).collectAsStateWithLifecycle(emptyList())

    var job by remember { mutableStateOf<Job?>(null) }
    var allowMetered by remember { mutableStateOf(false) }
    val running = sp.running
    val servers = SpeedServers.all(st)
    val selected = SpeedServers.selected(st)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("Network intelligence", "Live traffic, link quality and an on-demand speed test. These are three different things and are labelled separately.") }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Connection")
                MetricGrid(cols) {
                    item { m -> Metric("Type", c.typeLabel, m, info = "ConnectivityManager + NetworkCapabilities (+ TelephonyManager for 4G/5G)") }
                    item { m -> Metric("Internet", if (!c.connected) "Offline" else if (c.validated) "Validated" else "Connected, no internet", m, info = "NET_CAPABILITY_VALIDATED") }
                    item { m -> Metric("Metered", if (c.metered) "Yes" else "No", m) }
                    item { m ->
                        Metric(
                            "Signal", c.signalDbm?.let { "$it dBm" } ?: NA, m,
                            info = c.signalDbm?.let { "NetworkCapabilities.getSignalStrength()" }
                                ?: "Android did not provide a signal value for this connection.",
                        )
                    }
                    item { m -> Metric("Negotiated Wi-Fi link", c.linkMbps?.let { "$it Mbps" } ?: NA, m, sub = "radio rate, not internet speed") }
                    item { m -> Metric("OS capacity estimate", c.estDownKbps?.let { "↓ ${it / 1000} / ↑ ${(c.estUpKbps ?: 0) / 1000} Mbps" } ?: NA, m, sub = "Android's guess, not measured") }
                    item { m -> Metric("IPv4", c.ipv4 ?: NA, m, info = "LinkProperties (local address; public IP is not queried, to avoid contacting third parties)") }
                    item { m -> Metric("IPv6", c.ipv6 ?: NA, m) }
                }
                if (c.transport == "Cellular" && !phone) {
                    Spacer(Modifier.height(6.dp))
                    GhostButton("Allow Phone permission to show 4G / 5G", { actions.requestPhoneState() })
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Live traffic (not a speed test)")
                MetricGrid(2) {
                    item { m -> RMetric("Download now", net.downMbps, m, big = true) { Fmt.speed(it, st.speedMegabytes) } }
                    item { m -> RMetric("Upload now", net.upMbps, m, big = true) { Fmt.speed(it, st.speedMegabytes) } }
                }
                Chart(
                    listOf(Series("↓", accent, nh.downPts(st.speedMegabytes)), Series("↑", pink, nh.upPts(st.speedMegabytes))),
                    Modifier.padding(top = 6.dp), style = st.graphStyle, unit = unit, yMin = 0f, decimals = 2,
                )
                Text(
                    "Device-wide bytes through all apps (TrafficStats), differenced every ${st.netMs / 1000.0} s. It shows what is being used, " +
                        "not what your connection can deliver. Idle ≈ 0.",
                    fontSize = 10.sp, color = AuraColors.Muted,
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Link quality")
                val p = probe
                MetricGrid(cols) {
                    item { m -> Metric("Latency", Fmt.ms(p?.latencyMs), m, sub = p?.host?.let { "TCP connect to $it:443" }) }
                    item { m -> Metric("Jitter", Fmt.ms(p?.jitterMs), m, info = "Mean absolute difference between consecutive round-trip times") }
                    item { m -> Metric("Probe loss", p?.let { Fmt.pct(it.lossPct) } ?: NA, m, info = "Share of TCP connects that timed out. ICMP ping is not available to apps, so this is connect loss.") }
                    item { m -> Metric("DNS response", Fmt.ms(p?.dnsMs), m, info = "DnsResolver query with cache bypass (FLAG_NO_CACHE_LOOKUP)") }
                    item { m -> Metric("Stability", Fmt.pct(p?.stabilityPct), m, info = "Share of the last 60 probes that answered within 3× the median latency") }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Speed test")
                Text("Server", fontSize = 12.sp, color = AuraColors.Muted)
                ChipRow(servers.map { it.name }, servers.indexOfFirst { it.id == selected.id }.coerceAtLeast(0), { i -> g.edit { s -> s.copy(serverId = servers[i].id) } })
                if (selected.upUrl == null) Text("This server has no upload endpoint: upload will be skipped, not estimated.", fontSize = 11.sp, color = AuraColors.Warn)
                Spacer(Modifier.height(8.dp))
                if (c.metered) SwitchRow("Allow on metered connection", "A test can use roughly 20–200 MB depending on link speed.", allowMetered, { allowMetered = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!running) AuraButton(
                        "Start test",
                        { job = g.scope.launch { g.runSpeedTest() } },
                        enabled = c.connected && (!c.metered || allowMetered),
                    ) else AuraButton("Stop", { job?.cancel() }, danger = true)
                }
                Spacer(Modifier.height(8.dp))
                Text(sp.phase + if (running) String.format(Locale.US, " · %.1f %s", Fmt.speedNum(sp.liveMbps, st.speedMegabytes), unit) else "", fontSize = 14.sp)
                sp.error?.let { Text("Failed: $it", fontSize = 12.sp, color = AuraColors.Bad) }
                if (running || sp.series.isNotEmpty()) {
                    val t0 = remember(running) { System.currentTimeMillis() }
                    Chart(
                        listOf(Series("Live", accent, sp.series.mapIndexed { i, v -> (t0 + i * 250L) to Fmt.speedNum(v, st.speedMegabytes) })),
                        Modifier.padding(top = 6.dp), style = 1, unit = unit, height = 90.dp, yMin = 0f, decimals = 1,
                    )
                }
                MetricGrid(cols) {
                    item { m -> Metric("Download", sp.downMbps?.let { Fmt.speed(it, st.speedMegabytes) } ?: "–", m, big = true) }
                    item { m -> Metric("Upload", sp.upMbps?.let { Fmt.speed(it, st.speedMegabytes) } ?: "–", m, big = true) }
                    item { m -> Metric("Ping", sp.pingMs?.let { Fmt.ms(it) } ?: "–", m) }
                    item { m -> Metric("Jitter", sp.jitterMs?.let { Fmt.ms(it) } ?: "–", m) }
                    item { m -> Metric("Loss", sp.lossPct?.let { Fmt.pct(it) } ?: "–", m) }
                }
                Text(
                    "Method: 4 parallel HTTPS download streams then 3 upload streams for ~8 s each; the first second is discarded as TCP ramp-up. " +
                        "Result is the real throughput to the selected server, which can be below your plan's rated speed.",
                    fontSize = 10.sp, color = AuraColors.Muted,
                )
                CustomServer()
            }
        }

        item {
            ChartCard(
                "Speed history & comparison", listOf(
                    Series("↓", accent, results.mapNotNull { r -> r.downMbps?.let { r.ts to Fmt.speedNum(it, st.speedMegabytes) } }),
                    Series("↑", pink, results.mapNotNull { r -> r.upMbps?.let { r.ts to Fmt.speedNum(it, st.speedMegabytes) } }),
                ),
                unit, if (st.graphStyle == 2) 2 else 0, yMin = 0f, decimals = 1, xFmt = ::fmtDateTime,
                note = "${results.size} stored test(s), last 90 days.",
            )
        }
        items(results.takeLast(8).reversed()) { r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(fmtDateTime(r.ts) + " · " + r.server, fontSize = 12.sp, color = AuraColors.Muted)
                Text(
                    "↓ ${r.downMbps?.let { Fmt.speed(it, st.speedMegabytes) } ?: NA}   ↑ ${r.upMbps?.let { Fmt.speed(it, st.speedMegabytes) } ?: NA}   ping ${Fmt.ms(r.pingMs)}   jitter ${Fmt.ms(r.jitterMs)}",
                    fontSize = 13.sp,
                )
                Text(r.network, fontSize = 10.sp, color = AuraColors.Muted)
            }
        }
    }
}

@Composable
private fun CustomServer() {
    val g = LocalGraph.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var down by remember(st.customDown) { mutableStateOf(st.customDown) }
    var up by remember(st.customUp) { mutableStateOf(st.customUp) }
    Spacer(Modifier.height(6.dp))
    GhostButton(if (open) "Hide custom server" else "Custom server…", { open = !open })
    if (open) {
        Text("Any https:// file for download (e.g. your own nginx or LibreSpeed). Upload URL must accept POST bodies.", fontSize = 11.sp, color = AuraColors.Muted)
        OutlinedTextField(down, { down = it }, label = { Text("Download URL (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(up, { up = it }, label = { Text("Upload URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AuraButton("Save", { g.edit { it.copy(customDown = down.trim(), customUp = up.trim()) } }, Modifier.padding(top = 6.dp))
    }
}
