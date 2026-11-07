package app.aura.monitor

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import app.aura.core.Reading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class CpuStatic(
    val model: String, val soc: String?, val abis: String, val cores: Int, val maxFreqMhz: List<Int?>,
)

data class DisplayInfo(
    val widthPx: Int, val heightPx: Int, val refreshHz: Float, val supportedHz: List<Float>,
    val densityDpi: Int, val xdpi: Float, val brightness255: Int?, val autoBrightness: Boolean?,
    val wideGamut: Boolean, val hdrTypes: List<Int>, val name: String,
)

data class SystemSnapshot(
    val ts: Long,
    /** Overall CPU load 0..100 with the honest kind of measurement behind it. */
    val cpuLoad: Reading<Float>,
    val cpuLoadKind: String?,
    /** Per-core 0..1; null entries = core offline / unreadable. */
    val coreLoad: Reading<List<Float?>>,
    val coreFreqMhz: Reading<List<Int?>>,
    val appCpuPct: Float?,
    val thermalStatus: Int,
    val thermalHeadroom: Reading<Float>,
    val ramTotal: Long, val ramAvail: Long, val lowMemory: Boolean, val ramThreshold: Long,
    val swapTotal: Long?, val swapFree: Long?, val cached: Long?,
    val storageTotal: Long, val storageAvail: Long,
    val uptimeMs: Long,
    val display: DisplayInfo,
) {
    val ramUsed get() = ramTotal - ramAvail
    val ramUsedPct get() = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
    val storageUsed get() = storageTotal - storageAvail
    val storageUsedPct get() = if (storageTotal > 0) storageUsed * 100f / storageTotal else 0f
}

class SystemMonitor(private val ctx: Context) {
    private val am = ctx.getSystemService(ActivityManager::class.java)
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val dm = ctx.getSystemService(DisplayManager::class.java)

    private val _state = MutableStateFlow<SystemSnapshot?>(null)
    val state: StateFlow<SystemSnapshot?> = _state.asStateFlow()

    val cpuStatic: CpuStatic by lazy { readCpuStatic() }

    // /proc/stat is blocked for apps since Android 8; we try once and remember the outcome.
    private var procStatBlocked = false
    private var prevStat: List<LongArray>? = null
    private var prevAppCpuMs = 0L
    private var prevWallMs = 0L

    fun sample(): SystemSnapshot {
        val cores = cpuStatic.cores
        var cpuLoad: Reading<Float> = Reading.Unavailable("No readable CPU counters (see Hardware > CPU)")
        var kind: String? = null
        var coreLoad: Reading<List<Float?>> = Reading.Unavailable("No readable per-core data")

        // 1) true utilisation from /proc/stat, when the OS allows it
        val stat = if (procStatBlocked) null else readProcStat()
        if (stat == null) procStatBlocked = true
        if (stat != null) {
            val p = prevStat
            if (p != null && p.size == stat.size) {
                val loads = stat.indices.map { loadBetween(p[it], stat[it]) }
                if (loads.isNotEmpty() && loads[0] != null) {
                    cpuLoad = Reading.Value(loads[0]!! * 100f, "/proc/stat delta (true utilisation)")
                    kind = "Utilisation (/proc/stat)"
                    coreLoad = Reading.Value(loads.drop(1), "/proc/stat per-core delta")
                }
            }
            prevStat = stat
        }

        // 2) cpufreq counters (frequency relative to max; NOT utilisation)
        val freqs = (0 until cores).map { readLong("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") }
        val coreFreq: Reading<List<Int?>> =
            if (freqs.any { it != null }) Reading.Value(freqs.map { it?.let { f -> (f / 1000).toInt() } }, "/sys/devices/system/cpu/cpuN/cpufreq")
            else Reading.Unavailable("cpufreq sysfs is not readable by apps on this device")
        if (cpuLoad is Reading.Unavailable && freqs.any { it != null }) {
            val ratios = freqs.mapIndexed { i, f ->
                val max = cpuStatic.maxFreqMhz.getOrNull(i)?.times(1000L)
                if (f != null && max != null && max > 0) (f.toFloat() / max).coerceIn(0f, 1f) else null
            }
            val valid = ratios.filterNotNull()
            if (valid.isNotEmpty()) {
                cpuLoad = Reading.Value(valid.average().toFloat() * 100f, "cpufreq: mean(current/max frequency)")
                kind = "Frequency index (cur/max clock, not utilisation)"
                coreLoad = Reading.Value(ratios, "cpufreq: current/max frequency per core")
            }
        }

        // this app's own CPU share is always measurable
        val nowWall = SystemClock.elapsedRealtime()
        val nowCpu = Process.getElapsedCpuTime()
        val appPct = if (prevWallMs > 0 && nowWall > prevWallMs)
            ((nowCpu - prevAppCpuMs).toFloat() / (nowWall - prevWallMs) / cores * 100f).coerceIn(0f, 100f) else null
        prevWallMs = nowWall; prevAppCpuMs = nowCpu

        val headroom: Float = try { pm.getThermalHeadroom(10) } catch (_: Exception) { Float.NaN }

        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val meminfo = readMeminfo()
        val st = StatFs(Environment.getDataDirectory().path)

        val snap = SystemSnapshot(
            ts = System.currentTimeMillis(), cpuLoad = cpuLoad, cpuLoadKind = kind, coreLoad = coreLoad,
            coreFreqMhz = coreFreq, appCpuPct = appPct, thermalStatus = pm.currentThermalStatus,
            thermalHeadroom = if (headroom.isNaN()) Reading.Unavailable("PowerManager.getThermalHeadroom() unsupported")
            else Reading.Value(headroom, "PowerManager.getThermalHeadroom(10 s forecast); 1.0 = throttling threshold"),
            ramTotal = mi.totalMem, ramAvail = mi.availMem, lowMemory = mi.lowMemory, ramThreshold = mi.threshold,
            swapTotal = meminfo["SwapTotal"], swapFree = meminfo["SwapFree"], cached = meminfo["Cached"],
            storageTotal = st.totalBytes, storageAvail = st.availableBytes,
            uptimeMs = SystemClock.elapsedRealtime(), display = readDisplay(),
        )
        _state.value = snap
        return snap
    }

    private fun loadBetween(a: LongArray, b: LongArray): Float? {
        if (a.size < 5 || b.size < 5) return null
        val total = (0 until minOf(8, a.size, b.size)).sumOf { b[it] - a[it] }
        val idle = (b[3] - a[3]) + (b[4] - a[4])
        return if (total > 0) ((total - idle).toFloat() / total).coerceIn(0f, 1f) else null
    }

    private fun readProcStat(): List<LongArray>? = try {
        File("/proc/stat").readLines().filter { it.startsWith("cpu") }.map { line ->
            line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }.toLongArray()
        }.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }

    private fun readLong(path: String): Long? = try { File(path).readText().trim().toLong() } catch (_: Exception) { null }

    private fun readMeminfo(): Map<String, Long> = try {
        File("/proc/meminfo").readLines().mapNotNull {
            val p = it.split(":")
            if (p.size == 2) p[0].trim() to (p[1].trim().split(" ")[0].toLongOrNull()?.times(1024) ?: return@mapNotNull null) else null
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    private fun readCpuStatic(): CpuStatic {
        val cores = Runtime.getRuntime().availableProcessors()
        var hardware: String? = null
        var modelName: String? = null
        try {
            File("/proc/cpuinfo").forEachLine {
                val p = it.split(":", limit = 2)
                if (p.size == 2) {
                    val k = p[0].trim(); val v = p[1].trim()
                    if (k.equals("Hardware", true) && hardware == null) hardware = v
                    if (k.equals("model name", true) && modelName == null) modelName = v
                }
            }
        } catch (_: Exception) { }
        val soc = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").ifBlank { null } else null
        val model = listOfNotNull(soc, hardware, modelName, Build.HARDWARE.takeIf { it.isNotBlank() }).firstOrNull() ?: "Unknown"
        val max = (0 until cores).map { readLong("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")?.let { f -> (f / 1000).toInt() } }
        return CpuStatic(model, soc, Build.SUPPORTED_ABIS.joinToString(), cores, max)
    }

    private fun readDisplay(): DisplayInfo {
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = ctx.resources.displayMetrics
        val mode = d.mode
        val cr = ctx.contentResolver
        val br = try { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) } catch (_: Exception) { null }
        val auto = try { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC } catch (_: Exception) { null }
        val hdr = if (Build.VERSION.SDK_INT >= 34) mode.supportedHdrTypes.toList() else emptyList()
        return DisplayInfo(
            widthPx = mode.physicalWidth, heightPx = mode.physicalHeight, refreshHz = d.refreshRate,
            supportedHz = d.supportedModes.map { it.refreshRate }.distinct().sorted(),
            densityDpi = metrics.densityDpi, xdpi = metrics.xdpi, brightness255 = br, autoBrightness = auto,
            wideGamut = d.isWideColorGamut, hdrTypes = hdr, name = d.name,
        )
    }
}
