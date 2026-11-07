package app.aura.core

import kotlin.math.abs

/** Pure, unit-tested helpers. No Android dependencies so they can run on the JVM. */
object ChargeMath {
    /** V(mV) x I(mA) = uW; /1000 -> mW. Valid only when both come from the same battery fuel gauge. */
    fun powerMw(voltageMv: Int, currentMa: Float): Float = voltageMv * currentMa / 1000f

    /** Full-charge capacity estimate from the coulomb counter and the rounded percentage. Rough (+/- several %). */
    fun estFullMah(counterMah: Float, pct: Int): Float? =
        if (counterMah > 0f && pct in 10..100) counterMah * 100f / pct else null

    /** Linear estimate; ignores the constant-voltage taper near 100 %, so it under-estimates the real time. */
    fun etaFullMs(fullMah: Float?, counterMah: Float?, chargeMa: Float?): Long? {
        if (fullMah == null || counterMah == null || chargeMa == null || chargeMa < 50f) return null
        val remaining = (fullMah - counterMah).coerceAtLeast(0f)
        return (remaining / chargeMa * 3_600_000f).toLong()
    }

    fun etaEmptyMs(counterMah: Float?, drainMa: Float?): Long? {
        if (counterMah == null || drainMa == null || drainMa < 20f || counterMah <= 0f) return null
        return (counterMah / drainMa * 3_600_000f).toLong()
    }

    /** Inferred only from battery-side power. Android does not expose the charging protocol. */
    fun classify(batteryW: Float): String = when {
        batteryW < 2.5f -> "Trickle / low-power (USB-2 class)"
        batteryW < 7.5f -> "Standard"
        batteryW < 12f -> "Fast"
        else -> "Very fast"
    }

    /**
     * Battery-side power as a share of the user-entered adapter rating. This is a LOWER BOUND of efficiency
     * (the device is also powering itself and the adapter may not be at its rated output). Not true efficiency.
     */
    fun batterySharePct(batteryW: Float, adapterW: Float): Float? =
        if (adapterW > 0f && batteryW >= 0f) (batteryW / adapterW * 100f).coerceAtMost(100f) else null

    fun median(v: List<Float>): Float? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    fun jitterMs(rtts: List<Float>): Float? =
        if (rtts.size < 2) null else rtts.zipWithNext { a, b -> abs(b - a) }.average().toFloat()

    /**
     * Learns, from observed behaviour, (a) whether CURRENT_NOW is in uA (AOSP) or mA (some OEMs) and
     * (b) the sign convention (AOSP: positive = into the battery). Output is mA, positive = charging.
     */
    class CurrentCalibrator {
        private var maxAbs = 0
        private var n = 0
        private var posCharge = 0
        private var negCharge = 0
        private var posDischarge = 0
        private var negDischarge = 0
        var inMilliamps = false
            private set
        var flipped = false
            private set

        fun normalise(raw: Int, charging: Boolean, discharging: Boolean): Float {
            val a = abs(raw)
            if (a > maxAbs) maxAbs = a
            if (raw != 0 && (charging || discharging)) n++
            inMilliamps = n >= 10 && maxAbs < 10_000
            if (raw != 0) {
                if (charging) if (raw > 0) posCharge++ else negCharge++
                if (discharging) if (raw > 0) posDischarge++ else negDischarge++
            }
            flipped = (negCharge + posDischarge) > (posCharge + negDischarge) + 3
            val ma = if (inMilliamps) raw.toFloat() else raw / 1000f
            return if (flipped) -ma else ma
        }

        fun unitLabel() = if (inMilliamps) "device reports mA" else "uA"
    }
}
