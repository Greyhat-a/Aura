package app.aura.service

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.aura.AuraGraph
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Schedules the independent monitors. Sampling runs only while the UI is visible or the user enabled the
 * foreground monitoring service; in the background the interval is stretched (settings.bgIntervalMs) to save battery.
 */
class MonitorHub(private val g: AuraGraph) {
    private val visible = MutableStateFlow(false)
    private val bgHolds = MutableStateFlow(0)

    val batteryHistory = MutableStateFlow<List<app.aura.monitor.BatterySnapshot>>(emptyList())
    val systemHistory = MutableStateFlow<List<app.aura.monitor.SystemSnapshot>>(emptyList())
    val netHistory = MutableStateFlow<List<app.aura.monitor.NetworkSnapshot>>(emptyList())

    fun holdBackground(on: Boolean) { bgHolds.update { (it + if (on) 1 else -1).coerceAtLeast(0) } }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { visible.value = true }
            override fun onStop(owner: LifecycleOwner) { visible.value = false }
        })
        g.scope.launch {
            combine(visible, bgHolds) { v, b -> v to (b > 0) }.distinctUntilChanged().collectLatest { (fg, bg) ->
                if (fg || bg) coroutineScope {
                    launch { batteryLoop(fg) }
                    launch { systemLoop(fg) }
                    launch { netLoop(fg) }
                    if (fg) launch { probeLoop() }
                    launch { housekeepingLoop() }
                }
            }
        }
    }

    private fun interval(fg: Boolean, ms: Long) = if (fg) ms else maxOf(ms, g.settings.state.value.bgIntervalMs)

    private fun <T> List<T>.pushed(x: T, max: Int): List<T> = if (size >= max) drop(size - max + 1) + x else this + x

    private suspend fun batteryLoop(fg: Boolean) {
        while (true) {
            runCatching { g.battery.sample() }.getOrNull()?.let { s -> batteryHistory.update { it.pushed(s, 600) } }
            delay(interval(fg, g.settings.state.value.batteryMs))
        }
    }

    private suspend fun systemLoop(fg: Boolean) {
        while (true) {
            runCatching { g.system.sample() }.getOrNull()?.let { s -> systemHistory.update { it.pushed(s, 300) } }
            delay(interval(fg, g.settings.state.value.systemMs))
        }
    }

    private suspend fun netLoop(fg: Boolean) {
        while (true) {
            runCatching { g.network.sample() }.getOrNull()?.let { s -> netHistory.update { it.pushed(s, 300) } }
            delay(interval(fg, g.settings.state.value.netMs))
        }
    }

    private suspend fun probeLoop() {
        while (true) {
            val st = g.settings.state.value
            // skip while a speed test runs so the probe does not compete with it for bandwidth
            if (g.network.state.value.conn.connected && !g.speed.state.value.running) runCatching { g.probe.run(st.probeHost.ifBlank { "1.1.1.1" }) }
            delay(st.probeSec.coerceAtLeast(3) * 1000L)
        }
    }

    /** Persists history (throttled by settings.logSec) and evaluates alert rules every ~5 s. */
    private suspend fun housekeepingLoop() {
        delay(2000)
        while (true) {
            val st = g.settings.state.value
            val b = g.battery.state.value
            if (b != null) runCatching {
                val s = g.system.state.value
                g.recorder.record(b, s, st.logSec * 1000L)
                g.alerts.evaluate(b, s, g.network.state.value.conn, st)
            }
            delay(5000)
        }
    }
}
