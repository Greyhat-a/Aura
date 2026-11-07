package app.aura.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.os.CancellationSignal
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import app.aura.core.ChargeMath
import app.aura.core.Reading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

data class ConnInfo(
    val connected: Boolean = false,
    val transport: String = "None",
    val typeLabel: String = "No connection",
    val validated: Boolean = false,
    val metered: Boolean = false,
    val vpn: Boolean = false,
    val signalDbm: Int? = null,
    val linkMbps: Int? = null,
    val freqMhz: Int? = null,
    val estDownKbps: Int? = null,
    val estUpKbps: Int? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
)

data class NetworkSnapshot(
    val ts: Long,
    val conn: ConnInfo,
    /** Device-wide byte counters (TrafficStats) differenced over time. This is traffic, not link capacity. */
    val downMbps: Reading<Float>,
    val upMbps: Reading<Float>,
)

class NetworkMonitor(private val ctx: Context) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val tm = ctx.getSystemService(TelephonyManager::class.java)

    private val conn = MutableStateFlow(ConnInfo())
    private val _state = MutableStateFlow(NetworkSnapshot(System.currentTimeMillis(), ConnInfo(),
        Reading.Unavailable("Waiting for first sample"), Reading.Unavailable("Waiting for first sample")))
    val state: StateFlow<NetworkSnapshot> = _state.asStateFlow()

    @Volatile var activeNetwork: Network? = null
        private set

    private var prevRx = -1L
    private var prevTx = -1L
    private var prevNs = 0L

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            activeNetwork = network
            conn.value = build(caps, cm.getLinkProperties(network))
        }
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            cm.getNetworkCapabilities(network)?.let { conn.value = build(it, lp) }
        }
        override fun onLost(network: Network) {
            activeNetwork = null
            conn.value = ConnInfo()
        }
    }

    init {
        try { cm.registerDefaultNetworkCallback(callback) } catch (_: Exception) { }
    }

    /** One-shot connection read for workers (no callback needed). */
    fun currentConn(): ConnInfo {
        val n = cm.activeNetwork ?: return ConnInfo()
        val caps = cm.getNetworkCapabilities(n) ?: return ConnInfo()
        return build(caps, cm.getLinkProperties(n))
    }

    fun sample(): NetworkSnapshot {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val ns = System.nanoTime()
        var down: Reading<Float> = Reading.Unavailable("TrafficStats unsupported")
        var up: Reading<Float> = Reading.Unavailable("TrafficStats unsupported")
        if (rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()) {
            if (prevRx >= 0 && ns > prevNs) {
                val dt = (ns - prevNs) / 1e9
                val src = "TrafficStats device-wide byte counters (live traffic, not a speed test)"
                down = Reading.Value(((rx - prevRx).coerceAtLeast(0) * 8 / dt / 1e6).toFloat(), src)
                up = Reading.Value(((tx - prevTx).coerceAtLeast(0) * 8 / dt / 1e6).toFloat(), src)
            } else {
                down = Reading.Unavailable("Waiting for second sample"); up = down
            }
            prevRx = rx; prevTx = tx; prevNs = ns
        }
        val s = NetworkSnapshot(System.currentTimeMillis(), conn.value, down, up)
        _state.value = s
        return s
    }

    private fun build(c: NetworkCapabilities, lp: LinkProperties?): ConnInfo {
        val wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cell = c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val eth = c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val vpn = c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val transport = when { wifi -> "Wi-Fi"; cell -> "Cellular"; eth -> "Ethernet"; vpn -> "VPN"; else -> "Other" }
        var link: Int? = null
        var freq: Int? = null
        var wifiStd = ""
        val ti = c.transportInfo
        if (ti is WifiInfo) {
            if (ti.linkSpeed > 0) link = ti.linkSpeed
            if (ti.frequency > 0) freq = ti.frequency
            wifiStd = when (ti.wifiStandard) {
                6 -> " 802.11ax (Wi-Fi 6)"; 5 -> " 802.11ac (Wi-Fi 5)"; 4 -> " 802.11n (Wi-Fi 4)"
                7 -> " 802.11be (Wi-Fi 7)"; else -> ""
            }
        }
        val signal = c.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        val addrs = lp?.linkAddresses?.map { it.address }?.filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }.orEmpty()
        val label = when {
            wifi -> "Wi-Fi" + wifiStd + (freq?.let { " · ${if (it > 5900) "6" else if (it > 4900) "5" else "2.4"} GHz" } ?: "")
            cell -> cellularLabel()
            else -> transport
        }
        return ConnInfo(
            connected = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET), transport = transport,
            typeLabel = label, validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED), vpn = vpn,
            signalDbm = signal, linkMbps = link, freqMhz = freq,
            estDownKbps = c.linkDownstreamBandwidthKbps.takeIf { it > 0 },
            estUpKbps = c.linkUpstreamBandwidthKbps.takeIf { it > 0 },
            ipv4 = addrs.firstOrNull { it is Inet4Address }?.hostAddress,
            ipv6 = addrs.firstOrNull { it !is Inet4Address }?.hostAddress?.substringBefore('%'),
        )
    }

    private fun cellularLabel(): String {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return "Cellular (grant Phone permission on Network screen to see 4G/5G)"
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "Cellular · 5G NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "Cellular · LTE (4G)"
                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "Cellular · 3G"
                TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "Cellular · 2G"
                else -> "Cellular"
            }
        } catch (_: SecurityException) { "Cellular" }
    }

    fun hasPhonePermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
}

data class ProbeResult(
    val ts: Long, val host: String,
    val latencyMs: Float?, val jitterMs: Float?, val lossPct: Float,
    val dnsMs: Float?, val stabilityPct: Float?,
)

/**
 * Latency probe: timed TCP connects to host:443 (ICMP ping is not available to unprivileged apps),
 * plus an uncached DNS query. Jitter = mean absolute difference of consecutive RTTs.
 */
class LatencyProbe(private val net: NetworkMonitor) {
    private val _state = MutableStateFlow<ProbeResult?>(null)
    val state: StateFlow<ProbeResult?> = _state.asStateFlow()
    private val window = ArrayDeque<Float?>()   // last 60 probe medians; null = total failure

    suspend fun run(host: String, attempts: Int = 5): ProbeResult {
        val rtts = ArrayList<Float>()
        var lost = 0
        val addr = withContext(Dispatchers.IO) { try { InetAddress.getByName(host) } catch (_: Exception) { null } }
        if (addr == null) lost = attempts
        else repeat(attempts) {
            val t = tcpRtt(addr)
            if (t == null) lost++ else rtts += t
            delay(150)
        }
        val med = ChargeMath.median(rtts)
        window.addLast(med); if (window.size > 60) window.removeFirst()
        val winMed = ChargeMath.median(window.filterNotNull())
        val stability = if (window.size >= 3 && winMed != null)
            window.count { it != null && it <= winMed * 3f } * 100f / window.size else null
        val dns = dnsMs(listOf("cloudflare.com", "example.com", "wikipedia.org")[(System.currentTimeMillis() / 10_000 % 3).toInt()])
        val r = ProbeResult(System.currentTimeMillis(), host, med, ChargeMath.jitterMs(rtts), lost * 100f / attempts, dns, stability)
        _state.value = r
        return r
    }

    suspend fun tcpRtt(addr: InetAddress, port: Int = 443, timeoutMs: Int = 1500): Float? = withContext(Dispatchers.IO) {
        val s = Socket()
        try {
            val t0 = System.nanoTime()
            s.connect(InetSocketAddress(addr, port), timeoutMs)
            (System.nanoTime() - t0) / 1e6f
        } catch (_: Exception) { null } finally { try { s.close() } catch (_: Exception) { } }
    }

    private suspend fun dnsMs(name: String): Float? = withTimeoutOrNull(3000) {
        suspendCancellableCoroutine<Float?> { cont ->
            val t0 = System.nanoTime()
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            DnsResolver.getInstance().query(
                net.activeNetwork, name, DnsResolver.FLAG_NO_CACHE_LOOKUP, { it.run() }, signal,
                object : DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                        if (cont.isActive) cont.resume(if (rcode == 0) (System.nanoTime() - t0) / 1e6f else null)
                    }
                    override fun onError(error: DnsResolver.DnsException) { if (cont.isActive) cont.resume(null) }
                },
            )
        }
    }
}
