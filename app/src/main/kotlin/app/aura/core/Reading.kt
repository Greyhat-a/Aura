package app.aura.core

import java.util.Locale
import kotlin.math.abs

const val NA = "Not available on this device"

/**
 * A measurement is either a real value with a named data source, or explicitly unavailable
 * with a reason. There is deliberately no "default" or "simulated" variant.
 */
sealed interface Reading<out T> {
    data class Value<T>(val value: T, val source: String) : Reading<T>
    data class Unavailable(val reason: String) : Reading<Nothing>
}

fun <T> Reading<T>.orNull(): T? = (this as? Reading.Value<T>)?.value
fun <T> Reading<T>.sourceOrReason(): String = when (this) {
    is Reading.Value -> source
    is Reading.Unavailable -> reason
}
fun <T> Reading<T>.show(fmt: (T) -> String): String = when (this) {
    is Reading.Value -> fmt(value)
    is Reading.Unavailable -> NA
}

object Fmt {
    private fun f(fmt: String, vararg a: Any?) = String.format(Locale.US, fmt, *a)

    fun temp(c: Float?, fahrenheit: Boolean): String =
        if (c == null) NA else if (fahrenheit) f("%.1f °F", c * 9f / 5f + 32f) else f("%.1f °C", c)

    fun tempNum(c: Float, fahrenheit: Boolean): Float = if (fahrenheit) c * 9f / 5f + 32f else c

    fun power(mw: Float?, inMilliwatts: Boolean): String = when {
        mw == null -> NA
        inMilliwatts -> f("%.0f mW", mw)
        else -> f("%.2f W", mw / 1000f)
    }

    fun speed(mbps: Float?, megabytes: Boolean): String = when {
        mbps == null -> NA
        megabytes -> f("%.2f MB/s", mbps / 8f)
        else -> f("%.2f Mbps", mbps)
    }

    fun speedNum(mbps: Float, megabytes: Boolean) = if (megabytes) mbps / 8f else mbps
    fun speedUnit(megabytes: Boolean) = if (megabytes) "MB/s" else "Mbps"

    fun ms(v: Float?): String = if (v == null) NA else f("%.1f ms", v)
    fun pct(v: Float?): String = if (v == null) NA else f("%.0f %%", v)

    fun duration(ms: Long?): String {
        if (ms == null || ms < 0) return NA
        val m = ms / 60_000
        return if (m >= 60) f("%dh %02dm", m / 60, m % 60) else if (m >= 1) "${m} min" else "< 1 min"
    }

    fun uptime(ms: Long): String {
        val s = ms / 1000
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
    }

    fun bytes(b: Long): String {
        val a = abs(b).toDouble()
        return when {
            a >= 1L shl 30 -> f("%.2f GB", a / (1L shl 30))
            a >= 1L shl 20 -> f("%.0f MB", a / (1L shl 20))
            a >= 1L shl 10 -> f("%.0f KB", a / (1L shl 10))
            else -> "$b B"
        }
    }
}
