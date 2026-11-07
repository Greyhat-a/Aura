package app.aura.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.aura.core.CardCfg
import app.aura.core.DashCards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Settings(
    val accent: Int = 0xFF00B4FF.toInt(),
    val tempF: Boolean = false,
    val speedMegabytes: Boolean = false,
    val powerMilliwatts: Boolean = false,
    val graphStyle: Int = 1,          // 0 line, 1 area, 2 bars
    val animStyle: Int = 0,           // 0 hologram, 1 wireframe, 2 solid glow
    val batteryMs: Long = 1000,
    val systemMs: Long = 1000,
    val netMs: Long = 1000,
    val probeSec: Int = 10,
    val logSec: Int = 30,
    val bgIntervalMs: Long = 10_000,
    val dashboard: List<CardCfg> = DashCards.defaults,
    // alerts
    val tempAlertOn: Boolean = true, val tempAlertC: Float = 42f,
    val levelAlertOn: Boolean = false, val levelAlertPct: Int = 80,
    val slowAlertOn: Boolean = false, val slowAlertW: Float = 5f, val slowAlertMin: Int = 3,
    val dropAlertOn: Boolean = false,
    val speedAlertOn: Boolean = false, val speedAlertMbps: Float = 5f,
    val storageAlertOn: Boolean = true, val storageAlertPct: Int = 90,
    // user supplied hardware facts (never assumed by the app)
    val adapterW: Float = 0f,
    val ratedMah: Int = 0,
    // network test
    val serverId: String = "cloudflare",
    val customDown: String = "", val customUp: String = "", val customPing: String = "",
    val probeHost: String = "1.1.1.1",
    // background
    val bgMonitoring: Boolean = false,
    val periodicWork: Boolean = true,
    val periodicSpeed: Boolean = false,
)

private val Context.ds by preferencesDataStore("aura_settings")

private object K {
    val accent = intPreferencesKey("accent"); val tempF = booleanPreferencesKey("tempF")
    val speedMB = booleanPreferencesKey("speedMB"); val powerMw = booleanPreferencesKey("powerMw")
    val graph = intPreferencesKey("graph"); val anim = intPreferencesKey("anim")
    val batMs = longPreferencesKey("batMs"); val sysMs = longPreferencesKey("sysMs"); val netMs = longPreferencesKey("netMs")
    val probeSec = intPreferencesKey("probeSec"); val logSec = intPreferencesKey("logSec"); val bgMs = longPreferencesKey("bgMs")
    val dash = stringPreferencesKey("dash")
    val tOn = booleanPreferencesKey("tOn"); val tV = floatPreferencesKey("tV")
    val lOn = booleanPreferencesKey("lOn"); val lV = intPreferencesKey("lV")
    val sOn = booleanPreferencesKey("sOn"); val sW = floatPreferencesKey("sW"); val sM = intPreferencesKey("sM")
    val dOn = booleanPreferencesKey("dOn")
    val spOn = booleanPreferencesKey("spOn"); val spV = floatPreferencesKey("spV")
    val stOn = booleanPreferencesKey("stOn"); val stV = intPreferencesKey("stV")
    val adapter = floatPreferencesKey("adapter"); val rated = intPreferencesKey("rated")
    val server = stringPreferencesKey("server"); val cDown = stringPreferencesKey("cDown")
    val cUp = stringPreferencesKey("cUp"); val cPing = stringPreferencesKey("cPing")
    val host = stringPreferencesKey("host")
    val bg = booleanPreferencesKey("bg"); val periodic = booleanPreferencesKey("periodic")
    val periodicSpeed = booleanPreferencesKey("periodicSpeed")
}

private fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        accent = this[K.accent] ?: d.accent, tempF = this[K.tempF] ?: d.tempF,
        speedMegabytes = this[K.speedMB] ?: d.speedMegabytes, powerMilliwatts = this[K.powerMw] ?: d.powerMilliwatts,
        graphStyle = this[K.graph] ?: d.graphStyle, animStyle = this[K.anim] ?: d.animStyle,
        batteryMs = this[K.batMs] ?: d.batteryMs, systemMs = this[K.sysMs] ?: d.systemMs, netMs = this[K.netMs] ?: d.netMs,
        probeSec = this[K.probeSec] ?: d.probeSec, logSec = this[K.logSec] ?: d.logSec, bgIntervalMs = this[K.bgMs] ?: d.bgIntervalMs,
        dashboard = DashCards.decode(this[K.dash]),
        tempAlertOn = this[K.tOn] ?: d.tempAlertOn, tempAlertC = this[K.tV] ?: d.tempAlertC,
        levelAlertOn = this[K.lOn] ?: d.levelAlertOn, levelAlertPct = this[K.lV] ?: d.levelAlertPct,
        slowAlertOn = this[K.sOn] ?: d.slowAlertOn, slowAlertW = this[K.sW] ?: d.slowAlertW, slowAlertMin = this[K.sM] ?: d.slowAlertMin,
        dropAlertOn = this[K.dOn] ?: d.dropAlertOn,
        speedAlertOn = this[K.spOn] ?: d.speedAlertOn, speedAlertMbps = this[K.spV] ?: d.speedAlertMbps,
        storageAlertOn = this[K.stOn] ?: d.storageAlertOn, storageAlertPct = this[K.stV] ?: d.storageAlertPct,
        adapterW = this[K.adapter] ?: d.adapterW, ratedMah = this[K.rated] ?: d.ratedMah,
        serverId = this[K.server] ?: d.serverId, customDown = this[K.cDown] ?: "", customUp = this[K.cUp] ?: "",
        customPing = this[K.cPing] ?: "", probeHost = this[K.host] ?: d.probeHost,
        bgMonitoring = this[K.bg] ?: d.bgMonitoring, periodicWork = this[K.periodic] ?: d.periodicWork,
        periodicSpeed = this[K.periodicSpeed] ?: d.periodicSpeed,
    )
}

private fun MutablePreferences.write(s: Settings) {
    this[K.accent] = s.accent; this[K.tempF] = s.tempF; this[K.speedMB] = s.speedMegabytes
    this[K.powerMw] = s.powerMilliwatts; this[K.graph] = s.graphStyle; this[K.anim] = s.animStyle
    this[K.batMs] = s.batteryMs; this[K.sysMs] = s.systemMs; this[K.netMs] = s.netMs
    this[K.probeSec] = s.probeSec; this[K.logSec] = s.logSec; this[K.bgMs] = s.bgIntervalMs
    this[K.dash] = DashCards.encode(s.dashboard)
    this[K.tOn] = s.tempAlertOn; this[K.tV] = s.tempAlertC
    this[K.lOn] = s.levelAlertOn; this[K.lV] = s.levelAlertPct
    this[K.sOn] = s.slowAlertOn; this[K.sW] = s.slowAlertW; this[K.sM] = s.slowAlertMin
    this[K.dOn] = s.dropAlertOn
    this[K.spOn] = s.speedAlertOn; this[K.spV] = s.speedAlertMbps
    this[K.stOn] = s.storageAlertOn; this[K.stV] = s.storageAlertPct
    this[K.adapter] = s.adapterW; this[K.rated] = s.ratedMah
    this[K.server] = s.serverId; this[K.cDown] = s.customDown; this[K.cUp] = s.customUp
    this[K.cPing] = s.customPing; this[K.host] = s.probeHost
    this[K.bg] = s.bgMonitoring; this[K.periodic] = s.periodicWork; this[K.periodicSpeed] = s.periodicSpeed
}

class SettingsRepo(private val ctx: Context, scope: CoroutineScope) {
    val state: StateFlow<Settings> =
        ctx.ds.data.map { it.toSettings() }.stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Reads the persisted value directly (used by workers that may run before [state] has loaded). */
    suspend fun load(): Settings = ctx.ds.data.first().toSettings()

    /** Atomic read-modify-write. */
    suspend fun update(block: (Settings) -> Settings) {
        ctx.ds.edit { p -> p.write(block(p.toSettings())) }
    }
}
