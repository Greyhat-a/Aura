package app.aura

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.aura.alerts.AlertEngine
import app.aura.alerts.Notifier
import app.aura.data.AuraDatabase
import app.aura.data.Recorder
import app.aura.monitor.*
import app.aura.settings.SettingsRepo
import app.aura.service.MonitorHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Manual dependency graph: every monitor is an independent object; only [MonitorHub] schedules them. */
class AuraGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepo(app, scope)
    val db = AuraDatabase.get(app)
    val battery = BatteryMonitor(app)
    val system = SystemMonitor(app)
    val network = NetworkMonitor(app)
    val probe = LatencyProbe(network)
    val sensors = SensorExplorer(app)
    val speed = SpeedTester()
    val recorder = Recorder(db.dao())
    val alerts = AlertEngine(app)
    val hub = MonitorHub(this)

    /** Runs a speed test, stores the result and evaluates the speed alert. */
    suspend fun runSpeedTest(downSeconds: Int = 8, upSeconds: Int = 8) {
        val st = settings.load()
        val server = SpeedServers.selected(st)
        val label = network.currentConn().typeLabel
        val r = speed.run(server, label, downSeconds, upSeconds) ?: return
        recorder.recordSpeed(r)
        alerts.evaluateSpeed(r, st)
    }
}

class AuraApp : Application() {
    lateinit var graph: AuraGraph
        private set

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        graph = AuraGraph(this)
        graph.hub.start()
        graph.scope.launch {
            graph.settings.state.map { it.periodicWork }.distinctUntilChanged().collect { on ->
                val wm = WorkManager.getInstance(this@AuraApp)
                if (on) wm.enqueueUniquePeriodicWork(
                    "aura-sample", ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<app.aura.service.MonitorWorker>(15, TimeUnit.MINUTES).build(),
                ) else wm.cancelUniqueWork("aura-sample")
            }
        }
    }
}
