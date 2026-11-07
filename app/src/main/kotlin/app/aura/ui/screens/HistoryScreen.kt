package app.aura.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aura.core.*
import app.aura.data.CsvExport
import app.aura.ui.LocalActions
import app.aura.ui.LocalGraph
import app.aura.ui.components.*
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rangeLabels = listOf("1 h", "6 h", "24 h", "7 d", "30 d", "90 d")
private val rangeMs = listOf(3_600_000L, 6 * 3_600_000L, 86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L, 90 * 86_400_000L)
private const val DAY = 86_400_000L

@Composable
fun HistoryScreen() {
    val g = LocalGraph.current
    val dao = g.db.dao()
    val actions = LocalActions.current
    val st by g.settings.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    var ds by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableIntStateOf(0) }       // 0 raw, 1 hourly, 2 daily, 3 weekly
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(2) }
    var offsetDays by rememberSaveable { mutableIntStateOf(0) }
    val dataset = CsvExport.Dataset.values()[ds]

    val end = remember(offsetDays, range, ds) { System.currentTimeMillis() - offsetDays * DAY }
    val start = end - rangeMs[range]
    val bucketMs = listOf(0L, 3_600_000L, DAY, 7 * DAY)[mode]

    val metrics = when (dataset) {
        CsvExport.Dataset.BATTERY -> listOf("Level %", "Power", "Voltage", "Temperature")
        CsvExport.Dataset.SYSTEM -> listOf("CPU", "RAM")
        else -> emptyList()
    }
    if (metric >= metrics.size.coerceAtLeast(1)) metric = 0

    class Result(val series: List<Series>, val unit: String, val rows: List<String>, val count: Int)

    val result by produceState<Result?>(null, ds, mode, metric, range, offsetDays, st.tempF, st.powerMilliwatts, st.speedMegabytes) {
        value = withContext(Dispatchers.IO) {
            when (dataset) {
                CsvExport.Dataset.BATTERY -> if (mode == 0) {
                    val raw = dao.batteryRaw(start, end)
                    val name = listOf("Level", "Power", "Voltage", "Temp")[metric]
                    val unit = when (metric) { 0 -> "%"; 1 -> if (st.powerMilliwatts) "mW" else "W"; 2 -> "mV"; else -> if (st.tempF) "°F" else "°C" }
                    val f: (app.aura.data.BatterySampleEntity) -> Float? = { r ->
                        when (metric) {
                            0 -> r.level.toFloat()
                            1 -> r.powerMw?.let { if (st.powerMilliwatts) it else it / 1000f }
                            2 -> r.voltageMv.toFloat()
                            else -> Fmt.tempNum(r.tempC, st.tempF)
                        }
                    }
                    val pts = raw.mapNotNull { r -> f(r)?.let { r.ts to it } }.thin(500)
                    Result(listOf(Series(name, accent, pts)), unit, emptyList(), raw.size)
                } else {
                    val b = dao.batteryBuckets(start, end, bucketMs).first()
                    val (unit, avg, mx) = when (metric) {
                        0 -> Triple("%", b.map { it.bucket to it.avgLevel }, emptyList())
                        1 -> Triple(if (st.powerMilliwatts) "mW" else "W", b.mapNotNull { x -> x.avgPowerMw?.let { x.bucket to if (st.powerMilliwatts) it else it / 1000f } }, emptyList())
                        2 -> Triple("mV", b.map { it.bucket to it.avgVoltageMv }, emptyList())
                        else -> Triple(if (st.tempF) "°F" else "°C", b.map { it.bucket to Fmt.tempNum(it.avgTempC, st.tempF) }, b.map { it.bucket to Fmt.tempNum(it.maxTempC, st.tempF) })
                    }
                    Result(
                        listOfNotNull(Series("Avg", accent, avg), if (mx.isNotEmpty()) Series("Max", AuraColors.Bad, mx) else null), unit,
                        b.takeLast(12).reversed().map { x ->
                            String.format(Locale.US, "%s · %d%% avg · %.1f–%.1f °C · %.0f mV · n=%d", stamp(x.bucket, mode), x.avgLevel.toInt(), x.avgTempC, x.maxTempC, x.avgVoltageMv, x.n)
                        },
                        b.sumOf { it.n },
                    )
                }
                CsvExport.Dataset.SYSTEM -> if (mode == 0) {
                    val raw = dao.systemRaw(start, end)
                    val pts = raw.mapNotNull { r -> (if (metric == 0) r.cpuLoadPct else r.ramUsedPct)?.let { r.ts to it } }.thin(500)
                    Result(listOf(Series(if (metric == 0) "CPU" else "RAM", accent, pts)), "%", emptyList(), raw.size)
                } else {
                    val b = dao.systemBuckets(start, end, bucketMs).first()
                    val avg = b.mapNotNull { x -> (if (metric == 0) x.avgCpu else x.avgRam)?.let { x.bucket to it } }
                    val mx = b.mapNotNull { x -> (if (metric == 0) x.maxCpu else x.maxRam)?.let { x.bucket to it } }
                    Result(
                        listOf(Series("Avg", accent, avg), Series("Max", AuraColors.Bad, mx)), "%",
                        b.takeLast(12).reversed().map { x -> String.format(Locale.US, "%s · CPU %s · RAM %.0f%% (max %.0f%%)", stamp(x.bucket, mode), x.avgCpu?.let { "%.0f%%".format(it) } ?: NA, x.avgRam, x.maxRam) },
                        b.sumOf { it.n },
                    )
                }
                CsvExport.Dataset.SPEED -> {
                    val r = dao.speedResultsOnce(start, end)
                    Result(
                        listOf(
                            Series("↓", accent, r.mapNotNull { x -> x.downMbps?.let { x.ts to Fmt.speedNum(it, st.speedMegabytes) } }),
                            Series("↑", Color(0xFFFF6BD6), r.mapNotNull { x -> x.upMbps?.let { x.ts to Fmt.speedNum(it, st.speedMegabytes) } }),
                        ),
                        Fmt.speedUnit(st.speedMegabytes),
                        r.takeLast(12).reversed().map { x -> "${fmtDateTime(x.ts)} · ↓ ${x.downMbps?.let { Fmt.speed(it, st.speedMegabytes) } ?: NA} ↑ ${x.upMbps?.let { Fmt.speed(it, st.speedMegabytes) } ?: NA} · ping ${Fmt.ms(x.pingMs)} · ${x.server}" },
                        r.size,
                    )
                }
                CsvExport.Dataset.SESSIONS -> {
                    val r = dao.sessionsOnce(start, end)
                    Result(
                        listOf(Series("Avg W", accent, r.mapNotNull { x -> x.avgPowerMw?.let { x.startTs to it / 1000f } })), "W",
                        r.takeLast(20).reversed().map { x -> "${fmtDateTime(x.startTs)} · ${x.startLevel}%→${x.endLevel}% · ${Fmt.duration((x.endTs ?: x.startTs) - x.startTs)} · avg ${x.avgPowerMw?.let { Fmt.power(it, st.powerMilliwatts) } ?: NA}" },
                        r.size,
                    )
                }
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ScreenTitle("Historical analytics", "Measurements are stored on this device only (kept 90 days).") }
        item { ChipRow(CsvExport.Dataset.values().map { it.label }, ds, { ds = it; metric = 0; if (it >= 2) mode = 0 }) }
        if (dataset == CsvExport.Dataset.BATTERY || dataset == CsvExport.Dataset.SYSTEM) {
            item { ChipRow(listOf("Raw samples", "Hourly stats", "Daily stats", "Weekly stats"), mode, { mode = it }) }
            item { ChipRow(metrics, metric, { metric = it }) }
        }
        item { ChipRow(rangeLabels, range, { range = it }) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                GhostButton("‹ Earlier", { offsetDays += (rangeMs[range] / DAY).toInt().coerceAtLeast(1) })
                Text(fmtDateTime(start) + " – " + fmtDateTime(end), fontSize = 12.sp, color = AuraColors.Muted)
                GhostButton("Later ›", { offsetDays = (offsetDays - (rangeMs[range] / DAY).toInt().coerceAtLeast(1)).coerceAtLeast(0) }, enabled = offsetDays > 0)
            }
        }
        val r = result
        item {
            ChartCard(
                "${dataset.label}${if (metrics.isNotEmpty()) " · " + metrics[metric] else ""}", r?.series ?: emptyList(), r?.unit ?: "", st.graphStyle,
                xFmt = if (rangeMs[range] <= DAY) ::fmtTime else ::fmtDateTime, height = 180.dp, decimals = 1,
                note = r?.let { "${it.count} stored sample(s) in this window." + if (mode >= 2) " Daily/weekly buckets are aligned to UTC." else "" },
            )
        }
        r?.rows?.takeIf { it.isNotEmpty() }?.let { rows ->
            item { Label("Statistics table") }
            items(rows) { line -> GlassCard(Modifier.fillMaxWidth(), pad = 10.dp) { Text(line, fontSize = 12.sp) } }
        }
        item {
            val fname = "aura_${dataset.file}_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".csv"
            AuraButton("Export CSV (${dataset.label}, this window)", {
                actions.exportCsv(fname) { out -> CsvExport.write(dao, dataset, start, end, out) }
            }, Modifier.fillMaxWidth())
            Text("Exports raw samples (not aggregates) for the selected window.", fontSize = 10.sp, color = AuraColors.Muted)
        }
    }
}

private fun stamp(ms: Long, mode: Int) =
    SimpleDateFormat(if (mode == 1) "dd MMM HH:00" else "dd MMM yyyy", Locale.US).format(Date(ms))
