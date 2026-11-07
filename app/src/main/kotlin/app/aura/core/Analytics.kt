package app.aura.core

import java.util.Locale
import kotlin.math.abs

/**
 * Pure analysis helpers (no Android dependencies, unit-tested on the JVM).
 * Everything here works on measurements that were really recorded; none of it invents data.
 */
object Analytics {

    /** hourEpoch = ts / 3_600_000 (as produced by the DAO), capMah = counter / level * 100. */
    data class CapPoint(val hourEpoch: Long, val capMah: Float)

    data class Degradation(
        val medianFullMah: Float,
        val spreadMah: Float,
        val ratedMah: Int?,
        /** Estimated full-charge capacity as % of the rated capacity the USER entered. Null if no rating entered. */
        val pctOfRated: Float?,
        val hours: Int,
        val spanDays: Int,
        val confidence: String,
    )

    /**
     * Rough full-charge-capacity estimate from the fuel gauge's coulomb counter and the rounded percentage.
     * Needs at least 12 hourly buckets spread over at least 2 days, otherwise returns null ("not enough data").
     * Never reports "high" confidence: the percentage is rounded and vendors' counters drift.
     */
    fun degradation(points: List<CapPoint>, ratedMah: Int): Degradation? {
        val p = points.filter { it.capMah > 0f }
        if (p.size < 12) return null
        val spanDays = ((p.maxOf { it.hourEpoch } - p.minOf { it.hourEpoch }) / 24).toInt() + 1
        if (spanDays < 2) return null
        val sorted = p.map { it.capMah }.sorted()
        val med = ChargeMath.median(sorted)!!
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[(sorted.size * 3 / 4).coerceAtMost(sorted.size - 1)]
        val spread = q3 - q1
        val conf = when {
            p.size >= 48 && spread <= med * 0.08f -> "Moderate"
            p.size >= 24 && spread <= med * 0.15f -> "Low"
            else -> "Very low (noisy)"
        }
        val pct = if (ratedMah > 0) (med / ratedMah * 100f) else null
        return Degradation(med, spread, ratedMah.takeIf { it > 0 }, pct, p.size, spanDays, conf)
    }

    /** One battery sample reduced to what the consistency check needs. */
    data class Sample(val ts: Long, val currentMa: Float?, val counterMah: Float?)

    data class Consistency(val integratedMah: Float, val counterDeltaMah: Float, val ratioPct: Float, val seconds: Long)

    /**
     * Cross-checks two independent fuel-gauge outputs over a charging session:
     *   integral of CURRENT_NOW dt   vs   change of CHARGE_COUNTER.
     * They should agree (~100 %). A large deviation means the gauge/unit/sign assumption is off or the counter is
     * not a true coulomb counter. This validates the measurement chain. It is NOT charger efficiency
     * (nothing here sees the wall adapter).
     * Gaps longer than [maxGapMs] are skipped so sleep periods do not corrupt the integral.
     */
    fun coulombConsistency(samples: List<Sample>, maxGapMs: Long = 120_000L): Consistency? {
        var integrated = 0.0
        var delta = 0.0
        var secs = 0L
        for (i in 1 until samples.size) {
            val a = samples[i - 1]; val b = samples[i]
            val dt = b.ts - a.ts
            if (dt <= 0 || dt > maxGapMs) continue
            val ia = a.currentMa ?: continue; val ib = b.currentMa ?: continue
            val ca = a.counterMah ?: continue; val cb = b.counterMah ?: continue
            integrated += (ia + ib) / 2.0 * dt / 3_600_000.0
            delta += (cb - ca)
            secs += dt / 1000
        }
        if (abs(integrated) < 20.0) return null          // too little charge moved to compare meaningfully
        return Consistency(integrated.toFloat(), delta.toFloat(), (delta / integrated * 100.0).toFloat(), secs)
    }

    data class Warning(val level: Int, val text: String)   // level 1 = info, 2 = caution, 3 = serious

    fun temperatureWarnings(maxTempC24h: Float?, userLimitC: Float, healthText: String, sessionMaxTempC: Float?): List<Warning> {
        val w = ArrayList<Warning>()
        if (maxTempC24h != null && maxTempC24h >= 45f)
            w.add(Warning(3, "Battery reached ${f1(maxTempC24h)} °C in the last 24 h. Sustained temperatures above 45 °C accelerate ageing."))
        else if (maxTempC24h != null && maxTempC24h >= userLimitC)
            w.add(Warning(2, "Battery reached ${f1(maxTempC24h)} °C in the last 24 h (your limit is ${f1(userLimitC)} °C)."))
        if (sessionMaxTempC != null && sessionMaxTempC >= 40f)
            w.add(Warning(1, "The latest charging session peaked at ${f1(sessionMaxTempC)} °C. Charging while gaming or under a case runs hotter."))
        if (healthText != "Good" && healthText != "Unknown")
            w.add(Warning(3, "Android reports battery health: $healthText."))
        return w
    }

    private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
}

/** RFC-4180 style CSV helpers. */
object Csv {
    fun cell(v: Any?): String = when (v) {
        null -> ""
        is Float -> String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
        is Double -> String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
        is Boolean -> if (v) "1" else "0"
        else -> {
            val s = v.toString()
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
    }

    fun row(vararg v: Any?): String = v.joinToString(",") { cell(it) } + "\r\n"
}
