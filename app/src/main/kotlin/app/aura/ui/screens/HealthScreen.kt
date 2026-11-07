package app.aura.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.ui.LocalCols
import app.aura.ui.LocalGraph
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

@Composable
fun HealthScreen() {
    val g = LocalGraph.current
    val dao = g.db.dao()
    val st by g.settings.state.collectAsStateWithLifecycle()
    val b by g.battery.state.collectAsStateWithLifecycle()
    val cols = LocalCols.current
    val accent = LocalAccent.current
    var range by rememberSaveable { mutableIntStateOf(0) }
    val spans = listOf(86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L)
    val bucket = listOf(600_000L, 3_600_000L, 6 * 3_600_000L)[range]
    val now = remember(range) { System.currentTimeMillis() }

    val buckets by dao.batteryBuckets(now - spans[range], now + 60_000L, bucket).collectAsStateWithLifecycle(emptyList())
    val sessions by dao.sessions(now - 30L * 86_400_000, now + 60_000L).collectAsStateWithLifecycle(emptyList())

    val extras by produceState<Triple<List<Analytics.CapPoint>, List<app.aura.data.HourOfDayDrain>, Float?>>(Triple(emptyList(), emptyList(), null), range) {
        value = withContext(Dispatchers.IO) {
            val t = System.currentTimeMillis()
            Triple(
                dao.hourlyCapacity(t - 30L * 86_400_000).map { Analytics.CapPoint(it.h, it.capMah) },
                dao.drainByHourOfDay(t - 14L * 86_400_000, TimeZone.getDefault().getOffset(t).toLong()),
                dao.maxTempSince(t - 86_400_000),
            )
        }
    }
    val degr = Analytics.degradation(extras.first, st.ratedMah)
    val last = sessions.firstOrNull()
    val warnings = Analytics.temperatureWarnings(extras.third, st.tempAlertC, b?.healthText ?: "Unknown", last?.maxTempC)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("Battery health & diagnostics", "Built only from data AURA has actually recorded on this device.") }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("What Android reports")
                MetricGrid(cols) {
                    item { m -> Metric("Health category", b?.healthText ?: NA, m, info = "EXTRA_HEALTH — coarse: Good / Overheat / Dead …") }
                    item { m -> b?.let { RMetric("State of health", it.sohPct, m) { v -> "$v %" } } ?: Metric("State of health", NA, m) }
                    item { m -> b?.let { RMetric("Cycle count", it.cycleCount, m) { v -> "$v" } } ?: Metric("Cycle count", NA, m) }
                    item { m -> Metric("Rated capacity (you)", if (st.ratedMah > 0) "${st.ratedMah} mAh" else "Not set", m, info = "Set in Settings. Android does not expose the design capacity.") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android does not provide exact remaining capacity. Where the OEM does not publish state of health or cycles, AURA shows 'Not available' and never claims an exact figure.",
                    fontSize = 11.sp, color = AuraColors.Muted,
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Estimated degradation")
                if (degr == null) {
                    Text(
                        "Not enough data yet. AURA needs about 12 hours of charge-counter readings (between 20 % and 95 % battery) spread over at least 2 days. " +
                            "Keep AURA's history running and check back.",
                        fontSize = 13.sp, color = AuraColors.Muted,
                    )
                } else {
                    MetricGrid(cols) {
                        item { m -> Metric("Est. full capacity", String.format(Locale.US, "%.0f mAh", degr.medianFullMah), m, sub = "± ${String.format(Locale.US, "%.0f", degr.spreadMah / 2)} mAh") }
                        item { m -> Metric("Of rated", degr.pctOfRated?.let { String.format(Locale.US, "%.0f %%", it) } ?: NA, m, sub = if (degr.ratedMah == null) "set rated mAh in Settings" else "vs ${degr.ratedMah} mAh") }
                        item { m -> Metric("Confidence", degr.confidence, m, sub = "${degr.hours} h over ${degr.spanDays} d") }
                    }
                    NoteBox("Rough estimate: coulomb counter ÷ rounded percentage, median of hourly values. Treat it as a trend indicator, not a diagnosis.", tone = AuraColors.Warn)
                }
            }
        }

        if (warnings.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Label("Warnings")
                warnings.forEach {
                    Text("• " + it.text, fontSize = 13.sp, color = if (it.level >= 3) AuraColors.Bad else if (it.level == 2) AuraColors.Warn else AuraColors.Text, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        item { ChipRow(listOf("Last 24 h", "7 days", "30 days"), range, { range = it }) }

        val x: (Long) -> String = ::fmtDateTime
        val temp = buckets.map { it.bucket to Fmt.tempNum(it.avgTempC, st.tempF) }
        val tmax = buckets.map { it.bucket to Fmt.tempNum(it.maxTempC, st.tempF) }
        item {
            ChartCard(
                "Battery temperature history", listOf(Series("Avg", AuraColors.Warn, temp), Series("Max", AuraColors.Bad, tmax)),
                if (st.tempF) "°F" else "°C", st.graphStyle, xFmt = x,
            )
        }
        item { ChartCard("Battery voltage history", listOf(Series("Avg", Color(0xFF9B8CFF), buckets.map { it.bucket to it.avgVoltageMv })), "mV", st.graphStyle, decimals = 0, xFmt = x) }
        item {
            ChartCard(
                "Charge / discharge trend", listOf(Series("Level", accent, buckets.map { it.bucket to it.avgLevel })), "%", st.graphStyle,
                yMin = 0f, yMax = 100f, decimals = 0, xFmt = x, note = "Rises while charging, falls while discharging.",
            )
        }

        item {
            ChartCard(
                "Charging speed comparison (avg W per session)",
                listOf(Series("Avg W", accent, sessions.reversed().mapNotNull { s -> s.avgPowerMw?.let { s.startTs to it / 1000f } })),
                "W", 2, decimals = 2, xFmt = ::fmtDateTime, note = "One bar per charging session; battery-side power.",
            )
        }

        item { GlassCard(Modifier.fillMaxWidth()) { Label("Charging sessions (30 days)"); if (sessions.isEmpty()) EmptyHint("No completed or running sessions recorded yet.") } }
        items(sessions.take(10)) { s ->
            val dur = (s.endTs ?: System.currentTimeMillis()) - s.startTs
            GlassCard(Modifier.fillMaxWidth()) {
                Text(fmtDateTime(s.startTs) + if (s.endTs == null) "  · in progress" else "", fontSize = 13.sp)
                Text(
                    "${s.startLevel}% → ${s.endLevel}% · ${Fmt.duration(dur)} · ${app.aura.monitor.BatteryMonitor.pluggedText(s.plugged)}",
                    fontSize = 12.sp, color = AuraColors.Muted,
                )
                Text(
                    "avg ${s.avgPowerMw?.let { Fmt.power(it, st.powerMilliwatts) } ?: NA} · peak ${Fmt.power(s.peakPowerMw, st.powerMilliwatts)} · max ${Fmt.temp(s.maxTempC, st.tempF)}",
                    fontSize = 12.sp,
                )
            }
        }

        item {
            val d = extras.second
            ChartCard(
                "Usage pattern — average discharge power by hour of day (14 days)",
                listOf(Series("Draw", accent, d.map { (it.hour * 3_600_000L) to kotlin.math.abs(it.avgPowerMw) / 1000f })),
                "W", 2, decimals = 2, xFmt = { "${it / 3_600_000}:00" },
                note = if (d.isEmpty()) "Needs recorded discharge samples with current readings." else "Battery-side draw while unplugged; higher bars = heavier use at that hour.",
            )
        }
    }
}
