package app.aura.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aura.MainActivity
import app.aura.R
import app.aura.core.Fmt
import app.aura.core.orNull
import app.aura.data.SpeedResultEntity
import app.aura.monitor.BatterySnapshot
import app.aura.monitor.ConnInfo
import app.aura.monitor.SystemSnapshot
import app.aura.settings.Settings

object Notifier {
    const val CH_ALERTS = "aura_alerts"
    const val CH_SERVICE = "aura_monitor"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "AURA alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Background monitoring", NotificationManager.IMPORTANCE_LOW))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun alert(ctx: Context, id: Int, title: String, text: String) {
        val n = Notification.Builder(ctx, CH_ALERTS).setSmallIcon(R.drawable.ic_stat_aura).setContentTitle(title)
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx)).setAutoCancel(true).build()
        // If POST_NOTIFICATIONS was denied, Android drops this silently; the in-app Alerts screen explains that.
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun service(ctx: Context, text: String): Notification =
        Notification.Builder(ctx, CH_SERVICE).setSmallIcon(R.drawable.ic_stat_aura).setContentTitle("AURA monitoring")
            .setContentText(text).setOngoing(true).setContentIntent(openApp(ctx)).build()
}

/**
 * Threshold rules with persisted state (SharedPreferences) so they behave the same whether evaluated by the
 * in-process monitor, the foreground service, or WorkManager after the process was killed.
 */
class AlertEngine(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("alert_state", Context.MODE_PRIVATE)

    private fun due(key: String, cooldownMs: Long, now: Long): Boolean {
        if (now - sp.getLong(key, 0L) < cooldownMs) return false
        sp.edit().putLong(key, now).apply()
        return true
    }

    fun evaluate(b: BatterySnapshot, s: SystemSnapshot?, conn: ConnInfo?, st: Settings, now: Long = System.currentTimeMillis()) {
        // 1. temperature
        if (st.tempAlertOn && b.tempC >= st.tempAlertC && due("t", 15 * 60_000L, now))
            Notifier.alert(ctx, 1, "Battery is hot", "Battery temperature is ${Fmt.temp(b.tempC, st.tempF)} (limit ${Fmt.temp(st.tempAlertC, st.tempF)}).")

        // 2. level crossing in either direction
        val last = sp.getInt("lastLevel", -1)
        if (st.levelAlertOn && last >= 0 && last != b.levelPct) {
            val t = st.levelAlertPct
            if ((last < t && b.levelPct >= t) || (last > t && b.levelPct <= t))
                Notifier.alert(ctx, 2, "Battery at $t%", "Battery reached ${b.levelPct}% (${b.statusText.lowercase()}).")
        }
        sp.edit().putInt("lastLevel", b.levelPct).apply()

        // 3. unusually slow charging (battery-side power below limit for N minutes, below 80 %)
        val pw = b.powerMw.orNull()
        if (st.slowAlertOn && b.charging && b.levelPct < 80 && pw != null && pw / 1000f < st.slowAlertW) {
            val since = sp.getLong("slowSince", 0L).takeIf { it > 0 } ?: now.also { sp.edit().putLong("slowSince", it).apply() }
            if (now - since >= st.slowAlertMin * 60_000L && due("slow", 30 * 60_000L, now))
                Notifier.alert(ctx, 3, "Charging is slow", "Battery-side charging power is ${Fmt.power(pw, false)} (below ${st.slowAlertW} W). Check cable, adapter or heavy use.")
        } else sp.edit().putLong("slowSince", 0L).apply()

        // 4. connection dropped (20 s grace)
        if (st.dropAlertOn) {
            if (conn == null || !conn.connected) {
                val since = sp.getLong("downSince", 0L).takeIf { it > 0 } ?: now.also { sp.edit().putLong("downSince", it).apply() }
                if (now - since >= 20_000L && due("drop", 30 * 60_000L, now))
                    Notifier.alert(ctx, 4, "Internet connection lost", "No active internet connection for over 20 seconds.")
            } else sp.edit().putLong("downSince", 0L).apply()
        }

        // 6. storage
        if (st.storageAlertOn && s != null && s.storageUsedPct >= st.storageAlertPct && due("storage", 6 * 3600_000L, now))
            Notifier.alert(ctx, 6, "Storage almost full", "Internal storage is ${"%.0f".format(s.storageUsedPct)}% used (${Fmt.bytes(s.storageAvail)} free).")
    }

    /** 5. speed below threshold - evaluated whenever a real speed test completes. */
    fun evaluateSpeed(r: SpeedResultEntity, st: Settings, now: Long = System.currentTimeMillis()) {
        val d = r.downMbps ?: return
        if (st.speedAlertOn && d < st.speedAlertMbps && due("speed", 10 * 60_000L, now))
            Notifier.alert(ctx, 5, "Internet speed is low", "Download measured ${"%.1f".format(d)} Mbps (limit ${st.speedAlertMbps} Mbps).")
    }
}
