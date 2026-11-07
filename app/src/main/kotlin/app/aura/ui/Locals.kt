package app.aura.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import app.aura.AuraGraph
import app.aura.settings.Settings
import kotlinx.coroutines.launch
import java.io.OutputStream

/** Things only an Activity can do (permission prompts, file pickers, starting the foreground service). */
interface UiActions {
    val notifGranted: State<Boolean>
    val phoneGranted: State<Boolean>
    fun requestNotifications()
    fun requestPhoneState()
    /** Opens the system "save as" picker and streams [writer] into the chosen file. */
    fun exportCsv(fileName: String, writer: suspend (OutputStream) -> Unit)
    fun setBackgroundMonitoring(on: Boolean)
    fun openAppSettings()
    fun openBatterySettings()
}

val LocalGraph = staticCompositionLocalOf<AuraGraph> { error("AuraGraph not provided") }
val LocalActions = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }

/** Number of grid columns for the current window (2 phone, 3-4 tablet). */
val LocalCols = compositionLocalOf { 2 }

fun AuraGraph.edit(block: (Settings) -> Settings) {
    scope.launch { settings.update(block) }
}
