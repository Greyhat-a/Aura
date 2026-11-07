package app.aura.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.aura.AuraApp
import app.aura.alerts.Notifier
import app.aura.core.Fmt
import app.aura.core.orNull
import kotlinx.coroutines.*

/** User-enabled foreground service: keeps the process alive so history logging and alerts continue with the screen off. */
class MonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var held = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val g = (application as AuraApp).graph
        startForeground(NOTIF_ID, Notifier.service(this, "Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!held) {
            held = true
            g.hub.holdBackground(true)
            scope.launch {
                while (isActive) {
                    val b = g.battery.state.value
                    if (b != null) {
                        val st = g.settings.state.value
                        val text = "${b.levelPct}% · ${b.statusText}" +
                            (b.powerMw.orNull()?.takeIf { b.charging }?.let { " · ${Fmt.power(it, st.powerMilliwatts)}" } ?: "") +
                            " · ${Fmt.temp(b.tempC, st.tempF)}"
                        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, Notifier.service(this@MonitoringService, text))
                    }
                    delay(30_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (held) (application as AuraApp).graph.hub.holdBackground(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, MonitoringService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MonitoringService::class.java)) }
    }
}

/** Runs every ~15 min (WorkManager minimum) so history and alerts continue even without the foreground service. */
class MonitorWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val g = (applicationContext as AuraApp).graph
        val st = g.settings.load()
        val b = g.battery.sample()
        val s = g.system.sample()
        val conn = g.network.currentConn()
        g.recorder.record(b, s, 0L, force = true)
        g.alerts.evaluate(b, s, conn, st)
        if (st.periodicSpeed && conn.connected && !conn.metered) {
            // Deliberately small: 3 s download only, to limit data use.
            try { g.runSpeedTest(downSeconds = 3, upSeconds = 0) } catch (_: Exception) { }
        }
        return Result.success()
    }
}
