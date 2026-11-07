package app.aura

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import app.aura.service.MonitoringService
import app.aura.ui.AuraRoot
import app.aura.ui.LocalActions
import app.aura.ui.LocalGraph
import app.aura.ui.UiActions
import app.aura.ui.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class MainActivity : ComponentActivity(), UiActions {
    private lateinit var graph: AuraGraph
    private val _notif = mutableStateOf(false)
    private val _phone = mutableStateOf(false)
    override val notifGranted: State<Boolean> get() = _notif
    override val phoneGranted: State<Boolean> get() = _phone

    private var pendingWriter: (suspend (OutputStream) -> Unit)? = null

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }
    private val phoneLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val w = pendingWriter
        pendingWriter = null
        if (uri != null && w != null) {
            graph.scope.launch(Dispatchers.IO) {
                val msg = try {
                    contentResolver.openOutputStream(uri)?.use { w(it) }
                    "CSV exported"
                } catch (e: Exception) {
                    "Export failed: ${e.message}"
                }
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph = (application as AuraApp).graph
        refreshPermissions()
        // Ask once, on first launch, for the notification permission needed by alerts (Android 13+).
        val prefs = getSharedPreferences("ui_state", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !_notif.value && !prefs.getBoolean("askedNotif", false)) {
            prefs.edit().putBoolean("askedNotif", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CompositionLocalProvider(LocalGraph provides graph, LocalActions provides this) { AuraRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        // Android 15 only allows starting this kind of foreground service while the app is visible, so do it here.
        graph.scope.launch {
            if (graph.settings.load().bgMonitoring) withContext(Dispatchers.Main) { MonitoringService.start(this@MainActivity) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        _notif.value = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        _phone.value = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun requestPhoneState() { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) }

    override fun exportCsv(fileName: String, writer: suspend (OutputStream) -> Unit) {
        pendingWriter = writer
        createDoc.launch(fileName)
    }

    override fun setBackgroundMonitoring(on: Boolean) {
        graph.edit { it.copy(bgMonitoring = on) }
        if (on) MonitoringService.start(this) else MonitoringService.stop(this)
    }

    override fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
