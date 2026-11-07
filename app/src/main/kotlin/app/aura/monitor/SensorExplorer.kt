package app.aura.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class SensorSample(val values: FloatArray, val accuracy: Int, val tsNs: Long)

class SensorExplorer(ctx: Context) {
    private val sm = ctx.getSystemService(SensorManager::class.java)

    fun all(): List<Sensor> = sm.getSensorList(Sensor.TYPE_ALL).sortedBy { it.name }
    fun default(type: Int): Sensor? = sm.getDefaultSensor(type)

    val keyTypes = listOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_GYROSCOPE to "Gyroscope",
        Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_LIGHT to "Ambient light",
    )

    fun canStream(s: Sensor) = s.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT

    /** Live readings. Listener is unregistered as soon as the collector goes away. */
    fun stream(s: Sensor): Flow<SensorSample> = callbackFlow {
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { trySend(SensorSample(e.values.clone(), e.accuracy, e.timestamp)) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val ok = try { sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI) } catch (_: SecurityException) { false }
        if (!ok) close()
        awaitClose { sm.unregisterListener(l) }
    }.conflate()

    companion object {
        fun unit(type: Int): String = when (type) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
            Sensor.TYPE_GYROSCOPE -> "rad/s"
            Sensor.TYPE_MAGNETIC_FIELD -> "µT"
            Sensor.TYPE_LIGHT -> "lx"
            Sensor.TYPE_PROXIMITY -> "cm"
            Sensor.TYPE_PRESSURE -> "hPa"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
            else -> ""
        }

        fun axes(type: Int, n: Int): List<String> = when (type) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> listOf("x", "y", "z")
            else -> (0 until n).map { "v$it" }
        }.take(n)
    }
}
