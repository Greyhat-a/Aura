package app.aura.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import app.aura.core.ChargeMath
import app.aura.core.Reading
import app.aura.core.orNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

data class BatterySnapshot(
    val ts: Long,
    val levelPct: Int,
    val statusText: String,
    val charging: Boolean,
    val full: Boolean,
    val plugged: Int,
    val sourceText: String,
    val voltageMv: Int,
    val tempC: Float,
    val healthText: String,
    val technology: String?,
    /** Positive = into the battery. Battery-side, net of the device's own consumption. */
    val currentMa: Reading<Float>,
    val avgCurrentMa: Reading<Float>,
    /** Battery-side power (V x I). Never the wall adapter output. */
    val powerMw: Reading<Float>,
    val chargeMah: Reading<Float>,
    val cycleCount: Reading<Int>,
    val sohPct: Reading<Int>,
    val policy: Reading<Int>,
    val estFullMah: Float?,
    val etaFull: Reading<Long>,
    val etaEmpty: Reading<Long>,
    val chargeClass: String?,
    val currentNote: String,
)

class BatteryMonitor(private val ctx: Context) {
    private val bm = ctx.getSystemService(BatteryManager::class.java)
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val calib = ChargeMath.CurrentCalibrator()

    private val _state = MutableStateFlow<BatterySnapshot?>(null)
    val state: StateFlow<BatterySnapshot?> = _state.asStateFlow()

    private var emaCurrent: Float? = null      // mA, positive into battery
    private var emaFull: Float? = null
    private var zeroStreak = 0

    fun sample(): BatterySnapshot {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val now = System.currentTimeMillis()

        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt()
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val discharging = status == BatteryManager.BATTERY_STATUS_DISCHARGING && plugged == 0
        val voltage = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        // ---- current (BatteryManager property; unit and sign are normalised by observation) ----
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentNote = "BATTERY_PROPERTY_CURRENT_NOW"
        val current: Reading<Float> = when {
            raw == Int.MIN_VALUE -> Reading.Unavailable("BATTERY_PROPERTY_CURRENT_NOW is not supported by this device")
            else -> {
                if (raw == 0 && (charging || discharging)) zeroStreak++ else zeroStreak = 0
                if (zeroStreak >= 20) {
                    Reading.Unavailable("Fuel gauge keeps reporting 0 while ${if (charging) "charging" else "discharging"}; treated as unsupported")
                } else {
                    val ma = calib.normalise(raw, charging, discharging)
                    currentNote = "BATTERY_PROPERTY_CURRENT_NOW (${calib.unitLabel()}${if (calib.flipped) ", sign inverted by observation" else ""})"
                    Reading.Value(ma, currentNote)
                }
            }
        }
        current.orNull()?.let { emaCurrent = emaCurrent?.let { e -> e * 0.85f + it * 0.15f } ?: it }

        val avgRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val avgCurrent: Reading<Float> = when {
            avgRaw != Int.MIN_VALUE && avgRaw != 0 && current is Reading.Value -> {
                val v = if (calib.inMilliamps) avgRaw.toFloat() else avgRaw / 1000f
                Reading.Value(if (calib.flipped) -v else v, "BATTERY_PROPERTY_CURRENT_AVERAGE")
            }
            emaCurrent != null -> Reading.Value(emaCurrent!!, "Computed: ~7 s exponential average of CURRENT_NOW")
            else -> Reading.Unavailable("Needs CURRENT_NOW")
        }

        // ---- power = V x I, both from the battery side ----
        val power: Reading<Float> = current.orNull()?.let { ma ->
            if (voltage > 0) Reading.Value(
                ChargeMath.powerMw(voltage, ma),
                "Computed: EXTRA_VOLTAGE x CURRENT_NOW (battery-side, not wall-adapter)"
            ) else null
        } ?: Reading.Unavailable("Needs both voltage and current")

        // ---- charge counter / capacity estimate ----
        val counterRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counterMah: Reading<Float> =
            if (counterRaw > 0) Reading.Value(counterRaw / 1000f, "BATTERY_PROPERTY_CHARGE_COUNTER")
            else Reading.Unavailable("BATTERY_PROPERTY_CHARGE_COUNTER not supported")
        val rawEstFull = counterMah.orNull()?.let { ChargeMath.estFullMah(it, pct) }
        if (rawEstFull != null) emaFull = emaFull?.let { it * 0.98f + rawEstFull * 0.02f } ?: rawEstFull

        // ---- API 34+ battery properties ----
        var cycles: Reading<Int> = Reading.Unavailable("Not reported by this device")
        var soh: Reading<Int> = Reading.Unavailable("Not reported by this device")
        var policy: Reading<Int> = Reading.Unavailable("Not reported by this device")
        if (Build.VERSION.SDK_INT >= 34) {
            val c = i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            if (c >= 0) cycles = Reading.Value(c, "ACTION_BATTERY_CHANGED / EXTRA_CYCLE_COUNT")
            val s = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH)
            if (s in 1..100) soh = Reading.Value(s, "BATTERY_PROPERTY_STATE_OF_HEALTH")
            val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGING_POLICY)
            if (p != Int.MIN_VALUE && p > 0) policy = Reading.Value(p, "BATTERY_PROPERTY_CHARGING_POLICY")
        } else {
            cycles = Reading.Unavailable("Requires Android 14+")
            soh = Reading.Unavailable("Requires Android 14+")
        }

        // ---- ETAs: system estimate first, own estimate second ----
        val etaFull: Reading<Long> = when {
            full || pct >= 100 -> Reading.Value(0L, "Battery reports full")
            !charging -> Reading.Unavailable("Not charging")
            else -> {
                val sys = bm.computeChargeTimeRemaining()
                if (sys > 0) Reading.Value(sys, "System: BatteryManager.computeChargeTimeRemaining()")
                else ChargeMath.etaFullMs(emaFull, counterMah.orNull(), emaCurrent)
                    ?.let { Reading.Value(it, "Computed: (est. capacity - charge counter) / avg current; ignores CV taper") }
                    ?: Reading.Unavailable("System gave no estimate and charge counter/current unusable")
            }
        }
        val etaEmpty: Reading<Long> = when {
            charging || plugged != 0 -> Reading.Unavailable("Device is plugged in")
            else -> {
                val sys = try { pm.batteryDischargePrediction?.toMillis() } catch (_: Exception) { null }
                if (sys != null && sys > 0) Reading.Value(sys, "System: PowerManager.getBatteryDischargePrediction()")
                else ChargeMath.etaEmptyMs(counterMah.orNull(), emaCurrent?.let { -it })
                    ?.let { Reading.Value(it, "Computed: charge counter / average drain") }
                    ?: Reading.Unavailable("System gave no prediction and charge counter/current unusable")
            }
        }

        val chargeClass = if (charging) power.orNull()?.let { ChargeMath.classify(it / 1000f) } else null

        val snap = BatterySnapshot(
            ts = now, levelPct = pct, statusText = statusText(status), charging = charging, full = full,
            plugged = plugged, sourceText = pluggedText(plugged), voltageMv = voltage, tempC = temp,
            healthText = healthText(i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1),
            technology = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            currentMa = current, avgCurrentMa = avgCurrent, powerMw = power, chargeMah = counterMah,
            cycleCount = cycles, sohPct = soh, policy = policy, estFullMah = emaFull,
            etaFull = etaFull, etaEmpty = etaEmpty, chargeClass = chargeClass, currentNote = currentNote,
        )
        _state.value = snap
        return snap
    }

    companion object {
        fun statusText(s: Int) = when (s) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            else -> "Unknown"
        }

        fun pluggedText(p: Int): String = when {
            p == 0 -> "Not plugged in"
            else -> buildList {
                if (p and BatteryManager.BATTERY_PLUGGED_AC != 0) add("AC")
                if (p and BatteryManager.BATTERY_PLUGGED_USB != 0) add("USB")
                if (p and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add("Wireless")
                if (Build.VERSION.SDK_INT >= 33 && p and BatteryManager.BATTERY_PLUGGED_DOCK != 0) add("Dock")
            }.joinToString(" + ").ifEmpty { "Unknown ($p)" }
        }

        fun healthText(h: Int) = when (h) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        fun isOverheated(tempC: Float, threshold: Float) = abs(tempC) < 150f && tempC >= threshold
    }
}
