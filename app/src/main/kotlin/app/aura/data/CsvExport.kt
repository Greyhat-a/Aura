package app.aura.data

import app.aura.core.Csv
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Streams stored measurements as CSV, one day per query, so even a 90-day export keeps memory bounded. */
object CsvExport {
    enum class Dataset(val label: String, val file: String) {
        BATTERY("Battery", "battery"), SYSTEM("CPU & RAM", "cpu_ram"), SPEED("Internet speed", "speed"), SESSIONS("Charging sessions", "sessions"),
    }

    private const val DAY = 86_400_000L

    suspend fun write(dao: AuraDao, ds: Dataset, from: Long, to: Long, out: OutputStream) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fun t(ms: Long) = fmt.format(Date(ms))
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("﻿")   // BOM so spreadsheet apps detect UTF-8
        when (ds) {
            Dataset.BATTERY -> {
                w.write(Csv.row("timestamp_ms", "time_local", "level_pct", "charging", "plugged", "voltage_mv", "current_ma", "power_mw", "temp_c", "charge_counter_mah"))
                var s = from
                while (s <= to) {
                    dao.batteryRaw(s, minOf(s + DAY - 1, to)).forEach {
                        w.write(Csv.row(it.ts, t(it.ts), it.level, it.charging, it.plugged, it.voltageMv, it.currentMa, it.powerMw, it.tempC, it.chargeMah))
                    }
                    s += DAY
                }
            }
            Dataset.SYSTEM -> {
                w.write(Csv.row("timestamp_ms", "time_local", "cpu_load_pct", "cpu_load_kind", "ram_used_pct", "storage_used_pct", "thermal_headroom", "app_cpu_pct"))
                var s = from
                while (s <= to) {
                    dao.systemRaw(s, minOf(s + DAY - 1, to)).forEach {
                        w.write(Csv.row(it.ts, t(it.ts), it.cpuLoadPct, it.cpuLoadKind, it.ramUsedPct, it.storageUsedPct, it.thermalHeadroom, it.appCpuPct))
                    }
                    s += DAY
                }
            }
            Dataset.SPEED -> {
                w.write(Csv.row("timestamp_ms", "time_local", "server", "network", "download_mbps", "upload_mbps", "ping_ms", "jitter_ms", "loss_pct"))
                dao.speedResultsOnce(from, to).forEach {
                    w.write(Csv.row(it.ts, t(it.ts), it.server, it.network, it.downMbps, it.upMbps, it.pingMs, it.jitterMs, it.lossPct))
                }
            }
            Dataset.SESSIONS -> {
                w.write(Csv.row("id", "start_local", "end_local", "start_level", "end_level", "plugged", "avg_power_mw", "peak_power_mw", "max_temp_c", "start_counter_mah", "end_counter_mah"))
                dao.sessionsOnce(from, to).forEach {
                    w.write(Csv.row(it.id, t(it.startTs), it.endTs?.let(::t), it.startLevel, it.endLevel, it.plugged, it.avgPowerMw, it.peakPowerMw, it.maxTempC, it.startChargeMah, it.endChargeMah))
                }
            }
        }
        w.flush()
    }
}
