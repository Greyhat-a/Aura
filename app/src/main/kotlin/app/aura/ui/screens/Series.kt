package app.aura.ui.screens

import app.aura.core.Fmt
import app.aura.core.orNull
import app.aura.monitor.BatterySnapshot
import app.aura.monitor.NetworkSnapshot
import app.aura.monitor.SystemSnapshot

/** Converts live in-memory histories to (timestamp, value) points. Unavailable samples are skipped, never filled. */

fun List<BatterySnapshot>.powerPts(mw: Boolean): List<Pair<Long, Float>> =
    mapNotNull { s -> s.powerMw.orNull()?.let { s.ts to if (mw) it else it / 1000f } }

fun List<BatterySnapshot>.voltPts(): List<Pair<Long, Float>> =
    filter { it.voltageMv > 0 }.map { it.ts to it.voltageMv.toFloat() }

fun List<BatterySnapshot>.currentPts(): List<Pair<Long, Float>> =
    mapNotNull { s -> s.currentMa.orNull()?.let { s.ts to it } }

fun List<BatterySnapshot>.tempPts(fahrenheit: Boolean): List<Pair<Long, Float>> =
    map { it.ts to Fmt.tempNum(it.tempC, fahrenheit) }

fun List<SystemSnapshot>.cpuPts(): List<Pair<Long, Float>> =
    mapNotNull { s -> s.cpuLoad.orNull()?.let { s.ts to it } }

fun List<SystemSnapshot>.ramPts(): List<Pair<Long, Float>> = map { it.ts to it.ramUsedPct }

fun List<NetworkSnapshot>.downPts(megabytes: Boolean): List<Pair<Long, Float>> =
    mapNotNull { s -> s.downMbps.orNull()?.let { s.ts to Fmt.speedNum(it, megabytes) } }

fun List<NetworkSnapshot>.upPts(megabytes: Boolean): List<Pair<Long, Float>> =
    mapNotNull { s -> s.upMbps.orNull()?.let { s.ts to Fmt.speedNum(it, megabytes) } }

/** Thins a long series for drawing; keeps first and last point. */
fun <T> List<T>.thin(max: Int): List<T> {
    if (size <= max || max < 2) return this
    val step = (size - 1).toFloat() / (max - 1)
    return (0 until max).map { this[(it * step).toInt().coerceAtMost(size - 1)] }
}
