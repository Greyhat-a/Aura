package app.aura.data

import app.aura.core.orNull
import app.aura.monitor.BatterySnapshot
import app.aura.monitor.SystemSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persists measurements locally and tracks charging sessions. Only real, available values are stored. */
class Recorder(private val dao: AuraDao) {
    private val lock = Mutex()
    private var lastLogTs = 0L
    private var lastPrune = 0L
    private var open: ChargeSessionEntity? = null
    private var loaded = false

    suspend fun record(b: BatterySnapshot, s: SystemSnapshot?, minIntervalMs: Long, force: Boolean = false) = lock.withLock {
        if (!loaded) { open = dao.openSession(); loaded = true }
        trackSession(b)
        val now = System.currentTimeMillis()
        if (!force && now - lastLogTs < minIntervalMs) return@withLock
        lastLogTs = now
        dao.insert(
            BatterySampleEntity(
                ts = b.ts, level = b.levelPct, charging = b.charging, plugged = b.plugged, voltageMv = b.voltageMv,
                currentMa = b.currentMa.orNull(), powerMw = b.powerMw.orNull(), tempC = b.tempC, chargeMah = b.chargeMah.orNull(),
            )
        )
        if (s != null) dao.insert(
            SystemSampleEntity(
                ts = s.ts, cpuLoadPct = s.cpuLoad.orNull(), cpuLoadKind = s.cpuLoadKind, ramUsedPct = s.ramUsedPct,
                storageUsedPct = s.storageUsedPct, thermalHeadroom = s.thermalHeadroom.orNull(), appCpuPct = s.appCpuPct,
            )
        )
        if (now - lastPrune > 24L * 3600_000) {
            lastPrune = now
            val cutoff = now - 90L * 24 * 3600_000
            dao.pruneBattery(cutoff); dao.pruneSystem(cutoff); dao.pruneSpeed(cutoff); dao.pruneSessions(cutoff)
        }
    }

    suspend fun recordSpeed(r: SpeedResultEntity) { dao.insert(r) }

    private suspend fun trackSession(b: BatterySnapshot) {
        val cur = open
        val power = b.powerMw.orNull()
        if (b.charging && b.plugged != 0) {
            if (cur == null) {
                val e = ChargeSessionEntity(
                    startTs = b.ts, endTs = null, startLevel = b.levelPct, endLevel = b.levelPct, plugged = b.plugged,
                    sumPowerMw = 0.0, powerSamples = 0, peakPowerMw = 0f, maxTempC = b.tempC,
                    startChargeMah = b.chargeMah.orNull(), endChargeMah = b.chargeMah.orNull(),
                )
                open = e.copy(id = dao.insert(e))
            } else {
                val p = power?.takeIf { it > 0 }
                val upd = cur.copy(
                    endLevel = b.levelPct, maxTempC = maxOf(cur.maxTempC, b.tempC),
                    sumPowerMw = cur.sumPowerMw + (p ?: 0f), powerSamples = cur.powerSamples + if (p != null) 1 else 0,
                    peakPowerMw = maxOf(cur.peakPowerMw, p ?: 0f), endChargeMah = b.chargeMah.orNull() ?: cur.endChargeMah,
                )
                dao.update(upd); open = upd
            }
        } else if (cur != null && (b.plugged == 0 || !b.charging && !b.full)) {
            val closed = cur.copy(endTs = b.ts, endLevel = b.levelPct, endChargeMah = b.chargeMah.orNull() ?: cur.endChargeMah)
            dao.update(closed); open = null
        } else if (cur != null) {
            // plugged and full: keep session open but update end level
            val upd = cur.copy(endLevel = b.levelPct)
            dao.update(upd); open = upd
        }
    }
}
